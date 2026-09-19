@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.league

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.shiv.rally.presentation.common.RallyPagedRow
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.home.getLeagueBackdrop
import com.shiv.rally.presentation.home.getLeagueLogoResource
import com.shiv.rally.presentation.theme.AppleTvTheme
import com.shiv.rally.presentation.common.rallyFocusScale
import kotlinx.coroutines.delay

private val directoryShape = RoundedCornerShape(10.dp)

@Composable
fun LeaguesScreen(
    viewModel: LeaguesViewModel = hiltViewModel(),
    initialFocusRequester: FocusRequester? = null,
    onLeague: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        LeaguesUiState.Loading -> LeagueDirectoryMessage("Loading leagues…")
        is LeaguesUiState.Error -> LeagueDirectoryMessage(current.message)
        is LeaguesUiState.Success -> {
            val fallbackFocus = remember { FocusRequester() }
            val firstFocus = initialFocusRequester ?: fallbackFocus
            LaunchedEffect(current.leagues.size) { delay(120); runCatching { firstFocus.requestFocus() } }
            Column(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 12.dp, bottom = 18.dp)) {
                Column(Modifier.height(74.dp)) {
                    Text("LEAGUES", color = AppleTvTheme.RallyCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("Every sport. One starting point.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = (-.6).sp)
                    Text("Five leagues at a time. Open one for games, standings, and channels.", color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(16.dp))
                RallyPagedRow(
                    items = current.leagues,
                    key = { it.league },
                    firstFocusRequester = firstFocus,
                    spacing = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { item, modifier, width ->
                    LeagueDirectoryCard(item, { onLeague(item.league) }, modifier, width)
                }
            }
        }
    }
}

@Composable
private fun LeagueDirectoryCard(item: LeagueDirectoryItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp) {
    var focused by remember(item.league) { mutableStateOf(false) }
    Box(modifier.width(width).height(235.dp).onFocusChanged { focused = it.isFocused }.rallyFocusScale(focused).clip(directoryShape).background(Color(0xC20A101B)).border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else Color(0x385A7894), directoryShape).clickable(onClick = onClick)) {
        Image(painterResource(getLeagueBackdrop(item.league)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x2E05080F), Color(0xE605080F)))))
        val leagueLogo = remember(item.league) { getLeagueLogoResource(item.league) }
        Column(
            Modifier.fillMaxSize().padding(15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LEAGUE CENTER", color = AppleTvTheme.TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                if (item.events.any { it.status == com.shiv.rally.domain.model.EventStatus.LIVE || it.status == com.shiv.rally.domain.model.EventStatus.HALFTIME }) {
                    Text("● LIVE", color = AppleTvTheme.LiveRed, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (leagueLogo != null) {
                Image(
                    painter = painterResource(leagueLogo),
                    contentDescription = null,
                    modifier = Modifier.size(78.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    when (item.league.uppercase()) {
                        "NCAAF" -> "CFB"
                        "NCAAB" -> "CBB"
                        else -> item.league.uppercase().take(5)
                    },
                    color = AppleTvTheme.OffWhite,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatLeagueDisplayName(item.league).uppercase(), color = Color.White, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, letterSpacing = .5.sp)
                Text(if (item.events.isEmpty()) "OPEN LEAGUE CENTER" else "${item.events.size} GAMES", color = if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            }
        }
    }
}

@Composable private fun LeagueDirectoryMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message, color = AppleTvTheme.TextSecondary, fontSize = 16.sp) }
}
