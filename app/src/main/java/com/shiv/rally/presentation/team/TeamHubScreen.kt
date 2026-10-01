@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.team

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.TeamHub
import com.shiv.rally.domain.model.TeamInjury
import com.shiv.rally.domain.model.TeamPlayer
import com.shiv.rally.presentation.common.RallyTvActionButton
import com.shiv.rally.presentation.common.rallyReadableFocus
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import kotlinx.coroutines.delay
import java.time.ZoneId

@Composable
fun TeamHubScreen(
    viewModel: TeamHubViewModel = hiltViewModel(),
    onEventClick: (SportEvent) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstFocus = initialFocusRequester ?: remember { FocusRequester() }
    BackHandler(onBack = onBack)
    when (val current = state) {
        TeamHubUiState.Loading -> TeamHubMessage("Loading your team…", onBack = onBack, firstFocus = firstFocus)
        TeamHubUiState.NotFavorite -> TeamHubMessage("Team pages are available only for your favorite teams.", onBack = onBack, firstFocus = firstFocus)
        is TeamHubUiState.Error -> TeamHubMessage(current.message, onRetry = viewModel::load, onBack = onBack, firstFocus = firstFocus)
        is TeamHubUiState.Success -> {
            LaunchedEffect(current.hub.team.id) {
                delay(120)
                runCatching { firstFocus.requestFocus() }
            }
            TeamHubContent(current.hub, current.favoritePlayerIds, firstFocus, onEventClick, viewModel::toggleFavoritePlayer, viewModel::removeFavorite, onBack)
        }
    }
}

private enum class TeamHubSection { OVERVIEW, GAMES, ROSTER, INJURIES }

@Composable
private fun TeamHubContent(
    hub: TeamHub,
    favoritePlayerIds: Set<String>,
    firstFocus: FocusRequester,
    onEventClick: (SportEvent) -> Unit,
    onTogglePlayer: (String) -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit
) {
    var section by remember(hub.team.id) { mutableStateOf(TeamHubSection.OVERVIEW) }
    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 20.dp, bottom = 20.dp)) {
        Text("MY RALLY / ${formatLeagueDisplayName(hub.team.league).uppercase()}", color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                Text(hub.team.abbreviation.take(3), color = RallyTvPalette.Muted, fontFamily = RallyDisplayFont, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (!hub.team.logoUrl.isNullOrBlank()) AsyncImage(hub.team.logoUrl, hub.team.name, Modifier.size(50.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(hub.team.name, color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 35.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                hub.standing?.let { Text(it.summary, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp, maxLines = 1) }
            }
            RallyTvActionButton("Back", onBack, focusRequester = firstFocus)
            Spacer(Modifier.width(14.dp))
            RallyTvActionButton("Remove team", onRemove)
        }
        Spacer(Modifier.height(20.dp))
        RallyTvRule()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RallyTvActionButton("Overview", { section = TeamHubSection.OVERVIEW }, primary = section == TeamHubSection.OVERVIEW)
            RallyTvActionButton("Games", { section = TeamHubSection.GAMES }, primary = section == TeamHubSection.GAMES)
            if (hub.roster.isNotEmpty()) RallyTvActionButton("Roster", { section = TeamHubSection.ROSTER }, primary = section == TeamHubSection.ROSTER)
            if (hub.injuries.isNotEmpty()) RallyTvActionButton("Injuries", { section = TeamHubSection.INJURIES }, primary = section == TeamHubSection.INJURIES)
        }
        RallyTvRule()
        when (section) {
            TeamHubSection.OVERVIEW -> TeamOverview(hub, onEventClick)
            TeamHubSection.GAMES -> if (hub.schedule.isNotEmpty()) {
                TvLazyColumn(Modifier.fillMaxWidth()) {
                    items(hub.schedule, key = { it.id }) { event ->
                        TeamMatchupRow(event) { onEventClick(event) }
                        RallyTvRule()
                    }
                }
            } else TeamEmpty("No games are listed for this team.")
            TeamHubSection.ROSTER -> if (hub.roster.isNotEmpty()) {
                TvLazyColumn(Modifier.fillMaxWidth()) {
                    items(hub.roster, key = { it.id }) { player ->
                        TeamPlayerRow(player, player.id in favoritePlayerIds) { onTogglePlayer(player.id) }
                        RallyTvRule()
                    }
                }
            } else TeamEmpty("Roster data is not available.")
            TeamHubSection.INJURIES -> if (hub.injuries.isNotEmpty()) {
                TvLazyColumn(Modifier.fillMaxWidth()) {
                    items(hub.injuries, key = { "${it.playerName}:${it.status}" }) { injury ->
                        TeamInjuryRow(injury)
                        RallyTvRule()
                    }
                }
            } else TeamEmpty("No injuries are listed.")
        }
    }
}

@Composable
private fun TeamOverview(hub: TeamHub, onEventClick: (SportEvent) -> Unit) {
    val completed = remember(hub.schedule) { hub.schedule.filter { it.status == EventStatus.FINISHED }.takeLast(5).reversed() }
    val next = remember(hub.schedule) { hub.schedule.firstOrNull { it.status != EventStatus.FINISHED } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(22.dp))
        Text("SEASON", color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        Text(hub.standing?.summary ?: "Standings update when official league data is available.", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 19.sp)
        Spacer(Modifier.height(24.dp))
        Text("NEXT GAME", color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        if (next == null) TeamEmpty("No upcoming game is listed.") else {
            TeamMatchupRow(next) { onEventClick(next) }
            RallyTvRule()
        }
        Spacer(Modifier.height(20.dp))
        Text("RECENT FORM", color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        if (completed.isEmpty()) TeamEmpty("No completed games yet.") else completed.forEach { event ->
            TeamMatchupRow(event) { onEventClick(event) }
            RallyTvRule()
        }
    }
}

@Composable
private fun TeamMatchupRow(event: SportEvent, onClick: () -> Unit) {
    var focused by remember(event.id) { mutableStateOf(false) }
    val live = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
    val finished = event.status == EventStatus.FINISHED
    Row(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.rallyTvFocus(focused)
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(145.dp)) {
            Text(if (live) "●  LIVE" else if (finished) "FINAL" else event.startTime.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                color = if (live) RallyTvPalette.Live else RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(event.gameStatusDetail ?: formatLeagueDisplayName(event.league), color = RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 11.sp, maxLines = 1)
        }
        AsyncImage(event.awayTeamBadge ?: event.awayTeam?.logoUrl, null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(12.dp))
        Text(event.awayTeam?.name ?: "Away", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        Text(if (live || finished) "${event.scoreAway ?: "–"}  :  ${event.scoreHome ?: "–"}" else "AT",
            color = if (live) RallyTvPalette.Accent else RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(event.homeTeam?.name ?: "Home", color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 20.dp))
        AsyncImage(event.homeTeamBadge ?: event.homeTeam?.logoUrl, null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(18.dp))
        Text("→", color = if (focused) RallyTvPalette.Accent else RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 20.sp)
    }
}

@Composable
private fun TeamPlayerRow(player: TeamPlayer, isFavorite: Boolean, onClick: () -> Unit) {
    var focused by remember(player.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.rallyTvFocus(focused)
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(player.headshotUrl, player.name, Modifier.size(48.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(18.dp))
        Text(player.name, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(listOfNotNull(player.position, player.jersey?.let { "#$it" }).joinToString(" · "), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp)
        Spacer(Modifier.width(28.dp))
        Text(if (isFavorite) "FOLLOWING  ✓" else "FOLLOW  +", color = if (isFavorite || focused) RallyTvPalette.Accent else RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TeamInjuryRow(injury: TeamInjury) {
    Row(Modifier.fillMaxWidth().rallyReadableFocus().padding(horizontal = 14.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(injury.playerName, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        injury.detail?.let {
            Text(it, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Text(injury.status.uppercase(), color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TeamEmpty(message: String) {
    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
        Text(message, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 15.sp)
    }
}

@Composable
private fun TeamHubMessage(message: String, onRetry: (() -> Unit)? = null, onBack: () -> Unit, firstFocus: FocusRequester) {
    LaunchedEffect(message) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(66.dp)) {
        RallyTvActionButton("Back", onBack, focusRequester = firstFocus)
        Spacer(Modifier.height(64.dp))
        Text(message, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 20.sp)
        if (onRetry != null) {
            Spacer(Modifier.height(18.dp))
            RallyTvActionButton("Try Again", onRetry, primary = true)
        }
    }
}
