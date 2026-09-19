package com.shiv.rally.presentation.home

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.data.alerts.GameAlertManager
import com.shiv.rally.domain.model.GameAlert
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.repository.SportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sportsRepository: SportsRepository,
    private val iptvRepository: com.shiv.rally.domain.repository.IptvRepository,
    private val preferencesManager: PreferencesManager,
    private val gameAlertManager: GameAlertManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var refreshJob: Job? = null
    private var redZoneJob: Job? = null
    private var lastEvents: List<SportEvent> = emptyList()
    private var appliedSportsOrder = preferencesManager.sportsOrder
    private var appliedEnabledLeagues = preferencesManager.enabledLeagues
    private val _alerts = MutableSharedFlow<GameAlert>(extraBufferCapacity = 8)
    val alerts = _alerts.asSharedFlow()

    init {
        loadData()
    }

    /** Keep score polling active only while Home is actually on screen. */
    fun setActive(active: Boolean) {
        if (!active) {
            refreshJob?.cancel()
            refreshJob = null
            return
        }
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                loadData()
            }
        }
    }

    private fun loadData(force: Boolean = false) {
        if (force) {
            loadJob?.cancel()
        } else if (loadJob?.isActive == true) {
            return
        }
        loadJob = viewModelScope.launch {
            try {
                val (successState, loadedEvents) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val allEvents = sportsRepository.getRecentEvents()
                    val liveEvents = allEvents.filter {
                        it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME
                    }
                    val upcomingEvents = allEvents.filter { it.status == EventStatus.NOT_STARTED }

                    val favoriteTeams = preferencesManager.favoriteTeams.map { it.lowercase().trim() }
                    val favoriteProfiles = preferencesManager.favoriteTeamProfiles.toMutableList()
                    val favoriteSports = preferencesManager.favoriteSports.map { it.uppercase().trim() }
                    val recentLeagues = preferencesManager.recentLeagues
                    val sportsOrder = preferencesManager.sportsOrder

                    fun isFavoriteTeam(event: SportEvent): Boolean {
                        if (favoriteTeams.isEmpty() && favoriteProfiles.isEmpty()) return false
                        val home = event.homeTeam?.name?.lowercase() ?: ""
                        val away = event.awayTeam?.name?.lowercase() ?: ""
                        return favoriteTeams.any { fav -> home.contains(fav) || away.contains(fav) } ||
                            favoriteProfiles.any { profile ->
                                event.homeTeam?.id == profile.id || event.awayTeam?.id == profile.id
                            }
                    }

                    // Migrate legacy name-only favorites as matching teams appear in schedules.
                    allEvents.flatMap { event -> listOfNotNull(event.homeTeam, event.awayTeam).map { it to event.league } }
                        .forEach { (team, league) ->
                            val legacyMatch = favoriteTeams.any { favorite ->
                                team.name.lowercase().contains(favorite) || favorite.contains(team.name.lowercase())
                            }
                            if (legacyMatch && favoriteProfiles.none { it.id == team.id && it.league.equals(league, true) }) {
                                favoriteProfiles += FavoriteTeam(
                                    id = team.id,
                                    league = league,
                                    name = team.name,
                                    abbreviation = team.abbreviation,
                                    logoUrl = team.logoUrl,
                                    colors = team.colors
                                )
                            }
                        }
                    if (favoriteProfiles != preferencesManager.favoriteTeamProfiles) {
                        preferencesManager.favoriteTeamProfiles = favoriteProfiles
                    }

                    fun isFavoriteSport(event: SportEvent): Boolean {
                        if (favoriteSports.isEmpty()) return false
                        val league = event.league.uppercase()
                        val sport = event.sport.uppercase()
                        return favoriteSports.contains(league) || favoriteSports.contains(sport)
                    }

                    fun recentInterestRank(event: SportEvent): Int = recentLeagues.indexOfFirst {
                        it.equals(event.league, true)
                    }.let { if (it < 0) Int.MAX_VALUE else it }

                    // Prioritize live events: favorite team first, then favorite sport, then others
                    val prioritizedLiveEvents = liveEvents.sortedWith(
                        compareByDescending<SportEvent> { isFavoriteTeam(it) }
                            .thenByDescending { isFavoriteSport(it) }
                            .thenBy { recentInterestRank(it) }
                    )

                    // Editorial hero selection: the home screen behaves like live
                    // programming, not a permanently pinned first result.
                    val majorLeagues = setOf("NFL", "NCAAF", "NBA", "NCAAB", "MLB", "NHL", "EPL", "La Liga", "Champions League", "Serie A")
                    val now = Instant.now()
                    val twoHoursFromNow = now.plus(2, ChronoUnit.HOURS)
                    val startingSoon = upcomingEvents.filter {
                        it.status == EventStatus.NOT_STARTED &&
                        it.startTime.isAfter(now) &&
                        it.startTime.isBefore(twoHoursFromNow)
                    }
                    val closeGames = liveEvents.filter(::isEditoriallyClose)
                    val selectedLive = liveEvents.maxByOrNull { event ->
                        (if (isFavoriteTeam(event)) 500 else 0) +
                            (if (isEditoriallyClose(event)) 260 else 0) +
                            (if (isLateGame(event)) 130 else 0) +
                            (if (isFavoriteSport(event)) 80 else 0) +
                            (if (event.league in majorLeagues) 30 else 0) -
                            recentInterestRank(event).coerceAtMost(20)
                    }
                    val startingSoonHero = startingSoon.minWithOrNull(
                        compareByDescending<SportEvent> { isFavoriteTeam(it) }
                            .thenByDescending { isFavoriteSport(it) }
                            .thenByDescending { it.league in majorLeagues }
                            .thenBy { it.startTime }
                    )
                    val recentFinal = allEvents
                        .asSequence()
                        .filter { it.status == EventStatus.FINISHED }
                        .filter { it.startTime.isAfter(now.minus(12, ChronoUnit.HOURS)) }
                        .sortedWith(compareByDescending<SportEvent> { isFavoriteTeam(it) }.thenByDescending { it.startTime })
                        .firstOrNull()
                    val ordinaryUpcoming = upcomingEvents.firstOrNull { isFavoriteTeam(it) }
                        ?: upcomingEvents.firstOrNull { isFavoriteSport(it) }
                        ?: upcomingEvents.minByOrNull(::recentInterestRank)?.takeIf { recentInterestRank(it) != Int.MAX_VALUE }
                        ?: upcomingEvents.firstOrNull { it.league in majorLeagues }
                        ?: upcomingEvents.firstOrNull()

                    val baseFeatured = selectedLive ?: startingSoonHero ?: recentFinal ?: ordinaryUpcoming
                    val heroMode = when {
                        selectedLive != null && selectedLive in closeGames -> HomeHeroMode.CLOSE_GAME
                        selectedLive != null -> HomeHeroMode.LIVE
                        startingSoonHero != null -> HomeHeroMode.STARTING_SOON
                        recentFinal != null -> HomeHeroMode.FINAL_RECAP
                        ordinaryUpcoming != null -> HomeHeroMode.UPCOMING
                        else -> HomeHeroMode.EMPTY
                    }
                    val featured = if (heroMode == HomeHeroMode.FINAL_RECAP && baseFeatured != null) {
                        runCatching { sportsRepository.getEventSummary(baseFeatured) }.getOrDefault(baseFeatured)
                    } else baseFeatured
                    val favoriteEvents = allEvents.filter(::isFavoriteTeam).sortedWith(
                        compareByDescending<SportEvent> { it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME }
                            .thenBy { it.startTime }
                    )

                    // Group events by league for per-sport shelves
                    val enabledLeagues = preferencesManager.enabledLeagues
                    val groupedEvents = allEvents
                        .groupBy { it.league }
                        .filter { enabledLeagues.isEmpty() || it.key in enabledLeagues }
                        .filter { it.value.isNotEmpty() }

                    // Keep enabled league tabs visible even on days with no games.
                    val orderedEnabledLeagues = sportsOrder.filter {
                        enabledLeagues.isEmpty() || it in enabledLeagues
                    }
                    val leagueShelves = orderedEnabledLeagues.map { league ->
                        EventShelfData(league, groupedEvents[league].orEmpty().sortedBy { it.startTime })
                    } + groupedEvents.filterKeys { it !in orderedEnabledLeagues }.map { (league, events) ->
                            EventShelfData(league, events.sortedBy { it.startTime })
                        }

                    HomeUiState.Success(
                        featuredEvent = featured,
                        heroMode = heroMode,
                        liveEvents = prioritizedLiveEvents,
                        startingSoon = startingSoon,
                        upcomingEvents = upcomingEvents.take(20),
                        leagueShelves = leagueShelves,
                        favoriteTeams = favoriteProfiles.distinctBy { "${it.league}:${it.id}" },
                        favoriteEvents = favoriteEvents,
                        redZoneChannelId = (_uiState.value as? HomeUiState.Success)?.redZoneChannelId
                    ) to allEvents
                }

                _uiState.value = successState
                val favoriteIds = successState.favoriteTeams.mapTo(mutableSetOf()) { it.id }
                if (preferencesManager.liveGameAlertsEnabled) {
                    gameAlertManager.evaluate(lastEvents, loadedEvents, favoriteIds).forEach { _alerts.tryEmit(it) }
                }
                lastEvents = loadedEvents
                loadRedZoneInBackground()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refresh() {
        _uiState.value = HomeUiState.Loading
        loadData(force = true)
    }

    /** Refreshes preference-backed shelves without replacing the dashboard with a loading screen. */
    fun refreshForPreferences() {
        val currentOrder = preferencesManager.sportsOrder
        val currentEnabled = preferencesManager.enabledLeagues
        if (currentOrder == appliedSportsOrder && currentEnabled == appliedEnabledLeagues) return
        appliedSportsOrder = currentOrder
        appliedEnabledLeagues = currentEnabled
        loadData(force = true)
    }

    private fun loadRedZoneInBackground() {
        if (redZoneJob?.isActive == true) return
        redZoneJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val redZoneId = try {
                val channels = iptvRepository.searchChannels("redzone", limit = 20)
                channels.firstOrNull { channel ->
                    val name = channel.name.lowercase()
                    name.contains("nfl") && (name.contains("redzone") || name.contains("red zone"))
                }?.id ?: channels.firstOrNull { channel ->
                    val name = channel.name.lowercase()
                    name.contains("redzone") || name.contains("red zone")
                }?.id
            } catch (_: Exception) { null }

            if (redZoneId != null) {
                val guide = runCatching { iptvRepository.getChannelGuide(redZoneId) }.getOrNull()
                guide?.now?.title?.takeIf { title ->
                    preferencesManager.redZoneAlertsEnabled && (
                        title.contains("redzone", true) ||
                            title.contains("red zone", true) ||
                            title.contains("live", true)
                        )
                }?.let { program ->
                    gameAlertManager.redZoneAlert(redZoneId, program)?.let { _alerts.tryEmit(it) }
                }
                val current = _uiState.value
                if (current is HomeUiState.Success && current.redZoneChannelId != redZoneId) {
                    _uiState.value = current.copy(redZoneChannelId = redZoneId)
                }
            }
        }
    }
}

@Immutable
data class EventShelfData(
    val title: String,
    val events: List<SportEvent>
)

enum class HomeHeroMode { LIVE, CLOSE_GAME, STARTING_SOON, FINAL_RECAP, UPCOMING, EMPTY }

internal fun isEditoriallyClose(event: SportEvent): Boolean {
    val away = event.scoreAway ?: return false
    val home = event.scoreHome ?: return false
    val margin = kotlin.math.abs(away - home)
    return when {
        event.sport.contains("basket", true) || event.league.contains("NBA", true) || event.league.contains("NCAAB", true) -> margin <= 8
        event.sport.contains("football", true) || event.league.contains("NFL", true) || event.league.contains("NCAAF", true) -> margin <= 8
        else -> margin <= 2
    }
}

private fun isLateGame(event: SportEvent): Boolean {
    val status = event.gameStatusDetail.orEmpty().lowercase()
    return listOf("4th", "overtime", " ot", "9th", "extra", "80'", "85'", "90'").any(status::contains)
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    @Immutable
    data class Success(
        val featuredEvent: SportEvent?,
        val heroMode: HomeHeroMode = HomeHeroMode.EMPTY,
        val liveEvents: List<SportEvent>,
        val startingSoon: List<SportEvent>,
        val upcomingEvents: List<SportEvent>,
        val leagueShelves: List<EventShelfData>,
        val favoriteTeams: List<FavoriteTeam> = emptyList(),
        val favoriteEvents: List<SportEvent> = emptyList(),
        val redZoneChannelId: String? = null
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
