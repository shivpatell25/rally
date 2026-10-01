@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.league

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.LeagueHub
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.common.RallyTvActionButton
import com.shiv.rally.presentation.common.rallyReadableFocus
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun LeagueHubScreen(
    viewModel: LeagueHubViewModel = hiltViewModel(),
    onEventClick: (SportEvent) -> Unit,
    onWatchChannel: (String) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstFocus = initialFocusRequester ?: remember { FocusRequester() }
    BackHandler(onBack = onBack)
    when (val current = state) {
        LeagueHubUiState.Loading -> LeagueHubMessage("Loading league…", onBack = onBack, firstFocus = firstFocus)
        is LeagueHubUiState.Error -> LeagueHubMessage(current.message, onRetry = viewModel::load, onBack = onBack, firstFocus = firstFocus)
        is LeagueHubUiState.Success -> {
            LaunchedEffect(current.hub.league) {
                delay(120)
                runCatching { firstFocus.requestFocus() }
            }
            LeagueHubContent(current.hub, current.redZone, firstFocus, onEventClick, onWatchChannel, onBack)
        }
    }
}

@Composable
private fun LeagueHubContent(
    hub: LeagueHub,
    redZone: IptvChannel?,
    firstFocus: FocusRequester,
    onEventClick: (SportEvent) -> Unit,
    onWatchChannel: (String) -> Unit,
    onBack: () -> Unit
) {
    var section by remember(hub.league) { mutableStateOf(0) }
    var dayOffset by remember(hub.league) { mutableStateOf(0) }
    val selectedDate = remember(dayOffset) { LocalDate.now().plusDays(dayOffset.toLong()) }
    val visibleGames = remember(hub.events, selectedDate) {
        hub.events.filter { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() == selectedDate }
    }
    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 20.dp, bottom = 20.dp)) {
        Text("LEAGUE / ${hub.league.uppercase()}", color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatLeagueDisplayName(hub.league), color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 35.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${hub.events.size} games  ·  ${hub.standings.size} teams", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp)
            }
            RallyTvActionButton("Back", onBack, focusRequester = firstFocus)
            if (redZone != null) {
                Spacer(Modifier.width(14.dp))
                RallyTvActionButton("●  Watch RedZone", { onWatchChannel(redZone.id) }, primary = true)
            }
        }
        Spacer(Modifier.height(20.dp))
        RallyTvRule()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RallyTvActionButton("Games", { section = 0 }, primary = section == 0)
            if (hub.standings.isNotEmpty()) RallyTvActionButton("Standings", { section = 1 }, primary = section == 1)
            if (hub.playoffPicture.isNotEmpty() || hub.postseasonEvents.isNotEmpty()) {
                RallyTvActionButton("Playoffs", { section = 2 }, primary = section == 2)
            }
        }
        RallyTvRule()
        if (section == 0) {
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                RallyTvActionButton("←", { dayOffset-- })
                Spacer(Modifier.width(16.dp))
                Text(
                    when (dayOffset) { -1 -> "YESTERDAY"; 0 -> "TODAY"; 1 -> "TOMORROW"; else -> selectedDate.toString() },
                    color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
                )
                Spacer(Modifier.width(16.dp))
                RallyTvActionButton("→", { dayOffset++ })
                if (dayOffset != 0) {
                    Spacer(Modifier.width(12.dp))
                    RallyTvActionButton("Today", { dayOffset = 0 })
                }
            }
            RallyTvRule()
        }
        when {
            section == 1 -> {
                TvLazyColumn(Modifier.fillMaxWidth()) {
                    items(hub.standings, key = { it.first }) { standing ->
                        LeagueStandingRow(standing)
                        RallyTvRule()
                    }
                }
            }
            section == 2 && hub.postseasonEvents.isNotEmpty() -> {
                TvLazyColumn(Modifier.fillMaxWidth()) {
                    items(hub.postseasonEvents, key = { it.id }) { event ->
                        LeagueMatchupRow(event) { onEventClick(event) }
                        RallyTvRule()
                    }
                }
            }
            section == 2 -> {
                TvLazyColumn(Modifier.fillMaxWidth()) {
                    items(hub.playoffPicture, key = { it.first }) { standing ->
                        LeagueStandingRow(standing)
                        RallyTvRule()
                    }
                }
            }
            visibleGames.isNotEmpty() -> {
                TvLazyColumn(Modifier.fillMaxWidth()) {
                    items(visibleGames, key = { it.id }) { event ->
                        LeagueMatchupRow(event) { onEventClick(event) }
                        RallyTvRule()
                    }
                }
            }
            else -> LeagueEmpty("No games are listed for this date.")
        }
    }
}

@Composable
private fun LeagueMatchupRow(event: SportEvent, onClick: () -> Unit) {
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
            Text((event.eventContextTitle?.takeIf { it.isNotBlank() } ?: formatLeagueDisplayName(event.league)),
                color = RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 11.sp, maxLines = 1)
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
private fun LeagueStandingRow(standing: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().rallyReadableFocus().padding(horizontal = 14.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(standing.first, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(standing.second, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LeagueEmpty(message: String) {
    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
        Text(message, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 15.sp)
    }
}

@Composable
private fun LeagueHubMessage(message: String, onRetry: (() -> Unit)? = null, onBack: () -> Unit, firstFocus: FocusRequester) {
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
