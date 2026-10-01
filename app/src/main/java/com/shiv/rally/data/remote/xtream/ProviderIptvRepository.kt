package com.shiv.rally.data.remote.xtream

import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.IptvProvider
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.data.remote.stalker.StalkerIptvRepositoryImpl
import com.shiv.rally.data.remote.m3u.M3uIptvRepository
import javax.inject.Inject

/** Keeps all existing callers provider-agnostic. */
class ProviderIptvRepository @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val stalker: StalkerIptvRepositoryImpl,
    private val xtream: XtreamIptvRepositoryImpl,
    private val m3u: M3uIptvRepository
) : IptvRepository {
    private val active: IptvRepository
        get() = when (preferencesManager.iptvProvider) {
            IptvProvider.STALKER -> stalker
            IptvProvider.XTREAM -> xtream
            IptvProvider.M3U -> m3u
        }

    override suspend fun authenticate(): Boolean = active.authenticate()
    override suspend fun getChannels(): List<IptvChannel> = active.getChannels()
    override suspend fun refreshChannels(): List<IptvChannel> = active.refreshChannels()
    override suspend fun searchChannels(query: String, limit: Int): List<IptvChannel> = active.searchChannels(query, limit)
    override suspend fun getChannelStreamUrl(channelId: String): String = active.getChannelStreamUrl(channelId)
    override suspend fun getChannelStreamHeaders(channelId: String): Map<String, String> = active.getChannelStreamHeaders(channelId)
    override suspend fun getChannelStreamMimeType(channelId: String): String? = active.getChannelStreamMimeType(channelId)
    override suspend fun getChannelGuide(channelId: String): ChannelGuide? = active.getChannelGuide(channelId)
    override fun clearMemoryCache() {
        stalker.clearMemoryCache()
        xtream.clearMemoryCache()
        m3u.clearMemoryCache()
    }
}
