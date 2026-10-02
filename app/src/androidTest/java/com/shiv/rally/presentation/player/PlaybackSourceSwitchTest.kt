@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.shiv.rally.presentation.player

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.shiv.rally.domain.model.*
import com.shiv.rally.presentation.common.RallyTvBackdrop
import com.shiv.rally.presentation.theme.RallyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.io.File
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList

class PlaybackSourceSwitchTest {
    @get:Rule val compose = createComposeRule()
    private val hls = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
    private val mp4 = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4"
    private fun click(label: String) {
        compose.onAllNodes(hasText(label, ignoreCase = true) and hasClickAction()).onFirst().performClick()
        compose.mainClock.advanceTimeBy(400)
    }

    @Test fun switchingSourcesKeepsRenderingInGameViewAndFullscreen() {
        var source by mutableStateOf(hls)
        var revision by mutableStateOf(1L)
        val rendered = CopyOnWriteArrayList<String>()
        val failures = CopyOnWriteArrayList<String>()
        val event = SportEvent("fixture", "Away vs Home", Team("home", "Home", "H"), Team("away", "Away", "A"),
            Instant.now(), EventStatus.LIVE, scoreHome = 7, scoreAway = 10, sport = "Football", league = "NFL",
            plays = listOf(GamePlay("touchdown", 10, "Passing touchdown", awayScore = 10, homeScore = 7, period = 2, clock = "1:30", isScoringPlay = true)),
            playerStatTables = listOf(PlayerStatTable(teamId = "away", teamName = "Away", teamAbbreviation = "A", category = "Passing", labels = listOf("YDS"), rows = (1..25).map { PlayerStatRow("$it", "Player $it", stats = listOf("$it")) })))
        compose.setContent {
            RallyTheme { RallyTvBackdrop {
                PlayerContent(source, event = event, otherLiveEvents = emptyList(), playbackRequestId = revision,
                    isExternalStream = true, lowLatencyMode = false, audioNormalizationEnabled = false,
                    stremioStreams = listOf(StremioStreamOption("HLS test source", streamUrl = hls), StremioStreamOption("MP4 test source", streamUrl = mp4)),
                    onSwitchStream = { source = it; revision++ }, onBack = {}, onSelectOtherEvent = {},
                    onPlaybackReady = { url, _ -> rendered.add(url) }, onPlaybackFailure = { failures.add(it) })
            } }
        }
        try {
            compose.waitUntil(30_000) { rendered.size == 1 }
        } catch (failure: Throwable) {
            click("Fullscreen")
            click("Diagnostics")
            compose.mainClock.advanceTimeBy(1_000)
            Thread.sleep(1_000)
            compose.onRoot().printToLog("PlaybackSourceSwitchTest")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(instrumentation.targetContext.getExternalFilesDir(null), "source-switch-failure.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            throw AssertionError("No rendered first frame; playback failures: $failures", failure)
        }
        click("Passing touchdown")
        compose.onNodeWithText("Scoring play", substring = true).assertIsDisplayed()
        click("Players")
        compose.onNodeWithText("Player 1").assertIsDisplayed()
        click("Stats")
        for (index in 0..3) {
            if (index == 2) click("Fullscreen")
            click(if (index < 2) "Source" else "Pick Source")
            click(if (index % 2 == 0) "MP4 test source" else "HLS test source")
            compose.waitUntil(30_000) { rendered.size >= index + 2 }
            if (index >= 2) compose.onNodeWithText("Game View", ignoreCase = true).assertIsDisplayed()
            else compose.onNodeWithText("Stats").assertIsDisplayed()
        }
        // Reload the same URL too: URL equality must not suppress a retry.
        click("Pick Source")
        click("HLS test source")
        compose.waitUntil(30_000) { rendered.size >= 6 }
        compose.runOnIdle { assertTrue("Unexpected playback failure: $failures", failures.isEmpty()) }
        click("Game View")
        compose.onNodeWithText("Players").assertIsDisplayed()
    }
}
