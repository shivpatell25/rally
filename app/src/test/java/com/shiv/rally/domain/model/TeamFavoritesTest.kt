package com.shiv.rally.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TeamFavoritesTest {
    private val bears = FavoriteTeam("7", "NFL", "Chicago Bears", "CHI")
    private fun event(league: String) = SportEvent("game", "Game", Team("7", "Home", "H"), Team("9", "Away", "A"), Instant.now(), EventStatus.LIVE, sport = "Sport", league = league)
    @Test fun favoriteTeamIdIsScopedToLeague() {
        assertTrue(event("NFL").matchesFavoriteTeams(listOf(bears)))
        assertFalse(event("NBA").matchesFavoriteTeams(listOf(bears)))
        assertTrue(event("nfl").matchesFavoriteTeams(listOf(bears)))
    }
    @Test fun alertKeysCannotMatchTeamsInAnotherLeague() {
        val keys = setOf(favoriteTeamKey(bears.league, bears.id))
        assertTrue(event("NFL").matchesFavoriteTeamKeys(keys))
        assertFalse(event("NBA").matchesFavoriteTeamKeys(keys))
    }
}
