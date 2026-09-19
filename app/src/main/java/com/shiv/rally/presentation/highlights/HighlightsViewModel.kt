package com.shiv.rally.presentation.highlights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.HighlightClip
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.repository.SportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val sportsRepository: SportsRepository
) : ViewModel() {
    private val _state = MutableStateFlow<HighlightsUiState>(HighlightsUiState.Loading)
    val state: StateFlow<HighlightsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = HighlightsUiState.Loading
            runCatching {
                val events = sportsRepository.getRecentEvents()
                    .sortedWith(
                        compareBy<SportEvent> {
                            when (it.status) {
                                EventStatus.LIVE, EventStatus.HALFTIME -> 0
                                EventStatus.FINISHED -> 1
                                else -> 2
                            }
                        }.thenByDescending { it.startTime }
                    )
                    .take(18)
                val gate = Semaphore(3)
                events.map { event ->
                    async { gate.withPermit { runCatching { sportsRepository.getEventSummary(event) }.getOrDefault(event) } }
                }.awaitAll().flatMap { event ->
                    event.highlightClips.map { HighlightItem(event, it) }
                }.distinctBy { it.clip.id }
            }.onSuccess { clips ->
                _state.value = HighlightsUiState.Success(clips)
            }.onFailure { error ->
                _state.value = HighlightsUiState.Error(error.message ?: "Highlights are unavailable")
            }
        }
    }
}

@Immutable
data class HighlightItem(val event: SportEvent, val clip: HighlightClip)

sealed class HighlightsUiState {
    data object Loading : HighlightsUiState()
    @Immutable data class Success(val items: List<HighlightItem>) : HighlightsUiState()
    data class Error(val message: String) : HighlightsUiState()
}
