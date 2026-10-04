@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.shiv.rally.presentation.player

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.rally.presentation.common.RallyTvBackdrop
import com.shiv.rally.presentation.theme.RallyTheme
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

class PlayerForegroundRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var server: MultiViewFaultServer
    private var mounted by mutableStateOf(true)
    @After fun close() {
        compose.runOnIdle { mounted = false }
        compose.waitForIdle()
        if (::server.isInitialized) server.close()
    }
    private fun click(label: String) {
        compose.onAllNodes(hasText(label, ignoreCase = true) and hasClickAction()).onFirst().performClick()
    }
    private fun frames(): Int = compose.onAllNodes(SemanticsMatcher("numeric text") {
        it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { text -> text.text.toIntOrNull() != null }
    }, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.config
        ?.getOrElse(SemanticsProperties.Text) { emptyList() }?.firstOrNull()?.text?.toIntOrNull() ?: 0

    @Test fun playerRestartsAfterBackgroundAndPreservesUserPause() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        server = MultiViewFaultServer(emptyList(), instrumentation.context.assets.open("multiview/segment.ts").use { it.readBytes() })
        val ready = CopyOnWriteArrayList<String>()
        val failures = CopyOnWriteArrayList<String>()
        compose.setContent {
            if (mounted) RallyTheme { RallyTvBackdrop {
                PlayerContent("${server.base}/play/addon/game1/1/playlist.m3u8", isExternalStream = true, event = null, otherLiveEvents = emptyList(), onSelectOtherEvent = {},
                    streamHeaders = mapOf("Referer" to "${server.base}/watch", "User-Agent" to "RallyMultiviewQA"),
                    lowLatencyMode = false, audioNormalizationEnabled = false,
                    onBack = {}, onPlaybackReady = { url, _ -> ready.add(url) }, onPlaybackFailure = { failures.add(it) })
            } }
        }
        compose.waitUntil(30_000) { ready.isNotEmpty() }
        click("Diagnostics")
        compose.waitUntil(10_000) { frames() > 0 }
        repeat(3) {
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(400)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(2_000)
            compose.waitUntil(15_000) { frames() > 0 }
            val initial = frames()
            compose.waitUntil(10_000) { frames() > initial + 10 }
        }
        click("Done")
        click("Pause")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(400)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Play", ignoreCase = true).assertIsDisplayed()
        click("Play")
        click("Diagnostics")
        compose.waitUntil(15_000) { frames() > 0 }
        val initial = frames()
        compose.waitUntil(10_000) { frames() > initial + 10 }
        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(0, server.badHeaders.get())
    }
}
