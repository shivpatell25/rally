@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@file:androidx.media3.common.util.UnstableApi

package com.shiv.rally.presentation.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.AndroidExternalSurfaceZOrder
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.AndroidExternalSurfaceScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.SubtitleView
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
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
import com.shiv.rally.presentation.common.RallyTvActionButton
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.common.RallyControlButton
import com.shiv.rally.presentation.common.RallyPagedRow
import com.shiv.rally.presentation.home.formatTeamDisplayName
import com.shiv.rally.presentation.home.matchupTeamName
import com.shiv.rally.presentation.home.getEditorialPhoto
import com.shiv.rally.presentation.home.getSportBackdrop
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
    val renderedFrames: Int = 0,
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

private data class MediaTrackChoice(
    val label: String,
    val group: Tracks.Group?,
    val trackIndex: Int?,
    val selected: Boolean
)

private fun mediaTrackChoices(tracks: Tracks, type: Int, captionsDisabled: Boolean): List<MediaTrackChoice> {
    val trackChoices = tracks.groups
        .filter { it.type == type }
        .flatMap { group ->
            (0 until group.length)
                .filter(group::isTrackSupported)
                .map { index ->
                    val format = group.getTrackFormat(index)
                    val language = format.language?.takeUnless { it == "und" }
                    val label = listOfNotNull(
                        language?.let { Locale.forLanguageTag(it).getDisplayName(Locale.getDefault()) },
                        format.label?.takeIf { it.isNotBlank() },
                        format.codecs?.uppercase(Locale.getDefault())
                    ).distinct().joinToString(" · ").ifBlank { "Track ${index + 1}" }
                    MediaTrackChoice(label, group, index, group.isTrackSelected(index))
                }
        }
    return buildList {
        add(MediaTrackChoice("Automatic", null, null, trackChoices.none { it.selected } && !captionsDisabled))
        if (type == C.TRACK_TYPE_TEXT) add(MediaTrackChoice("Off", null, -1, captionsDisabled))
        addAll(trackChoices)
    }
}

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
    onSurfaceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var videoAspect by remember(exoPlayer) { mutableStateOf(16f / 9f) }
    var cues by remember(exoPlayer) { mutableStateOf<List<Cue>>(emptyList()) }
    DisposableEffect(exoPlayer) {
        fun updateSize(size: VideoSize) {
            if (size.width > 0 && size.height > 0) videoAspect =
                size.width * size.pixelWidthHeightRatio / size.height
        }
        updateSize(exoPlayer.videoSize)
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) = updateSize(videoSize)
            override fun onCues(cueGroup: CueGroup) { cues = cueGroup.cues }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    BoxWithConstraints(modifier.pointerInput(onSurfaceClick) {
        detectTapGestures { onSurfaceClick() }
    }, contentAlignment = Alignment.Center) {
        val surfaceWidth = minOf(maxWidth, maxHeight * videoAspect)
        val surfaceHeight = surfaceWidth / videoAspect
        val bindSurface: AndroidExternalSurfaceScope.() -> Unit = {
            onSurface { surface, _, _ ->
                exoPlayer.setVideoSurface(surface)
                surface.onDestroyed { runCatching { exoPlayer.clearVideoSurface(surface) } }
            }
        }
        // Android 14's SurfaceView/Compose synchronization can leave the decoded
        // picture obscured or cropped. Embed that surface in the UI layer on API 34;
        // other TV versions retain the lower-cost external video surface.
        key(exoPlayer) {
        if (android.os.Build.VERSION.SDK_INT == 34) {
            AndroidEmbeddedExternalSurface(
                modifier = Modifier.width(surfaceWidth).height(surfaceHeight),
                onInit = bindSurface
            )
        } else {
            AndroidExternalSurface(
                modifier = Modifier.width(surfaceWidth).height(surfaceHeight),
                zOrder = AndroidExternalSurfaceZOrder.MediaOverlay,
                onInit = bindSurface
            )
        }
        }
        AndroidView(
            factory = { context -> SubtitleView(context).apply {
                isFocusable = false
                setUserDefaultStyle()
                setFractionalTextSize(.035f)
            } },
            update = { it.setCues(cues) },
            modifier = Modifier.width(surfaceWidth).height(surfaceHeight)
        )
    }
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
            clipTitle = viewModel.clipTitle,
            streamUrl = current.streamUrl,
            playbackRequestId = current.playbackRequestId,
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
            streamMimeType = current.streamMimeType,
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
            onPlaybackFailure = { viewModel.recoverFromPlaybackFailure(it, current.playbackRequestId) },
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
    clipTitle: String? = null,
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
    streamMimeType: String? = null,
    isExternalStream: Boolean = false,
    recoveryAttempt: Int = 0,
    recoveryStatus: String? = null,
    terminalPlaybackError: String? = null,
    lowLatencyMode: Boolean = true,
    adaptiveQualityEnabled: Boolean = true,
    audioNormalizationEnabled: Boolean = true,
    sourceHealthScore: Int = 0,
    playbackRequestId: Long = 0,
    onBack: () -> Unit,
    onSelectOtherEvent: (String) -> Unit,
    onSwitchStream: (String) -> Unit = {},
    onPlaybackReady: (String, Long) -> Unit = { _, _ -> },
    onPlaybackStall: (String) -> Unit = {},
    onPlaybackFailure: (String) -> Unit = {},
    onNavigateToMultiView: (channelId: String, eventId: String?, pairedEventId: String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val playbackView = LocalView.current
    DisposableEffect(playbackView) {
        val previous = playbackView.keepScreenOn
        playbackView.keepScreenOn = true
        onDispose { playbackView.keepScreenOn = previous }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var controlsVisible by remember { mutableStateOf(event == null) }
    var gameViewVisible by remember { mutableStateOf(event != null) }
    var gameViewOverlayVisible by remember { mutableStateOf(event != null) }
    var currentHighlightsVisible by remember { mutableStateOf(false) }
    var defaultPresentationApplied by remember { mutableStateOf(event != null) }
    var selectedHighlight by remember { mutableStateOf<HighlightClip?>(null) }
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
    val gameViewStatsFocus = remember { FocusRequester() }
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
    val playbackSession = remember(context, playbackProfile, lowLatencyMode) {
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
        val renderersFactory = rallyRenderersFactory(context)
        val sessionPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(sessionTrackSelector)
            .setLoadControl(loadControl)
            .build()
        PlaybackSession(sessionPlayer, sessionTrackSelector)
    }
    val mediaSourceFactory = remember(context, safeHeaders, isExternalStream, macAddress, token) {
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

        DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource)
    }
    val exoPlayer = playbackSession.player
    val trackSelector = playbackSession.trackSelector
    var currentTracks by remember(exoPlayer) { mutableStateOf(exoPlayer.currentTracks) }
    var trackPickerType by remember { mutableStateOf<Int?>(null) }
    var captionsDisabled by remember(exoPlayer) { mutableStateOf(false) }
    val hasAudioTracks = currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && (0 until it.length).any(it::isTrackSupported) }
    val hasTextTracks = currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && (0 until it.length).any(it::isTrackSupported) }
    val highlightsAreVisible by rememberUpdatedState(currentHighlightsVisible)
    val playbackStartedAt = remember(playbackRequestId, streamUrl) { android.os.SystemClock.elapsedRealtime() }
    var readyReported by remember(playbackRequestId, streamUrl) { mutableStateOf(false) }
    var stallReportedAt by remember(playbackRequestId, streamUrl) { mutableStateOf(0L) }
    val audioNormalizer = remember { AudioNormalizationSession() }
    var highlightsSessionStarted by remember(streamUrl) { mutableStateOf(false) }
    var liveWasPlayingBeforeHighlights by remember(streamUrl) { mutableStateOf(true) }
    var liveVolumeBeforeHighlights by remember(streamUrl) { mutableStateOf(1f) }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        var resumeAfterForeground = false
        var stoppedForBackground = false
        var returnToLiveEdge = false
        val observer = LifecycleEventObserver { _, lifecycleEvent ->
            when (lifecycleEvent) {
                Lifecycle.Event.ON_PAUSE -> {
                    resumeAfterForeground = exoPlayer.playWhenReady && !highlightsAreVisible
                    returnToLiveEdge = exoPlayer.isCurrentMediaItemLive
                    stoppedForBackground = exoPlayer.mediaItemCount > 0 && !highlightsAreVisible
                    // A paused player still owns a decoder and an upstream
                    // connection. Release both before Multiview/another player
                    // enters, preserving the media item for navigation back.
                    exoPlayer.stop()
                }
                Lifecycle.Event.ON_RESUME -> if (stoppedForBackground && !highlightsAreVisible) {
                    if (returnToLiveEdge) exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = resumeAfterForeground
                    resumeAfterForeground = false
                    stoppedForBackground = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    var reconnectAttempt by remember(playbackRequestId, streamUrl) { mutableIntStateOf(0) }
    var reconnectVersion by remember(playbackRequestId, streamUrl) { mutableIntStateOf(0) }
    var resumeOnLoad by remember(playbackRequestId, streamUrl) { mutableStateOf(true) }
    val latestFailure by rememberUpdatedState(onPlaybackFailure)
    fun reconnect(reason: String) {
        if (reconnectAttempt < 2) {
            resumeOnLoad = exoPlayer.playWhenReady
            reconnectAttempt++
            reconnectVersion++
            onPlaybackStall(streamUrl)
        } else {
            latestFailure(reason)
        }
    }
    // Release the decoder only when leaving playback, never when its source changes.
    DisposableEffect(exoPlayer) {
        onDispose { audioNormalizer.release(); exoPlayer.release() }
    }
    DisposableEffect(exoPlayer, playbackRequestId, streamUrl, mediaSourceFactory, audioNormalizationEnabled) {
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
                    reconnect(message)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    streamSpecs = extractSpecs(exoPlayer.videoFormat, streamSpecs)
                }
            }

            override fun onRenderedFirstFrame() {
                if (!readyReported) {
                    readyReported = true
                    onPlaybackReady(streamUrl, android.os.SystemClock.elapsedRealtime() - playbackStartedAt)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
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
                    if (!readyReported && exoPlayer.currentTracks.groups.none { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }) {
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
                canRestart = exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_ENDED
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                canRestart = exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_ENDED
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED
                resumeOnLoad = playWhenReady
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioNormalizationEnabled) audioNormalizer.attach(audioSessionId)
                else audioNormalizer.release()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(exoPlayer, playbackRequestId, streamUrl, streamMimeType, mediaSourceFactory, reconnectVersion) {
        streamSpecs = VideoStreamSpecs()
        playbackError = null
        if (streamUrl.isBlank()) {
            playbackError = "No playable URL was returned for this source."
        } else {
            runCatching {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(streamUrl))
                    .setMimeType(streamMimeType)
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
                val shouldResume = resumeOnLoad
                exoPlayer.stop()
                exoPlayer.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = shouldResume
            }.onFailure { onPlaybackFailure(it.localizedMessage ?: "Unable to start playback.") }
        }
    }

    LaunchedEffect(exoPlayer, playbackRequestId, streamUrl, mediaSourceFactory, reconnectVersion, currentHighlightsVisible) {
        if (currentHighlightsVisible || streamUrl.isBlank()) return@LaunchedEffect
        val monitor = PlaybackProgressMonitor(android.os.SystemClock.elapsedRealtime())
        var healthySince = android.os.SystemClock.elapsedRealtime()
        while (true) {
            delay(1_000)
            val now = android.os.SystemClock.elapsedRealtime()
            if (!exoPlayer.isPlaying) healthySince = now
            else if (now - healthySince >= 60_000) { reconnectAttempt = 0; healthySince = now }
            val expectsVideo = exoPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            val reason = monitor.check(
                nowMs = android.os.SystemClock.elapsedRealtime(),
                wantsPlayback = exoPlayer.playWhenReady && exoPlayer.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                buffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                ready = exoPlayer.playbackState == Player.STATE_READY,
                positionMs = exoPlayer.currentPosition,
                renderedFrames = if (expectsVideo) exoPlayer.videoDecoderCounters?.renderedOutputBufferCount ?: 0 else null
            )
            if (reason != null) {
                reconnect(reason.message)
                break
            }
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
                renderedFrames = exoPlayer.videoDecoderCounters?.renderedOutputBufferCount ?: 0,
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
            trackPickerType != null -> trackPickerType = null
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
                    runCatching { gameViewStatsFocus.requestFocus() }
                } else if (!sourcePickerVisible) {
                    controlsVisible = !controlsVisible
                }
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> when {
                !controlsVisible && !gameViewVisible -> {
                    controlsVisible = true
                    true
                }
                else -> false
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> when {
                !controlsVisible && !gameViewVisible -> { controlsVisible = true; true }
                else -> false
            }
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> when {
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

    LaunchedEffect(controlsVisible, gameViewVisible, gameViewOverlayVisible, currentHighlightsVisible, sourcePickerVisible, selectedOtherEvent, playbackError, diagnosticsVisible, trackPickerType) {
        val target = when {
            diagnosticsVisible || trackPickerType != null || sourcePickerVisible || selectedOtherEvent != null -> null
            playbackError != null -> errorFocus
            currentHighlightsVisible -> highlightsFocus
            gameViewVisible -> gameViewFocus
            controlsVisible -> controlFocus
            else -> rootFocus
        }
        if (target != null) {
            repeat(3) { attempt ->
                if (attempt > 0) delay(80) else delay(120)
                if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(controlsVisible, gameViewVisible, sourcePickerVisible, selectedOtherEvent, playbackError, interactionVersion) {
        if (controlsVisible && !gameViewVisible && !sourcePickerVisible && selectedOtherEvent == null && playbackError == null) {
            delay(6_500)
            controlsVisible = false
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(if (gameViewVisible) Color.Transparent else Color.Black)
            .focusRequester(rootFocus)
            .focusProperties {
                canFocus = !controlsVisible && !gameViewVisible &&
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
        val gameViewHorizontalPadding = 26.dp
        val gameViewGap = 12.dp
        val gameViewAvailableWidth = maxWidth - (gameViewHorizontalPadding * 2) - gameViewGap
        val gameViewMainHeight = minOf(300.dp, gameViewAvailableWidth * (9f / 25f))
        val gameViewTop = 80.dp
        val gameViewVideoWidth = gameViewMainHeight * (16f / 9f)
        val gameViewInfoWidth = gameViewAvailableWidth - gameViewVideoWidth
        // Both columns end at the same baseline: 90dp to the moments rail, 51dp
        // for its thumbnail plus padding. Keep that baseline inside the safe area.
        val gameViewInfoHeight = minOf(gameViewMainHeight + 141.dp, maxHeight - gameViewTop - 20.dp)

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
                onSurfaceClick = {
                    if (gameViewVisible) gameViewOverlayVisible = true else controlsVisible = true
                    interactionVersion++
                },
                modifier = videoModifier
            )
        }

        if (gameViewVisible) {
            GameViewChrome(
                    event = event,
                    currentChannel = currentChannel,
                    sourceLabel = currentChannel?.name ?: activeAddonSource?.addonName?.takeIf(String::isNotBlank) ?: "Selected source",
                    sourceLabels = streamCandidates.map { it.title }.distinct(),
                    signalQuality = listOfNotNull(streamSpecs.resolution, streamSpecs.dynamicRange).joinToString(" · ").ifBlank { "Signal info" },
                    otherLiveEvents = otherLiveEvents,
                    mainTop = gameViewTop,
                    mainHeight = gameViewMainHeight,
                    videoWidth = gameViewVideoWidth,
                    infoWidth = gameViewInfoWidth,
                    infoHeight = gameViewInfoHeight,
                    isPlaying = isPlaying,
                    initialFocus = gameViewFocus,
                    segmentFocus = gameViewMenuFocus,
                    statsFocus = gameViewStatsFocus,
                    canRestart = canRestart,
                    canChooseAudio = hasAudioTracks,
                    canChooseCaptions = hasTextTracks,
                    onTogglePlayback = {
                        if (exoPlayer.playWhenReady) exoPlayer.pause() else {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0L)
                            exoPlayer.play()
                        }
                    },
                    onRestart = {
                        if (exoPlayer.isCurrentMediaItemSeekable) exoPlayer.seekTo(0L) else {
                            exoPlayer.seekToDefaultPosition()
                            exoPlayer.prepare()
                        }
                        exoPlayer.playWhenReady = true
                    },
                    onFullscreen = {
                        currentHighlightsVisible = false
                        gameViewVisible = false
                        gameViewOverlayVisible = false
                        controlsVisible = true
                    },
                    onAudio = { trackPickerType = C.TRACK_TYPE_AUDIO },
                    onCaptions = { trackPickerType = C.TRACK_TYPE_TEXT },
                    onDiagnostics = { diagnosticsVisible = true },
                    onChooseSource = { sourcePickerVisible = true },
                    onMultiView = {
                        onNavigateToMultiView(currentChannel?.id ?: streamUrl, event?.id, null)
                    },
                    onKeyMoments = { clip ->
                        selectedHighlight = clip
                        currentHighlightsVisible = true
                        gameViewOverlayVisible = false
                    },
                    onOtherEvent = { selectedOtherEvent = it }
                )
            if (currentHighlightsVisible) {
                CurrentHighlightsChrome(
                    event = event,
                    selectedClip = selectedHighlight,
                    mainTop = gameViewTop,
                    mainHeight = gameViewMainHeight,
                    videoWidth = gameViewVideoWidth,
                    initialFocus = highlightsFocus,
                    onBackToLive = { currentHighlightsVisible = false },
                    onRemoteKey = ::handleRemoteKey
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !gameViewVisible,
            enter = fadeIn(androidx.compose.animation.core.tween(160)),
            exit = fadeOut(androidx.compose.animation.core.tween(120))
        ) {
            PlaybackHud(
                clipTitle = clipTitle,
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
                isPlaying = isPlaying,
                onTogglePlayback = {
                    if (exoPlayer.playWhenReady) exoPlayer.pause() else {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0L)
                            exoPlayer.play()
                        }
                },
                canChooseAudio = hasAudioTracks,
                canChooseCaptions = hasTextTracks,
                onAudio = { trackPickerType = C.TRACK_TYPE_AUDIO },
                onCaptions = { trackPickerType = C.TRACK_TYPE_TEXT },
                canRestart = canRestart,
                onRestart = {
                    if (exoPlayer.isCurrentMediaItemSeekable) exoPlayer.seekTo(0L) else {
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.prepare()
                    }
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
                    reconnectAttempt = 0
                    resumeOnLoad = true
                    reconnectVersion++
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

        trackPickerType?.let { type ->
            MediaTrackPickerOverlay(
                title = if (type == C.TRACK_TYPE_AUDIO) "Audio track" else "Captions",
                choices = mediaTrackChoices(currentTracks, type, captionsDisabled),
                onSelect = { choice ->
                    val parameters = trackSelector.parameters.buildUpon()
                        .setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT && choice.trackIndex == -1)
                        .clearOverridesOfType(type)
                    if (choice.group != null && choice.trackIndex != null) {
                        parameters.addOverride(TrackSelectionOverride(choice.group.mediaTrackGroup, listOf(choice.trackIndex)))
                    }
                    if (type == C.TRACK_TYPE_TEXT) captionsDisabled = choice.trackIndex == -1
                    trackSelector.parameters = parameters.build()
                    trackPickerType = null
                },
                onDismiss = { trackPickerType = null }
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
        Modifier.fillMaxSize().background(Color(0x75000000)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            Modifier.padding(end = 28.dp).width(310.dp)
                .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup().clip(playerPanelShape)
                .background(AppleTvTheme.GlassPanelGradient)
                .border(1.dp, AppleTvTheme.GlassBorder, playerPanelShape)
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
            DiagnosticRow("Rendered frames", snapshot.renderedFrames.toString())
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
private fun MediaTrackPickerOverlay(
    title: String,
    choices: List<MediaTrackChoice>,
    onSelect: (MediaTrackChoice) -> Unit,
    onDismiss: () -> Unit
) {
    val firstChoiceFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstChoiceFocus.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(360.dp).focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()
                .clip(playerPanelShape).background(AppleTvTheme.GlassPanelGradient)
                .border(1.dp, AppleTvTheme.GlassBorder, playerPanelShape).padding(20.dp)
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            TvLazyColumn(Modifier.heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(choices) { choice ->
                    PlayerButton(
                        if (choice.selected) "✓  ${choice.label}" else choice.label,
                        { onSelect(choice) },
                        modifier = Modifier.fillMaxWidth().then(if (choice == choices.first()) Modifier.focusRequester(firstChoiceFocus) else Modifier)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            PlayerButton("Close", onDismiss, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun PlaybackHud(
    clipTitle: String?,
    event: SportEvent?,
    currentChannel: IptvChannel?,
    displaySpecs: String,
    firstFocus: FocusRequester,
    onBack: () -> Unit,
    onGameView: () -> Unit,
    onChooseSource: () -> Unit,
    onDiagnostics: () -> Unit,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    canChooseAudio: Boolean,
    canChooseCaptions: Boolean,
    onAudio: () -> Unit,
    onCaptions: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerButton("‹ Back", onBack)
            Spacer(Modifier.weight(1f))
            PlayerButton((currentChannel?.name ?: "Pick Source").take(18), onChooseSource)
            Spacer(Modifier.width(7.dp))
            PlayerMetaPill(displaySpecs)
            Spacer(Modifier.width(14.dp))
            Image(painterResource(R.drawable.rally_mark_ui), "Rally", Modifier.size(27.dp), contentScale = ContentScale.Fit)
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
                Text(clipTitle ?: currentChannel?.name ?: "Live stream", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(if (clipTitle != null) "RECENT HIGHLIGHTS" else currentChannel?.category?.ifBlank { "Live TV" } ?: "Live TV", color = AppleTvTheme.TextSecondary, fontSize = 12.sp, letterSpacing = .6.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerButton(if (isPlaying) "Pause" else "Play", onTogglePlayback, true, Modifier.focusRequester(firstFocus))
                PlayerButton("Restart", onRestart, enabled = canRestart)
                if (event != null) PlayerButton("Game View", onGameView)
                PlayerButton("Pick Source", onChooseSource)
                PlayerButton("Audio", onAudio, enabled = canChooseAudio)
                PlayerButton("Captions", onCaptions, enabled = canChooseCaptions)
                PlayerButton("Multiview", onMultiView)
                PlayerButton("Diagnostics", onDiagnostics)
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
    selectedClip: HighlightClip?,
    mainTop: androidx.compose.ui.unit.Dp,
    mainHeight: androidx.compose.ui.unit.Dp,
    videoWidth: androidx.compose.ui.unit.Dp,
    initialFocus: FocusRequester,
    onBackToLive: () -> Unit,
    onRemoteKey: (Int) -> Boolean
) {
    val playable = remember(event?.id, event?.highlightClips) {
        event?.highlightClips.orEmpty().filter { !it.streamUrl.isNullOrBlank() }
    }
    val selected = playable.firstOrNull { it.id == selectedClip?.id } ?: playable.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        if (selected != null) {
            HighlightClipPlayer(
                clip = selected,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 26.dp, top = mainTop)
                    .width(videoWidth).height(mainHeight).clip(playerPanelShape)
                    .border(1.dp, RallyTvPalette.Divider, playerPanelShape)
            )
        } else {
            Box(
                Modifier.align(Alignment.TopStart).padding(start = 26.dp, top = mainTop)
                    .width(videoWidth).height(mainHeight).clip(playerPanelShape)
                    .background(RallyTvPalette.BackgroundSoft)
                    .border(1.dp, RallyTvPalette.Divider, playerPanelShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No current highlights yet", color = RallyTvPalette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text("New clips appear here as the broadcast publishes them.", color = RallyTvPalette.Muted, fontSize = 11.sp)
                }
            }
        }

        PlayerButton(
            "Back to Live",
            onBackToLive,
            primary = true,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 44.dp, top = mainTop + 10.dp).focusRequester(initialFocus)
        )

        selected?.let { clip ->
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 43.dp, top = mainTop + mainHeight - 63.dp)
                    .width(videoWidth - 30.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
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
        ExoPlayer.Builder(context, rallyRenderersFactory(context))
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
    VideoPlayerSurface(player, onSurfaceClick = {}, modifier = modifier)
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
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) RallyTvPalette.FocusSurface else if (selected) RallyTvPalette.BackgroundSoft else Color.Transparent)
            .border(if (focused) 1.dp else 0.dp, if (focused) RallyTvPalette.Accent else RallyTvPalette.Divider, RoundedCornerShape(4.dp))
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
            Text(clip.title, color = RallyTvPalette.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            clip.durationSeconds?.let { seconds ->
                Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", color = RallyTvPalette.Muted, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun GameViewChrome(
    event: SportEvent?,
    currentChannel: IptvChannel?,
    sourceLabel: String,
    sourceLabels: List<String>,
    signalQuality: String,
    otherLiveEvents: List<SportEvent>,
    mainTop: androidx.compose.ui.unit.Dp,
    mainHeight: androidx.compose.ui.unit.Dp,
    videoWidth: androidx.compose.ui.unit.Dp,
    infoWidth: androidx.compose.ui.unit.Dp,
    infoHeight: androidx.compose.ui.unit.Dp,
    isPlaying: Boolean,
    initialFocus: FocusRequester,
    segmentFocus: FocusRequester,
    statsFocus: FocusRequester,
    canRestart: Boolean,
    canChooseAudio: Boolean,
    canChooseCaptions: Boolean,
    onTogglePlayback: () -> Unit,
    onRestart: () -> Unit,
    onFullscreen: () -> Unit,
    onAudio: () -> Unit,
    onCaptions: () -> Unit,
    onDiagnostics: () -> Unit,
    onChooseSource: () -> Unit,
    onMultiView: () -> Unit,
    onKeyMoments: (HighlightClip) -> Unit,
    onOtherEvent: (SportEvent) -> Unit
) {
    var requestedPlayId by remember(event?.id) { mutableStateOf<String?>(null) }
    var requestedPlayVersion by remember { mutableIntStateOf(0) }
    var selectedSegment by remember(event?.id) { mutableIntStateOf(0) }
    val lastControlFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    val otherGamesFocus = remember { FocusRequester() }
    val controlRequesters = remember(initialFocus, lastControlFocus) {
        listOf(initialFocus, FocusRequester(), FocusRequester(), FocusRequester(), FocusRequester(), FocusRequester(), lastControlFocus)
    }
    val enabledControls = listOf(true, canRestart, true, true, canChooseAudio, canChooseCaptions, true)
    fun controlNavigation(index: Int): Modifier = Modifier
        .focusRequester(controlRequesters[index])
        .focusProperties {
            left = (index - 1 downTo 0).firstOrNull { enabledControls[it] }
                ?.let { controlRequesters[it] } ?: FocusRequester.Cancel
            right = (index + 1 until controlRequesters.size).firstOrNull { enabledControls[it] }
                ?.let { controlRequesters[it] } ?: statsFocus
            up = sourceFocus
            down = segmentFocus
        }
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (event != null) {
                Box(Modifier.width(videoWidth), contentAlignment = Alignment.Center) {
                    RallyGameScoreHeader(event, Modifier.width(minOf(480.dp, videoWidth)))
                }
            } else {
                Box(Modifier.width(videoWidth), contentAlignment = Alignment.Center) {
                    Text(currentChannel?.name ?: "Live stream", color = RallyTvPalette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            PlayerButton("${if (sourceLabel == "Selected source") "Source" else sourceLabel.take(14)}  ⌄", onChooseSource, modifier = Modifier.focusRequester(sourceFocus).focusProperties { down = statsFocus })
            Spacer(Modifier.width(6.dp))
            PlayerButton(signalQuality, onDiagnostics, modifier = Modifier.focusProperties { down = statsFocus })
            Spacer(Modifier.width(12.dp))
            Image(painterResource(R.drawable.rally_mark_ui), "Rally", Modifier.size(27.dp), contentScale = ContentScale.Fit)
        }



        RallyGameInformationPanel(
            event = event,
            sourceLabel = sourceLabel,
            sourceLabels = sourceLabels,
            requestedPlayId = requestedPlayId,
            requestedPlayVersion = requestedPlayVersion,
            onChooseSource = onChooseSource,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = mainTop, end = 26.dp)
                .width(infoWidth)
                .height(infoHeight),
            initialTabFocus = statsFocus,
            leftExitFocus = lastControlFocus
        )

        Row(
            Modifier.align(Alignment.TopStart)
                .padding(start = 26.dp, top = mainTop + mainHeight + 8.dp)
                .width(videoWidth),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerButton(
                if (isPlaying) "Pause" else "Play",
                onTogglePlayback,
                primary = true,
                modifier = Modifier.weight(1f).then(controlNavigation(0))
            )
            PlayerButton("Restart", onRestart, modifier = Modifier.weight(1f).then(controlNavigation(1)), enabled = canRestart)
            PlayerButton("Fullscreen", onFullscreen, modifier = Modifier.weight(1.18f).then(controlNavigation(2)))
            PlayerButton("Source", onChooseSource, modifier = Modifier.weight(1f).then(controlNavigation(3)))
            PlayerButton("Audio", onAudio, modifier = Modifier.weight(.82f).then(controlNavigation(4)), enabled = canChooseAudio)
            PlayerButton("Captions", onCaptions, modifier = Modifier.weight(1f).then(controlNavigation(5)), enabled = canChooseCaptions)
            PlayerButton(
                "Multiview",
                onMultiView,
                modifier = Modifier.weight(1f).then(controlNavigation(6))
            )
        }

        Row(
            Modifier.align(Alignment.TopStart)
                .padding(start = 26.dp, top = mainTop + mainHeight + 52.dp)
                .width(videoWidth)
        ) {
            GameViewSegment("Key Moments", selectedSegment == 0, { selectedSegment = 0 }, Modifier.focusRequester(segmentFocus).focusProperties { up = initialFocus; right = otherGamesFocus })
            Spacer(Modifier.width(23.dp))
            GameViewSegment("Other Live Games", selectedSegment == 1, { selectedSegment = 1 }, Modifier.focusRequester(otherGamesFocus).focusProperties { up = initialFocus; left = segmentFocus; right = statsFocus })
        }

        if (selectedSegment == 0) {
            val plays = event?.plays.orEmpty().filter { it.isScoringPlay }.sortedByDescending { it.sequence }.take(4)
            val clips = event?.highlightClips.orEmpty().filter { !it.streamUrl.isNullOrBlank() }.take(4)
            if (plays.isEmpty() && clips.isEmpty()) {
                Text(
                    "Key moments are not available for this game yet.",
                    color = RallyTvPalette.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 26.dp, top = mainTop + mainHeight + 90.dp)
                )
            } else {
                Row(
                    Modifier.align(Alignment.TopStart)
                        .padding(start = 26.dp, top = mainTop + mainHeight + 90.dp, end = 26.dp)
                        .width(videoWidth),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    clips.forEach { clip ->
                        KeyMomentTile(clip.title, clip.durationSeconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: "Highlight",
                            clip.thumbnailUrl, event, true, { onKeyMoments(clip) }, Modifier.weight(1f))
                    }
                    plays.take((4 - clips.size).coerceAtLeast(0)).forEach { play ->
                        KeyMomentTile(play.text,
                            listOfNotNull(play.period?.let { "Q$it" }, play.clock).joinToString(" · "),
                            null, event, true, { requestedPlayId = play.id; requestedPlayVersion++ }, Modifier.weight(1f))
                    }
                }
            }
        } else if (otherLiveEvents.isEmpty()) {
            Text(
                "No other live games are available right now.",
                color = RallyTvPalette.Muted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 26.dp, top = mainTop + mainHeight + 90.dp)
            )
        } else {
            Row(
                Modifier.align(Alignment.TopStart)
                    .padding(start = 26.dp, top = mainTop + mainHeight + 90.dp, end = 26.dp)
                    .width(videoWidth),
                horizontalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                otherLiveEvents.take(3).forEach { liveEvent ->
                    GameViewLiveGameRow(liveEvent, { onOtherEvent(liveEvent) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KeyMomentTile(title: String, detail: String, thumbnailUrl: String?, event: SportEvent?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember(title) { mutableStateOf(false) }
    Row(modifier.onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(6.dp))
        .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
        .clickable(enabled = enabled, onClick = onClick).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(65.dp).height(47.dp).clip(RoundedCornerShape(5.dp))) {
            if (thumbnailUrl != null) AsyncImage(thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Row(Modifier.fillMaxSize().background(RallyTvPalette.BackgroundSoft).padding(5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                AsyncImage(event?.awayTeamBadge ?: event?.awayTeam?.logoUrl, null, Modifier.size(25.dp), contentScale = ContentScale.Fit)
                AsyncImage(event?.homeTeamBadge ?: event?.homeTeam?.logoUrl, null, Modifier.size(25.dp), contentScale = ContentScale.Fit)
            }
            Text(detail, color = Color.White, fontSize = 8.sp, lineHeight = 10.sp, modifier = Modifier.align(Alignment.BottomStart).background(Color(0xB0000000)).padding(2.dp))
        }
        Column(Modifier.padding(start = 6.dp)) {
            Text(title, color = RallyTvPalette.Text, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, color = RallyTvPalette.Muted, fontSize = 8.sp, lineHeight = 10.sp, maxLines = 1)
        }
    }
}


@Composable
private fun GameViewSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember(label) { mutableStateOf(false) }
    Column(
        modifier.onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Text(label, color = if (selected || focused) RallyTvPalette.Text else RallyTvPalette.Muted, fontSize = 12.sp, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width((label.length * 7).dp).height(2.dp).background(if (selected) RallyTvPalette.Accent else Color.Transparent))
    }
}

@Composable
private fun GameViewLiveGameRow(
    event: SportEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember(event.id) { mutableStateOf(false) }
    Column(
        modifier.onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text("${event.league}  ·  LIVE", color = RallyTvPalette.Live, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(event.name, color = RallyTvPalette.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(event.scoreLine(), color = RallyTvPalette.Muted, fontSize = 11.sp)
    }
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
    iconRes: Int? = null,
    enabled: Boolean = true
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .height(29.dp)
            .onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .clip(shape)
            .background(
                when {
                    !enabled -> Color(0x88101A21)
                    primary -> Color(0xFFF2F5F7)
                    focused -> Color(0xFF293038)
                    else -> Color(0xE013181D)
                }
            )
            .border(1.dp, if (focused) Color(0xA6D7DCE1) else Color(0x3C6A7076), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        iconRes?.let {
            Image(painterResource(it), null, Modifier.size(13.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label.uppercase(),
            color = when {
                !enabled -> RallyTvPalette.Subtle
                primary -> Color(0xFF050A0D)
                else -> RallyTvPalette.Text
            },
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .55.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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

/** The emulator's virtual AVC decoder can output corrupted buffers. Physical TVs
 * keep their normal hardware decoder order and power-efficient playback path. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun rallyRenderersFactory(context: android.content.Context): DefaultRenderersFactory {
    val factory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    if (android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) {
        factory.setMediaCodecSelector { mimeType, secure, tunneling ->
            MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
                .sortedBy { if (it.name.contains("goldfish", ignoreCase = true)) 1 else 0 }
        }
    }
    return factory
}
