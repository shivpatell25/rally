package com.shiv.rally.presentation.common

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

class TvActionGate(
    private val minimumIntervalMs: Long = 250L,
    private val clock: () -> Long = SystemClock::elapsedRealtime
) {
    private val lastActionAt = ConcurrentHashMap<String, Long>()

    fun tryAcquire(action: String): Boolean {
        val now = clock()
        var accepted = false
        lastActionAt.compute(action) { _, previous ->
            if (previous == null || now - previous >= minimumIntervalMs) {
                accepted = true
                now
            } else {
                previous
            }
        }
        return accepted
    }
}
