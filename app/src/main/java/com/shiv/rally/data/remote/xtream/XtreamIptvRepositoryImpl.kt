package com.shiv.rally.data.remote.xtream

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.EpgProgram
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Xtream Codes implementation backed by the standard player_api.php endpoints.
 * It intentionally keeps its catalog in memory so it never overwrites the Stalker
 * Room cache or makes a provider switch destructive.
 */
class XtreamIptvRepositoryImpl @Inject constructor(
    private val api: XtreamApi,
    private val preferencesManager: PreferencesManager
) : IptvRepository {

    companion object {
        private const val TAG = "XtreamRepo"
        private const val CHANNEL_CACHE_TTL_MS = 15 * 60 * 1000L
        private const val GUIDE_CACHE_TTL_MS = 2 * 60 * 1000L
        private const val AUTH_CACHE_TTL_MS = 5 * 60 * 1000L
    }

    private val requestMutex = Mutex()
    private val catalogMutex = Mutex()
    private val guideCache = ConcurrentHashMap<String, ChannelGuide>()
    private var cachedChannels: List<IptvChannel> = emptyList()
    private var cachedAtMs: Long = 0L
    private var authenticatedUntilMs: Long = 0L
    private var cacheIdentity: String = ""

    override suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        val settings = settings() ?: return@withContext false
        val identity = settings.identity
        if (identity == cacheIdentity && System.currentTimeMillis() < authenticatedUntilMs) return@withContext true

        requestMutex.withLock {
            if (identity == cacheIdentity && System.currentTimeMillis() < authenticatedUntilMs) return@withLock true
            val url = XtreamUrlBuilder.playerApi(settings.server, settings.username, settings.password)
                ?: return@withLock false
            val root = requestJson(url) ?: return@withLock false
            val userInfo = root.asJsonObjectOrNull()?.getAsJsonObject("user_info")
            val auth = userInfo?.stringValue("auth")
            val status = userInfo?.stringValue("status")
            val accepted = auth == null || auth == "1" || auth.equals("true", true)
            val active = status.isNullOrBlank() || status.equals("active", true) || status == "1"
            if (!accepted || !active) {
                authenticatedUntilMs = 0L
                Log.w(TAG, "Xtream account was rejected or is not active")
                return@withLock false
            }
            cacheIdentity = identity
            authenticatedUntilMs = System.currentTimeMillis() + AUTH_CACHE_TTL_MS
            true
        }
    }

    override suspend fun getChannels(): List<IptvChannel> = withContext(Dispatchers.IO) {
        val settings = settings() ?: return@withContext emptyList()
        val now = System.currentTimeMillis()
        if (cacheIdentity == settings.identity && cachedChannels.isNotEmpty() && now - cachedAtMs < CHANNEL_CACHE_TTL_MS) {
            return@withContext cachedChannels
        }
        catalogMutex.withLock {
            val secondRead = System.currentTimeMillis()
            if (cacheIdentity == settings.identity && cachedChannels.isNotEmpty() && secondRead - cachedAtMs < CHANNEL_CACHE_TTL_MS) {
                return@withLock cachedChannels
            }
            if (!authenticate()) return@withLock cachedChannels.takeIf { cacheIdentity == settings.identity }.orEmpty()
            fetchChannels(settings)
        }
    }

    override suspend fun refreshChannels(): List<IptvChannel> = withContext(Dispatchers.IO) {
        clearMemoryCache()
        getChannels()
    }

    override suspend fun getChannelStreamUrl(channelId: String): String = withContext(Dispatchers.IO) {
        if (channelId.startsWith("http://") || channelId.startsWith("https://")) return@withContext channelId
        val settings = settings() ?: return@withContext channelId
        cachedChannels.firstOrNull { it.id == channelId }?.streamUrl?.let { direct ->
            return@withContext direct
        }
        val streamId = channelId.removePrefix("xtream:")
        XtreamUrlBuilder.liveStream(settings.server, settings.username, settings.password, streamId) ?: channelId
    }

    override suspend fun getChannelGuide(channelId: String): ChannelGuide? = withContext(Dispatchers.IO) {
        val settings = settings() ?: return@withContext null
        val cached = guideCache[channelId]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtEpochMs < GUIDE_CACHE_TTL_MS) return@withContext cached
        if (!authenticate()) return@withContext cached
        val streamId = channelId.removePrefix("xtream:")
        val url = XtreamUrlBuilder.playerApi(settings.server, settings.username, settings.password, "get_short_epg")
            ?.let { "$it&stream_id=${java.net.URLEncoder.encode(streamId, Charsets.UTF_8.name())}&limit=10" }
            ?: return@withContext cached
        val result = parseGuide(requestJson(url)) ?: return@withContext cached
        guideCache[channelId] = result
        result
    }

    override fun clearMemoryCache() {
        cachedChannels = emptyList()
        cachedAtMs = 0L
        authenticatedUntilMs = 0L
        cacheIdentity = ""
        guideCache.clear()
    }

    private suspend fun fetchChannels(settings: XtreamSettings): List<IptvChannel> = coroutineScope {
        val categoryDeferred = async { requestJson(XtreamUrlBuilder.playerApi(settings.server, settings.username, settings.password, "get_live_categories")) }
        val streamsDeferred = async { requestJson(XtreamUrlBuilder.playerApi(settings.server, settings.username, settings.password, "get_live_streams")) }
        val categories = parseList(categoryDeferred.await()).associateBy(
            keySelector = { it.stringValue("category_id") ?: it.stringValue("id").orEmpty() },
            valueTransform = { it.stringValue("category_name") ?: it.stringValue("name") ?: "Live TV" }
        )
        val channels = parseList(streamsDeferred.await()).mapNotNull { item ->
            val streamId = item.stringValue("stream_id") ?: item.stringValue("id") ?: return@mapNotNull null
            val direct = item.stringValue("direct_source")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val archive = item.booleanValue("tv_archive")
            IptvChannel(
                id = "xtream:$streamId",
                number = item.stringValue("num") ?: streamId,
                name = item.stringValue("name") ?: item.stringValue("stream_name") ?: "Channel $streamId",
                category = categories[item.stringValue("category_id").orEmpty()] ?: item.stringValue("category_name") ?: "Live TV",
                logoUrl = item.stringValue("stream_icon"),
                streamUrl = direct,
                supportsCatchUp = archive,
                archiveDurationHours = item.intValue("tv_archive_duration")
            )
        }.distinctBy { it.id }
        cachedChannels = channels
        cachedAtMs = System.currentTimeMillis()
        cacheIdentity = settings.identity
        channels
    }

    private suspend fun requestJson(url: String?): JsonElement? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val response = api.request(url)
            if (!response.isSuccessful) {
                Log.w(TAG, "Xtream request failed with HTTP ${response.code()}")
                null
            } else response.body()
        }.onFailure { Log.w(TAG, "Xtream request failed: ${it.message}") }.getOrNull()
    }

    private fun parseList(element: JsonElement?): List<JsonObject> {
        if (element == null || element.isJsonNull) return emptyList()
        val array = when {
            element.isJsonArray -> element.asJsonArray
            element.isJsonObject -> element.asJsonObject.getAsJsonArray("data")
                ?: element.asJsonObject.getAsJsonArray("live_streams")
                ?: element.asJsonObject.getAsJsonArray("epg_listings")
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
    }

    private fun parseGuide(root: JsonElement?): ChannelGuide? {
        val entries = parseList(root).map { obj ->
            EpgProgram(
                title = obj.stringValue("title") ?: obj.stringValue("name") ?: "",
                description = obj.stringValue("description"),
                startTime = parseInstant(obj.stringValue("start_timestamp") ?: obj.stringValue("start")),
                endTime = parseInstant(obj.stringValue("stop_timestamp") ?: obj.stringValue("end"))
            )
        }.filter { it.title.isNotBlank() }
        if (entries.isEmpty()) return null
        val now = Instant.now()
        val current = entries.firstOrNull { it.startTime?.let { start -> start <= now } == true && it.endTime?.let { end -> end > now } == true }
        val next = entries.firstOrNull { it.startTime?.isAfter(now) == true }
        return ChannelGuide(now = current ?: entries.firstOrNull(), next = next ?: entries.drop(1).firstOrNull())
    }

    private fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        raw.toLongOrNull()?.let { value -> return Instant.ofEpochMilli(if (value < 10_000_000_000L) value * 1000 else value) }
        return runCatching { Instant.parse(raw) }.getOrElse {
            runCatching {
                LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toInstant(ZoneOffset.UTC)
            }.getOrNull()
        }
    }

    private fun settings(): XtreamSettings? {
        val server = preferencesManager.xtreamServerUrl
        val username = preferencesManager.xtreamUsername
        val password = preferencesManager.xtreamPassword
        if (server.isBlank() || username.isBlank() || password.isBlank()) return null
        return XtreamSettings(server, username, password)
    }

    private data class XtreamSettings(val server: String, val username: String, val password: String) {
        val identity: String get() = "$server|$username|$password"
    }
}

private fun JsonElement.asJsonObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.stringValue(key: String): String? = runCatching {
    get(key)?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
}.getOrNull()

private fun JsonObject.booleanValue(key: String): Boolean = stringValue(key)?.let {
    it == "1" || it.equals("true", true) || it.equals("yes", true)
} ?: false

private fun JsonObject.intValue(key: String): Int? = stringValue(key)?.toIntOrNull()
