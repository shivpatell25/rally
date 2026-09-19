@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.R
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.repository.SportsRepository
import com.shiv.rally.presentation.home.formatTeamDisplayName
import com.shiv.rally.presentation.theme.AppleTvTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val SCORE_SAVER_IDLE_MS = 5 * 60 * 1_000L
private val saverShape = RoundedCornerShape(10.dp)
private val saverTime = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())

@HiltViewModel
class ScoreSaverViewModel @Inject constructor(
    private val sportsRepository: SportsRepository
) : ViewModel() {
    private val _events = MutableStateFlow<List<SportEvent>>(emptyList())
    val events: StateFlow<List<SportEvent>> = _events.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _events.value = runCatching { sportsRepository.getEventsSnapshot() }.getOrDefault(_events.value)
        }
    }
}

@Composable
fun RallyScoreSaverHost(
    enabled: Boolean,
    interactionTick: Long,
    currentRoute: String,
    onDismiss: () -> Unit,
    viewModel: ScoreSaverViewModel = hiltViewModel()
) {
    var visible by remember { mutableStateOf(false) }
    val events by viewModel.events.collectAsStateWithLifecycle()
    var clockTime by remember { mutableStateOf(java.time.Instant.now()) }
    val allowed = enabled && !currentRoute.startsWith("player/") && !currentRoute.startsWith("multiview")
    LaunchedEffect(enabled, interactionTick, currentRoute) {
        visible = false
        if (allowed) {
            delay(SCORE_SAVER_IDLE_MS)
            visible = true
        }
    }
    if (!visible || !allowed) return
    LaunchedEffect(visible) {
        while (isActive) {
            clockTime = java.time.Instant.now()
            viewModel.refresh()
            delay(30_000L)
        }
    }
    BackHandler {
        visible = false
        onDismiss()
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val featured = events.sortedWith(
        compareByDescending<SportEvent> { it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME }
            .thenBy { it.startTime }
    ).take(4)
    Box(
        Modifier.fillMaxSize().background(Color(0xFF030609)).focusRequester(focus).focusable()
            .onPreviewKeyEvent {
                visible = false
                onDismiss()
                true
            }
    ) {
        Image(painterResource(R.drawable.rally_ambient_background_v5), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .24f)
        Column(Modifier.fillMaxSize().padding(horizontal = 54.dp, vertical = 42.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.rally_wordmark_color_ui), "Rally", Modifier.width(126.dp).height(44.dp), contentScale = ContentScale.Fit)
                Text(saverTime.format(clockTime), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            }
            Column {
                Text("RIGHT NOW", color = AppleTvTheme.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    if (featured.isEmpty()) {
                        Text("Waiting for the next game update", color = AppleTvTheme.TextSecondary, fontSize = 18.sp)
                    } else featured.forEach { event -> ScoreSaverCard(event, Modifier.weight(1f)) }
                }
            }
            Text("Press any button to return", color = AppleTvTheme.TextTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ScoreSaverCard(event: SportEvent, modifier: Modifier) {
    val live = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
    Column(
        modifier.height(150.dp).background(Color(0xC7101822), saverShape).border(1.dp, AppleTvTheme.GlassBorder, saverShape).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                Box(Modifier.size(6.dp).background(AppleTvTheme.LiveRed, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(if (live) "LIVE" else saverTime.format(event.startTime), color = if (live) AppleTvTheme.LiveRed else AppleTvTheme.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(event.awayTeamBadge ?: event.awayTeam?.logoUrl, null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
            Text(if (live) "${event.scoreAway ?: "–"}  –  ${event.scoreHome ?: "–"}" else "VS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            AsyncImage(event.homeTeamBadge ?: event.homeTeam?.logoUrl, null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
        }
        Text("${formatTeamDisplayName(event.awayTeam?.name)} · ${formatTeamDisplayName(event.homeTeam?.name)}", color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
