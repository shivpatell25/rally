package com.shiv.rally.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCapabilitiesTest {
    @Test
    fun capableHardwareKeeps4kHdrAvailable() {
        val profile = choosePlaybackProfile(
            displaySupportsHdr = true,
            supports4k30 = true,
            supports4k60 = true,
            isLowRamDevice = false
        )

        assertEquals(3840, profile.maxVideoWidth)
        assertEquals(2160, profile.maxVideoHeight)
        assertEquals(60, profile.maxVideoFrameRate)
        assertTrue(profile.displaySupportsHdr)
        assertTrue(profile.supportsHardware4k)
    }

    @Test
    fun lowRamDeviceUsesStable1080pCeiling() {
        val profile = choosePlaybackProfile(
            displaySupportsHdr = true,
            supports4k30 = true,
            supports4k60 = true,
            isLowRamDevice = true
        )

        assertEquals(1920, profile.maxVideoWidth)
        assertEquals(1080, profile.maxVideoHeight)
        assertEquals(15_000_000, profile.maxVideoBitrate)
        assertFalse(profile.supportsHardware4k)
    }

    @Test
    fun thirtyFpsDecoderDoesNotReceive4k60Track() {
        val profile = choosePlaybackProfile(
            displaySupportsHdr = false,
            supports4k30 = true,
            supports4k60 = false,
            isLowRamDevice = false
        )

        assertEquals(3840, profile.maxVideoWidth)
        assertEquals(30, profile.maxVideoFrameRate)
        assertFalse(profile.displaySupportsHdr)
    }
}
