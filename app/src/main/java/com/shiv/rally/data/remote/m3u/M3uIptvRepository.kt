package com.shiv.rally.data.remote.m3u

import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.repository.IptvRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

class M3uIptvRepository @Inject constructor(
    private val preferences: PreferencesManager,
    private val source: M3uPlaylistSource
) : IptvRepository {
    private data class Catalog(val identity: Pair<String, String>, val channels: List<IptvChannel>, val loadedAt: Long)
    @Volatile private var catalog: Catalog? = null
    private val mutex = Mutex()

    override suspend fun authenticate(): Boolean = try { getChannels().isNotEmpty() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }

    override suspend fun getChannels(): List<IptvChannel> = withContext(Dispatchers.IO) {
        val identity = preferences.m3uPlaylistUrl.trim() to preferences.m3uPlaylistName.trim()
        if (identity.first.isBlank()) return@withContext emptyList()
        mutex.withLock {
            catalog?.takeIf { it.identity == identity && System.currentTimeMillis() - it.loadedAt < 15 * 60 * 1000 }
                ?.let { return@withLock it.channels }
            val channels = source.load(identity.first, identity.second)
            if (identity.first == preferences.m3uPlaylistUrl.trim() && identity.second == preferences.m3uPlaylistName.trim()) {
                catalog = Catalog(identity, channels, System.currentTimeMillis())
            }
            channels
        }
    }

    override suspend fun refreshChannels(): List<IptvChannel> { clearMemoryCache(); return getChannels() }
    override suspend fun getChannelStreamUrl(channelId: String): String =
        getChannels().firstOrNull { it.id == channelId }?.streamUrl ?: error("This playlist channel is no longer available. Refresh Live TV.")
    override suspend fun getChannelStreamHeaders(channelId: String): Map<String, String> =
        getChannels().firstOrNull { it.id == channelId }?.streamHeaders.orEmpty()
    override suspend fun getChannelStreamMimeType(channelId: String): String? =
        getChannels().firstOrNull { it.id == channelId }?.streamMimeType
    override fun clearMemoryCache() { catalog = null }
}
