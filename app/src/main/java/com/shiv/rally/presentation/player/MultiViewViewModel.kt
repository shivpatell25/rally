package com.shiv.rally.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.MultiViewLayoutMode
import com.shiv.rally.domain.model.MultiViewSlot
import com.shiv.rally.domain.model.RelevantChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StreamCandidate
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.domain.repository.SportsRepository
import com.shiv.rally.domain.usecase.SelectBestStreamUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

private fun decodeRouteComponent(value: String): String = runCatching {
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}.getOrDefault(value)

private fun sourceQualityLabel(candidate: StreamCandidate): String = buildList {
    candidate.quality.resolution?.let(::add)
    candidate.quality.fps?.let(::add)
    if (candidate.quality.isHdr) add("HDR")
}.joinToString(" · ").ifBlank { "Adaptive" }

@Immutable
data class MultiViewUiState(
    val slots: List<MultiViewSlot> = emptyList(),
    val focusedSlotIndex: Int = 0,
    val audioSlotIndex: Int = 0,
    val layoutMode: MultiViewLayoutMode = MultiViewLayoutMode.AUTO,
    val availableLiveEvents: List<SportEvent> = emptyList(),
    val availableChannels: List<IptvChannel> = emptyList(),
    val isPickerOpen: Boolean = false,
    val pickerTargetSlotIndex: Int? = null,
    val isSourcePickerOpen: Boolean = false,
    val sourcePickerTargetSlotIndex: Int? = null,
    val sourcePickerLoading: Boolean = false,
    val sourcePickerCandidates: List<StreamCandidate> = emptyList(),
    val sourcePickerChannels: List<RelevantChannel> = emptyList(),
    val sourcePickerStreams: List<StremioStreamOption> = emptyList(),
    val macAddress: String = "",
    val token: String = ""
)

@HiltViewModel
class MultiViewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sportsRepository: SportsRepository,
    private val iptvRepository: IptvRepository,
    private val selectBestStream: SelectBestStreamUseCase,
    private val preferencesManager: PreferencesManager,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val slotJobs = ConcurrentHashMap<String, Job>()

    private val _uiState = MutableStateFlow(
        MultiViewUiState(
            macAddress = preferencesManager.macAddress.trim(),
            token = preferencesManager.authToken.trim()
        )
    )
    val uiState: StateFlow<MultiViewUiState> = _uiState.asStateFlow()

    init {
        val initialChannelId = savedStateHandle.get<String>("channelId")?.takeIf { it.isNotBlank() && it != "null" }
        val initialEventId = savedStateHandle.get<String>("eventId")?.takeIf { it.isNotBlank() && it != "null" }
        val rawEventIds = savedStateHandle.get<String>("eventIds")?.takeIf { it.isNotBlank() && it != "null" }
        val initialEventIds = rawEventIds?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

        initialize(initialChannelId, initialEventId, initialEventIds)
    }

    private fun initialize(
        initialChannelId: String?,
        initialEventId: String?,
        initialEventIds: List<String>?
    ) {
        viewModelScope.launch(ioDispatcher) {
            val liveEventsDeferred = async {
                try {
                    sportsRepository.getLiveEvents().filter { it.status != EventStatus.FINISHED }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val channelsDeferred = async {
                try {
                    iptvRepository.getChannels()
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val liveEvents = liveEventsDeferred.await()
            val channels = channelsDeferred.await()

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    availableLiveEvents = liveEvents,
                    availableChannels = channels
                )
            }

            // Scenario 1: Multiple events specified (e.g. from Home Screen Multi-View selector)
            if (!initialEventIds.isNullOrEmpty()) {
                val eventsToLoad = initialEventIds.take(4).mapNotNull { id ->
                    liveEvents.find { it.id == id } ?: sportsRepository.getEventById(id)
                }
                if (eventsToLoad.isNotEmpty()) {
                    loadMultipleEvents(eventsToLoad, channels)
                    return@launch
                }
            }

            // Scenario 2: Single initial event or channel (e.g. from PlayerScreen "+ Multi View")
            if (initialChannelId != null || initialEventId != null) {
                val event = if (initialEventId != null) {
                    liveEvents.find { it.id == initialEventId } ?: sportsRepository.getEventById(initialEventId)
                } else null

                val channel = if (initialChannelId != null) {
                    channels.find { it.id == initialChannelId || it.name.equals(initialChannelId, ignoreCase = true) }
                } else null

                val initialSlot = resolveSlot(event = event, channel = channel, fallbackChannelId = initialChannelId, allChannels = channels)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        slots = listOf(initialSlot),
                        focusedSlotIndex = 0,
                        isPickerOpen = true, // Prompt immediately to add Game 2!
                        pickerTargetSlotIndex = null // null means adding a new slot
                    )
                }
                return@launch
            }

            // Scenario 3: Launched directly with no args -> pre-load top 2 live events if available
            if (liveEvents.isNotEmpty()) {
                val topEvents = liveEvents.take(2)
                loadMultipleEvents(topEvents, channels)
            } else {
                // Open picker so user can pick channels
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isPickerOpen = true,
                        pickerTargetSlotIndex = null
                    )
                }
            }
        }
    }

    private suspend fun loadMultipleEvents(events: List<SportEvent>, allChannels: List<IptvChannel>) {
        val initialSlots = events.map { event ->
            MultiViewSlot(
                event = event,
                title = "${event.awayTeam?.abbreviation ?: ""} @ ${event.homeTeam?.abbreviation ?: ""}",
                subtitle = event.league,
                scoreText = if (event.scoreAway != null && event.scoreHome != null) "${event.scoreAway} - ${event.scoreHome}" else null,
                statusText = event.gameStatusDetail ?: if (event.status == EventStatus.LIVE) "LIVE" else "",
                isLoading = true
            )
        }

        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                slots = initialSlots,
                focusedSlotIndex = 0
            )
        }

        // Concurrently resolve stream URLs for each slot
        val resolvedSlots = events.mapIndexed { index, event ->
            viewModelScope.async(ioDispatcher) {
                val slot = resolveSlot(event = event, channel = null, fallbackChannelId = null, allChannels = allChannels)
                slot.copy(slotId = initialSlots.getOrNull(index)?.slotId ?: slot.slotId)
            }
        }.awaitAll()

        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(slots = resolvedSlots)
        }
    }

    private suspend fun resolveSlot(
        event: SportEvent?,
        channel: IptvChannel?,
        fallbackChannelId: String?,
        allChannels: List<IptvChannel>
    ): MultiViewSlot {
        var resolvedStreamUrl = ""
        var resolvedHeaders: Map<String, String>? = null
        var resolvedChannel: IptvChannel? = channel
        var resolvedEvent: SportEvent? = event
        var selectedSourceId: String? = null
        var sourceTitle: String? = channel?.name
        var sourceQuality: String? = null

        val title = when {
            event != null -> "${event.awayTeam?.abbreviation ?: ""} @ ${event.homeTeam?.abbreviation ?: ""}"
            channel != null -> channel.name
            !fallbackChannelId.isNullOrBlank() -> fallbackChannelId
            else -> "Live Stream"
        }

        val subtitle = when {
            event != null -> event.league
            channel != null -> channel.category.ifEmpty { "Sports" }
            else -> null
        }

        val scoreText = if (event != null && event.scoreAway != null && event.scoreHome != null) {
            "${event.scoreAway} - ${event.scoreHome}"
        } else null

        val statusText = event?.gameStatusDetail ?: if (event?.status == EventStatus.LIVE) "LIVE" else ""

        try {
            // Case A: Direct channel ID or fallbackChannelId provided
            if (channel != null || (!fallbackChannelId.isNullOrBlank() && (event == null || fallbackChannelId.startsWith("http")))) {
                val targetId = channel?.id ?: fallbackChannelId!!
                val decodedTarget = decodeRouteComponent(targetId)
                resolvedStreamUrl = if ((decodedTarget.startsWith("http://") || decodedTarget.startsWith("https://")) && !decodedTarget.contains("localhost")) {
                    decodedTarget
                } else {
                    iptvRepository.getChannelStreamUrl(decodedTarget)
                }
            } else if (event != null) {
                // Case B: use the same quality-first, exact-game resolver as Event and Player.
                val selection = selectBestStream(event, allChannels)
                val selected = selection.primary
                if (selected != null) {
                    resolvedChannel = selected.channel
                    resolvedHeaders = selected.headers
                    selectedSourceId = selected.id
                    sourceTitle = selected.title
                    sourceQuality = sourceQualityLabel(selected)
                    resolvedStreamUrl = if (selected.channel != null) {
                        iptvRepository.getChannelStreamUrl(decodeRouteComponent(selected.playbackTarget))
                    } else {
                        selected.playbackTarget
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { android.util.Log.e("MultiViewViewModel", "Error resolving stream for $title: ${e.message}", e) }
            return MultiViewSlot(
                event = resolvedEvent,
                channel = resolvedChannel,
                streamUrl = "",
                title = title,
                subtitle = subtitle,
                scoreText = scoreText,
                statusText = statusText,
                isLoading = false,
                error = e.message ?: "Failed to connect to stream"
            )
        }

        if (resolvedStreamUrl.isBlank()) {
            return MultiViewSlot(
                event = resolvedEvent,
                channel = resolvedChannel,
                streamUrl = "",
                title = title,
                subtitle = subtitle,
                scoreText = scoreText,
                statusText = statusText,
                isLoading = false,
                error = "No live broadcast available"
            )
        }

        return MultiViewSlot(
            event = resolvedEvent,
            channel = resolvedChannel,
            streamUrl = resolvedStreamUrl,
            streamHeaders = resolvedHeaders,
            title = title,
            subtitle = subtitle,
            scoreText = scoreText,
            statusText = statusText,
            isLoading = false,
            selectedSourceId = selectedSourceId,
            sourceTitle = sourceTitle,
            sourceQuality = sourceQuality,
            error = null
        )
    }

    fun setFocusedSlot(index: Int) {
        val currentSlots = _uiState.value.slots
        if (index in currentSlots.indices) {
            _uiState.value = _uiState.value.copy(focusedSlotIndex = index)
        }
    }

    fun setAudioSlot(index: Int) {
        if (index in _uiState.value.slots.indices) {
            _uiState.value = _uiState.value.copy(audioSlotIndex = index)
        }
    }

    fun promoteSlot(index: Int) {
        val state = _uiState.value
        if (index !in state.slots.indices || index == 0) return
        val audioId = state.slots.getOrNull(state.audioSlotIndex)?.slotId
        val focusedId = state.slots.getOrNull(state.focusedSlotIndex)?.slotId
        val updated = state.slots.toMutableList().apply { add(0, removeAt(index)) }
        _uiState.value = state.copy(
            slots = updated,
            focusedSlotIndex = updated.indexOfFirst { it.slotId == focusedId }.coerceAtLeast(0),
            audioSlotIndex = updated.indexOfFirst { it.slotId == audioId }.coerceAtLeast(0)
        )
    }

    fun swapSlot(index: Int) {
        val state = _uiState.value
        if (state.slots.size < 2 || index !in state.slots.indices) return
        val other = if (index == state.slots.lastIndex) index - 1 else index + 1
        val audioId = state.slots.getOrNull(state.audioSlotIndex)?.slotId
        val focusedId = state.slots.getOrNull(state.focusedSlotIndex)?.slotId
        val updated = state.slots.toMutableList().apply {
            val held = this[index]
            this[index] = this[other]
            this[other] = held
        }
        _uiState.value = state.copy(
            slots = updated,
            focusedSlotIndex = updated.indexOfFirst { it.slotId == focusedId }.coerceAtLeast(0),
            audioSlotIndex = updated.indexOfFirst { it.slotId == audioId }.coerceAtLeast(0)
        )
    }

    fun setLayoutMode(mode: MultiViewLayoutMode) {
        _uiState.value = _uiState.value.copy(layoutMode = mode)
    }

    fun openPickerForAdd() {
        if (_uiState.value.slots.size >= 4) return
        _uiState.value = _uiState.value.copy(
            isPickerOpen = true,
            pickerTargetSlotIndex = null
        )
    }

    fun openPickerForSwap(slotIndex: Int) {
        _uiState.value = _uiState.value.copy(
            isPickerOpen = true,
            pickerTargetSlotIndex = slotIndex
        )
    }

    fun closePicker() {
        _uiState.value = _uiState.value.copy(
            isPickerOpen = false,
            pickerTargetSlotIndex = null
        )
    }

    fun openSourcePicker(slotIndex: Int) {
        val state = _uiState.value
        val event = state.slots.getOrNull(slotIndex)?.event ?: return
        _uiState.value = state.copy(
            isSourcePickerOpen = true,
            sourcePickerTargetSlotIndex = slotIndex,
            sourcePickerLoading = true,
            sourcePickerCandidates = emptyList(),
            sourcePickerChannels = emptyList(),
            sourcePickerStreams = emptyList()
        )
        viewModelScope.launch(ioDispatcher) {
            val channels = state.availableChannels.ifEmpty {
                runCatching { iptvRepository.getChannels() }.getOrDefault(emptyList())
            }
            val selection = runCatching { selectBestStream(event, channels) }.getOrNull()
            withContext(Dispatchers.Main) {
                val current = _uiState.value
                if (current.sourcePickerTargetSlotIndex == slotIndex && current.isSourcePickerOpen) {
                    _uiState.value = current.copy(
                        sourcePickerLoading = false,
                        sourcePickerCandidates = selection?.candidates.orEmpty(),
                        sourcePickerChannels = selection?.relevantChannels.orEmpty(),
                        sourcePickerStreams = selection?.stremioStreams.orEmpty()
                    )
                }
            }
        }
    }

    fun closeSourcePicker() {
        _uiState.value = _uiState.value.copy(
            isSourcePickerOpen = false,
            sourcePickerTargetSlotIndex = null,
            sourcePickerLoading = false,
            sourcePickerCandidates = emptyList(),
            sourcePickerChannels = emptyList(),
            sourcePickerStreams = emptyList()
        )
    }

    fun selectSourceForFocusedSlot(playbackTarget: String) {
        val state = _uiState.value
        val slotIndex = state.sourcePickerTargetSlotIndex ?: return
        val slot = state.slots.getOrNull(slotIndex) ?: return
        val candidate = state.sourcePickerCandidates.firstOrNull { it.playbackTarget == playbackTarget }
            ?: state.sourcePickerCandidates.firstOrNull { it.stremioStream?.streamUrl == playbackTarget }
            ?: return
        val loadingSlot = slot.copy(
            streamUrl = "",
            streamHeaders = candidate.headers,
            selectedSourceId = candidate.id,
            sourceTitle = candidate.title,
            sourceQuality = sourceQualityLabel(candidate),
            channel = candidate.channel,
            isLoading = true,
            error = null
        )
        val updated = state.slots.toMutableList().apply { this[slotIndex] = loadingSlot }
        closeSourcePicker()
        _uiState.value = _uiState.value.copy(
            slots = updated,
            focusedSlotIndex = slotIndex
        )
        resolveIntoSlot(loadingSlot.slotId) {
            runCatching {
                val url = if (candidate.channel != null) {
                    iptvRepository.getChannelStreamUrl(decodeRouteComponent(candidate.playbackTarget))
                } else {
                    candidate.playbackTarget
                }
                loadingSlot.copy(streamUrl = url, isLoading = false, error = null)
            }.getOrElse { error ->
                loadingSlot.copy(isLoading = false, error = error.message ?: "Unable to start this source")
            }
        }
    }

    fun addSlotFromEvent(event: SportEvent) {
        val currentSlots = _uiState.value.slots
        if (currentSlots.size >= 4) return

        val newSlot = MultiViewSlot(
            event = event,
            title = "${event.awayTeam?.abbreviation ?: ""} @ ${event.homeTeam?.abbreviation ?: ""}",
            subtitle = event.league,
            scoreText = if (event.scoreAway != null && event.scoreHome != null) "${event.scoreAway} - ${event.scoreHome}" else null,
            statusText = event.gameStatusDetail ?: if (event.status == EventStatus.LIVE) "LIVE" else "",
            isLoading = true
        )

        val newSlots = currentSlots + newSlot
        val newTargetIndex = newSlots.size - 1
        _uiState.value = _uiState.value.copy(
            slots = newSlots,
            focusedSlotIndex = newTargetIndex,
            isPickerOpen = false,
            pickerTargetSlotIndex = null
        )

        resolveIntoSlot(newSlot.slotId) {
            val allChannels = _uiState.value.availableChannels.ifEmpty { iptvRepository.getChannels() }
            resolveSlot(event = event, channel = null, fallbackChannelId = null, allChannels = allChannels)
        }
    }

    fun addSlotFromChannel(channel: IptvChannel) {
        val currentSlots = _uiState.value.slots
        if (currentSlots.size >= 4) return

        val newSlot = MultiViewSlot(
            channel = channel,
            title = channel.name,
            subtitle = channel.category.ifEmpty { "Sports" },
            isLoading = true
        )

        val newSlots = currentSlots + newSlot
        val newTargetIndex = newSlots.size - 1
        _uiState.value = _uiState.value.copy(
            slots = newSlots,
            focusedSlotIndex = newTargetIndex,
            isPickerOpen = false,
            pickerTargetSlotIndex = null
        )

        resolveIntoSlot(newSlot.slotId) {
            resolveSlot(event = null, channel = channel, fallbackChannelId = channel.id, allChannels = emptyList())
        }
    }

    fun replaceSlotWithEvent(slotIndex: Int, event: SportEvent) {
        val currentSlots = _uiState.value.slots
        if (slotIndex !in currentSlots.indices) return

        val tempSlot = currentSlots[slotIndex].copy(
            event = event,
            channel = null,
            streamUrl = "",
            title = "${event.awayTeam?.abbreviation ?: ""} @ ${event.homeTeam?.abbreviation ?: ""}",
            subtitle = event.league,
            scoreText = if (event.scoreAway != null && event.scoreHome != null) "${event.scoreAway} - ${event.scoreHome}" else null,
            statusText = event.gameStatusDetail ?: if (event.status == EventStatus.LIVE) "LIVE" else "",
            selectedSourceId = null,
            sourceTitle = null,
            sourceQuality = null,
            isLoading = true,
            error = null
        )

        val updated = currentSlots.toMutableList()
        updated[slotIndex] = tempSlot
        _uiState.value = _uiState.value.copy(
            slots = updated,
            focusedSlotIndex = slotIndex,
            isPickerOpen = false,
            pickerTargetSlotIndex = null
        )

        resolveIntoSlot(tempSlot.slotId) {
            val allChannels = _uiState.value.availableChannels.ifEmpty { iptvRepository.getChannels() }
            resolveSlot(event = event, channel = null, fallbackChannelId = null, allChannels = allChannels)
        }
    }

    fun replaceSlotWithChannel(slotIndex: Int, channel: IptvChannel) {
        val currentSlots = _uiState.value.slots
        if (slotIndex !in currentSlots.indices) return

        val tempSlot = currentSlots[slotIndex].copy(
            event = null,
            channel = channel,
            streamUrl = "",
            title = channel.name,
            subtitle = channel.category.ifEmpty { "Sports" },
            scoreText = null,
            statusText = null,
            selectedSourceId = null,
            sourceTitle = null,
            sourceQuality = null,
            isLoading = true,
            error = null
        )

        val updated = currentSlots.toMutableList()
        updated[slotIndex] = tempSlot
        _uiState.value = _uiState.value.copy(
            slots = updated,
            focusedSlotIndex = slotIndex,
            isPickerOpen = false,
            pickerTargetSlotIndex = null
        )

        resolveIntoSlot(tempSlot.slotId) {
            resolveSlot(event = null, channel = channel, fallbackChannelId = channel.id, allChannels = emptyList())
        }
    }

    fun removeSlot(slotIndex: Int) {
        val currentSlots = _uiState.value.slots
        if (slotIndex !in currentSlots.indices) return

        val removed = currentSlots[slotIndex]
        val audioSlotId = currentSlots.getOrNull(_uiState.value.audioSlotIndex)?.slotId
        slotJobs.remove(removed.slotId)?.cancel()
        val updated = currentSlots.toMutableList()
        updated.removeAt(slotIndex)

        val newFocused = when {
            updated.isEmpty() -> 0
            _uiState.value.focusedSlotIndex >= updated.size -> updated.size - 1
            else -> _uiState.value.focusedSlotIndex
        }

        _uiState.value = _uiState.value.copy(
            slots = updated,
            focusedSlotIndex = newFocused,
            audioSlotIndex = updated.indexOfFirst { it.slotId == audioSlotId }.takeIf { it >= 0 } ?: 0
        )
    }

    fun retrySlot(slotIndex: Int) {
        val currentSlots = _uiState.value.slots
        val target = currentSlots.getOrNull(slotIndex) ?: return

        val updated = currentSlots.toMutableList()
        updated[slotIndex] = target.copy(isLoading = true, error = null)
        _uiState.value = _uiState.value.copy(slots = updated)

        resolveIntoSlot(target.slotId) {
            val allChannels = _uiState.value.availableChannels.ifEmpty { iptvRepository.getChannels() }
            resolveSlot(
                event = target.event,
                channel = target.channel,
                fallbackChannelId = target.channel?.id,
                allChannels = allChannels
            )
        }
    }

    private fun resolveIntoSlot(slotId: String, resolver: suspend () -> MultiViewSlot) {
        slotJobs.remove(slotId)?.cancel()
        slotJobs[slotId] = viewModelScope.launch(ioDispatcher) {
            val resolved = resolver().copy(slotId = slotId)
            withContext(Dispatchers.Main) {
                val index = _uiState.value.slots.indexOfFirst { it.slotId == slotId }
                if (index >= 0) {
                    val updated = _uiState.value.slots.toMutableList()
                    updated[index] = resolved
                    _uiState.value = _uiState.value.copy(slots = updated)
                }
            }
        }
    }

    fun updateSlotSpecs(slotId: String, res: String?, fps: String?) {
        val currentSlots = _uiState.value.slots
        val index = currentSlots.indexOfFirst { it.slotId == slotId }
        if (index != -1) {
            val current = currentSlots[index]
            if (current.resolution != res || current.fps != fps) {
                val updated = currentSlots.toMutableList()
                updated[index] = current.copy(resolution = res, fps = fps)
                _uiState.value = _uiState.value.copy(slots = updated)
            }
        }
    }
}
