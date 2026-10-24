package com.pacelog.core

import java.time.LocalDate

/*
 * Statistics are computed from actual activities only; planned mileage is
 * always computed separately. Weeks run Monday → Sunday everywhere.
 *
 * Completion (v1): (completed + modified) ÷ non-rest planned activities that
 * are "due" — dated today or earlier, or already resolved (completed,
 * modified, skipped). Rest days never count; future sessions never count
 * against the runner. Planned mileage excludes skipped sessions.
 */

data class PlanSummary(
    val totalActivities: Int,
    val sessionCount: Int,
    val planned: Int,
    val completed: Int,
    val modified: Int,
    val skipped: Int,
    val due: Int,
    val plannedKm: Double,
    val actualKm: Double,
) {
    val done get() = completed + modified
    val completionPct: Double? get() = if (due > 0) done * 100.0 / due else null
    val differenceKm get() = actualKm - plannedKm
}

fun Iterable<Actual>.inRange(r: DateRange) = filter { it.date in r }
fun Iterable<Planned>.plannedInRange(r: DateRange) = filter { it.date in r }
fun Iterable<Actual>.totalKm() = sumOf { it.distanceKm ?: 0.0 }

/** Planned mileage: sum of planned distances, excluding skipped sessions. */
fun Iterable<Planned>.plannedKm() = filter { it.status != Status.SKIPPED }.sumOf { it.plannedDistanceKm ?: 0.0 }

fun planSummary(planned: List<Planned>, actualKm: Double, today: LocalDate): PlanSummary {
    val sessions = planned.filter { !it.isRest }
    return PlanSummary(
        totalActivities = planned.size,
        sessionCount = sessions.size,
        planned = sessions.count { it.status == Status.PLANNED },
        completed = sessions.count { it.status == Status.COMPLETED },
        modified = sessions.count { it.status == Status.MODIFIED },
        skipped = sessions.count { it.status == Status.SKIPPED },
        due = sessions.count { it.status != Status.PLANNED || !it.date.isAfter(today) },
        plannedKm = planned.plannedKm(),
        actualKm = actualKm,
    )
}

/** Weighted average pace: total time ÷ total distance, never a mean of paces. */
fun weightedPace(actuals: Iterable<Actual>): Double? {
    var seconds = 0.0
    var km = 0.0
    for (a in actuals) {
        val d = a.distanceKm ?: continue
        val p = a.paceSecPerKm ?: continue
        seconds += d * p
        km += d
    }
    return if (km > 0) seconds / km else null
}

fun movingTimeSec(actuals: Iterable<Actual>) =
    actuals.sumOf { a -> if (a.distanceKm != null && a.paceSecPerKm != null) a.distanceKm * a.paceSecPerKm else 0.0 }

data class Totals(val km: Double, val count: Int)

fun totals(actuals: List<Actual>, r: DateRange) = actuals.inRange(r).let { Totals(it.totalKm(), it.size) }

data class PeriodTotals(val week: Totals, val month: Totals, val year: Totals, val allTime: Totals)

/** Totals for the week, month and year containing today (up to today) and all time. */
fun periodTotals(actuals: List<Actual>, today: LocalDate): PeriodTotals {
    val end = today.plusDays(1)
    return PeriodTotals(
        week = totals(actuals, DateRange(today.weekStart(), end)),
        month = totals(actuals, DateRange(today.monthStart(), end)),
        year = totals(actuals, DateRange(today.yearStart(), end)),
        allTime = totals(actuals, DateRange(null, null)),
    )
}

data class Aggregates(
    val km: Double,
    val count: Int,
    val avgDistanceKm: Double?,
    val avgPaceSecPerKm: Double?,
    val avgHeartRate: Double?,
    val movingTimeSec: Double,
    val longest: Actual?,
    val fastest: Actual?,
)

fun aggregates(actuals: List<Actual>): Aggregates {
    val withDistance = actuals.mapNotNull { it.distanceKm }
    val hrs = actuals.mapNotNull { it.avgHeartRate }
    return Aggregates(
        km = withDistance.sum(),
        count = actuals.size,
        avgDistanceKm = withDistance.takeIf { it.isNotEmpty() }?.average(),
        avgPaceSecPerKm = weightedPace(actuals),
        avgHeartRate = hrs.takeIf { it.isNotEmpty() }?.average(),
        movingTimeSec = movingTimeSec(actuals),
        longest = actuals.filter { it.distanceKm != null }
            .maxWithOrNull(compareBy<Actual> { it.distanceKm }.thenBy { it.date }),
        // Efforts under 1 km are too noisy to count as a pace record.
        fastest = actuals.filter { it.paceSecPerKm != null && (it.distanceKm ?: 0.0) >= 1.0 }
            .minWithOrNull(compareBy<Actual> { it.paceSecPerKm }.thenByDescending { it.date }),
    )
}

data class SeriesPoint(val date: LocalDate, val actualKm: Double, val count: Int, val plannedKm: Double)

/** Actual and planned (non-skipped) km per day for [days] days from [from]. */
fun dailySeries(actuals: List<Actual>, planned: List<Planned>, from: LocalDate, days: Int): List<SeriesPoint> {
    val byDay = actuals.groupBy { it.date }
    val planByDay = planned.groupBy { it.date }
    return (0 until days).map { i ->
        val d = from.plusDays(i.toLong())
        val a = byDay[d].orEmpty()
        SeriesPoint(d, a.totalKm(), a.size, planByDay[d].orEmpty().plannedKm())
    }
}

/** Actual and planned km per month for the calendar year of [anyDay]. */
fun monthlySeries(actuals: List<Actual>, planned: List<Planned>, anyDay: LocalDate): List<SeriesPoint> {
    val start = anyDay.yearStart()
    return (0 until 12).map { i ->
        val m = start.plusMonths(i.toLong())
        val r = DateRange(m, m.plusMonths(1))
        val a = actuals.inRange(r)
        SeriesPoint(m, a.totalKm(), a.size, planned.plannedInRange(r).plannedKm())
    }
}

enum class SeriesRange(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

fun mileageSeries(range: SeriesRange, actuals: List<Actual>, planned: List<Planned>, today: LocalDate) = when (range) {
    SeriesRange.WEEK -> dailySeries(actuals, planned, today.weekStart(), 7)
    SeriesRange.MONTH -> today.monthStart().let { dailySeries(actuals, planned, it, it.lengthOfMonth()) }
    SeriesRange.YEAR -> monthlySeries(actuals, planned, today)
}

/** Plan comparison for a period. Weeks use the whole week's plan; longer periods only the plan up to today. */
fun periodPlanSummary(period: Period, planned: List<Planned>, actuals: List<Actual>, today: LocalDate): PlanSummary {
    val r = period.range(today)
    val planRange = if (period == Period.WEEK) r else {
        val end = today.plusDays(1)
        DateRange(r.from, if (r.toExclusive == null || end.isBefore(r.toExclusive)) end else r.toExclusive)
    }
    return planSummary(planned.plannedInRange(planRange), actuals.inRange(r).totalKm(), today)
}

data class BlockWeek(val number: Int, val weekStart: LocalDate, val plannedKm: Double, val actualKm: Double, val done: Int, val due: Int)

/** Block statistics: actual figures include every activity dated inside the block, planned or not. */
data class BlockStats(
    val summary: PlanSummary,
    val longest: Actual?,
    val avgPlannedKm: Double?,
    val avgActualKm: Double?,
    val avgPaceSecPerKm: Double?,
    val weeks: List<BlockWeek>,
)

fun blockStats(block: Block, planned: List<Planned>, actuals: List<Actual>, today: LocalDate): BlockStats {
    val mine = planned.filter { it.blockId == block.id }
    val range = DateRange(block.startDate, block.endDate.plusDays(1))
    val inBlock = actuals.inRange(range)
    val agg = aggregates(inBlock)
    val weeks = mutableListOf<BlockWeek>()
    var ws = block.startDate.weekStart()
    var n = 1
    while (!ws.isAfter(block.endDate)) {
        val wr = DateRange(ws, ws.plusDays(7))
        val wp = mine.plannedInRange(wr)
        val ps = planSummary(wp, 0.0, today)
        weeks += BlockWeek(n++, ws, wp.plannedKm(), inBlock.inRange(wr).totalKm(), ps.done, ps.due)
        ws = ws.plusDays(7)
    }
    return BlockStats(
        summary = planSummary(mine, inBlock.totalKm(), today),
        longest = agg.longest,
        avgPlannedKm = mine.filter { !it.isRest && (it.plannedDistanceKm ?: 0.0) > 0 }
            .mapNotNull { it.plannedDistanceKm }.takeIf { it.isNotEmpty() }?.average(),
        avgActualKm = agg.avgDistanceKm,
        avgPaceSecPerKm = agg.avgPaceSecPerKm,
        weeks = weeks,
    )
}
