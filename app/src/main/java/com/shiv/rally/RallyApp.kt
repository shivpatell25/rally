package com.shiv.rally

import android.app.Application
import android.content.ComponentCallbacks2
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.shiv.rally.domain.repository.IptvRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RallyApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var iptvRepository: IptvRepository

    @Inject
    lateinit var diagnostics: com.shiv.rally.data.local.RallyDiagnostics

    override fun onCreate() {
        super.onCreate()
        diagnostics.installCrashHandler()
        diagnostics.record(
            kind = "App session",
            message = "Rally ${BuildConfig.VERSION_NAME} started",
            detail = "Android ${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}"
        )
    }

    override fun newImageLoader(): ImageLoader = imageLoader

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            diagnostics.record("Memory pressure", "Released image and channel caches", "Trim level $level")
            imageLoader.memoryCache?.clear()
            iptvRepository.clearMemoryCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        diagnostics.record("Memory pressure", "System reported low memory; caches released")
        imageLoader.memoryCache?.clear()
        iptvRepository.clearMemoryCache()
    }
}
