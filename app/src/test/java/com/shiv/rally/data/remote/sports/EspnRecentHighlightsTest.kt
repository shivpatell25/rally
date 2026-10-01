package com.shiv.rally.data.remote.sports

import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.EventStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class EspnRecentHighlightsTest {
    @Test
    fun `recent clips retain finished games older than the live scoreboard window`() = runTest {
        val api = mockk<EspnApi>()
        val preferences = mockk<PreferencesManager>(relaxed = true)
        every { preferences.enabledLeagues } returns setOf("MLB")
        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
        val games = (1..8).map { event(it.toString(), "post") } + event("upcoming", "pre")
        coEvery { api.getScoreboard("baseball", "mlb", yesterday, 40) } returns EspnScoreboardResponse(games)
        val repository = EspnRepositoryImpl(api, preferences, mockk(), backgroundScope)

        val recent = repository.getRecentCompletedEvents()

        assertEquals(8, recent.size)
        assertTrue(recent.all { it.status == EventStatus.FINISHED })
        assertTrue(recent.all { it.startTime.isBefore(Instant.now().minusSeconds(12 * 3600)) })
        assertEquals(recent, repository.getRecentCompletedEvents())
        coVerify(exactly = 1) { api.getScoreboard(any(), any(), any(), any()) }
    }

    private fun event(id: String, state: String) = EspnEvent(
        id = id,
        date = Instant.now().minusSeconds(48 * 3600).toString(),
        name = "Away at Home",
        shortName = "AWY @ HME",
        competitions = listOf(EspnCompetition(
            status = EspnStatus(EspnStatusType(
                name = if (state == "post") "STATUS_FINAL" else "STATUS_SCHEDULED",
                state = state, completed = state == "post", description = null
            )),
            competitors = listOf(
                EspnCompetitor("away", EspnTeam("away", "Away", "Away", "AWY", null), "2"),
                EspnCompetitor("home", EspnTeam("home", "Home", "Home", "HME", null), "3")
            ),
            broadcasts = emptyList()
        ))
    )
}
