package com.shiv.rally.data.remote.stalker

import android.util.Log
import com.google.gson.JsonElement
import com.shiv.rally.data.local.ChannelDao
import com.shiv.rally.data.local.ChannelEntity
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.EpgProgram
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.repository.IptvRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.shiv.rally.di.ApplicationScope
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class StalkerIptvRepositoryImpl @Inject constructor(
    private val api: StalkerApi,
    private val preferencesManager: PreferencesManager,
    private val channelDao: ChannelDao,
    @ApplicationScope private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : IptvRepository {

    companion object {
        private const val TAG = "StalkerRepo"
        @Volatile
        private var memoryCachedChannels: List<IptvChannel>? = null
        @Volatile
        private var lastFetchTimeMs: Long = 0L
        private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes
        private const val GUIDE_TTL_MS = 2 * 60 * 1000L
    }

    private val authMutex = Mutex()
    private val channelMutex = Mutex()
    private val guideCache = ConcurrentHashMap<String, ChannelGuide>()
    private val guideLocks = ConcurrentHashMap<String, Mutex>()
    @Volatile private var refreshJob: Job? = null

    override suspend fun authenticate(): Boolean = authenticateInternal(force = false)

    private suspend fun authenticateInternal(force: Boolean): Boolean = authMutex.withLock {
        if (!force && preferencesManager.authToken.isNotBlank()) return@withLock true
        if (preferencesManager.portalUrl.isBlank()) return@withLock false
        preferencesManager.authToken = ""

        // First try the configured URL
        if (tryAuth()) return@withLock true

        // If it failed, try auto-discovering common stalker portal paths
        var currentUrl = preferencesManager.portalUrl.trim().removeSuffix("/")
        if (currentUrl.isEmpty()) return@withLock false

        if (currentUrl.endsWith("/server/load.php")) {
            currentUrl = currentUrl.removeSuffix("/server/load.php").removeSuffix("/")
        } else if (currentUrl.endsWith("/load.php")) {
            currentUrl = currentUrl.removeSuffix("/load.php").removeSuffix("/")
        }

        val baseDomain = currentUrl.removeSuffix("/c").removeSuffix("/stalker_portal").removeSuffix("/")
        val candidateUrls = listOf(
            currentUrl,
            "$baseDomain/c",
            "$baseDomain/stalker_portal",
            "$baseDomain/stalker_portal/c"
        ).distinct()

        for (testUrl in candidateUrls) {
            if (testUrl == currentUrl && testUrl != candidateUrls.first()) continue
            preferencesManager.portalUrl = testUrl
            if (tryAuth()) {
                return@withLock true
            }
        }

        // If all auto-discovery failed, revert to original so we don't pollute settings
        preferencesManager.portalUrl = currentUrl
        false
    }

    private suspend fun tryAuth(): Boolean {
        return try {
            Log.d(TAG, "Initiating Stalker handshake...")
            val response = api.handshake(type = "stb", action = "handshake")
            val token = extractToken(response.js)

            if (!token.isNullOrBlank()) {
                val bearerToken = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                preferencesManager.authToken = bearerToken
                Log.d(TAG, "Handshake successful")

                // Call get_profile to verify session activation in Stalker middleware
                try {
                    val profileResp = api.getProfile(
                        type = "stb",
                        action = "get_profile",
                        token = bearerToken
                    )
                    val profileJs = profileResp.js
                    if (profileJs != null && profileJs.isJsonObject) {
                        val obj = profileJs.asJsonObject
                        val statusElem = obj.get("status")
                        if (statusElem != null && statusElem.isJsonPrimitive) {
                            val prim = statusElem.asJsonPrimitive
                            val statusCode = if (prim.isNumber) {
                                prim.asInt
                            } else {
                                prim.asString.toIntOrNull()
                            }
                            val statusText = if (!prim.isNumber) prim.asString else ""
                            val isRejected = statusCode == 1 || statusCode == 2 ||
                                statusText.equals("error", ignoreCase = true) ||
                                statusText.equals("failed", ignoreCase = true)
                            if (isRejected) {
                                Log.e(TAG, "Stalker profile validation rejected (status=${statusCode ?: statusText})")
                                preferencesManager.authToken = ""
                                return false
                            }
                        }
                    }
                    Log.d(TAG, "Profile activated successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "getProfile failed: ${e.message}", e)
                    preferencesManager.authToken = ""
                    return false
                }
                true
            } else {
                Log.w(TAG, "Handshake response did not contain a valid token")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed: ${e.message}", e)
            false
        }
    }

    private fun extractToken(js: JsonElement?): String? {
        if (js == null || js.isJsonNull) return null
        return when {
            js.isJsonObject -> {
                val obj = js.asJsonObject
                obj.get("token")?.asString ?: obj.get("random")?.asString
            }
            js.isJsonPrimitive -> js.asString
            else -> null
        }
    }

    private var lastFailureTimeMs = 0L

    override suspend fun getChannels(): List<IptvChannel> = withContext(Dispatchers.IO) {
        ensureCorrectCacheOwner()

        // 1. Instant in-memory cache hit (<0.1ms)
        val inMem = memoryCachedChannels
        if (inMem != null && inMem.isNotEmpty() && (System.currentTimeMillis() - lastFetchTimeMs < CACHE_TTL_MS)) {
            return@withContext inMem
        }

        // 2. Fast Room Database cache hit (<5ms)
        val dbChannels = try { channelDao.getAllChannels().map { it.toIptvChannel() } } catch (e: Exception) { emptyList() }
        if (dbChannels.isNotEmpty()) {
            memoryCachedChannels = dbChannels
            // Return DB channels immediately so the UI is NEVER blocked waiting on slow portal network calls!
            if (lastFetchTimeMs == 0L || (System.currentTimeMillis() - lastFetchTimeMs > CACHE_TTL_MS)) {
                lastFetchTimeMs = System.currentTimeMillis()
                // Refresh asynchronously in background without blocking caller
                scheduleBackgroundRefresh()
            }
            return@withContext dbChannels
        }

        // 3. If no portal URL configured, return empty immediately
        if (preferencesManager.portalUrl.trim().isEmpty() || preferencesManager.macAddress.trim().isEmpty()) {
            return@withContext emptyList()
        }

        // 4. Cooldown on recent network failure (e.g. 404 from portal) to prevent hammering
        if (System.currentTimeMillis() - lastFailureTimeMs < 60_000L) {
            return@withContext dbChannels
        }

        channelMutex.withLock {
            memoryCachedChannels?.takeIf { it.isNotEmpty() }?.let { return@withLock it }
            val channelsAfterWait = channelDao.getAllChannels().map { it.toIptvChannel() }
            if (channelsAfterWait.isNotEmpty()) {
                memoryCachedChannels = channelsAfterWait
                return@withLock channelsAfterWait
            }

            if (!authenticate()) {
                lastFailureTimeMs = System.currentTimeMillis()
                return@withLock emptyList()
            }
            fetchAndPersistChannels()
        }
    }

    override suspend fun searchChannels(query: String, limit: Int): List<IptvChannel> = withContext(Dispatchers.IO) {
        ensureCorrectCacheOwner()
        val normalized = query.trim()
        if (normalized.isEmpty() || limit <= 0) return@withContext emptyList()

        fun List<IptvChannel>.matchingChannels(): List<IptvChannel> = asSequence()
            .filter { channel ->
                channel.name.contains(normalized, ignoreCase = true) ||
                    channel.category.contains(normalized, ignoreCase = true) ||
                    channel.number.contains(normalized, ignoreCase = true)
            }
            .take(limit)
            .toList()

        memoryCachedChannels?.takeIf { it.isNotEmpty() }?.let { cached ->
            return@withContext cached.matchingChannels()
        }

        val databaseCount = runCatching { channelDao.getChannelCount() }.getOrDefault(0)
        if (databaseCount > 0) {
            return@withContext runCatching {
                channelDao.searchChannels(normalized, limit).map { it.toIptvChannel() }
            }.getOrDefault(emptyList())
        }

        // A first-time portal sync still has to populate the database. Subsequent
        // searches stay targeted and do not retain the full catalog in memory.
        getChannels().matchingChannels()
    }

    override suspend fun refreshChannels(): List<IptvChannel> = withContext(Dispatchers.IO) {
        ensureCorrectCacheOwner()
        if (preferencesManager.portalUrl.trim().isEmpty() || preferencesManager.macAddress.trim().isEmpty()) {
            return@withContext emptyList()
        }

        channelMutex.withLock {
            lastFailureTimeMs = 0L
            if (!authenticateInternal(force = true)) {
                lastFailureTimeMs = System.currentTimeMillis()
                return@withLock channelDao.getAllChannels().map { it.toIptvChannel() }
            }
            fetchAndPersistChannels(retryAuthenticationOnEmpty = false)
        }
    }

    private suspend fun ensureCorrectCacheOwner() {
        val identity = "${preferencesManager.portalUrl.lowercase()}|${preferencesManager.macAddress.uppercase()}"
        if (preferencesManager.channelCacheIdentity == identity) return
        channelMutex.withLock {
            if (preferencesManager.channelCacheIdentity != identity) {
                memoryCachedChannels = null
                guideCache.clear()
                guideLocks.clear()
                lastFetchTimeMs = 0L
                lastFailureTimeMs = 0L
                preferencesManager.authToken = ""
                channelDao.clearAll()
                preferencesManager.channelCacheIdentity = identity
            }
        }
    }

    private fun scheduleBackgroundRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = appScope.launch {
            channelMutex.withLock {
                try {
                    if (preferencesManager.authToken.isBlank() && !authenticate()) return@withLock
                    fetchAndPersistChannels()
                } catch (e: Exception) {
                    Log.d(TAG, "Background channel refresh failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun fetchAndPersistChannels(retryAuthenticationOnEmpty: Boolean = true): List<IptvChannel> {
        return try {
            var remoteChannels = fetchChannelsInternal()
            if (remoteChannels.isEmpty() && retryAuthenticationOnEmpty) {
                Log.w(TAG, "Portal returned no channels; refreshing the session and retrying once")
                if (authenticateInternal(force = true)) {
                    remoteChannels = fetchChannelsInternal()
                }
            }
            if (remoteChannels.isEmpty()) {
                lastFailureTimeMs = System.currentTimeMillis()
                return channelDao.getAllChannels().map { it.toIptvChannel() }
            }
            memoryCachedChannels = remoteChannels
            lastFetchTimeMs = System.currentTimeMillis()
            channelDao.replaceAll(remoteChannels.map { it.toEntity() })
            Log.d(TAG, "Channel cache refreshed (${remoteChannels.size} channels)")
            remoteChannels
        } catch (e: Exception) {
            lastFailureTimeMs = System.currentTimeMillis()
            Log.w(TAG, "Channel refresh failed: ${e.message}")
            channelDao.getAllChannels().map { it.toIptvChannel() }
        }
    }

    private suspend fun fetchChannelsInternal(): List<IptvChannel> = coroutineScope {
        val token = preferencesManager.authToken
        val allChannels = mutableListOf<StalkerChannel>()

        // 1. Fetch genres mapping (id -> genre title)
        val genreMap = mutableMapOf<String, String>()
        try {
            val genresResp = api.getGenres(type = "itv", action = "get_genres", token = token)
            val genresJs = genresResp.js
            if (genresJs != null && genresJs.isJsonArray) {
                for (item in genresJs.asJsonArray) {
                    if (item.isJsonObject) {
                        val obj = item.asJsonObject
                        val id = obj.get("id")?.asString
                        val title = obj.get("title")?.asString
                        if (!id.isNullOrBlank() && !title.isNullOrBlank() && id != "*") {
                            genreMap[id] = title
                        }
                    }
                }
            }
            Log.d(TAG, "Loaded ${genreMap.size} channel genres from Stalker")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load genres: ${e.message}")
        }

        // 2. Fetch all channels via get_all_channels (fast single call)
        try {
            Log.d(TAG, "Fetching channels via get_all_channels...")
            val allResponse = api.getAllChannels(
                type = "itv",
                action = "get_all_channels",
                token = token
            )
            val (channelsList, _) = parseChannels(allResponse.js)
            allChannels.addAll(channelsList)
            Log.d(TAG, "get_all_channels returned ${channelsList.size} channels")
        } catch (e: Exception) {
            Log.w(TAG, "get_all_channels failed: ${e.message}. Trying get_ordered_list...", e)
        }

        // 3. Fallback to get_ordered_list if get_all_channels returned no channels
        if (allChannels.isEmpty()) {
            try {
                Log.d(TAG, "Fetching channels via get_ordered_list (page 1)...")
                val page1Response = api.getOrderedList(
                    type = "itv",
                    action = "get_ordered_list",
                    page = 1,
                    token = token
                )
                val (page1Channels, totalItems) = parseChannels(page1Response.js)
                allChannels.addAll(page1Channels)
                Log.d(TAG, "Page 1 returned ${page1Channels.size} channels, totalItems: $totalItems")

                if (page1Channels.isNotEmpty() && totalItems != null && totalItems > page1Channels.size) {
                    val perPage = page1Channels.size.coerceAtLeast(1)
                    val totalPages = ((totalItems + perPage - 1) / perPage).coerceAtMost(30)
                    Log.d(TAG, "Fetching remaining ${totalPages - 1} pages concurrently...")

                    val deferredPages = (2..totalPages).map { pageNum ->
                        async {
                            try {
                                val resp = api.getOrderedList(
                                    type = "itv",
                                    action = "get_ordered_list",
                                    page = pageNum,
                                    token = token
                                )
                                val (pageChannels, _) = parseChannels(resp.js)
                                pageChannels
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to fetch page $pageNum: ${e.message}")
                                emptyList()
                            }
                        }
                    }
                    allChannels.addAll(deferredPages.awaitAll().flatten())
                }
            } catch (e: Exception) {
                Log.w(TAG, "get_ordered_list also failed: ${e.message}", e)
            }
        }

        allChannels.distinctBy { it.id }.map { stalkerChannel ->
            val categoryTitle = stalkerChannel.tv_genre_id?.let { genreMap[it] ?: it } ?: "Live TV"
            IptvChannel(
                id = stalkerChannel.id,
                number = stalkerChannel.number,
                name = stalkerChannel.name,
                category = categoryTitle,
                logoUrl = stalkerChannel.logo,
                streamUrl = stalkerChannel.cmd,
                supportsCatchUp = stalkerChannel.supportsCatchUp,
                archiveDurationHours = stalkerChannel.archiveDurationHours
            )
        }
    }

    /**
     * Flexible parser that handles:
     * - js as JsonArray
     * - js as JsonObject with "data" as JsonArray
     * - js as JsonObject with "data" as JsonObject (PHP associative array formatting)
     * - js as JsonObject direct key-value mapping
     */
    private fun parseChannels(jsElement: JsonElement?): Pair<List<StalkerChannel>, Int?> {
        if (jsElement == null || jsElement.isJsonNull) return Pair(emptyList(), null)

        val channels = mutableListOf<StalkerChannel>()
        var totalItems: Int? = null

        fun addChannelFromJson(elem: JsonElement) {
            if (!elem.isJsonObject) return
            val obj = elem.asJsonObject
            val id = obj.get("id")?.asString
                ?: obj.get("ch_id")?.asString
                ?: return
            val name = obj.get("name")?.asString ?: "Channel $id"
            val number = obj.get("number")?.asString
                ?: obj.get("num")?.asString
                ?: id
            val logo = obj.get("logo")?.asString
            val genreId = obj.get("tv_genre_id")?.asString
            val cmd = obj.get("cmd")?.asString ?: ""
            fun truthy(key: String): Boolean = runCatching {
                val raw = obj.get(key)?.asString?.trim()?.lowercase()
                raw == "1" || raw == "true" || raw == "yes" || (raw?.toIntOrNull() ?: 0) > 0
            }.getOrDefault(false)
            val supportsCatchUp = truthy("tv_archive") || truthy("allow_timeshift") || truthy("archive")
            val archiveDurationHours = listOf("tv_archive_duration", "archive_hours")
                .firstNotNullOfOrNull { key -> runCatching { obj.get(key)?.asString?.toIntOrNull() }.getOrNull() }
                ?: runCatching { obj.get("archive_days")?.asString?.toIntOrNull()?.times(24) }.getOrNull()
            channels.add(
                StalkerChannel(
                    id = id,
                    name = name,
                    number = number,
                    logo = logo,
                    tv_genre_id = genreId,
                    cmd = cmd,
                    supportsCatchUp = supportsCatchUp,
                    archiveDurationHours = archiveDurationHours
                )
            )
        }

        if (jsElement.isJsonArray) {
            for (item in jsElement.asJsonArray) {
                addChannelFromJson(item)
            }
        } else if (jsElement.isJsonObject) {
            val jsObj = jsElement.asJsonObject
            if (jsObj.has("total_items")) {
                try {
                    totalItems = jsObj.get("total_items").asString.toIntOrNull()
                } catch (ignored: Exception) {}
            }

            val dataElem = jsObj.get("data")
            if (dataElem != null && !dataElem.isJsonNull) {
                if (dataElem.isJsonArray) {
                    for (item in dataElem.asJsonArray) {
                        addChannelFromJson(item)
                    }
                } else if (dataElem.isJsonObject) {
                    for ((_, item) in dataElem.asJsonObject.entrySet()) {
                        addChannelFromJson(item)
                    }
                }
            } else {
                for ((key, value) in jsObj.entrySet()) {
                    if (key !in listOf("total_items", "max_page_items", "selected_item", "cur_page")) {
                        addChannelFromJson(value)
                    }
                }
            }
        }
        return Pair(channels, totalItems)
    }

    override suspend fun getChannelStreamUrl(channelId: String): String = withContext(Dispatchers.IO) {
        // If it's already an external HTTP stream URL and not a localhost Stalker cmd
        if ((channelId.startsWith("http://") || channelId.startsWith("https://")) && !channelId.contains("localhost")) {
            return@withContext cleanStreamUrl(channelId)
        }

        // Resolve cmd: if channelId is a numeric ID, look up its cmd in ChannelDao
        var cmd = channelId
        if (!cmd.contains("localhost") && !cmd.startsWith("ffmpeg") && !cmd.startsWith("ffrt") && !cmd.startsWith("auto")) {
            val cachedChannel = channelDao.getChannelById(channelId)
            val cachedUrl = cachedChannel?.streamUrl
            if (!cachedUrl.isNullOrBlank()) {
                cmd = cachedUrl
            }
        }

        try {
            if (preferencesManager.authToken.isEmpty()) {
                authenticateInternal(force = false)
            }
            val response = try {
                api.createLink(
                    type = "itv",
                    action = "create_link",
                    cmd = cmd,
                    token = preferencesManager.authToken
                )
            } catch (e: Exception) {
                Log.w(TAG, "createLink failed: ${e.message}. Re-authenticating...")
                if (authenticateInternal(force = true)) {
                    api.createLink(
                        type = "itv",
                        action = "create_link",
                        cmd = cmd,
                        token = preferencesManager.authToken
                    )
                } else throw e
            }
            val js = response.js
            val rawCmd = when {
                js == null || js.isJsonNull -> cmd
                js.isJsonObject -> js.asJsonObject.get("cmd")?.asString ?: cmd
                js.isJsonPrimitive -> js.asString
                else -> cmd
            }
            cleanStreamUrl(rawCmd)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving channel stream URL for $channelId: ${e.message}", e)
            cleanStreamUrl(channelId)
        }
    }

    private fun cleanStreamUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        val prefixes = listOf("ffmpeg ", "ffrt ", "auto ")
        for (prefix in prefixes) {
            if (url.startsWith(prefix, ignoreCase = true)) {
                url = url.substring(prefix.length).trim()
            }
        }
        return url
    }

    override suspend fun getChannelGuide(channelId: String): ChannelGuide? = withContext(Dispatchers.IO) {
        val cached = guideCache[channelId]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtEpochMs < GUIDE_TTL_MS) {
            return@withContext cached
        }
        if (preferencesManager.portalUrl.isBlank()) return@withContext cached

        guideLocks.getOrPut(channelId) { Mutex() }.withLock {
            val secondRead = guideCache[channelId]
            if (secondRead != null && System.currentTimeMillis() - secondRead.fetchedAtEpochMs < GUIDE_TTL_MS) {
                return@withLock secondRead
            }
            try {
                if (preferencesManager.authToken.isBlank() && !authenticate()) return@withLock secondRead
                val response = api.getShortEpg(
                    type = "itv",
                    action = "get_short_epg",
                    channelId = channelId,
                    size = 4,
                    token = preferencesManager.authToken
                )
                parseChannelGuide(response.js)?.also { guideCache[channelId] = it } ?: secondRead
            } catch (e: Exception) {
                Log.d(TAG, "EPG unavailable for channel $channelId: ${e.message}")
                secondRead
            }
        }
    }

    override fun clearMemoryCache() {
        memoryCachedChannels = null
        guideCache.clear()
        guideLocks.clear()
    }

    private fun parseChannelGuide(js: JsonElement?): ChannelGuide? {
        if (js == null || js.isJsonNull) return null
        val entries = mutableListOf<com.google.gson.JsonObject>()

        fun collect(element: JsonElement?) {
            when {
                element == null || element.isJsonNull -> Unit
                element.isJsonArray -> element.asJsonArray.forEach(::collect)
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val nested = listOf("data", "epg", "programs", "items")
                        .firstNotNullOfOrNull { key -> obj.get(key)?.takeUnless { it.isJsonNull } }
                    if (nested != null) collect(nested) else entries += obj
                }
            }
        }
        collect(js)

        fun stringValue(obj: com.google.gson.JsonObject, vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key ->
                runCatching { obj.get(key)?.takeUnless { it.isJsonNull }?.asString?.trim() }
                    .getOrNull()?.takeIf { it.isNotEmpty() }
            }

        fun instantValue(raw: String?): Instant? {
            if (raw.isNullOrBlank()) return null
            raw.toLongOrNull()?.let { value ->
                return runCatching {
                    Instant.ofEpochMilli(if (value < 10_000_000_000L) value * 1000 else value)
                }.getOrNull()
            }
            return runCatching { Instant.parse(raw) }.getOrNull()
        }

        val programs = entries.mapNotNull { obj ->
            val title = stringValue(obj, "name", "title", "program", "programme") ?: return@mapNotNull null
            EpgProgram(
                title = title,
                description = stringValue(obj, "descr", "description", "desc"),
                startTime = instantValue(stringValue(obj, "start_timestamp", "start", "begin", "time")),
                endTime = instantValue(stringValue(obj, "stop_timestamp", "end_timestamp", "end", "stop"))
            )
        }.sortedWith(compareBy(nullsLast()) { it.startTime })
        if (programs.isEmpty()) return null

        val nowInstant = Instant.now()
        val currentIndex = programs.indexOfFirst { program ->
            val start = program.startTime
            val end = program.endTime
            start != null && end != null && !nowInstant.isBefore(start) && nowInstant.isBefore(end)
        }.takeIf { it >= 0 } ?: 0
        return ChannelGuide(
            now = programs.getOrNull(currentIndex),
            next = programs.getOrNull(currentIndex + 1),
            fetchedAtEpochMs = System.currentTimeMillis()
        )
    }
}

private fun ChannelEntity.toIptvChannel() = IptvChannel(
    id = id,
    number = number,
    name = name,
    category = category,
    logoUrl = logoUrl,
    streamUrl = streamUrl,
    supportsCatchUp = supportsCatchUp,
    archiveDurationHours = archiveDurationHours
)

private fun IptvChannel.toEntity() = ChannelEntity(
    id = id,
    number = number,
    name = name,
    category = category,
    logoUrl = logoUrl,
    streamUrl = streamUrl,
    supportsCatchUp = supportsCatchUp,
    archiveDurationHours = archiveDurationHours
)
