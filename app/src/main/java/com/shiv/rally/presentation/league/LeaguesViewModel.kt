package com.shiv.rally.presentation.league

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.data.local.PreferencesManager
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
class LeaguesViewModel @Inject constructor(
    private val sportsRepository: SportsRepository,
    private val preferences: PreferencesManager
) : ViewModel() {
    private val _state = MutableStateFlow<LeaguesUiState>(LeaguesUiState.Loading)
    val state: StateFlow<LeaguesUiState> = _state.asStateFlow()
    init { load() }
    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val events = sportsRepository.getEventsSnapshot()
                val byLeague = events.groupBy { it.league }
                val enabled = preferences.enabledLeagues
                val ordered = preferences.sportsOrder.filter { enabled.isEmpty() || it in enabled }
                val names = (ordered + byLeague.keys.filterNot { it in ordered }).distinct()
                names.map { LeagueDirectoryItem(it, byLeague[it].orEmpty().sortedBy(SportEvent::startTime)) }
            }.onSuccess { _state.value = LeaguesUiState.Success(it) }
                .onFailure { _state.value = LeaguesUiState.Error(it.message ?: "Leagues are unavailable") }
        }
    }
}

@Immutable data class LeagueDirectoryItem(val league: String, val events: List<SportEvent>)
sealed class LeaguesUiState {
    data object Loading : LeaguesUiState()
    @Immutable data class Success(val leagues: List<LeagueDirectoryItem>) : LeaguesUiState()
    data class Error(val message: String) : LeaguesUiState()
}
