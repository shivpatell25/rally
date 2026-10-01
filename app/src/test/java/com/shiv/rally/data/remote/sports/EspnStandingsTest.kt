package com.shiv.rally.data.remote.sports

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class EspnStandingsTest {
    @Test fun recordUsesWinsEvenWhenFeedStartsWithGamesBehind() {
        val stats = JsonParser.parseString("""[{"name":"gamesBehind","displayValue":"-"},{"name":"losses","displayValue":"0"},{"name":"ties","displayValue":"0"},{"name":"wins","displayValue":"3"},{"name":"winPercent","displayValue":"1.000"},{"name":"overall","displayValue":"3-0"}]""").asJsonArray
        assertEquals("3-0 · PCT 1.000", espnStandingSummary(stats))
    }
    @Test fun playoffPictureUsesPublishedConferenceSeeds() {
        val root = JsonParser.parseString("""{"children":[{"abbreviation":"AFC","standings":{"entries":[
          {"team":{"displayName":"Team Z"},"stats":[{"name":"playoffSeed","displayValue":"8"}]},
          {"team":{"displayName":"Team A"},"stats":[{"name":"playoffSeed","displayValue":"2"}]},
          {"team":{"displayName":"Team B"},"stats":[{"name":"playoffSeed","displayValue":"1"}]}
        ]}},{"abbreviation":"NFC","standings":{"entries":[{"team":{"displayName":"Team C"},"stats":[{"name":"playoffSeed","displayValue":"1"}]}]}}]}""").asJsonObject
        val (standings, playoffs) = espnStandings(root, "NFL")
        assertEquals(4, standings.size)
        assertEquals(listOf("Team B", "Team A", "Team C"), playoffs.map { it.first })
        assertEquals("AFC · Seed 1", playoffs.first().second)
    }
    @Test fun missingSeedNeverBecomesInventedRanking() {
        val root = JsonParser.parseString("""{"standings":{"entries":[{"team":{"displayName":"Team"},"stats":[]}]}}""").asJsonObject
        assertTrue(espnStandings(root, "NFL").second.isEmpty())
    }
}
