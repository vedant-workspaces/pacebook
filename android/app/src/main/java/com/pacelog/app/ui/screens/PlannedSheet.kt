package com.pacelog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacelog.app.ui.AppViewModel
import com.pacelog.app.ui.Outcome
import com.pacelog.app.ui.Snapshot
import com.pacelog.app.ui.components.ActivityForm
import com.pacelog.app.ui.components.ActivityFormMode
import com.pacelog.app.ui.components.AppTextField
import com.pacelog.app.ui.components.ChipChoice
import com.pacelog.app.ui.components.ConfirmDialog
import com.pacelog.app.ui.components.DateField
import com.pacelog.app.ui.components.ErrorText
import com.pacelog.app.ui.components.Eyebrow
import com.pacelog.app.ui.components.PaceField
import com.pacelog.app.ui.components.StatusChip
import com.pacelog.app.ui.components.TypeBadge
import com.pacelog.app.ui.components.TypeField
import com.pacelog.app.ui.components.plannedTarget
import com.pacelog.app.ui.theme.Numeric
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.ActivityTypes
import com.pacelog.core.Block
import com.pacelog.core.Limits
import com.pacelog.core.Parsed
import com.pacelog.core.Planned
import com.pacelog.core.Reasons
import com.pacelog.core.ResultInput
import com.pacelog.core.Status
import com.pacelog.core.formatDistance
import com.pacelog.core.formatDuration
import com.pacelog.core.formatPace
import com.pacelog.core.formatSignedKm
import com.pacelog.core.long
import com.pacelog.core.parseDecimal
import com.pacelog.core.parsePace
import com.pacelog.core.parseWhole
import com.pacelog.core.splitPace
import com.pacelog.core.validatePlanned
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class PlannedMode { VIEW, COMPLETE, MODIFY, SKIP, EDIT }

/**
 * Everything you can do with one planned activity: see it, mark it completed /
 * completed differently / not completed, edit it, or delete it.
 */
@Composable
fun PlannedSheet(
    vm: AppViewModel,
    snapshot: Snapshot,
    plannedId: Long,
    initialMode: PlannedMode = PlannedMode.VIEW,
    onOpenActivity: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = snapshot.planned(plannedId)
    LaunchedEffect(p == null) { if (p == null) onDismiss() }
    if (p == null) return
    val block = snapshot.block(p.blockId)
    val actual = snapshot.resultOf(p.id)
    var mode by remember(plannedId) { mutableStateOf(initialMode) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pal.Card) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val title = when (mode) {
                PlannedMode.VIEW -> p.title
                PlannedMode.COMPLETE -> "Complete Activity"
                PlannedMode.MODIFY -> "Completed Differently"
                PlannedMode.SKIP -> "Why didn't you complete this?"
                PlannedMode.EDIT -> "Edit Planned Activity"
            }
            Column {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(p.date.long() + (block?.let { " · ${it.name}" } ?: ""), color = Pal.Muted, fontSize = 14.sp)
            }

            when (mode) {
                PlannedMode.VIEW -> {
                    Row(verticalAlignment = Alignment.Top) {
                        TypeBadge(p.type, dark = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Eyebrow("Planned · ${ActivityTypes.label(p.type)}")
                            Text(
                                p.plannedDistanceKm?.takeIf { it > 0 }?.let { formatDistance(it) } ?: ActivityTypes.label(p.type),
                                style = Numeric, fontSize = 26.sp,
                            )
                            p.plannedPaceSecPerKm?.let { Text("Target pace ${formatPace(it)}", color = Pal.InkSoft) }
                            p.plannedDurationSec?.let { Text(formatDuration(it), color = Pal.InkSoft) }
                        }
                        StatusChip(p.status)
                    }
                    p.description?.let {
                        Text(it, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Paper).padding(12.dp))
                    }
                    if (p.status == Status.SKIPPED) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Missed50).padding(12.dp)) {
                            Text("Not completed — ${Reasons.label(p.reason)}", color = Pal.Missed, fontWeight = FontWeight.SemiBold)
                            p.notes?.let { Text(it, color = Pal.InkSoft) }
                        }
                    }
                    if (actual != null) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Pal.Line, RoundedCornerShape(16.dp)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Eyebrow(
                                    "Actual" + if (p.status == Status.MODIFIED) " · ${ActivityTypes.label(actual.type)}" else "",
                                    Modifier.weight(1f),
                                )
                                TextButton(onClick = { onOpenActivity(actual.id) }) { Text("Open activity") }
                            }
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(formatDistance(actual.distanceKm), style = Numeric, fontSize = 28.sp)
                                Text(formatPace(actual.paceSecPerKm), style = Numeric, fontSize = 18.sp)
                                actual.avgHeartRate?.let { Text("♥ $it bpm", fontWeight = FontWeight.SemiBold) }
                            }
                            val plannedKm = p.plannedDistanceKm
                            val actualKm = actual.distanceKm
                            if (plannedKm != null && actualKm != null) {
                                Text(
                                    "Planned ${formatDistance(plannedKm)} · Actual ${formatDistance(actualKm)} · " +
                                        "Difference ${formatSignedKm(actualKm - plannedKm)}",
                                    color = Pal.Muted, fontSize = 13.sp,
                                )
                            }
                            actual.comment?.let { Text("“$it”", fontStyle = FontStyle.Italic, color = Pal.InkSoft) }
                        }
                    }
                    ErrorText(error)

                    val big = Modifier.fillMaxWidth().height(52.dp)
                    if (actual == null) {
                        Button(onClick = { mode = PlannedMode.COMPLETE }, modifier = big) { Text("✓  Mark Completed", fontWeight = FontWeight.Bold) }
                        OutlinedButton(onClick = { mode = PlannedMode.SKIP }, modifier = big) {
                            Text(if (p.status == Status.SKIPPED) "✕  Change Reason" else "✕  Mark Not Completed")
                        }
                        OutlinedButton(onClick = { mode = PlannedMode.MODIFY }, modifier = big) { Text("~  Completed Differently") }
                    } else {
                        OutlinedButton(
                            onClick = { mode = if (p.status == Status.MODIFIED) PlannedMode.MODIFY else PlannedMode.COMPLETE },
                            modifier = big,
                        ) { Text("Edit Result") }
                        if (p.status == Status.COMPLETED) {
                            OutlinedButton(onClick = { mode = PlannedMode.MODIFY }, modifier = big) { Text("~  Mark as Modified") }
                        } else {
                            OutlinedButton(onClick = { mode = PlannedMode.COMPLETE }, modifier = big) { Text("✓  Mark as Completed") }
                        }
                    }
                    if (p.status == Status.SKIPPED) {
                        TextButton(onClick = {
                            scope.launch { (vm.run { it.reset(p.id) } as? Outcome.Err)?.let { error = it.message } }
                        }, modifier = big) { Text("Back to Planned") }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { mode = PlannedMode.EDIT }, modifier = Modifier.weight(1f)) { Text("Edit Plan") }
                        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Delete", color = Pal.Missed) }
                    }
                }

                PlannedMode.COMPLETE, PlannedMode.MODIFY -> ActivityForm(
                    mode = if (mode == PlannedMode.COMPLETE) ActivityFormMode.COMPLETE else ActivityFormMode.MODIFY,
                    planned = p,
                    initial = actual,
                    onCancel = { mode = PlannedMode.VIEW },
                ) { v ->
                    val r = ResultInput(v.distanceKm, v.paceSecPerKm, v.avgHeartRate, v.comment,
                        type = if (mode == PlannedMode.MODIFY) v.type else null)
                    val out = vm.run { if (mode == PlannedMode.COMPLETE) it.complete(p.id, r) else it.modify(p.id, r) }
                    if (out is Outcome.Ok) mode = PlannedMode.VIEW
                    out
                }

                PlannedMode.SKIP -> SkipForm(p, onCancel = { mode = PlannedMode.VIEW }) { reason, notes ->
                    val out = vm.run { it.skip(p.id, reason, notes) }
                    if (out is Outcome.Ok) mode = PlannedMode.VIEW
                    out
                }

                PlannedMode.EDIT -> if (block != null) {
                    PlannedForm(block, p.date, p, onCancel = { mode = PlannedMode.VIEW }) { input ->
                        val out = vm.run { it.updatePlanned(p.id, input) }
                        if (out is Outcome.Ok) mode = PlannedMode.VIEW
                        out
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete planned activity?",
            message = if (actual != null) "The plan entry is removed. The activity you logged for it is kept as an unplanned activity."
            else "This removes it from your training plan.",
            confirm = "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    when (val r = vm.run { it.deletePlanned(p.id) }) {
                        is Outcome.Ok -> onDismiss()
                        is Outcome.Err -> error = r.message
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** "Why didn't you complete this activity?" */
@Composable
private fun SkipForm(p: Planned, onCancel: () -> Unit, onSubmit: suspend (String?, String?) -> Outcome<*>) {
    var reason by remember { mutableStateOf(p.reason) }
    var notes by remember { mutableStateOf(p.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ChipChoice(Reasons.all, reason, { reason = it }, label = "Reason")
        AppTextField(notes, { notes = it.take(Limits.COMMENT) }, "Additional notes", optional = true,
            placeholder = "Legs felt heavy after yesterday's run.", singleLine = false)
        ErrorText(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) { Text("Back") }
            Button(
                onClick = {
                    if (reason == null) {
                        error = "Please choose a reason."
                        return@Button
                    }
                    saving = true
                    scope.launch {
                        (onSubmit(reason, notes) as? Outcome.Err)?.let { error = it.message }
                        saving = false
                    }
                },
                enabled = !saving,
                modifier = Modifier.weight(2f).height(52.dp),
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        }
    }
}

/** Create or edit a planned activity. Only date and type are required. */
@Composable
fun PlannedForm(
    block: Block,
    date: LocalDate,
    initial: Planned? = null,
    onCancel: () -> Unit,
    onSubmit: suspend (com.pacelog.core.PlannedInput) -> Outcome<*>,
) {
    var d by remember { mutableStateOf(initial?.date ?: date) }
    var type by remember { mutableStateOf(initial?.type ?: "easy_run") }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var distance by remember { mutableStateOf(initial?.plannedDistanceKm?.let { com.pacelog.core.formatKm(it, 3).replace(",", "") } ?: "") }
    val (pm0, ps0) = splitPace(initial?.plannedPaceSecPerKm)
    var pm by remember { mutableStateOf(pm0) }
    var ps by remember { mutableStateOf(ps0) }
    var minutes by remember { mutableStateOf(initial?.plannedDurationSec?.let { (it / 60).toString() } ?: "") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rest = type == ActivityTypes.REST

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DateField("Date", d, { d = it }, min = block.startDate, max = block.endDate, error = errors["date"])
        TypeField("Activity type", type, { type = it })
        AppTextField(title, { title = it.take(Limits.NAME) }, "Title", optional = true, placeholder = "Easy Aerobic Run",
            hint = "Defaults to “${ActivityTypes.label(type)}”")
        AppTextField(description, { description = it.take(Limits.DESCRIPTION) }, "Description", optional = true,
            placeholder = "6 × 1 km @ 4:30/km, 90 s jog recovery", singleLine = false)
        if (!rest) {
            AppTextField(distance, { distance = it }, "Planned distance", optional = true, placeholder = "8", suffix = "km",
                keyboard = KeyboardType.Decimal, error = errors["distance"])
            PaceField(pm, ps, { a, b -> pm = a; ps = b }, label = "Planned pace", error = errors["pace"])
            AppTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, "Planned duration", optional = true,
                placeholder = "45", suffix = "min", keyboard = KeyboardType.Number, error = errors["duration"])
        }
        ErrorText(formError)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
            Button(
                onClick = {
                    val e = mutableMapOf<String, String>()
                    val km = parseDecimal(distance)
                    val pace = parsePace(pm, ps)
                    val dur = parseWhole(minutes)
                    if (km == Parsed.Invalid) e["distance"] = "Enter a number, e.g. 8"
                    if (pace == Parsed.Invalid) e["pace"] = "Enter a pace like 5:45"
                    if (dur == Parsed.Invalid) e["duration"] = "Enter whole minutes"
                    if (e.isNotEmpty()) { errors = e; return@Button }
                    val input = try {
                        validatePlanned(
                            block, d, type, title, description,
                            (km as? Parsed.Value)?.value, (pace as? Parsed.Value)?.value,
                            (dur as? Parsed.Value)?.value?.times(60),
                        )
                    } catch (ex: com.pacelog.core.ValidationException) {
                        errors = mapOf(ex.field to (ex.message ?: "Check this field"))
                        return@Button
                    }
                    errors = emptyMap()
                    saving = true
                    scope.launch {
                        (onSubmit(input) as? Outcome.Err)?.let { formError = it.message }
                        saving = false
                    }
                },
                enabled = !saving,
                modifier = Modifier.weight(2f).height(52.dp),
            ) { Text("Save Activity", fontWeight = FontWeight.Bold) }
        }
    }
}
