package com.pacelog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pacelog.app.ui.Outcome
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.ActivityTypes
import com.pacelog.core.Actual
import com.pacelog.core.Limits
import com.pacelog.core.Parsed
import com.pacelog.core.Planned
import com.pacelog.core.formatClock
import com.pacelog.core.parseDecimal
import com.pacelog.core.parsePace
import com.pacelog.core.parseWhole
import com.pacelog.core.splitPace
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * - LOG / EDIT: a whole actual activity (date, type, title, metrics)
 * - COMPLETE: result of a planned activity done as planned — metrics only
 * - MODIFY: result of a planned activity done differently — type + metrics
 */
enum class ActivityFormMode { LOG, EDIT, COMPLETE, MODIFY }

data class ActivityValues(
    val date: LocalDate,
    val type: String,
    val title: String,
    val distanceKm: Double?,
    val paceSecPerKm: Int?,
    val avgHeartRate: Int?,
    val comment: String?,
)

private val QUICK = listOf("easy_run", "long_run", "recovery", "tempo", "intervals")

@Composable
fun ActivityForm(
    mode: ActivityFormMode,
    planned: Planned? = null,
    initial: Actual? = null,
    initialDate: LocalDate = LocalDate.now(),
    submitLabel: String = "Save Activity",
    onCancel: (() -> Unit)? = null,
    onSubmit: suspend (ActivityValues) -> Outcome<*>,
) {
    val full = mode == ActivityFormMode.LOG || mode == ActivityFormMode.EDIT
    var date by remember { mutableStateOf(initial?.date ?: planned?.date ?: initialDate) }
    var type by remember {
        mutableStateOf(initial?.type ?: if (mode == ActivityFormMode.MODIFY) "easy_run" else planned?.type ?: "easy_run")
    }
    var title by remember { mutableStateOf(if (full) initial?.title.orEmpty() else "") }
    var distance by remember { mutableStateOf(initial?.distanceKm?.let { com.pacelog.core.formatKm(it, 3).replace(",", "") } ?: "") }
    val (pm0, ps0) = splitPace(initial?.paceSecPerKm)
    var paceMin by remember { mutableStateOf(pm0) }
    var paceSec by remember { mutableStateOf(ps0) }
    var hr by remember { mutableStateOf(initial?.avgHeartRate?.toString() ?: "") }
    var comment by remember { mutableStateOf(initial?.comment.orEmpty()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val effectiveType = if (mode == ActivityFormMode.COMPLETE) planned?.type ?: type else type
    val distanceRequired = ActivityTypes.isRun(effectiveType) && mode != ActivityFormMode.EDIT
    val km = (parseDecimal(distance) as? Parsed.Value)?.value
    val pace = (parsePace(paceMin, paceSec) as? Parsed.Value)?.value
    val moving = if (km != null && pace != null && km > 0) km * pace else null

    fun validate(): ActivityValues? {
        val e = mutableMapOf<String, String>()
        val d = parseDecimal(distance)
        when (d) {
            Parsed.Empty -> if (distanceRequired) e["distance"] = "Enter the distance you covered"
            Parsed.Invalid -> e["distance"] = "Enter a number, e.g. 8.4"
            is Parsed.Value -> when {
                d.value <= 0 -> e["distance"] = "Distance must be greater than zero"
                d.value >= Limits.MAX_DISTANCE_KM -> e["distance"] = "Distance must be less than 500 km"
            }
        }
        val p = parsePace(paceMin, paceSec)
        if (p == Parsed.Invalid || (p is Parsed.Value && p.value !in Limits.MIN_PACE..Limits.MAX_PACE)) {
            e["pace"] = "Enter a pace between 2:00 and 30:00 per km"
        }
        val h = parseWhole(hr)
        if (h == Parsed.Invalid || (h is Parsed.Value && h.value !in Limits.MIN_HR..Limits.MAX_HR)) {
            e["hr"] = "Heart rate must be between 30 and 250"
        }
        if (comment.length > Limits.COMMENT) e["comment"] = "Keep comments under 2000 characters"
        errors = e
        if (e.isNotEmpty()) return null
        return ActivityValues(
            date = date,
            type = effectiveType,
            title = title.trim(),
            distanceKm = (d as? Parsed.Value)?.value,
            paceSecPerKm = (p as? Parsed.Value)?.value,
            avgHeartRate = (h as? Parsed.Value)?.value,
            comment = comment.trim().ifEmpty { null },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (planned != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Pal.Paper)
                    .padding(12.dp),
            ) {
                Eyebrow("Planned")
                Text(
                    planned.title + plannedTarget(planned).let { if (it.isEmpty()) "" else " — $it" },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (full) {
            DateField("Date", date, { date = it }, max = LocalDate.now().plusYears(1))
            TypeField("Activity type", type, { type = it })
            if (mode == ActivityFormMode.LOG) {
                ChipChoice(ActivityTypes.all.filter { it.value in QUICK }, type, { type = it }, label = "Quick pick")
            }
        }
        if (mode == ActivityFormMode.MODIFY) {
            TypeField("What did you actually do?", type, { type = it })
        }
        AppTextField(
            distance, { distance = it }, "Distance",
            error = errors["distance"], optional = !distanceRequired, placeholder = "8.4", suffix = "km",
            keyboard = KeyboardType.Decimal,
        )
        PaceField(
            paceMin, paceSec, { m, s -> paceMin = m; paceSec = s },
            error = errors["pace"],
            hint = moving?.let { "Moving time ≈ ${formatClock(it)}" },
        )
        AppTextField(
            hr, { hr = it.filter(Char::isDigit).take(3) }, "Average heart rate",
            error = errors["hr"], optional = true, placeholder = "151", suffix = "bpm", keyboard = KeyboardType.Number,
        )
        if (full) {
            AppTextField(
                title, { title = it.take(Limits.NAME) }, "Title", optional = true, placeholder = "Morning run",
                hint = "Defaults to “${ActivityTypes.label(type)}”",
            )
        }
        AppTextField(
            comment, { comment = it.take(Limits.COMMENT) }, "Comment", optional = true,
            placeholder = "How did it feel?", singleLine = false, error = errors["comment"],
        )
        ErrorText(formError)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onCancel != null) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
            }
            Button(
                onClick = {
                    val v = validate() ?: return@Button
                    saving = true
                    formError = null
                    scope.launch {
                        val r = onSubmit(v)
                        if (r is Outcome.Err) {
                            formError = r.message
                            r.field?.let { errors = errors + (it to r.message) }
                        }
                        saving = false
                    }
                },
                enabled = !saving,
                modifier = Modifier.weight(2f).height(52.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.height(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(submitLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
