package com.pacelog.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.ActivityTypes
import com.pacelog.core.Option
import com.pacelog.core.long
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

@Composable
fun FieldLabel(label: String, optional: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (optional) Text("  Optional", color = Pal.Muted, fontSize = 12.sp)
    }
}

@Composable
fun AppTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    optional: Boolean = false,
    placeholder: String? = null,
    suffix: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    hint: String? = null,
) {
    Column(modifier) {
        FieldLabel(label, optional)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .let { if (!singleLine) it.heightIn(min = 96.dp) else it },
            singleLine = singleLine,
            isError = error != null,
            placeholder = placeholder?.let { { Text(it, color = Pal.Muted.copy(alpha = 0.6f)) } },
            suffix = suffix?.let { { Text(it, color = Pal.Muted, fontWeight = FontWeight.SemiBold) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )
        when {
            error != null -> Text(error, color = Pal.Missed, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            hint != null -> Text(hint, color = Pal.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Pace entered as minutes : seconds per km. */
@Composable
fun PaceField(minutes: String, seconds: String, onChange: (String, String) -> Unit, label: String = "Pace", error: String? = null, hint: String? = null) {
    Column {
        FieldLabel(label, optional = true)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            OutlinedTextField(
                value = minutes,
                onValueChange = { v -> onChange(v.filter(Char::isDigit).take(2), seconds) },
                modifier = Modifier.width(76.dp),
                singleLine = true,
                isError = error != null,
                placeholder = { Text("5", color = Pal.Muted.copy(alpha = 0.6f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
            Text(" : ", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Pal.Muted)
            OutlinedTextField(
                value = seconds,
                onValueChange = { v -> onChange(minutes, v.filter(Char::isDigit).take(2)) },
                modifier = Modifier.width(76.dp),
                singleLine = true,
                isError = error != null,
                placeholder = { Text("30", color = Pal.Muted.copy(alpha = 0.6f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
            Text("  /km", color = Pal.Muted, fontWeight = FontWeight.SemiBold)
        }
        when {
            error != null -> Text(error, color = Pal.Missed, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            hint != null -> Text(hint, color = Pal.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Date chooser. Dates are calendar dates; the picker's UTC millis map 1:1 to epoch days. */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    min: LocalDate? = null,
    max: LocalDate? = null,
    error: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel(label)
        OutlinedTextField(
            value = date?.long() ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            isError = error != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clickable { open = true },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Choose date") },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = Pal.Ink,
                disabledBorderColor = if (error != null) Pal.Missed else Pal.Muted.copy(alpha = 0.5f),
                disabledTrailingIconColor = Pal.Muted,
            ),
        )
        if (error != null) Text(error, color = Pal.Missed, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).toEpochDay() * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, DAY_MS))
                    return (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Activity type selector: a field that opens the full list. */
@Composable
fun TypeField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel(label)
        OutlinedTextField(
            value = ActivityTypes.label(value),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clickable { open = true },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose type") },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = Pal.Ink,
                disabledBorderColor = Pal.Muted.copy(alpha = 0.5f),
                disabledTrailingIconColor = Pal.Muted,
            ),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                LazyColumn {
                    items(ActivityTypes.all) { o ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChange(o.value); open = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = o.value == value, onClick = { onChange(o.value); open = false })
                            Text(o.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

/** Large tappable chips for picking one option. */
@Composable
fun ChipChoice(options: List<Option>, selected: String?, onSelect: (String) -> Unit, label: String? = null) {
    Column {
        if (label != null) FieldLabel(label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            options.forEach { o ->
                FilterChip(
                    selected = o.value == selected,
                    onClick = { onSelect(o.value) },
                    label = { Text(o.label, fontWeight = FontWeight.SemiBold) },
                )
            }
        }
    }
}
