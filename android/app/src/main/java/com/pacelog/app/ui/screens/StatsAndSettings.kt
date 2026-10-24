package com.pacelog.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacelog.app.ui.AppViewModel
import com.pacelog.app.ui.Outcome
import com.pacelog.app.ui.Snapshot
import com.pacelog.app.ui.components.AppCard
import com.pacelog.app.ui.components.BigNumber
import com.pacelog.app.ui.components.ChipChoice
import com.pacelog.app.ui.components.ConfirmDialog
import com.pacelog.app.ui.components.Eyebrow
import com.pacelog.app.ui.components.PlanComparison
import com.pacelog.app.ui.components.StatPair
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.Actual
import com.pacelog.core.Option
import com.pacelog.core.Period
import com.pacelog.core.aggregates
import com.pacelog.core.formatDistance
import com.pacelog.core.formatDuration
import com.pacelog.core.formatKm
import com.pacelog.core.formatPace
import com.pacelog.core.formatPct
import com.pacelog.core.inRange
import com.pacelog.core.periodPlanSummary
import com.pacelog.core.periodTotals
import com.pacelog.core.short
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** Mileage, averages, records and plan comparison — all from actual activities. */
@Composable
fun StatsScreen(s: Snapshot, onOpenRun: (Long) -> Unit) {
    val today = LocalDate.now()
    var period by rememberSaveable { mutableStateOf(Period.ALL) }
    val totals = periodTotals(s.actuals, today)
    val inPeriod = s.actuals.inRange(period.range(today))
    val agg = aggregates(inPeriod)
    val plan = periodPlanSummary(period, s.planned, s.actuals, today)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Statistics", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("Mileage, pace and consistency, calculated from what you actually ran.", color = Pal.Muted)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TotalCard("Weekly", totals.week.km, totals.week.count, Modifier.weight(1f))
                    TotalCard("Monthly", totals.month.km, totals.month.count, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TotalCard("Yearly", totals.year.km, totals.year.count, Modifier.weight(1f))
                    TotalCard("All time", totals.allTime.km, totals.allTime.count, Modifier.weight(1f), dark = true)
                }
            }
        }
        item { MileageCard(s, today) }
        item {
            Text("Averages & records", fontSize = 22.sp, fontWeight = FontWeight.Black)
            ChipChoice(Period.entries.map { Option(it.name, it.label) }, period.name, { period = Period.valueOf(it) })
        }
        item {
            AppCard {
                Row {
                    StatPair("Distance", formatDistance(agg.km), Modifier.weight(1f))
                    StatPair("Activities", agg.count.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    StatPair("Average distance", formatDistance(agg.avgDistanceKm), Modifier.weight(1f))
                    StatPair("Average pace", formatPace(agg.avgPaceSecPerKm), Modifier.weight(1f))
                }
                Text("Average pace = total time ÷ total distance", color = Pal.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row {
                    StatPair("Average HR", agg.avgHeartRate?.let { "${it.roundToInt()} bpm" } ?: "—", Modifier.weight(1f))
                    StatPair("Moving time", formatDuration(agg.movingTimeSec), Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Record("Longest activity", agg.longest, { formatDistance(it.distanceKm, 2) }, onOpenRun)
                Spacer(Modifier.height(8.dp))
                Record("Fastest average pace", agg.fastest, { formatPace(it.paceSecPerKm) }, onOpenRun, note = "Activities of 1 km or more")
            }
        }
        item {
            AppCard {
                Eyebrow("Planned vs actual · ${period.label.lowercase()}")
                Spacer(Modifier.height(8.dp))
                if (plan.totalActivities == 0) {
                    Text("No planned training in this period.", color = Pal.Muted)
                } else {
                    Text("${plan.completed} completed · ${plan.modified} modified · ${plan.skipped} skipped", color = Pal.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    PlanComparison(plan)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Training completion ${formatPct(plan.completionPct)} — completed or modified sessions ÷ non-rest sessions that are due " +
                        "(dated today or earlier, or already marked). Rest days and future sessions never count against you. " +
                        "Planned mileage excludes skipped sessions; for month, year and all time it counts the plan up to today.",
                    color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun TotalCard(label: String, km: Double, count: Int, modifier: Modifier, dark: Boolean = false) {
    AppCard(modifier, dark = dark) {
        Eyebrow("$label mileage", color = if (dark) Color.White.copy(alpha = 0.6f) else Pal.Muted)
        Spacer(Modifier.height(4.dp))
        BigNumber(formatKm(km), "km", color = if (dark) Color.White else Pal.Ink, size = 30)
        Text("$count ${if (count == 1) "activity" else "activities"}", color = if (dark) Color.White.copy(alpha = 0.7f) else Pal.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Record(title: String, a: Actual?, value: (Actual) -> String, onOpen: (Long) -> Unit, note: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Pal.Paper)
            .let { m -> if (a != null) m.clickable { onOpen(a.id) } else m }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Pal.Ember), contentAlignment = Alignment.Center) {
            Text("🏆", fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Eyebrow(title)
            if (a == null) {
                Text("No data yet", color = Pal.Muted)
            } else {
                Text(value(a), fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("${a.title} · ${a.date.short()}", color = Pal.Muted, fontSize = 12.sp)
                note?.let { Text(it, color = Pal.Muted, fontSize = 11.sp) }
            }
        }
    }
}

/** Backup and restore: the data lives only on this phone. */
@Composable
fun SettingsScreen(vm: AppViewModel, s: Snapshot) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val toast = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            when (val r = vm.exportBackup(uri)) {
                is Outcome.Ok -> toast("Backup saved.")
                is Outcome.Err -> toast(r.message)
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Black)
        AppCard {
            Eyebrow("Your data")
            Text(
                "Pacelog keeps everything on this phone — no account, no server. " +
                    "${s.blocks.size} training blocks, ${s.planned.size} planned sessions and ${s.actuals.size} activities are stored here.",
                color = Pal.InkSoft,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Uninstalling the app deletes this data. Save a backup regularly (for example to Google Drive) so you can restore it on a new phone.",
                color = Pal.Muted, fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { exporter.launch("pacelog-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Save backup", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Restore from backup")
            }
        }
        AppCard {
            Eyebrow("About")
            Text("Pacelog — plan your training, run your plan, track what actually happened.", color = Pal.InkSoft)
            Text("Weeks run Monday → Sunday. Mileage always comes from logged activities.", color = Pal.Muted, fontSize = 13.sp)
        }
    }

    confirmRestore?.let { uri ->
        ConfirmDialog(
            "Restore this backup?",
            "Everything currently in Pacelog on this phone is replaced by the backup's contents.",
            "Restore",
            onConfirm = {
                confirmRestore = null
                scope.launch {
                    when (val r = vm.restoreBackup(uri)) {
                        is Outcome.Ok -> toast("Restored ${r.value} activities.")
                        is Outcome.Err -> toast(r.message)
                    }
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
}
