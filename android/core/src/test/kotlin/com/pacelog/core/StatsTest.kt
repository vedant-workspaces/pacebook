package com.pacelog.core

import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsTest {
    private val d = { s: String -> LocalDate.parse(s) }
    private fun near(expected: Double, actual: Double?, label: String) =
        assertTrue(actual != null && abs(expected - actual) < 1e-6, "$label: expected $expected, got $actual")

    private fun actual(date: String, km: Double, pace: Int? = 330, id: Long = date.hashCode().toLong() + km.toLong()) =
        Actual(id, null, d(date), "easy_run", "Run", km, pace, null, null, 0)

    @Test
    fun mileageTotalsUseMondayWeeks() {
        val acts = listOf(
            actual("2026-10-04", 8.4), actual("2026-10-04", 4.2),
            actual("2026-09-28", 10.0), // Monday, same week
            actual("2026-09-27", 20.0), // Sunday of the previous week
            actual("2026-10-05", 6.0), // next week (future)
            actual("2026-01-15", 5.0), actual("2025-12-31", 7.0),
        )
        val today = d("2026-10-04")
        val week = mileageSeries(SeriesRange.WEEK, acts, emptyList(), today)
        assertEquals(7, week.size)
        assertEquals(d("2026-09-28"), week.first().date)
        near(22.6, week.sumOf { it.actualKm }, "weekly")
        near(18.6, mileageSeries(SeriesRange.MONTH, acts, emptyList(), today).sumOf { it.actualKm }, "monthly")
        val year = mileageSeries(SeriesRange.YEAR, acts, emptyList(), today)
        assertEquals(12, year.size)
        near(53.6, year.sumOf { it.actualKm }, "yearly")
        near(30.0, year[8].actualKm, "september")

        val t = periodTotals(acts, today)
        near(22.6, t.week.km, "week to date")
        near(12.6, t.month.km, "month to date")
        near(47.6, t.year.km, "year to date")
        near(60.6, t.allTime.km, "all time")
        near(20.0, aggregates(acts).longest?.distanceKm, "longest")
    }

    @Test
    fun weightedPaceIsTotalTimeOverDistance() {
        val acts = listOf(actual("2026-10-01", 5.0, 300), actual("2026-10-02", 20.0, 360))
        near(348.0, weightedPace(acts), "weighted pace") // not (300+360)/2 = 330
        near(300.0, aggregates(acts).fastest?.paceSecPerKm?.toDouble(), "fastest")
    }

    @Test
    fun completionAndPlannedVsActual() {
        fun p(id: Long, date: String, type: String, km: Double?, status: String) =
            Planned(id, 1, d(date), type, type, null, km, null, null, status, null, null)
        val planned = listOf(
            p(1, "2026-09-28", "easy_run", 8.0, Status.COMPLETED),
            p(2, "2026-09-29", "intervals", 10.0, Status.MODIFIED),
            p(3, "2026-09-30", "rest", null, Status.PLANNED),
            p(4, "2026-10-01", "tempo", 8.0, Status.SKIPPED),
            p(5, "2026-10-03", "long_run", 24.0, Status.PLANNED),
            p(6, "2026-10-06", "easy_run", 10.0, Status.PLANNED), // future
        )
        val acts = listOf(actual("2026-09-28", 8.2), actual("2026-09-29", 7.0), actual("2026-10-04", 5.0))
        val today = d("2026-10-04")

        val week = periodPlanSummary(Period.WEEK, planned, acts, today)
        near(42.0, week.plannedKm, "planned excludes skipped")
        near(20.2, week.actualKm, "actual")
        near(50.0, week.completionPct, "completion: 2 done of 4 due, rest ignored")

        val block = Block(1, "B", null, d("2026-09-28"), d("2026-10-11"), null)
        val s = blockStats(block, planned, acts, today)
        assertEquals(listOf(1, 1, 1, 2), listOf(s.summary.completed, s.summary.modified, s.summary.skipped, s.summary.planned))
        near(52.0, s.summary.plannedKm, "block planned")
        near(50.0, s.summary.completionPct, "block completion ignores future")
        assertEquals(2, s.weeks.size)
        near(10.0, s.weeks[1].plannedKm, "week 2 planned")
    }
}
