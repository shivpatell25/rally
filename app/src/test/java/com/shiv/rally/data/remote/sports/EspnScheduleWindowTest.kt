package com.shiv.rally.data.remote.sports

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EspnScheduleWindowTest {

    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun `daily leagues request the explicit local date`() {
        assertEquals("20260916", scoreboardDateQuery("MLB", today))
    }

    @Test
    fun `football leagues use the weekly scoreboard`() {
        assertEquals(null, scoreboardDateQuery("NFL", today))
        assertEquals(null, scoreboardDateQuery("NCAAF", today))
    }

    @Test
    fun `empty daily leagues fall back to the current schedule month`() {
        assertEquals(listOf("202609"), scoreboardFallbackMonthQueries("NBA", today))
    }

    @Test
    fun `fallback includes next month when the seven day window crosses a boundary`() {
        val monthEnd = LocalDate.of(2026, 9, 28)
        assertEquals(listOf("202609", "202610"), scoreboardFallbackMonthQueries("EPL", monthEnd))
    }
}
