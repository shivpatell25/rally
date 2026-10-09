@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package com.shiv.rally.presentation

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.rally.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Walks the shipping navigation with the emulator's existing configuration.
 * Does not save settings, change subscriptions or clear app data. */
class AppAuditEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitText(text: String, timeout: Long = 30_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun nav(text: String) {
        compose.onAllNodesWithText(text).onFirst().performClick()
        compose.mainClock.advanceTimeBy(500)
        Thread.sleep(450)
    }
    private fun capture(name: String) {
        compose.waitForIdle(); Thread.sleep(750)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "audit-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun navigationHomeGuideAndSettings() {
        waitText("Starting Soon", 60_000)
        Thread.sleep(4_000)
        capture("home-top")
        // Starting Soon remains on the first composition; only moving past its
        // fourth event opens the guide composition.
        val hero = compose.onNodeWithText("More Info", ignoreCase = true)
        if (compose.onAllNodesWithText("More Info", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()) {
            // Focus the preview directly, then send an actual remote Down.
            // This avoids touch mode affecting the preceding hero/rail steps.
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP)
            compose.waitForIdle()
            compose.onAllNodes(hasClickAction() and hasText("vs", substring = true)).onLast()
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onAllNodes(hasClickAction() and hasText("vs", substring = true)).onLast().assertIsFocused()
            compose.onNodeWithText("Starting Soon", ignoreCase = true).assertIsDisplayed()
            Thread.sleep(100) // MainActivity coalesces directional bursts below 40 ms.
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            Thread.sleep(350)
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            compose.onNodeWithText("Starting Soon", ignoreCase = true).assertDoesNotExist()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("BROWSE BY SPORT").assertIsDisplayed()
            capture("home-guide")
        }
        nav("Home")
        waitText("Starting Soon")
        nav("Live")
        waitText("Live Now")
        capture("live-games")
        compose.onAllNodesWithText("Browse Live TV", ignoreCase = true).onFirst().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Search channels").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Live TV is unavailable").fetchSemanticsNodes().isNotEmpty() }
        capture("live-tv")
        nav("Schedule")
        waitText("SCHEDULE")
        waitText("All leagues", 60_000)
        compose.onAllNodesWithText("Upcoming", ignoreCase = true).onFirst().performClick()
        compose.onNodeWithText("All", ignoreCase = true).performClick()
        capture("schedule")
        nav("Leagues")
        waitText("BROWSE / LEAGUES")
        waitText("NFL", 60_000)
        capture("leagues")
        compose.onNodeWithText("NFL").performClick()
        waitText("LEAGUE / NFL", 60_000)
        capture("league-games")
        if (compose.onAllNodesWithText("Standings", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Standings", ignoreCase = true).performClick()
            capture("league-standings")
        }
        nav("Highlights")
        waitText("The biggest moments")
        Thread.sleep(4_000)
        capture("highlights")
        val focusedClip = isFocused() and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
        if (compose.onAllNodes(focusedClip).fetchSemanticsNodes().isNotEmpty()) {
            val title = compose.onNode(focusedClip).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
            compose.onNode(focusedClip).performClick()
            waitText("RECENT HIGHLIGHTS", 30_000)
            compose.onNodeWithText(title).assertIsDisplayed()
            capture("highlight-player-title")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            // Back closes a visible player overlay before leaving playback.
            if (compose.onAllNodesWithText("The biggest moments").fetchSemanticsNodes().isEmpty()) {
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            }
            waitText("The biggest moments")
        }
        nav("My Rally")
        waitText("Your teams and games", 60_000)
        capture("my-rally")
        if (compose.onAllNodesWithText("Bears").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Bears").performClick()
            waitText("Chicago Bears", 60_000)
            capture("team-overview")
            listOf("Games", "Roster", "Injuries").forEach { tab ->
                if (compose.onAllNodesWithText(tab, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithText(tab, ignoreCase = true).performClick(); capture("team-${tab.lowercase()}")
                }
            }
        }
        compose.onNodeWithContentDescription("Search").performClick()
        waitText("Find your game.")
        compose.onNode(hasSetTextAction()).performTextInput("college")
        waitText("LEAGUES", 30_000)
        capture("search")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitText("Make Rally yours")
        listOf("Sources", "Playback", "Appearance & Accessibility", "Personalization", "Notifications", "About Rally").forEach { category ->
            // Activate the sidebar, then verify its detail panel. Presence of a
            // sidebar label alone does not prove that the destination works.
            compose.onAllNodesWithText(category).onFirst().performScrollTo().performClick()
            compose.waitForIdle()
            capture("settings-${category.substringBefore(' ').lowercase()}")
            when (category) {
                "Sources" -> compose.onNodeWithText("IPTV provider").assertIsDisplayed()
                "Playback" -> compose.onNodeWithText("Low-latency live playback").assertIsDisplayed()
                "Appearance & Accessibility" -> compose.onNodeWithText("Reduce motion").assertIsDisplayed()
                "Personalization" -> compose.onNodeWithText("SPORTS").assertIsDisplayed()
                "Notifications" -> compose.onNodeWithText("Game updates").assertIsDisplayed()
                "About Rally" -> compose.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
            }
        }
        compose.onNodeWithText("Check for updates").performScrollTo().performClick()
        Thread.sleep(2_000)
        capture("settings-support")
        nav("Home")
        waitText("Starting Soon")
        capture("home-return")
    }
}
