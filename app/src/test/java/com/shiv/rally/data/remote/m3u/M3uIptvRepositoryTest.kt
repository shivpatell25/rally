package com.shiv.rally.data.remote.m3u

import com.shiv.rally.data.local.PreferencesManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class M3uIptvRepositoryTest {
    private val preferences = mockk<PreferencesManager>(relaxed = true)
    private val source = mockk<M3uPlaylistSource>()
    private val url = "https://provider.example/playlist.m3u"
    private val channels = M3uPlaylistParser.parse("#EXTM3U\n#EXTINF:-1 group-title=Sports,ESPN\n#EXTVLCOPT:http-user-agent=Provider\nhttps://cdn.example/espn.m3u8", url)

    private fun repository(): M3uIptvRepository {
        every { preferences.m3uPlaylistUrl } returns url
        every { preferences.m3uPlaylistName } returns "My Playlist"
        every { source.load(url, "My Playlist") } returns channels
        return M3uIptvRepository(preferences, source)
    }

    @Test fun `catalog browsing search and playback use cached parsed channels`() = runTest {
        val repository = repository()
        assertTrue(repository.authenticate())
        assertEquals(channels, repository.getChannels())
        assertEquals(channels, repository.searchChannels("sports"))
        assertEquals("https://cdn.example/espn.m3u8", repository.getChannelStreamUrl(channels.single().id))
        assertEquals("Provider", repository.getChannelStreamHeaders(channels.single().id)["User-Agent"])
        verify(exactly = 1) { source.load(url, "My Playlist") }
        repository.refreshChannels()
        verify(exactly = 2) { source.load(url, "My Playlist") }
    }

    @Test fun `changing playlist identity invalidates the old catalog`() = runTest {
        val repository = repository()
        repository.getChannels()
        every { preferences.m3uPlaylistUrl } returns "$url?new=1"
        every { source.load("$url?new=1", "My Playlist") } returns emptyList()
        assertTrue(repository.getChannels().isEmpty())
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { repository.getChannelStreamUrl(channels.single().id) }
        }
    }

    @Test fun `unconfigured playlist never contacts a server`() = runTest {
        val repository = repository()
        every { preferences.m3uPlaylistUrl } returns ""
        assertFalse(repository.authenticate())
        assertTrue(repository.getChannels().isEmpty())
        verify(exactly = 0) { source.load(any(), any()) }
    }
}
