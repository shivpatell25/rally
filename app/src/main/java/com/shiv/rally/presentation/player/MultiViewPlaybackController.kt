@file:androidx.media3.common.util.UnstableApi

package com.shiv.rally.presentation.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import com.shiv.rally.R
import com.shiv.rally.domain.model.MultiViewSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Media3 1.2 PlayerView's SurfaceView can be obscured after Compose layouts
 * change on Android 14. Match Game View's embedded-surface workaround there. */
internal fun multiViewPlayerView(context: Context): PlayerView {
    val embedded = android.os.Build.VERSION.SDK_INT == 34
    val root = android.view.LayoutInflater.from(context).inflate(
        if (embedded) R.layout.player_view_surface_texture else R.layout.player_view_surface, null, false)
    return if (embedded) root.findViewById<PlayerView>(R.id.game_player_view).also {
        (it.parent as? android.view.ViewGroup)?.removeView(it)
    } else root as PlayerView
}

internal data class MultiViewPlaybackSnapshot(
    val session: MultiViewPlaybackSession? = null,
    val firstFrame: Boolean = false,
    val resolution: String? = null,
    val error: String? = null
)

/** Screen-owned sessions, never layout-owned players. All calls run on Main.
 * A source replacement releases its old decoder and surface BEFORE allocation.
 * Layout, focus, audio, score updates and stats overlays cannot recreate players. */
internal class MultiViewPlaybackController(
    context: Context,
    private val audioRouter: MultiViewAudioRouter,
    private val onFailure: (String, Int, String) -> Unit,
    private val onHealthy: (String, Int) -> Unit
) {
    private val context = context.applicationContext
    private val deviceClass = resolvePlaybackProfile(this.context).deviceClass
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private data class Record(val key: List<Any?>, val session: MultiViewPlaybackSession?)
    private val records = linkedMapOf<String, Record>()
    val snapshots = mutableStateMapOf<String, MultiViewPlaybackSnapshot>()
    private var closed = false

    fun sync(slots: List<MultiViewSlot>, mac: String, token: String, resumed: Boolean) {
        if (closed) return
        if (!resumed) { suspendPlayback(); return }
        val desired = slots.filter { it.streamUrl.isNotBlank() }.associateBy { it.slotId }
        // Complete every removal before creating any new player in this pass.
        records.keys.toList().forEach { id ->
            val slot = desired[id]
            if (slot == null || records[id]?.key != identity(slot, mac)) remove(id)
        }
        desired.values.forEachIndexed { index, slot ->
            val existing = records[slot.slotId]
            if (existing != null) {
                existing.session?.setStreamCount(slots.size)
                return@forEachIndexed
            }
            try {
                val session = MultiViewPlaybackSession(context, slot, slots.size, deviceClass, mac, token, audioRouter, scope,
                    publish = { snapshot -> snapshots[slot.slotId] = snapshot },
                    fail = { message -> onFailure(slot.slotId, slot.playbackRevision, message) },
                    healthy = { onHealthy(slot.slotId, slot.playbackRevision) })
                records[slot.slotId] = Record(identity(slot, mac), session)
                snapshots[slot.slotId] = MultiViewPlaybackSnapshot(session)
                session.start(index)
            } catch (failure: Exception) {
                Log.e("RallyMultiView", "Player allocation failed (${failure.javaClass.simpleName})")
                records[slot.slotId] = Record(identity(slot, mac), null)
                val message = "Unable to initialize this stream. Reconnecting…"
                snapshots[slot.slotId] = MultiViewPlaybackSnapshot(error = message)
                onFailure(slot.slotId, slot.playbackRevision, message)
            }
        }
    }

    private fun identity(slot: MultiViewSlot, mac: String): List<Any?> = listOf(
        slot.streamUrl, slot.playbackRevision, slot.streamHeaders ?: slot.channel?.streamHeaders,
        slot.channel?.id, slot.channel?.streamMimeType, mac
    ) // Token refresh for another tile must not restart every healthy stream.

    private fun remove(id: String) {
        records.remove(id)?.session?.release()
        snapshots.remove(id)
    }

    fun suspendPlayback() { records.keys.toList().forEach(::remove) }
    fun release() {
        if (closed) return
        closed = true
        suspendPlayback()
        scope.cancel()
    }
}

internal class MultiViewPlaybackSession(
    context: Context,
    private val slot: MultiViewSlot,
    count: Int,
    private val deviceClass: TvDeviceClass,
    mac: String,
    token: String,
    private val audioRouter: MultiViewAudioRouter,
    private val scope: CoroutineScope,
    private val publish: (MultiViewPlaybackSnapshot) -> Unit,
    private val fail: (String) -> Unit,
    private val healthy: () -> Unit
) {
    private val selector = DefaultTrackSelector(context)
    val player: ExoPlayer
    private var view: PlayerView? = null
    private var startJob: Job? = null
    private var monitorJob: Job? = null
    private var released = false
    private var firstFrame = false
    private var resolution: String? = null
    private var audible = false
    private var streamCount = 0
    private var lastProgressAt = 0L
    private var healthyReported = false
    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // Log only error codes, never signed URLs, tokens or provider headers.
            Log.w("RallyMultiView", "Tile ${slot.slotId.take(8)} failed: ${error.errorCodeName}")
            failSession("Stream interrupted. Reconnecting…")
        }
        override fun onRenderedFirstFrame() {
            if (released) return
            firstFrame = true
            publishState()
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (released || videoSize.height <= 0) return
            resolution = "${videoSize.height}p"
            publishState()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!released && playbackState == Player.STATE_ENDED) {
                failSession("This stream ended. Reconnecting…")
            }
        }
    }

    init {
        selector.parameters = selector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .setExceedRendererCapabilitiesIfNecessary(false)
            // Recovery reduces pressure on this tile only. Stronger devices
            // keep their normal rendition budget during healthy playback.
            .setForceLowestBitrate(deviceClass == TvDeviceClass.LOW_POWER || slot.recoveryAttempt > 0)
            .build()
        setStreamCount(count)
        val headers = sanitizedStreamHeaders(slot.streamHeaders ?: slot.channel?.streamHeaders).toMutableMap()
        val external = slot.channel == null || slot.channel.id.startsWith("m3u:") || slot.channel.id.startsWith("xtream:")
        val userAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
            ?: if (external) "Mozilla/5.0 (Android TV) AppleWebKit/537.36 Chrome/122 Safari/537.36"
            else "Mozilla/5.0 (QtEmbedded; U; Linux; C) MAG200 stbapp"
        headers.keys.filter { it.equals("User-Agent", true) }.forEach(headers::remove)
        if (!external) {
            if (mac.isNotBlank()) headers["Cookie"] = "mac=$mac; stb_lang=en; timezone=GMT"
            if (token.isNotBlank()) headers["Authorization"] = normalizedBearerToken(token)
        }
        val dataSource = DefaultHttpDataSource.Factory().setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(headers)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_500, 12_000, 800, 1_500)
            .setTargetBufferBytes(4 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        player = ExoPlayer.Builder(context, rallyRenderersFactory(context)
            .forceDisableMediaCodecAsynchronousQueueing())
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3)))
            .setTrackSelector(selector).setLoadControl(loadControl).build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                volume = 0f
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), false)
                addListener(listener)
            }
        audioRouter.register(slot.slotId) { enabled ->
            if (!released && audible != enabled) {
                audible = enabled
                player.volume = if (enabled) 1f else 0f
                selector.setParameters(selector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !enabled))
            }
        }
    }

    fun setStreamCount(count: Int) {
        if (streamCount == count) return
        streamCount = count
        val budget = multiViewVideoBudget(deviceClass, count)
        selector.setParameters(selector.buildUponParameters()
            .setMaxVideoSize(budget.width, budget.height)
            .setMaxVideoFrameRate(30).setMaxVideoBitrate(budget.bitrate)
            .setExceedVideoConstraintsIfNecessary(true))
    }

    fun attach(target: PlayerView) {
        if (released || view === target) return
        firstFrame = false
        PlayerView.switchTargetView(player, view, target)
        view = target
        publishState()
    }
    fun detach(target: PlayerView) {
        if (view !== target) return
        target.player = null
        view = null
    }

    fun start(index: Int) {
        startJob = scope.launch {
            delay((index * 250L).coerceAtMost(750))
            if (released) return@launch
            try {
                player.setMediaItem(MediaItem.Builder().setUri(slot.streamUrl)
                    .setMimeType(slot.channel?.streamMimeType).build())
                player.prepare()
                player.playWhenReady = true
                watchProgress()
            } catch (failure: Exception) {
                Log.w("RallyMultiView", "Tile startup failed (${failure.javaClass.simpleName})")
                failSession("Unable to start this stream. Reconnecting…")
            }
        }
    }

    private fun watchProgress() {
        monitorJob = scope.launch {
            val monitor = PlaybackProgressMonitor(SystemClock.elapsedRealtime())
            while (isActive && !released) {
                delay(2_000)
                if (released) break
                val now = SystemClock.elapsedRealtime()
                val counters = player.videoDecoderCounters
                counters?.ensureUpdated()
                val frames = counters?.renderedOutputBufferCount ?: 0
                val stalled = monitor.check(now, player.playWhenReady, player.playbackState == Player.STATE_BUFFERING,
                    player.playbackState == Player.STATE_READY, player.currentPosition, frames)
                if (stalled != null) { failSession(stalled.message); break }
                // A READY event alone doesn't prove recovery: require actual
                // advancing rendered frames over a healthy 30-second interval.
                if (firstFrame && player.isPlaying && frames > lastProgressAt) {
                    if (!healthyReported && now - healthyStartMs >= 30_000) {
                        healthyReported = true
                        healthy()
                    }
                } else if (!healthyReported) healthyStartMs = now
                lastProgressAt = frames.toLong()
            }
        }
    }
    private var healthyStartMs = SystemClock.elapsedRealtime()

    private fun publishState() { publish(MultiViewPlaybackSnapshot(this, firstFrame, resolution)) }
    private fun failSession(message: String) {
        if (released) return
        release() // Free only this tile; other games continue uninterrupted.
        publish(MultiViewPlaybackSnapshot(error = message))
        fail(message)
    }
    fun release() {
        if (released) return
        released = true
        startJob?.cancel()
        monitorJob?.cancel()
        audioRouter.unregister(slot.slotId)
        player.removeListener(listener)
        view?.player = null
        view = null
        player.release()
    }
}
