package com.shiv.rally.presentation.iptv

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class IptvBrowserViewModel @Inject constructor(
    private val iptvRepository: IptvRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<IptvBrowserUiState>(IptvBrowserUiState.Loading)
    val uiState: StateFlow<IptvBrowserUiState> = _uiState.asStateFlow()

    private var allChannels: List<IptvChannel> = emptyList()
    private var allCategories: List<String> = emptyList()
    private var currentCategory: String = "All"
    private var currentSearch: String = ""
    private var filterJob: Job? = null
    private val guideJobs = ConcurrentHashMap<String, Job>()
    private val pendingGuides = mutableMapOf<String, ChannelGuide>()
    private var guideFlushJob: Job? = null

    init {
        loadChannels()
    }

    fun loadChannels() {
        loadChannels(forceRefresh = false)
    }

    fun retryChannels() {
        loadChannels(forceRefresh = true)
    }

    private fun loadChannels(forceRefresh: Boolean) {
        viewModelScope.launch {
            guideJobs.values.forEach(Job::cancel)
            guideJobs.clear()
            guideFlushJob?.cancel()
            pendingGuides.clear()
            _uiState.value = IptvBrowserUiState.Loading
            try {
                val (channels, categories, initialCat) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val loaded = if (forceRefresh) iptvRepository.refreshChannels() else iptvRepository.getChannels()
                    val rawCategories = loaded
                        .map { it.category.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()

                    val sortedCategories = mutableListOf("All")
                    val sportsCats = rawCategories.filter { it.contains("sport", ignoreCase = true) }
                    val nonSportsCats = rawCategories.filter { !it.contains("sport", ignoreCase = true) }
                    sortedCategories.addAll(sportsCats)
                    sortedCategories.addAll(nonSportsCats)
                    val firstSport = sportsCats.firstOrNull() ?: "All"
                    Triple(loaded, sortedCategories, firstSport)
                }

                allChannels = channels
                allCategories = categories
                currentCategory = initialCat

                updateFilteredState()
            } catch (e: Exception) {
                _uiState.value = IptvBrowserUiState.Error(e.message ?: "Failed to load channels")
            }
        }
    }

    fun selectCategory(category: String) {
        currentCategory = category
        updateFilteredState()
    }

    fun setSearchQuery(query: String) {
        currentSearch = query
        updateFilteredState()
    }

    private fun updateFilteredState() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val cat = currentCategory
            val search = currentSearch
            val channelsList = allChannels
            val filtered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                channelsList.filter { channel ->
                    val matchesCategory = if (cat == "All") {
                        true
                    } else {
                        channel.category.equals(cat, ignoreCase = true)
                    }
                    val matchesSearch = if (search.isBlank()) {
                        true
                    } else {
                        channel.name.contains(search, ignoreCase = true) ||
                        channel.number.contains(search, ignoreCase = true)
                    }
                    matchesCategory && matchesSearch
                }
            }

            _uiState.value = IptvBrowserUiState.Success(
                categories = allCategories,
                selectedCategory = cat,
                channels = filtered,
                searchQuery = search,
                totalChannelCount = channelsList.size
            )
        }
    }

    fun loadGuide(channelId: String) {
        val existing = allChannels.firstOrNull { it.id == channelId } ?: return
        if (existing.guide != null || guideJobs[channelId]?.isActive == true) return
        guideJobs[channelId] = viewModelScope.launch {
            try {
                val guide = withContext(Dispatchers.IO) {
                    runCatching { iptvRepository.getChannelGuide(channelId) }.getOrNull()
                } ?: return@launch
                pendingGuides[channelId] = guide
                if (guideFlushJob?.isActive != true) {
                    guideFlushJob = viewModelScope.launch {
                        delay(150L)
                        val completed = pendingGuides.toMap()
                        pendingGuides.clear()
                        if (completed.isNotEmpty()) {
                            allChannels = allChannels.map { channel ->
                                completed[channel.id]?.let { channel.copy(guide = it) } ?: channel
                            }
                            updateFilteredState()
                        }
                    }
                }
            } finally {
                guideJobs.remove(channelId)
            }
        }
    }
}

sealed class IptvBrowserUiState {
    object Loading : IptvBrowserUiState()
    @Immutable
    data class Success(
        val categories: List<String>,
        val selectedCategory: String,
        val channels: List<IptvChannel>,
        val searchQuery: String = "",
        val totalChannelCount: Int
    ) : IptvBrowserUiState()
    data class Error(val message: String) : IptvBrowserUiState()
}
