package com.shiv.rally.data.remote.sports

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class EspnFootballPlaysTest {
    @Test fun combinesPastCurrentAndScoringDrivesWithoutDuplicates() {
        val summary = Gson().fromJson("""{
          "drives": {"previous": [{"plays": [
            {"id":"kick","sequenceNumber":"3900","text":"Kickoff","period":{"number":1},"clock":{"displayValue":"15:00"}},
            {"id":"td","sequenceNumber":"4600","text":"Passing touchdown","period":{"number":1},"scoringPlay":false}
          ]}], "current": {"plays": [{"id":"run","sequenceNumber":"5000","text":"Run for five yards","period":{"number":2}}]}},
          "scoringPlays": [{"id":"td","text":"Touchdown"}, {"id":"fg","sequenceNumber":"6000","text":"Field goal"}]
        }""", EspnSummaryResponse::class.java)
        val plays = summary.allPlays()
        assertEquals(listOf("kick", "td", "run", "fg"), plays.map { it.id })
        assertTrue(plays.first { it.id == "td" }.scoringPlay == true)
        assertTrue(plays.first { it.id == "fg" }.scoringPlay == true)
        assertEquals(1, plays.first().period?.number)
        assertEquals("15:00", plays.first().clock?.displayValue)
        assertEquals("Passing touchdown", plays.first { it.id == "td" }.text)
    }
}
