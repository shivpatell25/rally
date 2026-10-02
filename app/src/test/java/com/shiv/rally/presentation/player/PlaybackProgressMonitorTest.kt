package com.shiv.rally.presentation.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackProgressMonitorTest {
    @Test fun timesOutStartupAndBufferingAfterPlayback() {
        val monitor = PlaybackProgressMonitor(0)
        assertNull(monitor.check(24_000, true, true, false, 0, null))
        assertEquals(PlaybackStallReason.STARTUP, monitor.check(25_000, true, true, false, 0, null))
        assertNull(monitor.check(26_000, true, false, true, 1_000, 30))
        assertNull(monitor.check(40_000, true, true, false, 1_000, 30))
        assertEquals(PlaybackStallReason.BUFFERING, monitor.check(41_000, true, true, false, 1_000, 30))
    }
    @Test fun detectsFrozenVideoEvenWhileAudioTimeMoves() {
        val monitor = PlaybackProgressMonitor(0)
        assertNull(monitor.check(1_000, true, false, true, 1_000, 30))
        assertNull(monitor.check(10_000, true, false, true, 10_000, 30))
        assertEquals(PlaybackStallReason.VIDEO, monitor.check(16_000, true, false, true, 16_000, 30))
    }
    @Test fun ignoresPauseBackgroundEndAndAudioOnlyProgress() {
        val monitor = PlaybackProgressMonitor(0)
        assertNull(monitor.check(100_000, false, false, true, 0, 0))
        assertNull(monitor.check(101_000, true, false, true, 1_000, 30))
        assertNull(monitor.check(200_000, true, false, false, 1_000, 30))
        val audio = PlaybackProgressMonitor(0)
        assertNull(audio.check(1_000, true, false, true, 1_000, null))
        assertNull(audio.check(80_000, true, false, true, 80_000, null))
    }
}
