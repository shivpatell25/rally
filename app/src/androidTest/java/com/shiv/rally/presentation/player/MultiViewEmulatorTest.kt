@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.test.ExperimentalTestApi::class)

package com.shiv.rally.presentation.player

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.*
import com.shiv.rally.domain.repository.*
import com.shiv.rally.domain.usecase.*
import com.shiv.rally.presentation.common.RallyTvBackdrop
import com.shiv.rally.presentation.theme.RallyTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Test-only repositories exercise real playback and TV focus without changing
 * a user's configured provider or shipping sample games in the application. */
class MultiViewEmulatorTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var vm: MultiViewViewModel
    private val stream = "https://cmp-espn.media.dssott.com/opp/hls/espn/wsc/2026/0930/0eedac50-34a5-4a33-933b-afd99c16a5aa/0eedac50-34a5-4a33-933b-afd99c16a5aa/playlist.m3u8"
    private fun game(id: String) = SportEvent(id, "Chiefs vs Ravens", Team("kc", "Chiefs", "KC"), Team("bal", "Ravens", "BAL"),
        LocalDate.now(redZoneTimeZone).atTime(13, 0).atZone(redZoneTimeZone).toInstant(), EventStatus.LIVE,
        scoreHome = 24, scoreAway = 20, sport = "Football", league = "NFL", gameStatusDetail = "4th · 3:42",
        playerStatTables = listOf(PlayerStatTable(teamName = "Chiefs", teamAbbreviation = "KC", category = "passing", labels = listOf("C/ATT", "YDS", "TD", "INT"),
            rows = listOf(PlayerStatRow(athleteId = "15", displayName = "Test Quarterback", position = "QB", stats = listOf("21/30", "284", "2", "0"))))))
    private fun start() {
        val games = listOf(game("one"), game("two"))
        val sports = object : SportsRepository {
            override suspend fun getLiveEvents() = games
            override suspend fun getUpcomingEvents() = emptyList<SportEvent>()
            override suspend fun getEventsByLeague(league: String) = games
            override suspend fun getEventsForDate(league: String, date: LocalDate) = games + game("redzone-extra")
            override suspend fun getEventById(eventId: String) = games.find { it.id == eventId }
            override suspend fun searchEvents(query: String) = games
            override fun observeLiveEvent(eventId: String) = emptyFlow<SportEvent>()
            override suspend fun getTvStationsForEvent(eventId: String) = emptyList<String>()
            override suspend fun getEventSummary(event: SportEvent) = event
        }
        val channels = object : IptvRepository {
            override suspend fun authenticate() = true
            override suspend fun getChannels() = emptyList<IptvChannel>()
            override suspend fun getChannelStreamUrl(channelId: String) = stream
        }
        val addons = object : StremioRepository {
            override suspend fun getStreamsForEvent(event: SportEvent) = listOf(StremioStreamOption("Test broadcast", streamUrl = stream))
            override suspend fun searchStreams(query: String) = emptyList<StremioStreamOption>()
        }
        val matcher = object : MatcherService {
            override suspend fun matchEventToChannels(event: SportEvent, channels: List<IptvChannel>) = emptyList<MatchResult>()
            override suspend fun getRelevantChannelsForEvent(event: SportEvent, channels: List<IptvChannel>) = emptyList<RelevantChannel>()
        }
        compose.setContent {
            vm = androidx.compose.runtime.remember {
                MultiViewViewModel(SavedStateHandle(mapOf("eventIds" to "one,two")), sports, channels,
                    SelectBestStreamUseCase(channels, addons, matcher), PreferencesManager(InstrumentationRegistry.getInstrumentation().targetContext))
                    .also { store.put("multiview", it) }
            }
            RallyTheme { RallyTvBackdrop { MultiViewScreen(vm, { _, _ -> }, {}) } }
        }
        compose.waitUntil(15_000) { vm.uiState.value.slots.size == 2 && vm.uiState.value.slots.none { it.isLoading } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.waitUntil(30_000) { compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing")).fetchSemanticsNodes().size == 2 }
    }
    private fun capture(name: String) {
        Thread.sleep(700)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "audit-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @After fun finish() { compose.runOnIdle { store.clear() } }

    @Test fun focusAudioAndStatsTile() {
        start()
        compose.onNodeWithContentDescription("Stream 1: BAL vs KC").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("Stream 1: BAL vs KC").assertIsFocused()
        compose.onNodeWithContentDescription("Stream 1: BAL vs KC").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(1, vm.uiState.value.audioSlotIndex) }
        compose.onNodeWithText("Audio: Focus", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Stream 1: BAL vs KC").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("Stream 1: BAL vs KC").assertIsFocused()
        compose.runOnIdle { assertEquals(0, vm.uiState.value.focusedSlotIndex); assertEquals(1, vm.uiState.value.audioSlotIndex); assertFalse(vm.uiState.value.audioFollowsFocus) }
        compose.onNodeWithText("Audio: Pinned").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(0, vm.uiState.value.audioSlotIndex); assertTrue(vm.uiState.value.audioFollowsFocus) }
        compose.onNodeWithText("Layout · 50/50", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(MultiViewLayoutMode.DUAL_FOCUS, vm.uiState.value.layoutMode) }
        compose.onNodeWithText("Layout · 70/30", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Compare", useUnmergedTree = true).performClick()
        compose.onNodeWithText("GAME COMPARISON").assertIsDisplayed()
        compose.onAllNodesWithText("Done", useUnmergedTree = true).onLast().performClick()
        compose.onNodeWithContentDescription("Stream 1: BAL vs KC").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Change Source").performScrollTo().performClick()
        compose.waitUntil(10_000) { !vm.uiState.value.sourcePickerLoading }
        compose.onNodeWithText("Choose a broadcast").assertIsDisplayed()
        capture("multiview-sources")
        compose.onNodeWithText("Close", ignoreCase = true).performClick()
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Add Player Stats", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { !vm.uiState.value.statsLoading && vm.uiState.value.statsGames.size == 2 }
        compose.onNodeWithText("PLAYER STATS").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, vm.uiState.value.audioSlotIndex) }
        capture("multiview-stats")
        compose.onAllNodesWithText("Test Quarterback").onFirst().performClick()
        compose.onNodeWithText("YDS").assertIsDisplayed()
        compose.onNodeWithText("284").assertIsDisplayed()
        capture("multiview-player-detail")
    }

    @Test fun immersiveGridAndRedZoneDeduplication() {
        start()
        compose.runOnIdle {
            vm.addSlotFromChannel(IptvChannel("redzone", "1", "NFL RedZone HD", "Sports", stream))
            vm.addStatsTile()
        }
        compose.waitUntil(10_000) { !vm.uiState.value.statsLoading && vm.uiState.value.statsGames.size == 3 }
        compose.runOnIdle { assertEquals(listOf("one", "two", "redzone-extra"), vm.uiState.value.statsGames.map { it.id }) }
        compose.onNodeWithText("Immersive", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        capture("multiview-immersive-stats")
        compose.runOnIdle { vm.removeStatsTile(); vm.addSlotFromEvent(game("one")) }
        compose.waitUntil(10_000) { vm.uiState.value.slots.size == 4 && vm.uiState.value.slots.none { it.isLoading } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil(30_000) { compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing")).fetchSemanticsNodes().size == 4 }
        capture("multiview-immersive-four")
        compose.onNodeWithContentDescription("Stream 1: BAL vs KC").performClick()
        compose.onNodeWithText("Watch Full Screen", useUnmergedTree = true).assertIsDisplayed()
        capture("multiview-actions")
        compose.onNodeWithText("Watch Full Screen").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        var steps = 0
        while (compose.onAllNodes(hasText("Cancel") and isFocused()).fetchSemanticsNodes().isEmpty() && steps++ < 12) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            Thread.sleep(100)
            compose.mainClock.advanceTimeBy(250)
        }
        compose.onNodeWithText("Cancel").assertIsFocused().assertIsDisplayed()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Remove from Multi-View").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(3, vm.uiState.value.slots.size) }
    }
}
