package com.jtexpress.bevest.ui.contractor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.utils.DateTimeUtils
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max

private const val MONTHS_BACK = 6

/** Which incidents the line counts. */
private enum class TrendSeries(val label: String, val severity: AlertSeverity?) {
    ALL("All incidents", null),
    WARNING("Warning", AlertSeverity.WARNING),
    DANGER("Danger", AlertSeverity.DANGER),
    EMERGENCY("Emergency", AlertSeverity.EMERGENCY),
}

/** What the x axis steps through. */
private enum class TrendRange(val label: String) { MONTHS("Month"), DAYS("Day") }

/** The last [n] month keys ("yyyy-MM"), oldest first. */
private fun recentMonthKeys(n: Int): List<String> = (n - 1 downTo 0).map { offset ->
    val c = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, -offset)
    }
    DateTimeUtils.monthKey(c.timeInMillis)
}

private fun daysIn(monthKey: String): Int {
    val (y, m) = monthKey.split("-").map { it.toInt() }
    return Calendar.getInstance().apply { set(y, m - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun dayOfMonth(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

/**
 * The contractor's incident trend as a line, mirroring the web dashboard's chart.
 *
 * - The line counts all incidents, or one severity.
 * - The x axis steps month by month over the last six months, or day by day through
 *   one chosen month.
 * - The y axis is the number of incidents.
 */
@Composable
fun IncidentTrendChart(incidents: List<Incident>, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current
    val monthKeys = remember { recentMonthKeys(MONTHS_BACK) }
    var series by rememberSaveable { mutableStateOf(TrendSeries.ALL) }
    var range by rememberSaveable { mutableStateOf(TrendRange.MONTHS) }
    var selectedMonth by rememberSaveable { mutableStateOf(monthKeys.last()) }

    val color = when (series) {
        TrendSeries.ALL -> MaterialTheme.colorScheme.primary
        TrendSeries.WARNING -> palette.warning
        TrendSeries.DANGER -> palette.danger
        TrendSeries.EMERGENCY -> palette.emergency
    }

    val matching = incidents.filter { series.severity == null || it.severity == series.severity }
    val (labels, values) = when (range) {
        TrendRange.MONTHS -> {
            val counts = matching.groupingBy { DateTimeUtils.monthKey(it.createdAt) }.eachCount()
            monthKeys.map { DateTimeUtils.monthLabel(it).take(3) } to monthKeys.map { counts[it] ?: 0 }
        }
        TrendRange.DAYS -> {
            val days = daysIn(selectedMonth)
            val counts = IntArray(days)
            matching
                .filter { DateTimeUtils.monthKey(it.createdAt) == selectedMonth }
                .forEach { counts[dayOfMonth(it.createdAt) - 1]++ }
            (1..days).map { it.toString() } to counts.toList()
        }
    }
    val total = values.sum()
    val period = when (range) {
        TrendRange.MONTHS -> "the last $MONTHS_BACK months"
        TrendRange.DAYS -> DateTimeUtils.monthLabel(selectedMonth)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // One quiet control row: range on the left, severity on the right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RangeToggle(range, onChange = { range = it })
            Spacer(Modifier.weight(1f))
            SeverityMenu(series, colorOf = { s ->
                when (s) {
                    TrendSeries.ALL -> MaterialTheme.colorScheme.primary
                    TrendSeries.WARNING -> palette.warning
                    TrendSeries.DANGER -> palette.danger
                    TrendSeries.EMERGENCY -> palette.emergency
                }
            }, onPick = { series = it })
        }
        if (range == TrendRange.DAYS) {
            val index = monthKeys.indexOf(selectedMonth)
            MonthStepper(
                months = monthKeys,
                selected = selectedMonth,
                canGoBack = index > 0,
                canGoForward = index < monthKeys.lastIndex,
                onBack = { selectedMonth = monthKeys[index - 1] },
                onForward = { selectedMonth = monthKeys[index + 1] },
                onPick = { selectedMonth = it },
            )
        }

        Text(
            "$total ${if (total == 1) "incident" else "incidents"} in $period",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LineChart(
            labels = labels,
            values = values,
            color = color,
            // Six monthly points have room for their numbers; thirty-one days do not.
            printValues = range == TrendRange.MONTHS,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics {
                    contentDescription = "${series.label} by ${range.label.lowercase()}: " +
                        labels.zip(values).joinToString(", ") { (l, v) -> "$l $v" }
                },
        )
    }
}

/** A compact two-way segmented toggle: Month | Day. */
@Composable
private fun RangeToggle(range: TrendRange, onChange: (TrendRange) -> Unit) {
    Surface(
        shape = BevestShapes.pill,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(Modifier.padding(3.dp)) {
            TrendRange.entries.forEach { r ->
                val selected = r == range
                Surface(
                    shape = BevestShapes.pill,
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                    modifier = Modifier
                        .clip(BevestShapes.pill)
                        .clickable(enabled = !selected) { onChange(r) },
                ) {
                    Text(
                        r.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Severity picker: the current series as a dot and label, the rest in a menu. */
@Composable
private fun SeverityMenu(
    series: TrendSeries,
    colorOf: @Composable (TrendSeries) -> Color,
    onPick: (TrendSeries) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(BevestShapes.pill)
                .clickable { open = true }
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colorOf(series)))
            Text(series.label, style = MaterialTheme.typography.labelMedium)
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = "Choose severity",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TrendSeries.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colorOf(s)))
                    },
                    onClick = { onPick(s); open = false },
                )
            }
        }
    }
}

/**
 * ‹ August 2026 › — the arrows step one month at a time; tapping the month opens a list
 * of every available month to jump straight to one.
 */
@Composable
private fun MonthStepper(
    months: List<String>,
    selected: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Previous month",
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            DateTimeUtils.monthLabel(selected),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(BevestShapes.pill)
                .clickable(onClickLabel = "Choose month") { open = true }
                .padding(horizontal = Spacing.md, vertical = 6.dp),
        )
        IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Next month",
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (open) {
        MonthPickerDialog(
            months = months,
            selected = selected,
            onPick = { onPick(it); open = false },
            onDismiss = { open = false },
        )
    }
}

/** A centred popup with every available month as a tile, three to a row. */
@Composable
private fun MonthPickerDialog(
    months: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = BevestShapes.card,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Select month", style = MaterialTheme.typography.titleMedium)
                // Newest first, matching the order the arrows walk back through.
                months.reversed().chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        row.forEach { key ->
                            MonthTile(
                                monthKey = key,
                                selected = key == selected,
                                onClick = { onPick(key) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keep tiles the same width when the last row is short.
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun MonthTile(
    monthKey: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = BevestShapes.control,
        color = if (selected) primary.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .clip(BevestShapes.control)
            .clickable(onClick = onClick),
    ) {
        // monthLabel gives e.g. "October 2026": short month on top, year below.
        val (month, year) = DateTimeUtils.monthLabel(monthKey).split(" ").let {
            it.first().take(3) to it.getOrElse(1) { "" }
        }
        Column(
            Modifier.padding(vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                month,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                year,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A single line over a labelled grid. The y axis keeps headroom above the peak so the
 * highest point never sits on the top edge, and steps in whole incidents.
 */
@Composable
private fun LineChart(
    labels: List<String>,
    values: List<Int>,
    color: Color,
    printValues: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val axisText = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val valueText: TextStyle = MaterialTheme.typography.labelSmall.copy(color = color)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface

    // At most four steps up the axis, in whole incidents, with room above the peak.
    val peak = values.maxOrNull() ?: 0
    val step = max(1, ceil((peak + 1) / 4.0).toInt())
    val yMax = step * 4

    Canvas(modifier) {
        val yLabelWidth = 28.dp.toPx()
        val xLabelHeight = 20.dp.toPx()
        val top = 16.dp.toPx()
        val plotLeft = yLabelWidth
        val plotRight = size.width - 8.dp.toPx()
        val plotBottom = size.height - xLabelHeight
        val plotHeight = plotBottom - top
        val plotWidth = plotRight - plotLeft

        // Grid and y labels.
        for (i in 0..4) {
            val v = step * i
            val y = plotBottom - plotHeight * v / yMax
            drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(v.toString(), axisText)
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
        }

        if (values.isEmpty()) return@Canvas
        val dx = if (values.size > 1) plotWidth / (values.size - 1) else 0f
        val points = values.mapIndexed { i, v ->
            Offset(
                x = if (values.size > 1) plotLeft + dx * i else plotLeft + plotWidth / 2,
                y = plotBottom - plotHeight * v / yMax,
            )
        }

        // Fill under the line, then the line itself.
        val fill = Path().apply {
            moveTo(points.first().x, plotBottom)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, plotBottom)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.13f))
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            line,
            color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Points: every one when there are few, otherwise only the non-zero days.
        val radius = if (values.size > 12) 2.5.dp.toPx() else 4.dp.toPx()
        points.forEachIndexed { i, p ->
            if (values.size <= 12 || values[i] > 0) {
                drawCircle(color, radius, p)
                drawCircle(surface, radius * 0.45f, p)
            }
            if (printValues) {
                val layout = measurer.measure(values[i].toString(), valueText)
                drawText(
                    layout,
                    topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height - 4.dp.toPx()),
                )
            }
        }

        // X labels: all of them for months; for days, the 1st, every 5th and the last.
        labels.forEachIndexed { i, label ->
            // A 5th-day label right before the last one (e.g. 30 next to 31) would collide.
            val show = labels.size <= 12 || i == 0 || i == labels.lastIndex ||
                ((i + 1) % 5 == 0 && labels.lastIndex - i > 2)
            if (!show) return@forEachIndexed
            val layout = measurer.measure(label, axisText)
            val x = (points[i].x - layout.size.width / 2f)
                .coerceIn(plotLeft - yLabelWidth / 2, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, plotBottom + 4.dp.toPx()))
        }
    }
}
