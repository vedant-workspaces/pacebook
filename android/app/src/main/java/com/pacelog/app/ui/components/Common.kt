package com.pacelog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacelog.app.R
import com.pacelog.app.ui.theme.Numeric
import com.pacelog.app.ui.theme.Pal
import com.pacelog.core.ActivityTypes
import com.pacelog.core.Actual
import com.pacelog.core.Planned
import com.pacelog.core.Reasons
import com.pacelog.core.Status
import com.pacelog.core.formatDistance
import com.pacelog.core.formatDuration
import com.pacelog.core.formatKm
import com.pacelog.core.formatPace

/** Small uppercase section label. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Pal.Muted) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) Pal.Ink else Pal.Card)
            .border(1.dp, if (dark) Pal.Ink else Pal.Line, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Large mileage-style number with a small unit. */
@Composable
fun BigNumber(value: String, unit: String? = null, color: Color = Pal.Ink, size: Int = 40) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = Numeric, fontSize = size.sp, color = color, lineHeight = size.sp)
        if (unit != null) {
            Text(" $unit", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (color == Pal.Ink) Pal.Muted else color.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

data class StatusStyle(val label: String, val symbol: String, val fg: Color, val bg: Color)

fun statusStyle(status: String) = when (status) {
    Status.COMPLETED -> StatusStyle("Completed", "✓", Pal.Done, Pal.Done50)
    Status.MODIFIED -> StatusStyle("Modified", "~", Pal.Changed, Pal.Changed50)
    Status.SKIPPED -> StatusStyle("Skipped", "✕", Pal.Missed, Pal.Missed50)
    else -> StatusStyle("Planned", "○", Pal.Planned, Pal.Planned50)
}

/** Status as symbol + colour, with an accessible label (never colour alone). */
@Composable
fun StatusDot(status: String, size: Dp = 28.dp) {
    val s = statusStyle(status)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(s.bg)
            .semantics { contentDescription = s.label },
        contentAlignment = Alignment.Center,
    ) {
        Text(s.symbol, color = s.fg, fontWeight = FontWeight.Black, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun StatusChip(status: String) {
    val s = statusStyle(status)
    Text(
        "${s.symbol} ${s.label.uppercase()}",
        color = s.fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(s.bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Round badge for an activity type. */
@Composable
fun TypeBadge(type: String, dark: Boolean = false) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (dark) Pal.Ink else Pal.Ember50),
        contentAlignment = Alignment.Center,
    ) {
        if (ActivityTypes.isRun(type)) {
            Icon(painterResource(R.drawable.ic_run), contentDescription = null, tint = if (dark) Color.White else Pal.EmberDark, modifier = Modifier.size(22.dp))
        } else {
            Text(ActivityTypes.label(type).take(1), color = if (dark) Color.White else Pal.EmberDark, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
    }
}

/** "8 km @ 5:45/km" or "45 min". */
fun plannedTarget(p: Planned): String {
    val parts = mutableListOf<String>()
    p.plannedDistanceKm?.takeIf { it > 0 }?.let { parts += formatDistance(it) }
    p.plannedPaceSecPerKm?.let { parts += "@ ${formatPace(it)}" }
    if (parts.isEmpty()) p.plannedDurationSec?.let { parts += formatDuration(it) }
    return parts.joinToString(" ")
}

/** One planned activity: what was intended. */
@Composable
fun PlannedRow(p: Planned, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val s = statusStyle(p.status)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(p.status)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (p.status == Status.SKIPPED) Pal.Muted else Pal.Ink,
                textDecoration = if (p.status == Status.SKIPPED) TextDecoration.LineThrough else null,
            )
            val target = plannedTarget(p).ifEmpty { ActivityTypes.label(p.type) }
            val reason = if (p.status == Status.SKIPPED && p.reason != null) " — ${Reasons.label(p.reason)}" else ""
            Text("$target · ${s.label}$reason", color = Pal.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** One actual activity: what was done. */
@Composable
fun ActivityRow(a: Actual, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeBadge(a.type)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title + if (a.plannedId != null) "  🔗" else "", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ActivityTypes.label(a.type), color = Pal.Muted, fontSize = 12.sp, maxLines = 1)
        }
        MiniStat(formatKm(a.distanceKm), "KM")
        MiniStat(formatPace(a.paceSecPerKm, withUnit = false), "/KM")
        MiniStat(a.avgHeartRate?.toString() ?: "—", "BPM")
    }
}

@Composable
private fun MiniStat(value: String, unit: String) {
    Column(Modifier.width(54.dp), horizontalAlignment = Alignment.End) {
        Text(value, style = Numeric, fontSize = 17.sp)
        Text(unit, color = Pal.Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(title: String, message: String? = null, action: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Pal.Line, shape)
            .background(Color.White.copy(alpha = 0.6f))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message != null) Text(message, color = Pal.Muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.size(8.dp))
            action()
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Pal.Missed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ErrorText(message: String?) {
    if (message == null) return
    Text(
        message,
        color = Pal.Missed,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Pal.Missed50)
            .padding(12.dp),
    )
}

/** Label/value pair used in stat grids. */
@Composable
fun StatPair(label: String, value: String, modifier: Modifier = Modifier, light: Boolean = false) {
    Column(modifier) {
        Text(label, color = if (light) Color.White.copy(alpha = 0.6f) else Pal.Muted, fontSize = 12.sp)
        Text(value, style = Numeric, fontSize = 22.sp, color = if (light) Color.White else Pal.Ink)
    }
}
