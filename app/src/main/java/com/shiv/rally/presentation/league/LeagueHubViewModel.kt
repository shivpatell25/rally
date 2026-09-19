package com.shiv.rally.presentation.league

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.domain.model.LeagueHub
import com.shiv.rally.domain.repository.IptvRepository
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
class LeagueHubViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sportsRepository: SportsRepository,
    private val iptvRepository: IptvRepository
) : ViewModel() {
    private val league = runCatching {
        URLDecoder.decode(savedStateHandle.get<String>("league").orEmpty().replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(savedStateHandle.get<String>("league").orEmpty())
    private val _uiState = MutableStateFlow<LeagueHubUiState>(LeagueHubUiState.Loading)
    val uiState: StateFlow<LeagueHubUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = runCatching {
                val hub = sportsRepository.getLeagueHub(league)
                val redZone = if (league.equals("NFL", true)) {
                    iptvRepository.getChannels().firstOrNull {
                        it.name.contains("nfl", true) && (it.name.contains("redzone", true) || it.name.contains("red zone", true))
                    }?.let { channel -> channel.copy(guide = iptvRepository.getChannelGuide(channel.id)) }
                } else null
                LeagueHubUiState.Success(hub, redZone)
            }.getOrElse { LeagueHubUiState.Error(it.message ?: "League data is unavailable") }
        }
    }
}

sealed class LeagueHubUiState {
    data object Loading : LeagueHubUiState()
    @Immutable data class Success(val hub: LeagueHub, val redZone: com.shiv.rally.domain.model.IptvChannel?) : LeagueHubUiState()
    data class Error(val message: String) : LeagueHubUiState()
}
