@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package com.shiv.rally.presentation

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.rally.MainActivity
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.data.remote.m3u.M3uPlaylistSource
import com.shiv.rally.domain.model.IptvProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread

/** Uses a local playlist server; restores the emulator's provider settings afterwards. */
class M3uSettingsEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val clip = "https://cmp-espn.media.dssott.com/opp/hls/espn/wsc/2026/0930/0eedac50-34a5-4a33-933b-afd99c16a5aa/0eedac50-34a5-4a33-933b-afd99c16a5aa/playlist.m3u8"
    private fun waitText(text: String) = compose.waitUntil(45_000) {
        compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "audit-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun playlistTabValidationChannelBrowsingAndPlayback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferencesManager(context)
        val originalProvider = preferences.iptvProvider
        val originalUrl = preferences.m3uPlaylistUrl
        val originalName = preferences.m3uPlaylistName
        val originalToken = preferences.authToken
        val originalSetup = preferences.setupComplete
        val server = ServerSocket(0)
        val base = "http://127.0.0.1:${server.localPort}"
        val connection = java.net.URL(clip).openConnection().apply {
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        val clipBase = java.net.URI(clip)
        val streamPlaylist = connection.getInputStream().bufferedReader().use { it.readText() }
            .lineSequence().joinToString("\n") { line ->
                when {
                    line.isBlank() -> ""
                    line.startsWith("#") -> Regex("URI=\"([^\"]+)\"").replace(line) {
                        "URI=\"${clipBase.resolve(it.groupValues[1])}\""
                    }
                    else -> clipBase.resolve(line.trim()).toString()
                }
            }
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="101" group-title="Sports",QA Stadium
            #EXTVLCOPT:http-user-agent=RallyPlaylistAudit
            $base/live.m3u8
            #EXTINF:-1 tvg-chno="102" group-title="Sports",QA Tennis
            $clip
        """.trimIndent()
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val worker = thread(name = "playlist-audit", isDaemon = true) {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream().bufferedReader()
                    val request = input.readLine().orEmpty()
                    val headers = mutableListOf<String>()
                    while (true) { val line = input.readLine(); if (line.isNullOrEmpty()) break; headers += line }
                    requests += request + "\n" + headers.joinToString("\n")
                    val isStream = request.contains("/live.m3u8") || request.contains("/feed")
                    val bytes = (if (isStream) streamPlaylist else playlist).toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                }
            } catch (_: SocketException) { /* Closed at the end of the test. */ }
        }
        val localFile = File(context.cacheDir, "updates/audit-playlist.m3u8").apply { parentFile?.mkdirs(); writeText(playlist) }
        try {
            // Exercise the content URI file path with the shipping file provider.
            val document = FileProvider.getUriForFile(context, "${context.packageName}.files", localFile)
            assertEquals(2, M3uPlaylistSource(context).load(document.toString(), "File Playlist").size)
            assertEquals(1, M3uPlaylistSource(context).load("$base/live.m3u8", "Single Feed").size)
            assertEquals("application/x-mpegURL", M3uPlaylistSource(context).load("$base/feed?id=1", "Single Feed").single().streamMimeType)

            waitText("Starting Soon")
            compose.onNodeWithContentDescription("Settings").performClick()
            waitText("Make Rally yours")
            compose.onNodeWithText("Stalker / Ministra").performScrollTo()
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithText("Stalker / Ministra").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithText("Xtream Codes").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithText("M3U / M3U8").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithText("Playlist or HLS URL").performScrollTo().performTextReplacement("invalid")
            compose.onNodeWithText("Check playlist").performScrollTo().performClick()
            waitText("Enter a valid HTTP")
            compose.onNodeWithText("Playlist or HLS URL").performTextReplacement("$base/channels.m3u")
            compose.onNodeWithText("Playlist name (optional)").performTextReplacement("QA Playlist")
            compose.onNodeWithText("Check playlist").performClick()
            waitText("Ready · 2 channels")
            capture("m3u-settings")
            compose.onNodeWithText("Choose M3U / M3U8 file").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithText("Choose M3U / M3U8 file").assertIsFocused()
            val pickerIntent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*")
            if (context.packageManager.resolveActivity(pickerIntent, 0) == null) {
                compose.onNodeWithText("Choose M3U / M3U8 file").performClick()
                waitText("A file picker is unavailable")
            }
            compose.onAllNodesWithText("Save and Apply").onFirst().performScrollTo().performClick()
            waitText("Starting Soon")
            assertEquals(IptvProvider.M3U, preferences.iptvProvider)
            assertEquals("$base/channels.m3u", preferences.m3uPlaylistUrl)
            compose.onNodeWithText("Live").performClick()
            waitText("Browse Live TV")
            compose.onAllNodesWithText("Browse Live TV", ignoreCase = true).onFirst().performClick()
            waitText("QA Stadium")
            compose.onNodeWithText("QA Tennis").assertIsDisplayed()
            capture("m3u-channels")
            compose.onNodeWithText("QA Stadium").performClick()
            var lastWake = 0L
            compose.waitUntil(45_000) {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastWake > 1_000) {
                    // Keep controls visible while the real stream starts.
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP)
                    lastWake = now
                }
                compose.onAllNodesWithText("Pause", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(requests.any { it.contains("/live.m3u8") && it.contains("RallyPlaylistAudit") })
            capture("m3u-playback")
        } finally {
            preferences.iptvProvider = originalProvider
            preferences.m3uPlaylistUrl = originalUrl
            preferences.m3uPlaylistName = originalName
            preferences.authToken = originalToken
            preferences.setupComplete = originalSetup
            server.close()
            worker.join(1_000)
            localFile.delete()
        }
    }
}
