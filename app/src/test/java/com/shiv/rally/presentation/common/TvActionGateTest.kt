package com.shiv.rally.presentation.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvActionGateTest {
    @Test
    fun `blocks rapid repeats but keeps independent actions responsive`() {
        var now = 1_000L
        val gate = TvActionGate(minimumIntervalMs = 250L, clock = { now })

        assertTrue(gate.tryAcquire("open-event"))
        assertFalse(gate.tryAcquire("open-event"))
        assertTrue(gate.tryAcquire("open-search"))

        now += 249L
        assertFalse(gate.tryAcquire("open-event"))
        now += 1L
        assertTrue(gate.tryAcquire("open-event"))
    }

    @Test
    fun `survives a large remote input burst without leaking duplicate actions`() {
        var now = 10_000L
        val gate = TvActionGate(minimumIntervalMs = 100L, clock = { now })
        var accepted = 0
        repeat(5_000) {
            if (gate.tryAcquire("open-card")) accepted++
            now += 1L
        }
        assertTrue(accepted in 49..51)
        assertTrue(gate.tryAcquire("open-menu"))
    }
}
