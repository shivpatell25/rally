package com.shiv.rally.presentation.team

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.TeamHub
import com.shiv.rally.domain.repository.SportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class TeamHubViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sportsRepository: SportsRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val teamId = decode(savedStateHandle.get<String>("teamId").orEmpty())
    private val league = decode(savedStateHandle.get<String>("league").orEmpty())
    private val _uiState = MutableStateFlow<TeamHubUiState>(TeamHubUiState.Loading)
    val uiState: StateFlow<TeamHubUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val favorite = preferencesManager.favoriteTeamProfiles.firstOrNull {
                it.id == teamId && it.league.equals(league, true)
            }
            if (favorite == null) {
                _uiState.value = TeamHubUiState.NotFavorite
                return@launch
            }
            _uiState.value = runCatching {
                TeamHubUiState.Success(sportsRepository.getTeamHub(favorite), preferencesManager.favoritePlayerIds)
            }
                .getOrElse { TeamHubUiState.Error(it.message ?: "Team data is unavailable") }
        }
    }

    fun removeFavorite() {
        val current = (_uiState.value as? TeamHubUiState.Success)?.hub?.team ?: return
        preferencesManager.toggleFavoriteTeam(current)
        _uiState.value = TeamHubUiState.NotFavorite
    }

    fun toggleFavoritePlayer(playerId: String) {
        val current = _uiState.value as? TeamHubUiState.Success ?: return
        preferencesManager.toggleFavoritePlayer(playerId)
        _uiState.value = current.copy(favoritePlayerIds = preferencesManager.favoritePlayerIds)
    }
}

private fun decode(value: String): String = runCatching {
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}.getOrDefault(value)

sealed class TeamHubUiState {
    data object Loading : TeamHubUiState()
    data object NotFavorite : TeamHubUiState()
    @Immutable data class Success(val hub: TeamHub, val favoritePlayerIds: Set<String> = emptySet()) : TeamHubUiState()
    data class Error(val message: String) : TeamHubUiState()
}
