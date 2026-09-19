package com.shiv.rally.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.RelevantChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.model.StreamCandidate
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.domain.repository.SportsRepository
import com.shiv.rally.domain.usecase.MatcherService
import com.shiv.rally.domain.usecase.SelectBestStreamUseCase
import com.shiv.rally.domain.usecase.textMatchesEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.math.abs
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val iptvRepository: IptvRepository,
    private val sportsRepository: SportsRepository,
    private val matcherService: MatcherService,
    private val stremioRepository: com.shiv.rally.domain.repository.StremioRepository,
    private val preferencesManager: com.shiv.rally.data.local.PreferencesManager,
    private val selectBestStream: SelectBestStreamUseCase,
    private val diagnostics: com.shiv.rally.data.local.RallyDiagnostics? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val rawChannelId: String = checkNotNull(savedStateHandle["channelId"])
    private var activeChannelId: String = rawChannelId
    private var activeEventId: String? = savedStateHandle.get<String>("eventId")?.takeIf { it.isNotBlank() && it != "null" }
    private var loadJob: Job? = null
    private var metadataJob: Job? = null
    private var liveUpdateJob: Job? = null
    private var recoveryJob: Job? = null
    private var loadGeneration = 0L
    private val failedPlaybackTargets = linkedSetOf<String>()

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadStream(rawChannelId)
    }

    fun switchStream(newChannelId: String) {
        failedPlaybackTargets.clear()
        val current = _uiState.value as? PlayerUiState.Success
        val decodedTarget = decodePlayerTarget(newChannelId)
        loadStream(
            channelTarget = newChannelId,
            overrideEvent = current?.event,
            precomputedRelevant = current?.relevantChannels,
            initialHeaders = current?.stremioStreams
                ?.firstOrNull { it.streamUrl == newChannelId || it.streamUrl == decodedTarget }
                ?.headers,
            precomputedStremio = current?.stremioStreams,
            precomputedCandidates = current?.streamCandidates
        )
    }

    fun retry() {
        failedPlaybackTargets.clear()
        val current = _uiState.value as? PlayerUiState.Success
        loadStream(
            channelTarget = activeChannelId,
            overrideEvent = current?.event,
            precomputedRelevant = current?.relevantChannels,
            initialHeaders = current?.streamHeaders,
            precomputedStremio = current?.stremioStreams,
            precomputedCandidates = current?.streamCandidates
        )
    }

    fun switchToEvent(newEventId: String) {
        failedPlaybackTargets.clear()
        viewModelScope.launch(ioDispatcher) {
            try {
                val currentState = _uiState.value
                if (currentState is PlayerUiState.Success) {
                    _uiState.value = currentState.copy(isSwitchingGame = true)
                }

                activeEventId = newEventId
                val events = sportsRepository.getEventsSnapshot()
                val targetEvent = events.find { it.id == newEventId }
                if (targetEvent == null) {
                    if (currentState is PlayerUiState.Success) {
                        _uiState.value = currentState.copy(isSwitchingGame = false)
                    }
                    return@launch
                }

                val allChannels = iptvRepository.getChannels()
                val selection = selectBestStream(targetEvent, allChannels)
                val relevantChannels = selection.relevantChannels
                val selected = selection.primary
                val bestStreamTarget = selected?.playbackTarget

                if (bestStreamTarget != null) {
                    loadStream(
                        channelTarget = bestStreamTarget,
                        overrideEvent = targetEvent,
                        precomputedRelevant = relevantChannels,
                        initialHeaders = selected.headers,
                        precomputedStremio = selection.stremioStreams,
                        precomputedCandidates = selection.candidates
                    )
                } else if (currentState is PlayerUiState.Success) {
                    _uiState.value = currentState.copy(isSwitchingGame = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error switching event: ${e.message}", e)
                val currentState = _uiState.value
                if (currentState is PlayerUiState.Success) {
                    _uiState.value = currentState.copy(isSwitchingGame = false)
                }
            }
        }
    }

    private fun loadStream(
        channelTarget: String,
        overrideEvent: SportEvent? = null,
        precomputedRelevant: List<RelevantChannel>? = null,
        initialHeaders: Map<String, String>? = null,
        precomputedStremio: List<StremioStreamOption>? = null,
        precomputedCandidates: List<StreamCandidate>? = null,
        recoveryAttempt: Int = 0
    ) {
        loadJob?.cancel()
        metadataJob?.cancel()
        liveUpdateJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch(ioDispatcher) {
            try {
                activeChannelId = channelTarget
                val decodedChannelId = decodePlayerTarget(channelTarget)
                val isExternalStream = (decodedChannelId.startsWith("http://") || decodedChannelId.startsWith("https://")) &&
                    !decodedChannelId.contains("localhost")

                // If channelId is already an external direct stream URL (from Stremio), use it directly.
                // Otherwise (numeric ID or localhost/ffrt Stalker cmd), resolve via Stalker createLink
                val streamUrl = if (isExternalStream) {
                    decodedChannelId
                } else {
                    iptvRepository.getChannelStreamUrl(decodedChannelId)
                }

                if (generation != loadGeneration) return@launch
                _uiState.value = PlayerUiState.Success(
                    streamUrl = streamUrl,
                    macAddress = preferencesManager.macAddress.trim(),
                    token = preferencesManager.authToken.trim(),
                    event = overrideEvent,
                    otherLiveEvents = emptyList(),
                    currentChannel = null,
                    relevantChannels = precomputedRelevant.orEmpty(),
                    stremioStreams = precomputedStremio.orEmpty(),
                    streamCandidates = precomputedCandidates.orEmpty(),
                    isSwitchingGame = false,
                    streamHeaders = initialHeaders,
                    isExternalStream = isExternalStream,
                    recoveryAttempt = recoveryAttempt,
                    recoveryStatus = "Trying another verified source…".takeIf { recoveryAttempt > 0 },
                    lowLatencyMode = preferencesManager.lowLatencyMode,
                    adaptiveQualityEnabled = preferencesManager.adaptiveQualityEnabled,
                    audioNormalizationEnabled = preferencesManager.audioNormalizationEnabled,
                    sourceHealthScore = preferencesManager.streamHealth(streamUrl).score
                )

                // Playback can start as soon as its URL resolves. Catalogs enrich the UI concurrently.
                val channelsDeferred = async {
                    runCatching { iptvRepository.getChannels() }.getOrDefault(emptyList())
                }
                val eventsDeferred = async {
                    runCatching { sportsRepository.getEventsSnapshot() }.getOrDefault(emptyList())
                }
                val allEventsSnapshot = eventsDeferred.await()
                val allLive = allEventsSnapshot.filter {
                    it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME
                }
                val currentEventId = activeEventId
                var event: SportEvent? = overrideEvent
                if (event == null && currentEventId != null) {
                    event = allEventsSnapshot.find { it.id == currentEventId }
                        ?: sportsRepository.getEventById(currentEventId)
                }

                // Preserve the event context as soon as it resolves. Loading the full IPTV catalog
                // must never hold the Game View button hostage.
                if (generation != loadGeneration) return@launch
                val earlyEvent = event
                if (earlyEvent != null) {
                    activeEventId = earlyEvent.id
                    preferencesManager.recordViewedLeague(earlyEvent.league)
                    val currentState = _uiState.value
                    if (currentState is PlayerUiState.Success && currentState.streamUrl == streamUrl) {
                        _uiState.value = currentState.copy(
                            event = earlyEvent,
                            otherLiveEvents = allLive.filter { it.id != earlyEvent.id }
                        )
                    }
                }

                val allChannels = channelsDeferred.await()
                val channel = allChannels.find {
                    it.id == decodedChannelId || it.name.equals(decodedChannelId, ignoreCase = true)
                }
                val guide = if (event == null && channel != null) {
                    channel.guide ?: runCatching { iptvRepository.getChannelGuide(channel.id) }.getOrNull()
                } else {
                    channel?.guide
                }
                val currentChannel = channel?.let { if (guide != null) it.copy(guide = guide) else it }

                // Live TV can be opened directly, outside an event page. Use the provider's EPG
                // and dedicated matchup-channel name to recover the game context safely.
                if (event == null && currentChannel != null) {
                    event = inferEventForChannel(currentChannel, guide, allEventsSnapshot)
                    if (event != null) activeEventId = event.id
                }
                val resolvedEvent = event
                val otherLive = allLive.filter { it.id != (resolvedEvent?.id ?: currentEventId) }

                val relevantChannels = precomputedRelevant ?: if (resolvedEvent != null && allChannels.isNotEmpty()) {
                    matcherService.getRelevantChannelsForEvent(resolvedEvent, allChannels)
                } else {
                    emptyList()
                }

                if (generation != loadGeneration) return@launch
                val currentState = _uiState.value
                if (currentState is PlayerUiState.Success && currentState.streamUrl == streamUrl) {
                    _uiState.value = currentState.copy(
                        event = resolvedEvent,
                        otherLiveEvents = otherLive,
                        currentChannel = currentChannel,
                        relevantChannels = relevantChannels
                    )
                }

                // Asynchronously fetch Stremio streams and rich event summary (leaders/team stats) in background
                if (resolvedEvent != null) {
                    metadataJob = viewModelScope.launch(ioDispatcher) {
                        try {
                            val stremioDef = async {
                                precomputedStremio ?: try {
                                    stremioRepository.getStreamsForEvent(resolvedEvent)
                                } catch (e: Exception) {
                                    android.util.Log.e("PlayerViewModel", "Stremio stream fetch error", e)
                                    emptyList()
                                }
                            }
                            val summaryDef = async {
                                try {
                                    sportsRepository.getEventSummary(resolvedEvent)
                                } catch (e: Exception) {
                                    android.util.Log.e("PlayerViewModel", "Event summary fetch error", e)
                                    resolvedEvent
                                }
                            }
                            val stremioStreams = stremioDef.await()
                            val updatedEvent = summaryDef.await()
                            val immediate = _uiState.value
                            if (generation == loadGeneration && immediate is PlayerUiState.Success && immediate.streamUrl == streamUrl) {
                                _uiState.value = immediate.copy(event = updatedEvent, stremioStreams = stremioStreams)
                            }
                            val updatedSelection = runCatching {
                                selectBestStream(updatedEvent, allChannels, stremioStreams)
                            }.getOrNull()

                            val matchedStream = stremioStreams.find { it.streamUrl == streamUrl }
                            val current = _uiState.value
                            if (generation == loadGeneration && current is PlayerUiState.Success && current.streamUrl == streamUrl) {
                                _uiState.value = current.copy(
                                    event = updatedEvent,
                                    stremioStreams = stremioStreams,
                                    streamCandidates = updatedSelection?.candidates ?: current.streamCandidates,
                                    streamHeaders = matchedStream?.headers ?: current.streamHeaders
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PlayerViewModel", "Background resolution failed", e)
                        }
                    }

                    liveUpdateJob = viewModelScope.launch(ioDispatcher) {
                        sportsRepository.observeLiveEvent(resolvedEvent.id).collect { updated ->
                            val current = _uiState.value
                            if (generation == loadGeneration && current is PlayerUiState.Success) {
                                _uiState.value = current.copy(event = updated)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = PlayerUiState.Error(e.message ?: "Failed to load stream")
            }
        }
    }

    fun reportPlaybackReady(streamUrl: String, startupMs: Long) {
        preferencesManager.recordStreamSuccess(streamUrl, startupMs.coerceAtLeast(0L))
        diagnostics?.record(
            kind = "Playback ready",
            message = "Stream started in ${startupMs.coerceAtLeast(0L)} ms",
            detail = "Recovery attempt ${(_uiState.value as? PlayerUiState.Success)?.recoveryAttempt ?: 0}"
        )
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (current.streamUrl == streamUrl && current.recoveryStatus != null) {
            _uiState.value = current.copy(recoveryStatus = null, autoRecoveryExhausted = false)
        }
    }

    fun reportPlaybackStall(streamUrl: String) {
        preferencesManager.recordStreamStall(streamUrl)
        diagnostics?.record("Playback stall", "Playback entered buffering after startup")
    }

    fun recoverFromPlaybackFailure(reason: String) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (recoveryJob?.isActive == true || current.autoRecoveryExhausted) return
        preferencesManager.recordStreamFailure(current.streamUrl)
        diagnostics?.record("Playback", reason, "Recovery attempt ${current.recoveryAttempt + 1}")
        failedPlaybackTargets += current.streamUrl
        failedPlaybackTargets += activeChannelId
        recoveryJob = viewModelScope.launch(ioDispatcher) {
            val event = current.event
            if (event == null || current.recoveryAttempt >= 3) {
                _uiState.value = current.copy(autoRecoveryExhausted = true, recoveryStatus = null, terminalPlaybackError = reason)
                return@launch
            }
            val selection = runCatching {
                selectBestStream(event, precomputedKnownChannels(current), current.stremioStreams)
            }.getOrNull()
            val next = selection?.candidates?.let { candidates ->
                chooseRecoveryCandidate(candidates, failedPlaybackTargets)
            }
            if (next == null) {
                val latest = _uiState.value as? PlayerUiState.Success ?: current
                _uiState.value = latest.copy(autoRecoveryExhausted = true, recoveryStatus = null, terminalPlaybackError = reason)
            } else {
                diagnostics?.record(
                    kind = "Playback recovery",
                    message = "Switching to ${next.sourceKind.name.lowercase()} fallback",
                    detail = "${next.quality.resolution ?: "Unknown quality"} · ${next.matchEvidence}"
                )
                loadStream(
                    channelTarget = next.playbackTarget,
                    overrideEvent = event,
                    precomputedRelevant = selection.relevantChannels,
                    initialHeaders = next.headers,
                    precomputedStremio = selection.stremioStreams,
                    precomputedCandidates = selection.candidates,
                    recoveryAttempt = current.recoveryAttempt + 1
                )
            }
        }
    }

    private fun precomputedKnownChannels(current: PlayerUiState.Success): List<IptvChannel>? {
        val channels = current.relevantChannels.map { it.channel }
        return channels.takeIf { it.isNotEmpty() }
    }
}

internal fun decodePlayerTarget(value: String): String = runCatching {
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}.getOrDefault(value)

internal fun chooseRecoveryCandidate(
    candidates: List<StreamCandidate>,
    failedTargets: Set<String>
): StreamCandidate? {
    val normalizedFailures = failedTargets.flatMap { target ->
        listOf(target, decodePlayerTarget(target))
    }.toSet()
    return candidates.firstOrNull { candidate ->
        candidate.exactGameMatch &&
            candidate.preflightPassed != false &&
            candidate.playbackTarget !in normalizedFailures &&
            decodePlayerTarget(candidate.playbackTarget) !in normalizedFailures
    }
}

internal fun inferEventForChannel(
    channel: IptvChannel,
    guide: ChannelGuide?,
    events: List<SportEvent>,
    now: Instant = Instant.now()
): SportEvent? {
    val evidence = listOfNotNull(
        guide?.now?.title,
        guide?.now?.description,
        channel.name
    ).filter { it.isNotBlank() }
    if (evidence.isEmpty()) return null

    val nearbyEvents = events.filter { event ->
        event.status == EventStatus.LIVE ||
            event.status == EventStatus.HALFTIME ||
            (event.status == EventStatus.NOT_STARTED &&
                abs(event.startTime.epochSecond - now.epochSecond) <= 6 * 60 * 60)
    }
    return nearbyEvents
        .sortedWith(
            compareByDescending<SportEvent> {
                it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME
            }.thenBy { abs(it.startTime.epochSecond - now.epochSecond) }
        )
        .firstOrNull { event -> evidence.any { text -> textMatchesEvent(text, event) } }
}

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    @Immutable
    data class Success(
        val streamUrl: String,
        val macAddress: String = "",
        val token: String = "",
        val event: SportEvent?,
        val otherLiveEvents: List<SportEvent> = emptyList(),
        val currentChannel: IptvChannel? = null,
        val relevantChannels: List<RelevantChannel> = emptyList(),
        val stremioStreams: List<StremioStreamOption> = emptyList(),
        val streamCandidates: List<StreamCandidate> = emptyList(),
        val isSwitchingGame: Boolean = false,
        val streamHeaders: Map<String, String>? = null,
        val isExternalStream: Boolean = false,
        val recoveryAttempt: Int = 0,
        val recoveryStatus: String? = null,
        val autoRecoveryExhausted: Boolean = false,
        val terminalPlaybackError: String? = null,
        val lowLatencyMode: Boolean = true,
        val sourceHealthScore: Int = 0,
        val adaptiveQualityEnabled: Boolean = true,
        val audioNormalizationEnabled: Boolean = true
    ) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}
