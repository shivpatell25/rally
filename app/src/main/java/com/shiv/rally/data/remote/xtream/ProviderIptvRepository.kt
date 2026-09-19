package com.shiv.rally.data.remote.xtream

import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.IptvProvider
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.data.remote.stalker.StalkerIptvRepositoryImpl
import javax.inject.Inject

/** Keeps all existing callers provider-agnostic. */
class ProviderIptvRepository @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val stalker: StalkerIptvRepositoryImpl,
    private val xtream: XtreamIptvRepositoryImpl
) : IptvRepository {
    private val active: IptvRepository
        get() = if (preferencesManager.iptvProvider == IptvProvider.XTREAM) xtream else stalker

    override suspend fun authenticate(): Boolean = active.authenticate()
    override suspend fun getChannels(): List<IptvChannel> = active.getChannels()
    override suspend fun refreshChannels(): List<IptvChannel> = active.refreshChannels()
    override suspend fun searchChannels(query: String, limit: Int): List<IptvChannel> = active.searchChannels(query, limit)
    override suspend fun getChannelStreamUrl(channelId: String): String = active.getChannelStreamUrl(channelId)
    override suspend fun getChannelGuide(channelId: String): ChannelGuide? = active.getChannelGuide(channelId)
    override fun clearMemoryCache() = active.clearMemoryCache()
}
