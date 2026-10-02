package com.shiv.rally.presentation.player

import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.PlayerStatRow
import com.shiv.rally.domain.model.PlayerStatTable
import com.shiv.rally.domain.model.SportEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlayerStatsDisplayTest {

    @Test
    fun selectsOneUsefulTablePerTeamAndBoundsRows() {
        val sparse = table("away", "AWY", "Passing", listOf(row("1", "Quarterback", listOf(""))))
        val usefulAway = table(
            "away",
            "AWY",
            "Scoring",
            (1..9).map { row("a$it", "Away $it", listOf("$it", "${it + 1}")) }
        )
        val usefulHome = table(
            "home",
            "HME",
            "Scoring",
            listOf(row("h1", "Home One", listOf("20", "8")))
        )

        val result = event(listOf(sparse, usefulAway, usefulHome)).playerTablesForDisplay(rowLimit = 4)

        assertEquals(2, result.size)
        assertEquals("Scoring", result.first().category)
        assertEquals(4, result.first().rows.size)
        assertEquals("HME", result.last().teamAbbreviation)
    }

    @Test
    fun removesMalformedAndDuplicateRows() {
        val source = table(
            "home",
            "HME",
            "Players",
            listOf(
                row("p1", " Player One ", listOf(" 10 ", " 5 ", " 2 ", "extra")),
                row("p1", "Player One duplicate", listOf("1")),
                row(null, "", listOf("4"))
            )
        )

        val result = event(listOf(source)).playerTablesForDisplay()

        assertEquals(1, result.size)
        assertEquals(1, result.single().rows.size)
        assertEquals("Player One", result.single().rows.single().displayName)
        assertEquals(listOf("10", "5", "2"), result.single().rows.single().stats)
        assertTrue(result.single().labels.isNotEmpty())
    }

    @Test fun fullPlayersPreservesEveryAthleteAndCategoryFromBothTeams() {
        val passing = table("away", "AWY", "Passing", (1..25).map { row("$it", "Player $it", listOf("12", "220")) })
        val rushing = table("away", "AWY", "Rushing", listOf(row("1", "Player 1", listOf("4", "20"))))
        val home = table("home", "HME", "Receiving", listOf(row("1", "Home Player", listOf("2", "30"))))
        val teams = event(listOf(passing, rushing, home)).allGamePlayers()
        assertEquals(2, teams.size)
        assertEquals(25, teams.first().players.size)
        assertEquals(2, teams.first().players.first().categories.size)
        assertEquals("Home Player", teams.last().players.single().player.displayName)
    }

    private fun table(
        teamId: String,
        abbreviation: String,
        category: String,
        rows: List<PlayerStatRow>
    ) = PlayerStatTable(
        teamId = teamId,
        teamName = "$abbreviation Team",
        teamAbbreviation = abbreviation,
        category = category,
        labels = listOf("PTS", "REB", "AST"),
        rows = rows
    )

    private fun row(id: String?, name: String, stats: List<String>) = PlayerStatRow(
        athleteId = id,
        displayName = name,
        stats = stats
    )

    private fun event(tables: List<PlayerStatTable>) = SportEvent(
        id = "event",
        name = "Away vs Home",
        homeTeam = null,
        awayTeam = null,
        startTime = Instant.EPOCH,
        status = EventStatus.LIVE,
        sport = "Basketball",
        league = "NBA",
        playerStatTables = tables
    )
}
