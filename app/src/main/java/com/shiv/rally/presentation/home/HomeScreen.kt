@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.shiv.rally.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import com.shiv.rally.presentation.theme.LocalRallyAccessibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.R
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.GameAlert
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.Team
import com.shiv.rally.presentation.common.RallyTvActionButton
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import com.shiv.rally.presentation.highlights.HighlightItem
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d").withZone(ZoneId.systemDefault())
private val shortDayFormatter = DateTimeFormatter.ofPattern("EEE").withZone(ZoneId.systemDefault())
internal fun homePreviewTime(event: SportEvent): String =
    if (event.startTime.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()) {
        timeFormatter.format(event.startTime)
    } else {
        "${shortDayFormatter.format(event.startTime).uppercase()} · ${timeFormatter.format(event.startTime)}"
    }
private val homeShape = RoundedCornerShape(10.dp)
private enum class HomeViewport { TOP, GUIDE }
private val homeSportShortcuts = listOf(
    "NFL" to "NFL",
    "NBA" to "NBA",
    "MLB" to "MLB",
    "NHL" to "NHL",
    "NCAAF" to "NCAAF",
    "NCAAB" to "NCAAB",
    "MLS" to "MLS",
    "UFC" to "UFC",
    "Soccer" to "Soccer",
    "Tennis" to "Tennis"
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onEventClick: (SportEvent) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToPlayer: (String, String?) -> Unit = { _, _ -> },
    onNavigateToIptv: () -> Unit = {},
    onNavigateToLiveGames: () -> Unit = {},
    onNavigateToHighlights: () -> Unit = {},
    onLeagueClick: (String) -> Unit = {},
    onScheduleClick: () -> Unit = {},
    initialFocusRequester: FocusRequester? = null,
    topNavigationFocusRequester: FocusRequester? = null,
    homeResetRequest: Int = 0
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeAlert by remember { mutableStateOf<GameAlert?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.refreshForPreferences()
    }
    DisposableEffect(viewModel) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }
    LaunchedEffect(viewModel) {
        viewModel.alerts.collect { alert ->
            activeAlert = alert
            delay(5_000L)
            if (activeAlert?.id == alert.id) activeAlert = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val state = uiState) {
            HomeUiState.Loading -> HomeLoadingState()
            is HomeUiState.Error -> HomeErrorState(
                message = state.message,
                onRetry = viewModel::refresh,
                onSettings = onSettingsClick, initialFocusRequester = initialFocusRequester
            )
            is HomeUiState.Success -> HomeContent(
                state = state,
                onEventClick = onEventClick,
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToIptv = onNavigateToIptv,
                onNavigateToLiveGames = onNavigateToLiveGames,
                onNavigateToHighlights = onNavigateToHighlights,
                onLeagueClick = onLeagueClick,
                onScheduleClick = onScheduleClick,
                onToggleEventAlert = viewModel::toggleEventAlert,
                initialFocusRequester = initialFocusRequester,
                topNavigationFocusRequester = topNavigationFocusRequester,
                homeResetRequest = homeResetRequest
            )
        }
        activeAlert?.let { GameAlertBanner(it, Modifier.align(Alignment.TopCenter)) }
    }
}

@Composable
private fun HomeLoadingState() {
    Column(Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 34.dp)) {
        Box(Modifier.fillMaxWidth(.72f).height(22.dp).background(RallyTvPalette.FocusSurface))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(.45f).height(14.dp).background(RallyTvPalette.BackgroundSoft))
        Spacer(Modifier.height(50.dp))
        RallyTvRule()
        Spacer(Modifier.height(24.dp))
        repeat(3) {
            Box(Modifier.width(360.dp).height(190.dp).background(RallyTvPalette.BackgroundSoft))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeErrorState(message: String, onRetry: () -> Unit, onSettings: () -> Unit, initialFocusRequester: FocusRequester?) {
    val firstFocus = initialFocusRequester ?: remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { firstFocus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 60.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Home is unavailable", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RallyTvActionButton("Try again", onRetry, primary = true, focusRequester = firstFocus)
            RallyTvActionButton("Settings", onSettings)
        }
    }
}

@Composable
private fun GameAlertBanner(alert: GameAlert, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(top = 16.dp)
            .clip(homeShape)
            .background(RallyTvPalette.FocusSurface)
            .border(1.dp, RallyTvPalette.Divider, homeShape)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(RallyTvPalette.Live))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(alert.title, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(alert.message, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    onEventClick: (SportEvent) -> Unit,
    onNavigateToPlayer: (String, String?) -> Unit,
    onNavigateToIptv: () -> Unit,
    onNavigateToLiveGames: () -> Unit,
    onNavigateToHighlights: () -> Unit,
    onLeagueClick: (String) -> Unit,
    onScheduleClick: () -> Unit,
    onToggleEventAlert: (String) -> Unit,
    initialFocusRequester: FocusRequester?,
    topNavigationFocusRequester: FocusRequester?,
    homeResetRequest: Int
) {
    var viewport by rememberSaveable { mutableStateOf(HomeViewport.TOP) }
    val fallbackHeroFocus = remember { FocusRequester() }
    val heroFocus = if (viewport == HomeViewport.TOP) initialFocusRequester ?: fallbackHeroFocus else fallbackHeroFocus
    val sportFocus = remember { FocusRequester() }
    val fallbackLiveFocus = remember { FocusRequester() }
    val liveFocus = if (viewport == HomeViewport.GUIDE) initialFocusRequester ?: fallbackLiveFocus else fallbackLiveFocus
    val topSoonFocus = remember { FocusRequester() }
    val topSeeAllFocus = remember { FocusRequester() }
    val guideSoonFocus = topSoonFocus
    val seeFullScheduleFocus = remember { FocusRequester() }
    var transitionVersion by remember { mutableIntStateOf(0) }
    var transitionFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var handledHomeReset by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(homeResetRequest) {
        if (homeResetRequest != handledHomeReset) {
            handledHomeReset = homeResetRequest
            viewport = HomeViewport.TOP
            transitionFocus = initialFocusRequester ?: fallbackHeroFocus
            transitionVersion += 1
        }
    }
    val startingSoon = remember(state.startingSoon, state.upcomingEvents) {
        (state.startingSoon + state.upcomingEvents)
            .distinctBy { it.id }
            .sortedBy { it.startTime }
            .take(4)
    }
    val scheduleTitle = remember(startingSoon) {
        val today = LocalDate.now()
        if (startingSoon.all { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() == today })
            "Tonight's Schedule" else "Upcoming Schedule"
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { if (viewport == HomeViewport.TOP) heroFocus.requestFocus() else guideSoonFocus.requestFocus() }
    }
    val reducedMotion = LocalRallyAccessibility.current.reducedMotion
    LaunchedEffect(viewport, transitionVersion) {
        val requester = transitionFocus ?: return@LaunchedEffect
        // Claim focus after the destination has been measured. The live rail stays mounted
        // throughout the transition, preserving its current page and item focus.
        delay(if (reducedMotion) 16 else 310)
        var focusClaimed = false
        repeat(3) { attempt ->
            if (!focusClaimed) {
                focusClaimed = runCatching { requester.requestFocus() }.isSuccess
                if (!focusClaimed && attempt < 2) delay(55)
            }
        }
        transitionFocus = null
    }

    fun showGuide() {
        transitionFocus = guideSoonFocus
        viewport = HomeViewport.GUIDE
        transitionVersion += 1
    }

    fun showTop() {
        transitionFocus = topSoonFocus
        viewport = HomeViewport.TOP
        transitionVersion += 1
    }

    val transitionProgress = animateFloatAsState(
        targetValue = if (viewport == HomeViewport.GUIDE) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 300, easing = CubicBezierEasing(.2f, .65f, .25f, 1f)), label = "home-guide-morph"
    )
    Box(Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape)
        .onPreviewKeyEvent { transitionFocus != null && it.type == KeyEventType.KeyDown && it.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight) }) {
        // Measure the four sections once. Animation changes placement/layers only;
        // neither the hero artwork nor live thumbnails are remeasured every frame.
        Layout(modifier = Modifier.fillMaxSize(), content = {
            Column {
                RallyHero(state.featuredEvent, onEventClick, onNavigateToIptv, onScheduleClick,
                    heroFocus, topNavigationFocusRequester, liveFocus, {}, viewport == HomeViewport.TOP)
                Spacer(Modifier.height(5.dp))
            }
            LiveNowRail(
                events = state.liveEvents,
                highlights = state.recentHighlights,
                highlightsLoading = state.highlightsLoading,
                redZoneChannelId = state.redZoneChannelId,
                onEventClick = onEventClick,
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToIptv = onNavigateToIptv,
                onNavigateToLiveGames = onNavigateToLiveGames,
                onNavigateToHighlights = onNavigateToHighlights,
                firstFocus = liveFocus,
                upFocus = if (viewport == HomeViewport.TOP) heroFocus else liveFocus,
                downFocus = topSoonFocus,
                seeAllFocus = topSeeAllFocus,
                onExitUp = if (viewport == HomeViewport.GUIDE) ::showTop else null
            )
            RallyUpcomingMorph(startingSoon, viewport == HomeViewport.GUIDE, transitionProgress, scheduleTitle,
                topSoonFocus, liveFocus, sportFocus, seeFullScheduleFocus, state.savedEventIds,
                onToggleEventAlert, onEventClick, onScheduleClick, ::showGuide)
            Column(Modifier.graphicsLayer {
                alpha = transitionProgress.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }) {
                Spacer(Modifier.height(9.dp))
                SportNavigationRail(onLeagueClick, sportFocus, topSoonFocus, enabled = viewport == HomeViewport.GUIDE)
            }
        }) { nodes, constraints ->
            val sectionConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val sections = nodes.map { it.measure(sectionConstraints) }
            val hero = sections[0]
            val live = sections[1]
            val upcoming = sections[2]
            val sports = sections[3]
            val expandedBody = if (startingSoon.isEmpty()) 49.dp.roundToPx()
                else (34.dp * startingSoon.size + (startingSoon.size - 1).dp).roundToPx()
            val compactHeight = upcoming.height - maxOf(0, expandedBody - 49.dp.roundToPx())
            layout(constraints.maxWidth, constraints.maxHeight) {
                val t = transitionProgress.value
                val heroOffset = (hero.height * t).toInt()
                val liveY = hero.height - heroOffset
                val upcomingY = liveY + live.height + 12.dp.roundToPx()
                val upcomingHeight = (compactHeight + (upcoming.height - compactHeight) * t).toInt()
                hero.placeRelativeWithLayer(0, -heroOffset)
                live.placeRelativeWithLayer(0, liveY)
                upcoming.placeRelativeWithLayer(0, upcomingY)
                sports.placeRelativeWithLayer(0, upcomingY + upcomingHeight)
            }
        }
    }
}

@Composable
fun LiveGamesScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onEventClick: (SportEvent) -> Unit,
    onBrowseChannels: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstFocus = initialFocusRequester ?: remember { FocusRequester() }
    LaunchedEffect(viewModel) { viewModel.refreshForPreferences() }
    LaunchedEffect(state) {
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Live Now", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RallyTvActionButton("Browse Live TV", onBrowseChannels)
        }
        Spacer(Modifier.height(20.dp))
        val events = (state as? HomeUiState.Success)?.liveEvents.orEmpty().distinctBy { it.id }
        if (events.isEmpty()) {
            Column(Modifier.padding(horizontal = 60.dp)) {
                Text("No games are in progress right now.", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 16.sp)
                Spacer(Modifier.height(15.dp))
                RallyTvActionButton("Browse Live TV", onBrowseChannels, primary = true, focusRequester = firstFocus)
            }
        } else {
            TvLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 60.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                items(events.chunked(3)) { rowEvents ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        rowEvents.forEach { event ->
                            LandscapeEventThumbnail(
                                event = event,
                                onClick = { onEventClick(event) },
                                modifier = if (event == events.first()) Modifier.focusRequester(firstFocus) else Modifier,
                                width = 270.dp,
                                compact = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RallyHero(
    event: SportEvent?,
    onEventClick: (SportEvent) -> Unit,
    onNavigateToIptv: () -> Unit,
    onScheduleClick: () -> Unit,
    primaryFocus: FocusRequester,
    topFocus: FocusRequester?,
    downFocus: FocusRequester,
    onFocused: () -> Unit,
    enabled: Boolean = true
) {
    val live = event?.status == EventStatus.LIVE || event?.status == EventStatus.HALFTIME
    val final = event?.status == EventStatus.FINISHED
    Box(Modifier.fillMaxWidth().height(190.dp)) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = event?.venueImageUrl ?: getHeroVenueBackdrop(event),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(.78f)
                    .fillMaxHeight()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = .72f }
                    .drawWithCache {
                        val horizontalMask = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            .34f to Color.White.copy(alpha = .3f),
                            .68f to Color.White,
                            1f to Color.White.copy(alpha = .82f)
                        )
                        val verticalMask = Brush.verticalGradient(
                            0f to Color.Transparent,
                            .18f to Color.White.copy(alpha = .72f),
                            .62f to Color.White,
                            1f to Color.Transparent
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(horizontalMask, blendMode = BlendMode.DstIn)
                            drawRect(verticalMask, blendMode = BlendMode.DstIn)
                        }
                    },
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd
            )
        }
        Column(
            Modifier.fillMaxSize().padding(start = 70.dp, top = 28.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event?.league?.uppercase() ?: "RALLY", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp)
                Spacer(Modifier.width(13.dp))
                Text("·", color = RallyTvPalette.Muted, fontSize = 13.sp)
                Spacer(Modifier.width(13.dp))
                if (live) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(RallyTvPalette.Live))
                    Spacer(Modifier.width(7.dp))
                }
                Text(if (live) "LIVE" else if (final) "FINAL" else if (event != null) "UPCOMING" else "WELCOME",
                    color = if (live) Color(0xFFFFD96A) else RallyTvPalette.Muted,
                    fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (event == null) "Sports live on Rally" else "${matchupTeamName(event.awayTeam?.name, event.league)} vs ${matchupTeamName(event.homeTeam?.name, event.league)}",
                color = RallyTvPalette.Text,
                fontFamily = RallyDisplayFont,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.45).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.61f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    event == null -> "Live games, schedules and highlights in one place"
                    live || final -> "${event.awayTeam?.abbreviation ?: "AWAY"} ${event.scoreAway ?: "–"} — ${event.homeTeam?.abbreviation ?: "HOME"} ${event.scoreHome ?: "–"}   |   ${event.gameStatusDetail.orEmpty()}"
                    else -> "${dateFormatter.format(event.startTime)}  ·  ${timeFormatter.format(event.startTime)}"
                },
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 14.sp,
                maxLines = 1
            )
            if (event?.venue?.isNotBlank() == true) {
                Spacer(Modifier.height(2.dp))
                Text(event.venue, color = RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RallyTvActionButton(
                    when {
                        event == null -> "▶   Explore Games"
                        live -> "▶   Watch Live"
                        else -> "More Info"
                    },
                    { if (event != null) onEventClick(event) else onNavigateToIptv() },
                    primary = true,
                    focusRequester = primaryFocus,
                    onFocused = onFocused,
                    modifier = Modifier.focusProperties { canFocus = enabled; topFocus?.let { up = it }; down = downFocus }
                )
                RallyTvActionButton(
                    if (live) "More Info" else "Full Schedule",
                    { if (live && event != null) onEventClick(event) else onScheduleClick() },
                    modifier = Modifier.focusProperties { canFocus = enabled; topFocus?.let { up = it }; down = downFocus }
                )
            }
        }
    }
}

@Composable
private fun SportNavigationRail(
    onLeagueClick: (String) -> Unit,
    firstFocus: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester? = null,
    enabled: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        RallySectionHeader("Browse by Sport")
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 10.dp
            val available = maxWidth - 120.dp
            val shortcutCount = homeSportShortcuts.size
            val oneRowWidth = (available - gap * (shortcutCount - 1)) / shortcutCount
            val columns = if (oneRowWidth >= 68.dp) shortcutCount else 5
            val tileWidth = (available - gap * (columns - 1)) / columns
            val rows = homeSportShortcuts.chunked(columns)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { shortcuts ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 60.dp),
                        horizontalArrangement = if (shortcuts.size == columns) Arrangement.spacedBy(gap) else Arrangement.Center
                    ) {
                        shortcuts.forEach { (league, label) ->
                RallyBrowseSportTile(
                    league = league,
                    label = label,
                    onClick = { onLeagueClick(league) },
                    width = tileWidth,
                    enabled = enabled,
                    modifier = Modifier
                        .then(if (league == homeSportShortcuts.first().first) Modifier.focusRequester(firstFocus) else Modifier)
                        .focusProperties { up = upFocus; downFocus?.let { down = it } }
                )
                            if (shortcuts.size < columns && (league to label) != shortcuts.last()) Spacer(Modifier.width(gap))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RallyBrowseSportTile(league: String, label: String, onClick: () -> Unit, width: Dp, enabled: Boolean = true, modifier: Modifier = Modifier) {
    var focused by remember(league) { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    val tileBrush = if (focused) {
        Brush.radialGradient(
            0f to Color(0xD248515A),
            .48f to Color(0xE222292F),
            1f to Color(0xFA090D11),
            center = Offset(18f, 132f),
            radius = 150f
        )
    } else {
        Brush.verticalGradient(listOf(Color(0xD11A2026), Color(0xF2090D11)))
    }
    Column(
        modifier
            .width(width)
            .height(57.dp)
            .onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .clip(shape)
            .background(tileBrush)
            .border(1.dp, if (focused) Color(0xCCD2D7DC) else Color(0x46505860), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrowseSportMark(league)
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(),
            color = RallyTvPalette.Text,
            fontFamily = RallyBodyFont,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun BrowseSportMark(league: String) {
    val logo = getLeagueLogoResource(league)
    when {
        league == "Soccer" -> Canvas(Modifier.size(29.dp)) {
            drawCircle(Color(0xFFF1F3F5), radius = size.minDimension * .48f)
            fun patch(at: Offset, radius: Float, rotation: Double) {
                val path = Path()
                repeat(5) { index ->
                    val angle = Math.toRadians(rotation + index * 72)
                    val x = at.x + kotlin.math.cos(angle).toFloat() * radius
                    val y = at.y + kotlin.math.sin(angle).toFloat() * radius
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, Color(0xFF151B20))
            }
            patch(center, size.minDimension * .16f, -90.0)
            repeat(5) { index ->
                val angle = Math.toRadians((-90.0 + index * 72.0))
                val point = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * .34f,
                    center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * .34f
                )
                patch(point, size.minDimension * .095f, -90.0 + index * 72)
                drawLine(Color(0xFF151B20), center, point, 1.dp.toPx())
            }
        }
        logo != null -> Image(
            painterResource(logo), null, Modifier.size(29.dp),
            contentScale = ContentScale.Fit
        )
        league == "NCAAF" || league == "NCAAB" -> Box(
            Modifier.size(27.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1262C8)),
            contentAlignment = Alignment.Center
        ) {
            Text("NCAA", color = Color.White, fontFamily = RallyBodyFont, fontSize = 6.sp, fontWeight = FontWeight.Black)
        }
        league == "UFC" -> Text(
            "UFC",
            color = Color.White,
            fontFamily = RallyDisplayFont,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-.6).sp
        )
        else -> Canvas(Modifier.size(26.dp)) {
            drawCircle(Color(0xFFD9F36A))
            val seam = Path().apply {
                moveTo(size.width * .2f, 0f)
                cubicTo(size.width * .64f, size.height * .2f, size.width * .64f, size.height * .8f, size.width * .2f, size.height)
                moveTo(size.width * .8f, 0f)
                cubicTo(size.width * .36f, size.height * .2f, size.width * .36f, size.height * .8f, size.width * .8f, size.height)
            }
            drawPath(seam, Color(0xFF637733), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun LiveNowRail(
    events: List<SportEvent>,
    highlights: List<HighlightItem>,
    highlightsLoading: Boolean,
    redZoneChannelId: String?,
    onEventClick: (SportEvent) -> Unit,
    onNavigateToPlayer: (String, String?) -> Unit,
    onNavigateToIptv: () -> Unit,
    onNavigateToLiveGames: () -> Unit,
    onNavigateToHighlights: () -> Unit,
    firstFocus: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    seeAllFocus: FocusRequester,
    onExitUp: (() -> Unit)? = null,
    showSeeAll: Boolean = true
) {
    val liveEvents = remember(events) { events.distinctBy { it.id } }
    val showingHighlights = liveEvents.isEmpty()
    val ids = remember(liveEvents, highlights) {
        if (showingHighlights) highlights.map { it.clip.id } else liveEvents.map { it.id }
    }
    val count = ids.size
    val pageCount = ((count + 2) / 3).coerceAtLeast(1)
    var page by remember(ids) { mutableIntStateOf(0) }
    var pendingSlot by remember { mutableIntStateOf(-1) }
    val pageFocus = remember(firstFocus) { listOf(firstFocus, FocusRequester(), FocusRequester()) }
    val visibleCount = if (count == 0) 3 else minOf(3, count - page * 3).coerceAtLeast(1)
    val browse = if (showingHighlights) onNavigateToHighlights else onNavigateToLiveGames
    LaunchedEffect(pageCount) { if (page >= pageCount) page = pageCount - 1 }
    LaunchedEffect(page, pendingSlot) {
        if (pendingSlot >= 0) {
            delay(36)
            runCatching { pageFocus[pendingSlot.coerceAtMost(visibleCount - 1)].requestFocus() }
            pendingSlot = -1
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            RallySectionHeader(if (showingHighlights) "Recent Highlights" else "Live Now", Modifier.weight(1f).padding(start = 10.dp), 0.dp)
            if (showSeeAll) HeaderAction("See All  ›", browse, focusRequester = seeAllFocus,
                downFocus = pageFocus[visibleCount - 1], onDirectionUp = onExitUp)
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth - 120.dp - 36.dp) / 3
            Row(Modifier.fillMaxWidth().padding(horizontal = 60.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(visibleCount) { slot ->
                    val navigation = Modifier.focusRequester(pageFocus[slot]).focusProperties {
                        up = if (slot == visibleCount - 1) seeAllFocus else upFocus
                        down = downFocus
                        if (slot > 0) left = pageFocus[slot - 1]
                        if (slot < visibleCount - 1) right = pageFocus[slot + 1]
                    }.onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown || pendingSlot >= 0) return@onPreviewKeyEvent pendingSlot >= 0
                        when {
                            keyEvent.key == Key.DirectionRight && slot == visibleCount - 1 && page < pageCount - 1 -> { page += 1; pendingSlot = 0; true }
                            keyEvent.key == Key.DirectionRight && slot == visibleCount - 1 -> { runCatching { seeAllFocus.requestFocus() }; true }
                            keyEvent.key == Key.DirectionLeft && slot == 0 && page > 0 -> { page -= 1; pendingSlot = 2; true }
                            keyEvent.key == Key.DirectionUp && onExitUp != null && slot != visibleCount - 1 -> { onExitUp(); true }
                            else -> false
                        }
                    }
                    if (showingHighlights) HomeHighlightThumbnail(highlights.getOrNull(page * 3 + slot), cardWidth,
                        highlightsLoading, navigation, onNavigateToPlayer, browse)
                    else liveEvents.getOrNull(page * 3 + slot)?.let { event ->
                        LandscapeEventThumbnail(event, { onEventClick(event) }, navigation, cardWidth, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHighlightThumbnail(
    item: HighlightItem?, width: Dp, loading: Boolean, modifier: Modifier,
    onPlay: (String, String?) -> Unit, onBrowse: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width).onFocusChanged { focused = it.isFocused }.rallyTvFocus(focused)
        .clip(homeShape).background(if (focused) RallyTvPalette.FocusSurface.copy(alpha = .35f) else Color.Transparent)
        .clickable { item?.clip?.streamUrl?.let { onPlay(it, item.clip.title) } ?: onBrowse() }.padding(4.dp)) {
        Box(Modifier.fillMaxWidth().height(width * .36f).clip(homeShape)
            .background(RallyTvPalette.BackgroundSoft)
            .then(if (focused) Modifier.border(1.dp, RallyTvPalette.FocusEdge.copy(alpha = .6f), homeShape) else Modifier)) {
            if (item != null) {
                AsyncImage(item.clip.thumbnailUrl, item.clip.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.35f to Color.Transparent, 1f to Color(0xA6000000))))
                Text("▶  HIGHLIGHTS", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 9.sp, letterSpacing = .7.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                item.clip.durationSeconds?.let { seconds ->
                    Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", color = Color.White, fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
                }
            } else {
                Text(if (loading) "LOADING HIGHLIGHTS" else "BROWSE HIGHLIGHTS  ›", color = RallyTvPalette.Muted,
                    fontSize = 9.sp, letterSpacing = .6.sp, modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(item?.clip?.title.orEmpty(), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp,
            lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(item?.event?.let { "${it.league}  ·  ${matchupTeamName(it.awayTeam?.name, it.league)} vs ${matchupTeamName(it.homeTeam?.name, it.league)}" }.orEmpty(),
            color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ScheduleBell(focused: Boolean) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
        val color = if (focused) RallyTvPalette.Text else RallyTvPalette.Muted
        drawArc(color, 194f, 152f, false, topLeft = Offset(size.width * .2f, size.height * .16f), size = androidx.compose.ui.geometry.Size(size.width * .6f, size.height * .72f), style = stroke)
        drawLine(color, Offset(size.width * .19f, size.height * .7f), Offset(size.width * .81f, size.height * .7f), strokeWidth = stroke.width)
        drawCircle(color, radius = 1.dp.toPx(), center = Offset(size.width * .5f, size.height * .86f))
    }
}

@Composable
internal fun RallySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 70.dp
) {
    Text(
        title.uppercase(),
        color = RallyTvPalette.Muted,
        fontFamily = RallyBodyFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.65.sp,
        modifier = modifier.padding(horizontal = horizontalPadding)
    )
}

@Composable
internal fun HeaderAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
    onDirectionUp: (() -> Unit)? = null
) {
    var focused by remember(label) { mutableStateOf(false) }
    Text(
        label.uppercase(),
        color = if (focused) RallyTvPalette.Text else RallyTvPalette.Muted,
        fontFamily = RallyBodyFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.15.sp,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                upFocus?.let { up = it }
                downFocus?.let { down = it }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp && onDirectionUp != null) {
                    onDirectionUp()
                    true
                } else false
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(homeShape)
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

@Composable
private fun LandscapeEventThumbnail(
    event: SportEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp,
    compact: Boolean = false
) {
    var focused by remember(event.id) { mutableStateOf(false) }
    val live = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
    val final = event.status == EventStatus.FINISHED
    Column(
        modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .clip(homeShape)
            .background(if (focused) Color(0x451A2026) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compact) width * .36f else width * (9f / 16f))
                .clip(homeShape)
                .background(RallyTvPalette.BackgroundSoft)
                .border(if (focused) 1.dp else 0.dp, if (focused) RallyTvPalette.FocusEdge.copy(alpha = .82f) else Color.Transparent, homeShape)
        ) {
            TeamMatchupGraphic(event)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.1f to Color.Transparent, 1f to Color(0xB602090D))))
            if (compact) {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (live) "●  LIVE" else if (final) "FINAL" else "UPCOMING",
                        color = if (live) RallyTvPalette.Live else RallyTvPalette.Text,
                        fontFamily = RallyBodyFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .7.sp
                    )
                    Spacer(Modifier.weight(1f))
                }
            } else {
                Text(
                    eventStatusText(event, live, final),
                    color = if (live) RallyTvPalette.Live else RallyTvPalette.Text,
                    fontFamily = RallyBodyFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .7.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("${matchupTeamName(event.awayTeam?.name, event.league)} vs ${matchupTeamName(event.homeTeam?.name, event.league)}", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text("${event.league}  ·  ${event.gameStatusDetail?.takeIf(String::isNotBlank) ?: if (live) "Live" else timeFormatter.format(event.startTime)}", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TeamMatchupGraphic(event: SportEvent) {
    val awayColor = remember(event.awayTeam?.colors) {
        teamPrimaryColor(event.awayTeam, Color(0xFF244A64))
    }
    val homeColor = remember(event.homeTeam?.colors) {
        teamPrimaryColor(event.homeTeam, Color(0xFF5C2635))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to awayColor.copy(alpha = .9f),
                    .42f to awayColor.copy(alpha = .34f),
                    .5f to Color(0xFF071116),
                    .58f to homeColor.copy(alpha = .34f),
                    1f to homeColor.copy(alpha = .9f)
                )
            )
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AsyncImage(
                model = event.awayTeamBadge ?: event.awayTeam?.logoUrl,
                contentDescription = event.awayTeam?.name,
                modifier = Modifier.size(58.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                if (event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME || event.status == EventStatus.FINISHED) {
                    "${event.scoreAway ?: "–"}  —  ${event.scoreHome ?: "–"}"
                } else "VS",
                color = Color.White.copy(alpha = .88f),
                fontFamily = RallyBodyFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .5.sp
            )
            AsyncImage(
                model = event.homeTeamBadge ?: event.homeTeam?.logoUrl,
                contentDescription = event.homeTeam?.name,
                modifier = Modifier.size(58.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private fun teamPrimaryColor(team: Team?, fallback: Color): Color {
    val raw = team?.colors?.firstOrNull()?.trim().orEmpty()
    if (raw.isBlank()) {
        if (team == null) return fallback
        val palette = listOf(
            Color(0xFF1F5A78), Color(0xFF7B2436), Color(0xFF5A3185), Color(0xFF8A5A18),
            Color(0xFF1D6652), Color(0xFF843B22), Color(0xFF334A8A), Color(0xFF6D315F)
        )
        return palette[(team.id.hashCode() and Int.MAX_VALUE) % palette.size]
    }
    val normalized = when (raw.length) {
        6, 8 -> "#$raw"
        else -> raw
    }
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrDefault(fallback)
}

@Composable
private fun CompactTeamMark(logoUrl: String?, abbreviation: String?) {
    Box(
        Modifier.size(28.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Text(abbreviation.orEmpty(), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

private fun eventStatusText(event: SportEvent, live: Boolean, final: Boolean): String = when {
    live -> listOfNotNull("LIVE", event.gameStatusDetail?.takeIf(String::isNotBlank)).joinToString(" · ")
    final -> "FINAL"
    else -> "${dateFormatter.format(event.startTime)} · ${timeFormatter.format(event.startTime)}"
}

@Composable
fun AppleTvMatchesShelf(
    title: String,
    events: List<SportEvent>,
    onEventClick: (SportEvent) -> Unit,
    leadingCard: (@Composable () -> Unit)? = null,
    horizontalInset: Dp = 58.dp
) {
    val uniqueEvents = remember(events) { events.distinctBy { it.id } }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = horizontalInset), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        TvLazyRow(
            pivotOffsets = PivotOffsets(parentFraction = .05f, childFraction = 0f),
            contentPadding = PaddingValues(horizontal = horizontalInset, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            leadingCard?.let { card -> item(key = "leading-$title") { card() } }
            items(uniqueEvents, key = { it.id }) { event ->
                AppleTvStadiumCard(event) { onEventClick(event) }
            }
        }
    }
}

@Composable
fun AppleTvStadiumCard(event: SportEvent, onClick: () -> Unit) {
    LandscapeEventThumbnail(event = event, onClick = onClick, width = 286.dp)
}

fun getEditorialPhoto(event: SportEvent?): Int? {
    val teams = listOfNotNull(event?.awayTeam?.name, event?.homeTeam?.name).joinToString(" ").lowercase()
    fun features(first: String, second: String) = first in teams && second in teams
    return when (event?.league?.uppercase()) {
        "NFL", "NCAAF" -> if (features("chiefs", "ravens")) R.drawable.editorial_matchup_football else R.drawable.editorial_generic_football
        "NBA", "NCAAB" -> if (features("lakers", "celtics")) R.drawable.editorial_matchup_basketball else R.drawable.editorial_generic_basketball
        "MLB" -> if (features("yankees", "red sox")) R.drawable.editorial_matchup_baseball else R.drawable.editorial_generic_baseball
        "NHL" -> R.drawable.editorial_matchup_hockey_tv
        else -> null
    }
}

fun getHeroVenueBackdrop(event: SportEvent?): Int {
    if (event == null) return R.drawable.card_stadium_bg
    val sport = event.sport.lowercase()
    val league = event.league.uppercase()
    return when {
        sport.contains("basket") || league == "NBA" || league == "NCAAB" -> R.drawable.hero_landscape_basketball_rally
        sport.contains("hock") || league == "NHL" -> R.drawable.hero_landscape_hockey_rally
        sport.contains("socc") || league in setOf("EPL", "MLS") || league.contains("LIGA") || league.contains("CHAMPIONS") || league.contains("SERIE") -> R.drawable.hero_landscape_soccer_rally
        sport.contains("base") || league == "MLB" -> R.drawable.hero_landscape_baseball_rally
        else -> R.drawable.hero_landscape_football_rally
    }
}

fun getHeroColorBackdrop(event: SportEvent?): Int {
    if (event == null) return R.drawable.hero_landscape_football_rally
    val sport = event.sport.lowercase()
    val league = event.league.uppercase()
    return when {
        sport.contains("basket") || league == "NBA" || league == "NCAAB" -> R.drawable.hero_landscape_basketball_rally
        sport.contains("hock") || league == "NHL" -> R.drawable.editorial_matchup_hockey_tv
        sport.contains("socc") || league in setOf("EPL", "MLS") || league.contains("LIGA") || league.contains("CHAMPIONS") || league.contains("SERIE") -> R.drawable.hero_landscape_soccer_rally
        sport.contains("base") || league == "MLB" -> R.drawable.hero_landscape_baseball_rally
        else -> R.drawable.hero_landscape_football_rally
    }
}

fun getLeagueBackdrop(league: String?): Int {
    val normalized = league.orEmpty().uppercase()
    return when {
        normalized == "NBA" || normalized == "NCAAB" || normalized.contains("BASKET") -> R.drawable.card_editorial_basketball_tv
        normalized == "NHL" || normalized.contains("HOCKEY") -> R.drawable.card_editorial_hockey_tv
        normalized == "MLB" || normalized.contains("BASEBALL") -> R.drawable.card_editorial_baseball_tv
        normalized in setOf("EPL", "MLS") || normalized.contains("LIGA") || normalized.contains("CHAMPIONS") || normalized.contains("SERIE") || normalized.contains("SOCCER") -> R.drawable.card_editorial_soccer_tv
        else -> R.drawable.card_editorial_football_tv
    }
}

fun getSportBackdrop(event: SportEvent?): Int {
    if (event == null) return R.drawable.card_editorial_soccer_tv
    val sport = event.sport.lowercase()
    val league = event.league.uppercase()
    return when {
        sport.contains("basket") || league == "NBA" || league == "NCAAB" -> R.drawable.card_editorial_basketball_tv
        sport.contains("foot") || league == "NFL" || league == "NCAAF" -> R.drawable.card_editorial_football_tv
        sport.contains("hock") || league == "NHL" -> R.drawable.editorial_matchup_hockey_tv
        sport.contains("base") || league == "MLB" -> R.drawable.card_editorial_baseball_tv
        else -> R.drawable.card_editorial_soccer_tv
    }
}

fun getLeagueLogoResource(league: String): Int? = when (league.uppercase()) {
    "NFL" -> R.drawable.league_mark_nfl
    "NBA" -> R.drawable.league_mark_nba
    "MLB" -> R.drawable.league_mark_mlb
    "NHL" -> R.drawable.league_mark_nhl
    "EPL", "PREMIER LEAGUE" -> R.drawable.league_mark_epl
    "CHAMPIONS LEAGUE" -> R.drawable.league_mark_ucl
    "LA LIGA" -> R.drawable.league_mark_laliga
    "SERIE A" -> R.drawable.league_mark_seriea
    "MLS" -> R.drawable.league_mark_mls
    else -> null
}

fun formatLeagueDisplayName(league: String?): String {
    if (league.isNullOrBlank()) return "Sports"
    return when (league.lowercase().trim()) {
        "all" -> "All"
        "live" -> "Live"
        "epl", "premierleague", "premier league", "eng.1" -> "Premier League"
        "laliga", "la liga", "esp.1" -> "La Liga"
        "mls", "usa.1" -> "MLS"
        "champions", "uefa.champions", "uefa champions league" -> "Champions League"
        "ncaaf" -> "College Football"
        "ncaab" -> "College Basketball"
        else -> league
    }
}

fun matchupTeamName(rawName: String?, league: String? = null): String {
    val name = formatTeamDisplayName(rawName)
    // Club identities do not follow the city + nickname convention used by US
    // leagues. Keeping the club name prevents "Nashville SC" becoming "SC".
    if (league.orEmpty().uppercase() in setOf("MLS", "EPL", "PREMIER LEAGUE", "LA LIGA", "LALIGA", "SERIE A", "CHAMPIONS LEAGUE", "SOCCER")) return name
    val twoWordNames = listOf("Red Sox", "White Sox", "Blue Jays", "Tar Heels", "Golden Knights", "Maple Leafs", "Trail Blazers", "Real Madrid", "Manchester United")
    return twoWordNames.firstOrNull { name.endsWith(it, ignoreCase = true) }
        ?: name.substringAfterLast(' ', name)
}

fun formatTeamDisplayName(rawName: String?): String {
    if (rawName.isNullOrBlank()) return "TBD"
    val acronyms = setOf("BYU", "UCLA", "USC", "TCU", "LSU", "SMU", "UCF", "UNLV", "UTEP", "UTSA", "NYCFC", "LAFC", "PSG", "FC", "CF", "SC", "AFC")
    return rawName.split(" ").joinToString(" ") { word ->
        val uppercase = word.uppercase()
        when {
            uppercase in acronyms -> uppercase
            word.length > 1 && word.all { it.isUpperCase() || !it.isLetter() } -> word.lowercase().replaceFirstChar { it.uppercase() }
            else -> word
        }
    }
}
