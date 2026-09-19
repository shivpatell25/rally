package com.shiv.rally.di

import android.app.ActivityManager
import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.shiv.rally.data.remote.network.ResilientDns
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        resilientDns: ResilientDns
    ): ImageLoader {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val constrainedDevice = activityManager?.isLowRamDevice == true ||
            (activityManager?.memoryClass ?: 256) <= 256
        val memoryCacheBytes = if (constrainedDevice) 16 * 1024 * 1024 else 32 * 1024 * 1024
        return ImageLoader.Builder(context)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .dns(resilientDns)
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
            }
            .dispatcher(kotlinx.coroutines.Dispatchers.IO)
            .interceptorDispatcher(kotlinx.coroutines.Dispatchers.IO)
            .fetcherDispatcher(kotlinx.coroutines.Dispatchers.IO)
            .decoderDispatcher(kotlinx.coroutines.Dispatchers.IO)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(memoryCacheBytes)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(48L * 1024 * 1024)
                    .build()
            }
            // Team and league marks are effectively immutable. Keep showing the downloaded
            // artwork even when a CDN advertises a very short revalidation window.
            .respectCacheHeaders(false)
            .allowHardware(true)
            .allowRgb565(true)
            .crossfade(false) // Disable crossfade for snappier TV feel
            .build()
    }
}
