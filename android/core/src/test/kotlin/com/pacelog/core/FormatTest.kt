package com.pacelog.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun pace() {
        assertEquals("5:30/km", formatPace(330))
        assertEquals("3:30/km", formatPace(210))
        assertEquals("10:00/km", formatPace(600))
        assertEquals("5:48/km", formatPace(347.6))
        assertEquals("—", formatPace(null))
        assertEquals(Parsed.Value(330), parsePace("5", "30"))
        assertEquals(Parsed.Value(600), parsePace("10", ""))
        assertEquals(Parsed.Empty, parsePace("", ""))
        assertEquals(Parsed.Invalid, parsePace("5", "75"))
        assertEquals("6" to "15", splitPace(375))
    }

    @Test
    fun numbers() {
        assertEquals(Parsed.Value(8.4), parseDecimal("8,4"))
        assertEquals("+0.4 km", formatSignedKm(0.4))
        assertEquals("−3.6 km", formatSignedKm(-3.6))
        assertEquals("±0 km", formatSignedKm(0.01))
        assertEquals("1,245.4", formatKm(1245.4))
        assertEquals("12", formatKm(12.0))
        assertEquals("45 min", formatDuration(2700))
        assertEquals("1:30:00", formatClock(5400.0))
    }

    @Test
    fun mondayWeeks() {
        assertEquals(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04").weekStart())
        assertEquals(LocalDate.parse("2026-12-28"), LocalDate.parse("2027-01-01").weekStart())
    }
}
