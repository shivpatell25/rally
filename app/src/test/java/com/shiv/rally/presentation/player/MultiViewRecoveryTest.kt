package com.shiv.rally.presentation.player

import androidx.lifecycle.SavedStateHandle
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.*
import com.shiv.rally.domain.repository.*
import com.shiv.rally.domain.usecase.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MultiViewRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private val event = SportEvent("game", "Chiefs vs Ravens", Team("kc", "Chiefs", "KC"),
        Team("bal", "Ravens", "BAL"), Instant.now(), EventStatus.LIVE, sport = "Football", league = "NFL")
    private val channel = IptvChannel("portal:1", "1", "Chiefs vs Ravens", "Sports", "ffrt http://localhost/ch/1")
    private val preferences = mockk<PreferencesManager>(relaxed = true).apply {
        every { macAddress } returns "00:1A:79:AA:BB:CC"
        every { authToken } returns "current-session"
    }
    private val sports = mockk<SportsRepository>(relaxed = true).apply {
        coEvery { getLiveEvents() } returns listOf(event)
        coEvery { getEventSummary(any()) } answers { firstArg() }
    }
    private val iptv = mockk<IptvRepository>(relaxed = true)
    private val addons = mockk<StremioRepository>(relaxed = true)
    private val matcher = mockk<MatcherService>(relaxed = true)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { iptv.getChannels() } returns emptyList()
        coEvery { matcher.getRelevantChannelsForEvent(any(), any()) } returns emptyList()
        coEvery { addons.getStreamsForEvent(any()) } returns listOf(stream("old"))
        coEvery { addons.refreshStreamsForEvent(any()) } returns listOf(stream("fresh"))
    }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun stream(token: String) = StremioStreamOption("Sports Streams · game", streamUrl = "https://stream.example/live.m3u8?token=$token",
        headers = mapOf("Referer" to "https://sports.example/", "Authorization" to token))
    private fun vm() = MultiViewViewModel(SavedStateHandle(mapOf("eventIds" to "game")), sports, iptv,
        SelectBestStreamUseCase(iptv, addons, matcher), preferences, dispatcher)

    @Test fun `addon recovery fetches fresh signed URL and headers without touching another tile`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.addSlotFromEvent(event.copy(id = "other"))
        dispatcher.scheduler.advanceUntilIdle()
        val old = vm.uiState.value.slots[0]
        val other = vm.uiState.value.slots[1]
        vm.recoverPlayback(old.slotId, old.playbackRevision, "expired")
        dispatcher.scheduler.advanceUntilIdle()
        val recovered = vm.uiState.value.slots[0]
        assertTrue(recovered.streamUrl.endsWith("token=fresh"))
        assertEquals("fresh", recovered.streamHeaders?.get("Authorization"))
        assertEquals(1, recovered.recoveryAttempt)
        assertEquals(old.playbackRevision + 1, recovered.playbackRevision)
        assertEquals(other, vm.uiState.value.slots[1])
        coVerify(exactly = 1) { addons.refreshStreamsForEvent(event) }
    }

    @Test fun `portal recovery renegotiates channel with current session instead of reusing resolved URL`() = runTest(dispatcher) {
        coEvery { iptv.getChannels() } returns listOf(channel)
        coEvery { matcher.getRelevantChannelsForEvent(any(), any()) } returns listOf(RelevantChannel(channel, 99f))
        coEvery { addons.getStreamsForEvent(any()) } returns emptyList()
        var links = 0
        coEvery { iptv.getChannelStreamUrl(channel.id) } answers { "https://portal.example/live?session=${++links}" }
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        val slot = vm.uiState.value.slots.single()
        vm.recoverPlayback(slot.slotId, slot.playbackRevision, "network reset")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("https://portal.example/live?session=2", vm.uiState.value.slots.single().streamUrl)
        assertEquals("current-session", vm.uiState.value.token)
        assertEquals(2, links)
    }

    @Test fun `repeated errors cannot create parallel recovery jobs or unlimited retry loops`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        repeat(4) {
            val slot = vm.uiState.value.slots.single()
            repeat(10) { vm.recoverPlayback(slot.slotId, slot.playbackRevision, "broken") }
            dispatcher.scheduler.advanceUntilIdle()
        }
        assertEquals(3, vm.uiState.value.slots.single().recoveryAttempt)
        assertTrue(vm.uiState.value.slots.single().error!!.contains("choose another"))
        coVerify(exactly = 3) { addons.refreshStreamsForEvent(any()) }
        vm.retrySlot(0)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.slots.single().recoveryAttempt)
    }

    @Test fun `source refresh failures exhaust their budget without leaving a permanently loading tile`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        coEvery { addons.refreshStreamsForEvent(any()) } throws IllegalStateException("provider offline")
        val slot = vm.uiState.value.slots.single()
        vm.recoverPlayback(slot.slotId, slot.playbackRevision, "expired")
        dispatcher.scheduler.advanceUntilIdle()
        val failed = vm.uiState.value.slots.single()
        assertEquals(3, failed.recoveryAttempt)
        assertFalse(failed.isLoading)
        assertNotNull(failed.error)
        coVerify(exactly = 3) { addons.refreshStreamsForEvent(any()) }
    }

    @Test fun `stale decoder errors do not overwrite a newly selected source`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        val old = vm.uiState.value.slots.single()
        vm.retrySlot(0)
        dispatcher.scheduler.advanceUntilIdle()
        val current = vm.uiState.value.slots.single()
        vm.recoverPlayback(old.slotId, old.playbackRevision, "late error")
        vm.reportHealthyPlayback(old.slotId, old.playbackRevision)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(current, vm.uiState.value.slots.single())
    }

    @Test fun `removal cancels pending recovery and cannot resurrect the deleted tile`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        val slot = vm.uiState.value.slots.single()
        vm.recoverPlayback(slot.slotId, slot.playbackRevision, "broken")
        vm.removeSlot(0)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.slots.isEmpty())
        coVerify(exactly = 0) { addons.refreshStreamsForEvent(any()) }
    }

    @Test fun `actual healthy playback resets retry budget`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        val old = vm.uiState.value.slots.single()
        vm.recoverPlayback(old.slotId, old.playbackRevision, "broken")
        dispatcher.scheduler.advanceUntilIdle()
        val current = vm.uiState.value.slots.single()
        vm.reportHealthyPlayback(current.slotId, current.playbackRevision)
        assertEquals(0, vm.uiState.value.slots.single().recoveryAttempt)
    }
}
