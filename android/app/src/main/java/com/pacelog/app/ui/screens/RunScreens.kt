package com.pacelog.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacelog.app.ui.AppViewModel
import com.pacelog.app.ui.Outcome
import com.pacelog.app.ui.Snapshot
import com.pacelog.app.ui.components.ActivityForm
import com.pacelog.app.ui.components.ActivityFormMode
import com.pacelog.app.ui.components.AppCard
import com.pacelog.app.ui.components.ChipChoice
import com.pacelog.app.ui.components.ConfirmDialog
import com.pacelog.app.ui.components.EmptyState
import com.pacelog.app.ui.components.ErrorText
import com.pacelog.app.ui.components.Eyebrow
import com.pacelog.app.ui.components.StatusChip
import com.pacelog.app.ui.components.TypeBadge
import com.pacelog.app.ui.components.plannedTarget
import com.pacelog.app.ui.theme.Numeric
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.ActivityTypes
import com.pacelog.core.Option
import com.pacelog.core.formatClock
import com.pacelog.core.formatKm
import com.pacelog.core.formatPace
import com.pacelog.core.formatSignedKm
import com.pacelog.core.full
import com.pacelog.core.long
import com.pacelog.core.monthShort
import com.pacelog.core.validateActual
import com.pacelog.core.weekdayShort
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Complete history of actual activities, newest first, filterable by type. */
@Composable
fun RunsScreen(s: Snapshot, onOpen: (Long) -> Unit, onLog: () -> Unit) {
    var type by rememberSaveable { mutableStateOf("all") }
    val used = s.actuals.map { it.type }.toSet()
    val filters = listOf(Option("all", "All")) + ActivityTypes.all.filter { it.value in used }
    val list = if (type == "all") s.actuals else s.actuals.filter { it.type == type }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Runs", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("${list.size} ${if (list.size == 1) "activity" else "activities"} logged", color = Pal.Muted)
        }
        if (s.actuals.isEmpty()) {
            item {
                EmptyState("No runs yet.", "Start building your running history.") {
                    Button(onClick = onLog) { Text("Log Your First Activity") }
                }
            }
        } else {
            if (filters.size > 2) item { ChipChoice(filters, type, { type = it }) }
            item {
                AppCard(padding = 4.dp) {
                    list.forEachIndexed { i, a ->
                        if (i > 0) HorizontalDivider(color = Pal.Line)
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(a.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(a.date.weekdayShort().uppercase(), fontSize = 10.sp, color = Pal.Muted, fontWeight = FontWeight.Bold)
                                Text(a.date.dayOfMonth.toString(), style = Numeric, fontSize = 18.sp)
                                Text(a.date.monthShort().uppercase(), fontSize = 10.sp, color = Pal.Muted, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.title + if (a.plannedId != null) "  🔗" else "", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    formatPace(a.paceSecPerKm) + (a.avgHeartRate?.let { " · $it bpm" } ?: ""),
                                    color = Pal.InkSoft, fontSize = 13.sp,
                                )
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(formatKm(a.distanceKm), style = Numeric, fontSize = 22.sp)
                                Text(" km", color = Pal.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One actual activity, with its plan if it had one. */
@Composable
fun RunDetailScreen(vm: AppViewModel, s: Snapshot, id: Long, onEdit: () -> Unit, onDeleted: () -> Unit, onOpenBlock: (Long) -> Unit) {
    val a = s.actual(id)
    LaunchedEffect(a == null) { if (a == null) onDeleted() }
    if (a == null) return
    val p = s.planned(a.plannedId)
    val block = p?.let { s.block(it.blockId) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val km = a.distanceKm
    val pace = a.paceSecPerKm
    val moving = if (km != null && pace != null) km * pace else null

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppCard(dark = true, padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(a.type)
                Spacer(Modifier.width(12.dp))
                Column {
                    Eyebrow(ActivityTypes.label(a.type), color = Color.White.copy(alpha = 0.6f))
                    Text(a.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
            Text(a.date.full(), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatKm(a.distanceKm, 2), style = Numeric, fontSize = 56.sp, color = Color.White, lineHeight = 56.sp)
                Text(" km", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row {
                com.pacelog.app.ui.components.StatPair("Pace", formatPace(a.paceSecPerKm), Modifier.weight(1f), light = true)
                com.pacelog.app.ui.components.StatPair("Avg HR", a.avgHeartRate?.let { "$it bpm" } ?: "—", Modifier.weight(1f), light = true)
                com.pacelog.app.ui.components.StatPair("Time", moving?.let { formatClock(it) } ?: "—", Modifier.weight(1f), light = true)
            }
        }

        AppCard {
            Eyebrow("Training")
            if (block != null) {
                Text(block.name, color = Pal.EmberDark, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenBlock(block.id) })
            } else {
                Text("Unplanned Activity", fontWeight = FontWeight.Bold)
            }
            if (p != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Planned", Modifier.weight(1f))
                    StatusChip(p.status)
                }
                Text(p.title + plannedTarget(p).let { if (it.isEmpty()) "" else " — $it" }, fontWeight = FontWeight.SemiBold)
                val plannedKm = p.plannedDistanceKm
                if (plannedKm != null && km != null) {
                    Text(
                        "Planned ${formatKm(plannedKm)} km · Actual ${formatKm(km)} km · Difference ${formatSignedKm(km - plannedKm)}",
                        color = Pal.Muted, fontSize = 13.sp,
                    )
                }
            }
        }

        AppCard {
            Eyebrow("Comment")
            if (a.comment != null) Text("“${a.comment}”", fontStyle = FontStyle.Italic, fontSize = 17.sp)
            else Text("No comment.", color = Pal.Muted)
        }

        ErrorText(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(52.dp)) { Text("Edit") }
            TextButton(onClick = { confirm = true }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Delete", color = Pal.Missed) }
        }
        Box(Modifier.height(24.dp))
    }

    if (confirm) {
        ConfirmDialog(
            "Delete this activity?",
            "This will also affect your mileage statistics." +
                if (p != null) "\n\nThe planned activity “${p.title}” stays in your plan and returns to planned." else "",
            "Delete",
            onConfirm = {
                confirm = false
                scope.launch {
                    // On success the activity disappears from the snapshot and the
                    // LaunchedEffect above navigates away (exactly once).
                    (vm.run { it.deleteActivity(a.id) } as? Outcome.Err)?.let { error = it.message }
                }
            },
            onDismiss = { confirm = false },
        )
    }
}

/** Log a new activity (usually unplanned), or edit an existing one. */
@Composable
fun ActivityFormScreen(vm: AppViewModel, s: Snapshot, editId: Long?, date: LocalDate?, onSaved: (Long) -> Unit, onCancel: () -> Unit) {
    val existing = s.actual(editId)
    val linked = existing?.let { s.planned(it.plannedId) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow(if (existing != null) "Edit Activity" else "Log Activity")
        Text(if (existing != null) existing.title else "What did you do?", fontSize = 28.sp, fontWeight = FontWeight.Black)
        if (existing == null) Text("Distance, pace, heart rate — done in under a minute.", color = Pal.Muted)
        if (linked != null) {
            Text(
                "🔗 Linked to: ${linked.date.weekdayShort()} ${linked.title}",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Ember50).padding(12.dp),
                fontWeight = FontWeight.SemiBold,
            )
        }
        AppCard(padding = 16.dp) {
            ActivityForm(
                mode = if (existing != null) ActivityFormMode.EDIT else ActivityFormMode.LOG,
                initial = existing,
                initialDate = date ?: LocalDate.now(),
                submitLabel = if (existing != null) "Save Changes" else "Save Activity",
                onCancel = onCancel,
            ) { v ->
                val r = vm.run { d ->
                    val input = validateActual(v.date, v.type, v.title, v.distanceKm, v.paceSecPerKm, v.avgHeartRate, v.comment)
                    if (existing != null) {
                        d.updateActivity(existing.id, input); existing.id
                    } else d.logActivity(input)
                }
                if (r is Outcome.Ok) onSaved(r.value)
                r
            }
        }
        Box(Modifier.height(24.dp))
    }
}
