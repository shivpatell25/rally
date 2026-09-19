@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.event

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
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
import com.shiv.rally.domain.model.RelevantChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.model.StreamCandidate
import com.shiv.rally.domain.model.Team
import com.shiv.rally.domain.model.parseQualityFromChannelName
import com.shiv.rally.domain.model.resolveMaxBroadcastQuality
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.home.formatTeamDisplayName
import com.shiv.rally.presentation.home.getHeroColorBackdrop
import com.shiv.rally.presentation.theme.AppleTvTheme
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import com.shiv.rally.presentation.common.rallyFocusScale
import com.shiv.rally.presentation.common.RallyControlButton
import com.shiv.rally.presentation.common.RallyDashboardSkeleton
import com.shiv.rally.presentation.common.RallyActionableError
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val eventTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d · h:mm a").withZone(ZoneId.systemDefault())
private val eventHeroTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a").withZone(ZoneId.systemDefault())
private val pillShape = RoundedCornerShape(6.dp)
private val panelShape = RoundedCornerShape(10.dp)
private val sourceShape = RoundedCornerShape(8.dp)

@Composable
fun EventScreen(
    viewModel: EventViewModel = hiltViewModel(),
    onWatchLive: (channelId: String) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        EventUiState.Loading -> EventLoadingState()
        is EventUiState.Error -> EventErrorState(current.message, onBack)
        is EventUiState.Success -> EventContent(
            event = current.event,
            primaryStreamTarget = current.primaryStreamTarget,
            relevantChannels = current.relevantChannels,
            broadcastStations = current.broadcastStations,
            stremioStreams = current.stremioStreams,
            streamCandidates = current.streamCandidates,
            isLoadingStreams = current.isLoadingStreams,
            favoriteTeamIds = current.favoriteTeamIds,
            onToggleFavoriteTeam = viewModel::toggleFavoriteTeam,
            onWatchLive = onWatchLive,
            onBack = onBack,
            initialFocusRequester = initialFocusRequester
        )
    }
}

@Composable
private fun EventLoadingState() {
    RallyDashboardSkeleton()
}

@Composable
private fun EventErrorState(message: String, onBack: () -> Unit) {
    RallyActionableError(message, onBack = onBack)
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
    initialFocusRequester: FocusRequester? = null
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
            onToggleFavoriteTeam = onToggleFavoriteTeam,
            onWatchLive = onWatchLive,
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
    initialFocusRequester: FocusRequester? = null
) {
    val fallbackFocus = remember { FocusRequester() }
    val primaryFocus = initialFocusRequester ?: fallbackFocus
    val isLive = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
    val isFinal = event.status == EventStatus.FINISHED

    LaunchedEffect(primaryStreamId, isLoadingStreams) {
        delay(120)
        runCatching { primaryFocus.requestFocus() }
    }

    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 7.dp, bottom = 9.dp)
    ) {
            Box(
                Modifier.fillMaxWidth().height(207.dp).clip(panelShape)
                    .background(AppleTvTheme.GlassSurfaceHeavy).border(1.dp, AppleTvTheme.GlassBorder, panelShape)
            ) {
                Image(painterResource(getHeroColorBackdrop(event)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE805080F), .45f to Color(0xB805080F), .72f to Color(0x3805080F), 1f to Color(0x0805080F))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .68f to Color(0x1805080F), 1f to Color(0x9005080F))))

                Column(
                    Modifier.fillMaxHeight().fillMaxWidth(.54f).padding(start = 25.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(Modifier.width(430.dp), verticalAlignment = Alignment.CenterVertically) {
                        EventMetaPill(
                            if (isLive) "LIVE" else if (isFinal) "FINAL" else "UPCOMING",
                            isLive
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatLeagueDisplayName(event.league).uppercase(),
                            color = AppleTvTheme.TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = RallyBodyFont,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TeamHero(
                            event.awayTeam?.name,
                            event.awayTeam?.abbreviation,
                            event.awayTeamBadge ?: event.awayTeam?.logoUrl,
                            Modifier.weight(1f)
                        )
                        Column(Modifier.width(122.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (isLive || isFinal) "${event.scoreAway ?: "–"}  –  ${event.scoreHome ?: "–"}" else "VS",
                                color = Color.White,
                                fontSize = if (isLive || isFinal) 25.sp else 13.sp,
                                fontFamily = RallyBodyFont,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-.35).sp
                            )
                            Text(
                                when {
                                    isLive -> event.gameStatusDetail.orEmpty()
                                    isFinal -> "FINAL"
                                    else -> eventHeroTimeFormatter.format(event.startTime)
                                }.uppercase(),
                                color = AppleTvTheme.TextSecondary,
                                fontSize = 8.sp,
                                fontFamily = RallyBodyFont,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = .7.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            event.eventContextTitle?.takeIf(String::isNotBlank)?.let {
                                Text(it.uppercase(), color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontFamily = RallyBodyFont, letterSpacing = .7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TeamHero(
                            event.homeTeam?.name,
                            event.homeTeam?.abbreviation,
                            event.homeTeamBadge ?: event.homeTeam?.logoUrl,
                            Modifier.weight(1f)
                        )
                    }

                    Column {
                        Text(
                            listOfNotNull(event.venue, event.liveStats["TV Broadcast"]).filter(String::isNotBlank).distinct().joinToString("  ·  ").ifBlank { formatLeagueDisplayName(event.league) },
                            color = AppleTvTheme.TextSecondary,
                            fontSize = 8.sp,
                            fontFamily = RallyBodyFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            EventPillButton(
                                label = when {
                                    primaryStreamId != null && isLive -> "Watch live"
                                    primaryStreamId != null -> "Watch"
                                    isLoadingStreams -> "Finding broadcasts"
                                    else -> "Choose broadcast"
                                },
                                onClick = { primaryStreamId?.let(onWatchLive) ?: onPickSource() },
                                primary = true,
                                iconRes = R.drawable.ic_rally_play.takeIf { primaryStreamId != null },
                                loading = isLoadingStreams,
                                modifier = Modifier.focusRequester(primaryFocus)
                            )
                            if (primaryStreamId != null) EventPillButton("Pick source", onPickSource)
                            event.homeTeam?.let { team ->
                                EventPillButton(
                                    if (team.id in favoriteTeamIds) "Saved" else "Save",
                                    { onToggleFavoriteTeam(team) },
                                    iconRes = R.drawable.ic_rally_star
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EventStatsPanel(event, Modifier.weight(1f).fillMaxHeight())
                EventLeadersPanel(event, Modifier.weight(1f).fillMaxHeight())
                EventAnalyticsPanel(event, broadcastQuality, Modifier.weight(1f).fillMaxHeight())
            }
    }
}

@Composable
private fun EventStatsPanel(event: SportEvent, modifier: Modifier = Modifier) {
    Column(modifier.clip(panelShape).background(AppleTvTheme.GlassPanelGradient).border(1.dp, AppleTvTheme.GlassBorder, panelShape).padding(12.dp)) {
        val live = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
        val upcoming = event.status == EventStatus.NOT_STARTED
        PanelHeader(if (live) "LIVE STATS" else if (upcoming) "MATCHUP PREVIEW" else "MATCHUP STATS", if (live && event.teamStats.isNotEmpty()) "UPDATED" else null)
        Spacer(Modifier.height(7.dp))
        EventTeamComparisonHeader(event)
        Spacer(Modifier.height(5.dp))
        if (upcoming || event.teamStats.isEmpty()) {
            val awayRecord = event.liveStats["${event.awayTeam?.abbreviation.orEmpty()} Record"] ?: "—"
            val homeRecord = event.liveStats["${event.homeTeam?.abbreviation.orEmpty()} Record"] ?: "—"
            MatchupComparisonRow(awayRecord, "RECORD", homeRecord)
            MatchupComparisonRow(
                if (upcoming) eventHeroTimeFormatter.format(event.startTime) else event.scoreAway?.toString() ?: "—",
                if (upcoming) "START" else "SCORE",
                if (upcoming) "LOCAL" else event.scoreHome?.toString() ?: "—"
            )
            event.liveStats["TV Broadcast"]?.let { MatchupComparisonRow("", "BROADCAST", it) }
            event.venue?.let { MatchupComparisonRow("", "VENUE", it) }
            event.liveStats.entries.firstOrNull { it.key.contains("rank", true) }?.let { MatchupComparisonRow("", it.key, it.value) }
        } else event.teamStats.take(5).forEach { stat ->
            MatchupComparisonRow(stat.awayValue, stat.label, stat.homeValue)
        }
    }
}

@Composable
private fun EventTeamComparisonHeader(event: SportEvent) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(event.awayTeamBadge ?: event.awayTeam?.logoUrl, null, Modifier.size(25.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(6.dp))
            Text(event.awayTeam?.abbreviation.orEmpty(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(event.homeTeam?.abbreviation.orEmpty(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            AsyncImage(event.homeTeamBadge ?: event.homeTeam?.logoUrl, null, Modifier.size(25.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun MatchupComparisonRow(away: String, label: String, home: String) {
    Row(Modifier.fillMaxWidth().height(27.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(away, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(74.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label.uppercase(), color = AppleTvTheme.TextSecondary, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(home, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(74.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppleTvTheme.Divider))
}

@Composable
private fun EventLeadersPanel(event: SportEvent, modifier: Modifier = Modifier) {
    val showLeaders = event.status != EventStatus.NOT_STARTED && event.playerLeaders.isNotEmpty()
    Column(modifier.clip(panelShape).background(AppleTvTheme.GlassPanelGradient).border(1.dp, AppleTvTheme.GlassBorder, panelShape).padding(12.dp)) {
        PanelHeader(if (showLeaders) "TOP PERFORMERS" else "TEAM OUTLOOK", if (showLeaders) "LEADERS" else null)
        Spacer(Modifier.height(7.dp))
        if (!showLeaders) {
            val records = listOfNotNull(
                event.awayTeam?.let { team -> team to (event.liveStats["${team.abbreviation} Record"] ?: "Season record unavailable") },
                event.homeTeam?.let { team -> team to (event.liveStats["${team.abbreviation} Record"] ?: "Season record unavailable") }
            )
            records.forEach { (team, record) ->
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(team.logoUrl, null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(formatTeamDisplayName(team.name), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(record, color = AppleTvTheme.TextSecondary, fontSize = 9.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AppleTvTheme.Divider))
            }
            val previewFacts = event.liveStats.entries.filter {
                it.key.contains("injur", true) || it.key.contains("rank", true) || it.key.contains("streak", true)
            }.take(3)
            if (previewFacts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                previewFacts.forEach { fact ->
                    Text(fact.key.uppercase(), color = AppleTvTheme.TextTertiary, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
                    Text(fact.value, color = AppleTvTheme.TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Official lineups and player availability will appear when published.", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, lineHeight = 13.sp)
            }
        } else {
            val awayAbbr = event.awayTeam?.abbreviation.orEmpty()
            val homeAbbr = event.homeTeam?.abbreviation.orEmpty()
            val awayLeaders = event.playerLeaders.filter { it.teamAbbr.equals(awayAbbr, true) }.take(3)
            val homeLeaders = event.playerLeaders.filter { it.teamAbbr.equals(homeAbbr, true) }.take(3)
            val unassigned = event.playerLeaders.filter { leader ->
                !leader.teamAbbr.equals(awayAbbr, true) && !leader.teamAbbr.equals(homeAbbr, true)
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                EventLeaderTeamColumn(
                    teamName = awayAbbr,
                    logo = event.awayTeamBadge ?: event.awayTeam?.logoUrl,
                    leaders = awayLeaders.ifEmpty { unassigned.take(3) },
                    modifier = Modifier.weight(1f)
                )
                Box(Modifier.fillMaxHeight().width(1.dp).background(AppleTvTheme.Divider))
                EventLeaderTeamColumn(
                    teamName = homeAbbr,
                    logo = event.homeTeamBadge ?: event.homeTeam?.logoUrl,
                    leaders = homeLeaders.ifEmpty { unassigned.drop(3).take(3) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EventLeaderTeamColumn(
    teamName: String,
    logo: String?,
    leaders: List<com.shiv.rally.domain.model.PlayerLeader>,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(logo, null, Modifier.size(27.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(6.dp))
            Text(teamName, color = Color.White, fontSize = 11.sp, fontFamily = RallyBodyFont, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        if (leaders.isEmpty()) {
            Text("No official leaders yet", color = AppleTvTheme.TextTertiary, fontSize = 8.sp)
        } else leaders.forEach { leader ->
            Row(Modifier.fillMaxWidth().height(39.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    leader.headshotUrl ?: leader.teamLogoUrl,
                    null,
                    Modifier.size(25.dp).clip(CircleShape).background(AppleTvTheme.SurfaceFocused),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(leader.playerShortName, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(leader.statDisplay, color = AppleTvTheme.TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EventAnalyticsPanel(event: SportEvent, broadcastQuality: BroadcastQualityInfo, modifier: Modifier = Modifier) {
    val upcoming = event.status == EventStatus.NOT_STARTED
    val latest = event.winProbability.lastOrNull()?.homeWinPercentage
    val homePct = if (upcoming) null else latest?.times(100)?.toInt()
        ?: event.teamStats.firstOrNull { it.label.equals("Win Prob", true) }?.homeValue?.filter { it.isDigit() }?.toIntOrNull()
    val awayPct = homePct?.let { 100 - it }
    Column(modifier.clip(panelShape).background(AppleTvTheme.GlassPanelGradient).border(1.dp, AppleTvTheme.GlassBorder, panelShape).padding(12.dp)) {
        PanelHeader(if (upcoming) "GAME INFORMATION" else "ANALYTICS", if (!upcoming && event.winProbability.isNotEmpty()) "LIVE MODEL" else null)
        Spacer(Modifier.height(6.dp))
        if (homePct != null && awayPct != null) {
            Text("WIN PROBABILITY", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(event.awayTeamBadge ?: event.awayTeam?.logoUrl, null, Modifier.size(20.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(5.dp))
                    Text("${event.awayTeam?.abbreviation.orEmpty()}  $awayPct%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$homePct%  ${event.homeTeam?.abbreviation.orEmpty()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(5.dp))
                    AsyncImage(event.homeTeamBadge ?: event.homeTeam?.logoUrl, null, Modifier.size(20.dp), contentScale = ContentScale.Fit)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                Box(Modifier.weight(awayPct.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Color(0xFFFFC857)))
                Box(Modifier.weight(homePct.coerceAtLeast(1).toFloat()).fillMaxHeight().background(AppleTvTheme.RallyCyan))
            }
            if (event.winProbability.size > 1) {
                Spacer(Modifier.height(9.dp))
                Text("GAME MOMENTUM", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Spacer(Modifier.height(4.dp))
                ProbabilityChart(event, Modifier.fillMaxWidth().height(58.dp))
            } else {
                Spacer(Modifier.height(11.dp))
                Text("PREGAME OUTLOOK", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Live momentum will appear after the official play-by-play feed begins.",
                    color = AppleTvTheme.TextSecondary,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
            if (event.plays.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("LATEST PLAYS", color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                Spacer(Modifier.height(3.dp))
                event.plays.take(2).forEach { play ->
                    Row(Modifier.fillMaxWidth().height(25.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(play.period?.let { "P$it" }, play.clock).joinToString(" · "), color = AppleTvTheme.TextTertiary, fontSize = 7.sp, modifier = Modifier.width(46.dp))
                        Text(play.text, color = Color.White, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                val metricStats = event.teamStats.filterNot {
                    it.label.equals("Win Prob", true) || it.label.equals("Spread", true) ||
                        it.label.equals("Over/Under", true) || it.label.equals("Moneyline", true)
                }.take(3)
                if (metricStats.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        metricStats.forEach { stat ->
                            EventMetricTile(stat.label, "${stat.awayValue} · ${stat.homeValue}", Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Text("MATCHUP OVERVIEW", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EventMetricTile("STATUS", when (event.status) {
                    EventStatus.LIVE, EventStatus.HALFTIME -> event.gameStatusDetail ?: "LIVE"
                    EventStatus.FINISHED -> "FINAL"
                    else -> "UPCOMING"
                }, Modifier.weight(1f))
                EventMetricTile("VIDEO", broadcastQuality.badgeText, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            EventMetricTile("BROADCAST", broadcastQuality.network ?: event.liveStats["TV Broadcast"] ?: "TO BE ANNOUNCED", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val preview = listOfNotNull(
                event.venue?.takeIf(String::isNotBlank)?.let { "VENUE" to it },
                event.eventContextTitle?.takeIf(String::isNotBlank)?.let { "EVENT" to it }
            )
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                preview.take(2).forEach { (label, value) ->
                    Text(label, color = AppleTvTheme.TextTertiary, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                    Text(value, color = AppleTvTheme.TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun EventMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(7.dp)).background(Color(0x52172437)).border(1.dp, Color(0x285A7894), RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = AppleTvTheme.TextTertiary, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProbabilityChart(event: SportEvent, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        repeat(4) { index ->
            val x = size.width * index / 3f
            drawLine(Color(0x205A7894), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
        }
        repeat(3) { index ->
            val y = size.height * index / 2f
            drawLine(Color(0x285A7894), start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
        }
        val points = event.winProbability
        if (points.size > 1) {
            val path = Path()
            val fillPath = Path().apply { moveTo(0f, size.height) }
            points.forEachIndexed { index, point ->
                val x = size.width * index / (points.size - 1).toFloat()
                val y = size.height * (1f - point.homeWinPercentage.toFloat())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()
            drawPath(fillPath, AppleTvTheme.RallyCyan.copy(alpha = .13f))
            drawPath(path, AppleTvTheme.RallyCyan, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun EventHighlightsPanel(event: SportEvent, onPlay: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelHeader("HIGHLIGHTS", "${event.highlightClips.size} CLIPS")
        Spacer(Modifier.height(9.dp))
        androidx.tv.foundation.lazy.list.TvLazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(event.highlightClips, key = { it.id }) { clip ->
                var focused by remember(clip.id) { mutableStateOf(false) }
                Box(Modifier.width(300.dp).height(164.dp).onFocusChanged { focused = it.isFocused }.clip(panelShape).background(Color(0xD90A101B)).border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else Color(0x385A7894), panelShape).clickable { clip.streamUrl?.let(onPlay) }) {
                    AsyncImage(clip.thumbnailUrl, clip.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF205080F)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
                        Text("▶  PLAY HIGHLIGHT", color = AppleTvTheme.RallyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(clip.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTablePanel(event: SportEvent) {
    val tables = event.playerStatTables.take(2)
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelHeader("PLAYER PERFORMANCE", "OFFICIAL BOX SCORE")
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tables.forEach { table ->
                Column(Modifier.weight(1f).clip(panelShape).background(Color(0xBD0A101B)).border(1.dp, Color(0x385A7894), panelShape).padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(table.teamLogoUrl, null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
                        Spacer(Modifier.width(9.dp))
                        Text(table.teamName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(9.dp))
                    table.rows.take(6).forEach { player ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(player.jersey.orEmpty(), color = AppleTvTheme.TextTertiary, fontSize = 9.sp, modifier = Modifier.width(24.dp))
                            Text(player.shortName ?: player.displayName, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(player.stats.take(3).joinToString("   "), color = AppleTvTheme.TextSecondary, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
            repeat(2 - tables.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PanelHeader(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        trailing?.let { Text(it, color = AppleTvTheme.RallyCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp) }
    }
}

@Composable
private fun TeamHero(name: String?, abbreviation: String?, badge: String?, modifier: Modifier = Modifier) {
    var loaded by remember(badge) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            if (!loaded) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(9.dp)).background(AppleTvTheme.GlassSurfaceSubtle)
                        .border(1.dp, AppleTvTheme.GlassBorder, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(abbreviation?.take(4) ?: "TBD", color = Color.White, fontSize = 11.sp, fontFamily = RallyBodyFont, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                }
            }
            if (!badge.isNullOrBlank()) {
                AsyncImage(
                    model = badge,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Fit,
                    onSuccess = { loaded = true },
                    onError = { loaded = false }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            formatTeamDisplayName(name),
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = RallyBodyFont,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = .1.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EventInformationPanel(
    event: SportEvent,
    broadcastQuality: BroadcastQualityInfo,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(panelShape).background(AppleTvTheme.SurfaceRaised).border(1.dp, AppleTvTheme.GlassBorder, panelShape).padding(16.dp)
    ) {
        Text("MATCHUP", color = AppleTvTheme.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(7.dp))
        val summary = event.liveStats["Headline"] ?: event.liveStats["Details"]
            ?: "${formatTeamDisplayName(event.awayTeam?.name)} at ${formatTeamDisplayName(event.homeTeam?.name)}"
        Text(summary, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)

        Spacer(Modifier.height(17.dp))
        Text("GAME DETAILS", color = AppleTvTheme.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(7.dp))
        EventDetailRow(
            "Status",
            when (event.status) {
                EventStatus.LIVE, EventStatus.HALFTIME -> event.gameStatusDetail ?: "Live"
                EventStatus.FINISHED -> "Final"
                else -> "Scheduled"
            }
        )
        EventDetailRow("Start", eventTimeFormatter.format(event.startTime))
        event.venue?.takeIf(String::isNotBlank)?.let { EventDetailRow("Venue", it) }
        EventDetailRow("Video", broadcastQuality.badgeText)
        EventDetailRow("Verified by", broadcastQuality.evidenceSource)
        broadcastQuality.network?.takeIf(String::isNotBlank)?.let { EventDetailRow("Network", it) }

        if (event.teamStats.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("TEAM STATS", color = AppleTvTheme.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(7.dp))
            event.teamStats.take(3).forEach { stat ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stat.awayValue, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(stat.label, color = AppleTvTheme.TextSecondary, fontSize = 10.sp, maxLines = 1)
                    Text(stat.homeValue, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        if (event.playerLeaders.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("LEADERS", color = AppleTvTheme.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(7.dp))
            event.playerLeaders.take(if (event.teamStats.isEmpty()) 4 else 2).forEach { leader ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(leader.playerShortName, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(leader.statDisplay, color = AppleTvTheme.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun EventDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppleTvTheme.TextTertiary, fontSize = 10.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = AppleTvTheme.OffWhite,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
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
    val firstFocus = remember { FocusRequester() }
    val webSources = remember(stremioStreams) { stremioStreams.distinctBy { it.streamUrl } }
    val firstPlayableWebSource = remember(webSources) { webSources.firstOrNull { it.isDirectPlayable } }
    val iptvSources = remember(channels) { channels.distinctBy { it.channel.id } }
    val rankedSources = remember(candidates) { candidates.distinctBy { it.playbackTarget } }
    val hasVisibleSources = rankedSources.isNotEmpty() || webSources.isNotEmpty() || iptvSources.isNotEmpty()
    val hasPlayableSources = rankedSources.isNotEmpty() || firstPlayableWebSource != null || iptvSources.isNotEmpty()

    BackHandler(onBack = onCancel)
    LaunchedEffect(webSources.size, iptvSources.size) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(AppleTvTheme.ScreenGradient)) {
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 54.dp, end = 54.dp, top = 30.dp, bottom = 64.dp)
        ) {
            item(key = "source-header") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EventPillButton("‹ Matchup", onCancel, modifier = if (!hasPlayableSources) Modifier.focusRequester(firstFocus) else Modifier)
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text("Choose a broadcast", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text(eventName.ifBlank { "Available video options" }, color = AppleTvTheme.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        broadcastStations.firstOrNull()?.let {
                            EventMetaPill(it, false)
                            Spacer(Modifier.width(8.dp))
                        }
                        BroadcastQualityBadge(broadcastQuality.copy(network = null))
                    }
                }
                Spacer(Modifier.height(30.dp))
            }

            if (!hasVisibleSources) {
                item(key = "empty-sources") {
                    Box(Modifier.fillMaxWidth().height(280.dp).clip(panelShape).background(AppleTvTheme.Slate), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No broadcast is available yet", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(7.dp))
                            Text("Broadcasts can appear closer to game time. Check again later or review Sources in Settings.", color = AppleTvTheme.TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }

            if (rankedSources.isNotEmpty()) {
                item(key = "recommended-title") { SourceSectionTitle("Recommended broadcasts", "Verified for this matchup") }
                items(rankedSources.take(100), key = { it.id }) { source ->
                    val qualityLabel = listOfNotNull(
                        source.quality.resolution,
                        "HDR".takeIf { source.quality.isHdr },
                        source.quality.fps,
                        source.stremioStream?.bitrate
                    ).joinToString(" · ").ifBlank { "Adaptive" }
                    StreamSourceCard(
                        title = source.title,
                        subtitle = listOfNotNull(
                            source.stremioStream?.description,
                            source.matchEvidence
                        ).distinct().joinToString(" · "),
                        badges = listOfNotNull(
                            qualityLabel,
                            "Exact matchup".takeIf { source.exactGameMatch },
                            source.preflightLatencyMs?.let { "Verified · ${it} ms" },
                            "Restart available".takeIf { source.channel?.supportsCatchUp == true }
                        ),
                        onClick = { onChannelSelected(source.playbackTarget) },
                        enabled = source.preflightPassed != false,
                        showFullTitle = source.sourceKind == com.shiv.rally.domain.model.StreamSourceKind.STREMIO,
                        modifier = if (source == rankedSources.first()) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            } else if (webSources.isNotEmpty()) {
                val playableCount = webSources.count { it.isDirectPlayable }
                val webOnlyCount = webSources.size - playableCount
                item(key = "web-title") {
                    SourceSectionTitle(
                        "Recommended broadcasts",
                        listOfNotNull(
                            playableCount.takeIf { it > 0 }?.let { "$it playable" },
                            webOnlyCount.takeIf { it > 0 }?.let { "$it web-only" }
                        ).joinToString(" · ")
                    )
                }
                items(webSources, key = { it.streamUrl }) { source ->
                    val sourceSubtitle = if (source.isDirectPlayable) {
                        source.description ?: source.addonName.orEmpty()
                    } else {
                        listOfNotNull(source.description, "Browser-only source — unavailable in the TV player")
                            .joinToString(" · ")
                    }
                    StreamSourceCard(
                        title = source.title,
                        subtitle = sourceSubtitle,
                        badges = listOfNotNull(
                            source.quality,
                            source.bitrate,
                            if (!source.isDirectPlayable) "UNAVAILABLE" else null
                        ),
                        onClick = { onChannelSelected(source.streamUrl) },
                        enabled = source.isDirectPlayable,
                        modifier = if (source == firstPlayableWebSource) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
                item(key = "web-gap") { Spacer(Modifier.height(22.dp)) }
            }

            if (rankedSources.isEmpty() && iptvSources.isNotEmpty()) {
                item(key = "iptv-title") { SourceSectionTitle("More broadcasts", "Ranked for this matchup") }
                items(iptvSources.take(100), key = { it.channel.id }) { source ->
                    val quality = remember(source.channel.name) { parseQualityFromChannelName(source.channel.name) }
                    StreamSourceCard(
                        title = source.channel.name,
                        subtitle = source.channel.guide?.now?.title?.let { "Now · $it" }
                            ?: source.matchBadge ?: source.channel.category.ifBlank { "Live TV" },
                        badges = listOfNotNull(
                            source.channel.guide?.next?.title?.let { "Next · $it" },
                            quality.resolution,
                            quality.fps,
                            if (source.isOfficialBroadcast) "Broadcast" else null,
                            "${source.likelihoodScore.toInt()}% match"
                        ),
                        onClick = { onChannelSelected(source.channel.id) },
                        modifier = if (firstPlayableWebSource == null && source == iptvSources.first()) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceSectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = AppleTvTheme.TextTertiary, fontSize = 12.sp)
    }
}

@Composable
private fun StreamSourceCard(
    title: String,
    subtitle: String,
    badges: List<String>,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showFullTitle: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember(title, subtitle) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused, AppleTvTheme.ButtonFocusScale)
            .clip(sourceShape)
            .background(if (focused) AppleTvTheme.GlassPanelFocusedGradient else AppleTvTheme.GlassPanelGradient)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) Color(0xD6B9D8EA) else AppleTvTheme.GlassBorder, sourceShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (showFullTitle) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Clip
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, color = AppleTvTheme.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                badges.distinct().take(3).forEach { label -> EventMetaPill(label, false) }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (enabled) "Play  ›" else "Unavailable",
                    color = if (enabled) Color.White else AppleTvTheme.TextTertiary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EventPillButton(
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
private fun EventMetaPill(label: String, live: Boolean) {
    Row(
        modifier = Modifier.clip(pillShape).background(if (live) AppleTvTheme.LiveRed else Color(0x30FFFFFF)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (live) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = Color.White, fontSize = 10.sp, fontFamily = RallyBodyFont, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, maxLines = 1)
    }
}

@Composable
fun BroadcastQualityBadge(quality: BroadcastQualityInfo, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(pillShape).background(Color(0x30FFFFFF)).border(1.dp, Color(0x24FFFFFF), pillShape).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(quality.badgeText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        quality.network?.let {
            Text(" · $it", color = AppleTvTheme.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
