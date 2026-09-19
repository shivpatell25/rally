@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.team

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.TeamHub
import com.shiv.rally.presentation.common.RallyPagedRow
import com.shiv.rally.presentation.common.rallyFocusScale
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.home.getHeroColorBackdrop
import com.shiv.rally.presentation.theme.AppleTvTheme
import kotlinx.coroutines.delay

private val hubPanel = RoundedCornerShape(10.dp)
private val hubPill = RoundedCornerShape(8.dp)

@Composable
fun TeamHubScreen(
    viewModel: TeamHubViewModel = hiltViewModel(),
    onEventClick: (SportEvent) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    when (val current = state) {
        TeamHubUiState.Loading -> HubMessage("Loading your team…")
        TeamHubUiState.NotFavorite -> HubMessage("Team pages are available only for your favorite teams.", onBack)
        is TeamHubUiState.Error -> HubMessage(current.message, viewModel::load)
        is TeamHubUiState.Success -> TeamHubContent(current.hub, current.favoritePlayerIds, onEventClick, viewModel::toggleFavoritePlayer, viewModel::removeFavorite, onBack, initialFocusRequester)
    }
}

@Composable
private fun TeamHubContent(hub: TeamHub, favoritePlayerIds: Set<String>, onEventClick: (SportEvent) -> Unit, onTogglePlayer: (String) -> Unit, onRemove: () -> Unit, onBack: () -> Unit, initialFocusRequester: FocusRequester?) {
    val fallbackFocus = remember { FocusRequester() }
    val firstFocus = initialFocusRequester ?: fallbackFocus
    LaunchedEffect(hub.team.id) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }
    TeamHubDashboard(hub, favoritePlayerIds, firstFocus, onEventClick, onTogglePlayer, onRemove, onBack)
}

private enum class TeamHubSection { OVERVIEW, GAMES, ROSTER, INJURIES }

@Composable
private fun TeamHubDashboard(
    hub: TeamHub,
    favoritePlayerIds: Set<String>,
    firstFocus: FocusRequester,
    onEventClick: (SportEvent) -> Unit,
    onTogglePlayer: (String) -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit
) {
    var section by remember(hub.team.id) { mutableStateOf(TeamHubSection.OVERVIEW) }
    Column(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 9.dp, bottom = 15.dp)) {
        Row(Modifier.fillMaxWidth().height(104.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp).clip(hubPanel).background(AppleTvTheme.GlassSurfaceSubtle).border(1.dp, AppleTvTheme.GlassBorder, hubPanel), contentAlignment = Alignment.Center) {
                Text(hub.team.abbreviation.take(3), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (!hub.team.logoUrl.isNullOrBlank()) AsyncImage(hub.team.logoUrl, hub.team.name, Modifier.size(66.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("MY TEAMS · ${formatLeagueDisplayName(hub.team.league).uppercase()}", color = AppleTvTheme.RallyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text(hub.team.name, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = (-.6).sp)
                hub.standing?.let { Text(it.summary, color = AppleTvTheme.TextSecondary, fontSize = 10.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                HubButton("‹ Back", onBack, modifier = Modifier.focusRequester(firstFocus))
                HubButton("Overview", { section = TeamHubSection.OVERVIEW }, primary = section == TeamHubSection.OVERVIEW)
                HubButton("Games", { section = TeamHubSection.GAMES }, primary = section == TeamHubSection.GAMES)
                if (hub.roster.isNotEmpty()) HubButton("Roster", { section = TeamHubSection.ROSTER }, primary = section == TeamHubSection.ROSTER)
                if (hub.injuries.isNotEmpty()) HubButton("Injuries", { section = TeamHubSection.INJURIES }, primary = section == TeamHubSection.INJURIES)
                HubButton("Remove", onRemove)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(section.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(7.dp))
        when (section) {
            TeamHubSection.OVERVIEW -> TeamOverviewPanel(hub, onEventClick)
            TeamHubSection.GAMES -> if (hub.schedule.isNotEmpty()) {
                RallyPagedRow(hub.schedule, key = { it.id }, spacing = 10.dp) { event, modifier, width ->
                    TeamScheduleCard(event, modifier, width) { onEventClick(event) }
                }
            } else TeamHubEmptyPanel("No games are listed for this team.")
            TeamHubSection.ROSTER -> if (hub.roster.isNotEmpty()) {
                RallyPagedRow(hub.roster, key = { it.id }, spacing = 10.dp) { player, modifier, width ->
                    TeamPlayerCard(player, player.id in favoritePlayerIds, modifier, width) { onTogglePlayer(player.id) }
                }
            } else TeamHubEmptyPanel("Roster data is not available.")
            TeamHubSection.INJURIES -> if (hub.injuries.isNotEmpty()) {
                RallyPagedRow(hub.injuries, key = { "${it.playerName}:${it.status}" }, spacing = 10.dp) { injury, modifier, width ->
                    TeamInjuryCard(injury, modifier, width)
                }
            } else TeamHubEmptyPanel("No injuries are listed.")
        }
    }
}

@Composable
private fun TeamScheduleCard(event: SportEvent, modifier: Modifier, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    var focused by remember(event.id) { mutableStateOf(false) }
    Box(
        modifier.width(width).height(270.dp).onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused)
            .clip(hubPanel).background(Color(0xB80A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, hubPanel)
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(getHeroColorBackdrop(event)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x7605080F), 1f to Color(0xF205080F))))
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(formatLeagueDisplayName(event.league).uppercase(), color = AppleTvTheme.RallyCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AsyncImage(event.awayTeamBadge ?: event.awayTeam?.logoUrl, null, Modifier.size(44.dp), contentScale = ContentScale.Fit)
                    Text("VS", color = AppleTvTheme.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    AsyncImage(event.homeTeamBadge ?: event.homeTeam?.logoUrl, null, Modifier.size(44.dp), contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.height(13.dp))
                Text(event.awayTeam?.name.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("at ${event.homeTeam?.name.orEmpty()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(event.gameStatusDetail ?: event.startTime.toString().substringBefore("T"), color = AppleTvTheme.TextSecondary, fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TeamPlayerCard(player: com.shiv.rally.domain.model.TeamPlayer, isFavorite: Boolean, modifier: Modifier, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    var focused by remember(player.id) { mutableStateOf(false) }
    Column(
        modifier.width(width).height(215.dp).onFocusChanged { focused = it.isFocused }
            .clip(hubPanel).background(if (focused) Color(0xDF172437) else Color(0xB80A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, hubPanel)
            .clickable(onClick = onClick).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(player.headshotUrl, player.name, Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(12.dp))
        Text(player.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 2)
        Text(listOfNotNull(player.position, player.jersey?.let { "#$it" }).joinToString(" · "), color = AppleTvTheme.TextSecondary, fontSize = 9.sp)
        if (isFavorite) Text("FOLLOWING", color = Color(0xFF6FCFFF), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
    }
}

@Composable
private fun TeamOverviewPanel(hub: TeamHub, onEventClick: (SportEvent) -> Unit) {
    val completed = remember(hub.schedule) { hub.schedule.filter { it.status == com.shiv.rally.domain.model.EventStatus.FINISHED }.takeLast(5).reversed() }
    val next = remember(hub.schedule) { hub.schedule.firstOrNull { it.status != com.shiv.rally.domain.model.EventStatus.FINISHED } }
    Row(Modifier.fillMaxWidth().height(270.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(.8f).fillMaxSize().clip(hubPanel).background(Color(0xB80A101B)).border(1.dp, AppleTvTheme.GlassBorder, hubPanel).padding(16.dp)) {
            Text("SEASON", color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Text(hub.standing?.summary ?: "Standings update when official league data is available.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(18.dp))
            Text("RECENT FORM", color = AppleTvTheme.TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            if (completed.isEmpty()) Text("No completed games yet.", color = AppleTvTheme.TextSecondary, fontSize = 10.sp)
            completed.forEach { event ->
                Text(event.gameStatusDetail ?: "Final · ${event.scoreAway ?: "–"}–${event.scoreHome ?: "–"}", color = Color.White, fontSize = 10.sp, maxLines = 1)
                Spacer(Modifier.height(5.dp))
            }
        }
        Box(Modifier.weight(1.2f).fillMaxSize()) {
            if (next != null) TeamScheduleCard(next, Modifier.fillMaxWidth(), 420.dp) { onEventClick(next) }
            else TeamHubEmptyPanel("No upcoming game is listed.")
        }
    }
}

@Composable
private fun TeamInjuryCard(injury: com.shiv.rally.domain.model.TeamInjury, modifier: Modifier, width: androidx.compose.ui.unit.Dp) {
    var focused by remember(injury.playerName, injury.status) { mutableStateOf(false) }
    Column(
        modifier.width(width).height(190.dp).onFocusChanged { focused = it.isFocused }
            .clip(hubPanel).background(if (focused) Color(0xDF172437) else Color(0xB80A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, hubPanel)
            .clickable(onClick = {}).padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(injury.status.uppercase(), color = AppleTvTheme.RallyCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        Column {
            Text(injury.playerName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 2)
            injury.detail?.let { Text(it, color = AppleTvTheme.TextSecondary, fontSize = 9.sp, maxLines = 3) }
        }
    }
}

@Composable
private fun TeamHubEmptyPanel(message: String) {
    Box(Modifier.fillMaxWidth().height(220.dp).clip(hubPanel).background(Color(0xA80A101B)).border(1.dp, AppleTvTheme.GlassBorder, hubPanel), contentAlignment = Alignment.Center) {
        Text(message, color = AppleTvTheme.TextSecondary, fontSize = 13.sp)
    }
}

@Composable private fun HubSectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(start = 58.dp, end = 58.dp, top = 25.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = AppleTvTheme.TextSecondary, fontSize = 12.sp)
    }
}

@Composable private fun HubRow(title: String, value: String, detail: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 5.dp).clip(hubPanel).background(AppleTvTheme.SurfaceRaised).border(1.dp, AppleTvTheme.GlassBorder, hubPanel).padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            detail?.let { Text(it, color = AppleTvTheme.TextSecondary, fontSize = 10.sp) }
        }
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun HubMessage(message: String, action: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White, fontSize = 20.sp)
            if (action != null) { Spacer(Modifier.height(18.dp)); HubButton("Continue", action, true) }
        }
    }
}

@Composable private fun HubButton(label: String, onClick: () -> Unit, primary: Boolean = false, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.onFocusChanged { focused = it.isFocused }.rallyFocusScale(focused, AppleTvTheme.ButtonFocusScale).clip(hubPill).background(if (primary) AppleTvTheme.OffWhite else if (focused) AppleTvTheme.SurfaceFocused else AppleTvTheme.SurfaceRaised).border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else if (primary) Color(0xB8FFFFFF) else AppleTvTheme.GlassBorder, hubPill).clickable(onClick = onClick).padding(horizontal = 19.dp, vertical = 10.dp)) {
        Text(label, color = if (primary) AppleTvTheme.DeepNavy else AppleTvTheme.OffWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
