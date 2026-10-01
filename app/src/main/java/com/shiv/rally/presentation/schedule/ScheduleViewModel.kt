package com.shiv.rally.presentation.schedule

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.repository.SportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val sportsRepository: SportsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var refreshVersion = 0L

    init {
        refresh()
    }

    /** Reload the feed window. A newer request always owns the displayed result. */
    fun refresh() {
        val requestVersion = ++refreshVersion
        refreshJob?.cancel()
        val existing = _uiState.value as? ScheduleUiState.Content
        if (existing != null) {
            _uiState.value = existing.copy(isRefreshing = true)
        } else {
            _uiState.value = ScheduleUiState.Loading
        }

        refreshJob = viewModelScope.launch {
            try {
                val events = sportsRepository.getRecentEvents()
                    .distinctBy(SportEvent::id)
                    .sortedWith(compareBy<SportEvent> { it.startTime }.thenBy { it.id })
                val availableDates = events
                    .asSequence()
                    .map { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }
                    .distinct()
                    .sorted()
                    .toList()
                if (requestVersion != refreshVersion) return@launch
                val current = _uiState.value as? ScheduleUiState.Content ?: existing
                val selectedDate = current?.selectedDate?.takeIf(availableDates::contains)
                    ?: availableDates.firstOrNull()
                _uiState.value = ScheduleUiState.Content(
                    events = events,
                    availableDates = availableDates,
                    selectedDate = selectedDate,
                    selectedLeague = current?.selectedLeague?.takeIf { league ->
                        selectedDate != null && events.any {
                            it.league == league &&
                                it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() == selectedDate
                        }
                    },
                    statusFilter = current?.statusFilter ?: ScheduleStatusFilter.ALL
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (requestVersion == refreshVersion) {
                    _uiState.value = ScheduleUiState.Error(
                        message = error.message?.takeIf(String::isNotBlank)
                            ?: "The schedule could not be loaded."
                    )
                }
            }
        }
    }

    fun selectDate(date: LocalDate) = updateContent { current ->
        if (date !in current.availableDates) current else {
            val dateEvents = current.eventsFor(date)
            current.copy(
                selectedDate = date,
                selectedLeague = current.selectedLeague?.takeIf { league -> dateEvents.any { it.league == league } }
            )
        }
    }

    fun selectLeague(league: String?) = updateContent { current ->
        val validLeague = league?.takeIf { target ->
            current.selectedDate?.let(current::eventsFor)?.any { it.league == target } == true
        }
        current.copy(selectedLeague = validLeague)
    }

    fun selectStatus(filter: ScheduleStatusFilter) = updateContent { current ->
        current.copy(statusFilter = filter)
    }

    fun clearFilters() = updateContent { current ->
        current.copy(selectedLeague = null, statusFilter = ScheduleStatusFilter.ALL)
    }

    private inline fun updateContent(transform: (ScheduleUiState.Content) -> ScheduleUiState.Content) {
        val current = _uiState.value as? ScheduleUiState.Content ?: return
        _uiState.value = transform(current)
    }
}

@Immutable
sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState

    @Immutable
    data class Error(val message: String) : ScheduleUiState

    @Immutable
    data class Content(
        val events: List<SportEvent>,
        val availableDates: List<LocalDate>,
        val selectedDate: LocalDate?,
        val selectedLeague: String? = null,
        val statusFilter: ScheduleStatusFilter = ScheduleStatusFilter.ALL,
        val isRefreshing: Boolean = false
    ) : ScheduleUiState {
        fun eventsFor(date: LocalDate): List<SportEvent> = events.filter {
            it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() == date
        }
    }
}

enum class ScheduleStatusFilter(val label: String) {
    ALL("All"),
    LIVE("Live"),
    UPCOMING("Upcoming"),
    FINAL("Final");

    fun matches(event: SportEvent): Boolean = when (this) {
        ALL -> true
        LIVE -> event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
        UPCOMING -> event.status == EventStatus.NOT_STARTED || event.status == EventStatus.DELAYED
        FINAL -> event.status == EventStatus.FINISHED
    }
}
