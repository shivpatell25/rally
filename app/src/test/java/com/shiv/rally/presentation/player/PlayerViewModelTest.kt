package com.shiv.rally.presentation.player

import androidx.lifecycle.SavedStateHandle
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.EpgProgram
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.MatchResult
import com.shiv.rally.domain.model.RelevantChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.model.Team
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.domain.repository.SportsRepository
import com.shiv.rally.domain.repository.StremioRepository
import com.shiv.rally.domain.usecase.MatcherService
import com.shiv.rally.domain.usecase.SelectBestStreamUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-09-16T18:00:00Z")
    private val event = SportEvent(
        id = "event-1",
        name = "Kansas City Chiefs vs Baltimore Ravens",
        homeTeam = Team("chiefs", "Kansas City Chiefs", "KC"),
        awayTeam = Team("ravens", "Baltimore Ravens", "BAL"),
        startTime = now,
        status = EventStatus.LIVE,
        sport = "Football",
        league = "NFL"
    )
    private val guide = ChannelGuide(now = EpgProgram("Kansas City Chiefs vs Baltimore Ravens"))
    private val channel = IptvChannel("iptv-1", "101", "ESPN HD", "Sports", guide = guide)
    private val addonStream = StremioStreamOption(
        title = "Chiefs vs Ravens 1080p",
        streamUrl = "https://example.test/game.m3u8"
    )

    private val sportsRepository = object : SportsRepository {
        override suspend fun getLiveEvents() = listOf(event)
        override suspend fun getUpcomingEvents() = emptyList<SportEvent>()
        override suspend fun getEventsSnapshot() = listOf(event)
        override suspend fun getEventsByLeague(league: String) = listOf(event)
        override suspend fun getEventById(eventId: String) = event.takeIf { it.id == eventId }
        override suspend fun searchEvents(query: String) = emptyList<SportEvent>()
        override fun observeLiveEvent(eventId: String) = emptyFlow<SportEvent>()
        override suspend fun getTvStationsForEvent(eventId: String) = listOf("ESPN")
        override suspend fun getEventSummary(event: SportEvent) = event
    }

    private val iptvRepository = object : IptvRepository {
        override suspend fun authenticate() = true
        override suspend fun getChannels() = listOf(channel)
        override suspend fun getChannelStreamUrl(channelId: String) = "https://example.test/$channelId.m3u8"
        override suspend fun getChannelGuide(channelId: String) = guide.takeIf { channelId == channel.id }
    }

    private val stremioRepository = object : StremioRepository {
        override suspend fun getStreamsForEvent(event: SportEvent) = listOf(addonStream)
        override suspend fun searchStreams(query: String) = emptyList<StremioStreamOption>()
    }

    private val matcher = object : MatcherService {
        override suspend fun matchEventToChannels(event: SportEvent, channels: List<IptvChannel>) =
            emptyList<MatchResult>()

        override suspend fun getRelevantChannelsForEvent(event: SportEvent, channels: List<IptvChannel>) =
            listOf(RelevantChannel(channel, 98f, "Now playing"))
    }

    private val preferences = mockk<PreferencesManager>(relaxed = true).apply {
        every { macAddress } returns "00:1A:79:00:00:01"
        every { authToken } returns "token"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun switchingFromIptvToStremioKeepsGameViewEvent() = runTest(dispatcher) {
        val viewModel = createViewModel(
            SavedStateHandle(mapOf("channelId" to channel.id, "eventId" to event.id))
        )
        advanceUntilIdle()
        val iptvState = viewModel.uiState.value as PlayerUiState.Success
        assertEquals(event.id, iptvState.event?.id)
        assertEquals(false, iptvState.isExternalStream)

        viewModel.switchStream(addonStream.streamUrl)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerUiState.Success
        assertEquals(event.id, state.event?.id)
        assertEquals(addonStream.streamUrl, state.streamUrl)
        assertEquals(true, state.isExternalStream)
    }

    @Test
    fun directIptvLaunchUsesNowPlayingGuideToRestoreGameViewEvent() = runTest(dispatcher) {
        val viewModel = createViewModel(SavedStateHandle(mapOf("channelId" to channel.id)))
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerUiState.Success
        assertNotNull(state.event)
        assertEquals(event.id, state.event?.id)
    }

    private fun createViewModel(handle: SavedStateHandle): PlayerViewModel = PlayerViewModel(
        savedStateHandle = handle,
        iptvRepository = iptvRepository,
        sportsRepository = sportsRepository,
        matcherService = matcher,
        stremioRepository = stremioRepository,
        preferencesManager = preferences,
        selectBestStream = SelectBestStreamUseCase(iptvRepository, stremioRepository, matcher),
        ioDispatcher = dispatcher
    )
}
