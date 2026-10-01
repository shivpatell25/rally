package com.shiv.rally.data.remote.m3u

import org.junit.Assert.*
import org.junit.Test

class M3uPlaylistParserTest {
    private val source = "https://provider.example/lists/channels.m3u8?token=private"

    @Test fun `extended UTF8 catalog preserves titles groups logos and numbers`() {
        val channels = M3uPlaylistParser.parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="sports" tvg-chno="101" tvg-name="Fallback" tvg-logo="../logos/sports.png" group-title="Sports, US",Sports Café HD
            ../live/101.m3u8
            #EXTINF:-1 tvg-name='Tennis Channel',
            #EXTGRP:Tennis
            https://cdn.example/tennis.m3u8
        """.trimIndent().let { "\uFEFF$it" }, source)
        assertEquals(2, channels.size)
        assertEquals("Sports Café HD", channels[0].name)
        assertEquals("Sports, US", channels[0].category)
        assertEquals("101", channels[0].number)
        assertEquals("https://provider.example/logos/sports.png", channels[0].logoUrl)
        assertEquals("https://provider.example/live/101.m3u8", channels[0].streamUrl)
        assertEquals("Tennis Channel", channels[1].name)
        assertEquals("Tennis", channels[1].category)
    }

    @Test fun `HLS media manifest becomes one stream and never segment channels`() {
        val channels = M3uPlaylistParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment1.ts\n#EXTINF:6,\nsegment2.ts", source, "Stadium Feed")
        assertEquals(1, channels.size)
        assertEquals(source, channels.single().streamUrl)
        assertEquals("Stadium Feed", channels.single().name)
        assertEquals("application/x-mpegURL", channels.single().streamMimeType)
    }

    @Test fun `HLS master manifest keeps variants in a single adaptive stream`() {
        val channels = M3uPlaylistParser.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1200000\nlow/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=4000000\nhigh/index.m3u8", source)
        assertEquals(1, channels.size)
        assertEquals(source, channels.single().streamUrl)
    }

    @Test fun `duplicates collapse with stable ids when catalog order changes`() {
        val one = "#EXTINF:-1,One\nhttps://cdn.example/one.m3u8"
        val two = "#EXTINF:-1,Two\nhttps://cdn.example/two.m3u8"
        val original = M3uPlaylistParser.parse("#EXTM3U\n$one\n$two\n$one", source)
        val reordered = M3uPlaylistParser.parse("#EXTM3U\n$two\n$one", source)
        assertEquals(2, original.size)
        assertEquals(original[0].id, reordered[1].id)
    }

    @Test fun `playlist playback headers apply only to the following channel`() {
        val channels = M3uPlaylistParser.parse("""
            #EXTM3U
            #EXTINF:-1,One
            #EXTVLCOPT:http-user-agent=Provider Player
            #EXTVLCOPT:http-referrer=https://provider.example/player
            https://cdn.example/one.m3u8|Origin=https%3A%2F%2Fprovider.example
            #EXTINF:-1,Two
            https://cdn.example/two.m3u8
        """.trimIndent(), source)
        assertEquals("Provider Player", channels[0].streamHeaders["User-Agent"])
        assertEquals("https://provider.example/player", channels[0].streamHeaders["Referer"])
        assertEquals("https://provider.example", channels[0].streamHeaders["Origin"])
        assertTrue(channels[1].streamHeaders.isEmpty())
    }

    @Test fun `unsupported schemes and malformed entries never reach playback`() {
        val channels = M3uPlaylistParser.parse("#EXTM3U\n#EXTINF:-1,Invalid\nrtmp://cdn.example/live\n#EXTINF:-1,Valid\nhttps://cdn.example/live.m3u8", source)
        assertEquals(listOf("Valid"), channels.map { it.name })
    }

    @Test fun `document playlists accept absolute HTTP streams`() {
        val channels = M3uPlaylistParser.parse("#EXTM3U\n#EXTINF:-1,My Channel\nhttps://cdn.example/live.m3u8", "content://documents/playlist")
        assertEquals("My Channel", channels.single().name)
        assertTrue(M3uPlaylistParser.isSupportedSource("content://documents/playlist"))
        assertFalse(M3uPlaylistParser.isSupportedSource("file:///sdcard/playlist.m3u"))
        assertFalse(M3uPlaylistParser.isSupportedSource("javascript:alert(1)"))
    }

    @Test fun `HTML or empty server responses produce a useful error`() {
        listOf("", "<html>Sign in</html>", "#EXTM3U\n#EXTINF:-1,Offline\nrtmp://cdn.example/live").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { M3uPlaylistParser.parse(raw, source) }
        }
    }

    @Test fun `basic M3U URL lists work without extended metadata`() {
        val channels = M3uPlaylistParser.parse("https://cdn.example/one.m3u8\nhttps://cdn.example/two.ts", source)
        assertEquals(2, channels.size)
        assertEquals(listOf("Channel 1", "Channel 2"), channels.map { it.name })
    }

    @Test fun `header injection in URL options is rejected`() {
        val channel = M3uPlaylistParser.parse("#EXTM3U\n#EXTINF:-1,Channel\nhttps://cdn.example/live.m3u8|User-Agent=Good%0D%0AX-Injected%3Abad", source).single()
        assertTrue(channel.streamHeaders.isEmpty())
    }
}
