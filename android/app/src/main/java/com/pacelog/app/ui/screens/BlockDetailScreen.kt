package com.pacelog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacelog.app.ui.AppViewModel
import com.pacelog.app.ui.Outcome
import com.pacelog.app.ui.Snapshot
import com.pacelog.app.ui.components.ActivityRow
import com.pacelog.app.ui.components.AppCard
import com.pacelog.app.ui.components.BarChart
import com.pacelog.app.ui.components.ChartRow
import com.pacelog.app.ui.components.ConfirmDialog
import com.pacelog.app.ui.components.Eyebrow
import com.pacelog.app.ui.components.PlannedRow
import com.pacelog.app.ui.components.StatPair
import com.pacelog.app.ui.components.statusStyle
import com.pacelog.app.ui.theme.Numeric
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.ActivityTypes
import com.pacelog.core.Block
import com.pacelog.core.Planned
import com.pacelog.core.blockStats
import com.pacelog.core.dateRange
import com.pacelog.core.daysBetween
import com.pacelog.core.formatDistance
import com.pacelog.core.formatDuration
import com.pacelog.core.formatKm
import com.pacelog.core.formatPace
import com.pacelog.core.formatPct
import com.pacelog.core.formatSignedKm
import com.pacelog.core.long
import com.pacelog.core.monthStart
import com.pacelog.core.monthYear
import com.pacelog.core.short
import com.pacelog.core.weekStart
import com.pacelog.core.weekdayShort
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun BlockDetailScreen(
    vm: AppViewModel,
    s: Snapshot,
    blockId: Long,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onOpenRun: (Long) -> Unit,
    onLog: (LocalDate) -> Unit,
) {
    val block = s.block(blockId)
    LaunchedEffect(block == null) { if (block == null) onDeleted() }
    if (block == null) return
    val today = LocalDate.now()
    val initial = when {
        today.isBefore(block.startDate) -> block.startDate
        today.isAfter(block.endDate) -> block.endDate
        else -> today
    }
    var anchorDay by rememberSaveable(blockId) { mutableStateOf(initial.toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorDay)
    var monthView by rememberSaveable { mutableStateOf(false) }
    var openPlanned by remember { mutableStateOf<Long?>(null) }
    var openDay by remember { mutableStateOf<LocalDate?>(null) }
    var addDate by remember { mutableStateOf<LocalDate?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val planned = s.planned.filter { it.blockId == block.id }
    val stats = blockStats(block, s.planned, s.actuals, today)
    val from = if (monthView) anchor.monthStart().weekStart() else anchor.weekStart()
    val days = if (monthView) {
        val last = anchor.monthStart().plusMonths(1).minusDays(1)
        (((daysBetween(from, last) + 1 + 6) / 7) * 7).toInt()
    } else 7
    val dates = (0 until days).map { from.plusDays(it.toLong()) }
    val plannedByDay = planned.groupBy { it.date }
    val kmByDay = s.actuals.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.distanceKm ?: 0.0 } }
    val totalWeeks = stats.weeks.size
    val weekNo = (daysBetween(block.startDate.weekStart(), from) / 7 + 1).toInt()
    val label = if (monthView) anchor.monthYear()
    else "${from.short()} – ${from.plusDays(6).short()}" + if (weekNo in 1..totalWeeks) " · Week $weekNo of $totalWeeks" else ""

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BlockHeader(block, stats.summary.completionPct, stats.summary, onEdit) { confirmDelete = true } }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { anchorDay = (if (monthView) anchor.minusMonths(1) else anchor.minusWeeks(1)).toEpochDay() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = if (monthView) "Previous month" else "Previous week")
                    }
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 2)
                    IconButton(onClick = { anchorDay = (if (monthView) anchor.plusMonths(1) else anchor.plusWeeks(1)).toEpochDay() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = if (monthView) "Next month" else "Next week")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        SegmentedButton(selected = !monthView, onClick = { monthView = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Week") }
                        SegmentedButton(selected = monthView, onClick = { monthView = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Month") }
                    }
                    TextButton(onClick = { anchorDay = initial.toEpochDay() }) { Text("Today") }
                }
                Text("✓ Completed   ~ Modified   ✕ Skipped   ○ Planned", color = Pal.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (monthView) {
            item {
                MonthGrid(dates, anchor, block, plannedByDay, kmByDay, onDay = { openDay = it })
            }
        } else {
            dates.forEach { d ->
                item(key = "day-$d") {
                    DayCard(d, block, plannedByDay[d].orEmpty(), kmByDay[d], d == today,
                        onDay = { openDay = d }, onPlanned = { openPlanned = it }, onAdd = { addDate = d })
                }
            }
        }

        item { BlockStatsCard(stats, today, onOpenRun) }
    }

    openDay?.let { d ->
        DaySheet(s, d, onDismiss = { openDay = null }, onPlanned = { openPlanned = it }, onOpenRun = { openDay = null; onOpenRun(it) },
            onAdd = if (!d.isBefore(block.startDate) && !d.isAfter(block.endDate)) ({ openDay = null; addDate = d }) else null,
            onLog = { openDay = null; onLog(d) })
    }
    openPlanned?.let { id ->
        PlannedSheet(vm, s, id, onOpenActivity = { openPlanned = null; openDay = null; onOpenRun(it) }, onDismiss = { openPlanned = null })
    }
    addDate?.let { d ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { addDate = null }, sheetState = sheet, containerColor = Pal.Card) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add Activity", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(d.long(), color = Pal.Muted)
                PlannedForm(block, d, onCancel = { addDate = null }) { input ->
                    val r = vm.run { it.addPlanned(block.id, input) }
                    if (r is Outcome.Ok) addDate = null
                    r
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete this training block?",
            "All planned activities in this block are deleted. Activities you've logged are kept in your run history as unplanned activities.",
            "Delete Block",
            onConfirm = {
                confirmDelete = false
                // Navigation happens via the LaunchedEffect once the block is gone.
                scope.launch { vm.run { it.deleteBlock(block.id) } }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun BlockHeader(block: Block, completion: Double?, sum: com.pacelog.core.PlanSummary, onEdit: () -> Unit, onDelete: () -> Unit) {
    AppCard(dark = true, padding = 20.dp) {
        Eyebrow("Training block · ${dateRange(block.startDate, block.endDate)}", color = Color.White.copy(alpha = 0.6f))
        Text(block.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
        block.goal?.let {
            Spacer(Modifier.height(8.dp))
            Text("🏆 Goal: $it", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.Ember).padding(horizontal = 12.dp, vertical = 4.dp))
        }
        block.description?.let { Text(it, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Row {
            StatPair("Completion", formatPct(completion), Modifier.weight(1f), light = true)
            StatPair("Planned", "${formatKm(sum.plannedKm, 0)} km", Modifier.weight(1f), light = true)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            StatPair("Actual", "${formatKm(sum.actualKm, 0)} km", Modifier.weight(1f), light = true)
            StatPair("Sessions", "${sum.done} / ${sum.due}", Modifier.weight(1f), light = true)
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) { Text("Edit", color = Color.White) }
            OutlinedButton(onClick = onDelete) { Text("Delete", color = Color.White) }
        }
    }
}

@Composable
private fun DayCard(
    d: LocalDate,
    block: Block,
    items: List<Planned>,
    km: Double?,
    isToday: Boolean,
    onDay: () -> Unit,
    onPlanned: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    val inBlock = !d.isBefore(block.startDate) && !d.isAfter(block.endDate)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (inBlock) Pal.Card else Pal.Card.copy(alpha = 0.5f))
            .border(if (isToday) 2.dp else 1.dp, if (isToday) Pal.Ember else Pal.Line, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onDay).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(d.weekdayShort().uppercase(), color = if (isToday) Pal.EmberDark else Pal.Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(d.dayOfMonth.toString(), style = Numeric, fontSize = 20.sp, color = if (isToday) Pal.EmberDark else Pal.Ink)
            if (isToday) Text("  TODAY", color = Pal.EmberDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            if (km != null && km > 0) Text("${formatKm(km)} km done", style = Numeric, fontSize = 14.sp, color = Pal.InkSoft)
        }
        items.forEach { p -> PlannedChip(p) { onPlanned(p.id) } }
        if (items.isEmpty() && inBlock) Text("Nothing planned", color = Pal.Muted, fontSize = 13.sp)
        if (inBlock) {
            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ Add") }
        }
    }
}

@Composable
private fun PlannedChip(p: Planned, onClick: () -> Unit) {
    val st = statusStyle(p.status)
    val metric = p.plannedDistanceKm?.takeIf { it > 0 }?.let { "${formatKm(it)} km" }
        ?: p.plannedDurationSec?.let { formatDuration(it) } ?: ""
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(st.bg)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${p.title}, ${metric.ifEmpty { ActivityTypes.label(p.type) }}, ${st.label}" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(st.symbol, color = st.fg, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(ActivityTypes.label(p.type), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                textDecoration = if (st.label == "Skipped") TextDecoration.LineThrough else null)
            if (p.title != ActivityTypes.label(p.type)) Text(p.title, fontSize = 12.sp, color = Pal.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (metric.isNotEmpty()) Text(metric, style = Numeric, fontSize = 16.sp)
    }
}

/** Compact month grid: day number, status symbols and actual km. */
@Composable
private fun MonthGrid(
    dates: List<LocalDate>,
    anchor: LocalDate,
    block: Block,
    plannedByDay: Map<LocalDate, List<Planned>>,
    kmByDay: Map<LocalDate, Double>,
    onDay: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, modifier = Modifier.weight(1f), color = Pal.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        dates.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    val inMonth = d.month == anchor.month
                    val inBlock = !d.isBefore(block.startDate) && !d.isAfter(block.endDate)
                    val items = plannedByDay[d].orEmpty()
                    val shape = RoundedCornerShape(10.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.72f)
                            .clip(shape)
                            .background(if (inMonth && inBlock) Pal.Card else Pal.Card.copy(alpha = 0.4f))
                            .border(if (d == today) 2.dp else 1.dp, if (d == today) Pal.Ember else Pal.Line, shape)
                            .clickable { onDay(d) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(d.dayOfMonth.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (inMonth) Pal.Ink else Pal.Muted)
                        Text(items.joinToString("") { statusStyle(it.status).symbol }, fontSize = 12.sp, fontWeight = FontWeight.Black,
                            color = items.firstOrNull()?.let { statusStyle(it.status).fg } ?: Pal.Muted, maxLines = 1)
                        val km = kmByDay[d]
                        Spacer(Modifier.weight(1f))
                        if (km != null && km > 0) Text(formatKm(km), style = Numeric, fontSize = 11.sp, color = Pal.EmberDark)
                    }
                }
            }
        }
    }
}

/** What was PLANNED next to what ACTUALLY happened on one day. */
@Composable
fun DaySheet(
    s: Snapshot,
    d: LocalDate,
    onDismiss: () -> Unit,
    onPlanned: (Long) -> Unit,
    onOpenRun: (Long) -> Unit,
    onAdd: (() -> Unit)?,
    onLog: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val planned = s.planned.filter { it.date == d }
    val actual = s.actuals.filter { it.date == d }.sortedBy { it.createdAt }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pal.Card) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(d.long(), fontSize = 24.sp, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Plan", Modifier.weight(1f))
                if (onAdd != null) TextButton(onClick = onAdd) { Text("+ Add Activity") }
            }
            HorizontalDivider(color = Pal.Line)
            if (planned.isEmpty()) Text("Nothing planned for this day.", color = Pal.Muted)
            planned.forEach { p -> PlannedRow(p, onClick = { onPlanned(p.id) }) }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Eyebrow("Actual", Modifier.weight(1f))
                TextButton(onClick = onLog) { Text("+ Log Activity") }
            }
            HorizontalDivider(color = Pal.Line)
            if (actual.isEmpty()) Text("No activities logged.", color = Pal.Muted)
            actual.forEach { a -> ActivityRow(a) { onOpenRun(a.id) } }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(Pal.Ink).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow("Total", Modifier.weight(1f), color = Color.White.copy(alpha = 0.6f))
                Text(formatDistance(actual.sumOf { it.distanceKm ?: 0.0 }), style = Numeric, fontSize = 26.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun BlockStatsCard(stats: com.pacelog.core.BlockStats, today: LocalDate, onOpenRun: (Long) -> Unit) {
    val s = stats.summary
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("Block statistics", Modifier.padding(top = 8.dp))
        AppCard {
            Eyebrow("Activities")
            Spacer(Modifier.height(8.dp))
            Row { StatPair("Planned sessions", s.sessionCount.toString(), Modifier.weight(1f)); StatPair("✓ Completed", s.completed.toString(), Modifier.weight(1f)) }
            Spacer(Modifier.height(8.dp))
            Row { StatPair("~ Modified", s.modified.toString(), Modifier.weight(1f)); StatPair("✕ Skipped", s.skipped.toString(), Modifier.weight(1f)) }
            Spacer(Modifier.height(8.dp))
            Row { StatPair("Completion", formatPct(s.completionPct), Modifier.weight(1f)); StatPair("Still planned", s.planned.toString(), Modifier.weight(1f)) }
            Spacer(Modifier.height(16.dp))
            Eyebrow("Distance")
            Spacer(Modifier.height(8.dp))
            Row { StatPair("Planned mileage", formatDistance(s.plannedKm, 0), Modifier.weight(1f)); StatPair("Actual mileage", formatDistance(s.actualKm, 0), Modifier.weight(1f)) }
            Spacer(Modifier.height(8.dp))
            Row { StatPair("Avg planned", formatDistance(stats.avgPlannedKm), Modifier.weight(1f)); StatPair("Avg actual", formatDistance(stats.avgActualKm), Modifier.weight(1f)) }
            Spacer(Modifier.height(8.dp))
            StatPair("Average pace", formatPace(stats.avgPaceSecPerKm))
            stats.longest?.let { a ->
                Spacer(Modifier.height(16.dp))
                Eyebrow("Longest run")
                ActivityRow(a) { onOpenRun(a.id) }
            }
        }
        AppCard {
            Eyebrow("Planned vs actual mileage by week")
            Spacer(Modifier.height(10.dp))
            BarChart(stats.weeks.map { ChartRow("W${it.number}", "Week ${it.number} · ${it.weekStart.short()}", it.actualKm, it.plannedKm) })
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Week" to 1.4f, "Planned" to 1f, "Actual" to 1f, "Diff" to 1f).forEach { (h, w) ->
                    Text(h, modifier = Modifier.weight(w), color = Pal.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            stats.weeks.forEach { w ->
                val future = w.weekStart.isAfter(today)
                HorizontalDivider(color = Pal.Line)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (w.weekStart == today.weekStart()) Pal.Ember50 else Color.Transparent)
                        .padding(vertical = 8.dp),
                ) {
                    Text("Week ${w.number}", modifier = Modifier.weight(1.4f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("${formatKm(w.plannedKm)} km", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text(if (future) "—" else "${formatKm(w.actualKm)} km", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text(if (future) "—" else formatSignedKm(w.actualKm - w.plannedKm), modifier = Modifier.weight(1f), fontSize = 13.sp, color = Pal.InkSoft)
                }
            }
        }
        Box(Modifier.height(24.dp))
    }
}
