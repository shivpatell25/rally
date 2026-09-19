package com.shiv.rally.presentation.home

import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LivingHeroTest {
    @Test
    fun `basketball stays editorially close within eight points`() {
        assertTrue(isEditoriallyClose(event("Basketball", "NBA", 101, 108)))
        assertFalse(isEditoriallyClose(event("Basketball", "NBA", 90, 110)))
    }

    @Test
    fun `low scoring sports use a two point threshold`() {
        assertTrue(isEditoriallyClose(event("Soccer", "EPL", 1, 2)))
        assertFalse(isEditoriallyClose(event("Soccer", "EPL", 0, 3)))
    }

    private fun event(sport: String, league: String, away: Int, home: Int) = SportEvent(
        id = "$league-$away-$home",
        name = "Away at Home",
        homeTeam = null,
        awayTeam = null,
        startTime = Instant.EPOCH,
        status = EventStatus.LIVE,
        scoreHome = home,
        scoreAway = away,
        sport = sport,
        league = league
    )
}
