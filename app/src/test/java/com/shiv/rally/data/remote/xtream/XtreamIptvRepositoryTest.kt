package com.shiv.rally.data.remote.xtream

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.shiv.rally.data.local.PreferencesManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class XtreamIptvRepositoryTest {
    private class FakeXtreamApi : XtreamApi {
        val requestedUrls = mutableListOf<String>()

        override suspend fun request(url: String): Response<JsonElement> {
            requestedUrls += url
            return when {
                "get_live_categories" in url -> Response.success(JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("category_id", "7")
                        addProperty("category_name", "Sports")
                    })
                })
                "get_live_streams" in url -> Response.success(JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("stream_id", 42)
                        addProperty("num", 700)
                        addProperty("name", "ESPN HD")
                        addProperty("category_id", "7")
                        addProperty("stream_icon", "https://cdn.example.to/espn.png")
                        addProperty("tv_archive", 1)
                        addProperty("tv_archive_duration", 72)
                    })
                })
                "get_short_epg" in url -> Response.success(JsonObject().apply {
                    add("epg_listings", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("title", "Live Sports")
                            addProperty("start_timestamp", "2000000000")
                        })
                    })
                })
                else -> Response.success(JsonObject().apply {
                    add("user_info", JsonObject().apply {
                        addProperty("auth", 1)
                        addProperty("status", "Active")
                    })
                })
            }
        }
    }

    private val preferences: PreferencesManager = mockk(relaxed = true)
    private lateinit var api: FakeXtreamApi
    private lateinit var repository: XtreamIptvRepositoryImpl

    @Before
    fun setup() {
        api = FakeXtreamApi()
        every { preferences.xtreamServerUrl } returns "https://provider.example.to:8080"
        every { preferences.xtreamUsername } returns "user name"
        every { preferences.xtreamPassword } returns "p@ss/word"
        repository = XtreamIptvRepositoryImpl(api, preferences)
    }

    @Test
    fun `catalog maps Xtream streams to shared IPTV channels`() = runTest {
        val channels = repository.getChannels()

        assertEquals(1, channels.size)
        assertEquals("xtream:42", channels.first().id)
        assertEquals("Sports", channels.first().category)
        assertTrue(channels.first().supportsCatchUp)
        assertEquals("https://provider.example.to:8080/live/user%20name/p@ss%2Fword/42.m3u8", repository.getChannelStreamUrl(channels.first().id))
        assertTrue(api.requestedUrls.any { "get_live_streams" in it })
    }

    @Test
    fun `guide uses Xtream short EPG endpoint`() = runTest {
        val guide = repository.getChannelGuide("xtream:42")

        assertEquals("Live Sports", guide?.now?.title)
        assertTrue(api.requestedUrls.any { "get_short_epg" in it && "stream_id=42" in it })
    }
}
