@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@file:androidx.media3.common.util.UnstableApi

package com.shiv.rally.presentation.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.R
import com.shiv.rally.domain.model.BroadcastQualityInfo
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.MultiViewLayoutMode
import com.shiv.rally.domain.model.MultiViewSlot
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.home.formatTeamDisplayName
import com.shiv.rally.presentation.event.AppleTvStreamPicker
import com.shiv.rally.presentation.theme.AppleTvTheme
import kotlinx.coroutines.delay

private val multiPanelShape = RoundedCornerShape(10.dp)
private val multiPillShape = RoundedCornerShape(6.dp)
private val multiActionShape = RoundedCornerShape(8.dp)

private data class MultiViewPlaybackSession(
    val player: ExoPlayer,
    val trackSelector: DefaultTrackSelector
)

@Composable
fun MultiViewScreen(
    viewModel: MultiViewViewModel = hiltViewModel(),
    onFullScreen: (channelOrStreamUrl: String, eventId: String?) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var actionSlotIndex by remember { mutableStateOf<Int?>(null) }
    var immersive by remember { mutableStateOf(false) }
    var comparisonVisible by remember { mutableStateOf(false) }
    val firstSlotFocus = remember { FocusRequester() }

    BackHandler {
        when {
            actionSlotIndex != null -> actionSlotIndex = null
            comparisonVisible -> comparisonVisible = false
            state.isSourcePickerOpen -> viewModel.closeSourcePicker()
            state.isPickerOpen -> viewModel.closePicker()
            immersive -> immersive = false
            else -> onBack()
        }
    }

    LaunchedEffect(state.slots.isNotEmpty(), state.isPickerOpen, actionSlotIndex) {
        if (state.slots.isNotEmpty() && !state.isPickerOpen && actionSlotIndex == null) {
            delay(120)
            runCatching { firstSlotFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (immersive) Modifier.background(Color.Black)
                else Modifier.background(AppleTvTheme.ScreenGradient)
            )
            .onPreviewKeyEvent { event ->
                if (!immersive || actionSlotIndex != null || state.isPickerOpen) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val isSelect = native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                    native.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                val isMenu = native.keyCode == android.view.KeyEvent.KEYCODE_MENU
                when {
                    isMenu && event.type == KeyEventType.KeyDown && native.repeatCount == 0 -> {
                        actionSlotIndex = state.focusedSlotIndex.takeIf { state.slots.isNotEmpty() }
                        true
                    }
                    isSelect && event.type == KeyEventType.KeyDown -> true
                    isSelect && event.type == KeyEventType.KeyUp -> {
                        if (native.eventTime - native.downTime >= 450L) {
                            actionSlotIndex = state.focusedSlotIndex.takeIf { state.slots.isNotEmpty() }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(if (immersive) PaddingValues(0.dp) else PaddingValues(horizontal = 24.dp, vertical = 13.dp))
        ) {
            if (!immersive) {
                MultiViewHeader(
                    slotCount = state.slots.size,
                    layoutMode = state.layoutMode,
                    onAdd = viewModel::openPickerForAdd,
                    onChangeLayout = {
                        viewModel.setLayoutMode(nextLayout(state.slots.size, state.layoutMode))
                    },
                    onImmersive = { if (state.slots.isNotEmpty()) immersive = true },
                    onCompare = { if (state.slots.size >= 2) comparisonVisible = true },
                    onDone = onBack
                )
                Spacer(Modifier.height(10.dp))
            }
            MultiViewGrid(
                slots = state.slots,
                focusedIndex = state.focusedSlotIndex,
                audioIndex = state.audioSlotIndex,
                layoutMode = state.layoutMode,
                immersive = immersive,
                macAddress = state.macAddress,
                token = state.token,
                firstSlotFocus = firstSlotFocus,
                onFocus = viewModel::setFocusedSlot,
                onOpenActions = { index ->
                    if (immersive) viewModel.setFocusedSlot(index) else actionSlotIndex = index
                },
                onAdd = viewModel::openPickerForAdd
            )
        }

        actionSlotIndex?.let { index ->
            state.slots.getOrNull(index)?.let { slot ->
                SlotActionDialog(
                    slot = slot,
                    onFullScreen = {
                        actionSlotIndex = null
                        onFullScreen(slot.channel?.id ?: slot.streamUrl, slot.event?.id)
                    },
                    onChange = {
                        actionSlotIndex = null
                        viewModel.openPickerForSwap(index)
                    },
                    onChangeSource = {
                        actionSlotIndex = null
                        viewModel.openSourcePicker(index)
                    },
                    isAudioActive = state.audioSlotIndex == index,
                    onUseAudio = {
                        actionSlotIndex = null
                        viewModel.setAudioSlot(index)
                    },
                    onPromote = {
                        actionSlotIndex = null
                        viewModel.promoteSlot(index)
                    },
                    onSwap = {
                        actionSlotIndex = null
                        viewModel.swapSlot(index)
                    },
                    canAdd = state.slots.size < 4,
                    canChangeLayout = state.slots.size >= 2 && state.slots.size != 3,
                    isImmersive = immersive,
                    onAdd = {
                        actionSlotIndex = null
                        viewModel.openPickerForAdd()
                    },
                    onChangeLayout = {
                        actionSlotIndex = null
                        viewModel.setLayoutMode(nextLayout(state.slots.size, state.layoutMode))
                    },
                    onToggleImmersive = {
                        actionSlotIndex = null
                        immersive = !immersive
                    },
                    onRetry = {
                        actionSlotIndex = null
                        viewModel.retrySlot(index)
                    },
                    onRemove = {
                        actionSlotIndex = null
                        viewModel.removeSlot(index)
                    },
                    onDismiss = { actionSlotIndex = null }
                )
            }
        }

        if (comparisonVisible) {
            MultiViewComparisonOverlay(state.slots, onDismiss = { comparisonVisible = false })
        }

        if (state.isPickerOpen) {
            val targetIndex = state.pickerTargetSlotIndex
            val occupiedEvents = state.slots.mapIndexedNotNull { index, slot ->
                slot.event?.id?.takeUnless { index == targetIndex }
            }.toSet()
            val occupiedChannels = state.slots.mapIndexedNotNull { index, slot ->
                slot.channel?.id?.takeUnless { index == targetIndex }
            }.toSet()
            MultiViewPicker(
                replacingSlot = targetIndex,
                liveEvents = state.availableLiveEvents.filterNot { it.id in occupiedEvents },
                channels = state.availableChannels.filterNot { it.id in occupiedChannels },
                onSelectEvent = { event ->
                    if (targetIndex == null) viewModel.addSlotFromEvent(event)
                    else viewModel.replaceSlotWithEvent(targetIndex, event)
                },
                onSelectChannel = { channel ->
                    if (targetIndex == null) viewModel.addSlotFromChannel(channel)
                    else viewModel.replaceSlotWithChannel(targetIndex, channel)
                },
                onDismiss = viewModel::closePicker
            )
        }

        if (state.isSourcePickerOpen) {
            val targetSlot = state.slots.getOrNull(state.sourcePickerTargetSlotIndex ?: -1)
            if (state.sourcePickerLoading) {
                MultiViewSourceLoadingOverlay(onCancel = viewModel::closeSourcePicker)
            } else if (targetSlot != null) {
                AppleTvStreamPicker(
                    channels = state.sourcePickerChannels,
                    stremioStreams = state.sourcePickerStreams,
                    candidates = state.sourcePickerCandidates,
                    broadcastQuality = BroadcastQualityInfo("Adaptive", "Quality varies by source"),
                    eventName = targetSlot.title,
                    onChannelSelected = viewModel::selectSourceForFocusedSlot,
                    onCancel = viewModel::closeSourcePicker
                )
            }
        }
    }
}

private fun nextLayout(count: Int, current: MultiViewLayoutMode): MultiViewLayoutMode = when (count) {
    2 -> if (current == MultiViewLayoutMode.DUAL_FOCUS) MultiViewLayoutMode.DUAL_SPLIT else MultiViewLayoutMode.DUAL_FOCUS
    3 -> MultiViewLayoutMode.TRIPLE_FOCUS
    else -> if (current == MultiViewLayoutMode.QUAD_FOCUS) MultiViewLayoutMode.QUAD_GRID else MultiViewLayoutMode.QUAD_FOCUS
}

@Composable
private fun MultiViewHeader(
    slotCount: Int,
    layoutMode: MultiViewLayoutMode,
    onAdd: () -> Unit,
    onChangeLayout: () -> Unit,
    onImmersive: () -> Unit,
    onCompare: () -> Unit,
    onDone: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painterResource(R.drawable.rally_wordmark_white_ui),
                "Rally",
                Modifier.height(44.dp)
            )
            Spacer(Modifier.width(15.dp))
            Column {
                Text("Multi-View", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$slotCount of 4 streams · audio stays on your selected game", color = AppleTvTheme.TextSecondary, fontSize = 10.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (slotCount < 4) MultiButton("Add Game", onAdd, primary = slotCount < 2)
            if (slotCount >= 2 && slotCount != 3) MultiButton(layoutLabel(slotCount, layoutMode), onChangeLayout)
            if (slotCount >= 2) MultiButton("Compare", onCompare)
            if (slotCount > 0) MultiButton("Immersive", onImmersive)
            MultiButton("Done", onDone)
        }
    }
}

@Composable
private fun MultiViewComparisonOverlay(slots: List<MultiViewSlot>, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xB8000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(650.dp).clip(RoundedCornerShape(10.dp)).background(AppleTvTheme.GlassPanelGradient)
                .border(1.dp, Color(0x788CA8BE), RoundedCornerShape(10.dp)).padding(22.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("GAME COMPARISON", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Live games side by side", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                MultiButton("Done", onDismiss, modifier = Modifier.focusRequester(focus))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                slots.take(4).forEach { slot ->
                    Column(
                        Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xA80A101B))
                            .border(1.dp, AppleTvTheme.GlassBorder, RoundedCornerShape(8.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(slot.event?.league ?: "LIVE", color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(slot.scoreText ?: slot.event?.let { event -> "${event.scoreAway ?: "–"}  –  ${event.scoreHome ?: "–"}" } ?: "Live", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Column {
                            Text(slot.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(slot.statusText ?: slot.event?.gameStatusDetail ?: "Live", color = AppleTvTheme.TextSecondary, fontSize = 8.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun layoutLabel(count: Int, mode: MultiViewLayoutMode): String = when (count) {
    2 -> if (mode == MultiViewLayoutMode.DUAL_FOCUS) "Layout · 70/30" else "Layout · 50/50"
    3 -> "Layout · 2 + 1"
    else -> if (mode == MultiViewLayoutMode.QUAD_FOCUS) "Layout · Focus" else "Layout · Grid"
}

@Composable
private fun MultiViewSourceLoadingOverlay(onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(AppleTvTheme.ScreenGradient), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(470.dp).clip(AppleTvTheme.DialogShape)
                .background(AppleTvTheme.GlassPanelGradient)
                .border(1.dp, AppleTvTheme.GlassBorder, AppleTvTheme.DialogShape)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Finding broadcasts", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Checking configured sources for this game…", color = AppleTvTheme.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
            MultiButton("Cancel", onCancel, modifier = Modifier.focusRequester(focus))
        }
    }
}

@Composable
private fun MultiViewGrid(
    slots: List<MultiViewSlot>,
    focusedIndex: Int,
    audioIndex: Int,
    layoutMode: MultiViewLayoutMode,
    immersive: Boolean,
    macAddress: String,
    token: String,
    firstSlotFocus: FocusRequester,
    onFocus: (Int) -> Unit,
    onOpenActions: (Int) -> Unit,
    onAdd: () -> Unit
) {
    if (slots.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AddStreamCard(onAdd, Modifier.width(430.dp).height(240.dp))
        }
        return
    }

    @Composable
    fun Slot(index: Int, modifier: Modifier) {
        val slot = slots[index]
        MultiViewSlotItem(
            slot = slot,
            slotCount = slots.size,
            isFocused = focusedIndex == index,
            isAudioActive = audioIndex == index,
            macAddress = macAddress,
            token = token,
            focusRequester = if (index == 0) firstSlotFocus else null,
            showChrome = !immersive,
            onFocus = { onFocus(index) },
            onClick = { onOpenActions(index) },
            modifier = modifier
        )
    }

    when (slots.size) {
        1 -> if (immersive) {
            Slot(0, Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slot(0, Modifier.weight(1.7f).fillMaxHeight())
                AddStreamCard(onAdd, Modifier.weight(1f).fillMaxHeight())
            }
        }
        2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val focusLayout = layoutMode == MultiViewLayoutMode.DUAL_FOCUS
            Slot(0, Modifier.weight(if (focusLayout) 1.9f else 1f).fillMaxHeight())
            Slot(1, Modifier.weight(1f).fillMaxHeight())
        }
        3 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Slot(0, Modifier.weight(1f).fillMaxHeight())
                Slot(1, Modifier.weight(1f).fillMaxHeight())
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Slot(2, Modifier.fillMaxWidth(.5f).fillMaxHeight())
            }
        }
        else -> if (layoutMode == MultiViewLayoutMode.QUAD_FOCUS) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Slot(0, Modifier.weight(1.85f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slot(1, Modifier.weight(1f).fillMaxWidth())
                    Slot(2, Modifier.weight(1f).fillMaxWidth())
                    Slot(3, Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Slot(0, Modifier.weight(1f).fillMaxHeight())
                    Slot(1, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Slot(2, Modifier.weight(1f).fillMaxHeight())
                    Slot(3, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun MultiViewSlotItem(
    slot: MultiViewSlot,
    slotCount: Int,
    isFocused: Boolean,
    isAudioActive: Boolean,
    macAddress: String,
    token: String,
    focusRequester: FocusRequester?,
    showChrome: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasFocus by remember { mutableStateOf(false) }
    var playbackError by remember(slot.slotId) { mutableStateOf<String?>(null) }
    var resolution by remember(slot.slotId) { mutableStateOf<String?>(null) }
    val safeHeaders = remember(slot.streamHeaders) { sanitizedStreamHeaders(slot.streamHeaders) }
    val isExternal = slot.channel == null
    val initialMaxHeight = if (slotCount <= 2) 720 else 480
    val initialMaxWidth = if (slotCount <= 2) 1280 else 854
    val initialMaxBitrate = if (slotCount <= 2) 2_500_000 else 1_200_000
    val playbackSession = remember(context, slot.slotId, slot.streamUrl, safeHeaders, isExternal, macAddress, token) {
        if (slot.streamUrl.isBlank()) null else {
            val sessionTrackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setMaxVideoSize(initialMaxWidth, initialMaxHeight)
                    .setMaxVideoFrameRate(30)
                    .setMaxVideoBitrate(initialMaxBitrate)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !isAudioActive)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(false)
                    .build()
            }
            val dataSource = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(if (isExternal) 10_000 else 7_000)
                .setReadTimeoutMs(if (isExternal) 10_000 else 7_000)
            if (isExternal) {
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
                .setBufferDurationsMs(1_500, 8_000, 500, 1_000)
                .setTargetBufferBytes(1 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            val renderers = DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            val sessionPlayer = ExoPlayer.Builder(context, renderers)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
                .setTrackSelector(sessionTrackSelector)
                .setLoadControl(loadControl)
                .build().apply {
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                }
            MultiViewPlaybackSession(sessionPlayer, sessionTrackSelector)
        }
    }
    val exoPlayer = playbackSession?.player
    val trackSelector = playbackSession?.trackSelector

    LaunchedEffect(exoPlayer, isAudioActive, slotCount) {
        exoPlayer?.let { player ->
            player.volume = if (isAudioActive) 1f else 0f
        }
        trackSelector?.let { selector ->
            val maxHeight = if (slotCount <= 2) 720 else 480
            val maxWidth = if (slotCount <= 2) 1280 else 854
            val maxBitrate = if (slotCount <= 2) 2_500_000 else 1_200_000
            selector.setParameters(
                selector.buildUponParameters()
                    .setMaxVideoSize(maxWidth, maxHeight)
                    .setMaxVideoFrameRate(30)
                    .setMaxVideoBitrate(maxBitrate)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !isAudioActive)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(false)
            )
        }
    }

    DisposableEffect(exoPlayer, slot.streamUrl) {
        if (exoPlayer == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Connection failed"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playbackError = null
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    resolution = when {
                        videoSize.height >= 2160 -> "4K"
                        videoSize.height >= 1080 -> "1080p"
                        videoSize.height >= 720 -> "720p"
                        else -> "${videoSize.height}p"
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        runCatching {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(slot.streamUrl)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }.onFailure { playbackError = it.localizedMessage ?: "Unable to start stream" }
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val activeError = slot.error ?: playbackError
    val borderColor = if (isFocused || hasFocus) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder
    Box(
        modifier
            .clip(if (showChrome) multiPanelShape else RoundedCornerShape(0.dp))
            .background(Color.Black)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
            .border(
                if (showChrome && (isFocused || hasFocus)) 2.dp else if (showChrome) 1.dp else 0.dp,
                borderColor,
                if (showChrome) multiPanelShape else RoundedCornerShape(0.dp)
            )
    ) {
        if (exoPlayer != null && activeError == null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = if (showChrome) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = {
                    if (it.player !== exoPlayer) it.player = exoPlayer
                    it.resizeMode = if (showChrome) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (slot.isLoading && activeError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Starting stream…", color = AppleTvTheme.TextSecondary, fontSize = 12.sp)
            }
        }

        if (activeError != null) {
            Box(Modifier.fillMaxSize().background(AppleTvTheme.DeepNavy).padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Stream unavailable", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(activeError, color = AppleTvTheme.TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(7.dp))
                    Text("Press OK for options", color = AppleTvTheme.TextTertiary, fontSize = 9.sp)
                }
            }
        }

        if (showChrome) Box(
            Modifier.fillMaxWidth().height(60.dp).background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (slot.event?.status == EventStatus.LIVE || slot.event?.status == EventStatus.HALFTIME) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(AppleTvTheme.AccentRed))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(slot.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    slot.scoreText?.let { Text(it, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    (slot.sourceQuality ?: resolution ?: slot.resolution)?.let { SlotBadge(it) }
                    if (isAudioActive) SlotBadge("AUDIO")
                }
            }
        }

        if (showChrome && (isFocused || hasFocus)) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(multiPillShape).background(Color(0xC9000000)).padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Press OK for options", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SlotBadge(label: String) {
    Box(Modifier.clip(multiPillShape).background(Color(0x38FFFFFF)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp)
    }
}

@Composable
private fun AddStreamCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(multiPanelShape),
        scale = CardDefaults.scale(scale = 1f, focusedScale = AppleTvTheme.CardFocusScale),
        colors = CardDefaults.colors(containerColor = AppleTvTheme.SurfaceRaised, focusedContainerColor = AppleTvTheme.SurfaceFocused),
        border = CardDefaults.border(
            border = Border(border = BorderStroke(1.dp, Color(0x24FFFFFF)), shape = multiPanelShape),
            focusedBorder = Border(border = BorderStroke(2.dp, AppleTvTheme.RallyCyan), shape = multiPanelShape)
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x18FFFFFF)), contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.height(10.dp))
                Text("Add another game", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text("Up to four live streams", color = AppleTvTheme.TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SlotActionDialog(
    slot: MultiViewSlot,
    onFullScreen: () -> Unit,
    onChange: () -> Unit,
    onChangeSource: () -> Unit,
    isAudioActive: Boolean,
    onUseAudio: () -> Unit,
    onPromote: () -> Unit,
    onSwap: () -> Unit,
    canAdd: Boolean,
    canChangeLayout: Boolean,
    isImmersive: Boolean,
    onAdd: () -> Unit,
    onChangeLayout: () -> Unit,
    onToggleImmersive: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
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
            Modifier.width(440.dp).clip(AppleTvTheme.DialogShape).background(AppleTvTheme.Slate).border(1.dp, Color(0x28FFFFFF), AppleTvTheme.DialogShape).padding(25.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(slot.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(slot.subtitle, slot.statusText).joinToString(" · "), color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MultiButton("Watch Full Screen", onFullScreen, primary = true, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus))
                if (!isAudioActive) MultiButton("Use This Audio", onUseAudio, modifier = Modifier.fillMaxWidth())
                MultiButton("Move to Main", onPromote, modifier = Modifier.fillMaxWidth())
                MultiButton("Swap Position", onSwap, modifier = Modifier.fillMaxWidth())
                if (canAdd) MultiButton("Add Stream", onAdd, modifier = Modifier.fillMaxWidth())
                MultiButton("Change Game / Channel", onChange, modifier = Modifier.fillMaxWidth())
                if (slot.event != null) MultiButton("Change Source", onChangeSource, modifier = Modifier.fillMaxWidth())
                if (canChangeLayout) MultiButton("Change Layout", onChangeLayout, modifier = Modifier.fillMaxWidth())
                if (slot.error != null) MultiButton("Retry", onRetry, modifier = Modifier.fillMaxWidth())
                MultiButton(if (isImmersive) "Exit Immersive" else "Enter Immersive", onToggleImmersive, modifier = Modifier.fillMaxWidth())
                MultiButton("Remove from Multi-View", onRemove, modifier = Modifier.fillMaxWidth(), danger = true)
                MultiButton("Cancel", onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MultiViewPicker(
    replacingSlot: Int?,
    liveEvents: List<SportEvent>,
    channels: List<IptvChannel>,
    onSelectEvent: (SportEvent) -> Unit,
    onSelectChannel: (IptvChannel) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(if (liveEvents.isEmpty()) 1 else 0) }
    val gamesTabFocus = remember { FocusRequester() }
    val channelsTabFocus = remember { FocusRequester() }
    val firstGameFocus = remember { FocusRequester() }
    val firstChannelFocus = remember { FocusRequester() }
    val sportsChannels = remember(channels) {
        channels.filter { channel ->
            val text = "${channel.category} ${channel.name}".uppercase()
            listOf("SPORT", "ESPN", "FOX", "FS1", "NBC", "CBS", "TNT", "SKY", "NFL", "NBA", "MLB", "NHL").any(text::contains)
        }.ifEmpty { channels }.take(250)
    }

    BackHandler(onBack = onDismiss)
    LaunchedEffect(tab, liveEvents.size, sportsChannels.size) {
        delay(120)
        val requester = when {
            tab == 0 && liveEvents.isNotEmpty() -> firstGameFocus
            tab == 1 && sportsChannels.isNotEmpty() -> firstChannelFocus
            tab == 0 -> gamesTabFocus
            else -> channelsTabFocus
        }
        runCatching { requester.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(AppleTvTheme.ScreenGradient)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 38.dp, vertical = 25.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (replacingSlot == null) "Add to Multi-View" else "Change Stream", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Choose a live game or IPTV channel", color = AppleTvTheme.TextSecondary, fontSize = 12.sp)
                }
                MultiButton("Cancel", onDismiss)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MultiButton("Live Games · ${liveEvents.size}", { tab = 0 }, primary = tab == 0, modifier = Modifier.focusRequester(gamesTabFocus))
                MultiButton("Live TV · ${sportsChannels.size}", { tab = 1 }, primary = tab == 1, modifier = Modifier.focusRequester(channelsTabFocus))
            }
            Spacer(Modifier.height(16.dp))

            if (tab == 0) {
                if (liveEvents.isEmpty()) {
                    PickerEmptyState("No additional games are live right now.")
                } else {
                    TvLazyVerticalGrid(
                        columns = TvGridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(liveEvents, key = { _, event -> event.id }) { index, event ->
                            PickerEventCard(event, { onSelectEvent(event) }, if (index == 0) Modifier.focusRequester(firstGameFocus) else Modifier)
                        }
                    }
                }
            } else {
                if (sportsChannels.isEmpty()) {
                    PickerEmptyState("No IPTV channels are configured.")
                } else {
                    TvLazyVerticalGrid(
                        columns = TvGridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(sportsChannels, key = { _, channel -> channel.id }) { index, channel ->
                            PickerChannelCard(channel, { onSelectChannel(channel) }, if (index == 0) Modifier.focusRequester(firstChannelFocus) else Modifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerEmptyState(message: String) {
    Box(Modifier.fillMaxSize().clip(multiPanelShape).background(AppleTvTheme.Slate), contentAlignment = Alignment.Center) {
        Text(message, color = AppleTvTheme.TextSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun PickerEventCard(event: SportEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(92.dp),
        shape = CardDefaults.shape(multiPanelShape),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
        colors = CardDefaults.colors(containerColor = AppleTvTheme.Slate, focusedContainerColor = AppleTvTheme.Graphite),
        border = CardDefaults.border(
            border = Border(border = BorderStroke(1.dp, Color(0x20FFFFFF)), shape = multiPanelShape),
            focusedBorder = Border(border = BorderStroke(2.dp, AppleTvTheme.RallyCyan), shape = multiPanelShape)
        )
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(AppleTvTheme.AccentRed))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(event.gameStatusDetail ?: event.league, color = AppleTvTheme.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "${formatTeamDisplayName(event.awayTeam?.name)} at ${formatTeamDisplayName(event.homeTeam?.name)}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(event.league, color = AppleTvTheme.TextTertiary, fontSize = 10.sp)
            }
            if (event.scoreAway != null && event.scoreHome != null) {
                Text("${event.scoreAway} – ${event.scoreHome}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PickerChannelCard(channel: IptvChannel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(76.dp),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
        colors = CardDefaults.colors(containerColor = AppleTvTheme.Slate, focusedContainerColor = AppleTvTheme.Graphite),
        border = CardDefaults.border(
            border = Border(border = BorderStroke(1.dp, Color(0x20FFFFFF)), shape = RoundedCornerShape(10.dp)),
            focusedBorder = Border(border = BorderStroke(2.dp, AppleTvTheme.RallyCyan), shape = RoundedCornerShape(10.dp))
        )
    ) {
        Row(Modifier.fillMaxSize().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(AppleTvTheme.GlassSurfaceSubtle), contentAlignment = Alignment.Center) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(channel.logoUrl, null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
                } else {
                    Text(channel.number.take(3), color = AppleTvTheme.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(channel.category.ifBlank { "Live TV" }, color = AppleTvTheme.TextTertiary, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MultiButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ButtonDefaults.shape(multiActionShape),
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = AppleTvTheme.ButtonFocusScale),
        colors = ButtonDefaults.colors(
            containerColor = when {
                primary -> AppleTvTheme.OffWhite
                danger -> Color(0x28FF453A)
                else -> AppleTvTheme.SurfaceRaised
            },
            focusedContainerColor = if (primary) Color.White else AppleTvTheme.SurfaceFocused,
            contentColor = when {
                primary -> AppleTvTheme.DeepNavy
                danger -> AppleTvTheme.AccentRed
                else -> Color.White
            },
            focusedContentColor = AppleTvTheme.RallyCyan
        ),
        border = ButtonDefaults.border(
            border = Border(border = BorderStroke(1.dp, Color(0x28FFFFFF)), shape = multiActionShape),
            focusedBorder = Border(border = BorderStroke(2.dp, AppleTvTheme.RallyCyan), shape = multiActionShape)
        )
    ) {
        Text(
            label,
            color = when {
                primary -> AppleTvTheme.DeepNavy
                focused -> AppleTvTheme.RallyCyan
                danger -> AppleTvTheme.AccentRed
                else -> Color.White
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
