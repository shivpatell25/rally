@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.league

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.R
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.common.RallyPagedRow
import com.shiv.rally.presentation.common.rallyFocusScale
import com.shiv.rally.presentation.common.RallyDashboardSkeleton
import com.shiv.rally.presentation.common.RallyActionableError
import com.shiv.rally.presentation.home.AppleTvMatchesShelf
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.home.getHeroColorBackdrop
import com.shiv.rally.presentation.home.getSportBackdrop
import com.shiv.rally.presentation.theme.AppleTvTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

private val leaguePanel = RoundedCornerShape(10.dp)
private val leaguePill = RoundedCornerShape(8.dp)

@Composable
fun LeagueHubScreen(
    viewModel: LeagueHubViewModel = hiltViewModel(),
    onEventClick: (SportEvent) -> Unit,
    onWatchChannel: (String) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    when (val current = state) {
        LeagueHubUiState.Loading -> RallyDashboardSkeleton()
        is LeagueHubUiState.Error -> RallyActionableError(current.message, onRetry = viewModel::load, onBack = onBack)
        is LeagueHubUiState.Success -> {
            val fallbackFocus = remember { FocusRequester() }
            val firstFocus = initialFocusRequester ?: fallbackFocus
            LaunchedEffect(current.hub.league) {
                delay(120)
                runCatching { firstFocus.requestFocus() }
            }
            LeagueHubDashboard(current.hub, current.redZone, firstFocus, onEventClick, onWatchChannel, onBack)
        }
    }
}

@Composable
private fun LeagueHubDashboard(
    hub: com.shiv.rally.domain.model.LeagueHub,
    redZone: com.shiv.rally.domain.model.IptvChannel?,
    firstFocus: FocusRequester,
    onEventClick: (SportEvent) -> Unit,
    onWatchChannel: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedSection by remember(hub.league) { mutableStateOf(0) }
    var dayOffset by remember(hub.league) { mutableStateOf(0) }
    val selectedDate = remember(dayOffset) { LocalDate.now().plusDays(dayOffset.toLong()) }
    val visibleGames = remember(hub.events, selectedDate) {
        hub.events.filter { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() == selectedDate }
    }
    Column(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 10.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(82.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RALLY SPORTS · LEAGUE CENTER", color = AppleTvTheme.RallyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(formatLeagueDisplayName(hub.league), color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black, letterSpacing = (-.7).sp)
                Text("${hub.events.size} games · ${hub.standings.size} teams", color = AppleTvTheme.TextSecondary, fontSize = 10.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LeagueButton("‹ Back", onBack)
                LeagueButton("Games", { selectedSection = 0 }, primary = selectedSection == 0)
                if (hub.standings.isNotEmpty()) LeagueButton("Standings", { selectedSection = 1 }, primary = selectedSection == 1)
                if (hub.playoffPicture.isNotEmpty() || hub.postseasonEvents.isNotEmpty()) {
                    LeagueButton("Playoffs", { selectedSection = 2 }, primary = selectedSection == 2)
                }
                redZone?.let { channel -> LeagueButton("● RedZone", { onWatchChannel(channel.id) }) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(when (selectedSection) { 1 -> "STANDINGS"; 2 -> if (hub.postseasonEvents.isNotEmpty()) "PLAYOFFS" else "PLAYOFF PICTURE"; else -> "GAMES" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(7.dp))
        if (selectedSection == 0) {
            Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LeagueButton("‹", { dayOffset-- })
                Text(
                    when (dayOffset) { -1 -> "YESTERDAY"; 0 -> "TODAY"; 1 -> "TOMORROW"; else -> selectedDate.toString() },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .8.sp
                )
                LeagueButton("›", { dayOffset++ })
                if (dayOffset != 0) LeagueButton("Today", { dayOffset = 0 })
            }
            Spacer(Modifier.height(7.dp))
        }
        if (selectedSection == 1) {
            RallyPagedRow(
                items = hub.standings,
                key = { it.first },
                firstFocusRequester = firstFocus,
                spacing = 10.dp
            ) { standing, modifier, width ->
                LeagueStandingCard(standing, modifier, width)
            }
        } else if (selectedSection == 2 && hub.postseasonEvents.isNotEmpty()) {
            RallyPagedRow(
                items = hub.postseasonEvents,
                key = { it.id },
                firstFocusRequester = firstFocus,
                spacing = 10.dp
            ) { event, modifier, width ->
                LeagueGameCard(event, modifier, width) { onEventClick(event) }
            }
        } else if (selectedSection == 2 && hub.playoffPicture.isNotEmpty()) {
            RallyPagedRow(
                items = hub.playoffPicture,
                key = { it.first },
                firstFocusRequester = firstFocus,
                spacing = 10.dp
            ) { standing, modifier, width ->
                LeagueStandingCard(standing, modifier, width, "PLAYOFF POSITION")
            }
        } else if (visibleGames.isNotEmpty()) {
            RallyPagedRow(
                items = visibleGames,
                key = { it.id },
                firstFocusRequester = firstFocus,
                spacing = 10.dp
            ) { event, modifier, width ->
                LeagueGameCard(event, modifier, width) { onEventClick(event) }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(260.dp).focusRequester(firstFocus).focusable().clip(leaguePanel).background(Color(0xA80A101B)).border(1.dp, AppleTvTheme.GlassBorder, leaguePanel), contentAlignment = Alignment.Center) {
                Text("No games are listed for this date.", color = AppleTvTheme.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LeagueGameCard(event: SportEvent, modifier: Modifier, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    var focused by remember(event.id) { mutableStateOf(false) }
    val live = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
    val final = event.status == EventStatus.FINISHED
    Box(
        modifier.width(width).height(285.dp).onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused)
            .clip(leaguePanel).background(Color(0xB80A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, leaguePanel)
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(getSportBackdrop(event)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x8005080F), .48f to Color(0x6505080F), 1f to Color(0xF205080F))))
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                (event.eventContextTitle?.takeIf { it.isNotBlank() } ?: formatLeagueDisplayName(event.league)).uppercase(),
                color = AppleTvTheme.TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .7.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Text(if (live) "● LIVE" else if (final) "FINAL" else event.startTime.toString().substringBefore("T"), color = if (live) AppleTvTheme.LiveRed else AppleTvTheme.RallyCyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(event.awayTeamBadge ?: event.awayTeam?.logoUrl, null, Modifier.size(48.dp), contentScale = ContentScale.Fit)
                Text(if (live || final) "${event.scoreAway ?: "–"}  –  ${event.scoreHome ?: "–"}" else "AT", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                AsyncImage(event.homeTeamBadge ?: event.homeTeam?.logoUrl, null, Modifier.size(48.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(15.dp))
            Text(event.awayTeam?.name.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("at ${event.homeTeam?.name.orEmpty()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            event.venue?.let { Text(it, color = AppleTvTheme.TextTertiary, fontSize = 8.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun LeagueStandingCard(standing: Pair<String, String>, modifier: Modifier, width: androidx.compose.ui.unit.Dp, eyebrow: String = "STANDING") {
    var focused by remember(standing.first) { mutableStateOf(false) }
    Column(
        modifier.width(width).height(190.dp).onFocusChanged { focused = it.isFocused }
            .clip(leaguePanel).background(if (focused) Color(0xDF172437) else Color(0xB80A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, leaguePanel)
            .clickable(onClick = {}).padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(eyebrow, color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Column {
            Text(standing.first, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 2)
            Spacer(Modifier.height(5.dp))
            Text(standing.second, color = AppleTvTheme.TextSecondary, fontSize = 10.sp, maxLines = 3)
        }
    }
}

@Composable private fun LeagueMetric(label: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x33202834)).border(1.dp, AppleTvTheme.GlassBorder, RoundedCornerShape(6.dp)).padding(horizontal = 11.dp, vertical = 7.dp)) {
        Text(label, color = AppleTvTheme.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
    }
}

@Composable private fun LeagueSectionTitle(title: String) { Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 58.dp, top = 22.dp, bottom = 8.dp)) }

@Composable private fun LeagueMessage(message: String, action: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White, fontSize = 20.sp)
            if (action != null) { Spacer(Modifier.height(18.dp)); LeagueButton("Try Again", action, true) }
        }
    }
}

@Composable private fun LeagueButton(label: String, onClick: () -> Unit, primary: Boolean = false, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.onFocusChanged { focused = it.isFocused }.rallyFocusScale(focused, AppleTvTheme.ButtonFocusScale).clip(leaguePill).background(if (primary) AppleTvTheme.OffWhite else if (focused) AppleTvTheme.SurfaceFocused else AppleTvTheme.SurfaceRaised).border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else if (primary) Color(0xB8FFFFFF) else AppleTvTheme.GlassBorder, leaguePill).clickable(onClick = onClick).padding(horizontal = 19.dp, vertical = 10.dp)) {
        Text(label, color = if (primary) AppleTvTheme.DeepNavy else AppleTvTheme.OffWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
