package com.pacelog.core

import java.time.LocalDate

/** Statuses of a planned activity. */
object Status {
    const val PLANNED = "planned"
    const val COMPLETED = "completed"
    const val SKIPPED = "skipped"
    const val MODIFIED = "modified"
}

/** A period of structured training, e.g. "TMM 2027 Marathon Training". */
data class Block(
    val id: Long,
    val name: String,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val goal: String?,
)

/** What the runner planned to do on a date. */
data class Planned(
    val id: Long,
    val blockId: Long,
    val date: LocalDate,
    val type: String,
    val title: String,
    val description: String?,
    val plannedDistanceKm: Double?,
    val plannedPaceSecPerKm: Int?,
    val plannedDurationSec: Int?,
    val status: String,
    val reason: String?,
    val notes: String?,
) {
    val isRest get() = type == ActivityTypes.REST
}

/**
 * What the runner actually did. [plannedId] links it to the planned activity
 * it fulfilled, or is null for an unplanned activity. Many actual activities
 * can share a date.
 */
data class Actual(
    val id: Long,
    val plannedId: Long?,
    val date: LocalDate,
    val type: String,
    val title: String,
    val distanceKm: Double?,
    val paceSecPerKm: Int?,
    val avgHeartRate: Int?,
    val comment: String?,
    val createdAt: Long,
)
