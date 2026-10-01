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
import androidx.compose.foundation.focusable
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
import com.shiv.rally.presentation.common.RallyTvActionButton
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
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
        is IptvBrowserUiState.Error -> IptvErrorState(current.message, viewModel::loadChannels, onBack, initialFocusRequester)
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
            Text("Live", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("Loading channels", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp)
        }
    }
}

@Composable
private fun IptvErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit, initialFocusRequester: FocusRequester?) {
    val firstFocus = initialFocusRequester ?: remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { firstFocus.requestFocus() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Live TV is unavailable", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp, maxLines = 2)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RallyTvActionButton("Try Again", onRetry, primary = true, focusRequester = firstFocus)
                RallyTvActionButton("Back", onBack)
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
    val categoryFocus = initialFocusRequester ?: fallbackFocus
    val channelFocus = remember { FocusRequester() }
    LaunchedEffect(state.categories, state.channels) {
        delay(120)
        val target = if (state.categories.isNotEmpty()) categoryFocus else channelFocus
        runCatching { target.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 66.dp, vertical = 18.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Live", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("${state.channels.size} channels · ${state.totalChannelCount} total", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp)
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQuery,
                singleLine = true,
                label = { androidx.compose.material3.Text("Search channels") },
                modifier = Modifier.width(280.dp).height(58.dp),
                shape = RoundedCornerShape(4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RallyTvPalette.Text,
                    unfocusedTextColor = RallyTvPalette.Text,
                    focusedBorderColor = RallyTvPalette.Accent,
                    unfocusedBorderColor = RallyTvPalette.Divider,
                    focusedContainerColor = RallyTvPalette.BackgroundSoft,
                    unfocusedContainerColor = RallyTvPalette.BackgroundSoft,
                    focusedLabelColor = RallyTvPalette.Text,
                    unfocusedLabelColor = RallyTvPalette.Muted
                )
            )
        }
        Spacer(Modifier.height(15.dp))
        androidx.tv.foundation.lazy.list.TvLazyRow(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 3.dp)
        ) {
            items(state.categories, key = { it }) { category ->
                IptvCategoryItem(
                    label = category,
                    selected = category == state.selectedCategory,
                    modifier = if (category == state.categories.firstOrNull()) Modifier.focusRequester(categoryFocus) else Modifier,
                    onClick = { onSelectCategory(category) }
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        RallyTvRule()
        Spacer(Modifier.height(8.dp))

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
                        color = RallyTvPalette.Text,
                        fontFamily = RallyDisplayFont,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (libraryIsEmpty) "Check your provider details in Settings, then retry."
                        else "Try another category or search term.",
                        color = RallyTvPalette.Muted,
                        fontFamily = RallyBodyFont,
                        fontSize = 13.sp
                    )
                    if (libraryIsEmpty) {
                        Spacer(Modifier.height(16.dp))
                        RallyTvActionButton("Try Again", onRetry, primary = true, focusRequester = channelFocus)
                    }
                }
            }
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(state.channels, key = { it.id }) { channel ->
                    IptvChannelCard(
                        channel = channel,
                        onVisible = { onChannelVisible(channel.id) },
                        modifier = if (channel == state.channels.firstOrNull()) Modifier.focusRequester(channelFocus) else Modifier
                    ) { onChannelClick(channel.id) }
                }
            }
        }
    }
}

@Composable
private fun IptvCategoryItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember(label) { mutableStateOf(false) }
    Column(
        modifier.onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 11.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = if (selected || focused) RallyTvPalette.Text else RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) RallyTvPalette.Accent else Color.Transparent))
    }
}

@Composable
private fun IptvChannelCard(
    channel: IptvChannel,
    onVisible: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val quality = remember(channel.name) { parseQualityFromChannelName(channel.name) }
    var focused by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id) { onVisible() }
    Row(
        modifier.fillMaxWidth().height(94.dp)
            .onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(channel.logoUrl, null, Modifier.size(42.dp), contentScale = ContentScale.Fit)
            } else {
                Text(channel.number.ifBlank { channel.name.take(2).uppercase() }, color = RallyTvPalette.Muted, fontFamily = RallyDisplayFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                quality.resolution?.let { Text(it, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
            }
            channel.guide?.now?.title?.let { now ->
                Text("NOW · $now", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                channel.guide?.next?.title?.let { "NEXT · $it" } ?: channel.category.ifBlank { "Live TV" },
                color = RallyTvPalette.Subtle,
                fontFamily = RallyBodyFont,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
