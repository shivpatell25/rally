@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.common.RallyPagedRow
import com.shiv.rally.presentation.common.RallyTvActionButton
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.home.formatTeamDisplayName
import com.shiv.rally.presentation.home.matchupTeamName
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val teamGameTime = DateTimeFormatter.ofPattern("MMM d · h:mm a").withZone(ZoneId.systemDefault())
private val watchShape = RoundedCornerShape(4.dp)

@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel = hiltViewModel(),
    onTeam: (FavoriteTeam) -> Unit,
    onEvent: (SportEvent) -> Unit,
    onManage: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        WatchlistUiState.Loading -> WatchMessage("Loading your watchlist…")
        is WatchlistUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val firstFocus = initialFocusRequester ?: remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(100); runCatching { firstFocus.requestFocus() } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.message, color = RallyTvPalette.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                RallyTvActionButton("Try again", viewModel::load, focusRequester = firstFocus)
            }
        }
        is WatchlistUiState.Success -> MyRallyContent(current, onTeam, onEvent, onManage, initialFocusRequester)
    }
}

@Composable
private fun MyRallyContent(
    current: WatchlistUiState.Success,
    onTeam: (FavoriteTeam) -> Unit,
    onEvent: (SportEvent) -> Unit,
    onManage: () -> Unit,
    initialFocusRequester: FocusRequester?
) {
    val fallbackFocus = remember { FocusRequester() }
    val firstFocus = initialFocusRequester ?: fallbackFocus
    val teamGamesFocus = remember { FocusRequester() }
    val savedEventsFocus = remember { FocusRequester() }
    val hasContent = current.teams.isNotEmpty() || current.teamEvents.isNotEmpty() || current.savedEvents.isNotEmpty()

    LaunchedEffect(current.teams.size, current.teamEvents.size, current.savedEvents.size) {
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 54.dp, vertical = 18.dp)) {
        Text("MY RALLY", color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(5.dp))
        Text("Your teams and games", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 31.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Followed teams, their schedule, and games you saved.", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 14.sp)
        if (hasContent) {
            Spacer(Modifier.height(10.dp))
            RallyTvActionButton("Manage teams", onManage)
        }
        Spacer(Modifier.height(18.dp))

        if (!hasContent) {
            EmptyWatchlist(Modifier.focusRequester(firstFocus), onManage)
        } else {

            if (current.teams.isNotEmpty()) {
                WatchlistSectionTitle("FOLLOWED TEAMS", "Open a team")
                Spacer(Modifier.height(6.dp))
                RallyPagedRow(
                    items = current.teams,
                    key = { "${it.league}:${it.id}" },
                    firstFocusRequester = firstFocus,
                    downFocusRequester = when {
                        current.teamEvents.isNotEmpty() -> teamGamesFocus
                        current.savedEvents.isNotEmpty() -> savedEventsFocus
                        else -> null
                    },
                    spacing = 8.dp
                ) { team, modifier, width ->
                    TeamWatchItem(team, modifier, width) { onTeam(team) }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (current.teamEvents.isNotEmpty()) {
                WatchlistSectionTitle("TEAM GAMES", "Schedule for followed teams")
                Spacer(Modifier.height(6.dp))
                RallyPagedRow(
                    items = current.teamEvents,
                    key = { it.id },
                    firstFocusRequester = if (current.teams.isEmpty()) firstFocus else teamGamesFocus,
                    upFocusRequester = firstFocus.takeIf { current.teams.isNotEmpty() },
                    downFocusRequester = savedEventsFocus.takeIf { current.savedEvents.isNotEmpty() },
                    spacing = 8.dp
                ) { event, modifier, width ->
                    WatchEventItem(event, modifier, width) { onEvent(event) }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (current.savedEvents.isNotEmpty()) {
                WatchlistSectionTitle("SAVED EVENTS", "Games saved individually")
                Spacer(Modifier.height(6.dp))
                RallyPagedRow(
                    items = current.savedEvents,
                    key = { it.id },
                    firstFocusRequester = if (current.teams.isEmpty() && current.teamEvents.isEmpty()) firstFocus else savedEventsFocus,
                    upFocusRequester = when {
                        current.teamEvents.isNotEmpty() -> teamGamesFocus
                        current.teams.isNotEmpty() -> firstFocus
                        else -> null
                    },
                    spacing = 8.dp
                ) { event, modifier, width ->
                    WatchEventItem(event, modifier, width) { onEvent(event) }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(subtitle, color = RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        RallyTvRule()
    }
}

@Composable
private fun TeamWatchItem(team: FavoriteTeam, modifier: Modifier, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    var focused by remember(team.id) { mutableStateOf(false) }
    Row(
        modifier.width(width).height(78.dp).onFocusChanged { focused = it.isFocused }.rallyTvFocus(focused)
            .clip(watchShape).background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(team.logoUrl, team.name, Modifier.size(38.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(matchupTeamName(team.name, team.league), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(formatLeagueDisplayName(team.league), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WatchEventItem(event: SportEvent, modifier: Modifier, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    var focused by remember(event.id) { mutableStateOf(false) }
    val live = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
    Column(
        modifier.width(width).height(112.dp).onFocusChanged { focused = it.isFocused }.rallyTvFocus(focused)
            .clip(watchShape).background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatLeagueDisplayName(event.league), color = RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Text(if (live) "LIVE" else if (event.status == EventStatus.FINISHED) "FINAL" else teamGameTime.format(event.startTime).uppercase(), color = if (live) RallyTvPalette.Live else RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(event.awayTeamBadge ?: event.awayTeam?.logoUrl, null, Modifier.size(22.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(7.dp))
            Text(formatTeamDisplayName(event.awayTeam?.name), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            event.scoreAway?.takeIf { event.status !in setOf(EventStatus.NOT_STARTED, EventStatus.CANCELED) }?.let { Text(it.toString(), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(event.homeTeamBadge ?: event.homeTeam?.logoUrl, null, Modifier.size(22.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(7.dp))
            Text(formatTeamDisplayName(event.homeTeam?.name), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            event.scoreHome?.takeIf { event.status !in setOf(EventStatus.NOT_STARTED, EventStatus.CANCELED) }?.let { Text(it.toString(), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun EmptyWatchlist(modifier: Modifier, onManage: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.Start) {
        Text("Nothing in My Rally yet", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Follow a team or save a game from its event detail page.", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        RallyTvActionButton("Choose teams", onManage, modifier = modifier, primary = true)
    }
}

@Composable
private fun WatchMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 16.sp)
    }
}
