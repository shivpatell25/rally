package com.shiv.rally.domain.usecase

import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.Team
import com.shiv.rally.domain.repository.SportsRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MatchEventToStreamUseCaseTest {

    private val fakeSportsRepository = object : SportsRepository {
        override suspend fun getLiveEvents(): List<SportEvent> = emptyList()
        override suspend fun getUpcomingEvents(): List<SportEvent> = emptyList()
        override suspend fun getEventsByLeague(league: String): List<SportEvent> = emptyList()
        override suspend fun getEventById(eventId: String): SportEvent? = null
        override suspend fun searchEvents(query: String): List<SportEvent> = emptyList()
        override fun observeLiveEvent(eventId: String) = emptyFlow<SportEvent>()
        override suspend fun getTvStationsForEvent(eventId: String): List<String> {
            return if (eventId == "nfl_game_1") listOf("FOX") else emptyList()
        }
        override suspend fun getEventSummary(event: SportEvent): SportEvent = event
    }

    private val matcher = MatchEventToStreamUseCase(fakeSportsRepository)

    @Test
    fun matchesEventWithTeamAndLeague() = runTest {
        val lakers = Team("1", "Los Angeles Lakers", "LAL")
        val warriors = Team("2", "Golden State Warriors", "GSW")
        val event = SportEvent(
            id = "e1",
            name = "Lakers vs Warriors",
            homeTeam = lakers,
            awayTeam = warriors,
            startTime = Instant.now(),
            status = EventStatus.LIVE,
            sport = "Basketball",
            league = "NBA"
        )

        val channels = listOf(
            IptvChannel("c1", "101", "US | NBA TV HD", "Sports"),
            IptvChannel("c2", "102", "US | Lakers vs Warriors Live", "Sports"),
            IptvChannel("c3", "103", "UK | Sky News", "News")
        )

        val results = matcher.matchEventToChannels(event, channels)

        assertTrue(results.isNotEmpty())
        assertEquals("c2", results.first().iptvChannel.id)
        assertTrue(results.first().confidenceScore >= 0.8f)
    }

    @Test
    fun `getRelevantChannelsForEvent filters out non-sports channels`() = runTest {
        val seahawks = Team("1", "Seattle Seahawks", "SEA")
        val patriots = Team("2", "New England Patriots", "NE")
        val event = SportEvent(
            id = "nfl_game_1",
            name = "Seahawks vs Patriots",
            homeTeam = seahawks,
            awayTeam = patriots,
            startTime = Instant.now(),
            status = EventStatus.LIVE,
            sport = "Football",
            league = "NFL"
        )

        val channels = listOf(
            IptvChannel("c1", "1", "CNN International", "News"),
            IptvChannel("c2", "2", "Cartoon Network HD", "Kids"),
            IptvChannel("c3", "3", "HBO Max 1", "Movies"),
            IptvChannel("c4", "4", "US | FOX 4K", "US | Sports"),
            IptvChannel("c5", "5", "US | ESPN HD", "US | Sports"),
            IptvChannel("c6", "6", "Turkish Music Top 40", "Music")
        )

        val relevant = matcher.getRelevantChannelsForEvent(event, channels)

        // Only FOX and ESPN should be kept; news, kids, movies, music must be filtered out
        assertEquals(2, relevant.size)
        assertTrue(relevant.any { it.channel.id == "c4" })
        assertTrue(relevant.any { it.channel.id == "c5" })
        assertFalse(relevant.any { it.channel.id == "c1" })
        assertFalse(relevant.any { it.channel.id == "c2" })
        assertFalse(relevant.any { it.channel.id == "c3" })
        assertFalse(relevant.any { it.channel.id == "c6" })
    }

    @Test
    fun `getRelevantChannelsForEvent sorts official ESPN broadcast station and league channels first`() = runTest {
        val seahawks = Team("1", "Seattle Seahawks", "SEA")
        val patriots = Team("2", "New England Patriots", "NE")
        val event = SportEvent(
            id = "nfl_game_1", // ESPN returns TV station: ["FOX"]
            name = "Seahawks vs Patriots",
            homeTeam = seahawks,
            awayTeam = patriots,
            startTime = Instant.now(),
            status = EventStatus.LIVE,
            sport = "Football",
            league = "NFL"
        )

        val channels = listOf(
            IptvChannel("c_espn", "10", "US | ESPN HD", "Sports"),
            IptvChannel("c_fox", "20", "US | FOX 4K", "Sports"),
            IptvChannel("c_nfl", "30", "US | NFL NETWORK HD", "Sports"),
            IptvChannel("c_golf", "40", "US | GOLF CHANNEL", "Sports"),
            IptvChannel("c_match", "50", "US | Seahawks vs Patriots Live Feed", "Sports")
        )

        val relevant = matcher.getRelevantChannelsForEvent(event, channels)

        assertEquals(5, relevant.size)
        // 1st: Direct Matchup
        assertEquals("c_match", relevant[0].channel.id)
        assertEquals("Game Matchup", relevant[0].matchBadge)

        // 2nd: Official Broadcast (FOX from ESPN API)
        assertEquals("c_fox", relevant[1].channel.id)
        assertTrue(relevant[1].isOfficialBroadcast)
        assertEquals("Official Broadcast: FOX", relevant[1].matchBadge)

        // 3rd: Dedicated League Channel (NFL Network)
        assertEquals("c_nfl", relevant[2].channel.id)
        assertEquals("NFL Network", relevant[2].matchBadge)

        // 4th: Major Sports Network (ESPN)
        assertEquals("c_espn", relevant[3].channel.id)
        assertEquals("Sports Network", relevant[3].matchBadge)

        // 5th: General Sports Channel (Golf)
        assertEquals("c_golf", relevant[4].channel.id)
    }

    @Test
    fun `getRelevantChannelsForEvent excludes cross-sport matchup false positives`() = runTest {
        val seahawks = Team("1", "Seattle Seahawks", "SEA")
        val patriots = Team("2", "New England Patriots", "NE")
        val event = SportEvent(
            id = "nfl_game_1", // ESPN returns TV station: ["FOX"]
            name = "Seahawks vs Patriots",
            homeTeam = seahawks,
            awayTeam = patriots,
            startTime = Instant.now(),
            status = EventStatus.LIVE,
            sport = "Football",
            league = "NFL"
        )

        val channels = listOf(
            IptvChannel("c_fox_sports", "1", "US | FOX SPORTS 1 HD", "Sports"),
            IptvChannel("c_milb_patriots", "2", "MILB 12 : ERIE SEAWOLVES VS SOMERSET PATRIOTS", "Sports | MLB"),
            IptvChannel("c_milb_seadogs", "3", "MILB 23 : CHESAPEAKE BAYSOX VS PORTLAND SEA DOGS", "Sports | MLB"),
            IptvChannel("c_seahawks_feed", "4", "NFL | SEAHAWKS", "Sports | NFL")
        )

        val relevant = matcher.getRelevantChannelsForEvent(event, channels)

        // 1st should be official broadcast FOX
        assertEquals("c_fox_sports", relevant[0].channel.id)
        assertTrue(relevant[0].isOfficialBroadcast)

        // 2nd should be NFL Seahawks feed
        assertEquals("c_seahawks_feed", relevant[1].channel.id)
        assertEquals("Seattle Seahawks Feed", relevant[1].matchBadge)

        // MILB channels must NOT have "Game Matchup" or be ranked at the top
        val milbPatriots = relevant.first { it.channel.id == "c_milb_patriots" }
        assertFalse(milbPatriots.matchBadge == "Game Matchup")
        assertTrue(relevant.indexOf(milbPatriots) > 1)
    }

    @Test
    fun `parseQualityFromChannelName correctly extracts resolution and fps`() {
        val q1 = com.shiv.rally.domain.model.parseQualityFromChannelName("NBC SPORTS 4K 60FPS")
        assertEquals("4K", q1.resolution)
        assertEquals("60 fps", q1.fps)
        assertTrue(q1.is4K)
        assertTrue(q1.is60Fps)

        val q2 = com.shiv.rally.domain.model.parseQualityFromChannelName("SKY SPORTS MAIN EVENT FHD 50FPS")
        assertEquals("1080p", q2.resolution)
        assertEquals("50 fps", q2.fps)

        val q3 = com.shiv.rally.domain.model.parseQualityFromChannelName("US | ABC 720P")
        assertEquals("720p", q3.resolution)
        assertFalse(q3.is4K)

        val q4 = com.shiv.rally.domain.model.parseQualityFromChannelName("US | ESPN HD")
        assertEquals("HD", q4.resolution)

        val q5 = com.shiv.rally.domain.model.parseQualityFromChannelName("GENERIC CHANNEL NAME")
        assertEquals(null, q5.resolution)
        assertEquals(null, q5.fps)
    }

    @Test
    fun `getRelevantChannelsForEvent filters out replay classic rewind offline channels`() = runTest {
        val yankees = Team("1", "New York Yankees", "NYY")
        val redsox = Team("2", "Boston Red Sox", "BOS")
        val event = SportEvent(
            id = "mlb_1",
            name = "Yankees vs Red Sox",
            homeTeam = yankees,
            awayTeam = redsox,
            startTime = Instant.now(),
            status = EventStatus.LIVE,
            sport = "Baseball",
            league = "MLB"
        )

        val channels = listOf(
            IptvChannel("c1", "1", "YES NETWORK HD", "Sports | MLB"),
            IptvChannel("c2", "2", "MLB CLASSIC REPLAY 1998", "Sports | MLB"),
            IptvChannel("c3", "3", "MLB REWIND VAULT", "Sports | MLB"),
            IptvChannel("c4", "4", "NESN BOSTON [OFFLINE]", "Sports | MLB")
        )

        val relevant = matcher.getRelevantChannelsForEvent(event, channels)

        assertEquals(1, relevant.size)
        assertEquals("c1", relevant[0].channel.id)
    }

    @Test
    fun `getRelevantChannelsForEvent ranks RSN and out-of-market package channels highly`() = runTest {
        val yankees = Team("1", "New York Yankees", "NYY")
        val redsox = Team("2", "Boston Red Sox", "BOS")
        val event = SportEvent(
            id = "mlb_1",
            name = "Yankees vs Red Sox",
            homeTeam = yankees,
            awayTeam = redsox,
            startTime = Instant.now(),
            status = EventStatus.LIVE,
            sport = "Baseball",
            league = "MLB"
        )

        val channels = listOf(
            IptvChannel("c_generic", "1", "GENERAL SPORTS 1", "Sports"),
            IptvChannel("c_nesn", "2", "US | NESN BOSTON RED SOX HD", "Sports | MLB"),
            IptvChannel("c_mlb_ei", "3", "MLB EXTRA INNINGS 04 HD", "Sports | MLB"),
            IptvChannel("c_yes", "4", "US | YES NETWORK NY YANKEES 4K", "Sports | MLB")
        )

        val relevant = matcher.getRelevantChannelsForEvent(event, channels)

        // YES and NESN are team RSNs, MLB EI is league package
        assertTrue(relevant.any { it.channel.id == "c_yes" })
        assertTrue(relevant.any { it.channel.id == "c_nesn" })
        assertTrue(relevant.any { it.channel.id == "c_mlb_ei" })

        val yesItem = relevant.first { it.channel.id == "c_yes" }
        val nesnItem = relevant.first { it.channel.id == "c_nesn" }
        val genericItem = relevant.first { it.channel.id == "c_generic" }

        assertTrue(yesItem.likelihoodScore > genericItem.likelihoodScore)
        assertTrue(nesnItem.likelihoodScore > genericItem.likelihoodScore)
    }

    @Test
    fun `getRelevantChannelsForEvent resolves station aliases like SECN and ranks official broadcast first`() = runTest {
        val mizzouRepo = object : SportsRepository {
            override suspend fun getLiveEvents(): List<SportEvent> = emptyList()
            override suspend fun getUpcomingEvents(): List<SportEvent> = emptyList()
            override suspend fun getEventsByLeague(league: String): List<SportEvent> = emptyList()
            override suspend fun getEventById(eventId: String): SportEvent? = null
            override suspend fun searchEvents(query: String): List<SportEvent> = emptyList()
            override fun observeLiveEvent(eventId: String) = emptyFlow<SportEvent>()
            override suspend fun getTvStationsForEvent(eventId: String): List<String> {
                return listOf("SECN") // ESPN gives SECN
            }
            override suspend fun getEventSummary(event: SportEvent): SportEvent = event
        }

        val collegeMatcher = MatchEventToStreamUseCase(mizzouRepo)

        val mizzou = Team("1", "Missouri Tigers", "MIZZOU")
        val uapb = Team("2", "Arkansas-Pine Bluff Golden Lions", "UAPB")
        val event = SportEvent(
            id = "college_game_1",
            name = "Arkansas-Pine Bluff vs Missouri",
            homeTeam = mizzou,
            awayTeam = uapb,
            startTime = Instant.now(),
            status = EventStatus.LIVE,
            sport = "Football",
            league = "NCAAF"
        )

        val channels = listOf(
            IptvChannel("c_secn", "10", "US | SEC NETWORK HD", "US Entertainment"), // In non-sports category!
            IptvChannel("c_random", "20", "RANDOM MOVIE CHANNEL", "Movies"),
            IptvChannel("c_espn", "30", "US | ESPN HD", "Sports")
        )

        val relevant = collegeMatcher.getRelevantChannelsForEvent(event, channels)

        // SEC Network must match official broadcast SECN despite category being "US Entertainment"
        assertEquals(2, relevant.size)
        val secItem = relevant.first { it.channel.id == "c_secn" }
        assertTrue(secItem.isOfficialBroadcast)
        assertTrue(secItem.likelihoodScore >= 85.0f)
        assertEquals("c_secn", relevant.first().channel.id) // First/top recommendation!
    }
}


