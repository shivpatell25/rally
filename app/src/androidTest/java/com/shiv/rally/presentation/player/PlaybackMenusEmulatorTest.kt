@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.test.ExperimentalTestApi::class)
package com.shiv.rally.presentation.player

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.rally.domain.model.*
import com.shiv.rally.presentation.common.RallyTvBackdrop
import com.shiv.rally.presentation.event.EventContent
import com.shiv.rally.presentation.onboarding.OnboardingScreen
import com.shiv.rally.presentation.theme.RallyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.Instant

class PlaybackMenusEmulatorTest {
    @get:Rule val compose = createComposeRule()
    private val stream = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
    private val event = SportEvent("audit-game", "Chiefs vs Ravens", Team("bal", "Baltimore Ravens", "BAL", "https://a.espncdn.com/i/teamlogos/nfl/500/bal.png"), Team("kc", "Kansas City Chiefs", "KC", "https://a.espncdn.com/i/teamlogos/nfl/500/kc.png"), Instant.now(),
        EventStatus.LIVE, scoreHome = 20, scoreAway = 24, venue = "M&T Bank Stadium", sport = "Football", league = "NFL", gameStatusDetail = "4th · 3:42",
        teamStats = listOf(TeamStatComparison("Total Yards", "382", "355"), TeamStatComparison("First Downs", "24", "28"),
            TeamStatComparison("3rd Down", "6/10", "5/11"), TeamStatComparison("Red Zone", "2/2", "3/4"),
            TeamStatComparison("Turnovers", "1", "2"), TeamStatComparison("Possession", "31:12", "28:48")),
        playerLeaders = listOf(PlayerLeader("Passing", teamAbbr = "KC", playerShortName = "P. Mahomes", statDisplay = "284 pass yds"),
            PlayerLeader("Receiving", teamAbbr = "KC", playerShortName = "T. Kelce", statDisplay = "92 rec yds"),
            PlayerLeader("Rushing", teamAbbr = "KC", playerShortName = "I. Pacheco", statDisplay = "78 rush yds"),
            PlayerLeader("Passing", teamAbbr = "BAL", playerShortName = "L. Jackson", statDisplay = "246 pass yds"),
            PlayerLeader("Receiving", teamAbbr = "BAL", playerShortName = "Z. Flowers", statDisplay = "81 rec yds"),
            PlayerLeader("Rushing", teamAbbr = "BAL", playerShortName = "D. Henry", statDisplay = "104 rush yds")),
        liveStats = mapOf("Current Drive" to "KC · 7 plays · 72 yards", "Drive Yard Line" to "72"),
        plays = (1..6).map { GamePlay("play-$it", it, "Mahomes pass complete to Kelce for ${it * 3} yards", period = 4, clock = "3:42") },
        highlightClips = (1..4).map { HighlightClip("clip-$it", "Key moment $it", durationSeconds = 42, streamUrl = stream) },
        playerStatTables = listOf(PlayerStatTable(teamName = "Chiefs", teamAbbreviation = "KC", category = "passing", labels = listOf("C/ATT", "YDS", "TD", "INT"), rows = (1..25).map { PlayerStatRow(athleteId = "$it", displayName = "Player $it", position = "QB", stats = listOf("21/30", "284", "2", "0")) })))
    private fun click(text: String) { compose.onAllNodesWithText(text, ignoreCase = true, substring = true).onFirst().performClick(); compose.mainClock.advanceTimeBy(400) }
    private fun capture(name: String) {
        compose.waitForIdle(); Thread.sleep(650)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(ctx.getExternalFilesDir(null), "audit-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun playbackControlsStatsTabsAndFullscreenMenus() {
        compose.setContent { RallyTheme { RallyTvBackdrop { PlayerContent(stream, event = event, otherLiveEvents = emptyList(), isExternalStream = true,
            audioNormalizationEnabled = false, onBack = {}, onSelectOtherEvent = {}) } } }
        compose.mainClock.advanceTimeBy(1_000)
        Thread.sleep(5_000)
        capture("game-view")
        compose.onAllNodesWithText("KC").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("BAL").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("24").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("20").onFirst().assertIsDisplayed()
        // D-pad traverses the actual tab focus targets, not just touch actions.
        compose.onNodeWithText("Stats").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Stats").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Plays").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Mahomes pass complete to Kelce for 18 yards").assertIsDisplayed()
        click("Players")
        compose.onNodeWithText("Players").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        repeat(25) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }; compose.mainClock.advanceTimeBy(150) }
        compose.onNodeWithText("Player 25").assertIsDisplayed()
        capture("game-view-players")
        click("Sources")
        capture("game-view-sources")
        click("Stats")
        // The fixture is an actual finite sports clip; replay it if it ended
        // while the 25-player remote navigation check was running.
        if (compose.onAllNodesWithText("Pause", ignoreCase = true).fetchSemanticsNodes().isEmpty()) click("Restart")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Pause", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        click("Pause")
        compose.onNodeWithText("Play", ignoreCase = true).assertIsDisplayed()
        click("Play")
        compose.onNodeWithText("Restart", ignoreCase = true).assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Pause", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pause", ignoreCase = true).assertIsDisplayed()
        click("Fullscreen")
        compose.onNodeWithText("Game View", ignoreCase = true).assertIsDisplayed()
        capture("fullscreen-menu")
        click("Diagnostics")
        compose.onNodeWithText("PLAYBACK DIAGNOSTICS").assertIsDisplayed()
        capture("playback-diagnostics")
        click("Done")
        click("Audio")
        capture("audio-picker")
        click("Close")
        if (!compose.onNodeWithText("Captions", ignoreCase = true).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)) {
            click("Captions")
            compose.onAllNodesWithText("Captions").onLast().assertIsDisplayed()
            capture("caption-picker")
            click("Close")
        } else compose.onNodeWithText("Captions", ignoreCase = true).assertIsNotEnabled()
        click("Pick Source")
        capture("player-source-picker")
        click("Close")
        click("Game View")
        compose.onNodeWithText("Stats").assertIsDisplayed()
    }
    @Test fun eventTabsAndWatchlistActions() {
        var saves = 0
        var playedHighlight: Pair<String, String>? = null
        val clip = HighlightClip(id = "audit-clip", title = "Game-winning goal", streamUrl = stream)
        compose.setContent { RallyTheme { RallyTvBackdrop { EventContent(event.copy(highlightClips = listOf(clip)), stream, emptyList(), emptyList(), emptyList(),
            onWatchLive = {}, onBack = {}, onToggleEventWatchlist = { saves++ },
            onPlayHighlight = { target, title -> playedHighlight = target to title }) } } }
        compose.mainClock.advanceTimeBy(600)
        capture("event-overview")
        listOf("Stats", "Lineups", "Plays", "Sources", "Highlights").forEach { tab -> click(tab); capture("event-${tab.lowercase()}") }
        click(clip.title)
        compose.runOnIdle { assertEquals(stream to clip.title, playedHighlight) }
        click("Overview")
        click("Add to Watchlist")
        compose.runOnIdle { assertEquals(1, saves) }
    }
    @Test fun onboardingContinue() {
        var continued = false
        compose.setContent { RallyTheme { RallyTvBackdrop { OnboardingScreen { continued = true } } } }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Continue to setup").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertTrue(continued) }
        capture("onboarding")
    }
}
