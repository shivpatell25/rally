package com.shiv.rally.presentation.home

import androidx.lifecycle.ViewModelStore
import com.shiv.rally.data.alerts.GameAlertManager
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.*
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.domain.repository.SportsRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test fun `hero card and league shelf publish one current game snapshot`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val base = SportEvent("game", "Kings vs Avalanche", Team("col", "Colorado Avalanche", "COL"),
                Team("la", "Los Angeles Kings", "LA"), Instant.now(), EventStatus.LIVE,
                scoreHome = 0, scoreAway = 0, sport = "Hockey", league = "NHL", gameStatusDetail = "12:07 - 1st Period")
            val current = base.copy(scoreHome = 1, gameStatusDetail = "9:39 - 1st Period")
            val sports = mockk<SportsRepository>()
            coEvery { sports.getRecentEvents() } returns listOf(base)
            coEvery { sports.getEventSummary(any()) } returns current
            val channels = mockk<IptvRepository>(relaxed = true)
            coEvery { channels.searchChannels(any(), any()) } returns emptyList()
            val prefs = mockk<PreferencesManager>(relaxed = true)
            every { prefs.sportsOrder } returns listOf("NHL")
            every { prefs.enabledLeagues } returns setOf("NHL")
            every { prefs.favoriteTeams } returns emptySet()
            every { prefs.favoriteTeamProfiles } returns emptyList()
            every { prefs.favoriteSports } returns emptySet()
            every { prefs.recentLeagues } returns emptyList()
            every { prefs.savedEventIds } returns emptySet()
            every { prefs.liveGameAlertsEnabled } returns false
            val vm = HomeViewModel(sports, channels, prefs, mockk<GameAlertManager>(relaxed = true))
            store.put("home", vm)
            val state = withContext(Dispatchers.Default) {
                withTimeout(5_000) { vm.uiState.filterIsInstance<HomeUiState.Success>().first() }
            }
            assertEquals(current, state.featuredEvent)
            assertEquals(current, state.liveEvents.single())
            assertEquals(current, state.leagueShelves.single().events.single())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
