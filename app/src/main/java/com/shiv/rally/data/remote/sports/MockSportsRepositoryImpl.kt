package com.shiv.rally.data.remote.sports

import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.Team
import com.shiv.rally.domain.repository.SportsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class MockSportsRepositoryImpl @Inject constructor() : SportsRepository {

    override suspend fun getTvStationsForEvent(eventId: String): List<String> {
        return emptyList()
    }

    override suspend fun getEventSummary(event: SportEvent): SportEvent {
        return event
    }

    private val lakers = Team("1", "Los Angeles Lakers", "LAL", colors = listOf("#552583", "#FDB927"))
    private val warriors = Team("2", "Golden State Warriors", "GSW", colors = listOf("#1D428A", "#FFC72C"))
    private val chiefs = Team("3", "Kansas City Chiefs", "KC", colors = listOf("#E31837", "#FFB81C"))
    private val eagles = Team("4", "Philadelphia Eagles", "PHI", colors = listOf("#004C54", "#A5ACAF"))

    private val dummyEvents = listOf(
        SportEvent(
            id = "e1",
            name = "Lakers vs Warriors",
            homeTeam = lakers,
            awayTeam = warriors,
            startTime = Instant.now().minus(1, ChronoUnit.HOURS),
            status = EventStatus.LIVE,
            scoreHome = 102,
            scoreAway = 98,
            sport = "Basketball",
            league = "NBA",
            liveStats = mapOf("Quarter" to "4th", "Time Remaining" to "02:15")
        ),
        SportEvent(
            id = "e2",
            name = "Chiefs vs Eagles",
            homeTeam = chiefs,
            awayTeam = eagles,
            startTime = Instant.now().plus(2, ChronoUnit.HOURS),
            status = EventStatus.NOT_STARTED,
            sport = "Football",
            league = "NFL"
        )
    )

    override suspend fun getLiveEvents(): List<SportEvent> {
        return dummyEvents.filter { it.status == EventStatus.LIVE }
    }

    override suspend fun getUpcomingEvents(): List<SportEvent> {
        return dummyEvents.filter { it.status == EventStatus.NOT_STARTED }
    }

    override suspend fun getEventsByLeague(league: String): List<SportEvent> {
        return dummyEvents.filter { it.league.equals(league, ignoreCase = true) }
    }

    override suspend fun getEventById(eventId: String): SportEvent? {
        return dummyEvents.find { it.id == eventId }
    }

    override suspend fun searchEvents(query: String): List<SportEvent> {
        return dummyEvents.filter { 
            it.name.contains(query, ignoreCase = true) || 
            it.homeTeam?.name?.contains(query, ignoreCase = true) == true ||
            it.awayTeam?.name?.contains(query, ignoreCase = true) == true
        }
    }

    override fun observeLiveEvent(eventId: String): Flow<SportEvent> = flow {
        val event = dummyEvents.find { it.id == eventId }
        if (event != null) {
            emit(event)
        }
    }
}
