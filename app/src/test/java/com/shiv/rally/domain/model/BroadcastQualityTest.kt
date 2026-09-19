package com.shiv.rally.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BroadcastQualityTest {
    private val event = SportEvent(
        id = "event",
        name = "Away at Home",
        homeTeam = Team("home", "Home Team", "HOM"),
        awayTeam = Team("away", "Away Team", "AWY"),
        startTime = Instant.EPOCH,
        status = EventStatus.LIVE,
        sport = "football",
        league = "NFL"
    )

    @Test
    fun `network name alone does not claim 4k`() {
        val result = resolveMaxBroadcastQuality(event, broadcastStations = listOf("FOX"))
        assertEquals("HD", result.badgeText)
        assertFalse(result.is4K)
    }

    @Test
    fun `iptv channel labels do not inflate official game quality`() {
        val channel = IptvChannel("1", "1", "FOX SPORTS 4K HDR", "Sports")
        val result = resolveMaxBroadcastQuality(
            event,
            broadcastStations = listOf("FOX"),
            relevantChannels = listOf(RelevantChannel(channel, 90f))
        )
        assertEquals("HD", result.badgeText)
        assertFalse(result.is4K)
        assertFalse(result.isHdr)
    }

    @Test
    fun `direct addon metadata can report verified quality`() {
        val result = resolveMaxBroadcastQuality(
            event,
            broadcastStations = listOf("FOX"),
            stremioStreams = listOf(StremioStreamOption("Game 4K HDR", streamUrl = "https://example.test/game.m3u8"))
        )
        assertEquals("4K HDR", result.badgeText)
        assertEquals("Verified stream metadata", result.evidenceSource)
    }

    @Test
    fun `official espn nfl production reports broadcaster quality`() {
        val result = resolveMaxBroadcastQuality(event, broadcastStations = listOf("ESPN"))
        assertEquals("4K HDR", result.badgeText)
        assertEquals("Official broadcaster", result.evidenceSource)
    }
}
