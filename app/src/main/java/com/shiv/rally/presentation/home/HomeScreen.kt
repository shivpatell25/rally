@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shiv.rally.R
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.GameAlert
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.common.RallyPagedRow
import com.shiv.rally.presentation.common.RallyControlButton
import com.shiv.rally.presentation.common.RallyDashboardSkeleton
import com.shiv.rally.presentation.common.RallyActionableError
import com.shiv.rally.presentation.theme.AppleTvTheme
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import com.shiv.rally.presentation.theme.LocalRallyAccessibility
import com.shiv.rally.presentation.common.rallyFocusScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
private val heroShape = RoundedCornerShape(12.dp)
private val cardShape = RoundedCornerShape(10.dp)
private val buttonShape = RoundedCornerShape(8.dp)
private val pillShape = RoundedCornerShape(6.dp)

// Keep hero art consistent and inexpensive: a single matrix gives every source
// the Rally editorial monochrome treatment without creating processed bitmaps
// or adding a runtime blur/shader cost on TV hardware.
private val rallyHeroColorMatrix = ColorMatrix(
    floatArrayOf(
        0.153f, 0.515f, 0.052f, 0f, 0f,
        0.153f, 0.515f, 0.052f, 0f, 0f,
        0.153f, 0.515f, 0.052f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onEventClick: (SportEvent) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateToIptv: () -> Unit = {},
    onLeagueClick: (String) -> Unit = {},
    initialFocusRequester: FocusRequester? = null,
    topNavigationFocusRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeAlert by remember { mutableStateOf<GameAlert?>(null) }
    LaunchedEffect(viewModel) {
        // This composable is recreated when returning from Settings. Refreshing here
        // makes the customized By Sport order visible immediately.
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
                onSettings = onSettingsClick
            )
            is HomeUiState.Success -> HomeContent(
                state = state,
                onEventClick = onEventClick,
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToIptv = onNavigateToIptv,
                onLeagueClick = onLeagueClick,
                initialFocusRequester = initialFocusRequester,
                topNavigationFocusRequester = topNavigationFocusRequester
            )
        }
        activeAlert?.let { alert -> GameAlertBanner(alert, Modifier.align(Alignment.TopCenter)) }
    }
}

@Composable
private fun GameAlertBanner(alert: GameAlert, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 20.dp).width(520.dp).clip(cardShape).background(AppleTvTheme.Slate).border(1.dp, Color(0x36FFFFFF), cardShape).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(AppleTvTheme.AccentRed))
        Spacer(Modifier.width(11.dp))
        Column {
            Text(alert.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(alert.message, color = AppleTvTheme.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HomeLoadingState() {
    RallyDashboardSkeleton()
}

@Composable
private fun HomeErrorState(message: String, onRetry: () -> Unit, onSettings: () -> Unit) {
    RallyActionableError(message, onRetry = onRetry, onSettings = onSettings)
}

@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    onEventClick: (SportEvent) -> Unit,
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToIptv: () -> Unit,
    onLeagueClick: (String) -> Unit,
    initialFocusRequester: FocusRequester?,
    topNavigationFocusRequester: FocusRequester?
) {
    val fallbackHeroFocus = remember { FocusRequester() }
    val heroFocus = initialFocusRequester ?: fallbackHeroFocus
    val liveFocus = remember { FocusRequester() }
    val sportsFocus = remember { FocusRequester() }

    LaunchedEffect(state.featuredEvent?.id) {
        delay(80)
        repeat(3) {
            if (runCatching { heroFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(80)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 7.dp)
    ) {
        HomeDashboardHero(
            featuredEvent = state.featuredEvent,
            heroMode = state.heroMode,
            onNavigateToIptv = onNavigateToIptv,
            onEventClick = { state.featuredEvent?.let(onEventClick) },
            primaryFocus = heroFocus,
            topFocus = topNavigationFocusRequester,
            downFocus = liveFocus
        )
        Spacer(Modifier.height(7.dp))
        HomeLivePagedShelf(
            events = (state.liveEvents + state.upcomingEvents).distinctBy { it.id }.take(15),
            showingUpcoming = state.liveEvents.isEmpty(),
            redZoneChannelId = state.redZoneChannelId,
            onEventClick = onEventClick,
            onRedZone = onNavigateToPlayer,
            onLiveTv = onNavigateToIptv,
            firstFocus = liveFocus,
            upFocus = heroFocus,
            downFocus = sportsFocus
        )
        Spacer(Modifier.height(7.dp))
        HomeSportsPagedShelf(
            shelves = state.leagueShelves,
            onLeagueClick = onLeagueClick,
            firstFocus = sportsFocus,
            upFocus = liveFocus
        )
    }
}

private sealed interface HomeLiveShelfItem {
    data class Game(val event: SportEvent) : HomeLiveShelfItem
    data class RedZone(val channelId: String) : HomeLiveShelfItem
    data object Empty : HomeLiveShelfItem
}

@Composable
private fun HomeDashboardHero(
    featuredEvent: SportEvent?,
    heroMode: HomeHeroMode,
    onNavigateToIptv: () -> Unit,
    onEventClick: () -> Unit,
    primaryFocus: FocusRequester,
    topFocus: FocusRequester?,
    downFocus: FocusRequester
) {
    val isLive = featuredEvent?.status == EventStatus.LIVE || featuredEvent?.status == EventStatus.HALFTIME
    val isFinal = featuredEvent?.status == EventStatus.FINISHED
    val showScore = isLive || isFinal
    val backdrop = remember(featuredEvent?.sport, featuredEvent?.league) { getHeroColorBackdrop(featuredEvent) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(202.dp)
            .clip(heroShape)
            .background(AppleTvTheme.GlassSurfaceHeavy)
            .border(1.dp, AppleTvTheme.GlassBorder, heroShape)
    ) {
        Image(
            painter = painterResource(backdrop),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(rallyHeroColorMatrix)
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xE805080F),
                    .44f to Color(0xB805080F),
                    .72f to Color(0x3805080F),
                    1f to Color(0x0805080F)
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .68f to Color(0x1805080F), 1f to Color(0x8F05080F))))

        Column(
            Modifier.fillMaxHeight().fillMaxWidth(.51f).padding(start = 27.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (featuredEvent != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (heroMode) {
                        HomeHeroMode.CLOSE_GAME -> LivePill("CLOSE GAME")
                        HomeHeroMode.LIVE -> LivePill(featuredEvent.gameStatusDetail)
                        HomeHeroMode.STARTING_SOON -> MetaPill("STARTING SOON")
                        HomeHeroMode.FINAL_RECAP -> MetaPill("FINAL RECAP")
                        HomeHeroMode.UPCOMING -> MetaPill("FEATURED")
                        HomeHeroMode.EMPTY -> Unit
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        featuredEvent.eventContextTitle?.uppercase()
                            ?: formatLeagueDisplayName(featuredEvent.league).uppercase(),
                        color = AppleTvTheme.TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = RallyBodyFont,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(Modifier.width(420.dp), verticalAlignment = Alignment.CenterVertically) {
                    HomeHeroTeam(
                        featuredEvent.awayTeam?.name,
                        featuredEvent.awayTeam?.abbreviation,
                        featuredEvent.awayTeamBadge ?: featuredEvent.awayTeam?.logoUrl,
                        Modifier.weight(1f)
                    )
                    Column(
                        Modifier.width(104.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (showScore) "${featuredEvent.scoreAway ?: "–"}  –  ${featuredEvent.scoreHome ?: "–"}" else "VS",
                            color = Color.White,
                            fontSize = if (showScore) 22.sp else 13.sp,
                            fontFamily = RallyBodyFont,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-.25).sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (isLive) featuredEvent.gameStatusDetail.orEmpty()
                            else if (isFinal) "FINAL · ${featuredEvent.highlightClips.size.takeIf { it > 0 }?.let { "$it HIGHLIGHTS" } ?: "GAME RECAP"}"
                            else "${dateFormatter.format(featuredEvent.startTime)} · ${timeFormatter.format(featuredEvent.startTime)}",
                            color = AppleTvTheme.TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = RallyBodyFont,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = .5.sp,
                            maxLines = 1
                        )
                    }
                    HomeHeroTeam(
                        featuredEvent.homeTeam?.name,
                        featuredEvent.homeTeam?.abbreviation,
                        featuredEvent.homeTeamBadge ?: featuredEvent.homeTeam?.logoUrl,
                        Modifier.weight(1f)
                    )
                }
                Column {
                    Text(
                        listOfNotNull(
                            featuredEvent.venue?.takeIf(String::isNotBlank),
                            featuredEvent.eventContextTitle?.takeIf(String::isNotBlank)
                        ).distinct().joinToString("  ·  ").ifBlank { formatLeagueDisplayName(featuredEvent.league) },
                        color = AppleTvTheme.TextSecondary,
                        fontSize = 8.sp,
                        fontFamily = RallyBodyFont,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = .25.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        RallyActionButton(
                            when {
                                isLive -> "Watch live"
                                isFinal && featuredEvent.highlightClips.isNotEmpty() -> "Watch highlights"
                                else -> "Game center"
                            },
                            onEventClick,
                            primary = true,
                            iconRes = if (isLive) R.drawable.ic_rally_play else null,
                            modifier = Modifier
                                .focusRequester(primaryFocus)
                                .focusProperties {
                                    topFocus?.let { up = it }
                                    down = downFocus
                                }
                        )
                        RallyActionButton(
                            "Details",
                            onEventClick,
                            modifier = Modifier.focusProperties {
                                topFocus?.let { up = it }
                                down = downFocus
                            }
                        )
                    }
                }
            } else {
                Text("RALLY · LIVE SPORTS", color = AppleTvTheme.RallyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Column {
                    Text("Every game. One place.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = (-.6).sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Live schedules, channels, and addon streams—kept simple.", color = AppleTvTheme.TextSecondary, fontSize = 12.sp)
                }
                RallyActionButton(
                    "Browse Live TV",
                    onNavigateToIptv,
                    primary = true,
                    modifier = Modifier
                        .focusRequester(primaryFocus)
                        .focusProperties {
                            topFocus?.let { up = it }
                            down = downFocus
                        }
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.rally_mark_ui),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(15.dp).size(22.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun HomeHeroTeam(
    name: String?,
    abbreviation: String?,
    logo: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var logoLoaded by remember(logo) { mutableStateOf(false) }
    val request = remember(logo) {
        ImageRequest.Builder(context).data(logo).size(128, 128).allowHardware(true).allowRgb565(false).crossfade(false).build()
    }
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!logoLoaded) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x7A101A28))
                        .border(1.dp, Color(0x335A7894), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        abbreviation?.take(4)?.uppercase() ?: name?.take(3)?.uppercase() ?: "TBD",
                        color = AppleTvTheme.OffWhite,
                        fontSize = 11.sp,
                        fontFamily = RallyBodyFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .8.sp
                    )
                }
            }
            if (!logo.isNullOrBlank()) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.size(62.dp),
                    contentScale = ContentScale.Fit,
                    onSuccess = { logoLoaded = true },
                    onError = { logoLoaded = false }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            formatTeamDisplayName(name),
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontFamily = RallyBodyFont,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = .15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HomeLivePagedShelf(
    events: List<SportEvent>,
    showingUpcoming: Boolean,
    redZoneChannelId: String?,
    onEventClick: (SportEvent) -> Unit,
    onRedZone: (String) -> Unit,
    onLiveTv: () -> Unit,
    firstFocus: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester
) {
    val containsLive = remember(events) { events.any { it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME } }
    val containsUpcoming = remember(events) { events.any { it.status != EventStatus.LIVE && it.status != EventStatus.HALFTIME } }
    val items = remember(events, redZoneChannelId) {
        buildList<HomeLiveShelfItem> {
            redZoneChannelId?.let { add(HomeLiveShelfItem.RedZone(it)) }
            events.distinctBy { it.id }.forEach { add(HomeLiveShelfItem.Game(it)) }
            if (isEmpty()) add(HomeLiveShelfItem.Empty)
        }
    }
    Column(Modifier.fillMaxWidth().height(112.dp)) {
        CompactShelfHeader(
            when {
                containsLive && containsUpcoming -> "LIVE / UPCOMING"
                showingUpcoming || containsUpcoming -> "LIVE / UPCOMING"
                else -> "LIVE NOW"
            }
        )
        Spacer(Modifier.height(6.dp))
        RallyPagedRow(
            items = items,
            key = {
                when (it) {
                    is HomeLiveShelfItem.Game -> it.event.id
                    is HomeLiveShelfItem.RedZone -> "redzone:${it.channelId}"
                    HomeLiveShelfItem.Empty -> "empty"
                }
            },
            firstFocusRequester = firstFocus,
            upFocusRequester = upFocus,
            downFocusRequester = downFocus,
            pageSize = 4,
            spacing = 13.dp
        ) { item, modifier, width ->
            when (item) {
                is HomeLiveShelfItem.Game -> HomeCompactLiveCard(item.event, width, modifier) { onEventClick(item.event) }
                is HomeLiveShelfItem.RedZone -> HomeCompactRedZoneCard(width, modifier) { onRedZone(item.channelId) }
                HomeLiveShelfItem.Empty -> HomeCompactEmptyLiveCard(width, modifier, onLiveTv)
            }
        }
    }
}

@Composable
private fun HomeSportsPagedShelf(
    shelves: List<EventShelfData>,
    onLeagueClick: (String) -> Unit,
    firstFocus: FocusRequester,
    upFocus: FocusRequester
) {
    val distinct = remember(shelves) { shelves.distinctBy { it.title } }
    Column(Modifier.fillMaxWidth().height(119.dp)) {
        CompactShelfHeader("BY SPORT")
        Spacer(Modifier.height(6.dp))
        RallyPagedRow(
            items = distinct,
            key = { it.title },
            firstFocusRequester = firstFocus,
            upFocusRequester = upFocus,
            spacing = 13.dp
        ) { shelf, modifier, width ->
            HomeCompactSportCard(shelf, width, modifier) { onLeagueClick(shelf.title) }
        }
    }
}

@Composable
private fun CompactShelfHeader(title: String) {
    Row(Modifier.fillMaxWidth().height(19.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = AppleTvTheme.OffWhite, fontSize = 13.sp, fontFamily = RallyBodyFont, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
    }
}

@Composable
private fun HomeCompactLiveCard(event: SportEvent, width: Dp, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember(event.id) { mutableStateOf(false) }
    val isLive = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
    val homeLogo = event.homeTeamBadge ?: event.homeTeam?.logoUrl
    val awayLogo = event.awayTeamBadge ?: event.awayTeam?.logoUrl
    val accessibility = LocalRallyAccessibility.current
    val spokenSummary = remember(event) {
        val teams = "${formatTeamDisplayName(event.awayTeam?.name)} at ${formatTeamDisplayName(event.homeTeam?.name)}"
        val status = if (isLive) "live, ${event.scoreAway ?: 0} to ${event.scoreHome ?: 0}, ${event.gameStatusDetail.orEmpty()}" else "upcoming ${dateFormatter.format(event.startTime)} at ${timeFormatter.format(event.startTime)}"
        "$teams, $status"
    }
    Box(
        modifier.width(width).height(86.dp)
            .then(if (accessibility.spokenScoreSummaries) Modifier.semantics { contentDescription = spokenSummary } else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused)
            .clip(cardShape).background(AppleTvTheme.GlassSurfaceDefault)
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(getSportBackdrop(event)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x2605080F), .48f to Color(0x7805080F), 1f to Color(0xE805080F))))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isLive) "● LIVE" else "UPCOMING  ·  ${timeFormatter.format(event.startTime)}",
                    color = if (isLive) AppleTvTheme.LiveRed else AppleTvTheme.OffWhite,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isLive) event.gameStatusDetail?.uppercase().orEmpty() else dateFormatter.format(event.startTime).uppercase(),
                    color = AppleTvTheme.TextSecondary,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                CompactTeamLogo(awayLogo, event.awayTeam?.abbreviation)
                if (isLive) {
                    Text(event.scoreAway?.toString() ?: "–", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("–", color = AppleTvTheme.TextTertiary, fontSize = 13.sp)
                    Text(event.scoreHome?.toString() ?: "–", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("VS", color = Color.White, fontSize = 10.sp, fontFamily = RallyBodyFont, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                }
                CompactTeamLogo(homeLogo, event.homeTeam?.abbreviation)
            }
            Text(
                listOfNotNull(event.awayTeam?.abbreviation, event.homeTeam?.abbreviation).joinToString("   ·   ").ifBlank { formatLeagueDisplayName(event.league) }.uppercase(),
                color = AppleTvTheme.OffWhite.copy(alpha = .82f),
                fontSize = 7.sp,
                fontFamily = RallyBodyFont,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactTeamLogo(logo: String?, fallback: String?) {
    var loaded by remember(logo) { mutableStateOf(false) }
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        if (!loaded) Text(fallback?.take(3) ?: "TBD", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        if (!logo.isNullOrBlank()) {
            AsyncImage(
                logo,
                null,
                Modifier.size(33.dp),
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false }
            )
        }
    }
}

@Composable
private fun HomeCompactRedZoneCard(width: Dp, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.width(width).height(86.dp).onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused)
            .clip(cardShape).background(AppleTvTheme.GlassSurfaceDefault)
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(R.drawable.card_editorial_football_tv), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF205080F), 1f to Color(0x7505080F))))
        Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("● LIVE · NFL", color = AppleTvTheme.LiveRed, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Column {
                Text("REDZONE", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("Every touchdown, every game", color = AppleTvTheme.TextSecondary, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun HomeCompactEmptyLiveCard(width: Dp, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.width(width).height(86.dp).onFocusChanged { focused = it.isFocused }
            .clip(cardShape).background(if (focused) Color(0xD9172437) else Color(0xB80A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.ic_rally_live), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.TextSecondary))
        Spacer(Modifier.width(9.dp))
        Column {
            Text("Live TV", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("No games in progress", color = AppleTvTheme.TextSecondary, fontSize = 7.sp)
        }
    }
}

@Composable
private fun HomeCompactSportCard(shelf: EventShelfData, width: Dp, modifier: Modifier, onClick: () -> Unit) {
    val liveCount = remember(shelf.events) { shelf.events.count { it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME } }
    var focused by remember(shelf.title) { mutableStateOf(false) }
    Box(
        modifier.width(width).height(94.dp).onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused)
            .clip(cardShape).background(AppleTvTheme.GlassSurfaceDefault)
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(getLeagueBackdrop(shelf.title)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x2605080F), .54f to Color(0x6E05080F), 1f to Color(0xE505080F))))
        Column(
            Modifier.fillMaxSize().padding(9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (liveCount > 0) {
                Text("●  $liveCount LIVE", color = AppleTvTheme.LiveRed, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            } else {
                Spacer(Modifier.height(8.dp))
            }
            LeagueCardMark(shelf.title)
            Text(
                formatLeagueDisplayName(shelf.title).uppercase(),
                color = Color.White,
                fontSize = 8.sp,
                fontFamily = RallyBodyFont,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LeagueCardMark(league: String) {
    val logo = remember(league) { getLeagueLogoResource(league) }
    Box(Modifier.height(43.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                modifier = Modifier.size(width = 54.dp, height = 42.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                leagueCardMark(league),
                color = AppleTvTheme.OffWhite,
                fontSize = 17.sp,
                fontFamily = RallyBodyFont,
                fontWeight = FontWeight.Bold,
                letterSpacing = .8.sp
            )
        }
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

private fun leagueCardMark(league: String): String = when (league.uppercase()) {
    "NCAAF" -> "CFB"
    "NCAAB" -> "CBB"
    "CHAMPIONS LEAGUE" -> "UCL"
    "LA LIGA" -> "LIGA"
    "SERIE A" -> "SERIE A"
    else -> league.uppercase().take(5)
}

@Composable
private fun RallyHomeNavigation(
    onHome: () -> Unit,
    onLive: () -> Unit,
    onSports: () -> Unit,
    onTeams: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        Modifier
            .width(76.dp)
            .fillMaxHeight()
            .background(Color.Transparent)
            .border(width = 1.dp, color = AppleTvTheme.Divider)
            .padding(horizontal = 8.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.rally_mark_ui),
            contentDescription = "Rally",
            modifier = Modifier.size(38.dp)
        )
        Spacer(Modifier.height(34.dp))
        RallyHomeNavItem(
            "Home",
            R.drawable.ic_rally_home,
            true,
            onHome,
            Modifier.focusRequester(firstFocus),
            onRight = onHome
        )
        Spacer(Modifier.height(8.dp))
        RallyHomeNavItem("Live", R.drawable.ic_rally_live, false, onLive)
        Spacer(Modifier.height(8.dp))
        RallyHomeNavItem("Sports", R.drawable.ic_rally_sports, false, onSports)
        Spacer(Modifier.height(8.dp))
        RallyHomeNavItem("Teams", R.drawable.ic_rally_teams, false, onTeams)
        Spacer(Modifier.weight(1f))
        RallyHomeNavItem("Search", R.drawable.ic_rally_search, false, onSearch)
        Spacer(Modifier.height(8.dp))
        RallyHomeNavItem("Settings", R.drawable.ic_rally_settings, false, onSettings)
    }
}

@Composable
private fun RallyHomeNavItem(
    label: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRight: (() -> Unit)? = null
) {
    var focused by remember(label) { mutableStateOf(false) }
    val foreground = when {
        focused || selected -> AppleTvTheme.RallyCyan
        else -> AppleTvTheme.TextSecondary
    }
    Box(
        modifier
            .width(62.dp)
            .height(58.dp)
            .onPreviewKeyEvent { event ->
                if (onRight != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onRight()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) AppleTvTheme.SurfaceFocused else if (selected) Color(0x18202834) else Color.Transparent)
            .border(
                1.dp,
                if (focused) Color(0x3DF5F7FA) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        if (selected || focused) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppleTvTheme.RallyCyan)
            )
        }
        Column(
            Modifier.fillMaxSize().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                colorFilter = ColorFilter.tint(foreground)
            )
            Spacer(Modifier.height(3.dp))
            Text(label, color = foreground, fontSize = 8.sp, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun RallyHomeTop(
    featuredEvent: SportEvent?,
    onNavigateToIptv: () -> Unit,
    onEventClick: () -> Unit,
    focusRequester: FocusRequester,
    downFocus: FocusRequester
) {
    val backdrop = remember(featuredEvent?.sport, featuredEvent?.league) { getHeroColorBackdrop(featuredEvent) }
    val isLive = featuredEvent?.status == EventStatus.LIVE || featuredEvent?.status == EventStatus.HALFTIME

    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(AppleTvTheme.DeepNavy)
            .padding(start = 36.dp, end = 42.dp, top = 12.dp, bottom = 6.dp)
            .clip(heroShape)
            .background(AppleTvTheme.Slate)
            .border(1.dp, Color(0x2EF5F7FA), heroShape)
    ) {
                Image(
                    painter = painterResource(backdrop),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(rallyHeroColorMatrix)
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to Color(0xF205080F),
                            .58f to Color(0xB005080F),
                            1f to Color(0x4205080F)
                        )
                    )
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x1405080F), .68f to Color.Transparent, 1f to Color(0xD905080F))))
                Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
                    if (featuredEvent != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLive) LivePill(featuredEvent.gameStatusDetail) else MetaPill("FEATURED")
                            Spacer(Modifier.width(9.dp))
                            Text(
                                if (isLive) "LIVE COVERAGE" else "${dateFormatter.format(featuredEvent.startTime)} · ${timeFormatter.format(featuredEvent.startTime)}",
                                color = AppleTvTheme.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                featuredEvent.eventContextTitle?.uppercase() ?: formatLeagueDisplayName(featuredEvent.league).uppercase(),
                                color = Color.White.copy(alpha = .72f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${formatTeamDisplayName(featuredEvent.awayTeam?.name)}\nvs. ${formatTeamDisplayName(featuredEvent.homeTeam?.name)}",
                            color = AppleTvTheme.OffWhite,
                            fontSize = 28.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1.1).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            buildString {
                                append(formatLeagueDisplayName(featuredEvent.league))
                                featuredEvent.venue?.takeIf(String::isNotBlank)?.let { append("  ·  $it") }
                            },
                            color = AppleTvTheme.TextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            RallyActionButton(
                                if (isLive) "Watch Live" else "Game Center",
                                onEventClick,
                                primary = true,
                                modifier = Modifier.focusRequester(focusRequester).focusProperties { down = downFocus }
                            )
                        }
                    } else {
                        Text("RALLY", color = AppleTvTheme.RallyCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Sports, kept simple.", color = AppleTvTheme.OffWhite, fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Live schedules, channels, and addon streams in one place.", color = AppleTvTheme.TextSecondary, fontSize = 15.sp)
                        Spacer(Modifier.height(18.dp))
                        RallyActionButton(
                            "Browse Live TV",
                            onNavigateToIptv,
                            primary = true,
                            modifier = Modifier.focusRequester(focusRequester).focusProperties { down = downFocus }
                        )
                    }
                }
    }
}

@Composable
private fun MyTeamsPanel(
    teams: List<FavoriteTeam>,
    events: List<SportEvent>,
    onTeamClick: (FavoriteTeam) -> Unit,
    onManageTeamsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(heroShape)
            .background(AppleTvTheme.Slate)
            .border(1.dp, Color(0x24F5F7FA), heroShape)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("My Teams", color = AppleTvTheme.OffWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Your clubs at a glance", color = AppleTvTheme.TextTertiary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(11.dp))
        if (teams.isEmpty()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("Make Rally yours", color = AppleTvTheme.OffWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text("Favorite teams to surface their next game here.", color = AppleTvTheme.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                teams.take(4).forEach { team ->
                    val nextEvent = remember(team.id, events) {
                        events.filter { event ->
                            event.homeTeam?.id == team.id || event.awayTeam?.id == team.id ||
                                event.homeTeam?.name.equals(team.name, true) || event.awayTeam?.name.equals(team.name, true)
                        }.sortedWith(
                            compareBy<SportEvent> {
                                if (it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME) 0 else 1
                            }.thenBy { it.startTime }
                        ).firstOrNull()
                    }
                    MyTeamRow(team, nextEvent) { onTeamClick(team) }
                }
            }
        }
        RallyActionButton(
            label = if (teams.isEmpty()) "Choose Teams" else "Manage Teams",
            onClick = onManageTeamsClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MyTeamRow(team: FavoriteTeam, nextEvent: SportEvent?, onClick: () -> Unit) {
    var focused by remember(team.id, team.league) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) AppleTvTheme.Graphite else Color(0xFF0A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else Color(0x18F5F7FA), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(AppleTvTheme.Graphite), contentAlignment = Alignment.Center) {
            if (!team.logoUrl.isNullOrBlank()) AsyncImage(team.logoUrl, null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
            else Text(team.abbreviation.take(3), color = AppleTvTheme.OffWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(team.name, color = AppleTvTheme.OffWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    nextEvent == null -> formatLeagueDisplayName(team.league)
                    nextEvent.status == EventStatus.LIVE || nextEvent.status == EventStatus.HALFTIME -> "LIVE · ${nextEvent.gameStatusDetail.orEmpty()}"
                    else -> "Next · ${dateFormatter.format(nextEvent.startTime)} · ${timeFormatter.format(nextEvent.startTime)}"
                },
                color = if (nextEvent?.status == EventStatus.LIVE || nextEvent?.status == EventStatus.HALFTIME) AppleTvTheme.RallyCyan else AppleTvTheme.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppleTvImmersiveHero(
    featuredEvent: SportEvent?,
    selectedFilter: String,
    filterTabs: List<String>,
    onFilterSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNavigateToIptv: () -> Unit,
    onMultiViewClick: () -> Unit,
    onLeagueClick: () -> Unit,
    onEventClick: () -> Unit
) {
    val primaryFocus = remember { FocusRequester() }
    val backdrop = remember(featuredEvent?.sport, featuredEvent?.league) { getHeroColorBackdrop(featuredEvent) }
    val isLive = featuredEvent?.status == EventStatus.LIVE || featuredEvent?.status == EventStatus.HALFTIME

    LaunchedEffect(featuredEvent?.id) {
        delay(140)
        runCatching { primaryFocus.requestFocus() }
    }

    Box(Modifier.fillMaxWidth().height(500.dp).background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
        ) {
            Image(
                painter = painterResource(backdrop),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(rallyHeroColorMatrix)
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color(0xF2000000),
                        .48f to Color(0x8A000000),
                        1f to Color(0x18000000)
                    )
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x3D000000),
                        .58f to Color.Transparent,
                        1f to Color.Black
                    )
                )
            )
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 26.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.rally_wordmark_white_ui),
                    contentDescription = "Rally",
                    modifier = Modifier.height(46.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvPillButton("Search", onSearchClick)
                    TvPillButton("Live TV", onNavigateToIptv, modifier = Modifier.focusRequester(primaryFocus))
                    TvPillButton("Multi-View", onMultiViewClick)
                    TvPillButton("Settings", onSettingsClick)
                }
            }

            Spacer(Modifier.height(20.dp))
            TvLazyRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filterTabs, key = { it }) { filter ->
                    FilterPill(
                        label = formatLeagueDisplayName(filter),
                        selected = filter == selectedFilter,
                        live = filter == "Live",
                        onClick = { onFilterSelected(filter) }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth(.57f)) {
                if (featuredEvent != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLive) LivePill(featuredEvent.gameStatusDetail)
                        else MetaPill("${dateFormatter.format(featuredEvent.startTime)} · ${timeFormatter.format(featuredEvent.startTime)}")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            featuredEvent.eventContextTitle?.uppercase() ?: formatLeagueDisplayName(featuredEvent.league).uppercase(),
                            color = Color.White.copy(alpha = .72f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(15.dp))
                    Text(
                        text = "${formatTeamDisplayName(featuredEvent.awayTeam?.name)}\nvs. ${formatTeamDisplayName(featuredEvent.homeTeam?.name)}",
                        color = Color.White,
                        fontSize = 42.sp,
                        lineHeight = 45.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = buildString {
                            append(formatLeagueDisplayName(featuredEvent.league))
                            featuredEvent.venue?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                        },
                        color = AppleTvTheme.TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvPillButton(
                            label = if (isLive) "Watch Live" else "View Match",
                            onClick = onEventClick,
                            primary = true
                        )
                        TvPillButton("More Info", onEventClick)
                        if (selectedFilter != "All" && selectedFilter != "Live") {
                            TvPillButton("League Center", onLeagueClick)
                        }
                    }
                } else {
                    val selectedLeague = selectedFilter.takeUnless { it == "All" || it == "Live" }
                        ?.let(::formatLeagueDisplayName)
                    Text(
                        text = selectedLeague?.uppercase() ?: if (selectedFilter == "Live") "LIVE SPORTS" else "SPORTS",
                        color = Color.White.copy(alpha = .68f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = selectedLeague?.let { "$it\nGame Center" }
                            ?: if (selectedFilter == "Live") "No games are\nlive right now." else "Every game.\nOne place.",
                        color = Color.White,
                        fontSize = 46.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.2).sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = selectedLeague?.let {
                            "No live or upcoming $it games are listed right now. This section updates automatically when the schedule changes."
                        } ?: if (selectedFilter == "Live") {
                            "Upcoming games are still available by league, alongside your IPTV lineup and addon streams."
                        } else {
                            "Live schedules, your IPTV lineup, and addon streams in a single TV-first experience."
                        },
                        color = AppleTvTheme.TextSecondary,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvPillButton("Browse Live TV", onNavigateToIptv, primary = true)
                        TvPillButton("Configure Sources", onSettingsClick)
                        if (selectedLeague != null) TvPillButton("League Center", onLeagueClick)
                    }
                }
            }
            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun RallyActionButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    loading: Boolean = false
) {
    RallyControlButton(label, onClick, modifier, primary, iconRes, loading)
}

@Composable
private fun TvPillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier
) = RallyActionButton(label, onClick, primary, modifier)

@Composable
private fun FilterPill(label: String, selected: Boolean, live: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer()
            .clip(pillShape)
            .background(
                when {
                    focused -> Color(0xB0233449)
                    selected -> Color(0x781A293C)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = when {
                    focused -> Color(0xD6B9D8EA)
                    selected -> Color(0x3D7A94AF)
                    else -> Color.Transparent
                },
                shape = pillShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (live) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(AppleTvTheme.AccentRed))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (focused || selected) AppleTvTheme.OffWhite else Color(0xB8FFFFFF),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun LivePill(detail: String?) {
    Row(
        modifier = Modifier.clip(pillShape).background(AppleTvTheme.LiveRed).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(6.dp))
        Text(
            buildString { append("LIVE"); detail?.takeIf { it.isNotBlank() }?.let { append(" · $it") } },
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = RallyBodyFont,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun MetaPill(label: String) {
    Box(Modifier.clip(pillShape).background(Color(0x3DFFFFFF)).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(label, color = Color.White, fontSize = 11.sp, fontFamily = RallyBodyFont, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

@Composable
private fun FavoriteTeamsShelf(
    teams: List<FavoriteTeam>,
    events: List<SportEvent>,
    onTeamClick: (FavoriteTeam) -> Unit,
    onManageTeams: () -> Unit,
    firstFocus: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester
) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
        EditorialShelfTitle("My Teams", "Your clubs, live scores and next games")
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 36.dp, end = 42.dp, top = 14.dp, bottom = 8.dp)
        ) {
            items(teams, key = { "${it.league}:${it.id}" }) { team ->
                var focused by remember(team.id, team.league) { mutableStateOf(false) }
                val nextEvent = remember(team.id, events) {
                    events.filter { event ->
                        event.homeTeam?.id == team.id || event.awayTeam?.id == team.id ||
                            event.homeTeam?.name.equals(team.name, true) || event.awayTeam?.name.equals(team.name, true)
                    }.sortedWith(compareBy<SportEvent> {
                        if (it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME) 0 else 1
                    }.thenBy { it.startTime }).firstOrNull()
                }
                Row(
                    Modifier
                        .width(252.dp)
                        .height(94.dp)
                        .then(
                            if (team == teams.firstOrNull()) Modifier
                                .focusRequester(firstFocus)
                                .focusProperties {
                                    up = upFocus
                                    down = downFocus
                                }
                            else Modifier
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .graphicsLayer {
                            scaleX = if (focused) 1.015f else 1f
                            scaleY = if (focused) 1.015f else 1f
                        }
                        .clip(cardShape)
                        .background(if (focused) AppleTvTheme.Graphite else AppleTvTheme.Slate)
                        .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
                        .clickable { onTeamClick(team) }
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(AppleTvTheme.GlassSurfaceSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!team.logoUrl.isNullOrBlank()) {
                            AsyncImage(team.logoUrl, null, Modifier.size(48.dp), contentScale = ContentScale.Fit)
                        } else {
                            Text(team.abbreviation.take(3), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(team.name, color = if (focused) AppleTvTheme.RallyCyan else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                nextEvent == null -> formatLeagueDisplayName(team.league)
                                nextEvent.status == EventStatus.LIVE || nextEvent.status == EventStatus.HALFTIME -> {
                                    val away = nextEvent.awayTeam?.abbreviation.orEmpty()
                                    val home = nextEvent.homeTeam?.abbreviation.orEmpty()
                                    "LIVE · $away ${nextEvent.scoreAway ?: "–"}  $home ${nextEvent.scoreHome ?: "–"}"
                                }
                                else -> "Next · ${dateFormatter.format(nextEvent.startTime)} · ${timeFormatter.format(nextEvent.startTime)}"
                            },
                            color = if (nextEvent?.status == EventStatus.LIVE || nextEvent?.status == EventStatus.HALFTIME) AppleTvTheme.RallyCyan else AppleTvTheme.TextTertiary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item(key = "manage-teams") {
                var focused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .width(150.dp)
                        .height(94.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(cardShape)
                        .background(if (focused) AppleTvTheme.Graphite else AppleTvTheme.GlassSurfaceSubtle)
                        .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
                        .clickable(onClick = onManageTeams),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Manage Teams", color = if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RallyNoLiveShelf(onLiveTv: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
        EditorialShelfTitle("Live Now", "No games are currently in progress")
        TvLazyRow(
            contentPadding = PaddingValues(start = 36.dp, end = 42.dp, top = 14.dp, bottom = 12.dp)
        ) {
            item(key = "live-empty") {
                Row(
                    modifier
                        .width(420.dp)
                        .height(112.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(cardShape)
                        .background(if (focused) AppleTvTheme.Graphite else AppleTvTheme.Slate)
                        .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
                        .clickable(onClick = onLiveTv)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(AppleTvTheme.GlassSurfaceSubtle), contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(R.drawable.ic_rally_live),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            colorFilter = ColorFilter.tint(if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.TextSecondary)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nothing live right now", color = AppleTvTheme.OffWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text("Browse live channels while the next game gets underway.", color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
                    }
                    Text("LIVE TV  ›", color = if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RallyLandscapeMatchesShelf(
    title: String,
    events: List<SportEvent>,
    onEventClick: (SportEvent) -> Unit,
    leadingCard: (@Composable (Modifier) -> Unit)? = null,
    firstFocus: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null
) {
    val uniqueEvents = remember(events) { events.distinctBy { it.id } }
    Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp)) {
        EditorialShelfTitle(title, "Live coverage and current scores")
        TvLazyRow(
            pivotOffsets = PivotOffsets(parentFraction = .05f, childFraction = 0f),
            contentPadding = PaddingValues(start = 36.dp, end = 42.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            leadingCard?.let { card ->
                item(key = "live-leading-$title") {
                    card(
                        if (firstFocus != null) Modifier
                            .focusRequester(firstFocus)
                            .focusProperties {
                                if (upFocus != null) up = upFocus
                                if (downFocus != null) down = downFocus
                            }
                        else Modifier
                    )
                }
            }
            items(uniqueEvents, key = { it.id }) { event ->
                val firstEventModifier = if (leadingCard == null && event == uniqueEvents.firstOrNull() && firstFocus != null) {
                    Modifier.focusRequester(firstFocus).focusProperties {
                        if (upFocus != null) up = upFocus
                        if (downFocus != null) down = downFocus
                    }
                } else Modifier
                RallyLandscapeGameCard(event, firstEventModifier) { onEventClick(event) }
            }
        }
    }
}

@Composable
private fun RallyLandscapeGameCard(event: SportEvent, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val homeLogo = event.homeTeamBadge ?: event.homeTeam?.logoUrl
    val awayLogo = event.awayTeamBadge ?: event.awayTeam?.logoUrl
    val homeBadge = remember(homeLogo) {
        ImageRequest.Builder(context).data(homeLogo).size(80, 80).allowHardware(true).allowRgb565(true).crossfade(false).build()
    }
    val awayBadge = remember(awayLogo) {
        ImageRequest.Builder(context).data(awayLogo).size(80, 80).allowHardware(true).allowRgb565(true).crossfade(false).build()
    }
    var focused by remember(event.id) { mutableStateOf(false) }

    Box(
        modifier
            .width(230.dp)
            .height(132.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) AppleTvTheme.CardFocusScale else 1f
                scaleY = if (focused) AppleTvTheme.CardFocusScale else 1f
            }
            .clip(cardShape)
            .background(AppleTvTheme.Slate)
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(getSportBackdrop(event)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xF205080F),
                    .58f to Color(0xC905080F),
                    1f to Color(0x8205080F)
                )
            )
        )
        Column(Modifier.fillMaxSize().padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.eventContextTitle?.uppercase() ?: formatLeagueDisplayName(event.league).uppercase(),
                    color = AppleTvTheme.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                EditorialStatusBadge(
                    if (!event.gameStatusDetail.isNullOrBlank()) "LIVE · ${event.gameStatusDetail}" else "LIVE",
                    live = true
                )
            }
            Spacer(Modifier.weight(1f))
            LandscapeTeamLine(event.awayTeam?.name, event.awayTeam?.abbreviation, awayBadge, awayLogo != null, event.scoreAway)
            Spacer(Modifier.height(5.dp))
            LandscapeTeamLine(event.homeTeam?.name, event.homeTeam?.abbreviation, homeBadge, homeLogo != null, event.scoreHome)
            event.venue?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(7.dp))
                Text(it, color = AppleTvTheme.TextTertiary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RallyLandscapeRedZoneCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .width(230.dp)
            .height(132.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) AppleTvTheme.CardFocusScale else 1f
                scaleY = if (focused) AppleTvTheme.CardFocusScale else 1f
            }
            .clip(cardShape)
            .background(AppleTvTheme.SurfaceRaised)
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(R.drawable.card_bg_nfl), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF205080F), .62f to Color(0xCB05080F), 1f to Color(0x7805080F))))
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            EditorialStatusBadge("LIVE", true)
            Column {
                Text("NFL", color = AppleTvTheme.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("REDZONE", color = AppleTvTheme.OffWhite, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = (-.4).sp)
                Text("Every touchdown, every game.", color = AppleTvTheme.TextTertiary, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun LandscapeTeamLine(name: String?, abbreviation: String?, badge: ImageRequest, hasLogo: Boolean, score: Int?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(27.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xC00F1724)), contentAlignment = Alignment.Center) {
            if (hasLogo) AsyncImage(badge, null, Modifier.size(23.dp), contentScale = ContentScale.Fit)
            else Text(abbreviation?.take(3) ?: "TBD", color = AppleTvTheme.OffWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(9.dp))
        Text(formatTeamDisplayName(name), color = AppleTvTheme.OffWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        score?.let { Text(it.toString(), color = AppleTvTheme.OffWhite, fontSize = 16.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun RallySportsShelf(
    shelves: List<EventShelfData>,
    onLeagueClick: (String) -> Unit,
    firstFocus: FocusRequester,
    upFocus: FocusRequester
) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        EditorialShelfTitle("Sports", "Choose a sport to see every game")
        TvLazyRow(
            pivotOffsets = PivotOffsets(parentFraction = .05f, childFraction = 0f),
            contentPadding = PaddingValues(start = 36.dp, end = 42.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(shelves.distinctBy { it.title }, key = { it.title }) { shelf ->
                RallySportCard(
                    shelf,
                    if (shelf == shelves.firstOrNull()) Modifier
                        .focusRequester(firstFocus)
                        .focusProperties { up = upFocus }
                    else Modifier
                ) { onLeagueClick(shelf.title) }
            }
        }
    }
}

@Composable
private fun RallySportCard(shelf: EventShelfData, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val liveCount = remember(shelf.events) { shelf.events.count { it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME } }
    var focused by remember(shelf.title) { mutableStateOf(false) }
    Box(
        modifier
            .width(205.dp)
            .height(118.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) AppleTvTheme.CardFocusScale else 1f
                scaleY = if (focused) AppleTvTheme.CardFocusScale else 1f
            }
            .clip(cardShape)
            .background(AppleTvTheme.Slate)
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(getLeagueBackdrop(shelf.title)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x4405080F), 1f to Color(0xF205080F))))
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (liveCount > 0) "$liveCount LIVE NOW" else "FULL SCHEDULE",
                color = AppleTvTheme.RallyCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.15.sp
            )
            Column {
                Text(formatLeagueDisplayName(shelf.title), color = AppleTvTheme.OffWhite, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (liveCount > 0) "$liveCount live · ${shelf.events.size} games" else "${shelf.events.size} games",
                    color = if (liveCount > 0) AppleTvTheme.RallyCyan else AppleTvTheme.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EditorialShelfTitle(title: String, subtitle: String, start: Dp = 36.dp, end: Dp = 42.dp) {
    Row(Modifier.fillMaxWidth().padding(start = start, end = end), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(AppleTvTheme.RallyCyan))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = AppleTvTheme.OffWhite, fontSize = 23.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.35).sp)
            Text(subtitle, color = AppleTvTheme.TextTertiary, fontSize = 10.sp, lineHeight = 13.sp)
        }
    }
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
        EditorialShelfTitle(
            title,
            when {
                title.equals("Up Next", true) -> "Scheduled across your sports"
                title.contains("Schedule", true) -> "Upcoming games and recent results"
                else -> "Schedule and results"
            },
            start = horizontalInset,
            end = horizontalInset
        )
        TvLazyRow(
            pivotOffsets = PivotOffsets(parentFraction = .05f, childFraction = 0f),
            contentPadding = PaddingValues(start = horizontalInset, end = horizontalInset, top = 20.dp, bottom = 20.dp),
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
    val context = LocalContext.current
    val isLive = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
    val isFinal = event.status == EventStatus.FINISHED
    val homeLogo = event.homeTeamBadge ?: event.homeTeam?.logoUrl
    val awayLogo = event.awayTeamBadge ?: event.awayTeam?.logoUrl
    val homeBadge = remember(homeLogo) {
        ImageRequest.Builder(context).data(homeLogo).size(112, 112).allowHardware(true).allowRgb565(true).crossfade(false).build()
    }
    val awayBadge = remember(awayLogo) {
        ImageRequest.Builder(context).data(awayLogo).size(112, 112).allowHardware(true).allowRgb565(true).crossfade(false).build()
    }
    val status = when {
        isLive -> "LIVE"
        isFinal -> "FINAL"
        else -> "${dateFormatter.format(event.startTime)} · ${timeFormatter.format(event.startTime)}"
    }
    val qualityLabel = remember(event.id, event.liveStats) { eventQualityLabel(event) }
    var focused by remember(event.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(240.dp)
            .height(360.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) 1.025f else 1f
                scaleY = if (focused) 1.025f else 1f
            }
            .clip(cardShape)
            .background(AppleTvTheme.Slate)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder,
                cardShape
            )
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(getSportBackdrop(event)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xA605080F),
                    .38f to Color(0x5205080F),
                    .64f to Color(0xD905080F),
                    1f to AppleTvTheme.DeepNavy
                )
            )
        )
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text(
                event.eventContextTitle?.uppercase() ?: formatLeagueDisplayName(event.league).uppercase(),
                color = AppleTvTheme.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            EditorialStatusBadge(
                if (isLive && !event.gameStatusDetail.isNullOrBlank()) "$status · ${event.gameStatusDetail}" else status,
                live = isLive
            )

            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                EditorialTeamLogo(awayBadge, event.awayTeam?.abbreviation ?: "AWAY", awayLogo != null)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if ((isLive || isFinal) && event.scoreAway != null && event.scoreHome != null) {
                        Text("${event.scoreAway}", color = AppleTvTheme.OffWhite, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        Box(Modifier.width(20.dp).height(1.dp).background(Color(0x66F5F7FA)))
                        Text("${event.scoreHome}", color = AppleTvTheme.OffWhite, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    } else {
                        Text("AT", color = AppleTvTheme.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                EditorialTeamLogo(homeBadge, event.homeTeam?.abbreviation ?: "HOME", homeLogo != null)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                formatTeamDisplayName(event.awayTeam?.name),
                color = AppleTvTheme.OffWhite,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "at ${formatTeamDisplayName(event.homeTeam?.name)}",
                color = AppleTvTheme.OffWhite,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            event.venue?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = AppleTvTheme.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            qualityLabel?.let {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFF202834)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(it, color = AppleTvTheme.RallyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EditorialStatusBadge(label: String, live: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(5.dp)).background(if (live) AppleTvTheme.LiveRed else Color(0xD9202834)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (live) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = AppleTvTheme.OffWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun EditorialTeamLogo(badge: ImageRequest, fallback: String, hasLogo: Boolean) {
    Box(
        Modifier.size(70.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xA60F1724)).border(1.dp, Color(0x2EF5F7FA), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (hasLogo) AsyncImage(model = badge, contentDescription = null, modifier = Modifier.size(56.dp), contentScale = ContentScale.Fit)
        else Text(fallback.take(3), color = AppleTvTheme.OffWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private fun eventQualityLabel(event: SportEvent): String? {
    val value = event.liveStats.entries.firstOrNull { (key, raw) ->
        (key.contains("quality", true) || key.contains("resolution", true) || key.contains("video", true)) &&
            Regex("(?i)(2160|4K|1080|720|HDR|fps|adaptive)").containsMatchIn(raw)
    }?.value ?: return null
    return value
        .replace(Regex("(?i)\\b(IPTV|Stremio)\\b\\s*[·|:\\-]?\\s*"), "")
        .trim()
        .take(28)
        .takeIf(String::isNotBlank)
}

@Composable
fun AppleTvRedZoneCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(360.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) 1.025f else 1f
                scaleY = if (focused) 1.025f else 1f
            }
            .clip(cardShape)
            .background(AppleTvTheme.Slate)
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, cardShape)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF202834), AppleTvTheme.DeepNavy))).padding(18.dp)) {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                LivePill(null)
                Column {
                    Text("NFL", color = Color.White.copy(alpha = .65f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("REDZONE", color = Color.White, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-.8).sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Every touchdown from every game.", color = Color.White.copy(alpha = .72f), fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptySportsShelf(onLiveTv: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 42.dp, top = 34.dp, bottom = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.fillMaxWidth(.62f)) {
            Text("No scheduled games found", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Your live channels remain available. You can also check enabled leagues and addon sources in Settings.", color = AppleTvTheme.TextSecondary, fontSize = 15.sp, lineHeight = 21.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvPillButton("Live TV", onLiveTv, primary = true)
            TvPillButton("Settings", onSettings)
        }
    }
}

@Composable
private fun HomeScreenMultiViewModal(
    events: List<SportEvent>,
    onDismiss: () -> Unit,
    onLaunch: (List<String>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val firstFocus = remember { FocusRequester() }
    val launchFocus = remember { FocusRequester() }
    val candidateEvents = remember(events) {
        events.sortedWith(
            compareBy<SportEvent> {
                when (it.status) {
                    EventStatus.LIVE, EventStatus.HALFTIME -> 0
                    else -> 1
                }
            }.thenBy { it.startTime }
        ).take(12)
    }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(candidateEvents) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color(0xD9000000)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(900.dp).height(650.dp).clip(heroShape).background(AppleTvTheme.Slate).border(1.dp, Color(0x35FFFFFF), heroShape).padding(28.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Choose games", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Select up to four for Multi-View", color = AppleTvTheme.TextSecondary, fontSize = 14.sp)
                }
                Text("${selectedIds.size}/4", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))

            if (candidateEvents.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No games are currently available", color = AppleTvTheme.TextSecondary, fontSize = 17.sp)
                }
            } else {
                TvLazyVerticalGrid(
                    columns = TvGridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(count = candidateEvents.size, key = { candidateEvents[it].id }) { index ->
                        val event = candidateEvents[index]
                        val selected = event.id in selectedIds
                        var focused by remember(event.id) { mutableStateOf(false) }
                        Card(
                            onClick = {
                                selectedIds = when {
                                    selected -> selectedIds - event.id
                                    selectedIds.size < 4 -> selectedIds + event.id
                                    else -> selectedIds
                                }
                            },
                            modifier = Modifier
                                .height(116.dp)
                                .onFocusChanged { focused = it.isFocused }
                                .focusProperties {
                                    if (index >= candidateEvents.size - 3) down = launchFocus
                                }
                                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
                            colors = CardDefaults.colors(
                                containerColor = if (selected) AppleTvTheme.Graphite else AppleTvTheme.Slate,
                                focusedContainerColor = AppleTvTheme.Graphite
                            ),
                            border = CardDefaults.border(
                                border = Border(border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AppleTvTheme.RallyCyan else Color(0x24FFFFFF)), shape = RoundedCornerShape(10.dp)),
                                focusedBorder = Border(border = BorderStroke(2.dp, AppleTvTheme.RallyCyan), shape = RoundedCornerShape(10.dp))
                            )
                        ) {
                            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
                                Text(
                                    "${formatTeamDisplayName(event.awayTeam?.name)} at ${formatTeamDisplayName(event.homeTeam?.name)}",
                                    color = if (focused) AppleTvTheme.RallyCyan else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    if (event.status == EventStatus.LIVE) "LIVE · ${event.gameStatusDetail.orEmpty()}" else "${formatLeagueDisplayName(event.league)} · ${timeFormatter.format(event.startTime)}",
                                    fontSize = 11.sp,
                                    color = when {
                                        focused -> AppleTvTheme.TextSecondary
                                        selected -> Color.White.copy(alpha = .7f)
                                        else -> AppleTvTheme.TextSecondary
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvPillButton("Cancel", onDismiss)
                Spacer(Modifier.width(10.dp))
                TvPillButton(
                    "Open Multi-View",
                    { if (selectedIds.isNotEmpty()) onLaunch(selectedIds.toList()) },
                    primary = selectedIds.isNotEmpty(),
                    modifier = Modifier.focusRequester(launchFocus)
                )
            }
        }
    }
}

fun getHeroColorBackdrop(event: SportEvent?): Int {
    if (event == null) return R.drawable.hero_landscape_football_rally
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
        sport.contains("hock") || league == "NHL" -> R.drawable.card_editorial_hockey_tv
        sport.contains("base") || league == "MLB" -> R.drawable.card_editorial_baseball_tv
        else -> R.drawable.card_editorial_soccer_tv
    }
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
