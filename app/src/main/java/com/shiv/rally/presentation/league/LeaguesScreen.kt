@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.league

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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Text
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.presentation.common.RallyTvActionButton
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.home.getLeagueLogoResource
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import kotlinx.coroutines.delay

@Composable
fun LeaguesScreen(
    viewModel: LeaguesViewModel = hiltViewModel(),
    initialFocusRequester: FocusRequester? = null,
    onLeague: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstFocus = initialFocusRequester ?: remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 25.dp)) {
        Text("BROWSE / LEAGUES", color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(12.dp))
        Text("Leagues", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 35.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        RallyTvRule()
        when (val current = state) {
            LeaguesUiState.Loading -> LeagueDirectoryMessage("Loading leagues…")
            is LeaguesUiState.Error -> Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(current.message, color = RallyTvPalette.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                RallyTvActionButton("Try again", viewModel::load, focusRequester = firstFocus)
                LaunchedEffect(Unit) { delay(120); runCatching { firstFocus.requestFocus() } }
            }
            is LeaguesUiState.Success -> {
                LaunchedEffect(current.leagues.size) {
                    if (current.leagues.isNotEmpty()) {
                        delay(120)
                        runCatching { firstFocus.requestFocus() }
                    }
                }
                if (current.leagues.isEmpty()) {
                    LeagueDirectoryMessage("No leagues are available right now.")
                } else {
                    TvLazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        itemsIndexed(current.leagues, key = { _, item -> item.league }) { index, item ->
                            LeagueDirectoryRow(
                                item = item,
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                onClick = { onLeague(item.league) }
                            )
                            RallyTvRule()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeagueDirectoryRow(item: LeagueDirectoryItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember(item.league) { mutableStateOf(false) }
    val logo = remember(item.league) { getLeagueLogoResource(item.league) }
    val liveCount = item.events.count { it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME }
    Row(
        modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.rallyTvFocus(focused)
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (logo != null) {
                androidx.compose.foundation.Image(painterResource(logo), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
            } else {
                Text(item.league.uppercase().take(4), color = RallyTvPalette.Accent, fontFamily = RallyDisplayFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(formatLeagueDisplayName(item.league), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(item.league.uppercase(), color = RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 12.sp)
        }
        if (liveCount > 0) {
            Text("●  $liveCount LIVE", color = RallyTvPalette.Live, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(28.dp))
        }
        Text(if (item.events.isEmpty()) "EXPLORE" else "${item.events.size} GAMES", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(20.dp))
        Text("→", color = if (focused) RallyTvPalette.Accent else RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 20.sp)
    }
}

@Composable
private fun LeagueDirectoryMessage(message: String) {
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Text(message, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 16.sp)
    }
}
