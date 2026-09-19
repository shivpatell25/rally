package com.shiv.rally.presentation.player

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display

internal data class PlaybackProfile(
    val maxVideoWidth: Int,
    val maxVideoHeight: Int,
    val maxVideoBitrate: Int,
    val maxVideoFrameRate: Int,
    val displaySupportsHdr: Boolean,
    val supportsHardware4k: Boolean,
    val deviceClass: TvDeviceClass = TvDeviceClass.STANDARD,
    val multiViewMaxTiles: Int = 4,
    val targetBufferBytes: Int = 12 * 1024 * 1024
)

internal enum class TvDeviceClass(val displayName: String) {
    LOW_POWER("Low-power TV"),
    STANDARD("Standard TV"),
    PREMIUM("Premium 4K TV")
}

internal fun choosePlaybackProfile(
    displaySupportsHdr: Boolean,
    supports4k30: Boolean,
    supports4k60: Boolean,
    isLowRamDevice: Boolean,
    deviceClass: TvDeviceClass = if (isLowRamDevice) TvDeviceClass.LOW_POWER else TvDeviceClass.STANDARD
): PlaybackProfile {
    val allow4k = supports4k30 && !isLowRamDevice
    return PlaybackProfile(
        maxVideoWidth = if (allow4k) 3840 else 1920,
        maxVideoHeight = if (allow4k) 2160 else 1080,
        maxVideoBitrate = if (allow4k) 35_000_000 else 15_000_000,
        maxVideoFrameRate = if (allow4k && !supports4k60) 30 else 60,
        displaySupportsHdr = displaySupportsHdr,
        supportsHardware4k = allow4k,
        deviceClass = deviceClass,
        multiViewMaxTiles = when (deviceClass) {
            TvDeviceClass.LOW_POWER -> 2
            TvDeviceClass.STANDARD -> 3
            TvDeviceClass.PREMIUM -> 4
        },
        targetBufferBytes = if (deviceClass == TvDeviceClass.LOW_POWER) 8 * 1024 * 1024 else 16 * 1024 * 1024
    )
}

@Suppress("DEPRECATION")
internal fun resolvePlaybackProfile(context: Context): PlaybackProfile {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val display = context.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
    val displaySupportsHdr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
    val decoderSupport = inspectHardware4kSupport()
    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL} ${Build.DEVICE}".lowercase()
    val deviceClass = when {
        activityManager.isLowRamDevice || deviceName.contains("chromecast") || deviceName.contains("sabrina") -> TvDeviceClass.LOW_POWER
        deviceName.contains("shield") || deviceName.contains("nvidia") -> TvDeviceClass.PREMIUM
        else -> TvDeviceClass.STANDARD
    }
    return choosePlaybackProfile(
        displaySupportsHdr = displaySupportsHdr,
        supports4k30 = decoderSupport.first,
        supports4k60 = decoderSupport.second,
        isLowRamDevice = deviceClass == TvDeviceClass.LOW_POWER,
        deviceClass = deviceClass
    )
}

private fun inspectHardware4kSupport(): Pair<Boolean, Boolean> {
    val mimeTypes = buildList {
        add(MediaFormat.MIMETYPE_VIDEO_HEVC)
        add(MediaFormat.MIMETYPE_VIDEO_VP9)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaFormat.MIMETYPE_VIDEO_AV1)
    }
    var supports30 = false
    var supports60 = false
    runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.forEach { codec ->
            if (codec.isEncoder || codec.isSoftwareCodec()) return@forEach
            val type = codec.supportedTypes.firstOrNull { supported ->
                mimeTypes.any { it.equals(supported, ignoreCase = true) }
            } ?: return@forEach
            val videoCapabilities = runCatching { codec.getCapabilitiesForType(type).videoCapabilities }.getOrNull()
                ?: return@forEach
            supports30 = supports30 || runCatching {
                videoCapabilities.areSizeAndRateSupported(3840, 2160, 30.0)
            }.getOrDefault(false)
            supports60 = supports60 || runCatching {
                videoCapabilities.areSizeAndRateSupported(3840, 2160, 60.0)
            }.getOrDefault(false)
        }
    }
    return supports30 to supports60
}

private fun MediaCodecInfo.isSoftwareCodec(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return isSoftwareOnly
    val normalized = name.lowercase()
    return normalized.startsWith("omx.google.") || normalized.startsWith("c2.android.") ||
        normalized.contains("software") || normalized.contains("sw.")
}
