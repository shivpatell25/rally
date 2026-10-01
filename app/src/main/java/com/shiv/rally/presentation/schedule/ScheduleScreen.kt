@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.common.RallyTvActionButton
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import com.shiv.rally.presentation.theme.RallyLayout
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val scheduleDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val scheduleHeadingFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val scheduleTimeFormatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
private val scheduleControlShape = RoundedCornerShape(4.dp)

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    onEventClick: (SportEvent) -> Unit,
    initialFocusRequester: FocusRequester? = null,
    initialStatusFilter: ScheduleStatusFilter = ScheduleStatusFilter.ALL
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, initialStatusFilter, state is ScheduleUiState.Content) {
        if (state is ScheduleUiState.Content && initialStatusFilter != ScheduleStatusFilter.ALL) {
            viewModel.selectStatus(initialStatusFilter)
        }
    }

    when (val current = state) {
        ScheduleUiState.Loading -> ScheduleLoadingState()
        is ScheduleUiState.Error -> ScheduleErrorState(
            message = current.message,
            onRetry = viewModel::refresh,
            initialFocusRequester = initialFocusRequester
        )
        is ScheduleUiState.Content -> ScheduleContent(
            state = current,
            onSelectDate = viewModel::selectDate,
            onSelectLeague = viewModel::selectLeague,
            onSelectStatus = viewModel::selectStatus,
            onClearFilters = viewModel::clearFilters,
            onRefresh = viewModel::refresh,
            onEventClick = onEventClick,
            initialFocusRequester = initialFocusRequester
        )
    }
}

@Composable
private fun ScheduleContent(
    state: ScheduleUiState.Content,
    onSelectDate: (LocalDate) -> Unit,
    onSelectLeague: (String?) -> Unit,
    onSelectStatus: (ScheduleStatusFilter) -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    onEventClick: (SportEvent) -> Unit,
    initialFocusRequester: FocusRequester?
) {
    val fallbackFocus = remember { FocusRequester() }
    val firstFocus = initialFocusRequester ?: fallbackFocus
    val selectedDateEvents = remember(state.events, state.selectedDate) {
        state.selectedDate?.let(state::eventsFor).orEmpty()
    }
    val leagues = remember(selectedDateEvents) {
        selectedDateEvents.asSequence()
            .map(SportEvent::league)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .toList()
    }
    val visibleEvents = remember(selectedDateEvents, state.selectedLeague, state.statusFilter) {
        selectedDateEvents.asSequence()
            .filter { state.selectedLeague == null || it.league == state.selectedLeague }
            .filter(state.statusFilter::matches)
            .sortedWith(compareBy<SportEvent> { statusPriority(it) }.thenBy { it.startTime }.thenBy { it.name })
            .toList()
    }

    LaunchedEffect(firstFocus, state.events, state.availableDates) {
        delay(80)
        repeat(3) {
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(80)
        }
    }

    Box(Modifier.fillMaxSize()) {
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = RallyLayout.SafeHorizontal,
                end = RallyLayout.SafeHorizontal,
                top = 22.dp,
                bottom = 54.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "schedule-title") {
                ScheduleTitle(
                    eventCount = state.events.size,
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh
                )
            }

            if (state.availableDates.isEmpty()) {
                item(key = "schedule-empty-window") {
                    ScheduleMessage(
                        title = "No games in the current schedule window",
                        detail = "The sports feed has not returned any available dates yet.",
                        actionLabel = "Refresh schedule",
                        onAction = onRefresh,
                        actionFocusRequester = firstFocus
                    )
                }
            } else {
                item(key = "schedule-controls") {
                    ScheduleFilters(
                        state = state,
                        leagues = leagues,
                        onSelectDate = onSelectDate,
                        onSelectLeague = onSelectLeague,
                        onSelectStatus = onSelectStatus,
                        firstFocus = firstFocus
                    )
                }
                item(key = "schedule-results-heading") {
                    ScheduleResultsHeading(
                        selectedDate = state.selectedDate,
                        eventCount = visibleEvents.size,
                        showClearFilters = state.selectedLeague != null || state.statusFilter != ScheduleStatusFilter.ALL,
                        onClearFilters = onClearFilters
                    )
                }
                if (visibleEvents.isEmpty()) {
                    item(key = "schedule-empty-filter") {
                        ScheduleMessage(
                            title = "No games match these filters",
                            detail = "Try another league or status to see the games returned for this date.",
                            actionLabel = "Show all games",
                            onAction = onClearFilters
                        )
                    }
                } else {
                    items(visibleEvents, key = SportEvent::id) { event ->
                        ScheduleMatchupRow(event = event, onClick = { onEventClick(event) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleTitle(eventCount: Int, isRefreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "SCHEDULE",
                color = RallyTvPalette.Text,
                fontFamily = RallyDisplayFont,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "$eventCount games in the current feed window",
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 13.sp
            )
        }
        RallyTvActionButton(
            label = if (isRefreshing) "Refreshing…" else "Refresh",
            onClick = onRefresh,
            enabled = !isRefreshing
        )
    }
}

@Composable
private fun ScheduleFilters(
    state: ScheduleUiState.Content,
    leagues: List<String>,
    onSelectDate: (LocalDate) -> Unit,
    onSelectLeague: (String?) -> Unit,
    onSelectStatus: (ScheduleStatusFilter) -> Unit,
    firstFocus: FocusRequester
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ScheduleControlRow(label = "DATE") {
            items(state.availableDates, key = LocalDate::toString) { date ->
                ScheduleControl(
                    label = scheduleDateLabel(date),
                    selected = date == state.selectedDate,
                    onClick = { onSelectDate(date) },
                    modifier = if (date == state.selectedDate) Modifier.focusRequester(firstFocus) else Modifier
                )
            }
        }
        ScheduleControlRow(label = "LEAGUE") {
            item(key = "all-leagues") {
                ScheduleControl(
                    label = "All leagues",
                    selected = state.selectedLeague == null,
                    onClick = { onSelectLeague(null) }
                )
            }
            items(leagues, key = { it }) { league ->
                ScheduleControl(
                    label = league,
                    selected = league == state.selectedLeague,
                    onClick = { onSelectLeague(league) }
                )
            }
        }
        ScheduleControlRow(label = "STATUS") {
            items(ScheduleStatusFilter.entries, key = ScheduleStatusFilter::name) { filter ->
                ScheduleControl(
                    label = filter.label,
                    selected = filter == state.statusFilter,
                    onClick = { onSelectStatus(filter) }
                )
            }
        }
    }
}

@Composable
private fun ScheduleControlRow(
    label: String,
    controls: androidx.tv.foundation.lazy.list.TvLazyListScope.() -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(74.dp),
            color = RallyTvPalette.Subtle,
            fontFamily = RallyBodyFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
        TvLazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            content = controls
        )
    }
}

@Composable
private fun ScheduleControl(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember(label) { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .clip(scheduleControlShape)
            .background(
                when {
                    selected -> RallyTvPalette.Accent
                    focused -> RallyTvPalette.FocusSurface
                    else -> RallyTvPalette.BackgroundSoft
                }
            )
            .border(
                width = if (focused || selected) 1.dp else 0.dp,
                color = if (selected) RallyTvPalette.Accent else RallyTvPalette.Divider,
                shape = scheduleControlShape
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) RallyTvPalette.Background else RallyTvPalette.Text,
            fontFamily = RallyBodyFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScheduleResultsHeading(
    selectedDate: LocalDate?,
    eventCount: Int,
    showClearFilters: Boolean,
    onClearFilters: () -> Unit
) {
    Column {
        RallyTvRule()
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    selectedDate?.let(scheduleHeadingFormatter::format)?.uppercase().orEmpty(),
                    color = RallyTvPalette.Text,
                    fontFamily = RallyDisplayFont,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.15.sp
                )
                Text(
                    "Local time · $eventCount game${if (eventCount == 1) "" else "s"}",
                    color = RallyTvPalette.Muted,
                    fontFamily = RallyBodyFont,
                    fontSize = 12.sp
                )
            }
            if (showClearFilters) {
                RallyTvActionButton(
                    label = "Clear filters",
                    onClick = onClearFilters
                )
            }
        }
    }
}

@Composable
private fun ScheduleMatchupRow(event: SportEvent, onClick: () -> Unit) {
    var focused by remember(event.id) { mutableStateOf(false) }
    val statusColor = when (event.status) {
        EventStatus.LIVE, EventStatus.HALFTIME -> RallyTvPalette.Live
        EventStatus.FINISHED -> RallyTvPalette.Muted
        EventStatus.CANCELED -> RallyTvPalette.Subtle
        else -> RallyTvPalette.Accent
    }

    Column(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .clip(scheduleControlShape)
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(122.dp)) {
                Text(
                    event.scheduleStatusLabel(),
                    color = statusColor,
                    fontFamily = RallyBodyFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    event.localTimeLabel(),
                    color = RallyTvPalette.Muted,
                    fontFamily = RallyBodyFont,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ScheduleTeamLine(
                    logo = event.awayTeam?.logoUrl ?: event.awayTeamBadge,
                    fallback = event.awayTeam?.abbreviation,
                    name = event.awayTeam?.name ?: event.name.substringBefore(" at ")
                )
                ScheduleTeamLine(
                    logo = event.homeTeam?.logoUrl ?: event.homeTeamBadge,
                    fallback = event.homeTeam?.abbreviation,
                    name = event.homeTeam?.name ?: event.name.substringAfter(" at ", "Home")
                )
            }
            Column(
                modifier = Modifier.width(52.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ScheduleScore(event.scoreAway?.takeIf { event.status !in setOf(EventStatus.NOT_STARTED, EventStatus.CANCELED) })
                ScheduleScore(event.scoreHome?.takeIf { event.status !in setOf(EventStatus.NOT_STARTED, EventStatus.CANCELED) })
            }
            Column(
                modifier = Modifier.width(116.dp).padding(start = 18.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    event.league.uppercase(),
                    color = RallyTvPalette.Muted,
                    fontFamily = RallyBodyFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                event.venue?.takeIf(String::isNotBlank)?.let { venue ->
                    Text(
                        venue,
                        color = RallyTvPalette.Subtle,
                        fontFamily = RallyBodyFont,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        RallyTvRule()
    }
}

@Composable
private fun ScheduleTeamLine(logo: String?, fallback: String?, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ScheduleTeamMark(logo = logo, fallback = fallback)
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            color = RallyTvPalette.Text,
            fontFamily = RallyDisplayFont,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScheduleScore(score: Int?) {
    Text(
        score?.toString() ?: "–",
        color = RallyTvPalette.Text,
        fontFamily = RallyDisplayFont,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

@Composable
private fun ScheduleTeamMark(logo: String?, fallback: String?) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (logo.isNullOrBlank()) {
            Text(
                fallback?.take(3) ?: "TBD",
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            AsyncImage(
                model = logo,
                contentDescription = fallback,
                modifier = Modifier.size(26.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun ScheduleMessage(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionFocusRequester: FocusRequester? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = RallyTvPalette.Text,
            fontFamily = RallyDisplayFont,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            detail,
            color = RallyTvPalette.Muted,
            fontFamily = RallyBodyFont,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))
        RallyTvActionButton(
            label = actionLabel,
            onClick = onAction,
            primary = true,
            focusRequester = actionFocusRequester
        )
    }
}

@Composable
private fun ScheduleLoadingState() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Loading schedule",
                color = RallyTvPalette.Text,
                fontFamily = RallyDisplayFont,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "Getting the latest matchups",
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ScheduleErrorState(
    message: String,
    onRetry: () -> Unit,
    initialFocusRequester: FocusRequester?
) {
    val fallbackFocus = remember { FocusRequester() }
    val retryFocus = initialFocusRequester ?: fallbackFocus
    LaunchedEffect(retryFocus) {
        delay(80)
        runCatching { retryFocus.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(540.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Schedule unavailable",
                color = RallyTvPalette.Text,
                fontFamily = RallyDisplayFont,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))
            RallyTvActionButton(
                label = "Try again",
                onClick = onRetry,
                primary = true,
                focusRequester = retryFocus
            )
        }
    }
}

private fun scheduleDateLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today · ${scheduleDateFormatter.format(date)}"
    LocalDate.now().plusDays(1) -> "Tomorrow · ${scheduleDateFormatter.format(date)}"
    LocalDate.now().minusDays(1) -> "Yesterday · ${scheduleDateFormatter.format(date)}"
    else -> scheduleDateFormatter.format(date)
}

private fun SportEvent.localTimeLabel(): String = when (status) {
    EventStatus.FINISHED -> "Started ${scheduleTimeFormatter.format(startTime)}"
    EventStatus.CANCELED -> "Canceled · ${scheduleTimeFormatter.format(startTime)}"
    else -> scheduleTimeFormatter.format(startTime)
}

private fun SportEvent.scheduleStatusLabel(): String = when (status) {
    EventStatus.LIVE -> "● LIVE"
    EventStatus.HALFTIME -> "● HALFTIME"
    EventStatus.FINISHED -> "FINAL"
    EventStatus.DELAYED -> "DELAYED"
    EventStatus.CANCELED -> "CANCELED"
    EventStatus.NOT_STARTED -> "UPCOMING"
}

private fun statusPriority(event: SportEvent): Int = when (event.status) {
    EventStatus.LIVE, EventStatus.HALFTIME -> 0
    EventStatus.NOT_STARTED, EventStatus.DELAYED -> 1
    EventStatus.FINISHED -> 2
    EventStatus.CANCELED -> 3
}
