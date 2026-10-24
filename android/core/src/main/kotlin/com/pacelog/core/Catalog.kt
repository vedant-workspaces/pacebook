package com.pacelog.core

data class Option(val value: String, val label: String, val isRun: Boolean = false)

/** Single source of truth for activity types; add new ones here. */
object ActivityTypes {
    const val REST = "rest"

    val all = listOf(
        Option("easy_run", "Easy Run", true),
        Option("long_run", "Long Run", true),
        Option("recovery", "Recovery Run", true),
        Option("tempo", "Tempo Run", true),
        Option("intervals", "Intervals", true),
        Option("fartlek", "Fartlek", true),
        Option("hill_repeats", "Hill Repeats", true),
        Option("race", "Race", true),
        Option("trail_run", "Trail Run", true),
        Option("treadmill", "Treadmill", true),
        Option("walk", "Walk"),
        Option("strength", "Strength"),
        Option("cross_training", "Cross Training"),
        Option(REST, "Rest"),
        Option("other", "Other"),
    )

    fun isValid(v: String) = all.any { it.value == v }
    fun label(v: String) = all.firstOrNull { it.value == v }?.label
        ?: v.replace('_', ' ').replaceFirstChar { it.uppercase() }
    fun isRun(v: String) = all.firstOrNull { it.value == v }?.isRun ?: false
}

object Reasons {
    val all = listOf(
        Option("injury", "Injury"),
        Option("illness", "Illness"),
        Option("fatigue", "Fatigue"),
        Option("work", "Work"),
        Option("travel", "Travel"),
        Option("weather", "Weather"),
        Option("lack_of_time", "Lack of Time"),
        Option("recovery", "Recovery"),
        Option("personal", "Personal"),
        Option("other", "Other"),
    )

    fun isValid(v: String) = all.any { it.value == v }
    fun label(v: String?) = all.firstOrNull { it.value == v }?.label ?: v.orEmpty()
}
