@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.presentation.theme.AppleTvTheme
import com.shiv.rally.presentation.common.rallyFocusScale
import kotlinx.coroutines.delay

private val searchPanel = RoundedCornerShape(10.dp)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onEvent: (SportEvent) -> Unit,
    onTeam: (FavoriteTeam) -> Unit,
    onLeague: (String) -> Unit,
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fallbackFocus = remember { FocusRequester() }
    val searchFocus = initialFocusRequester ?: fallbackFocus
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { delay(120); runCatching { searchFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text("RALLY · GLOBAL SEARCH", color = AppleTvTheme.RallyCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(4.dp))
                Text("Search", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-.6).sp)
                Text("Games, favorite teams, leagues, channels and addon streams", color = AppleTvTheme.TextSecondary, fontSize = 13.sp)
            }
            SearchButton("‹ Back", onBack)
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            singleLine = true,
            placeholder = { androidx.compose.material3.Text("Team, game, league or channel") },
            modifier = Modifier.fillMaxWidth().height(62.dp).focusRequester(searchFocus),
            shape = searchPanel,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AppleTvTheme.RallyCyan,
                unfocusedBorderColor = Color(0x35FFFFFF),
                focusedContainerColor = AppleTvTheme.Graphite,
                unfocusedContainerColor = Color(0xB80A101B)
            )
        )
        Spacer(Modifier.height(18.dp))
        TvLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 50.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (state.query.isBlank()) item("prompt") { SearchHint("Start typing to search every source.") }
            if (state.isSearching) item("loading") { SearchHint("Searching…") }
            if (state.events.isNotEmpty()) {
                item("events-title") { SearchTitle("Games") }
                items(state.events, key = { "event:${it.id}" }) { event -> SearchResultRow(event.name, "${event.league} · ${event.gameStatusDetail.orEmpty()}") { onEvent(event) } }
            }
            if (state.favoriteTeams.isNotEmpty()) {
                item("teams-title") { SearchTitle("My Teams") }
                items(state.favoriteTeams, key = { "team:${it.league}:${it.id}" }) { team -> SearchResultRow(team.name, "Favorite · ${team.league}") { onTeam(team) } }
            }
            if (state.leagues.isNotEmpty()) {
                item("leagues-title") { SearchTitle("Leagues") }
                items(state.leagues, key = { "league:$it" }) { league -> SearchResultRow(league, "League Center") { onLeague(league) } }
            }
            if (state.channels.isNotEmpty()) {
                item("channels-title") { SearchTitle("Live TV") }
                items(state.channels, key = { "channel:${it.id}" }) { channel -> ChannelSearchRow(channel) { onPlay(channel.id) } }
            }
            val playableAddonStreams = state.addonStreams.filter { it.isDirectPlayable }
            if (playableAddonStreams.isNotEmpty()) {
                item("addons-title") { SearchTitle("Streams") }
                items(playableAddonStreams, key = { "stream:${it.streamUrl}" }) { stream -> StreamSearchRow(stream) { onPlay(stream.streamUrl) } }
            }
            val noResults = state.query.isNotBlank() && !state.isSearching && state.events.isEmpty() && state.favoriteTeams.isEmpty() && state.leagues.isEmpty() && state.channels.isEmpty() && state.addonStreams.isEmpty()
            if (noResults) item("empty") { SearchHint("No results found.") }
        }
    }
}

@Composable private fun ChannelSearchRow(channel: IptvChannel, onClick: () -> Unit) {
    val now = channel.guide?.now?.title
    val next = channel.guide?.next?.title
    SearchResultRow(channel.name, listOfNotNull(now?.let { "Now · $it" }, next?.let { "Next · $it" }, channel.category.takeIf { now == null }).joinToString("   "), onClick)
}

@Composable private fun StreamSearchRow(stream: StremioStreamOption, onClick: () -> Unit) {
    SearchResultRow(stream.title, listOfNotNull(stream.quality, stream.bitrate).joinToString(" · ").ifBlank { "Adaptive" }, onClick)
}

@Composable private fun SearchTitle(title: String) { Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 13.dp, bottom = 2.dp)) }
@Composable private fun SearchHint(text: String) { Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Text(text, color = AppleTvTheme.TextSecondary, fontSize = 16.sp) } }

@Composable private fun SearchResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    var focused by remember(title, subtitle) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(70.dp).onFocusChanged { focused = it.isFocused }.rallyFocusScale(focused, AppleTvTheme.ButtonFocusScale).clip(searchPanel).background(if (focused) Color(0xE6172437) else Color(0xB80A101B)).border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else Color(0x385A7894), searchPanel).clickable(onClick = onClick).padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (focused) AppleTvTheme.RallyCyan else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = AppleTvTheme.TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = if (focused) AppleTvTheme.RallyCyan else Color.White, fontSize = 22.sp)
    }
}

@Composable private fun SearchButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.onFocusChanged { focused = it.isFocused }.rallyFocusScale(focused, AppleTvTheme.ButtonFocusScale).clip(RoundedCornerShape(8.dp)).background(if (focused) AppleTvTheme.SurfaceFocused else AppleTvTheme.SurfaceRaised).border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 19.dp, vertical = 10.dp)) {
        Text(label, color = if (focused) AppleTvTheme.RallyCyan else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
