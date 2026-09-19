@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.iptv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.R
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.parseQualityFromChannelName
import com.shiv.rally.presentation.theme.AppleTvTheme
import com.shiv.rally.presentation.common.rallyFocusScale
import kotlinx.coroutines.delay

private val navShape = RoundedCornerShape(8.dp)
private val channelShape = RoundedCornerShape(10.dp)
private val pillShape = RoundedCornerShape(6.dp)

@Composable
fun IptvBrowserScreen(
    viewModel: IptvBrowserViewModel = hiltViewModel(),
    onChannelClick: (String) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    when (val current = state) {
        IptvBrowserUiState.Loading -> IptvLoadingState()
        is IptvBrowserUiState.Error -> IptvErrorState(current.message, viewModel::loadChannels, onBack)
        is IptvBrowserUiState.Success -> IptvBrowserContent(
            state = current,
            onSelectCategory = viewModel::selectCategory,
            onSearchQuery = viewModel::setSearchQuery,
            onRetry = viewModel::retryChannels,
            onChannelVisible = viewModel::loadGuide,
            onChannelClick = onChannelClick,
            onBack = onBack,
            initialFocusRequester = initialFocusRequester
        )
    }
}

@Composable
private fun IptvLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.rally_wordmark_white_ui), "Rally", Modifier.height(48.dp))
            Spacer(Modifier.height(20.dp))
            Text("Loading your channels", color = AppleTvTheme.TextSecondary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun IptvErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Live TV is unavailable", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = AppleTvTheme.TextSecondary, fontSize = 15.sp, maxLines = 2)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrowserButton("Try Again", onRetry, true)
                BrowserButton("Back", onBack)
            }
        }
    }
}

@Composable
private fun IptvBrowserContent(
    state: IptvBrowserUiState.Success,
    onSelectCategory: (String) -> Unit,
    onSearchQuery: (String) -> Unit,
    onRetry: () -> Unit,
    onChannelVisible: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onBack: () -> Unit,
    initialFocusRequester: FocusRequester?
) {
    val fallbackFocus = remember { FocusRequester() }
    val firstCategoryFocus = initialFocusRequester ?: fallbackFocus
    LaunchedEffect(state.categories) {
        delay(120)
        runCatching { firstCategoryFocus.requestFocus() }
    }

    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(270.dp).fillMaxHeight().background(Color(0xB80A101B)).border(1.dp, Color(0x385A7894)).padding(start = 32.dp, end = 22.dp, top = 22.dp, bottom = 24.dp)
        ) {
            Text("LIVE TV", color = AppleTvTheme.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(10.dp))
            TvLazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(state.categories, key = { it }) { category ->
                    val selected = category == state.selectedCategory
                    var focused by remember(category) { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (category == state.categories.firstOrNull()) Modifier.focusRequester(firstCategoryFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .clip(navShape)
                            .background(if (focused) AppleTvTheme.SurfaceFocused else if (selected) Color(0x24202834) else Color.Transparent)
                            .border(1.dp, if (focused) Color(0x3DF5F7FA) else if (selected) AppleTvTheme.Divider else Color.Transparent, navShape)
                            .clickable { onSelectCategory(category) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (category.contains("sport", true)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (selected) AppleTvTheme.AccentRed else Color(0x73FFFFFF)))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(category, color = if (focused || selected) AppleTvTheme.RallyCyan else Color(0xA6FFFFFF), fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            BrowserButton("‹ Sports", onBack)
        }

        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 34.dp, vertical = 26.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(state.selectedCategory, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
                    Spacer(Modifier.height(3.dp))
                    Text("${state.channels.size} channels · ${state.totalChannelCount} total", color = AppleTvTheme.TextSecondary, fontSize = 13.sp)
                }
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQuery,
                    singleLine = true,
                    label = { androidx.compose.material3.Text("Search channels") },
                    modifier = Modifier.width(300.dp).height(58.dp),
                    shape = navShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AppleTvTheme.RallyCyan,
                        unfocusedBorderColor = Color(0x2EFFFFFF),
                        focusedContainerColor = AppleTvTheme.Graphite,
                        unfocusedContainerColor = AppleTvTheme.Slate,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = AppleTvTheme.TextSecondary
                    )
                )
            }
            Spacer(Modifier.height(22.dp))

            if (state.channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val libraryIsEmpty = state.totalChannelCount == 0
                        Text(
                            when {
                                libraryIsEmpty -> "No channels were returned"
                                state.searchQuery.isBlank() -> "No channels in this category"
                                else -> "No results for “${state.searchQuery}”"
                            },
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (libraryIsEmpty) "Retry, or check your portal details in Settings."
                            else "Try another category or search term.",
                            color = AppleTvTheme.TextSecondary,
                            fontSize = 14.sp
                        )
                        if (libraryIsEmpty) {
                            Spacer(Modifier.height(18.dp))
                            BrowserButton("Try Again", onRetry, primary = true)
                        }
                    }
                }
            } else {
                TvLazyVerticalGrid(
                    columns = TvGridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.channels, key = { it.id }) { channel ->
                        IptvChannelCard(channel, onVisible = { onChannelVisible(channel.id) }) { onChannelClick(channel.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IptvChannelCard(channel: IptvChannel, onVisible: () -> Unit, onClick: () -> Unit) {
    val quality = remember(channel.name) { parseQualityFromChannelName(channel.name) }
    var focused by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id) { onVisible() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused, AppleTvTheme.CardFocusScale)
            .clip(channelShape)
            .background(if (focused) AppleTvTheme.SurfaceFocused else AppleTvTheme.SurfaceRaised)
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else AppleTvTheme.GlassBorder, channelShape)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(AppleTvTheme.GlassSurfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(channel.logoUrl, null, Modifier.size(50.dp), contentScale = ContentScale.Fit)
                } else {
                    Text(channel.number.ifBlank { channel.name.take(2).uppercase() }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                channel.guide?.now?.title?.let { now ->
                    Text("Now · $now", color = if (focused) Color.White else Color(0xFFE5E5EA), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.guide?.next?.title?.let { "Next · $it" } ?: channel.category.ifBlank { "Live TV" }, color = AppleTvTheme.TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    quality.resolution?.let { BrowserBadge(it) }
                }
            }
        }
    }
}

@Composable
private fun BrowserBadge(label: String) {
    Box(Modifier.clip(pillShape).background(Color(0x2EFFFFFF)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BrowserButton(label: String, onClick: () -> Unit, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused, AppleTvTheme.ButtonFocusScale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) AppleTvTheme.OffWhite else if (focused) AppleTvTheme.SurfaceFocused else AppleTvTheme.SurfaceRaised)
            .border(if (focused) 2.dp else 1.dp, if (focused) AppleTvTheme.RallyCyan else if (primary) Color(0xB8FFFFFF) else AppleTvTheme.GlassBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (primary) AppleTvTheme.DeepNavy else AppleTvTheme.OffWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
