package com.shiv.rally.presentation.watchlist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.SportEvent
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
                val ids = teams.mapTo(hashSetOf()) { it.id }
                val events = sportsRepository.getEventsSnapshot().filter { it.homeTeam?.id in ids || it.awayTeam?.id in ids }
                WatchlistUiState.Success(teams, events.sortedBy { it.startTime })
            }.onSuccess { _state.value = it }.onFailure { _state.value = WatchlistUiState.Error(it.message ?: "Watchlist is unavailable") }
        }
    }
}

sealed class WatchlistUiState {
    data object Loading : WatchlistUiState()
    @Immutable data class Success(val teams: List<FavoriteTeam>, val events: List<SportEvent>) : WatchlistUiState()
    data class Error(val message: String) : WatchlistUiState()
}
