package com.shiv.rally.presentation.player

import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.MultiViewSlot
import com.shiv.rally.domain.model.SportEvent
import java.time.LocalDate
import java.time.ZoneId

internal val redZoneTimeZone: ZoneId = ZoneId.of("America/New_York")

internal fun MultiViewSlot.isRedZone(): Boolean = listOf(title, channel?.name, sourceTitle).any {
    it?.contains(Regex("red\\s*zone", RegexOption.IGNORE_CASE)) == true
}

/** RedZone's daytime slate excludes international morning and primetime games.
 * A selected RedZone feed also permits special daytime slates on other weekdays. */
internal fun redZoneGamesForDay(events: List<SportEvent>, day: LocalDate): List<SportEvent> = events.filter {
    val kickoff = it.startTime.atZone(redZoneTimeZone)
    it.league.equals("NFL", true) && kickoff.toLocalDate() == day &&
        kickoff.hour in 13..17 && it.status != EventStatus.CANCELED
}.distinctBy { it.id }.sortedBy { it.startTime }

internal fun multiviewStatsGames(slots: List<MultiViewSlot>, redZoneGames: List<SportEvent>): List<SportEvent> =
    (slots.mapNotNull { it.event } + if (slots.any { it.isRedZone() }) redZoneGames else emptyList())
        .distinctBy { it.id }

internal fun multiViewTitle(event: SportEvent): String {
    val away = event.awayTeam?.abbreviation?.takeIf(String::isNotBlank)
        ?: event.awayTeam?.name?.takeIf(String::isNotBlank)
    val home = event.homeTeam?.abbreviation?.takeIf(String::isNotBlank)
        ?: event.homeTeam?.name?.takeIf(String::isNotBlank)
    return if (away != null && home != null) "$away vs $home" else event.name.ifBlank { event.league }
}

/** Muting every other player before unmuting the target prevents overlapping
 * audio during rapid focus changes. Registration never starts an audible stream. */
internal class MultiViewAudioRouter {
    private val outputs = linkedMapOf<String, (Boolean) -> Unit>()
    private var selected: String? = null
    private var audioAllowed = true
    fun register(id: String, output: (Boolean) -> Unit) {
        output(false)
        outputs[id] = output
        if (audioAllowed && selected == id) output(true)
    }
    fun setAudioAllowed(allowed: Boolean) {
        if (audioAllowed == allowed) return
        outputs.values.forEach { it(false) }
        audioAllowed = allowed
        if (allowed) outputs[selected]?.invoke(true)
    }
    fun unregister(id: String) { outputs.remove(id)?.invoke(false) }
    fun select(id: String?) {
        if (id == selected) return
        outputs.values.forEach { it(false) }
        selected = id
        if (audioAllowed) outputs[id]?.invoke(true)
    }
}
