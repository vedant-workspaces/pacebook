package com.pacelog.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Monday of the Monday→Sunday week containing this date. */
fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
fun LocalDate.monthStart(): LocalDate = withDayOfMonth(1)
fun LocalDate.yearStart(): LocalDate = withDayOfYear(1)
fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

/** Half-open date range [from, toExclusive); null bounds are open. */
data class DateRange(val from: LocalDate?, val toExclusive: LocalDate?) {
    operator fun contains(d: LocalDate) =
        (from == null || !d.isBefore(from)) && (toExclusive == null || d.isBefore(toExclusive))
}

enum class Period(val label: String) {
    WEEK("This week"), MONTH("This month"), YEAR("This year"), ALL("All time");

    fun range(today: LocalDate): DateRange = when (this) {
        WEEK -> today.weekStart().let { DateRange(it, it.plusDays(7)) }
        MONTH -> today.monthStart().let { DateRange(it, it.plusMonths(1)) }
        YEAR -> today.yearStart().let { DateRange(it, it.plusYears(1)) }
        ALL -> DateRange(null, null)
    }
}
