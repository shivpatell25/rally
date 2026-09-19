@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.common.RallyPagedRow
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.home.formatTeamDisplayName
import com.shiv.rally.presentation.home.getSportBackdrop
import com.shiv.rally.presentation.theme.AppleTvTheme
import com.shiv.rally.presentation.common.rallyFocusScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

private val watchShape = RoundedCornerShape(10.dp)

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
        is WatchlistUiState.Error -> WatchMessage(current.message)
        is WatchlistUiState.Success -> MyTeamsContent(current, onTeam, onEvent, onManage, initialFocusRequester)
    }
}

private val teamGameTime = DateTimeFormatter.ofPattern("MMM d · h:mm a").withZone(ZoneId.systemDefault())

@Composable
private fun MyTeamsContent(
    current: WatchlistUiState.Success,
    onTeam: (FavoriteTeam) -> Unit,
    onEvent: (SportEvent) -> Unit,
    onManage: () -> Unit,
    initialFocusRequester: FocusRequester?
) {
    val fallbackFocus = remember { FocusRequester() }
    val firstFocus = initialFocusRequester ?: fallbackFocus
    val gamesFocus = remember { FocusRequester() }
    LaunchedEffect(current.teams.size) { delay(100); runCatching { firstFocus.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 12.dp, bottom = 14.dp)) {
        Column(Modifier.height(70.dp)) {
            Text("MY TEAMS", color = AppleTvTheme.RallyCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Your teams. Their next moments.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = (-.6).sp)
            Text("Only favorites receive a dedicated Rally team page.", color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (current.teams.isEmpty()) {
            EmptyTeamsCard(Modifier.focusRequester(firstFocus), onManage)
        } else {
            MyTeamsSectionTitle("FOLLOWING", "Five teams per page")
            Spacer(Modifier.height(4.dp))
            RallyPagedRow(
                items = current.teams,
                key = { "${it.league}:${it.id}" },
                firstFocusRequester = firstFocus,
                downFocusRequester = if (current.events.isNotEmpty()) gamesFocus else null,
                spacing = 10.dp
            ) { team, modifier, width ->
                TeamWatchCard(team, modifier, width) { onTeam(team) }
            }
            Spacer(Modifier.height(12.dp))
            MyTeamsSectionTitle("GAMES FOR YOU", "Live games and the next scheduled matchups")
            Spacer(Modifier.height(4.dp))
            if (current.events.isNotEmpty()) {
                RallyPagedRow(
                    items = current.events,
                    key = { it.id },
                    firstFocusRequester = gamesFocus,
                    upFocusRequester = firstFocus,
                    spacing = 10.dp
                ) { event, modifier, width ->
                    MyTeamsEventCard(event, modifier, width) { onEvent(event) }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(138.dp).clip(watchShape).background(Color(0x990A101B)).border(1.dp, Color(0x385A7894), watchShape), contentAlignment = Alignment.Center) {
                    Text("No favorite-team games are scheduled in the current feed.", color = AppleTvTheme.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MyTeamsSectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().height(17.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Spacer(Modifier.width(9.dp))
        Text(subtitle, color = AppleTvTheme.TextTertiary, fontSize = 8.sp)
    }
}

@Composable
private fun TeamWatchCard(team: FavoriteTeam, modifier: Modifier, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    var focused by remember(team.id) { mutableStateOf(false) }
    Row(modifier.width(width).height(90.dp).onFocusChanged { focused = it.isFocused }.rallyFocusScale(focused).clip(watchShape).background(if (focused) Color(0xE6172437) else Color(0xB80A101B)).border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else Color(0x385A7894), watchShape).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            Text(team.abbreviation.take(3), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            AsyncImage(team.logoUrl, team.name, Modifier.size(44.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(team.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatLeagueDisplayName(team.league), color = AppleTvTheme.TextSecondary, fontSize = 8.sp)
            Text("TEAM CENTER  ›", color = AppleTvTheme.RallyCyan, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
        }
    }
}

@Composable
private fun MyTeamsEventCard(event: SportEvent, modifier: Modifier, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    var focused by remember(event.id) { mutableStateOf(false) }
    val live = event.status == com.shiv.rally.domain.model.EventStatus.LIVE || event.status == com.shiv.rally.domain.model.EventStatus.HALFTIME
    Box(
        modifier.width(width).height(138.dp).onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused)
            .clip(watchShape).background(Color(0xB80A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else Color(0x385A7894), watchShape)
            .clickable(onClick = onClick)
    ) {
        Image(painterResource(getSportBackdrop(event)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x4505080F), 1f to Color(0xF205080F))))
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(if (live) "● LIVE" else teamGameTime.format(event.startTime).uppercase(), color = if (live) AppleTvTheme.LiveRed else AppleTvTheme.RallyCyan, fontSize = 7.sp, fontWeight = FontWeight.Black)
            Column {
                Text(formatTeamDisplayName(event.awayTeam?.name), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("at ${formatTeamDisplayName(event.homeTeam?.name)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatLeagueDisplayName(event.league), color = AppleTvTheme.TextSecondary, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun EmptyTeamsCard(modifier: Modifier, onManage: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(190.dp).onFocusChanged { focused = it.isFocused }
            .clip(watchShape).background(if (focused) Color(0xD6172437) else Color(0xA80A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else Color(0x385A7894), watchShape)
            .clickable(onClick = onManage).padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("MAKE RALLY YOURS", color = AppleTvTheme.RallyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text("Choose favorite teams to build this page.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Text("CHOOSE TEAMS  ›", color = if (focused) AppleTvTheme.RallyCyan else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WatchMessage(message: String, action: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = AppleTvTheme.TextSecondary, fontSize = 16.sp)
            action?.let {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.clip(watchShape).background(AppleTvTheme.OffWhite).border(1.dp, Color(0xB8FFFFFF), watchShape).clickable(onClick = it).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text("Choose Teams", color = AppleTvTheme.DeepNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
