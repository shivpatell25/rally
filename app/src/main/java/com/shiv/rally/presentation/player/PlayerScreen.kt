@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@file:androidx.media3.common.util.UnstableApi

package com.shiv.rally.presentation.player

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import coil.compose.AsyncImage
import com.shiv.rally.R
import com.shiv.rally.domain.model.BroadcastQualityInfo
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.HighlightClip
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.RelevantChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.model.StreamCandidate
import com.shiv.rally.domain.model.parseQualityFromChannelName
import com.shiv.rally.domain.model.resolveMaxBroadcastQuality
import com.shiv.rally.presentation.event.AppleTvStreamPicker
import com.shiv.rally.presentation.common.RallyControlButton
import com.shiv.rally.presentation.common.RallyPagedRow
import com.shiv.rally.presentation.home.formatTeamDisplayName
import com.shiv.rally.presentation.theme.AppleTvTheme
import kotlinx.coroutines.delay
import java.util.Locale

private val playerPanelShape = RoundedCornerShape(10.dp)
private val playerPillShape = RoundedCornerShape(6.dp)
private val playerActionShape = RoundedCornerShape(8.dp)

@Immutable
data class VideoStreamSpecs(
    val resolution: String? = null,
    val fps: String? = null,
    val bitrate: String? = null,
    val dynamicRange: String? = null
)

@Immutable
private data class PlaybackDiagnostics(
    val codec: String = "Detecting",
    val bufferedMs: Long = 0L,
    val droppedFrames: Int = 0,
    val sourceHealthScore: Int = 0,
    val recoveryAttempt: Int = 0,
    val lowLatencyMode: Boolean = true,
    val adaptiveQuality: String = "Automatic",
    val deviceProfile: String = "TV"
)

private data class PlaybackSession(
    val player: ExoPlayer,
    val trackSelector: DefaultTrackSelector
)

private fun extractSpecs(format: Format?, current: VideoStreamSpecs): VideoStreamSpecs {
    if (format == null) return current
    val resolution = if (format.width > 0 && format.height > 0) {
        when {
            format.width >= 3840 || format.height >= 2160 -> "4K"
            format.width >= 1920 || format.height >= 1080 -> "1080p"
            format.width >= 1280 || format.height >= 720 -> "720p"
            else -> "${format.width}×${format.height}"
        }
    } else current.resolution
    val fps = if (format.frameRate > 0) "${Math.round(format.frameRate)} fps" else current.fps
    val rawBitrate = when {
        format.bitrate > 0 -> format.bitrate
        format.peakBitrate > 0 -> format.peakBitrate
        else -> 0
    }
    val bitrate = if (rawBitrate > 0) {
        String.format(Locale.US, "%.1f Mbps", rawBitrate / 1_000_000.0)
    } else current.bitrate
    val dynamicRange = when {
        format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
        format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
        else -> current.dynamicRange
    }
    return VideoStreamSpecs(resolution, fps, bitrate, dynamicRange)
}

@Composable
private fun VideoPlayerSurface(
    exoPlayer: ExoPlayer,
    onRemoteKey: (Int) -> Boolean,
    onSurfaceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            val parent = android.widget.FrameLayout(context)
            (LayoutInflater.from(context).inflate(R.layout.player_view_surface, parent, false) as PlayerView).apply {
                player = exoPlayer
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                runCatching {
                    PlayerView::class.java
                        .getMethod("setEnableComposeSurfaceSyncWorkaround", Boolean::class.javaPrimitiveType)
                        .invoke(this, true)
                }
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setOnClickListener { onSurfaceClick() }
                setOnKeyListener { _, keyCode, event ->
                    event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 && onRemoteKey(keyCode)
                }
            }
        },
        update = {
            if (it.player !== exoPlayer) it.player = exoPlayer
            it.setOnClickListener { onSurfaceClick() }
        },
        modifier = modifier
    )
}

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateToMultiView: (channelId: String, eventId: String?, pairedEventId: String?) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        PlayerUiState.Loading -> PlayerLoadingState()
        is PlayerUiState.Error -> PlayerErrorState(current.message, viewModel::retry, onBack)
        is PlayerUiState.Success -> PlayerContent(
            streamUrl = current.streamUrl,
            macAddress = current.macAddress,
            token = current.token,
            event = current.event,
            currentChannel = current.currentChannel,
            relevantChannels = current.relevantChannels,
            stremioStreams = current.stremioStreams,
            streamCandidates = current.streamCandidates,
            otherLiveEvents = current.otherLiveEvents,
            isSwitchingGame = current.isSwitchingGame,
            streamHeaders = current.streamHeaders,
            isExternalStream = current.isExternalStream,
            recoveryAttempt = current.recoveryAttempt,
            recoveryStatus = current.recoveryStatus,
            terminalPlaybackError = current.terminalPlaybackError,
            lowLatencyMode = current.lowLatencyMode,
            adaptiveQualityEnabled = current.adaptiveQualityEnabled,
            audioNormalizationEnabled = current.audioNormalizationEnabled,
            sourceHealthScore = current.sourceHealthScore,
            onBack = onBack,
            onSelectOtherEvent = viewModel::switchToEvent,
            onSwitchStream = viewModel::switchStream,
            onPlaybackReady = viewModel::reportPlaybackReady,
            onPlaybackStall = viewModel::reportPlaybackStall,
            onPlaybackFailure = viewModel::recoverFromPlaybackFailure,
            onNavigateToMultiView = onNavigateToMultiView
        )
    }
}

@Composable
private fun PlayerLoadingState() {
    Box(Modifier.fillMaxSize().background(AppleTvTheme.ScreenGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.rally_wordmark_white_ui), "Rally", Modifier.height(54.dp))
            Spacer(Modifier.height(18.dp))
            Text("Starting playback", color = AppleTvTheme.TextSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PlayerErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(AppleTvTheme.ScreenGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Unable to play this stream", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(message, color = AppleTvTheme.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerButton("Try Again", onRetry, true, Modifier.focusRequester(firstFocus))
                PlayerButton("Back", onBack)
            }
        }
    }
}

@Composable
fun PlayerContent(
    streamUrl: String,
    macAddress: String = "",
    token: String = "",
    event: SportEvent?,
    currentChannel: IptvChannel? = null,
    relevantChannels: List<RelevantChannel> = emptyList(),
    stremioStreams: List<StremioStreamOption> = emptyList(),
    streamCandidates: List<StreamCandidate> = emptyList(),
    otherLiveEvents: List<SportEvent>,
    isSwitchingGame: Boolean = false,
    streamHeaders: Map<String, String>? = null,
    isExternalStream: Boolean = false,
    recoveryAttempt: Int = 0,
    recoveryStatus: String? = null,
    terminalPlaybackError: String? = null,
    lowLatencyMode: Boolean = true,
    adaptiveQualityEnabled: Boolean = true,
    audioNormalizationEnabled: Boolean = true,
    sourceHealthScore: Int = 0,
    onBack: () -> Unit,
    onSelectOtherEvent: (String) -> Unit,
    onSwitchStream: (String) -> Unit = {},
    onPlaybackReady: (String, Long) -> Unit = { _, _ -> },
    onPlaybackStall: (String) -> Unit = {},
    onPlaybackFailure: (String) -> Unit = {},
    onNavigateToMultiView: (channelId: String, eventId: String?, pairedEventId: String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var controlsVisible by remember(streamUrl) { mutableStateOf(event == null) }
    var gameViewVisible by remember(streamUrl) { mutableStateOf(event != null) }
    var gameViewOverlayVisible by remember(streamUrl) { mutableStateOf(event != null) }
    var currentHighlightsVisible by remember(streamUrl) { mutableStateOf(false) }
    var defaultPresentationApplied by remember(streamUrl) { mutableStateOf(event != null) }
    var sourcePickerVisible by remember { mutableStateOf(false) }
    var selectedOtherEvent by remember { mutableStateOf<SportEvent?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var streamSpecs by remember { mutableStateOf(VideoStreamSpecs()) }
    var isPlaying by remember { mutableStateOf(false) }
    var diagnosticsVisible by remember { mutableStateOf(false) }
    var diagnosticsSnapshot by remember { mutableStateOf(PlaybackDiagnostics()) }
    var canRestart by remember(streamUrl) { mutableStateOf(false) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val controlFocus = remember { FocusRequester() }
    val gameViewFocus = remember { FocusRequester() }
    val gameViewMenuFocus = remember { FocusRequester() }
    val highlightsFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }

    LaunchedEffect(event?.id, streamUrl) {
        // Matchup streams open in the split game experience. Unmatched linear
        // channels (including RedZone) retain the distraction-free full screen.
        if (event != null && !defaultPresentationApplied) {
            gameViewVisible = true
            controlsVisible = false
            defaultPresentationApplied = true
        }
    }

    val activeHeaders = streamHeaders ?: stremioStreams.firstOrNull { it.streamUrl == streamUrl }?.headers
    val safeHeaders = remember(activeHeaders) { sanitizedStreamHeaders(activeHeaders) }
    val playbackProfile = remember(context) { resolvePlaybackProfile(context) }
    var qualityFallbackActive by remember(streamUrl) { mutableStateOf(false) }
    var liveWindowRecoveryUsed by remember(streamUrl) { mutableStateOf(false) }
    val playbackSession = remember(context, streamUrl, safeHeaders, isExternalStream, macAddress, token, playbackProfile, lowLatencyMode) {
        val sessionTrackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(playbackProfile.maxVideoWidth, playbackProfile.maxVideoHeight)
                    .setMaxVideoBitrate(playbackProfile.maxVideoBitrate)
                    .setMaxVideoFrameRate(playbackProfile.maxVideoFrameRate)
                    // A number of IPTV and direct add-on links contain only one video
                    // rendition. Keep that rendition selected when no lower adaptive
                    // alternative exists instead of producing audio with a black frame.
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(false)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
            )
        }
        val dataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(if (isExternalStream) 12_000 else 8_000)
            .setReadTimeoutMs(if (isExternalStream) 12_000 else 8_000)

        if (isExternalStream) {
            val userAgent = safeHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                ?: "Mozilla/5.0 (Android TV) AppleWebKit/537.36 Chrome/122 Safari/537.36"
            dataSource.setUserAgent(userAgent)
            val requestHeaders = safeHeaders.filterKeys { !it.equals("User-Agent", true) }
            if (requestHeaders.isNotEmpty()) dataSource.setDefaultRequestProperties(requestHeaders)
        } else {
            dataSource.setUserAgent("Mozilla/5.0 (QtEmbedded; U; Linux; C) MAG200 stbapp")
            if (macAddress.isNotBlank()) {
                val requestHeaders = mutableMapOf("Cookie" to "mac=$macAddress; stb_lang=en; timezone=GMT")
                if (token.isNotBlank()) requestHeaders["Authorization"] = normalizedBearerToken(token)
                dataSource.setDefaultRequestProperties(requestHeaders)
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (lowLatencyMode) 2_500 else 6_000,
                if (lowLatencyMode) 10_000 else 20_000,
                if (lowLatencyMode) 500 else 800,
                if (lowLatencyMode) 1_000 else 1_500
            )
            .setTargetBufferBytes(playbackProfile.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        val sessionPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(sessionTrackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build()
        PlaybackSession(sessionPlayer, sessionTrackSelector)
    }
    val exoPlayer = playbackSession.player
    val trackSelector = playbackSession.trackSelector
    val playbackStartedAt = remember(streamUrl) { android.os.SystemClock.elapsedRealtime() }
    var readyReported by remember(streamUrl) { mutableStateOf(false) }
    var stallReportedAt by remember(streamUrl) { mutableStateOf(0L) }
    val audioNormalizer = remember { AudioNormalizationSession() }
    var highlightsSessionStarted by remember(streamUrl) { mutableStateOf(false) }
    var liveWasPlayingBeforeHighlights by remember(streamUrl) { mutableStateOf(true) }
    var liveVolumeBeforeHighlights by remember(streamUrl) { mutableStateOf(1f) }

    LaunchedEffect(currentHighlightsVisible, exoPlayer) {
        if (currentHighlightsVisible) {
            liveWasPlayingBeforeHighlights = exoPlayer.playWhenReady
            liveVolumeBeforeHighlights = exoPlayer.volume
            highlightsSessionStarted = true
            exoPlayer.volume = 0f
            // Stop the live decoder while clips are open. This keeps Current
            // Highlights within a single hardware-decoder budget on Chromecast.
            exoPlayer.stop()
        } else if (highlightsSessionStarted) {
            highlightsSessionStarted = false
            exoPlayer.volume = liveVolumeBeforeHighlights
            exoPlayer.seekToDefaultPosition()
            exoPlayer.prepare()
            exoPlayer.playWhenReady = liveWasPlayingBeforeHighlights
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!liveWindowRecoveryUsed && error.isBehindLiveWindowFailure()) {
                    // Live HLS windows can advance while a device is briefly stalled.
                    // Jump to the current live edge instead of leaving a black frame or
                    // unnecessarily abandoning an otherwise healthy source.
                    liveWindowRecoveryUsed = true
                    playbackError = null
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } else if (!qualityFallbackActive && error.isVideoDecoderFailure()) {
                    qualityFallbackActive = true
                    playbackError = null
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters()
                            .setMaxVideoSize(1920, 1080)
                            .setMaxVideoBitrate(15_000_000)
                            .setMaxVideoFrameRate(60)
                            .setExceedVideoConstraintsIfNecessary(true)
                            .setExceedRendererCapabilitiesIfNecessary(false)
                    )
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } else {
                    val message = if (qualityFallbackActive && error.isVideoDecoderFailure()) {
                        "This source exceeds the device's video decoder limits. Choose a lower-quality source."
                    } else {
                        error.localizedMessage ?: "The stream stopped responding."
                    }
                    playbackError = null
                    onPlaybackFailure(message)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    streamSpecs = extractSpecs(exoPlayer.videoFormat, streamSpecs)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }?.let { group ->
                    (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { index ->
                        streamSpecs = extractSpecs(group.getTrackFormat(index), streamSpecs)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                    streamSpecs = extractSpecs(exoPlayer.videoFormat, streamSpecs)
                    if (!readyReported) {
                        readyReported = true
                        onPlaybackReady(streamUrl, android.os.SystemClock.elapsedRealtime() - playbackStartedAt)
                    }
                } else if (playbackState == Player.STATE_BUFFERING && readyReported) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - stallReportedAt > 15_000L) {
                        stallReportedAt = now
                        onPlaybackStall(streamUrl)
                    }
                }
                canRestart = exoPlayer.isCurrentMediaItemSeekable
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                canRestart = exoPlayer.isCurrentMediaItemSeekable
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioNormalizationEnabled) audioNormalizer.attach(audioSessionId)
                else audioNormalizer.release()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            audioNormalizer.release()
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, streamUrl) {
        streamSpecs = VideoStreamSpecs()
        playbackError = null
        if (streamUrl.isBlank()) {
            playbackError = "No playable URL was returned for this source."
        } else {
            runCatching {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(streamUrl))
                    .apply {
                        if (lowLatencyMode && event?.isLive() == true) {
                            setLiveConfiguration(
                                MediaItem.LiveConfiguration.Builder()
                                    .setTargetOffsetMs(3_000)
                                    .setMinPlaybackSpeed(.97f)
                                    .setMaxPlaybackSpeed(1.03f)
                                    .build()
                            )
                        }
                    }
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }.onFailure { onPlaybackFailure(it.localizedMessage ?: "Unable to start playback.") }
        }
    }

    LaunchedEffect(terminalPlaybackError) {
        terminalPlaybackError?.let { playbackError = it }
    }

    var adaptiveTier by remember(streamUrl) { mutableIntStateOf(if (playbackProfile.supportsHardware4k) 2 else 1) }
    var adaptiveReason by remember(streamUrl) { mutableStateOf("Automatic") }
    LaunchedEffect(exoPlayer, streamUrl, playbackProfile, adaptiveQualityEnabled) {
        var lastDropped = 0
        var stableSamples = 0
        while (true) {
            delay(4_000)
            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) continue
            val dropped = exoPlayer.videoDecoderCounters?.droppedBufferCount ?: 0
            val droppedDelta = (dropped - lastDropped).coerceAtLeast(0)
            lastDropped = dropped
            val bufferMs = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)
            if (!adaptiveQualityEnabled) continue
            val struggling = exoPlayer.playbackState == Player.STATE_BUFFERING || droppedDelta >= 5 || (exoPlayer.isPlaying && bufferMs < 900)
            if (struggling && adaptiveTier > 0) {
                adaptiveTier--
                stableSamples = 0
                adaptiveReason = if (adaptiveTier == 0) "Stability · 720p ceiling" else "Stability · 1080p ceiling"
            } else if (!struggling && bufferMs >= 4_000) {
                stableSamples++
                val maximumTier = if (playbackProfile.supportsHardware4k) 2 else 1
                if (stableSamples >= 10 && adaptiveTier < maximumTier) {
                    adaptiveTier++
                    stableSamples = 0
                    adaptiveReason = "Connection stable · quality restored"
                }
            } else {
                stableSamples = 0
            }
        }
    }

    LaunchedEffect(adaptiveTier, trackSelector, playbackProfile) {
        val (width, height, bitrate) = when (adaptiveTier) {
            0 -> Triple(1280, 720, 7_000_000)
            1 -> Triple(1920, 1080, 15_000_000)
            else -> Triple(playbackProfile.maxVideoWidth, playbackProfile.maxVideoHeight, playbackProfile.maxVideoBitrate)
        }
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSize(width, height)
                .setMaxVideoBitrate(bitrate)
                .setMaxVideoFrameRate(playbackProfile.maxVideoFrameRate)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(false)
        )
    }

    LaunchedEffect(diagnosticsVisible, exoPlayer, streamSpecs, sourceHealthScore, recoveryAttempt, lowLatencyMode) {
        while (diagnosticsVisible) {
            val format = exoPlayer.videoFormat
            diagnosticsSnapshot = PlaybackDiagnostics(
                codec = format?.sampleMimeType?.substringAfterLast('/')?.uppercase(Locale.US) ?: "Detecting",
                bufferedMs = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L),
                droppedFrames = exoPlayer.videoDecoderCounters?.droppedBufferCount ?: 0,
                sourceHealthScore = sourceHealthScore,
                recoveryAttempt = recoveryAttempt,
                lowLatencyMode = lowLatencyMode,
                adaptiveQuality = adaptiveReason,
                deviceProfile = playbackProfile.deviceClass.displayName
            )
            delay(1_000)
        }
    }

    val nameQuality = remember(currentChannel?.name) { currentChannel?.let { parseQualityFromChannelName(it.name) } }
    val activeAddonSource = remember(stremioStreams, streamUrl) { stremioStreams.firstOrNull { it.streamUrl == streamUrl } }
    val displaySpecs = remember(streamSpecs, nameQuality, activeAddonSource, qualityFallbackActive) {
        listOfNotNull(
            streamSpecs.resolution ?: nameQuality?.resolution ?: activeAddonSource?.quality,
            streamSpecs.dynamicRange,
            streamSpecs.fps ?: nameQuality?.fps,
            streamSpecs.bitrate ?: activeAddonSource?.bitrate,
            "Compatibility".takeIf { qualityFallbackActive }
        ).distinct().joinToString(" · ").ifBlank { "Adaptive" }
    }
    val broadcastStations = remember(event) { event?.broadcastStations().orEmpty() }
    val broadcastQuality = remember(event, broadcastStations, relevantChannels, stremioStreams) {
        event?.let { resolveMaxBroadcastQuality(it, broadcastStations, relevantChannels, stremioStreams) }
            ?: BroadcastQualityInfo("HD", "Available: HD")
    }

    fun dismissLayerOrLeave() {
        when {
            diagnosticsVisible -> diagnosticsVisible = false
            selectedOtherEvent != null -> selectedOtherEvent = null
            sourcePickerVisible -> sourcePickerVisible = false
            playbackError != null -> onBack()
            currentHighlightsVisible -> currentHighlightsVisible = false
            gameViewVisible -> {
                gameViewVisible = false
                gameViewOverlayVisible = false
                controlsVisible = false
            }
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }

    fun handleRemoteKey(keyCode: Int): Boolean {
        interactionVersion++
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_BACK -> {
                dismissLayerOrLeave()
                true
            }
            android.view.KeyEvent.KEYCODE_MENU -> {
                if (gameViewVisible && !sourcePickerVisible) {
                    gameViewOverlayVisible = !gameViewOverlayVisible
                } else if (!sourcePickerVisible) {
                    controlsVisible = !controlsVisible
                }
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> when {
                gameViewVisible && !gameViewOverlayVisible -> {
                    runCatching { gameViewMenuFocus.requestFocus() }
                    true
                }
                !controlsVisible && !gameViewVisible -> {
                    controlsVisible = true
                    true
                }
                else -> false
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if (controlsVisible && !gameViewVisible) {
                controlsVisible = false
                true
            } else false
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> when {
                gameViewVisible && !gameViewOverlayVisible -> {
                    gameViewOverlayVisible = true
                    true
                }
                !controlsVisible && !gameViewVisible -> {
                    controlsVisible = true
                    true
                }
                else -> false
            }
            else -> false
        }
    }

    BackHandler { dismissLayerOrLeave() }

    LaunchedEffect(controlsVisible, gameViewVisible, gameViewOverlayVisible, currentHighlightsVisible, sourcePickerVisible, selectedOtherEvent, playbackError, diagnosticsVisible) {
        val target = when {
            diagnosticsVisible -> null
            sourcePickerVisible || selectedOtherEvent != null -> null
            playbackError != null -> errorFocus
            currentHighlightsVisible -> highlightsFocus
            gameViewVisible && gameViewOverlayVisible -> gameViewFocus
            controlsVisible -> controlFocus
            else -> rootFocus
        }
        if (target != null) {
            repeat(3) { attempt ->
                if (attempt > 0) delay(80) else delay(120)
                runCatching { target.requestFocus() }
            }
        }
    }

    LaunchedEffect(controlsVisible, gameViewVisible, sourcePickerVisible, selectedOtherEvent, playbackError, interactionVersion) {
        if (controlsVisible && !gameViewVisible && !sourcePickerVisible && selectedOtherEvent == null && playbackError == null) {
            delay(6_500)
            controlsVisible = false
        }
    }

    LaunchedEffect(gameViewOverlayVisible, gameViewVisible, sourcePickerVisible, selectedOtherEvent, playbackError, interactionVersion) {
        if (gameViewVisible && gameViewOverlayVisible && !currentHighlightsVisible && !sourcePickerVisible && selectedOtherEvent == null && playbackError == null) {
            delay(4_000)
            gameViewOverlayVisible = false
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusProperties {
                canFocus = !controlsVisible &&
                    (!gameViewVisible || !gameViewOverlayVisible) &&
                    !currentHighlightsVisible &&
                    !sourcePickerVisible &&
                    selectedOtherEvent == null &&
                    playbackError == null
            }
            .focusable()
            .onPreviewKeyEvent {
                it.type == KeyEventType.KeyDown &&
                    it.nativeKeyEvent.repeatCount == 0 &&
                    handleRemoteKey(it.nativeKeyEvent.keyCode)
            }
    ) {
        val gameViewHorizontalPadding = 28.dp
        val gameViewGap = 16.dp
        val gameViewAvailableWidth = maxWidth - (gameViewHorizontalPadding * 2) - gameViewGap
        val gameViewMainHeight = minOf(320.dp, gameViewAvailableWidth * (9f / 25f))
        val centeredGameViewTop = ((maxHeight - gameViewMainHeight) / 2f).coerceAtLeast(52.dp)
        val gameViewTop = if (currentHighlightsVisible || otherLiveEvents.isEmpty()) centeredGameViewTop else 48.dp
        val gameViewVideoWidth = gameViewMainHeight * (16f / 9f)
        val gameViewInfoWidth = gameViewAvailableWidth - gameViewVideoWidth

        if (gameViewVisible) {
            Image(
                painterResource(R.drawable.rally_ambient_background_v5),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color(0x3205080F)))
        }

        val videoModifier = if (gameViewVisible) {
            Modifier
                .align(Alignment.TopStart)
                .padding(start = gameViewHorizontalPadding, top = gameViewTop)
                .width(gameViewVideoWidth)
                .height(gameViewMainHeight)
                .clip(playerPanelShape)
                .border(1.dp, Color(0x2EFFFFFF), playerPanelShape)
        } else {
            Modifier.fillMaxSize()
        }
        if (!currentHighlightsVisible) {
            VideoPlayerSurface(
                exoPlayer = exoPlayer,
                onRemoteKey = ::handleRemoteKey,
                onSurfaceClick = {
                    if (gameViewVisible) gameViewOverlayVisible = true else controlsVisible = true
                    interactionVersion++
                },
                modifier = videoModifier
            )
        }

        if (gameViewVisible) {
            GameViewModeBar(
                highlightsSelected = currentHighlightsVisible,
                highlightsAvailable = event != null,
                gameViewFocusRequester = gameViewMenuFocus,
                onGameView = { currentHighlightsVisible = false },
                onHighlights = {
                    currentHighlightsVisible = true
                    gameViewOverlayVisible = false
                }
            )
            if (currentHighlightsVisible) {
                CurrentHighlightsChrome(
                    event = event,
                    mainTop = gameViewTop,
                    mainHeight = gameViewMainHeight,
                    videoWidth = gameViewVideoWidth,
                    infoWidth = gameViewInfoWidth,
                    initialFocus = highlightsFocus,
                    onRemoteKey = ::handleRemoteKey
                )
            } else {
                GameViewChrome(
                    event = event,
                    currentChannel = currentChannel,
                    displaySpecs = displaySpecs,
                    broadcastQuality = broadcastQuality,
                    otherLiveEvents = otherLiveEvents,
                    mainTop = gameViewTop,
                    mainHeight = gameViewMainHeight,
                    videoWidth = gameViewVideoWidth,
                    infoWidth = gameViewInfoWidth,
                    isPlaying = isPlaying,
                    overlayVisible = gameViewOverlayVisible,
                    initialFocus = gameViewFocus,
                    onTogglePlayback = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onFullScreen = {
                        gameViewVisible = false
                        gameViewOverlayVisible = false
                        controlsVisible = false
                    },
                    onChooseSource = { sourcePickerVisible = true },
                    onMultiView = {
                        onNavigateToMultiView(currentChannel?.id ?: streamUrl, event?.id, null)
                    },
                    onOtherEvent = { selectedOtherEvent = it }
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !gameViewVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlaybackHud(
                event = event,
                currentChannel = currentChannel,
                displaySpecs = displaySpecs,
                firstFocus = controlFocus,
                onBack = onBack,
                onGameView = {
                    gameViewVisible = true
                    gameViewOverlayVisible = true
                },
                onChooseSource = { sourcePickerVisible = true },
                onDiagnostics = { diagnosticsVisible = true },
                canRestart = canRestart || currentChannel?.supportsCatchUp == true,
                onRestart = {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.playWhenReady = true
                },
                onMultiView = {
                    onNavigateToMultiView(currentChannel?.id ?: streamUrl, event?.id, null)
                }
            )
        }

        if (isSwitchingGame) {
            Box(Modifier.fillMaxSize().background(Color(0x85000000)), contentAlignment = Alignment.Center) {
                Text("Switching game…", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        recoveryStatus?.let { message ->
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 18.dp).width(390.dp)
                    .clip(playerPanelShape).background(Color(0xF00B1420)).border(1.dp, Color(0x5A8CA8BE), playerPanelShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF6FCFFF)))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("RECOVERING PLAYBACK", color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .9.sp)
                    Text(message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${recoveryAttempt.coerceAtLeast(1)}/2", color = AppleTvTheme.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (diagnosticsVisible) {
            PlaybackDiagnosticsPanel(
                specs = displaySpecs,
                snapshot = diagnosticsSnapshot,
                isExternalStream = isExternalStream,
                onDismiss = { diagnosticsVisible = false }
            )
        }

        playbackError?.let { message ->
            PlaybackErrorOverlay(
                message = message,
                initialFocus = errorFocus,
                canChooseSource = relevantChannels.isNotEmpty() || stremioStreams.isNotEmpty(),
                onRetry = {
                    playbackError = null
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                },
                onChooseSource = { sourcePickerVisible = true },
                onBack = onBack
            )
        }

        if (sourcePickerVisible) {
            AppleTvStreamPicker(
                channels = relevantChannels,
                stremioStreams = stremioStreams,
                candidates = streamCandidates,
                broadcastStations = broadcastStations,
                broadcastQuality = broadcastQuality,
                eventName = event?.name.orEmpty(),
                onChannelSelected = {
                    sourcePickerVisible = false
                    onSwitchStream(it)
                },
                onCancel = { sourcePickerVisible = false }
            )
        }

        selectedOtherEvent?.let { target ->
            OtherLiveGameActionDialog(
                currentEvent = event,
                targetEvent = target,
                onWatchInMultiView = {
                    selectedOtherEvent = null
                    onNavigateToMultiView(currentChannel?.id ?: streamUrl, event?.id, target.id)
                },
                onSwitchFullScreen = {
                    selectedOtherEvent = null
                    gameViewVisible = false
                    controlsVisible = false
                    onSelectOtherEvent(target.id)
                },
                onDismiss = { selectedOtherEvent = null }
            )
        }
    }
}

@Composable
private fun PlaybackDiagnosticsPanel(
    specs: String,
    snapshot: PlaybackDiagnostics,
    isExternalStream: Boolean,
    onDismiss: () -> Unit
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { closeFocus.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0x75000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            Modifier.padding(end = 28.dp).width(310.dp).clip(playerPanelShape)
                .background(AppleTvTheme.GlassPanelGradient)
                .border(1.dp, Color(0x5A8CA8BE), playerPanelShape)
                .padding(20.dp)
                .clickable(enabled = false) {}
        ) {
            Text("PLAYBACK DIAGNOSTICS", color = AppleTvTheme.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Live signal", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            DiagnosticRow("Video", specs)
            DiagnosticRow("Codec", snapshot.codec)
            DiagnosticRow("Buffer", "${snapshot.bufferedMs / 1_000.0} s")
            DiagnosticRow("Dropped frames", snapshot.droppedFrames.toString())
            DiagnosticRow("Source health", healthLabel(snapshot.sourceHealthScore))
            DiagnosticRow("Recovery", if (snapshot.recoveryAttempt == 0) "Not needed" else "Attempt ${snapshot.recoveryAttempt}")
            DiagnosticRow("Latency", if (snapshot.lowLatencyMode) "Low latency" else "Standard")
            DiagnosticRow("Quality control", snapshot.adaptiveQuality)
            DiagnosticRow("Device profile", snapshot.deviceProfile)
            DiagnosticRow("Delivery", if (isExternalStream) "Adaptive web stream" else "TV provider stream")
            Spacer(Modifier.height(16.dp))
            PlayerButton("Done", onDismiss, true, Modifier.focusRequester(closeFocus))
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun healthLabel(score: Int): String = when {
    score >= 55 -> "Excellent"
    score >= 15 -> "Good"
    score >= -20 -> "Fair"
    else -> "Poor"
}

@Composable
private fun PlaybackHud(
    event: SportEvent?,
    currentChannel: IptvChannel?,
    displaySpecs: String,
    firstFocus: FocusRequester,
    onBack: () -> Unit,
    onGameView: () -> Unit,
    onChooseSource: () -> Unit,
    onDiagnostics: () -> Unit,
    canRestart: Boolean,
    onRestart: () -> Unit,
    onMultiView: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color(0xA6000000),
                .28f to Color.Transparent,
                .57f to Color.Transparent,
                1f to Color(0xE6000000)
            )
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerButton("‹ Back", onBack)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.rally_mark_ui), "Rally", Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Text(currentChannel?.name ?: "Live Sports", color = AppleTvTheme.TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 30.dp, end = 30.dp, bottom = 27.dp)) {
            if (event != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.isLive()) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(AppleTvTheme.AccentRed))
                        Spacer(Modifier.width(7.dp))
                        Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(event.gameStatusDetail ?: event.league, color = AppleTvTheme.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(7.dp))
                Text(event.scoreLine(), color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(
                    listOfNotNull(event.league, event.venue).joinToString(" · "),
                    color = AppleTvTheme.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(currentChannel?.name ?: "Live stream", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(currentChannel?.category?.ifBlank { "Live TV" } ?: "Live TV", color = AppleTvTheme.TextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                if (event != null) PlayerButton("Game View", onGameView, true, Modifier.focusRequester(firstFocus))
                PlayerButton("Sources", onChooseSource, modifier = if (event == null) Modifier.focusRequester(firstFocus) else Modifier)
                PlayerButton("Diagnostics", onDiagnostics)
                if (canRestart) PlayerButton("Restart", onRestart)
                PlayerButton("Multi-View", onMultiView)
                PlayerMetaPill(displaySpecs)
            }
        }
    }
}

@Composable
private fun GameViewModeBar(
    highlightsSelected: Boolean,
    highlightsAvailable: Boolean,
    gameViewFocusRequester: FocusRequester,
    onGameView: () -> Unit,
    onHighlights: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 28.dp, vertical = 6.dp)) {
        Row(
            Modifier.align(Alignment.Center).clip(playerActionShape)
                .background(Color(0x9C0A101B)).border(1.dp, Color(0x405A7894), playerActionShape)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            GameViewModeItem("GAME VIEW", !highlightsSelected, true, onGameView, Modifier.focusRequester(gameViewFocusRequester))
            GameViewModeItem("CURRENT HIGHLIGHTS", highlightsSelected, highlightsAvailable, onHighlights)
        }
        Image(
            painterResource(R.drawable.rally_mark_ui),
            contentDescription = "Rally",
            modifier = Modifier.align(Alignment.CenterEnd).size(27.dp)
        )
    }
}

@Composable
private fun GameViewModeItem(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember(label) { mutableStateOf(false) }
    Box(
        modifier.onFocusChanged { focused = it.isFocused }
            .clip(playerPillShape)
            .background(
                when {
                    focused -> Color(0xFFECF3F8)
                    selected -> Color(0xC92A3D52)
                    else -> Color.Transparent
                }
            )
            .border(
                if (focused || selected) 1.dp else 0.dp,
                if (focused) Color.White else Color(0x455A7894),
                playerPillShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = when {
                !enabled -> AppleTvTheme.TextTertiary
                focused -> AppleTvTheme.DeepNavy
                else -> Color.White
            },
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .9.sp
        )
    }
}

@Composable
private fun CurrentHighlightsChrome(
    event: SportEvent?,
    mainTop: androidx.compose.ui.unit.Dp,
    mainHeight: androidx.compose.ui.unit.Dp,
    videoWidth: androidx.compose.ui.unit.Dp,
    infoWidth: androidx.compose.ui.unit.Dp,
    initialFocus: FocusRequester,
    onRemoteKey: (Int) -> Boolean
) {
    val playable = remember(event?.id, event?.highlightClips) {
        event?.highlightClips.orEmpty().filter { !it.streamUrl.isNullOrBlank() }
    }
    var selectedId by remember(event?.id, playable) { mutableStateOf(playable.firstOrNull()?.id) }
    val selected = playable.firstOrNull { it.id == selectedId } ?: playable.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        if (selected != null) {
            HighlightClipPlayer(
                clip = selected,
                onRemoteKey = onRemoteKey,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = mainTop)
                    .width(videoWidth).height(mainHeight).clip(playerPanelShape)
                    .border(1.dp, Color(0x355A7894), playerPanelShape)
            )
        } else {
            Box(
                Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = mainTop)
                    .width(videoWidth).height(mainHeight).clip(playerPanelShape)
                    .background(AppleTvTheme.GlassPanelGradient)
                    .border(1.dp, AppleTvTheme.GlassBorder, playerPanelShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No current highlights yet", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text("New clips appear here as the broadcast publishes them.", color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Column(
            Modifier.align(Alignment.TopEnd).padding(top = mainTop, end = 28.dp)
                .width(infoWidth).height(mainHeight).clip(playerPanelShape)
                .background(AppleTvTheme.GlassPanelGradient)
                .border(1.dp, AppleTvTheme.GlassBorder, playerPanelShape)
                .padding(13.dp)
        ) {
            Text("CURRENT HIGHLIGHTS", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Text(
                event?.name ?: "Live game",
                color = AppleTvTheme.TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            if (playable.isEmpty()) {
                Box(Modifier.fillMaxSize().focusRequester(initialFocus).focusable(), contentAlignment = Alignment.Center) {
                    Text("Highlights will refresh automatically.", color = AppleTvTheme.TextTertiary, fontSize = 11.sp)
                }
            } else {
                TvLazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(playable, key = { it.id }) { clip ->
                        HighlightClipChoice(
                            clip = clip,
                            selected = clip.id == selected?.id,
                            onClick = { selectedId = clip.id },
                            modifier = if (clip == playable.first()) Modifier.focusRequester(initialFocus) else Modifier
                        )
                    }
                }
            }
        }

        selected?.let { clip ->
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 43.dp, top = mainTop + mainHeight - 63.dp)
                    .width(videoWidth - 30.dp)
                    .clip(playerPillShape).background(Color(0xA805080F)).padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text("NOW PLAYING · HIGHLIGHT", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Text(clip.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HighlightClipPlayer(
    clip: HighlightClip,
    onRemoteKey: (Int) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(clip.id, clip.streamUrl) {
        val selector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .setMaxVideoBitrate(12_000_000)
                    .setExceedVideoConstraintsIfNecessary(true)
            )
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_500, 8_000, 500, 800)
            .setTargetBufferBytes(8 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .build()
    }
    DisposableEffect(player, clip.streamUrl) {
        clip.streamUrl?.let { url ->
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
        onDispose { player.release() }
    }
    VideoPlayerSurface(player, onRemoteKey, onSurfaceClick = {}, modifier = modifier)
}

@Composable
private fun HighlightClipChoice(
    clip: HighlightClip,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember(clip.id) { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(70.dp).onFocusChanged { focused = it.isFocused }
            .clip(playerActionShape)
            .background(if (focused) Color(0xD0223349) else if (selected) Color(0xB8172437) else Color(0x750A101B))
            .border(if (focused) 1.5.dp else 1.dp, if (focused) Color.White else Color(0x315A7894), playerActionShape)
            .clickable(onClick = onClick)
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = clip.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.width(92.dp).fillMaxHeight().clip(playerPillShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(clip.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            clip.durationSeconds?.let { seconds ->
                Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", color = AppleTvTheme.TextTertiary, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun GameViewChrome(
    event: SportEvent?,
    currentChannel: IptvChannel?,
    displaySpecs: String,
    broadcastQuality: BroadcastQualityInfo,
    otherLiveEvents: List<SportEvent>,
    mainTop: androidx.compose.ui.unit.Dp,
    mainHeight: androidx.compose.ui.unit.Dp,
    videoWidth: androidx.compose.ui.unit.Dp,
    infoWidth: androidx.compose.ui.unit.Dp,
    isPlaying: Boolean,
    overlayVisible: Boolean,
    initialFocus: FocusRequester,
    onTogglePlayback: () -> Unit,
    onFullScreen: () -> Unit,
    onChooseSource: () -> Unit,
    onMultiView: () -> Unit,
    onOtherEvent: (SportEvent) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        VideoFrameChrome(
            event = event,
            currentChannel = currentChannel,
            displaySpecs = displaySpecs,
            isPlaying = isPlaying,
            overlayVisible = overlayVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 28.dp, top = mainTop)
                .width(videoWidth)
                .height(mainHeight),
            initialFocus = initialFocus,
            onTogglePlayback = onTogglePlayback,
            onFullScreen = onFullScreen,
            onChooseSource = onChooseSource,
            onMultiView = onMultiView
        )

        GameInformationPanel(
            event = event,
            broadcastQuality = broadcastQuality,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = mainTop, end = 28.dp)
                .width(infoWidth)
                .height(mainHeight)
        )

        if (otherLiveEvents.isNotEmpty()) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 18.dp)) {
                Text("OTHER LIVE GAMES", color = AppleTvTheme.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                RallyPagedRow(
                    items = otherLiveEvents.take(12),
                    key = { it.id },
                    pageSize = 4,
                    spacing = 10.dp
                ) { liveEvent, itemModifier, width ->
                    LiveGameCard(liveEvent, { onOtherEvent(liveEvent) }, itemModifier.width(width))
                }
            }
        }
    }
}

@Composable
private fun VideoFrameChrome(
    event: SportEvent?,
    currentChannel: IptvChannel?,
    displaySpecs: String,
    isPlaying: Boolean,
    overlayVisible: Boolean,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester,
    onTogglePlayback: () -> Unit,
    onFullScreen: () -> Unit,
    onChooseSource: () -> Unit,
    onMultiView: () -> Unit
) {
    Box(modifier.clip(playerPanelShape)) {
        AnimatedVisibility(visible = overlayVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0xB8000000),
                        .30f to Color.Transparent,
                        .62f to Color.Transparent,
                        1f to Color(0xE6000000)
                    )
                )
            ) {
        if (event != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.isLive()) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(AppleTvTheme.AccentRed))
                        Spacer(Modifier.width(7.dp))
                        Text("LIVE", color = AppleTvTheme.AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(event.league, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                }
                Text(event.gameStatusDetail.orEmpty(), color = AppleTvTheme.TextSecondary, fontSize = 10.sp)
            }

            Column(Modifier.align(Alignment.TopCenter).padding(top = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(event.scoreLine(), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(event.awayTeam?.abbreviation, event.homeTeam?.abbreviation).joinToString("   ·   "),
                    color = AppleTvTheme.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .8.sp
                )
            }
        } else {
            Text(
                currentChannel?.name ?: "Live stream",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

            Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerButton(
                    if (isPlaying) "Pause" else "Play",
                    onTogglePlayback,
                    true,
                    Modifier.focusRequester(initialFocus),
                    iconRes = if (isPlaying) R.drawable.ic_rally_pause else R.drawable.ic_rally_play
                )
                if (event?.isLive() == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(AppleTvTheme.AccentRed))
                        Spacer(Modifier.width(5.dp))
                        Text("LIVE", color = AppleTvTheme.AccentRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerMetaPill(displaySpecs)
                PlayerButton("Sources", onChooseSource)
                PlayerButton("Multi", onMultiView)
                PlayerButton("Full", onFullScreen)
            }
            }
        }
    }
}

}

@Composable
private fun GameInformationPanel(
    event: SportEvent?,
    broadcastQuality: BroadcastQualityInfo,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(event?.id) { mutableStateOf(0) }
    Column(
        modifier.clip(playerPanelShape).background(AppleTvTheme.GlassPanelGradient).border(1.dp, AppleTvTheme.GlassBorder, playerPanelShape).padding(15.dp)
    ) {
        if (event == null) {
            Text("GAME VIEW", color = AppleTvTheme.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Live channel", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("Game data is available when playback starts from a matchup card.", color = AppleTvTheme.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        } else {
            Row(Modifier.fillMaxWidth().clip(playerActionShape).background(Color(0x80172437)).border(1.dp, Color(0x385A7894), playerActionShape).padding(2.dp)) {
                GameInfoTab("STATS", selectedTab == 0, { selectedTab = 0 }, Modifier.weight(1f))
                GameInfoTab("PLAYERS", selectedTab == 1, { selectedTab = 1 }, Modifier.weight(1f))
                GameInfoTab("PLAYS", selectedTab == 2, { selectedTab = 2 }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            if (selectedTab == 1) {
                val tables = event.playerStatTables
                    .distinctBy { it.teamId ?: it.teamAbbreviation }
                    .take(2)
                if (tables.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        tables.forEach { table ->
                            GameViewPlayerTeamColumn(table, Modifier.weight(1f).fillMaxHeight())
                        }
                        repeat(2 - tables.size) { Spacer(Modifier.weight(1f)) }
                    }
                } else if (event.playerLeaders.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        val awayAbbr = event.awayTeam?.abbreviation
                        val homeAbbr = event.homeTeam?.abbreviation
                        GameViewLeaderTeamColumn(
                            teamName = event.awayTeam?.name,
                            abbreviation = awayAbbr,
                            logoUrl = event.awayTeamBadge,
                            leaders = event.playerLeaders.filter { it.teamAbbr.equals(awayAbbr, true) },
                            modifier = Modifier.weight(1f)
                        )
                        GameViewLeaderTeamColumn(
                            teamName = event.homeTeam?.name,
                            abbreviation = homeAbbr,
                            logoUrl = event.homeTeamBadge,
                            leaders = event.playerLeaders.filter { it.teamAbbr.equals(homeAbbr, true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Player statistics are not published for this matchup yet.", color = AppleTvTheme.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("STREAM QUALITY", color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Text(broadcastQuality.badgeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            } else if (selectedTab == 2) {
                Text("GAME TIMELINE", color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Spacer(Modifier.height(5.dp))
                if (event.plays.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Play-by-play will appear when the official game feed publishes it.", color = AppleTvTheme.TextSecondary, fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        event.plays.take(5).forEach { play ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (play.isScoringPlay) Color(0x1F6FCFFF) else Color(0x0FFFFFFF)).padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(listOfNotNull(play.period?.let { "P$it" }, play.clock).joinToString(" · "), color = AppleTvTheme.TextTertiary, fontSize = 7.sp, modifier = Modifier.width(45.dp))
                                Text(play.text, color = Color.White, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (play.awayScore != null && play.homeScore != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("${play.awayScore}–${play.homeScore}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("OFFICIAL DATA", color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Text(broadcastQuality.badgeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                TeamScoreRow(event)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PlayerMetaPill(event.league)
                    PlayerMetaPill(broadcastQuality.badgeText)
                }
                val insights = remember(event) { event.gameInsights() }
                val comparisonStats = remember(event) { event.teamStats.filterNot { it.label.isPredictionMetric() } }
                if (comparisonStats.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("TEAM STATS", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Spacer(Modifier.height(3.dp))
                    comparisonStats.take(3).forEach { stat ->
                        Row(Modifier.fillMaxWidth().height(15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stat.awayValue, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(stat.label, color = AppleTvTheme.TextSecondary, fontSize = 10.sp)
                            Text(stat.homeValue, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (insights.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Text("GAME ANALYTICS", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Spacer(Modifier.height(3.dp))
                    insights.take(2).forEach { insight ->
                        Row(Modifier.fillMaxWidth().height(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(4.dp).clip(CircleShape).background(AppleTvTheme.RallyCyan))
                            Spacer(Modifier.width(6.dp))
                            Text(insight, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (event.playerLeaders.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Text("PLAYER LEADERS", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Spacer(Modifier.height(3.dp))
                    event.playerLeaders.take(if (insights.isEmpty()) 3 else 2).forEach { leader ->
                        PlayerLeaderRow(leader)
                    }
                } else if (comparisonStats.isEmpty() && insights.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(event.venue ?: "Live matchup information", color = AppleTvTheme.TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun GameViewPlayerTeamColumn(
    table: com.shiv.rally.domain.model.PlayerStatTable,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(table.teamLogoUrl, null, Modifier.size(27.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(7.dp))
            Text(table.teamName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PLAYER", color = AppleTvTheme.TextTertiary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text(table.labels.take(3).joinToString("  ").ifBlank { "STATS" }, color = AppleTvTheme.TextTertiary, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        table.rows.take(4).forEach { player ->
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(player.headshotUrl, null, Modifier.size(21.dp).clip(CircleShape).background(Color(0x14FFFFFF)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(player.shortName ?: player.displayName, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(player.jersey, player.position).joinToString(" · "), color = AppleTvTheme.TextTertiary, fontSize = 6.sp, maxLines = 1)
                }
                Text(player.stats.take(3).joinToString("  "), color = AppleTvTheme.TextSecondary, fontSize = 7.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GameViewLeaderTeamColumn(
    teamName: String?,
    abbreviation: String?,
    logoUrl: String?,
    leaders: List<com.shiv.rally.domain.model.PlayerLeader>,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(logoUrl, null, Modifier.size(27.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(7.dp))
            Text(
                formatTeamDisplayName(teamName).takeUnless { it.isBlank() || it == "Team" } ?: abbreviation.orEmpty(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(7.dp))
        if (leaders.isEmpty()) {
            Text("No verified player data yet.", color = AppleTvTheme.TextSecondary, fontSize = 8.sp, lineHeight = 12.sp)
        } else {
            leaders.take(4).forEach { leader -> PlayerLeaderRow(leader) }
        }
    }
}

@Composable
private fun GameInfoTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember(label) { mutableStateOf(false) }
    Box(
        modifier.height(34.dp).onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(7.dp))
            .background(
                when {
                    focused -> Color(0xB0233449)
                    selected -> Color(0x781A293C)
                    else -> Color.Transparent
                }
            )
            .border(
                if (focused) 1.5.dp else 1.dp,
                when {
                    focused -> Color(0xD6B9D8EA)
                    selected -> Color(0x3D7A94AF)
                    else -> Color.Transparent
                },
                RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected || focused) Color.White else AppleTvTheme.TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .35.sp)
    }
}

@Composable
private fun PlayerLeaderRow(leader: com.shiv.rally.domain.model.PlayerLeader) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
            val imageUrl = leader.headshotUrl ?: leader.teamLogoUrl
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(imageUrl, null, Modifier.size(18.dp), contentScale = ContentScale.Fit)
            } else {
                Text(leader.teamAbbr?.take(3).orEmpty(), color = AppleTvTheme.TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(leader.playerShortName, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(leader.position, leader.category).joinToString(" · "), color = AppleTvTheme.TextTertiary, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(6.dp))
        Text(leader.statDisplay, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TeamScoreRow(event: SportEvent) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamMark(event.awayTeamBadge, event.awayTeam?.abbreviation ?: "AWAY")
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(formatTeamDisplayName(event.awayTeam?.name), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatTeamDisplayName(event.homeTeam?.name), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(event.scoreAway?.toString() ?: "–", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(event.scoreHome?.toString() ?: "–", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(9.dp))
        TeamMark(event.homeTeamBadge, event.homeTeam?.abbreviation ?: "HOME")
    }
}

@Composable
private fun TeamMark(url: String?, abbreviation: String) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(url, null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
        } else {
            Text(abbreviation.take(3), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiveGameCard(event: SportEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.height(106.dp),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(scale = 1f, focusedScale = AppleTvTheme.CardFocusScale),
        colors = CardDefaults.colors(containerColor = Color(0xD90A101B), focusedContainerColor = Color(0xF2172437)),
        border = CardDefaults.border(
            border = Border(border = BorderStroke(1.dp, Color(0x20FFFFFF)), shape = RoundedCornerShape(10.dp)),
            focusedBorder = Border(border = BorderStroke(2.dp, AppleTvTheme.RallyCyan), shape = RoundedCornerShape(10.dp))
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(painterResource(playerSportArtwork(event.sport)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x5C02060B), Color(0xF00A101B)))))
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(AppleTvTheme.AccentRed))
                    Spacer(Modifier.width(5.dp))
                    Text("LIVE", color = AppleTvTheme.AccentRed, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(7.dp))
                    Text(event.league, color = AppleTvTheme.TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TeamMark(event.awayTeamBadge, event.awayTeam?.abbreviation ?: "AWAY")
                    Text(event.scoreLine(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TeamMark(event.homeTeamBadge, event.homeTeam?.abbreviation ?: "HOME")
                }
                Text(
                    listOfNotNull(event.awayTeam?.abbreviation, event.gameStatusDetail, event.homeTeam?.abbreviation).joinToString("   ·   "),
                    color = AppleTvTheme.TextSecondary,
                    fontSize = 7.sp,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun playerSportArtwork(sport: String): Int = when {
    sport.contains("football", true) -> R.drawable.card_landscape_football_v2
    sport.contains("basketball", true) -> R.drawable.card_landscape_basketball_v2
    sport.contains("baseball", true) -> R.drawable.card_landscape_baseball_v2
    sport.contains("hockey", true) -> R.drawable.card_landscape_hockey_v2
    sport.contains("soccer", true) -> R.drawable.card_landscape_soccer_v2
    else -> R.drawable.rally_ambient_background_v2
}

@Composable
private fun PlaybackErrorOverlay(
    message: String,
    initialFocus: FocusRequester,
    canChooseSource: Boolean,
    onRetry: () -> Unit,
    onChooseSource: () -> Unit,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xD9000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(470.dp).clip(playerPanelShape).background(AppleTvTheme.Slate).border(1.dp, Color(0x25FFFFFF), playerPanelShape).padding(27.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Playback interrupted", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(message, color = AppleTvTheme.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(21.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PlayerButton("Retry", onRetry, true, Modifier.focusRequester(initialFocus))
                if (canChooseSource) PlayerButton("Choose Source", onChooseSource)
                PlayerButton("Back", onBack)
            }
        }
    }
}

@Composable
private fun OtherLiveGameActionDialog(
    currentEvent: SportEvent?,
    targetEvent: SportEvent,
    onWatchInMultiView: () -> Unit,
    onSwitchFullScreen: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color(0xB8000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(470.dp).clip(AppleTvTheme.DialogShape).background(AppleTvTheme.Slate).border(1.dp, Color(0x28FFFFFF), AppleTvTheme.DialogShape).padding(27.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(targetEvent.scoreLine(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(5.dp))
            Text("${targetEvent.league} · ${targetEvent.gameStatusDetail ?: "LIVE"}", color = AppleTvTheme.TextSecondary, fontSize = 12.sp)
            currentEvent?.let {
                Spacer(Modifier.height(10.dp))
                Text("Now playing ${it.awayTeam?.abbreviation ?: ""} at ${it.homeTeam?.abbreviation ?: ""}", color = AppleTvTheme.TextTertiary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                PlayerButton("Watch Both in Multi-View", onWatchInMultiView, true, Modifier.fillMaxWidth().focusRequester(firstFocus))
                PlayerButton("Switch to This Game", onSwitchFullScreen, modifier = Modifier.fillMaxWidth())
                PlayerButton("Cancel", onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PlayerButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    iconRes: Int? = null
) {
    RallyControlButton(label, onClick, modifier, primary, iconRes)
}

@Composable
private fun PlayerMetaPill(label: String) {
    Box(
        Modifier.clip(playerPillShape).background(Color(0x28FFFFFF)).border(1.dp, Color(0x20FFFFFF), playerPillShape).padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun SportEvent.isLive(): Boolean = status == EventStatus.LIVE || status == EventStatus.HALFTIME

private fun SportEvent.scoreLine(): String {
    val away = awayTeam?.abbreviation?.ifBlank { null } ?: formatTeamDisplayName(awayTeam?.name)
    val home = homeTeam?.abbreviation?.ifBlank { null } ?: formatTeamDisplayName(homeTeam?.name)
    return if (scoreAway != null && scoreHome != null) {
        "$away $scoreAway  –  $scoreHome $home"
    } else {
        "$away at $home"
    }
}

internal fun SportEvent.gameInsights(): List<String> {
    val away = awayTeam?.abbreviation?.takeIf { it.isNotBlank() } ?: "Away"
    val home = homeTeam?.abbreviation?.takeIf { it.isNotBlank() } ?: "Home"
    val insights = mutableListOf<String>()

    winProbability.lastOrNull()?.let { point ->
        val homeChance = (point.homeWinPercentage * 100).toInt()
        val leader = if (homeChance >= 50) home else away
        val chance = if (homeChance >= 50) homeChance else 100 - homeChance
        insights += "$leader win probability $chance%"
    }

    fun addLeaderInsight(statLabel: String, text: (String, String) -> String) {
        val stat = teamStats.firstOrNull { it.label.equals(statLabel, ignoreCase = true) } ?: return
        val awayNumber = stat.awayValue.metricNumber() ?: return
        val homeNumber = stat.homeValue.metricNumber() ?: return
        if (awayNumber == homeNumber) return
        val leader = if (awayNumber > homeNumber) away else home
        val leaderValue = if (awayNumber > homeNumber) stat.awayValue else stat.homeValue
        insights += text(leader, leaderValue)
    }

    if (winProbability.isEmpty()) {
        addLeaderInsight("Win Prob") { leader, value -> "$leader win probability $value" }
    }
    addLeaderInsight("Possession") { leader, value ->
        val percentage = if (value.contains('%')) value else "$value%"
        "$leader controls $percentage possession"
    }

    val shots = teamStats.firstOrNull {
        it.label.equals("SOG", ignoreCase = true) || it.label.contains("shots", ignoreCase = true)
    }
    if (shots != null) {
        val awayShots = shots.awayValue.metricNumber()
        val homeShots = shots.homeValue.metricNumber()
        if (awayShots != null && homeShots != null && awayShots != homeShots) {
            val leader = if (awayShots > homeShots) away else home
            insights += "$leader leads ${shots.label} ${shots.awayValue}–${shots.homeValue}"
        }
    }

    if (scoreAway != null && scoreHome != null && scoreAway != scoreHome) {
        val leader = if (scoreAway > scoreHome) away else home
        insights += "$leader leads by ${kotlin.math.abs(scoreAway - scoreHome)}"
    }

    teamStats.firstOrNull { it.label.equals("Spread", true) || it.label.equals("Line", true) }?.let {
        insights += "Line · $away ${it.awayValue}  $home ${it.homeValue}"
    }
    teamStats.firstOrNull { it.label.equals("Over/Under", true) }?.let {
        insights += "Total · ${it.awayValue.removePrefix("O ")}"
    }
    teamStats.firstOrNull { it.label.equals("Moneyline", true) }?.let {
        insights += "Moneyline · $away ${it.awayValue}  $home ${it.homeValue}"
    }

    return insights.distinct().take(3)
}

private fun String.metricNumber(): Double? =
    Regex("-?\\d+(?:\\.\\d+)?").find(replace(",", ""))?.value?.toDoubleOrNull()

private fun String.isPredictionMetric(): Boolean =
    equals("Spread", true) || equals("Line", true) || equals("Over/Under", true) ||
        equals("Moneyline", true) || equals("Win Prob", true)

private fun PlaybackException.isVideoDecoderFailure(): Boolean {
    val decoderError = errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
        errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
    val resourceFailure = generateSequence(cause) { it.cause }.any {
        it is OutOfMemoryError || it.javaClass.name.contains("MediaCodec", ignoreCase = true)
    }
    return decoderError || resourceFailure
}

private fun PlaybackException.isBehindLiveWindowFailure(): Boolean =
    generateSequence(cause) { it.cause }.any {
        it is androidx.media3.exoplayer.source.BehindLiveWindowException
    }

private fun SportEvent.broadcastStations(): List<String> {
    val fromStats = liveStats["TV Broadcast"]
        ?.split(",", "/", "&", "+")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()
    if (fromStats.isNotEmpty()) return fromStats
    val context = eventContextTitle.orEmpty()
    return if (context.startsWith("TV:", ignoreCase = true)) {
        context.substringAfter(":").split(",").map(String::trim).filter(String::isNotEmpty)
    } else emptyList()
}
