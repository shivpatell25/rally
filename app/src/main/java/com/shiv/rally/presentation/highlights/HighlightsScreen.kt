@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.highlights

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.presentation.common.RallyPagedRow
import com.shiv.rally.presentation.common.rallyFocusScale
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.theme.AppleTvTheme
import kotlinx.coroutines.delay

private val highlightShape = RoundedCornerShape(10.dp)

@Composable
fun HighlightsScreen(
    viewModel: HighlightsViewModel = hiltViewModel(),
    onPlay: (String) -> Unit,
    onEvent: (com.shiv.rally.domain.model.SportEvent) -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        HighlightsUiState.Loading -> HighlightMessage("Finding today’s highlights…")
        is HighlightsUiState.Error -> HighlightMessage(current.message, viewModel::load)
        is HighlightsUiState.Success -> HighlightsContent(current.items, onPlay, onEvent, initialFocusRequester)
    }
}

@Composable
private fun HighlightsContent(items: List<HighlightItem>, onPlay: (String) -> Unit, onEvent: (com.shiv.rally.domain.model.SportEvent) -> Unit, initialFocusRequester: FocusRequester?) {
    val fallbackFocus = remember { FocusRequester() }
    val firstFocus = initialFocusRequester ?: fallbackFocus
    LaunchedEffect(items.size) { delay(120); runCatching { firstFocus.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 12.dp, bottom = 18.dp)) {
        Column(Modifier.height(74.dp)) {
            Text("HIGHLIGHTS", color = AppleTvTheme.RallyCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("The biggest moments, right now.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = (-.6).sp)
            Text("Event-linked clips from supported leagues.", color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(16.dp))
        if (items.isEmpty()) {
            HighlightEmptyCard(Modifier.focusRequester(firstFocus))
        } else {
            RallyPagedRow(
                items = items,
                key = { it.clip.id },
                firstFocusRequester = firstFocus,
                spacing = 12.dp
            ) { item, modifier, width ->
                HighlightCard(
                    item = item,
                    onClick = { item.clip.streamUrl?.let(onPlay) ?: onEvent(item.event) },
                    modifier = modifier,
                    width = width
                )
            }
        }
    }
}

@Composable
private fun HighlightCard(item: HighlightItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp) {
    var focused by remember(item.clip.id) { mutableStateOf(false) }
    Box(
        modifier.width(width).height(235.dp).onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused)
            .clip(highlightShape).background(Color(0xC20A101B)).border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else Color(0x385A7894), highlightShape).clickable(onClick = onClick)
    ) {
        AsyncImage(item.clip.thumbnailUrl, item.clip.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x12000000), .48f to Color(0x4D05080F), 1f to Color(0xF205080F))))
        Column(Modifier.align(Alignment.BottomStart).padding(15.dp)) {
            Text(formatLeagueDisplayName(item.event.league).uppercase(), color = AppleTvTheme.RallyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(item.clip.title, color = Color.White, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.event.name, color = AppleTvTheme.TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HighlightEmptyCard(modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(190.dp).onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused)
            .clip(highlightShape)
            .background(if (focused) Color(0xD6172437) else Color(0xA80A101B))
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else Color(0x385A7894), highlightShape)
            .clickable(onClick = {})
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("RECENT COVERAGE", color = AppleTvTheme.RallyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(7.dp))
            Text("No league clips have been published yet.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("This page fills automatically as supported leagues release highlights.", color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HighlightMessage(message: String, retry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = AppleTvTheme.TextSecondary, fontSize = 16.sp)
            retry?.let { action ->
                Spacer(Modifier.height(14.dp))
                var focused by remember { mutableStateOf(false) }
                Box(Modifier.onFocusChanged { focused = it.isFocused }.clip(highlightShape).background(if (focused) AppleTvTheme.OffWhite else AppleTvTheme.SurfaceRaised).border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, highlightShape).clickable(onClick = action).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text("Try Again", color = if (focused) AppleTvTheme.DeepNavy else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
