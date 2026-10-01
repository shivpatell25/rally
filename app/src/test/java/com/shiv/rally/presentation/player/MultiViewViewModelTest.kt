package com.shiv.rally.presentation.player

import androidx.lifecycle.SavedStateHandle
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.*
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MultiViewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockPreferencesManager = mockk<PreferencesManager>(relaxed = true).apply {
        every { macAddress } returns "00:1A:79:AA:BB:CC"
        every { authToken } returns "fake_token"
    }

    private val fakeEvent1 = SportEvent(
        id = "event_1",
        name = "Chiefs vs Ravens",
        homeTeam = Team("1", "Kansas City Chiefs", "KC"),
        awayTeam = Team("2", "Baltimore Ravens", "BAL"),
        startTime = Instant.now(),
        status = EventStatus.LIVE,
        sport = "Football",
        league = "NFL"
    )

    private val fakeEvent2 = SportEvent(
        id = "event_2",
        name = "Celtics vs Lakers",
        homeTeam = Team("3", "Los Angeles Lakers", "LAL"),
        awayTeam = Team("4", "Boston Celtics", "BOS"),
        startTime = Instant.now(),
        status = EventStatus.LIVE,
        sport = "Basketball",
        league = "NBA"
    )

    private val fakeChannel1 = IptvChannel(
        id = "chan_1",
        number = "101",
        name = "Chiefs vs Ravens ESPN HD",
        category = "Sports",
        streamUrl = "http://fake.stream/espn.m3u8"
    )

    private val fakeSportsRepository = object : SportsRepository {
        override suspend fun getLiveEvents(): List<SportEvent> = listOf(fakeEvent1, fakeEvent2)
        override suspend fun getUpcomingEvents(): List<SportEvent> = emptyList()
        override suspend fun getEventsByLeague(league: String): List<SportEvent> = emptyList()
        override suspend fun getEventById(eventId: String): SportEvent? = when (eventId) {
            "event_1" -> fakeEvent1
            "event_2" -> fakeEvent2
            else -> null
        }
        override suspend fun searchEvents(query: String): List<SportEvent> = emptyList()
        override fun observeLiveEvent(eventId: String) = emptyFlow<SportEvent>()
        override suspend fun getTvStationsForEvent(eventId: String): List<String> = emptyList()
        override suspend fun getEventSummary(event: SportEvent): SportEvent = event
    }

    private val fakeIptvRepository = object : IptvRepository {
        override suspend fun authenticate(): Boolean = true
        override suspend fun getChannels(): List<IptvChannel> = listOf(fakeChannel1)
        override suspend fun getChannelStreamUrl(channelId: String): String = "http://fake.stream/$channelId.m3u8"
    }

    private val fakeStremioRepository = object : StremioRepository {
        override suspend fun getStreamsForEvent(event: SportEvent): List<StremioStreamOption> = emptyList()
        override suspend fun searchStreams(query: String): List<StremioStreamOption> = emptyList()
    }

    private val fakeMatcherService = object : MatcherService {
        override suspend fun matchEventToChannels(event: SportEvent, channels: List<IptvChannel>): List<MatchResult> = emptyList()
        override suspend fun getRelevantChannelsForEvent(event: SportEvent, channels: List<IptvChannel>): List<RelevantChannel> {
            val matchupChannel = fakeChannel1.copy(
                name = "${event.awayTeam?.name} vs ${event.homeTeam?.name} ESPN HD"
            )
            return listOf(RelevantChannel(matchupChannel, 98f, "Game Matchup"))
        }
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): MultiViewViewModel {
        return MultiViewViewModel(
            savedStateHandle = savedStateHandle,
            sportsRepository = fakeSportsRepository,
            iptvRepository = fakeIptvRepository,
            selectBestStream = SelectBestStreamUseCase(fakeIptvRepository, fakeStremioRepository, fakeMatcherService),
            preferencesManager = mockPreferencesManager,
            ioDispatcher = testDispatcher
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitializationWithMultipleEventIds_loadsAllSlots() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("eventIds" to "event_1,event_2"))
        val viewModel = createViewModel(savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.slots.size)
        assertEquals("event_1", state.slots[0].event?.id)
        assertEquals("event_2", state.slots[1].event?.id)
        assertEquals(0, state.focusedSlotIndex)
        assertEquals("http://fake.stream/chan_1.m3u8", state.slots[0].streamUrl)
    }

    @Test
    fun testInitializationWithSingleChannel_promptsPickerForSecondGame() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("channelId" to "chan_1"))
        val viewModel = createViewModel(savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.slots.size)
        assertTrue(state.isPickerOpen) // Opens picker to prompt adding game 2
        assertNull(state.pickerTargetSlotIndex)
    }

    @Test fun `a matchup channel contributes its game once to the stats tile`() = runTest(testDispatcher) {
        val vm = createViewModel(SavedStateHandle(mapOf("eventIds" to "event_1,event_2")))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.addSlotFromChannel(fakeChannel1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("event_1", vm.uiState.value.slots.last().event?.id)
        vm.addStatsTile()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("event_1", "event_2"), vm.uiState.value.statsGames.map { it.id })
    }

    @Test
    fun testAddSlotFromEvent_appendsSlotAndUpdatesFocus() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addSlotFromEvent(fakeEvent2)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.slots.any { it.event?.id == "event_2" })
    }

    @Test
    fun testSetFocusedSlot_updatesFocusIndex() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("eventIds" to "event_1,event_2"))
        val viewModel = createViewModel(savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFocusedSlot(1)
        assertEquals(1, viewModel.uiState.value.focusedSlotIndex)
        assertEquals(1, viewModel.uiState.value.audioSlotIndex)
    }

    @Test fun pinnedAudioSurvivesFocusAndPositionChanges() = runTest(testDispatcher) {
        val vm = createViewModel(SavedStateHandle(mapOf("eventIds" to "event_1,event_2")))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.setAudioSlot(0)
        vm.setFocusedSlot(1)
        assertEquals(0, vm.uiState.value.audioSlotIndex)
        vm.swapSlot(0)
        assertEquals("event_1", vm.uiState.value.slots[vm.uiState.value.audioSlotIndex].event?.id)
        vm.toggleAudioFollowsFocus()
        assertEquals(vm.uiState.value.focusedSlotIndex, vm.uiState.value.audioSlotIndex)
    }

    @Test fun statsTileOccupiesOneSlotAndDoesNotTakeOverAudio() = runTest(testDispatcher) {
        val vm = createViewModel(SavedStateHandle(mapOf("eventIds" to "event_1,event_2")))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.setFocusedSlot(1)
        vm.addStatsTile()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.statsTileEnabled)
        assertEquals(2, vm.uiState.value.focusedSlotIndex)
        assertEquals(1, vm.uiState.value.audioSlotIndex)
        assertEquals(2, vm.uiState.value.statsGames.size)
        vm.addSlotFromChannel(fakeChannel1)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.addSlotFromChannel(fakeChannel1.copy(id = "extra"))
        assertEquals(3, vm.uiState.value.slots.size)
        vm.removeStatsTile()
        assertFalse(vm.uiState.value.statsTileEnabled)
    }

    @Test fun duplicateEventStreamsProduceOneStatsGame() = runTest(testDispatcher) {
        val vm = createViewModel(SavedStateHandle(mapOf("eventIds" to "event_1")))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.addSlotFromEvent(fakeEvent1)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.addStatsTile()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.slots.size)
        assertEquals(listOf("event_1"), vm.uiState.value.statsGames.map { it.id })
    }

    @Test fun statsCanReplaceStreamWhenAllFourTilesAreOccupied() = runTest(testDispatcher) {
        val vm = createViewModel(SavedStateHandle(mapOf("eventIds" to "event_1,event_2")))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.addSlotFromChannel(fakeChannel1)
        vm.addSlotFromChannel(fakeChannel1.copy(id = "extra"))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.openPickerForSwap(3)
        vm.addStatsTile()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, vm.uiState.value.slots.size)
        assertTrue(vm.uiState.value.statsTileEnabled)
        assertFalse(vm.uiState.value.isPickerOpen)
    }

    @Test
    fun testRemoveSlot_adjustsSlotsAndClampsFocus() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("eventIds" to "event_1,event_2"))
        val viewModel = createViewModel(savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFocusedSlot(1)
        viewModel.removeSlot(1)

        val state = viewModel.uiState.value
        assertEquals(1, state.slots.size)
        assertEquals(0, state.focusedSlotIndex)
        assertEquals("event_1", state.slots[0].event?.id)
    }

    @Test
    fun testSetLayoutMode() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle)

        viewModel.setLayoutMode(MultiViewLayoutMode.QUAD_GRID)
        assertEquals(MultiViewLayoutMode.QUAD_GRID, viewModel.uiState.value.layoutMode)
    }

    @Test
    fun retryKeepsTheSelectedSourceInsteadOfRunningAutoSelectionAgain() = runTest(testDispatcher) {
        val viewModel = createViewModel(SavedStateHandle(mapOf("eventIds" to "event_1")))
        testDispatcher.scheduler.advanceUntilIdle()

        val before = viewModel.uiState.value.slots.single()
        assertEquals("chan_1", before.sourcePlaybackTarget)
        assertEquals("iptv:chan_1", before.selectedSourceId)

        viewModel.retrySlot(0)
        testDispatcher.scheduler.advanceUntilIdle()

        val after = viewModel.uiState.value.slots.single()
        assertEquals(before.sourcePlaybackTarget, after.sourcePlaybackTarget)
        assertEquals(before.selectedSourceId, after.selectedSourceId)
        assertEquals(before.playbackRevision + 1, after.playbackRevision)
        assertEquals("http://fake.stream/chan_1.m3u8", after.streamUrl)
    }
}
