package com.pacelog.core

import java.time.LocalDate
import kotlin.math.roundToLong

/** A user-facing validation problem on one form field. */
class ValidationException(val field: String, message: String) : Exception(message)

object Limits {
    const val NAME = 120
    const val GOAL = 200
    const val DESCRIPTION = 2000
    const val COMMENT = 2000
    const val MAX_DISTANCE_KM = 500.0 // exclusive
    const val MIN_PACE = 120 // 2:00/km
    const val MAX_PACE = 1800 // 30:00/km
    const val MIN_HR = 30
    const val MAX_HR = 250
    const val MAX_DURATION_SEC = 48 * 3600
    const val MAX_BLOCK_DAYS = 731L
}

private fun fail(field: String, msg: String): Nothing = throw ValidationException(field, msg)

/** Trims text; blank becomes null so optional fields store nothing. */
fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun checkLength(field: String, v: String?, max: Int, label: String) {
    if (v != null && v.length > max) fail(field, "$label must be at most $max characters")
}

/** Distances are kept to 3 decimals, like the original database column. */
fun roundDistance(d: Double?) = d?.let { (it * 1000).roundToLong() / 1000.0 }

data class BlockInput(
    val name: String,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val goal: String?,
)

fun validateBlock(name: String, description: String?, start: LocalDate?, end: LocalDate?, goal: String?): BlockInput {
    val n = name.clean() ?: fail("name", "Give your training block a name")
    checkLength("name", n, Limits.NAME, "Name")
    start ?: fail("startDate", "Choose a start date")
    end ?: fail("endDate", "Choose an end date")
    if (end.isBefore(start)) fail("endDate", "End date must be on or after the start date")
    if (daysBetween(start, end) > Limits.MAX_BLOCK_DAYS) fail("endDate", "A training block can be at most two years long")
    val d = description.clean().also { checkLength("description", it, Limits.DESCRIPTION, "Description") }
    val g = goal.clean().also { checkLength("goal", it, Limits.GOAL, "Goal") }
    return BlockInput(n, d, start, end, g)
}

data class PlannedInput(
    val date: LocalDate,
    val type: String,
    val title: String,
    val description: String?,
    val distanceKm: Double?,
    val paceSecPerKm: Int?,
    val durationSec: Int?,
)

fun validatePlanned(
    block: Block,
    date: LocalDate?,
    type: String,
    title: String?,
    description: String?,
    distanceKm: Double?,
    paceSecPerKm: Int?,
    durationSec: Int?,
): PlannedInput {
    date ?: fail("date", "Choose a date")
    if (date.isBefore(block.startDate) || date.isAfter(block.endDate)) {
        fail("date", "Date must be within the training block (${block.startDate.short()} – ${block.endDate.short()})")
    }
    if (!ActivityTypes.isValid(type)) fail("type", "Unknown activity type")
    val t = title.clean() ?: ActivityTypes.label(type)
    checkLength("title", t, Limits.NAME, "Title")
    val d = description.clean().also { checkLength("description", it, Limits.DESCRIPTION, "Description") }
    val rest = type == ActivityTypes.REST
    val km = if (rest) null else roundDistance(distanceKm)
    if (km != null && (km.isNaN() || km < 0 || km >= Limits.MAX_DISTANCE_KM)) {
        fail("distance", "Enter a planned distance between 0 and 500 km")
    }
    val pace = if (rest) null else paceSecPerKm
    if (pace != null && pace !in Limits.MIN_PACE..Limits.MAX_PACE) fail("pace", "Pace must be between 2:00 and 30:00 per km")
    val dur = if (rest) null else durationSec
    if (dur != null && (dur <= 0 || dur > Limits.MAX_DURATION_SEC)) fail("duration", "Enter a duration up to 48 hours")
    return PlannedInput(date, type, t, d, km, pace, dur)
}

data class ActualInput(
    val date: LocalDate,
    val type: String,
    val title: String,
    val distanceKm: Double?,
    val paceSecPerKm: Int?,
    val avgHeartRate: Int?,
    val comment: String?,
)

fun validateActual(
    date: LocalDate?,
    type: String,
    title: String?,
    distanceKm: Double?,
    paceSecPerKm: Int?,
    avgHeartRate: Int?,
    comment: String?,
): ActualInput {
    date ?: fail("date", "Choose a date")
    if (!ActivityTypes.isValid(type)) fail("type", "Unknown activity type")
    val km = roundDistance(distanceKm)
    if (km != null) {
        if (km.isNaN() || km <= 0) fail("distance", "Distance must be greater than zero")
        if (km >= Limits.MAX_DISTANCE_KM) fail("distance", "Distance must be less than 500 km")
    }
    if (paceSecPerKm != null && paceSecPerKm !in Limits.MIN_PACE..Limits.MAX_PACE) {
        fail("pace", "Pace must be between 2:00 and 30:00 per km")
    }
    if (avgHeartRate != null && avgHeartRate !in Limits.MIN_HR..Limits.MAX_HR) {
        fail("hr", "Heart rate must be between 30 and 250 bpm")
    }
    val c = comment.clean().also { checkLength("comment", it, Limits.COMMENT, "Comment") }
    val t = title.clean() ?: ActivityTypes.label(type)
    checkLength("title", t, Limits.NAME, "Title")
    return ActualInput(date, type, t, km, paceSecPerKm, avgHeartRate, c)
}

fun validateSkip(reason: String?, notes: String?): Pair<String, String?> {
    if (reason == null || !Reasons.isValid(reason)) fail("reason", "Please choose a reason")
    val n = notes.clean().also { checkLength("notes", it, Limits.COMMENT, "Notes") }
    return reason to n
}
