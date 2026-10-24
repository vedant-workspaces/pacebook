package com.pacelog.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacelog.app.ui.AppViewModel
import com.pacelog.app.ui.Outcome
import com.pacelog.app.ui.Snapshot
import com.pacelog.app.ui.components.AppCard
import com.pacelog.app.ui.components.AppTextField
import com.pacelog.app.ui.components.DateField
import com.pacelog.app.ui.components.EmptyState
import com.pacelog.app.ui.components.ErrorText
import com.pacelog.app.ui.components.Eyebrow
import com.pacelog.app.ui.components.StatPair
import com.pacelog.app.ui.theme.Numeric
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.Block
import com.pacelog.core.DateRange
import com.pacelog.core.Limits
import com.pacelog.core.ValidationException
import com.pacelog.core.dateRange
import com.pacelog.core.daysBetween
import com.pacelog.core.formatKm
import com.pacelog.core.formatPct
import com.pacelog.core.inRange
import com.pacelog.core.planSummary
import com.pacelog.core.totalKm
import com.pacelog.core.validateBlock
import com.pacelog.core.weekStart
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Training blocks: active, upcoming and past. */
@Composable
fun TrainingScreen(s: Snapshot, onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val today = LocalDate.now()
    val active = s.blocks.filter { !it.startDate.isAfter(today) && !it.endDate.isBefore(today) }
    val upcoming = s.blocks.filter { it.startDate.isAfter(today) }.sortedBy { it.startDate }
    val past = s.blocks.filter { it.endDate.isBefore(today) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Training", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("Your training blocks — the plan behind the miles.", color = Pal.Muted)
                }
            }
        }
        item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("+ Create Training Block", fontWeight = FontWeight.Bold)
            }
        }
        if (s.blocks.isEmpty()) {
            item {
                EmptyState("No training blocks yet.", "Create a training block to start planning your training.")
            }
        }
        listOf("Active" to active, "Upcoming" to upcoming, "Recent blocks" to past).forEach { (title, list) ->
            if (list.isNotEmpty()) {
                item { Eyebrow(title, Modifier.padding(top = 8.dp)) }
                items(list, key = { it.id }) { b -> BlockCard(s, b, today, title == "Active") { onOpen(b.id) } }
            }
        }
    }
}

@Composable
private fun BlockCard(s: Snapshot, b: Block, today: LocalDate, featured: Boolean, onClick: () -> Unit) {
    val planned = s.planned.filter { it.blockId == b.id }
    val actualKm = s.actuals.inRange(DateRange(b.startDate, b.endDate.plusDays(1))).totalKm()
    val sum = planSummary(planned, actualKm, today)
    val totalDays = daysBetween(b.startDate, b.endDate) + 1
    val weeks = ((totalDays + 6) / 7).toInt()
    val progress = ((daysBetween(b.startDate, today) + 1).toFloat() / totalDays).coerceIn(0f, 1f)
    AppCard(onClick = onClick) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(dateRange(b.startDate, b.endDate, today), color = Pal.Muted, fontSize = 13.sp)
                Text(b.name, fontSize = if (featured) 24.sp else 20.sp, fontWeight = FontWeight.Black)
                b.goal?.let { Text("🏆 Goal: $it", color = Pal.EmberDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPct(sum.completionPct), style = Numeric, fontSize = 30.sp)
                Text("completion", color = Pal.Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            StatPair("Length", "$weeks wk", Modifier.weight(1f))
            StatPair("Sessions", sum.sessionCount.toString(), Modifier.weight(1f))
            StatPair("Actual / plan", "${formatKm(sum.actualKm, 0)}/${formatKm(sum.plannedKm, 0)} km", Modifier.weight(1.4f))
        }
        if (featured) {
            Spacer(Modifier.height(12.dp))
            val weekNo = minOf(weeks, (daysBetween(b.startDate, today) / 7 + 1).toInt())
            Text("Week $weekNo of $weeks · ${(progress * 100).toInt()}% through the block", color = Pal.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = Pal.Ember,
                trackColor = Pal.Paper,
            )
        }
    }
}

/** Create or edit a training block. */
@Composable
fun BlockFormScreen(vm: AppViewModel, s: Snapshot, blockId: Long?, onDone: (Long) -> Unit, onCancel: () -> Unit) {
    val existing = s.block(blockId)
    val nextMonday = LocalDate.now().weekStart().plusDays(7)
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var goal by remember { mutableStateOf(existing?.goal.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var start by remember { mutableStateOf(existing?.startDate ?: nextMonday) }
    var end by remember { mutableStateOf(existing?.endDate ?: nextMonday.plusDays(16 * 7 - 1)) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val weeks = if (!end.isBefore(start)) ((daysBetween(start, end) + 7) / 7).toInt() else null

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (existing != null) "Edit Training Block" else "New Training Block", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("A block is a stretch of structured training toward a goal.", color = Pal.Muted)
        AppTextField(name, { name = it.take(Limits.NAME) }, "Training block name", placeholder = "Marathon Training — TMM 2027",
            error = errors["name"])
        AppTextField(goal, { goal = it.take(Limits.GOAL) }, "Goal", optional = true, placeholder = "Sub 4:00",
            hint = "e.g. Sub 4:00 Marathon, Build aerobic base, Prepare for 50K ultra")
        DateField("Start date", start, { start = it }, error = errors["startDate"])
        DateField("End date", end, { end = it }, min = start, error = errors["endDate"])
        weeks?.let { Text("$it weeks", color = Pal.Muted, fontSize = 13.sp) }
        AppTextField(description, { description = it.take(Limits.DESCRIPTION) }, "Description", optional = true,
            placeholder = "Focus, key races, notes for yourself…", singleLine = false)
        ErrorText(formError)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
            Button(
                onClick = {
                    val input = try {
                        validateBlock(name, description, start, end, goal)
                    } catch (e: ValidationException) {
                        errors = mapOf(e.field to (e.message ?: "Check this field"))
                        return@Button
                    }
                    errors = emptyMap()
                    saving = true
                    scope.launch {
                        val r = if (existing != null) vm.run { it.updateBlock(existing.id, input); existing.id } else vm.run { it.createBlock(input) }
                        when (r) {
                            is Outcome.Ok -> onDone(r.value)
                            is Outcome.Err -> {
                                formError = r.message
                                r.field?.let { f -> errors = mapOf(f to r.message) }
                            }
                        }
                        saving = false
                    }
                },
                enabled = !saving,
                modifier = Modifier.weight(2f).height(52.dp),
            ) { Text(if (existing != null) "Save Changes" else "Create Training Block", fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.height(40.dp))
    }
}
