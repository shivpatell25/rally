package com.shiv.rally.domain.usecase

import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.EpgProgram
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.MatchResult
import com.shiv.rally.domain.model.RelevantChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StreamSourceKind
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.model.Team
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.domain.repository.StremioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SelectBestStreamUseCaseTest {
    private val event = SportEvent(
        id = "game-1",
        name = "Ravens at Chiefs",
        homeTeam = Team("kc", "Kansas City Chiefs", "KC"),
        awayTeam = Team("bal", "Baltimore Ravens", "BAL"),
        startTime = Instant.EPOCH,
        status = EventStatus.LIVE,
        sport = "Football",
        league = "NFL"
    )

    @Test
    fun `stremio wins an equal or higher quality matchup`() = runTest {
        val iptv = channel("espn", "ESPN HD")
        val selection = resolver(
            channels = listOf(iptv),
            guides = mapOf(iptv.id to guide("Baltimore Ravens at Kansas City Chiefs")),
            matches = listOf(RelevantChannel(iptv, 99f)),
            streams = listOf(stream("4K HDR Game Feed"))
        )(event)

        assertEquals(StreamSourceKind.STREMIO, selection.primary?.sourceKind)
        assertTrue(selection.primary?.quality?.is4K == true)
    }

    @Test
    fun `iptv wins only when its exact game feed is higher quality`() = runTest {
        val iptv = channel("game", "Ravens vs Chiefs 4K HDR")
        val selection = resolver(
            channels = listOf(iptv),
            guides = mapOf(iptv.id to guide("Baltimore Ravens at Kansas City Chiefs")),
            matches = listOf(RelevantChannel(iptv, 98f)),
            streams = listOf(stream("1080p Addon Feed"))
        )(event)

        assertEquals(StreamSourceKind.IPTV, selection.primary?.sourceKind)
        assertEquals("game", selection.primary?.playbackTarget)
    }

    @Test
    fun `high quality iptv with the wrong program is not auto selected`() = runTest {
        val iptv = channel("sports", "Premium Sports 4K HDR")
        val selection = resolver(
            channels = listOf(iptv),
            guides = mapOf(iptv.id to guide("New York Yankees at Boston Red Sox")),
            matches = listOf(RelevantChannel(iptv, 96f)),
            streams = listOf(stream("1080p Addon Feed"))
        )(event)

        assertEquals(StreamSourceKind.STREMIO, selection.primary?.sourceKind)
        assertFalse(selection.candidates.first { it.sourceKind == StreamSourceKind.IPTV }.exactGameMatch)
    }

    @Test
    fun `redzone is never treated as an individual game feed`() = runTest {
        val redZone = channel("rz", "NFL RedZone 4K")
        val selection = resolver(
            channels = listOf(redZone),
            guides = mapOf(redZone.id to guide("Baltimore Ravens at Kansas City Chiefs")),
            matches = listOf(RelevantChannel(redZone, 100f)),
            streams = emptyList()
        )(event)

        assertEquals(null, selection.primary)
        assertFalse(selection.candidates.single().exactGameMatch)
    }

    @Test
    fun `browser-only addon pages remain visible but are never auto selected`() = runTest {
        val browserPage = StremioStreamOption(
            title = "Web Stream",
            streamUrl = "https://example.test/watch?embed=game",
            isDirectPlayable = false
        )
        val selection = resolver(
            channels = emptyList(),
            guides = emptyMap(),
            matches = emptyList(),
            streams = listOf(browserPage)
        )(event)

        assertEquals(null, selection.primary)
        assertTrue(selection.candidates.isEmpty())
        assertEquals(listOf(browserPage), selection.stremioStreams)
    }

    @Test
    fun `event matching accepts team nicknames and abbreviations`() {
        assertTrue(textMatchesEvent("BAL Ravens vs KC Chiefs", event))
        assertFalse(textMatchesEvent("Bills vs Dolphins", event))
    }

    private fun resolver(
        channels: List<IptvChannel>,
        guides: Map<String, ChannelGuide>,
        matches: List<RelevantChannel>,
        streams: List<StremioStreamOption>
    ) = SelectBestStreamUseCase(
        iptvRepository = object : IptvRepository {
            override suspend fun authenticate() = true
            override suspend fun getChannels() = channels
            override suspend fun getChannelStreamUrl(channelId: String) = "https://example.test/$channelId.m3u8"
            override suspend fun getChannelGuide(channelId: String) = guides[channelId]
        },
        stremioRepository = object : StremioRepository {
            override suspend fun getStreamsForEvent(event: SportEvent) = streams
            override suspend fun searchStreams(query: String) = emptyList<StremioStreamOption>()
        },
        matcherService = object : MatcherService {
            override suspend fun matchEventToChannels(event: SportEvent, channels: List<IptvChannel>) = emptyList<MatchResult>()
            override suspend fun getRelevantChannelsForEvent(event: SportEvent, channels: List<IptvChannel>) = matches
        }
    )

    private fun channel(id: String, name: String) = IptvChannel(id, "1", name, "Sports")
    private fun guide(title: String) = ChannelGuide(now = EpgProgram(title))
    private fun stream(title: String) = StremioStreamOption(title = title, streamUrl = "https://example.test/$title.m3u8")
}
