package com.jtexpress.bevest.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class BarDatum(val label: String, val value: Float)

/** Simple, readable bar chart drawn on Canvas — no external chart library (design plan). */
@Composable
fun BarChart(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (data.isEmpty()) return
    val maxValue = max(1f, data.maxOf { it.value })
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val gap = size.width / (data.size * 2f)
            val barWidth = gap
            data.forEachIndexed { index, datum ->
                val x = gap + index * (barWidth + gap)
                val barHeight = (datum.value / maxValue) * (size.height - 8.dp.toPx())
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
            data.forEach {
                Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

data class DonutSlice(val label: String, val value: Float, val color: Color)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    centerValue: String? = null,
    centerLabel: String? = null,
) {
    val total = max(1f, slices.sumOf { it.value.toDouble() }.toFloat())
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp)) {
                val stroke = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
                // faint full-circle track so an all-zero state still reads as a chart
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 20.dp.toPx()),
                )
                var startAngle = -90f
                slices.forEach { slice ->
                    val sweep = (slice.value / total) * 360f
                    if (sweep > 0f) {
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweep - 1.5f,
                            useCenter = false,
                            style = stroke,
                        )
                    }
                    startAngle += sweep
                }
            }
            if (centerValue != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        centerValue,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (centerLabel != null) {
                        Text(
                            centerLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            slices.forEach { slice ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(slice.color))
                        Text(
                            slice.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        slice.value.toInt().toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = slice.color,
                    )
                }
            }
        }
    }
}
