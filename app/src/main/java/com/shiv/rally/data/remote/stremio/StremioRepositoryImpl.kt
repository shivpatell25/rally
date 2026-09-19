package com.shiv.rally.data.remote.stremio

import android.util.Log
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.repository.StremioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StremioRepositoryImpl @Inject constructor(
    private val api: StremioApi,
    private val preferencesManager: PreferencesManager
) : StremioRepository {

    companion object {
        private const val TAG = "StremioRepo"
        private const val MANIFEST_TTL_MS = 30 * 60 * 1000L
        private const val STREAM_TTL_MS = 90 * 1000L
        private const val ADDON_DISCOVERY_TIMEOUT_MS = 20_000L
        private val BITRATE_PATTERN = Regex("""\d+(?:\.\d+)?\s*(?:MBPS|MB/S|KBPS)""")
        private val HDR_PATTERN = Regex("""\b(HDR|HLG|DOLBY\s+VISION)\b""")
        private val UHD_PATTERN = Regex("""\b(4K|UHD|2160P?)\b""")
        private val FHD_PATTERN = Regex("""\b(1080P?|FHD)\b""")
        private val HD_PATTERN = Regex("""\b(720P?|HD)\b""")
    }

    private data class TimedManifest(val value: StremioManifest, val fetchedAt: Long)
    private data class TimedStreams(val value: List<StremioStreamOption>, val fetchedAt: Long)
    private val manifestCache = ConcurrentHashMap<String, TimedManifest>()
    private val streamCache = ConcurrentHashMap<String, TimedStreams>()
    private val requestSemaphore = Semaphore(4)

    override suspend fun getStreamsForEvent(event: SportEvent): List<StremioStreamOption> = withContext(Dispatchers.IO) {
        val addonUrls = preferencesManager.stremioAddonUrls.filter { it.isNotBlank() }
        if (addonUrls.isEmpty()) {
            return@withContext emptyList()
        }

        val cacheKey = "${event.id}|${addonUrls.joinToString("|")}" 
        streamCache[cacheKey]?.takeIf {
            it.value.isNotEmpty() && System.currentTimeMillis() - it.fetchedAt < STREAM_TTL_MS
        }?.let {
            return@withContext it.value
        }

        // Keep completed addon results even when another addon is slow. The previous
        // awaitAll inside one timeout discarded every stream when any addon timed out.
        val completedStreams = Collections.synchronizedList(mutableListOf<StremioStreamOption>())
        supervisorScope {
            addonUrls.map { addonUrl ->
                launch {
                    try {
                        val streams = withTimeoutOrNull(ADDON_DISCOVERY_TIMEOUT_MS) {
                            resolveStreamsFromAddon(addonUrl.trim(), event)
                        }.orEmpty()
                        completedStreams.addAll(streams)
                        val playableCount = streams.count { it.isDirectPlayable }
                        Log.d(
                            TAG,
                            "Addon ${addonUrl.substringBefore("/manifest.json")} returned ${streams.size} results " +
                                "($playableCount playable, ${streams.size - playableCount} web-only)"
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Addon discovery failed: ${e.message}")
                    }
                }
            }.joinAll()
        }
        val allStreams = completedStreams.distinctBy { it.streamUrl }
        if (allStreams.isNotEmpty()) {
            streamCache[cacheKey] = TimedStreams(allStreams, System.currentTimeMillis())
        } else {
            streamCache.remove(cacheKey)
        }
        Log.d(
            TAG,
            "Resolved ${allStreams.size} total results (${allStreams.count { it.isDirectPlayable }} playable) " +
                "for '${event.name}'"
        )
        allStreams
    }

    private suspend fun resolveStreamsFromAddon(addonUrl: String, event: SportEvent): List<StremioStreamOption> = kotlinx.coroutines.coroutineScope {
        val manifestUrl = if (!addonUrl.endsWith("manifest.json")) {
            addonUrl.removeSuffix("/") + "/manifest.json"
        } else {
            addonUrl
        }
        val baseUrl = manifestUrl.substringBeforeLast("manifest.json")

        try {
            val cachedManifest = manifestCache[manifestUrl]
                ?.takeIf { System.currentTimeMillis() - it.fetchedAt < MANIFEST_TTL_MS }
                ?.value
            val manifest = cachedManifest ?: run {
                val fetched = requestSemaphore.withPermit { api.getManifest(manifestUrl) }
                manifestCache[manifestUrl] = TimedManifest(fetched, System.currentTimeMillis())
                fetched
            }
            val addonName = manifest.name ?: "Stremio"

            val sportLower = event.sport.lowercase()
            val leagueLower = event.league.lowercase()
            val allCats = manifest.catalogs ?: emptyList()
            val targetCatalogs = mutableListOf<StremioCatalogDesc>()

            // 1. Match live / schedule catalogs
            allCats.filter {
                val idLower = it.id?.lowercase() ?: ""
                val nameLower = it.name?.lowercase() ?: ""
                idLower.contains("live") || idLower.contains("today") || idLower.contains("schedule") ||
                        nameLower.contains("live") || nameLower.contains("today")
            }.forEach { targetCatalogs.add(it) }

            // 2. Match sport-specific catalogs
            val matchedSportCats = allCats.filter { cat ->
                val id = cat.id?.lowercase() ?: ""
                val name = cat.name?.lowercase() ?: ""
                val text = "$id $name"
                when {
                    leagueLower == "nfl" -> {
                        text.contains("american_football") || text.contains("nfl")
                    }
                    leagueLower.contains("ncaa") || sportLower.contains("college football") -> {
                        text.contains("american_football") || text.contains("college") || text.contains("ncaa")
                    }
                    sportLower.contains("football") -> {
                        text.contains("american_football") || text.contains("nfl") || text.contains("college")
                    }
                    leagueLower == "nba" || leagueLower.contains("ncaab") || sportLower.contains("basket") -> {
                        text.contains("basket") || text.contains("nba") || text.contains("ncaab")
                    }
                    leagueLower == "mlb" || sportLower.contains("base") -> {
                        text.contains("base") || text.contains("mlb")
                    }
                    leagueLower == "nhl" || sportLower.contains("hock") -> {
                        text.contains("hock") || text.contains("nhl")
                    }
                    leagueLower in listOf("epl", "la liga", "champions league", "serie a", "mls", "soccer") || sportLower.contains("socc") -> {
                        (text.contains("football") && !text.contains("american")) || text.contains("socc") || text.contains("epl") || text.contains("mls")
                    }
                    sportLower.contains("fight") || sportLower.contains("ufc") || sportLower.contains("box") -> {
                        text.contains("fight") || text.contains("ufc") || text.contains("box") || text.contains("mma")
                    }
                    sportLower.contains("motor") || sportLower.contains("f1") || sportLower.contains("nascar") -> {
                        text.contains("motor") || text.contains("f1") || text.contains("nascar") || text.contains("racing")
                    }
                    else -> false
                }
            }
            targetCatalogs.addAll(matchedSportCats)

            // 3. Fallback: if no catalogs matched, or addon has <= 3 catalogs, include all sport / tv catalogs
            if (targetCatalogs.isEmpty() || allCats.size <= 3) {
                targetCatalogs.addAll(allCats.filter { (it.type == "sport" || it.type == "tv" || it.type == "events") })
            }

            val uniqueTargetCatalogs = targetCatalogs.distinctBy { it.id }

            val homeName = event.homeTeam?.name?.trim() ?: ""
            val awayName = event.awayTeam?.name?.trim() ?: ""
            val homeAbbr = event.homeTeam?.abbreviation?.trim()?.lowercase() ?: ""
            val awayAbbr = event.awayTeam?.abbreviation?.trim()?.lowercase() ?: ""

            val homeKeywords = extractKeywords(homeName)
            val awayKeywords = extractKeywords(awayName)

            Log.d(TAG, "Resolving addon '$addonName' for '${event.name}'. Catalogs: ${uniqueTargetCatalogs.map { it.id }}")

            // Query matched catalogs concurrently
            val catalogDeferreds = uniqueTargetCatalogs.map { cat ->
                async {
                    val metas = mutableListOf<StremioMetaItem>()
                    val catType = cat.type ?: "sport"
                    val catId = cat.id ?: return@async metas

                    try {
                        val catUrl = "${baseUrl}catalog/$catType/$catId.json"
                        val catResponse = requestSemaphore.withPermit { api.getCatalogByUrl(catUrl) }
                        catResponse.metas?.forEach { meta ->
                            if (isMatch(meta, homeKeywords, awayKeywords, homeAbbr, awayAbbr, event.name)) {
                                metas.add(meta)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error fetching catalog $catType/$catId from $addonName: ${e.message}")
                    }

                    // If no direct matches in catalog, try searching primary keywords
                    if (metas.isEmpty() && (homeKeywords.isNotEmpty() || awayKeywords.isNotEmpty())) {
                        val searchQueries = listOfNotNull(homeKeywords.firstOrNull(), awayKeywords.firstOrNull())
                        for (query in searchQueries) {
                            try {
                                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                                val searchUrl = "${baseUrl}catalog/$catType/$catId/search=$encodedQuery.json"
                                val searchResponse = requestSemaphore.withPermit { api.getCatalogByUrl(searchUrl) }
                                searchResponse.metas?.forEach { meta ->
                                    if (isMatch(meta, homeKeywords, awayKeywords, homeAbbr, awayAbbr, event.name)) {
                                        metas.add(meta)
                                    }
                                }
                                if (metas.isNotEmpty()) break
                            } catch (e: Exception) {
                                Log.d(TAG, "Search query '$query' failed for $catType/$catId in $addonName: ${e.message}")
                            }
                        }
                    }
                    metas
                }
            }

            val matchedMetas = catalogDeferreds.awaitAll().flatten().distinctBy { it.id }
            Log.d(TAG, "Addon '$addonName' matched ${matchedMetas.size} meta items")

            // Fetch streams concurrently for matched metas
            val streamDeferreds = matchedMetas.map { meta ->
                async {
                    try {
                        val streamType = meta.type ?: "sport"
                        val streamUrl = "${baseUrl}stream/$streamType/${meta.id}.json"
                        val streamResponse = requestSemaphore.withPermit { api.getStreamsByUrl(streamUrl) }
                        streamResponse.streams.orEmpty().mapNotNull { toOption(it, meta, addonName) }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error fetching streams for meta ${meta.id} in $addonName: ${e.message}")
                        emptyList()
                    }
                }
            }

            streamDeferreds.awaitAll().flatten()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving addon $addonUrl", e)
            emptyList()
        }
    }

    override suspend fun searchStreams(query: String): List<StremioStreamOption> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(8_000L) {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                preferencesManager.stremioAddonUrls.map { addonUrl ->
                    async {
                        val manifestUrl = if (addonUrl.endsWith("manifest.json")) addonUrl else "${addonUrl.removeSuffix("/")}/manifest.json"
                        val baseUrl = manifestUrl.substringBeforeLast("manifest.json")
                        try {
                            val cached = manifestCache[manifestUrl]
                                ?.takeIf { System.currentTimeMillis() - it.fetchedAt < MANIFEST_TTL_MS }
                                ?.value
                            val manifest = cached ?: requestSemaphore.withPermit { api.getManifest(manifestUrl) }.also {
                                manifestCache[manifestUrl] = TimedManifest(it, System.currentTimeMillis())
                            }
                            val addonName = manifest.name ?: "Stremio"
                            manifest.catalogs.orEmpty().filter { it.id != null }.take(4).map { catalog ->
                                async {
                                    try {
                                        val type = catalog.type ?: "sport"
                                        val response = requestSemaphore.withPermit {
                                            api.getCatalogByUrl("${baseUrl}catalog/$type/${catalog.id}/search=$encoded.json")
                                        }
                                        response.metas.orEmpty().take(8).map { meta ->
                                            async {
                                                try {
                                                    val streamType = meta.type ?: type
                                                    val streams = requestSemaphore.withPermit {
                                                        api.getStreamsByUrl("${baseUrl}stream/$streamType/${meta.id}.json")
                                                    }
                                                    streams.streams.orEmpty().mapNotNull { toOption(it, meta, addonName) }
                                                } catch (_: Exception) { emptyList() }
                                            }
                                        }.awaitAll().flatten()
                                    } catch (_: Exception) { emptyList() }
                                }
                            }.awaitAll().flatten()
                        } catch (_: Exception) { emptyList() }
                    }
                }.awaitAll().flatten().distinctBy { it.streamUrl }
            } ?: emptyList()
        }
    }

    private fun toOption(stream: StremioStream, meta: StremioMetaItem, addonName: String): StremioStreamOption? {
        val mediaUrl = stream.url?.takeIf { it.isNotBlank() }
        val externalPageUrl = stream.externalUrl?.takeIf { it.isNotBlank() }
        val selectedUrl = mediaUrl ?: externalPageUrl ?: return null
        val isDirectPlayable = mediaUrl != null
        val availabilityText = listOfNotNull(stream.title, stream.name, stream.description).joinToString(" ")
        val blocked = (!selectedUrl.startsWith("http://") && !selectedUrl.startsWith("https://")) ||
                selectedUrl.contains("youtube.com", ignoreCase = true) ||
                availabilityText.contains("🔒") ||
                availabilityText.contains("upgrade to", ignoreCase = true) ||
                availabilityText.contains("premium required", ignoreCase = true)
        if (blocked) return null

        // Stremio's `title` is the add-on supplied stream name and commonly
        // carries the edition, resolution, language, and provider details.
        // Preserve it in full for the TV source picker.
        val title = stream.title?.takeIf { it.isNotBlank() }?.trim()
            ?: stream.name?.takeIf { it.isNotBlank() }?.trim()
            ?: meta.name?.takeIf { it.isNotBlank() }?.trim()
            ?: "Live Stream"
        val description = listOfNotNull(stream.description, stream.name, meta.description)
            .filterNot { it.trim() == title }
            .firstOrNull { it.isNotBlank() }.orEmpty()
        val fullText = "$description $title".uppercase()
        val bitrate = BITRATE_PATTERN.find(fullText)?.value
        val isHdr = HDR_PATTERN.containsMatchIn(fullText)
        val quality = when {
            UHD_PATTERN.containsMatchIn(fullText) -> if (isHdr) "4K HDR" else "4K"
            FHD_PATTERN.containsMatchIn(fullText) -> if (isHdr) "1080p HDR" else "1080p"
            HD_PATTERN.containsMatchIn(fullText) -> "720p"
            else -> null
        }
        return StremioStreamOption(
            title = title,
            description = description.ifBlank { null },
            streamUrl = selectedUrl,
            quality = quality,
            bitrate = bitrate,
            addonName = addonName,
            headers = stream.behaviorHints?.proxyHeaders?.request,
            isDirectPlayable = isDirectPlayable
        )
    }

    private fun extractKeywords(text: String): List<String> {
        val stopwords = setOf(
            "at", "vs", "versus", "the", "and", "state", "university", "college",
            "club", "fc", "sc", "united", "city", "real", "athletic", "st", "men", "women"
        )
        return text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopwords }
    }

    private fun isMatch(
        meta: StremioMetaItem,
        homeKeywords: List<String>,
        awayKeywords: List<String>,
        homeAbbr: String,
        awayAbbr: String,
        eventName: String
    ): Boolean {
        val metaName = meta.name?.lowercase() ?: ""
        val metaDesc = meta.description?.lowercase() ?: ""
        val fullText = "$metaName $metaDesc"

        // 1. Dual team keyword match (e.g. "delaware" AND "merrimack")
        val hasHome = homeKeywords.any { fullText.contains(it) }
        val hasAway = awayKeywords.any { fullText.contains(it) }
        if (hasHome && hasAway) return true

        // 2. Abbr match if abbreviations are distinct (e.g. "del" and "mrmk")
        if (homeAbbr.length >= 3 && awayAbbr.length >= 3) {
            if (fullText.contains(homeAbbr) && fullText.contains(awayAbbr)) return true
        }

        // 3. Direct event name containment
        val cleanEvent = eventName.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
        val cleanMeta = metaName.replace(Regex("[^a-z0-9 ]"), " ").trim()
        if (cleanEvent.isNotEmpty() && cleanMeta.isNotEmpty()) {
            if (cleanEvent.contains(cleanMeta) || cleanMeta.contains(cleanEvent)) return true
        }

        return false
    }
}
