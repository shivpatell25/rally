@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import kotlinx.coroutines.delay

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
    var fieldFocused by remember { mutableStateOf(false) }
    val playableStreams = state.addonStreams.filter { it.isDirectPlayable }
    val hasResults = state.events.isNotEmpty() || state.favoriteTeams.isNotEmpty() ||
        state.leagues.isNotEmpty() || state.channels.isNotEmpty() || playableStreams.isNotEmpty()

    BackHandler(onBack = onBack)
    LaunchedEffect(searchFocus) { delay(120); runCatching { searchFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 66.dp, vertical = 26.dp)) {
        Text(
            "SEARCH",
            color = RallyTvPalette.Subtle,
            fontFamily = RallyBodyFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "Find your game.",
            color = RallyTvPalette.Text,
            fontFamily = RallyDisplayFont,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Games, teams, leagues, channels and streams.",
            color = RallyTvPalette.Muted,
            fontFamily = RallyBodyFont,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            singleLine = true,
            placeholder = {
                androidx.compose.material3.Text(
                    "Search Rally TV",
                    fontFamily = RallyBodyFont,
                    color = RallyTvPalette.Subtle
                )
            },
            textStyle = TextStyle(fontFamily = RallyBodyFont, fontSize = 17.sp),
            modifier = Modifier.fillMaxWidth().height(60.dp)
                .onFocusChanged { fieldFocused = it.isFocused }
                .focusRequester(searchFocus)
                .rallyTvFocus(fieldFocused),
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RallyTvPalette.Text,
                unfocusedTextColor = RallyTvPalette.Text,
                cursorColor = RallyTvPalette.Accent,
                focusedBorderColor = RallyTvPalette.Accent,
                unfocusedBorderColor = RallyTvPalette.Divider,
                focusedContainerColor = RallyTvPalette.FocusSurface,
                unfocusedContainerColor = RallyTvPalette.BackgroundSoft
            )
        )
        Spacer(Modifier.height(22.dp))
        RallyTvRule()
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            if (state.query.isBlank()) {
                item("prompt") {
                    SearchHint(if (state.isIndexReady) "Search across Rally TV." else "Preparing search…")
                }
            } else {
                if (state.isSearching) item("loading") { SearchHint("Searching…") }
                if (state.events.isNotEmpty()) {
                    item("events-title") { SearchTitle("GAMES") }
                    items(state.events, key = { "event:${it.id}" }) { event ->
                        SearchResultRow(event.name, listOfNotNull(event.league, event.gameStatusDetail?.takeIf { it.isNotBlank() }).joinToString(" · ")) { onEvent(event) }
                    }
                }
                if (state.favoriteTeams.isNotEmpty()) {
                    item("teams-title") { SearchTitle("MY RALLY") }
                    items(state.favoriteTeams, key = { "team:${it.league}:${it.id}" }) { team ->
                        SearchResultRow(team.name, "Following · ${team.league}") { onTeam(team) }
                    }
                }
                if (state.leagues.isNotEmpty()) {
                    item("leagues-title") { SearchTitle("LEAGUES") }
                    items(state.leagues, key = { "league:$it" }) { league ->
                        SearchResultRow(league, "League Center") { onLeague(league) }
                    }
                }
                if (state.channels.isNotEmpty()) {
                    item("channels-title") { SearchTitle("LIVE TV") }
                    items(state.channels, key = { "channel:${it.id}" }) { channel ->
                        ChannelSearchRow(channel) { onPlay(channel.id) }
                    }
                }
                if (playableStreams.isNotEmpty()) {
                    item("addons-title") { SearchTitle("STREAMS") }
                    items(playableStreams, key = { "stream:${it.streamUrl}" }) { stream ->
                        StreamSearchRow(stream) { onPlay(stream.streamUrl) }
                    }
                }
                if (!state.isSearching && !hasResults) {
                    item("empty") { SearchHint("No results found. Try a different search.") }
                }
            }
        }
    }
}

@Composable
private fun ChannelSearchRow(channel: IptvChannel, onClick: () -> Unit) {
    val now = channel.guide?.now?.title
    val next = channel.guide?.next?.title
    SearchResultRow(
        channel.name,
        listOfNotNull(now?.let { "Now · $it" }, next?.let { "Next · $it" }, channel.category.takeIf { now == null })
            .joinToString("   "),
        onClick
    )
}

@Composable
private fun StreamSearchRow(stream: StremioStreamOption, onClick: () -> Unit) {
    SearchResultRow(stream.title, listOfNotNull(stream.quality, stream.bitrate).joinToString(" · ").ifBlank { "Adaptive" }, onClick)
}

@Composable
private fun SearchTitle(title: String) {
    Text(
        title,
        color = RallyTvPalette.Muted,
        fontFamily = RallyBodyFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = 23.dp, bottom = 9.dp)
    )
}

@Composable
private fun SearchHint(text: String) {
    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 15.sp)
    }
}

@Composable
private fun SearchResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().height(58.dp)
                .onFocusChanged { focused = it.isFocused }
                .rallyTvFocus(focused)
                .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
                .clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = RallyTvPalette.Text,
                fontFamily = RallyBodyFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.5f)
            )
            Spacer(Modifier.width(20.dp))
            Text(
                subtitle,
                color = if (focused) RallyTvPalette.Text else RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "›",
                color = if (focused) RallyTvPalette.Accent else RallyTvPalette.Subtle,
                fontFamily = RallyBodyFont,
                fontSize = 20.sp
            )
        }
        RallyTvRule()
    }
}
