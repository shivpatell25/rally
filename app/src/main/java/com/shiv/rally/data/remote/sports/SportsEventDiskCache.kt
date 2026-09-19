package com.shiv.rally.data.remote.sports

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.Team
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Small last-known-good schedule cache used when a TV temporarily loses DNS/network access. */
@Singleton
class SportsEventDiskCache @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "sports_schedule_cache_v1.json")
    private val atomicFile = AtomicFile(file)
    private var lastSignature: Int? = null

    fun read(): List<SportEvent> = runCatching {
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText())
        val cachedAt = root.optLong("cachedAt", 0L)
        if (cachedAt <= 0L || System.currentTimeMillis() - cachedAt > MAX_CACHE_AGE_MS) return emptyList()
        val events = root.optJSONArray("events") ?: return emptyList()
        buildList {
            for (index in 0 until events.length()) {
                events.optJSONObject(index)?.toEvent()?.let(::add)
            }
        }.also { lastSignature = signature(it) }
    }.getOrDefault(emptyList())

    fun write(events: List<SportEvent>) {
        if (events.isEmpty()) return
        val signature = signature(events)
        if (signature == lastSignature) return
        val root = JSONObject().apply {
            put("cachedAt", System.currentTimeMillis())
            put("events", JSONArray().apply {
                events.distinctBy { it.id }.forEach { put(it.toJson()) }
            })
        }
        val output = runCatching { atomicFile.startWrite() }.getOrNull() ?: return
        try {
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
            lastSignature = signature
        } catch (_: Exception) {
            atomicFile.failWrite(output)
        }
    }

    private fun signature(events: List<SportEvent>): Int = events
        .sortedBy { it.id }
        .joinToString("|") { "${it.id}:${it.status}:${it.scoreAway}:${it.scoreHome}:${it.startTime}" }
        .hashCode()

    private fun SportEvent.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("homeTeam", homeTeam?.toJson())
        put("awayTeam", awayTeam?.toJson())
        put("startTime", startTime.toEpochMilli())
        put("status", status.name)
        put("scoreHome", scoreHome)
        put("scoreAway", scoreAway)
        put("sport", sport)
        put("league", league)
        put("bannerUrl", bannerUrl)
        put("homeTeamBadge", homeTeamBadge)
        put("awayTeamBadge", awayTeamBadge)
        put("venue", venue)
        put("eventContextTitle", eventContextTitle)
        put("gameStatusDetail", gameStatusDetail)
        put("liveStats", JSONObject(liveStats))
    }

    private fun Team.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("abbreviation", abbreviation)
        put("logoUrl", logoUrl)
        put("colors", JSONArray(colors))
    }

    private fun JSONObject.toEvent(): SportEvent? = runCatching {
        SportEvent(
            id = getString("id"),
            name = getString("name"),
            homeTeam = optJSONObject("homeTeam")?.toTeam(),
            awayTeam = optJSONObject("awayTeam")?.toTeam(),
            startTime = Instant.ofEpochMilli(getLong("startTime")),
            status = runCatching { EventStatus.valueOf(getString("status")) }.getOrDefault(EventStatus.NOT_STARTED),
            scoreHome = optionalInt("scoreHome"),
            scoreAway = optionalInt("scoreAway"),
            sport = getString("sport"),
            league = getString("league"),
            bannerUrl = optionalString("bannerUrl"),
            homeTeamBadge = optionalString("homeTeamBadge"),
            awayTeamBadge = optionalString("awayTeamBadge"),
            venue = optionalString("venue"),
            eventContextTitle = optionalString("eventContextTitle"),
            liveStats = optJSONObject("liveStats")?.toStringMap().orEmpty(),
            gameStatusDetail = optionalString("gameStatusDetail")
        )
    }.getOrNull()

    private fun JSONObject.toTeam(): Team? = runCatching {
        val colorsJson = optJSONArray("colors")
        Team(
            id = getString("id"),
            name = getString("name"),
            abbreviation = getString("abbreviation"),
            logoUrl = optionalString("logoUrl"),
            colors = buildList {
                if (colorsJson != null) for (index in 0 until colorsJson.length()) add(colorsJson.getString(index))
            }
        )
    }.getOrNull()

    private fun JSONObject.toStringMap(): Map<String, String> = buildMap {
        keys().forEach { key -> optString(key).takeIf(String::isNotBlank)?.let { put(key, it) } }
    }

    private fun JSONObject.optionalString(key: String): String? =
        takeIf { has(key) && !isNull(key) }?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.optionalInt(key: String): Int? =
        takeIf { has(key) && !isNull(key) }?.optInt(key)

    private companion object {
        const val MAX_CACHE_AGE_MS = 72L * 60L * 60L * 1_000L
    }
}
