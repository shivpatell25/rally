package com.shiv.rally.data.remote.sports

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class EspnSummaryTeamTest {
    @Test
    fun `historical summary logos and records are retained`() {
        val competitor = Gson().fromJson("""
            {"homeAway":"home","score":"5","team":{
                "id":"37","abbreviation":"VGK","displayName":"Vegas Golden Knights",
                "logos":[{"href":"https://a.espncdn.com/i/teamlogos/nhl/500/vgk.png"}]
            },"record":[{"type":"total","summary":"1-0-0"}]}
        """.trimIndent(), EspnCompetitor::class.java)

        assertEquals("https://a.espncdn.com/i/teamlogos/nhl/500/vgk.png", competitor.team?.logoImage)
        assertEquals("total", competitor.records?.single()?.name)
        assertEquals("1-0-0", competitor.records?.single()?.summary)
    }

    @Test
    fun `scoreboard single logo and records still decode`() {
        val competitor = Gson().fromJson("""
            {"team":{"id":"37","logo":"https://a.espncdn.com/i/teamlogos/nhl/500/vgk.png"},
             "records":[{"name":"total","summary":"1-0-0"}]}
        """.trimIndent(), EspnCompetitor::class.java)

        assertEquals("https://a.espncdn.com/i/teamlogos/nhl/500/vgk.png", competitor.team?.logoImage)
        assertEquals("1-0-0", competitor.records?.single()?.summary)
    }
}
