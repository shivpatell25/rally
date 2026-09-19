package com.shiv.rally.presentation.event

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.MatchResult
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.Team
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.domain.repository.SportsRepository
import com.shiv.rally.domain.repository.StremioRepository
import com.shiv.rally.domain.usecase.SelectBestStreamUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sportsRepository: SportsRepository,
    private val iptvRepository: IptvRepository,
    private val stremioRepository: StremioRepository,
    private val selectBestStream: SelectBestStreamUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val eventId: String = checkNotNull(savedStateHandle["eventId"])

    private val _uiState = MutableStateFlow<EventUiState>(EventUiState.Loading)
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    init {
        loadEventDetails()
    }

    private fun loadEventDetails() {
        viewModelScope.launch {
            try {
                val event = sportsRepository.getEventById(eventId)

                if (event == null) {
                    _uiState.value = EventUiState.Error("Event not found")
                    return@launch
                }

                // 1. Immediate broadcast stations extraction from event stats
                val tvBroadcast = event.liveStats["TV Broadcast"]
                val initialStations = if (!tvBroadcast.isNullOrEmpty()) {
                    tvBroadcast.split(",", "/", "&", "+").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }

                // 2. Instant initial Success state (<1ms) so the Event Details screen renders with zero lag!
                _uiState.value = EventUiState.Success(
                    event = event,
                    bestMatch = null,
                    alternativeMatches = emptyList(),
                    relevantChannels = emptyList(),
                    broadcastStations = initialStations,
                    stremioStreams = emptyList(),
                    streamCandidates = emptyList(),
                    isLoadingStreams = true,
                    primaryStreamTarget = null,
                    favoriteTeamIds = favoriteIdsFor(event)
                )

                // 3. Match IPTV channels, query Stremio addons, and fetch rich stats summary concurrently!
                val channelsDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                    try { iptvRepository.getChannels() } catch (e: Exception) { emptyList() }
                }
                val stremioDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                    try { stremioRepository.getStreamsForEvent(event) } catch (e: Exception) { emptyList() }
                }
                val summaryDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                    try { sportsRepository.getEventSummary(event) } catch (e: Exception) { event }
                }

                // Publish addon streams as soon as they are ready. IPTV discovery may
                // involve thousands of channels and must not hold Stremio results back.
                val stremioStreams = stremioDeferred.await()
                if (stremioStreams.isNotEmpty()) {
                    val immediate = _uiState.value as? EventUiState.Success
                    if (immediate != null) {
                        _uiState.value = immediate.copy(stremioStreams = stremioStreams)
                    }
                    val addonSelection = selectBestStream(
                        event = event,
                        channels = emptyList(),
                        knownStremioStreams = stremioStreams
                    )
                    val current = _uiState.value as? EventUiState.Success
                    if (current != null) {
                        _uiState.value = current.copy(
                            stremioStreams = stremioStreams,
                            primaryStreamTarget = addonSelection.primary?.playbackTarget,
                            streamCandidates = addonSelection.candidates
                        )
                    }
                }

                val channels = channelsDeferred.await()
                val selectionDeferred = async(kotlinx.coroutines.Dispatchers.IO) {
                    selectBestStream(event, channels, stremioStreams)
                }
                val selection = selectionDeferred.await()
                val relevantChannels = selection.relevantChannels
                val matches = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    relevantChannels.map { relevant ->
                        MatchResult(event, relevant.channel, (relevant.likelihoodScore / 100f).coerceIn(0f, 1f))
                    }
                }
                val summaryEvent = summaryDeferred.await()
                _uiState.value = EventUiState.Success(
                    event = summaryEvent,
                    bestMatch = matches.firstOrNull(),
                    alternativeMatches = matches.drop(1),
                    relevantChannels = relevantChannels,
                    broadcastStations = initialStations,
                    stremioStreams = selection.stremioStreams,
                    streamCandidates = selection.candidates,
                    isLoadingStreams = false,
                    primaryStreamTarget = selection.primary?.playbackTarget,
                    favoriteTeamIds = favoriteIdsFor(summaryEvent)
                )

                // Start observing for live updates
                sportsRepository.observeLiveEvent(eventId).collect { liveEvent ->
                    val currentState = _uiState.value
                    if (currentState is EventUiState.Success) {
                        _uiState.value = currentState.copy(event = liveEvent)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = EventUiState.Error(e.message ?: "Failed to load event")
            }
        }
    }

    fun toggleFavoriteTeam(team: Team) {
        val current = _uiState.value as? EventUiState.Success ?: return
        preferencesManager.toggleFavoriteTeam(
            FavoriteTeam(team.id, current.event.league, team.name, team.abbreviation, team.logoUrl, team.colors)
        )
        _uiState.value = current.copy(favoriteTeamIds = favoriteIdsFor(current.event))
    }

    private fun favoriteIdsFor(event: SportEvent): Set<String> = listOfNotNull(event.homeTeam, event.awayTeam)
        .filter { preferencesManager.isFavoriteTeam(it.id, event.league) }
        .mapTo(mutableSetOf()) { it.id }
}

sealed class EventUiState {
    object Loading : EventUiState()
    @Immutable
    data class Success(
        val event: SportEvent,
        val bestMatch: MatchResult?,
        val alternativeMatches: List<MatchResult>,
        val relevantChannels: List<com.shiv.rally.domain.model.RelevantChannel>,
        val broadcastStations: List<String> = emptyList(),
        val stremioStreams: List<StremioStreamOption>,
        val streamCandidates: List<com.shiv.rally.domain.model.StreamCandidate> = emptyList(),
        val isLoadingStreams: Boolean = false,
        val primaryStreamTarget: String? = null,
        val favoriteTeamIds: Set<String> = emptySet()
    ) : EventUiState()
    data class Error(val message: String) : EventUiState()
}
