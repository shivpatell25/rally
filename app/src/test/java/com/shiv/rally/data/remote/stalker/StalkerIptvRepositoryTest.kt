package com.shiv.rally.data.remote.stalker

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.shiv.rally.data.local.ChannelDao
import com.shiv.rally.data.local.ChannelEntity
import com.shiv.rally.data.local.PreferencesManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StalkerIptvRepositoryTest {

    private class FakeStalkerApi : StalkerApi {
        var createLinkCmd: String? = null
        var createLinkCallCount = 0
        var handshakeCallCount = 0
        var returnEmptyFirstChannelRequest = false
        var channelRequestCount = 0
            private set

        override suspend fun handshake(type: String, action: String): StalkerResponse<JsonElement> {
            handshakeCallCount++
            return StalkerResponse(js = JsonObject().apply { addProperty("token", "fake_token") })
        }

        override suspend fun getProfile(type: String, action: String, token: String): StalkerResponse<JsonElement> =
            StalkerResponse(js = JsonObject().apply { addProperty("id", 123) })

        override suspend fun getOrderedList(type: String, action: String, page: Int, token: String): StalkerResponse<JsonElement> =
            StalkerResponse()

        override suspend fun getAllChannels(type: String, action: String, token: String): StalkerResponse<JsonElement> {
            channelRequestCount++
            if (returnEmptyFirstChannelRequest && channelRequestCount == 1) {
                return StalkerResponse()
            }
            val array = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("id", "101")
                    addProperty("name", "Sky Sports News")
                    addProperty("number", "1")
                    addProperty("tv_genre_id", "8")
                    addProperty("cmd", "ffrt http://localhost/ch/101")
                })
            }
            return StalkerResponse(js = JsonObject().apply { add("data", array) })
        }

        override suspend fun getGenres(type: String, action: String, token: String): StalkerResponse<JsonElement> {
            val array = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("id", "8")
                    addProperty("title", "US | SPORTS")
                })
            }
            return StalkerResponse(js = array)
        }

        override suspend fun getShortEpg(
            type: String,
            action: String,
            channelId: String,
            size: Int,
            token: String
        ): StalkerResponse<JsonElement> = StalkerResponse(js = JsonArray())

        override suspend fun createLink(type: String, action: String, cmd: String, token: String): StalkerResponse<JsonElement> {
            createLinkCallCount++
            createLinkCmd = cmd
            return StalkerResponse(js = JsonObject().apply {
                addProperty("cmd", "http://184.107.122.92/live/index.m3u8?token=secure_hls_token")
            })
        }
    }

    private lateinit var fakeApi: FakeStalkerApi
    private val preferencesManager: PreferencesManager = mockk(relaxed = true)
    private val channelDao: ChannelDao = mockk(relaxed = true)
    private lateinit var repository: StalkerIptvRepositoryImpl

    @Before
    fun setup() {
        fakeApi = FakeStalkerApi()
        every { preferencesManager.authToken } returns "test_token_123"
        every { preferencesManager.portalUrl } returns "http://tv.stream4k.cc"
        every { preferencesManager.macAddress } returns "3C:6D:66:24:70:3E"
        repository = StalkerIptvRepositoryImpl(fakeApi, preferencesManager, channelDao)
        repository.clearMemoryCache()
    }

    @Test
    fun `getChannelStreamUrl resolves numeric channelId via ChannelDao cmd and createLink`() = runTest {
        val channelEntity = ChannelEntity(
            id = "71914",
            number = "101",
            name = "ESPN HD",
            category = "Sports",
            logoUrl = null,
            streamUrl = "ffrt http://localhost/ch/475241"
        )
        coEvery { channelDao.getChannelById("71914") } returns channelEntity

        val result = repository.getChannelStreamUrl("71914")

        assertEquals("http://184.107.122.92/live/index.m3u8?token=secure_hls_token", result)
        assertEquals(1, fakeApi.createLinkCallCount)
        assertEquals("ffrt http://localhost/ch/475241", fakeApi.createLinkCmd)
    }

    @Test
    fun `getChannelStreamUrl bypasses createLink for direct external stream URLs`() = runTest {
        val externalUrl = "https://cdn.example.com/live/stream.m3u8"
        val result = repository.getChannelStreamUrl(externalUrl)

        assertEquals(externalUrl, result)
        assertEquals(0, fakeApi.createLinkCallCount)
    }

    @Test
    fun `getChannels loads channels and maps genre names from getGenres`() = runTest {
        val channels = repository.getChannels()

        assertEquals(1, channels.size)
        val channel = channels.first()
        assertEquals("101", channel.id)
        assertEquals("Sky Sports News", channel.name)
        assertEquals("US | SPORTS", channel.category)
        assertEquals("ffrt http://localhost/ch/101", channel.streamUrl)
    }

    @Test
    fun `getChannels refreshes stale session and retries an empty portal response once`() = runTest {
        fakeApi.returnEmptyFirstChannelRequest = true

        val channels = repository.getChannels()

        assertEquals(1, channels.size)
        assertEquals("101", channels.first().id)
        assertEquals(1, fakeApi.handshakeCallCount)
    }

    @Test
    fun `searchChannels uses targeted database query without refreshing the full portal catalog`() = runTest {
        val identity = "http://tv.stream4k.cc|3C:6D:66:24:70:3E"
        every { preferencesManager.channelCacheIdentity } returns identity
        coEvery { channelDao.getChannelCount() } returns 4_679
        coEvery { channelDao.searchChannels("redzone", 20) } returns listOf(
            ChannelEntity(
                id = "69785",
                number = "700",
                name = "NFL RedZone HD",
                category = "US | SPORTS",
                logoUrl = null,
                streamUrl = "ffrt http://localhost/ch/69785"
            )
        )

        val channels = repository.searchChannels("redzone", 20)

        assertEquals(listOf("69785"), channels.map { it.id })
        assertEquals(0, fakeApi.channelRequestCount)
    }
}
