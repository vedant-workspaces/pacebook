package com.pacelog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacelog.app.ui.theme.Numeric
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.PlanSummary
import com.pacelog.core.formatDistance
import com.pacelog.core.formatKm
import com.pacelog.core.formatPct
import com.pacelog.core.formatSignedKm
import kotlin.math.ceil
import kotlin.math.max

data class ChartRow(val label: String, val full: String, val actual: Double, val planned: Double)

@Composable
fun Legend(showPlanned: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(Pal.Actual, "Actual")
        if (showPlanned) LegendItem(Pal.Plan, "Planned")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Pal.InkSoft)
    }
}

private fun niceMax(v: Double): Double {
    if (v <= 0) return 10.0
    val step = when {
        v <= 10 -> 2.0
        v <= 50 -> 10.0
        v <= 200 -> 25.0
        else -> 100.0
    }
    return ceil(v / step) * step
}

/**
 * Columns of actual mileage, paired with planned columns when any plan is in
 * range. Tap a column to read its values. The accessible description lists
 * every value, so the chart is never the only way to get the numbers.
 */
@Composable
fun BarChart(rows: List<ChartRow>, modifier: Modifier = Modifier, height: Int = 200) {
    if (rows.isEmpty()) return
    val showPlanned = rows.any { it.planned > 0 }
    var selected by remember(rows) { mutableStateOf<Int?>(null) }
    val maxV = niceMax(rows.maxOf { max(it.actual, if (showPlanned) it.planned else 0.0) })
    val description = rows.joinToString("; ") { r ->
        "${r.full}: actual ${formatKm(r.actual)} km" + if (showPlanned) ", planned ${formatKm(r.planned)} km" else ""
    }
    val labelEvery = when {
        rows.size <= 8 -> 1
        rows.size <= 16 -> 2
        else -> 5
    }

    Column(modifier.fillMaxWidth()) {
        val sel = selected?.let { rows.getOrNull(it) }
        Text(
            if (sel != null) {
                "${sel.full} · Actual ${formatKm(sel.actual)} km" + if (showPlanned) " · Planned ${formatKm(sel.planned)} km" else ""
            } else {
                "Tap a bar for details"
            },
            fontSize = 12.sp,
            color = if (sel != null) Pal.Ink else Pal.Muted,
            fontWeight = if (sel != null) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row {
            Column(Modifier.height(height.dp).width(32.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(formatKm(maxV, 0), fontSize = 10.sp, color = Pal.Muted)
                Text(formatKm(maxV / 2, 0), fontSize = 10.sp, color = Pal.Muted)
                Text("0", fontSize = 10.sp, color = Pal.Muted)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .semantics { contentDescription = description }
                    .pointerInput(rows) {
                        detectTapGestures { pos ->
                            val i = (pos.x / (size.width.toFloat() / rows.size)).toInt().coerceIn(0, rows.lastIndex)
                            selected = if (selected == i) null else i
                        }
                    },
            ) {
                drawGrid()
                val slot = size.width / rows.size
                val groupWidth = slot * 0.62f
                val gap = 2.dp.toPx()
                val barCount = if (showPlanned) 2 else 1
                val barWidth = minOf((groupWidth - gap * (barCount - 1)) / barCount, 18.dp.toPx())
                rows.forEachIndexed { i, r ->
                    val totalW = barWidth * barCount + gap * (barCount - 1)
                    var x = slot * i + (slot - totalW) / 2
                    if (selected == i) {
                        drawRect(Pal.Ink.copy(alpha = 0.05f), topLeft = Offset(slot * i, 0f), size = Size(slot, size.height))
                    }
                    if (showPlanned) {
                        drawBar(x, barWidth, r.planned, maxV, Pal.Plan)
                        x += barWidth + gap
                    }
                    drawBar(x, barWidth, r.actual, maxV, Pal.Actual)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp)) {
            rows.forEachIndexed { i, r ->
                Text(
                    if (i % labelEvery == 0) r.label else "",
                    fontSize = 10.sp,
                    color = Pal.Muted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Legend(showPlanned)
    }
}

private fun DrawScope.drawGrid() {
    val line = Color(0xFFECE9E2)
    listOf(0f, 0.5f, 1f).forEach { f ->
        val y = size.height * f
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

/** A column with a 4dp rounded top, square at the baseline. */
private fun DrawScope.drawBar(x: Float, w: Float, value: Double, maxV: Double, color: Color) {
    if (value <= 0) return
    val h = (value / maxV * size.height).toFloat().coerceAtLeast(2.dp.toPx())
    val r = minOf(4.dp.toPx(), w / 2, h)
    drawRoundRect(color, topLeft = Offset(x, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(r, r))
    drawRect(color, topLeft = Offset(x, size.height - minOf(h, r)), size = Size(w, minOf(h, r)))
}

/** Neutral planned-vs-actual comparison: two labelled bars and the difference. */
@Composable
fun PlanComparison(s: PlanSummary) {
    val maxKm = max(max(s.plannedKm, s.actualKm), 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("Planned", s.plannedKm, Pal.Plan), Triple("Actual", s.actualKm, Pal.Actual)).forEach { (name, km, color) ->
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(name, fontWeight = FontWeight.SemiBold, color = Pal.InkSoft, modifier = Modifier.weight(1f))
                    Text(formatDistance(km), style = Numeric, fontSize = 20.sp)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Pal.Paper)) {
                    Box(Modifier.fillMaxWidth((km / maxKm).toFloat()).height(10.dp).clip(RoundedCornerShape(50)).background(color))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            StatPair("Difference", formatSignedKm(s.differenceKm), Modifier.weight(1f))
            StatPair("Sessions completed", formatPct(s.completionPct), Modifier.weight(1f))
        }
    }
}
