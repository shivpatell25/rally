package com.shiv.rally.data.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shiv.rally.MainActivity
import com.shiv.rally.R
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.GameAlert
import com.shiv.rally.domain.model.GameAlertType
import com.shiv.rally.domain.model.SportEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class GameAlertManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val delivered = ConcurrentHashMap.newKeySet<String>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live sports alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Kickoff, scoring, close-game, overtime and final alerts"
            }
        )
    }

    fun evaluate(previous: List<SportEvent>, current: List<SportEvent>, favoriteTeamIds: Set<String>): List<GameAlert> {
        if (previous.isEmpty()) return emptyList()
        val previousById = previous.associateBy { it.id }
        return current.flatMap { event ->
            val old = previousById[event.id] ?: return@flatMap emptyList()
            val favorite = event.homeTeam?.id in favoriteTeamIds || event.awayTeam?.id in favoriteTeamIds
            if (!favorite) return@flatMap emptyList()
            buildList {
                if (old.status == EventStatus.NOT_STARTED && event.status in setOf(EventStatus.LIVE, EventStatus.HALFTIME)) {
                    add(alert(event, GameAlertType.KICKOFF, "Kickoff", event.name))
                }
                if (scoreChanged(old, event)) {
                    add(alert(event, GameAlertType.SCORE, "Score update", scoreLine(event)))
                }
                val detail = event.gameStatusDetail.orEmpty().lowercase()
                val oldDetail = old.gameStatusDetail.orEmpty().lowercase()
                if ((detail.contains("overtime") || detail.contains(" ot")) && !(oldDetail.contains("overtime") || oldDetail.contains(" ot"))) {
                    add(alert(event, GameAlertType.OVERTIME, "Overtime", scoreLine(event)))
                }
                if (isCloseLateGame(event) && !isCloseLateGame(old)) {
                    add(alert(event, GameAlertType.CLOSE_GAME, "Close game", scoreLine(event)))
                }
                if (old.status != EventStatus.FINISHED && event.status == EventStatus.FINISHED) {
                    add(alert(event, GameAlertType.FINAL, "Final", scoreLine(event)))
                }
            }
        }.filter { delivered.add(it.id) }.onEach(::notify)
    }

    fun redZoneAlert(channelId: String, program: String?): GameAlert? {
        val key = "redzone:$channelId:${program.orEmpty()}"
        if (!delivered.add(key)) return null
        return GameAlert(key, null, GameAlertType.RED_ZONE, "NFL RedZone is live", program ?: "Whip-around coverage is available").also(::notify)
    }

    private fun alert(event: SportEvent, type: GameAlertType, title: String, message: String): GameAlert {
        val scoreKey = "${event.scoreAway}:${event.scoreHome}:${event.gameStatusDetail}"
        return GameAlert("${event.id}:$type:$scoreKey", event.id, type, title, message)
    }

    private fun scoreChanged(old: SportEvent, current: SportEvent): Boolean =
        old.scoreHome != null && old.scoreAway != null &&
            (old.scoreHome != current.scoreHome || old.scoreAway != current.scoreAway)

    private fun isCloseLateGame(event: SportEvent): Boolean {
        val home = event.scoreHome ?: return false
        val away = event.scoreAway ?: return false
        val detail = event.gameStatusDetail.orEmpty().lowercase()
        val late = listOf("4th", "final minute", "2:00", "1:00", "9th", "3rd period", "90'").any(detail::contains)
        return late && abs(home - away) <= when {
            event.sport.contains("basket", true) -> 5
            event.sport.contains("base", true) -> 1
            event.sport.contains("hock", true) || event.sport.contains("socc", true) -> 1
            else -> 3
        }
    }

    private fun scoreLine(event: SportEvent): String =
        "${event.awayTeam?.abbreviation ?: "Away"} ${event.scoreAway ?: "–"} · ${event.homeTeam?.abbreviation ?: "Home"} ${event.scoreHome ?: "–"}"

    private fun notify(alert: GameAlert) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = alert.eventId?.let { Uri.parse("rally://event/$it") }
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, alert.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notificationManager.notify(
            alert.id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.rally_mark_white)
                .setContentTitle(alert.title)
                .setContentText(alert.message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    private companion object { const val CHANNEL_ID = "live_sports" }
}
