@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.shiv.rally.presentation.event

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.domain.model.BroadcastQualityInfo
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.RelevantChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.model.StreamCandidate
import com.shiv.rally.domain.model.Team
import com.shiv.rally.domain.model.TeamInjury
import com.shiv.rally.domain.model.resolveMaxBroadcastQuality
import com.shiv.rally.presentation.common.RallyActionableError
import com.shiv.rally.presentation.common.RallyDashboardSkeleton
import com.shiv.rally.presentation.common.RallyTvActionButton
import com.shiv.rally.presentation.common.rallyReadableFocus
import com.shiv.rally.presentation.player.playerStatPairs
import com.shiv.rally.presentation.player.statCategoryLabel
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.home.getHeroVenueBackdrop
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.home.formatTeamDisplayName
import com.shiv.rally.presentation.home.matchupTeamName
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val eventTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d · h:mm a").withZone(ZoneId.systemDefault())
private val eventHeroTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a").withZone(ZoneId.systemDefault())
private val eventShape = RoundedCornerShape(10.dp)

@Composable
fun EventScreen(
    viewModel: EventViewModel = hiltViewModel(),
    onWatchLive: (channelId: String) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null,
    onPlayHighlight: (String, String) -> Unit = { target, _ -> onWatchLive(target) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        EventUiState.Loading -> RallyDashboardSkeleton()
        is EventUiState.Error -> RallyActionableError(current.message, onRetry = viewModel::retry, onBack = onBack, initialFocusRequester = initialFocusRequester)
        is EventUiState.Success -> EventContent(
            event = current.event,
            primaryStreamTarget = current.primaryStreamTarget,
            relevantChannels = current.relevantChannels,
            broadcastStations = current.broadcastStations,
            stremioStreams = current.stremioStreams,
            streamCandidates = current.streamCandidates,
            isLoadingStreams = current.isLoadingStreams,
            favoriteTeamIds = current.favoriteTeamIds,
            isEventWatchlisted = current.isEventWatchlisted,
            injuries = current.injuries,
            onToggleFavoriteTeam = viewModel::toggleFavoriteTeam,
            onToggleEventWatchlist = viewModel::toggleEventWatchlist,
            onWatchLive = onWatchLive,
            onPlayHighlight = onPlayHighlight,
            onBack = onBack,
            initialFocusRequester = initialFocusRequester
        )
    }
}

@Composable
fun EventContent(
    event: SportEvent,
    primaryStreamTarget: String?,
    relevantChannels: List<RelevantChannel>,
    broadcastStations: List<String>,
    stremioStreams: List<StremioStreamOption>,
    streamCandidates: List<StreamCandidate> = emptyList(),
    isLoadingStreams: Boolean = false,
    favoriteTeamIds: Set<String> = emptySet(),
    onToggleFavoriteTeam: (Team) -> Unit = {},
    onWatchLive: (String) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null,
    isEventWatchlisted: Boolean = false,
    injuries: Map<String, List<TeamInjury>> = emptyMap(),
    onToggleEventWatchlist: () -> Unit = {},
    onPlayHighlight: (String, String) -> Unit = { target, _ -> onWatchLive(target) }
) {
    var choosingSource by remember { mutableStateOf(false) }
    val quality = remember(event, broadcastStations, relevantChannels, stremioStreams) {
        resolveMaxBroadcastQuality(event, broadcastStations, relevantChannels, stremioStreams)
    }

    BackHandler(enabled = choosingSource) { choosingSource = false }
    if (choosingSource) {
        AppleTvStreamPicker(
            channels = relevantChannels,
            stremioStreams = stremioStreams,
            candidates = streamCandidates,
            broadcastStations = broadcastStations,
            broadcastQuality = quality,
            eventName = event.name,
            onChannelSelected = onWatchLive,
            onCancel = { choosingSource = false }
        )
    } else {
        EventDetailsView(
            event = event,
            primaryStreamId = primaryStreamTarget,
            broadcastQuality = quality,
            isLoadingStreams = isLoadingStreams,
            favoriteTeamIds = favoriteTeamIds,
            isEventWatchlisted = isEventWatchlisted,
            injuries = injuries,
            relevantChannels = relevantChannels,
            stremioStreams = stremioStreams,
            streamCandidates = streamCandidates,
            broadcastStations = broadcastStations,
            onToggleFavoriteTeam = onToggleFavoriteTeam,
            onToggleEventWatchlist = onToggleEventWatchlist,
            onWatchLive = onWatchLive,
            onPlayHighlight = onPlayHighlight,
            onPickSource = { choosingSource = true },
            onBack = onBack,
            initialFocusRequester = initialFocusRequester
        )
    }
}

@Composable
fun EventDetailsView(
    event: SportEvent,
    primaryStreamId: String?,
    broadcastQuality: BroadcastQualityInfo,
    isLoadingStreams: Boolean,
    favoriteTeamIds: Set<String>,
    onToggleFavoriteTeam: (Team) -> Unit,
    onWatchLive: (String) -> Unit,
    onPickSource: () -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null,
    isEventWatchlisted: Boolean = false,
    injuries: Map<String, List<TeamInjury>> = emptyMap(),
    relevantChannels: List<RelevantChannel> = emptyList(),
    stremioStreams: List<StremioStreamOption> = emptyList(),
    streamCandidates: List<StreamCandidate> = emptyList(),
    broadcastStations: List<String> = emptyList(),
    onToggleEventWatchlist: () -> Unit = {},
    onPlayHighlight: (String, String) -> Unit = { target, _ -> onWatchLive(target) }
) {
    val fallbackFocus = remember { FocusRequester() }
    val primaryFocus = initialFocusRequester ?: fallbackFocus
    val overviewFocus = remember { FocusRequester() }
    val tabLabels = remember { listOf("Overview", "Stats", "Lineups", "Plays", "Highlights", "Sources") }
    val tabFocus = remember { listOf(overviewFocus, FocusRequester(), FocusRequester(), FocusRequester(), FocusRequester(), FocusRequester()) }
    var selectedTab by remember(event.id) { mutableStateOf("Overview") }
    val isLive = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME

    LaunchedEffect(event.id) {
        delay(120)
        runCatching { primaryFocus.requestFocus() }
    }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        EventPageBackdrop(event)
    Column(Modifier.fillMaxSize()) {
        EventHero(
            event = event,
            isLive = isLive,
            primaryStreamId = primaryStreamId,
            isLoadingStreams = isLoadingStreams,
            isEventWatchlisted = isEventWatchlisted,
            primaryFocus = primaryFocus,
            nextFocus = overviewFocus,
            onWatch = { primaryStreamId?.let(onWatchLive) ?: onPickSource() },
            onToggleEventWatchlist = onToggleEventWatchlist
        )
        Spacer(Modifier.height(7.dp))
        Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, bottom = 26.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabLabels.forEachIndexed { index, tab ->
                EventTab(
                    label = tab,
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    modifier = Modifier
                        .focusRequester(tabFocus[index])
                        .focusProperties {
                            up = primaryFocus
                            left = if (index > 0) tabFocus[index - 1] else FocusRequester.Cancel
                            right = if (index < tabFocus.lastIndex) tabFocus[index + 1] else FocusRequester.Cancel
                        }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        RallyTvRule()
        Spacer(Modifier.height(10.dp))
        when (selectedTab) {
            "Overview" -> EventOverview(
                event = event,
                broadcastQuality = broadcastQuality,
                favoriteTeamIds = favoriteTeamIds,
                injuries = injuries,
                onToggleFavoriteTeam = onToggleFavoriteTeam,
                modifier = Modifier.weight(1f)
            )
            "Stats" -> EventStats(event, Modifier.weight(1f))
            "Lineups" -> EventLineups(event, Modifier.weight(1f))
            "Plays" -> EventPlays(event, onWatchLive, Modifier.weight(1f))
            "Highlights" -> EventHighlights(event, onPlayHighlight, Modifier.weight(1f))
            "Sources" -> EventSources(
                candidates = streamCandidates,
                channels = relevantChannels,
                stremioStreams = stremioStreams,
                broadcastStations = broadcastStations,
                quality = broadcastQuality,
                loading = isLoadingStreams,
                onWatchLive = onWatchLive,
                onPickSource = onPickSource,
                modifier = Modifier.weight(1f)
            )
        }
        }
    }
    }
}

@Composable
private fun EventPageBackdrop(event: SportEvent) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            event.venueImageUrl ?: getHeroVenueBackdrop(event),
            null,
            Modifier.align(Alignment.TopEnd).fillMaxWidth(.80f).height(270.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = .66f }
                .drawWithCache {
                    val horizontalMask = Brush.horizontalGradient(0f to Color.Transparent, .38f to Color.White.copy(alpha = .4f), .7f to Color.White, 1f to Color.White.copy(alpha = .8f))
                    val verticalMask = Brush.verticalGradient(0f to Color.Transparent, .22f to Color.White.copy(alpha = .7f), .60f to Color.White, 1f to Color.Transparent)
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
}

@Composable
private fun EventHero(
    event: SportEvent,
    isLive: Boolean,
    primaryStreamId: String?,
    isLoadingStreams: Boolean,
    isEventWatchlisted: Boolean,
    primaryFocus: FocusRequester,
    nextFocus: FocusRequester,
    onWatch: () -> Unit,
    onToggleEventWatchlist: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 62.dp, top = 77.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.league.uppercase(), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp)
                Spacer(Modifier.width(11.dp))
                Text("·", color = RallyTvPalette.Muted, fontSize = 13.sp)
                Spacer(Modifier.width(11.dp))
                if (isLive) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(RallyTvPalette.Live))
                    Spacer(Modifier.width(7.dp))
                }
                Text(if (isLive) "LIVE" else if (event.status == EventStatus.FINISHED) "FINAL" else "UPCOMING",
                    color = if (isLive) Color(0xFFFFD46A) else RallyTvPalette.Muted,
                    fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(5.dp))
            Text("${matchupTeamName(event.awayTeam?.name, event.league)} vs ${matchupTeamName(event.homeTeam?.name, event.league)}",
                color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 31.sp,
                fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.62f))
            Text(
                if (isLive || event.status == EventStatus.FINISHED)
                    "${event.awayTeam?.abbreviation ?: "AWAY"} ${event.scoreAway ?: "–"} — ${event.homeTeam?.abbreviation ?: "HOME"} ${event.scoreHome ?: "–"}   |   ${event.gameStatusDetail.orEmpty()}"
                else eventHeroTimeFormatter.format(event.startTime),
                color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 14.sp, maxLines = 1)
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                val watchLabel = when {
                    isLive -> "▶   Watch Live"
                    event.status == EventStatus.FINISHED -> "▶   Watch Replay"
                    else -> "▶   View Sources"
                }
                RallyTvActionButton(watchLabel, onWatch, primary = true, focusRequester = primaryFocus, modifier = Modifier.focusProperties { down = nextFocus })
                RallyTvActionButton(if (isEventWatchlisted) "✓   In Watchlist" else "+   Add to Watchlist", onToggleEventWatchlist, modifier = Modifier.focusProperties { down = nextFocus })
                if (primaryStreamId == null && isLoadingStreams) Text("Finding broadcasts…", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun EventStatusLine(event: SportEvent, isLive: Boolean) {
    val label = when {
        isLive -> "LIVE · ${event.gameStatusDetail?.takeIf(String::isNotBlank) ?: "NOW"}"
        event.status == EventStatus.FINISHED -> "FINAL"
        else -> "UPCOMING"
    }
    Text(
        label,
        color = RallyTvPalette.Text,
        fontFamily = RallyBodyFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = .7.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isLive) RallyTvPalette.Live else Color(0xB30F1724))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun HeroScore(team: Team?, badge: String?, score: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(badge ?: team?.logoUrl, null, Modifier.size(38.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(4.dp))
        Text(team?.abbreviation ?: "—", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(score?.toString() ?: "–", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EventTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember(label) { mutableStateOf(false) }
    Column(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected || focused) Brush.verticalGradient(listOf(Color.White.copy(alpha = if (focused) .16f else .11f), Color.White.copy(alpha = .04f))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
            .then(if (selected || focused) Modifier.border(1.dp, Color.White.copy(alpha = if (focused) .34f else .14f), RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = if (selected || focused) RallyTvPalette.Text else RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun EventOverview(
    event: SportEvent,
    broadcastQuality: BroadcastQualityInfo,
    favoriteTeamIds: Set<String>,
    injuries: Map<String, List<TeamInjury>>,
    onToggleFavoriteTeam: (Team) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EventColumn("Game Info", Modifier.weight(1f)) {
            OverviewDetail(OverviewIcon.STADIUM, event.venue ?: "Venue to be announced", event.liveStats["Venue City"].orEmpty())
            OverviewDetail(OverviewIcon.TV, event.liveStats["TV Broadcast"] ?: "Broadcast to be announced", broadcastQuality.badgeText)
            OverviewDetail(OverviewIcon.CALENDAR, eventTimeFormatter.format(event.startTime), event.gameStatusDetail.orEmpty())
            val awayRecord = event.liveStats["${event.awayTeam?.abbreviation} Record"]
            val homeRecord = event.liveStats["${event.homeTeam?.abbreviation} Record"]
            val records = listOfNotNull(
                awayRecord?.let { "${matchupTeamName(event.awayTeam?.name, event.league)} $it" },
                homeRecord?.let { "${matchupTeamName(event.homeTeam?.name, event.league)} $it" }
            ).joinToString("  |  ")
            OverviewDetail(OverviewIcon.TEAMS, records.ifBlank { "${event.awayTeam?.abbreviation ?: "Away"} vs ${event.homeTeam?.abbreviation ?: "Home"}" }, event.eventContextTitle.orEmpty())
            event.liveStats["Weather"]?.let { weather ->
                OverviewDetail(OverviewIcon.WEATHER, listOfNotNull(event.liveStats["Temperature"], weather).joinToString(" · "), "")
            }
        }
        EventColumn("Team Form", Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TeamFormBadge(event.awayTeam, event.awayTeamBadge, event.liveStats, Modifier.weight(1f), event.league)
                Text("VS", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 11.sp)
                TeamFormBadge(event.homeTeam, event.homeTeamBadge, event.liveStats, Modifier.weight(1f), event.league)
            }
            Spacer(Modifier.height(6.dp))
            val formStats = event.teamStats.filterNot { stat ->
                listOf("spread", "moneyline", "over/under", "odds", "prediction", "win prob").any { stat.label.contains(it, true) }
            }.take(3)
            if (formStats.isEmpty()) Text("Team statistics will appear when available.", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 10.sp)
            formStats.forEach { stat ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stat.awayValue, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(stat.label, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 9.sp, modifier = Modifier.weight(2f), textAlign = TextAlign.Center, maxLines = 1)
                    Text(stat.homeValue, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
            EventWinProbability(event)
        }
        EventColumn("Player Leaders", Modifier.weight(1f)) {
            EventPlayerLeaders(event)
        }
    }
}

@Composable
private fun EventPlayerLeaders(event: SportEvent) {
    val leaders = event.playerLeaders
        .distinctBy { "${it.teamAbbr}:${it.playerShortName}" }
        .take(4)
    if (leaders.isNotEmpty()) {
        leaders.forEach { leader ->
            Row(
                Modifier.fillMaxWidth().height(31.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    leader.headshotUrl ?: leader.teamLogoUrl,
                    leader.playerShortName,
                    Modifier.size(26.dp).clip(RoundedCornerShape(50)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(leader.playerShortName, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(leader.position, leader.category).joinToString(" · ").uppercase(), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 7.5.sp, maxLines = 1)
                }
                Text(leader.statDisplay, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        return
    }

    val rows = event.playerStatTables.flatMap { table -> table.rows.take(2).map { table to it } }.take(4)
    if (rows.isEmpty()) {
        Text("Player leaders will appear when published.", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 9.sp)
    } else rows.forEach { (table, player) ->
        Row(Modifier.fillMaxWidth().height(31.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(player.headshotUrl ?: table.teamLogoUrl, player.displayName, Modifier.size(26.dp).clip(RoundedCornerShape(50)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(player.displayName, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(player.position, player.jersey?.let { "#$it" }).joinToString(" · ").uppercase(), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 7.5.sp, maxLines = 1)
            }
            Text(player.stats.firstOrNull().orEmpty(), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun EventWinProbability(event: SportEvent) {
    val latest = event.winProbability.maxByOrNull { it.sequence }
    val stat = event.teamStats.firstOrNull { it.label.contains("win prob", true) }
    val providerHome = latest?.homeWinPercentage?.coerceIn(0.0, 1.0)
        ?: stat?.homeValue?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()?.div(100.0)
    val awayScore = event.scoreAway?.toDouble()
    val homeScore = event.scoreHome?.toDouble()
    val home = providerHome ?: if (awayScore != null && homeScore != null) {
        val margin = (homeScore - awayScore).coerceIn(-20.0, 20.0)
        (0.5 + margin * 0.022).coerceIn(0.08, 0.92)
    } else 0.5
    val tie = latest?.tiePercentage?.coerceIn(0.0, 1.0) ?: 0.0
    val away = (1.0 - home - tie).coerceIn(0.0, 1.0)
    val awayColor = eventTeamColor(event.awayTeam, Color(0xFF315B78))
    val homeColor = eventTeamColor(event.homeTeam, Color(0xFF7B3345))
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val homePercent = kotlin.math.round(home * 100).toInt()
        val awayPercent = 100 - homePercent - kotlin.math.round(tie * 100).toInt()
        Text("${event.awayTeam?.abbreviation ?: "AWAY"} $awayPercent%", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(if (providerHome == null) "SCORE-BASED ESTIMATE" else "WIN PROBABILITY", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 7.sp, letterSpacing = .5.sp)
        Text("$homePercent% ${event.homeTeam?.abbreviation ?: "HOME"}", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(3.dp))
    Row(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50))) {
        Box(Modifier.weight(away.toFloat().coerceAtLeast(.02f)).fillMaxHeight().background(awayColor))
        Box(Modifier.weight(home.toFloat().coerceAtLeast(.02f)).fillMaxHeight().background(homeColor))
    }
}

private fun eventTeamColor(team: Team?, fallback: Color): Color {
    val raw = team?.colors?.firstOrNull()?.trim().orEmpty()
    if (raw.isNotBlank()) {
        val normalized = if (raw.startsWith("#")) raw else "#$raw"
        runCatching { return Color(android.graphics.Color.parseColor(normalized)) }
    }
    if (team == null) return fallback
    val palette = listOf(Color(0xFF275A77), Color(0xFF7A2C42), Color(0xFF563985), Color(0xFF8B5C1D), Color(0xFF23624E), Color(0xFF7A3D24))
    return palette[(team.id.hashCode() and Int.MAX_VALUE) % palette.size]
}

private enum class OverviewIcon { STADIUM, TV, CALENDAR, TEAMS, WEATHER }

@Composable
private fun OverviewDetail(icon: OverviewIcon, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp)) {
            val color = RallyTvPalette.Muted
            val stroke = 1.2.dp.toPx()
            when (icon) {
                OverviewIcon.STADIUM -> {
                    drawOval(color, topLeft = Offset(size.width * .1f, size.height * .12f), size = Size(size.width * .8f, size.height * .38f), style = Stroke(stroke))
                    drawOval(color, topLeft = Offset(size.width * .22f, size.height * .22f), size = Size(size.width * .56f, size.height * .17f), style = Stroke(stroke))
                    drawLine(color, Offset(size.width * .1f, size.height * .31f), Offset(size.width * .16f, size.height * .78f), stroke)
                    drawLine(color, Offset(size.width * .9f, size.height * .31f), Offset(size.width * .84f, size.height * .78f), stroke)
                    drawLine(color, Offset(size.width * .16f, size.height * .78f), Offset(size.width * .84f, size.height * .78f), stroke)
                }
                OverviewIcon.TV -> {
                    drawRect(color, topLeft = Offset(size.width * .1f, size.height * .12f), size = Size(size.width * .8f, size.height * .62f), style = Stroke(stroke))
                    drawLine(color, Offset(size.width * .5f, size.height * .74f), Offset(size.width * .5f, size.height * .88f), stroke)
                    drawLine(color, Offset(size.width * .32f, size.height * .88f), Offset(size.width * .68f, size.height * .88f), stroke)
                }
                OverviewIcon.CALENDAR -> {
                    drawRect(color, topLeft = Offset(size.width * .12f, size.height * .2f), size = Size(size.width * .76f, size.height * .68f), style = Stroke(stroke))
                    drawLine(color, Offset(size.width * .12f, size.height * .4f), Offset(size.width * .88f, size.height * .4f), stroke)
                    drawLine(color, Offset(size.width * .32f, size.height * .1f), Offset(size.width * .32f, size.height * .29f), stroke)
                    drawLine(color, Offset(size.width * .68f, size.height * .1f), Offset(size.width * .68f, size.height * .29f), stroke)
                }
                OverviewIcon.TEAMS -> {
                    drawCircle(color, radius = size.width * .13f, center = Offset(size.width * .34f, size.height * .3f), style = Stroke(stroke))
                    drawCircle(color, radius = size.width * .13f, center = Offset(size.width * .67f, size.height * .3f), style = Stroke(stroke))
                    drawArc(color, 190f, 160f, false, topLeft = Offset(size.width * .05f, size.height * .43f), size = Size(size.width * .55f, size.height * .49f), style = Stroke(stroke))
                    drawArc(color, 190f, 160f, false, topLeft = Offset(size.width * .4f, size.height * .43f), size = Size(size.width * .55f, size.height * .49f), style = Stroke(stroke))
                }
                OverviewIcon.WEATHER -> {
                    drawCircle(color, radius = size.width * .18f, center = Offset(size.width * .58f, size.height * .38f), style = Stroke(stroke))
                    drawArc(color, 165f, 210f, false, topLeft = Offset(size.width * .08f, size.height * .38f), size = Size(size.width * .78f, size.height * .44f), style = Stroke(stroke))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TeamFormBadge(team: Team?, badge: String?, liveStats: Map<String, String>, modifier: Modifier = Modifier, league: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(badge ?: team?.logoUrl, null, Modifier.size(30.dp), contentScale = ContentScale.Fit)
        Text(matchupTeamName(team?.name, league), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(liveStats["${team?.abbreviation} Record"] ?: team?.abbreviation.orEmpty(), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 9.sp)
    }
}

@Composable
private fun EventColumn(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(eventShape)
            .background(Color(0xD0071116))
            .border(1.dp, Color(0x3D6B89A5), eventShape)
            .padding(10.dp)
    ) {
        Text(title, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        content()
    }
}

@Composable
private fun TeamInfo(
    team: Team?,
    badge: String?,
    stats: Map<String, String>,
    favoriteTeamIds: Set<String>,
    onToggleFavoriteTeam: (Team) -> Unit
) {
    if (team == null) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(badge ?: team.logoUrl, null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(formatTeamDisplayName(team.name), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stats["${team.abbreviation} Record"] ?: team.abbreviation, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, maxLines = 1)
        }
        RallyTvActionButton(if (team.id in favoriteTeamIds) "Following" else "Follow", { onToggleFavoriteTeam(team) })
    }
}

@Composable
private fun EventStats(event: SportEvent, modifier: Modifier = Modifier) {
    EventColumn("TEAM STATISTICS", modifier) {
        EventTeamHeader(event)
        if (event.teamStats.isEmpty()) {
            EventEmpty("ESPN has not published team statistics for this game.")
        } else {
            TvLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(event.teamStats, key = { it.label }) { stat ->
                    Row(Modifier.fillMaxWidth().rallyReadableFocus().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stat.awayValue, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(stat.label.uppercase(), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stat.homeValue, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    RallyTvRule()
                }
            }
        }
    }
}

@Composable
private fun EventTeamHeader(event: SportEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(event.awayTeam?.abbreviation.orEmpty(), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(event.homeTeam?.abbreviation.orEmpty(), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EventLineups(event: SportEvent, modifier: Modifier = Modifier) {
    EventColumn("LINEUPS & PLAYER STATS", modifier) {
        if (event.playerStatTables.isEmpty()) {
            EventEmpty("ESPN has not published lineups or player statistics for this game.")
        } else {
            TvLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                event.playerStatTables.forEachIndexed { index, table ->
                    item(key = "table:$index") {
                        Text("${table.teamName} · ${statCategoryLabel(table.category)}", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 9.dp, bottom = 4.dp))
                    }
                    items(table.rows.distinctBy { it.athleteId ?: it.displayName }, key = { "player:$index:${it.athleteId ?: it.displayName}" }) { player ->
                        Row(Modifier.fillMaxWidth().rallyReadableFocus().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(player.position ?: player.jersey.orEmpty(), color = RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 12.sp, modifier = Modifier.width(46.dp), maxLines = 1)
                            Text(player.displayName, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(playerStatPairs(table, player).joinToString(" · ") { "${it.first} ${it.second}" }, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                        RallyTvRule()
                    }
                }
            }
        }
    }
}

@Composable
private fun EventPlays(event: SportEvent, onWatchLive: (String) -> Unit, modifier: Modifier = Modifier) {
    EventColumn("PLAY-BY-PLAY", modifier) {
        if (event.plays.isEmpty()) {
            EventEmpty("ESPN has not published play-by-play for this game.")
        } else {
            TvLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(event.plays, key = { "play:${it.id}" }) { play ->
                    Row(Modifier.fillMaxWidth().rallyReadableFocus().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(play.period?.let { "P$it" }, play.clock).joinToString(" · ").ifBlank { "PLAY" }, color = if (play.isScoringPlay) RallyTvPalette.Text else RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(88.dp))
                        Text(play.text, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${play.awayScore ?: "–"}–${play.homeScore ?: "–"}", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp, modifier = Modifier.width(55.dp), textAlign = TextAlign.End)
                    }
                    RallyTvRule()
                }
            }
        }
    }
}

@Composable
private fun EventHighlights(event: SportEvent, onPlayHighlight: (String, String) -> Unit, modifier: Modifier = Modifier) {
    EventColumn("HIGHLIGHTS", modifier) {
        val clips = event.highlightClips
        if (clips.isEmpty()) {
            EventEmpty("Highlights will appear here as they are published.")
        } else {
            TvLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(clips, key = { it.id }) { clip ->
                    var focused by remember(clip.id) { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().height(74.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .rallyTvFocus(focused)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
                            .clickable(enabled = !clip.streamUrl.isNullOrBlank()) { clip.streamUrl?.let { onPlayHighlight(it, clip.title) } }
                            .padding(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(clip.thumbnailUrl, null, Modifier.width(110.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(clip.title, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            clip.description?.takeIf(String::isNotBlank)?.let { Text(it, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        clip.durationSeconds?.let { Text("${it / 60}:${(it % 60).toString().padStart(2, '0')}", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 11.sp) }
                    }
                    RallyTvRule()
                }
            }
        }
    }
}

@Composable
private fun EventSources(
    candidates: List<StreamCandidate>,
    channels: List<RelevantChannel>,
    stremioStreams: List<StremioStreamOption>,
    broadcastStations: List<String>,
    quality: BroadcastQualityInfo,
    loading: Boolean,
    onWatchLive: (String) -> Unit,
    onPickSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recommendedSources = remember(candidates) { candidates.distinctBy { it.playbackTarget } }
    val candidateTargets = remember(recommendedSources) { recommendedSources.mapTo(hashSetOf()) { it.playbackTarget } }
    val remainingWebSources = remember(stremioStreams, candidateTargets) {
        stremioStreams.distinctBy { it.streamUrl }.filterNot { it.streamUrl in candidateTargets }
    }
    val remainingIptvSources = remember(channels, candidateTargets) {
        channels.distinctBy { it.channel.id }.filterNot { it.channel.id in candidateTargets }
    }
    EventColumn("SOURCES", modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(quality.badgeText, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(quality.evidenceSource, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp)
            }
            RallyTvActionButton("Open all sources", onPickSource)
        }
        broadcastStations.firstOrNull()?.let {
            Spacer(Modifier.height(12.dp))
            Text("Broadcast: $it", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (loading && candidates.isEmpty() && channels.isEmpty() && stremioStreams.isEmpty()) {
            EventEmpty("Finding IPTV and Stremio sources…")
        } else if (candidates.isEmpty() && channels.isEmpty() && stremioStreams.isEmpty()) {
            EventEmpty("No broadcast choices are available yet. Open all sources to check again.")
        } else {
            TvLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                if (recommendedSources.isNotEmpty()) {
                    item("recommended") { EventListLabel("RECOMMENDED") }
                    items(recommendedSources, key = { it.id }) { source ->
                        EventSourceLine(source.title, source.matchEvidence, source.playbackTarget, source.preflightPassed != false, onWatchLive)
                    }
                }
                if (remainingWebSources.isNotEmpty()) {
                    item("stremio") { EventListLabel("STREMIO") }
                    items(remainingWebSources, key = { it.streamUrl }) { source ->
                        EventSourceLine(source.title, source.description ?: source.addonName.orEmpty(), source.streamUrl, source.isDirectPlayable, onWatchLive)
                    }
                }
                if (remainingIptvSources.isNotEmpty()) {
                    item("iptv") { EventListLabel("IPTV") }
                    items(remainingIptvSources, key = { it.channel.id }) { source ->
                        EventSourceLine(source.channel.name, source.matchBadge ?: source.channel.category, source.channel.id, true, onWatchLive)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventSourceLine(title: String, subtitle: String, target: String, enabled: Boolean, onWatchLive: (String) -> Unit) {
    var focused by remember(target) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.rallyTvFocus(focused)
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(enabled = enabled) { onWatchLive(target) }.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) RallyTvPalette.Text else RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (enabled) "Watch" else "Unavailable", color = if (enabled) RallyTvPalette.Accent else RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    RallyTvRule()
}

@Composable
private fun EventListLabel(label: String) {
    Text(label, color = RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, bottom = 5.dp))
}

@Composable
private fun EventEmpty(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EventDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
    }
    RallyTvRule()
}

@Composable
fun AppleTvStreamPicker(
    channels: List<RelevantChannel>,
    stremioStreams: List<StremioStreamOption>,
    candidates: List<StreamCandidate> = emptyList(),
    broadcastStations: List<String> = emptyList(),
    broadcastQuality: BroadcastQualityInfo = BroadcastQualityInfo("HD", "Available: HD"),
    eventName: String = "",
    onChannelSelected: (String) -> Unit,
    onCancel: () -> Unit
) {
    val backFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    val rankedSources = remember(candidates) { candidates.distinctBy { it.playbackTarget } }
    val candidateTargets = remember(rankedSources) { rankedSources.mapTo(hashSetOf()) { it.playbackTarget } }
    val webSources = remember(stremioStreams, candidateTargets) {
        stremioStreams.distinctBy { it.streamUrl }.filterNot { it.streamUrl in candidateTargets }
    }
    val iptvSources = remember(channels, candidateTargets) {
        channels.distinctBy { it.channel.id }.filterNot { it.channel.id in candidateTargets }
    }
    val firstRankedSource = rankedSources.firstOrNull { it.preflightPassed != false }
    val firstWebSource = webSources.firstOrNull { it.isDirectPlayable }
    val hasPlayableSource = firstRankedSource != null || firstWebSource != null || iptvSources.isNotEmpty()

    BackHandler(onBack = onCancel)
    LaunchedEffect(rankedSources.size, webSources.size, iptvSources.size, hasPlayableSource) {
        delay(120)
        runCatching { (if (hasPlayableSource) sourceFocus else backFocus).requestFocus() }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xA8000000)),
        contentAlignment = Alignment.CenterEnd
    ) {
        val panelShape = RoundedCornerShape(10.dp)
        val panelWidth = minOf(410.dp, maxWidth - 48.dp)
        val panelHeight = maxHeight - 32.dp
        Column(
            Modifier
                .padding(end = 16.dp)
                .width(panelWidth)
                .height(panelHeight)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .clip(panelShape)
                .background(Color(0xF0181D23))
                .border(1.dp, Color(0x59858D96), panelShape)
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Choose a broadcast", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(eventName.ifBlank { "Available broadcasts" }, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.width(105.dp), horizontalAlignment = Alignment.End) {
                    Text(broadcastStations.firstOrNull() ?: "SOURCES", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${broadcastQuality.badgeText} · ${broadcastQuality.evidenceSource}", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(11.dp))
            RallyTvActionButton("Close", onCancel, focusRequester = backFocus, modifier = Modifier.fillMaxWidth().focusProperties { up = FocusRequester.Cancel; left = FocusRequester.Cancel; right = FocusRequester.Cancel; if (hasPlayableSource) down = sourceFocus })
            Spacer(Modifier.height(10.dp))
            if (rankedSources.isEmpty() && webSources.isEmpty() && iptvSources.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp)) {
                    Text("No broadcast is available yet.", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Broadcasts can appear closer to game time. Check again later or review Sources in Settings.",
                        color = RallyTvPalette.Muted,
                        fontFamily = RallyBodyFont,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                TvLazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (rankedSources.isNotEmpty()) {
                        item("ranked-title") { EventListLabel("RECOMMENDED") }
                        items(rankedSources, key = { it.id }) { source ->
                            SourcePickerLine(source.title, source.matchEvidence, source.playbackTarget, source.preflightPassed != false, if (source == firstRankedSource) sourceFocus else null, onChannelSelected)
                        }
                    }
                    if (webSources.isNotEmpty()) {
                        item("web-title") { EventListLabel("STREMIO") }
                        items(webSources, key = { it.streamUrl }) { source ->
                            SourcePickerLine(source.title, source.description ?: source.addonName.orEmpty(), source.streamUrl, source.isDirectPlayable, if (firstRankedSource == null && source == firstWebSource) sourceFocus else null, onChannelSelected)
                        }
                    }
                    if (iptvSources.isNotEmpty()) {
                        item("iptv-title") { EventListLabel("IPTV") }
                        items(iptvSources, key = { it.channel.id }) { source ->
                            SourcePickerLine(source.channel.name, source.matchBadge ?: source.channel.category, source.channel.id, true, if (firstRankedSource == null && firstWebSource == null && source == iptvSources.first()) sourceFocus else null, onChannelSelected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePickerLine(
    title: String,
    subtitle: String,
    target: String,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onChannelSelected: (String) -> Unit
) {
    var focused by remember(target) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties { left = FocusRequester.Cancel; right = FocusRequester.Cancel }
            .onFocusChanged { focused = it.isFocused }.rallyTvFocus(focused)
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(enabled = enabled) { onChannelSelected(target) }.padding(horizontal = 9.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) RallyTvPalette.Text else RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (enabled) "WATCH" else "UNAVAILABLE", color = if (enabled) RallyTvPalette.Text else RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
    }
    RallyTvRule()
}
