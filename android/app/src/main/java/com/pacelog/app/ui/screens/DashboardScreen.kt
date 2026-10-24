package com.pacelog.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacelog.app.ui.AppViewModel
import com.pacelog.app.ui.Snapshot
import com.pacelog.app.ui.components.ActivityRow
import com.pacelog.app.ui.components.AppCard
import com.pacelog.app.ui.components.BarChart
import com.pacelog.app.ui.components.BigNumber
import com.pacelog.app.ui.components.ChartRow
import com.pacelog.app.ui.components.EmptyState
import com.pacelog.app.ui.components.Eyebrow
import com.pacelog.app.ui.components.PlanComparison
import com.pacelog.app.ui.components.PlannedRow
import com.pacelog.app.ui.theme.Numeric
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.Period
import com.pacelog.core.SeriesPoint
import com.pacelog.core.SeriesRange
import com.pacelog.core.Status
import com.pacelog.core.formatDistance
import com.pacelog.core.formatKm
import com.pacelog.core.long
import com.pacelog.core.mileageSeries
import com.pacelog.core.monthShort
import com.pacelog.core.monthYear
import com.pacelog.core.periodPlanSummary
import com.pacelog.core.periodTotals
import com.pacelog.core.short
import com.pacelog.core.weekdayShort
import java.time.LocalDate
import java.time.LocalTime

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 5 -> "Late miles"
        h < 12 -> "Good morning"
        h < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    s: Snapshot,
    onLog: (LocalDate?) -> Unit,
    onOpenRun: (Long) -> Unit,
    onTraining: () -> Unit,
) {
    val today = LocalDate.now()
    var sheet by remember { mutableStateOf<Pair<Long, PlannedMode>?>(null) }
    val todayPlanned = s.planned.filter { it.date == today }
    val todayActual = s.actuals.filter { it.date == today }.sortedBy { it.createdAt }
    val totals = periodTotals(s.actuals, today)
    val week = periodPlanSummary(Period.WEEK, s.planned, s.actuals, today)

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Eyebrow(today.long())
                Text(greeting(), fontSize = 32.sp, fontWeight = FontWeight.Black)
            }
        }

        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Today's plan", Modifier.weight(1f))
                    TextButton(onClick = onTraining) { Text("Training") }
                }
                if (todayPlanned.isEmpty()) {
                    Text("Nothing planned today.", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Enjoy your rest day or log an activity if you run.", color = Pal.Muted)
                } else {
                    todayPlanned.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = Pal.Line)
                        PlannedRow(p, onClick = { sheet = p.id to PlannedMode.VIEW }) {
                            if (p.status == Status.PLANNED && !p.isRest) {
                                Button(onClick = { sheet = p.id to PlannedMode.COMPLETE }) { Text("✓ Complete") }
                            }
                        }
                    }
                }
            }
        }

        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Today's activities", Modifier.weight(1f))
                    TextButton(onClick = { onLog(today) }) { Text("+ Log") }
                }
                if (todayActual.isEmpty()) {
                    Text("No activities yet today.", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Back from a run? Log it in under a minute.", color = Pal.Muted)
                } else {
                    todayActual.forEachIndexed { i, a ->
                        if (i > 0) HorizontalDivider(color = Pal.Line)
                        ActivityRow(a) { onOpenRun(a.id) }
                    }
                    HorizontalDivider(color = Pal.Line)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Eyebrow("Total", Modifier.weight(1f))
                        Text(formatDistance(todayActual.sumOf { it.distanceKm ?: 0.0 }), style = Numeric, fontSize = 24.sp)
                    }
                }
            }
        }

        if (s.actuals.isEmpty()) {
            item {
                EmptyState("No runs yet.", "Start building your running history.") {
                    Button(onClick = { onLog(null) }) { Text("Log Your First Activity") }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val cards = listOf("This week" to totals.week, "This month" to totals.month, "This year" to totals.year)
                items(cards) { (label, t) ->
                    AppCard(Modifier.width(170.dp), dark = label == "This week") {
                        val dark = label == "This week"
                        Eyebrow(label, color = if (dark) Color.White.copy(alpha = 0.6f) else Pal.Muted)
                        Spacer(Modifier.height(6.dp))
                        BigNumber(formatKm(t.km), "km", color = if (dark) Color.White else Pal.Ink, size = 34)
                        Text("${t.count} ${if (t.count == 1) "activity" else "activities"}",
                            color = if (dark) Color.White.copy(alpha = 0.7f) else Pal.Muted, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            AppCard {
                Eyebrow("Planned vs actual · this week")
                Spacer(Modifier.height(10.dp))
                if (week.totalActivities == 0) {
                    Text("No training planned this week.", color = Pal.Muted)
                    TextButton(onClick = onTraining) { Text("+ Add Training") }
                } else {
                    PlanComparison(week)
                }
            }
        }

        item { MileageCard(s, today) }
    }

    sheet?.let { (id, mode) ->
        PlannedSheet(vm, s, id, mode, onOpenActivity = { sheet = null; onOpenRun(it) }, onDismiss = { sheet = null })
    }
}

/** Actual mileage with a Week / Month / Year switch. */
@Composable
fun MileageCard(s: Snapshot, today: LocalDate) {
    var range by rememberSaveable { mutableStateOf(SeriesRange.WEEK) }
    val points = mileageSeries(range, s.actuals, s.planned, today)
    val rows = points.map { p: SeriesPoint ->
        when (range) {
            SeriesRange.WEEK -> ChartRow(p.date.weekdayShort(), "${p.date.weekdayShort()}, ${p.date.short()}", p.actualKm, p.plannedKm)
            SeriesRange.MONTH -> ChartRow(p.date.dayOfMonth.toString(), p.date.short(), p.actualKm, p.plannedKm)
            SeriesRange.YEAR -> ChartRow(p.date.monthShort(), p.date.monthYear(), p.actualKm, p.plannedKm)
        }
    }
    val caption = when (range) {
        SeriesRange.WEEK -> "Daily mileage this week"
        SeriesRange.MONTH -> "Daily mileage this month"
        SeriesRange.YEAR -> "Monthly mileage this year"
    }
    AppCard {
        Eyebrow("Mileage")
        Text(formatDistance(points.sumOf { it.actualKm }), style = Numeric, fontSize = 28.sp)
        Text(caption, color = Pal.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SeriesRange.entries.forEachIndexed { i, r ->
                SegmentedButton(
                    selected = range == r,
                    onClick = { range = r },
                    shape = SegmentedButtonDefaults.itemShape(i, SeriesRange.entries.size),
                ) { Text(r.label) }
            }
        }
        Spacer(Modifier.height(12.dp))
        BarChart(rows)
    }
}
