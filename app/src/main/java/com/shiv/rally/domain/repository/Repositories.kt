package com.shiv.rally.domain.repository

import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.LeagueHub
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.TeamHub
import kotlinx.coroutines.flow.Flow

interface SportsRepository {
    suspend fun getLiveEvents(): List<SportEvent>
    suspend fun getUpcomingEvents(): List<SportEvent>
    suspend fun getEventsSnapshot(): List<SportEvent> = getLiveEvents() + getUpcomingEvents()
    suspend fun getRecentEvents(): List<SportEvent> = getEventsSnapshot()
    suspend fun getEventsByLeague(league: String): List<SportEvent>
    suspend fun getEventById(eventId: String): SportEvent?
    suspend fun searchEvents(query: String): List<SportEvent>
    fun observeLiveEvent(eventId: String): Flow<SportEvent>
    suspend fun getTvStationsForEvent(eventId: String): List<String>
    suspend fun getEventSummary(event: SportEvent): SportEvent
    suspend fun getTeamsForLeague(league: String): List<FavoriteTeam> = emptyList()
    suspend fun getTeamHub(team: FavoriteTeam): TeamHub = TeamHub(team)
    suspend fun getLeagueHub(league: String): LeagueHub = LeagueHub(league, getEventsByLeague(league))
}

interface IptvRepository {
    suspend fun authenticate(): Boolean
    suspend fun getChannels(): List<IptvChannel>
    suspend fun refreshChannels(): List<IptvChannel> = getChannels()
    suspend fun searchChannels(query: String, limit: Int = 50): List<IptvChannel> =
        getChannels().asSequence()
            .filter { channel ->
                channel.name.contains(query, ignoreCase = true) ||
                    channel.category.contains(query, ignoreCase = true) ||
                    channel.number.contains(query, ignoreCase = true)
            }
            .take(limit)
            .toList()
    suspend fun getChannelStreamUrl(channelId: String): String
    suspend fun getChannelGuide(channelId: String): ChannelGuide? = null
    fun clearMemoryCache() = Unit
}

interface StremioRepository {
    suspend fun getStreamsForEvent(event: SportEvent): List<com.shiv.rally.domain.model.StremioStreamOption>
    suspend fun searchStreams(query: String): List<com.shiv.rally.domain.model.StremioStreamOption>
}
