package com.pacelog.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pace is stored as whole seconds per km: 330 → "5:30/km". */
fun formatPace(seconds: Number?, withUnit: Boolean = true): String {
    val s = seconds?.toDouble() ?: return "—"
    if (s.isNaN() || s <= 0) return "—"
    val total = s.roundToInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}" + if (withUnit) "/km" else ""
}

sealed interface Parsed<out T> {
    data object Empty : Parsed<Nothing>
    data object Invalid : Parsed<Nothing>
    data class Value<T>(val value: T) : Parsed<T>
}

/** "5" + "30" → 330 seconds per km. */
fun parsePace(minutes: String, seconds: String): Parsed<Int> {
    val m = minutes.trim()
    val s = seconds.trim()
    if (m.isEmpty() && s.isEmpty()) return Parsed.Empty
    val mi = (m.ifEmpty { "0" }).toIntOrNull() ?: return Parsed.Invalid
    val se = (s.ifEmpty { "0" }).toIntOrNull() ?: return Parsed.Invalid
    if (mi < 0 || se < 0 || se > 59 || m.length > 2 || s.length > 2) return Parsed.Invalid
    return Parsed.Value(mi * 60 + se)
}

fun splitPace(seconds: Int?): Pair<String, String> =
    if (seconds == null) "" to "" else (seconds / 60).toString() to (seconds % 60).toString().padStart(2, '0')

/** Parses a decimal typed by a runner; accepts "8,4" as 8.4. */
fun parseDecimal(v: String): Parsed<Double> {
    val t = v.trim().replace(',', '.')
    if (t.isEmpty()) return Parsed.Empty
    val d = t.toDoubleOrNull() ?: return Parsed.Invalid
    return if (d.isFinite()) Parsed.Value(d) else Parsed.Invalid
}

fun parseWhole(v: String): Parsed<Int> {
    val t = v.trim()
    if (t.isEmpty()) return Parsed.Empty
    return t.toIntOrNull()?.let { Parsed.Value(it) } ?: Parsed.Invalid
}

fun formatKm(km: Double?, digits: Int = 1): String {
    if (km == null || km.isNaN()) return "—"
    val factor = Math.pow(10.0, digits.toDouble())
    val rounded = Math.round(km * factor) / factor
    val whole = floor(rounded)
    val text = if (rounded == whole) "%,d".format(Locale.US, whole.toLong())
    else "%,.${digits}f".format(Locale.US, rounded).trimEnd('0').trimEnd('.')
    return text
}

fun formatDistance(km: Double?, digits: Int = 1) = if (km == null) "—" else "${formatKm(km, digits)} km"

/** Neutral difference: "+0.4 km", "−3.6 km", "±0 km". */
fun formatSignedKm(km: Double): String {
    val r = Math.round(km * 10) / 10.0
    if (r == 0.0) return "±0 km"
    return (if (r > 0) "+" else "−") + formatKm(abs(r)) + " km"
}

fun formatDuration(seconds: Number?): String {
    val s = seconds?.toDouble() ?: return "—"
    if (s <= 0) return "—"
    val h = (s / 3600).toInt()
    val m = ((s % 3600) / 60).roundToInt()
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

/** 2840 → "47:20", 5400 → "1:30:00". */
fun formatClock(seconds: Double): String {
    val t = seconds.roundToInt()
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = (t % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
}

fun formatPct(p: Double?) = if (p == null) "—" else "${p.roundToInt()}%"

private val SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val LONG = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
private val FULL = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

fun LocalDate.short(): String = format(SHORT)
fun LocalDate.long(): String = format(LONG)
fun LocalDate.full(): String = format(FULL)
fun LocalDate.monthYear(): String = format(MONTH_YEAR)
fun LocalDate.weekdayShort(): String = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
fun LocalDate.monthShort(): String = month.getDisplayName(TextStyle.SHORT, Locale.US)

/** "Oct 5 → Jan 17", with years when the range isn't in the current year. */
fun dateRange(from: LocalDate, to: LocalDate, today: LocalDate = LocalDate.now()): String {
    val withYear = from.year != to.year || from.year != today.year
    val f = if (withYear) DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US) else SHORT
    return "${from.format(f)} → ${to.format(f)}"
}
