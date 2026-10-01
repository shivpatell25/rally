package com.shiv.rally.presentation.watchlist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.matchesFavoriteTeams
import kotlinx.coroutines.CancellationException
import com.shiv.rally.domain.repository.SportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val sportsRepository: SportsRepository,
    private val preferences: PreferencesManager
) : ViewModel() {
    private val _state = MutableStateFlow<WatchlistUiState>(WatchlistUiState.Loading)
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val teams = preferences.favoriteTeamProfiles
                val snapshot = sportsRepository.getEventsSnapshot()
                val teamEvents = snapshot
                    .filter { it.matchesFavoriteTeams(teams) }
                    .distinctBy { it.id }
                    .sortedBy { it.startTime }
                val teamEventIds = teamEvents.mapTo(hashSetOf()) { it.id }
                val savedEvents = preferences.savedEventIds.mapNotNull { eventId ->
                    snapshot.firstOrNull { it.id == eventId } ?: sportsRepository.getEventById(eventId)
                }
                    .distinctBy { it.id }
                    .filterNot { it.id in teamEventIds }
                    .sortedBy { it.startTime }
                WatchlistUiState.Success(teams, teamEvents, savedEvents)
            }.onSuccess { _state.value = it }
                .onFailure { if (it is CancellationException) throw it; _state.value = WatchlistUiState.Error(it.message ?: "Watchlist is unavailable") }
        }
    }
}

sealed class WatchlistUiState {
    data object Loading : WatchlistUiState()
    @Immutable
    data class Success(
        val teams: List<FavoriteTeam>,
        val teamEvents: List<SportEvent>,
        val savedEvents: List<SportEvent>
    ) : WatchlistUiState()
    data class Error(val message: String) : WatchlistUiState()
}
