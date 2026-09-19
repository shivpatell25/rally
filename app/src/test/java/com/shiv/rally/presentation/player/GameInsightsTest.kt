package com.shiv.rally.presentation.player

import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.Team
import com.shiv.rally.domain.model.TeamStatComparison
import com.shiv.rally.domain.model.WinProbabilityPoint
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameInsightsTest {
    private fun event(
        awayScore: Int = 0,
        homeScore: Int = 0,
        stats: List<TeamStatComparison>,
        probability: List<WinProbabilityPoint> = emptyList()
    ) = SportEvent(
        id = "game",
        name = "Away at Home",
        homeTeam = Team("home", "Home Team", "HOM"),
        awayTeam = Team("away", "Away Team", "AWY"),
        startTime = Instant.EPOCH,
        status = EventStatus.LIVE,
        scoreHome = homeScore,
        scoreAway = awayScore,
        sport = "Soccer",
        league = "Test League",
        teamStats = stats,
        winProbability = probability
    )

    @Test
    fun possessionAndShotsBecomeReadableInsights() {
        val insights = event(
            awayScore = 0,
            homeScore = 2,
            stats = listOf(
                TeamStatComparison("SOG", "0", "2"),
                TeamStatComparison("Possession", "34.4", "65.6")
            )
        ).gameInsights()

        assertEquals("HOM controls 65.6% possession", insights[0])
        assertEquals("HOM leads SOG 0–2", insights[1])
    }

    @Test
    fun predictionIsPrioritizedWhenAvailable() {
        val insights = event(
            stats = listOf(
                TeamStatComparison("Win Prob", "37.5%", "62.5%"),
                TeamStatComparison("Spread", "+3.5", "-3.5")
            )
        ).gameInsights()

        assertEquals("HOM win probability 62.5%", insights.first())
        assertTrue(insights.any { it.startsWith("Line ·") })
    }

    @Test
    fun tiedMetricsDoNotClaimALeader() {
        val insights = event(
            stats = listOf(
                TeamStatComparison("Possession", "50", "50"),
                TeamStatComparison("SOG", "3", "3")
            )
        ).gameInsights()

        assertTrue(insights.isEmpty())
    }

    @Test
    fun realWinProbabilityTimelineTakesPriority() {
        val insights = event(
            stats = listOf(TeamStatComparison("Win Prob", "10%", "90%")),
            probability = listOf(WinProbabilityPoint(homeWinPercentage = .63, sequence = 0))
        ).gameInsights()

        assertEquals("HOM win probability 63%", insights.first())
        assertEquals(1, insights.count { it.contains("win probability") })
    }
}
