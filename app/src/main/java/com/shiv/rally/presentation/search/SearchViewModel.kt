package com.shiv.rally.presentation.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.domain.repository.SportsRepository
import com.shiv.rally.domain.repository.StremioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val sportsRepository: SportsRepository,
    private val iptvRepository: IptvRepository,
    private val stremioRepository: StremioRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var events: List<SportEvent> = emptyList()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            events = try {
                sportsRepository.getEventsSnapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(isIndexReady = true)
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250L)
            val normalized = query.trim()
            if (normalized.isBlank()) {
                _uiState.value = SearchUiState(query = query, isIndexReady = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isSearching = true)
            val channelResults = async(Dispatchers.IO) {
                try {
                    iptvRepository.searchChannels(normalized, limit = 24)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val addonResults = async(Dispatchers.IO) {
                if (normalized.length < 3) return@async emptyList()
                try {
                    stremioRepository.searchStreams(normalized).take(20)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val local = withContext(Dispatchers.Default) {
                val eventResults = events.filter { event ->
                    listOf(event.name, event.league, event.sport, event.homeTeam?.name, event.awayTeam?.name)
                        .any { it?.contains(normalized, true) == true }
                }.take(20)
                val teamResults = preferencesManager.favoriteTeamProfiles.filter {
                    it.name.contains(normalized, true) || it.abbreviation.contains(normalized, true) || it.league.contains(normalized, true)
                }.take(12)
                val leagues = preferencesManager.sportsOrder.filter { it.contains(normalized, true) }.take(8)
                LocalResults(eventResults, teamResults, leagues)
            }
            val channels = channelResults.await()
            val channelsWithGuide = channels.take(8).map { channel ->
                async(Dispatchers.IO) {
                    val guide = try {
                        iptvRepository.getChannelGuide(channel.id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    channel.copy(guide = guide)
                }
            }.map { it.await() } + channels.drop(8)
            val addonStreams = addonResults.await()
            if (_uiState.value.query == query) {
                _uiState.value = SearchUiState(
                    query = query,
                    events = local.events,
                    favoriteTeams = local.teams,
                    leagues = local.leagues,
                    channels = channelsWithGuide,
                    addonStreams = addonStreams,
                    isIndexReady = true,
                    isSearching = false
                )
            }
        }
    }

    private data class LocalResults(
        val events: List<SportEvent>,
        val teams: List<FavoriteTeam>,
        val leagues: List<String>
    )
}

@Immutable
data class SearchUiState(
    val query: String = "",
    val events: List<SportEvent> = emptyList(),
    val favoriteTeams: List<FavoriteTeam> = emptyList(),
    val leagues: List<String> = emptyList(),
    val channels: List<IptvChannel> = emptyList(),
    val addonStreams: List<StremioStreamOption> = emptyList(),
    val isIndexReady: Boolean = false,
    val isSearching: Boolean = false
)
