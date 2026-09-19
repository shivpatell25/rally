package com.shiv.rally.presentation.player

import android.media.audiofx.DynamicsProcessing
import android.os.Build

/** Optional lightweight limiter that smooths abrupt level jumps between sports feeds. */
internal class AudioNormalizationSession {
    private var effect: DynamicsProcessing? = null

    fun attach(audioSessionId: Int, channelCount: Int = 2) {
        release()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || audioSessionId <= 0) return
        runCatching {
            val channels = channelCount.coerceIn(1, 2)
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                channels,
                false,
                0,
                false,
                0,
                false,
                0,
                true
            ).build()
            DynamicsProcessing(0, audioSessionId, config).also { processor ->
                repeat(channels) { channel ->
                    processor.setLimiterByChannelIndex(
                        channel,
                        DynamicsProcessing.Limiter(
                            true,
                            true,
                            0,
                            3f,
                            80f,
                            10f,
                            -2.5f,
                            0f
                        )
                    )
                }
                processor.enabled = true
                effect = processor
            }
        }
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
    }
}
