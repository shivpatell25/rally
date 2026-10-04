@file:androidx.media3.common.util.UnstableApi

package com.shiv.rally.presentation.player

import android.view.LayoutInflater
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.rally.R
import com.shiv.rally.domain.model.MultiViewSlot
import org.junit.*
import org.junit.Assert.*

/** Verify actual player identity and view detachment, not just unchanged URLs. */
class MultiViewSessionOwnershipTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var pool: MultiViewPlaybackController
    private lateinit var server: MultiViewFaultServer
    private val views = mutableMapOf<String, PlayerView>()
    private var mounted by mutableStateOf(true)
    private var reversed by mutableStateOf(false)
    private var slots = emptyList<MultiViewSlot>()
    private val failures = mutableListOf<String>()

    @After fun close() {
        if (::pool.isInitialized) compose.runOnIdle { mounted = false; pool.release() }
        compose.waitForIdle()
        if (::server.isInitialized) server.close()
    }
    @Test fun layoutFocusMetadataAndTokenChangesPreservePlayersAndReplacementReleasesOnlyOne() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        server = MultiViewFaultServer(emptyList(), instrumentation.context.assets.open("multiview/segment.ts").use { it.readBytes() })
        slots = (1..4).map { i -> MultiViewSlot(slotId = "slot$i", streamUrl = "${server.base}/play/addon/game$i/1/playlist.m3u8",
            streamHeaders = mapOf("Referer" to "${server.base}/watch", "User-Agent" to "RallyMultiviewQA")) }
        compose.setContent {
            pool = remember { MultiViewPlaybackController(instrumentation.targetContext, MultiViewAudioRouter(),
                { id, _, message -> failures.add("$id: $message") }, { _, _ -> }) }
            if (mounted) Row(Modifier.fillMaxSize()) {
                (if (reversed) slots.reversed() else slots).forEach { slot -> key(slot.slotId) {
                    val session = pool.snapshots[slot.slotId]?.session
                    if (session != null) AndroidView(factory = { context ->
                        multiViewPlayerView(context)
                            .also { views[slot.slotId] = it; session.attach(it) }
                    }, update = { session.attach(it) }, onRelease = { session.detach(it) }, modifier = Modifier.weight(1f).fillMaxHeight())
                } }
            }
        }
        compose.runOnIdle { pool.sync(slots, "", "token1", true) }
        compose.waitUntil(30_000) { pool.snapshots.size == 4 && pool.snapshots.values.all { it.firstFrame } }
        lateinit var before: Map<String, ExoPlayer>
        compose.runOnIdle { before = pool.snapshots.mapValues { it.value.session!!.player } }
        repeat(12) { n ->
            compose.runOnIdle {
                reversed = !reversed
                pool.sync(slots.reversed().map { it.copy(scoreText = "$n – 0", title = "updated") }, "", "refreshed-token", true)
                before.forEach { (id, player) -> assertSame(player, pool.snapshots[id]?.session?.player) }
            }
        }
        lateinit var oldView: PlayerView
        compose.runOnIdle {
            oldView = views.getValue("slot1")
            slots = slots.map { if (it.slotId == "slot1") it.copy(playbackRevision = 1) else it }
            pool.sync(slots, "", "new-token", true)
            assertNull(oldView.player)
            assertNotSame(before.getValue("slot1"), pool.snapshots["slot1"]?.session?.player)
            before.filterKeys { it != "slot1" }.forEach { (id, player) -> assertSame(player, pool.snapshots[id]?.session?.player) }
        }
        compose.waitUntil(30_000) { pool.snapshots.values.all { it.firstFrame } }
        compose.runOnIdle {
            pool.suspendPlayback()
            assertTrue(pool.snapshots.isEmpty())
            views.values.forEach { assertNull(it.player) }
            pool.sync(slots, "", "", true)
        }
        compose.waitUntil(30_000) { pool.snapshots.values.all { it.firstFrame } && pool.snapshots.size == 4 }
        compose.runOnIdle {
            // Remove all slots before staggered prepare jobs can fire.
            pool.sync(slots.map { it.copy(playbackRevision = 2) }, "", "", true)
            pool.sync(emptyList(), "", "", true)
            assertTrue(pool.snapshots.isEmpty())
        }
        Thread.sleep(1_000)
        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(0, server.badHeaders.get())
    }
}
