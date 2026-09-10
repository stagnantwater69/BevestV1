package com.jtexpress.bevest.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.Motion
import com.jtexpress.bevest.ui.theme.Spacing
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Charts for a phone held in one hand, on a building site.
 *
 * Every chart here follows two rules that the previous versions did not. First, values
 * are printed, not just plotted — reading a height off an unlabelled axis is a task, and
 * on a 5-inch screen it is an unreliable one. Second, each chart is paired with a
 * sentence saying what it means; a chart the reader has to interpret unaided has not
 * finished its job, and a chart nobody can interpret is decoration.
 */

data class BarDatum(val label: String, val value: Float)

/**
 * A trend over time.
 *
 * The most recent bar is drawn in full color and the earlier ones muted, because on a
 * trend chart "what is it doing now" is nearly always the question, and giving every bar
 * equal weight makes the reader hunt for the end of the series.
 */
@Composable
fun TrendBars(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    height: androidx.compose.ui.unit.Dp = 140.dp,
) {
    if (data.isEmpty()) return
    val accent = barColor ?: MaterialTheme.colorScheme.primary
    val muted = accent.copy(alpha = 0.32f)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val maxValue = max(1f, data.maxOf { it.value })

    // Bars grow in on first composition, which reads as the data arriving.
    val grow by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(if (LocalInspectionMode.current) 0 else Motion.STANDARD * 2),
        label = "barGrow",
    )

    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = data.joinToString(", ") {
                "${it.label}: ${it.value.roundToInt()}"
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEachIndexed { index, datum ->
                val latest = index == data.lastIndex
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // The value, printed above its bar. No axis to read against.
                    Text(
                        datum.value.roundToInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (latest) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    val fraction = (datum.value / maxValue) * grow
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                    ) {
                        val barHeight = (size.height * fraction).coerceAtLeast(2f)
                        drawRoundRect(
                            color = if (latest) accent else muted,
                            topLeft = Offset(0f, size.height - barHeight),
                            size = Size(size.width, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawLine(
                axisColor,
                Offset(0f, 0f),
                Offset(size.width, 0f),
                strokeWidth = size.height,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            data.forEachIndexed { index, datum ->
                Text(
                    datum.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == data.lastIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

data class StatusSlice(val label: String, val value: Int, val color: Color)

/**
 * A breakdown of one population by status.
 *
 * Deliberately a horizontal stacked bar rather than the donut this replaced. A donut is
 * the default choice in every dashboard kit, and it is the wrong one here: it costs a
 * square of scarce vertical space, it makes small slices — which on a safety screen are
 * the *important* ones — nearly invisible, and comparing two arcs by eye is harder than
 * comparing two lengths. A bar spends one strip of height, keeps a single worker in
 * danger visible as a distinct band, and leaves room to print every count in full
 * underneath.
 *
 * Bands narrower than a minimum are widened so a lone danger case can never be a
 * sub-pixel sliver. That trades exact proportion for never hiding a person, which on
 * this screen is the right way round.
 */
@Composable
fun StatusBar(
    slices: List<StatusSlice>,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.value }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val grow by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(if (LocalInspectionMode.current) 0 else Motion.STANDARD * 2),
        label = "statusBarGrow",
    )

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(BevestShapes.pill)
                .background(trackColor)
                .semantics(mergeDescendants = true) {
                    contentDescription = slices.joinToString(", ") { "${it.value} ${it.label}" }
                },
        ) {
            if (total > 0) {
                Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                    val minBand = 6.dp.toPx()
                    // Lay out non-empty bands, floor each at minBand, then rescale so
                    // the whole set still fits exactly.
                    val present = slices.filter { it.value > 0 }
                    val raw = present.map { size.width * (it.value.toFloat() / total) }
                    val floored = raw.map { max(it, minBand) }
                    val scale = size.width / floored.sum().coerceAtLeast(1f)

                    var x = 0f
                    present.forEachIndexed { index, slice ->
                        val w = floored[index] * scale * grow
                        drawRect(
                            color = slice.color,
                            topLeft = Offset(x, 0f),
                            size = Size(w, size.height),
                        )
                        x += w
                    }
                }
            }
        }

        // Counts in full, with their share. A percentage alone hides how big the
        // population is; a count alone hides how unusual it is.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            slices.forEach { slice ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(BevestShapes.round)
                            .background(slice.color),
                    )
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        slice.value.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (slice.value > 0) {
                            slice.color
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        if (total > 0) {
                            "${(slice.value * 100f / total).roundToInt()}%"
                        } else {
                            "—"
                        },
                        style = EyebrowStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
