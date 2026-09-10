package com.jtexpress.bevest.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.ui.theme.LocalBrandPalette
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Motion
import kotlin.math.min

/**
 * The app's signature marks, taken straight off the vest in the BeVest logo.
 *
 * Each one earns its place by doing a job that a plain rectangle could not:
 *
 *  - [ReflectiveBand] separates sections and marks the active tab. It is the single
 *    most recognizable element in the product — a screenshot with a band in it is
 *    identifiable as BeVest with the logo cropped out.
 *  - [StatusShield] frames a safety state in the shield from the logo, so the container
 *    carries meaning rather than just holding a color.
 *  - [EcgTrace] renders heart-rate history as an actual cardiac waveform. A generic
 *    sparkline would show the same numbers; this says *whose* numbers they are.
 *  - [HazardStripe] is caution tape, and appears on exactly one thing: an active
 *    emergency. Reserving it is what keeps it loud.
 */

// ------------------------------------------------------------------ band

/**
 * The retroreflective stripe. Drawn as edge / bright core / edge, the way a real band
 * catches light, rather than as a flat rule.
 *
 * [emphasis] scales the whole band's presence: a section divider sits low, the active
 * navigation tab sits high.
 */
@Composable
fun ReflectiveBand(
    modifier: Modifier = Modifier,
    thickness: Dp = 3.dp,
    emphasis: Float = 1f,
    tint: Color? = null,
) {
    val brand = LocalBrandPalette.current
    val core = tint ?: brand.bandCore
    val edge = tint?.copy(alpha = 0.35f) ?: brand.bandEdge

    Canvas(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .clearAndSetSemantics { },
    ) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to edge.copy(alpha = edge.alpha * emphasis),
                0.5f to core.copy(alpha = core.alpha * emphasis),
                1f to edge.copy(alpha = edge.alpha * emphasis),
            ),
        )
    }
}

// ---------------------------------------------------------------- shield

/**
 * The logo's shield, holding a safety status. Used where a state needs a container of
 * its own — the site status panel, a worker's headline state.
 *
 * [progress] optionally fills the shield from the bottom, which the pairing flow uses to
 * show a connection test completing.
 */
@Composable
fun StatusShield(
    status: SafetyStatus,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    progress: Float? = null,
    content: @Composable () -> Unit = {},
) {
    val color = LocalStatusPalette.current.forStatus(status)
    Box(
        modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val path = shieldPath(this.size)
            drawPath(path, color.copy(alpha = 0.12f))
            if (progress != null) {
                clipRect(top = this.size.height * (1f - progress.coerceIn(0f, 1f))) {
                    drawPath(path, color.copy(alpha = 0.28f))
                }
            }
            drawPath(
                path = path,
                color = color.copy(alpha = 0.55f),
                style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round),
            )
        }
        content()
    }
}

/**
 * The shield outline: square shoulders that taper to a point, matching the logo mark.
 * Proportions are held in fractions of the drawn size so it scales cleanly.
 */
private fun shieldPath(size: Size): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(w * 0.5f, h * 0.02f)
        lineTo(w * 0.94f, h * 0.20f)
        lineTo(w * 0.94f, h * 0.55f)
        // Shoulders curve down into the point.
        cubicTo(w * 0.94f, h * 0.80f, w * 0.74f, h * 0.94f, w * 0.5f, h * 0.99f)
        cubicTo(w * 0.26f, h * 0.94f, w * 0.06f, h * 0.80f, w * 0.06f, h * 0.55f)
        lineTo(w * 0.06f, h * 0.20f)
        close()
    }
}

// ------------------------------------------------------------------- ecg

/**
 * Heart-rate history drawn as a cardiac trace.
 *
 * Samples are oldest-first. The line is normalised across its own range rather than an
 * absolute BPM scale, so the *shape* of the last few minutes reads clearly even when the
 * numbers sit in a narrow band — which, for a healthy worker, they usually do.
 *
 * With fewer than two samples this draws a flat baseline rather than nothing, so the
 * card keeps its height and the layout does not jump when the first reading lands.
 */
@Composable
fun EcgTrace(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    color: Color? = null,
    strokeWidth: Dp = 2.dp,
) {
    val traceColor = color ?: LocalStatusPalette.current.normal
    val baseline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    Canvas(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
    ) {
        if (samples.size < 2) {
            drawLine(
                color = baseline,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = strokeWidth.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val lo = samples.min().toFloat()
        val hi = samples.max().toFloat()
        // A flat series would divide by zero; give it a nominal range so it centres.
        val range = (hi - lo).takeIf { it > 0.5f } ?: 1f
        val stepX = size.width / (samples.size - 1)
        val inset = strokeWidth.toPx()
        val usableHeight = size.height - inset * 2

        val path = Path()
        samples.forEachIndexed { i, sample ->
            val x = i * stepX
            val y = inset + usableHeight * (1f - (sample - lo) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = traceColor,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

// ---------------------------------------------------------------- hazard

/**
 * Caution-tape diagonals, for the emergency banner and nothing else.
 *
 * [animated] creeps the stripes sideways. The movement is slow and small: enough to
 * register as live in peripheral vision, not enough to compete with reading the name of
 * the worker who needs help.
 */
@Composable
fun HazardStripe(
    modifier: Modifier = Modifier,
    thickness: Dp = 8.dp,
    animated: Boolean = true,
) {
    val brand = LocalBrandPalette.current
    val stripe = brand.hazard
    val still = LocalInspectionMode.current

    val shift = if (animated && !still) {
        val transition = rememberInfiniteTransition(label = "hazard")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(Motion.PULSE * 3),
                repeatMode = RepeatMode.Restart,
            ),
            label = "hazardShift",
        )
        v
    } else {
        0f
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .clearAndSetSemantics { },
    ) {
        drawHazardDiagonals(stripe, shift)
    }
}

private fun DrawScope.drawHazardDiagonals(stripe: Color, shift: Float) {
    val band = 14.dp.toPx()
    val period = band * 2
    val offset = shift * period

    // Rotating about the centre swings the corners out by up to (width + height) / 2,
    // so the overdraw has to be based on both dimensions. Deriving it from the height
    // alone leaves most of a wide, short strip unpainted — the stripes then appear as a
    // patch in the middle rather than running edge to edge.
    val overscan = size.width + size.height

    // Clip to this composable's own bounds, because a Compose Canvas does not. Without
    // it the overdraw escapes into whatever ancestor sets the clip — on the emergency
    // banner that is the whole card, so the stripes ran across the worker's name.
    clipRect {
        rotate(degrees = -45f) {
            var x = -overscan + offset
            while (x < size.width + overscan) {
                drawRect(
                    color = stripe,
                    topLeft = Offset(x, -overscan),
                    size = Size(band, size.height + overscan * 2),
                )
                x += period
            }
        }
    }
}

// ----------------------------------------------------------------- pulse

/**
 * A live-data lamp: a filled dot with a ring that expands and fades, like a radar
 * return. Marks a reading that is arriving right now, as opposed to one being
 * remembered — which is the distinction that matters most on a monitoring screen.
 */
@Composable
fun LivePulse(
    modifier: Modifier = Modifier,
    color: Color? = null,
    size: Dp = 10.dp,
) {
    val dot = color ?: LocalStatusPalette.current.normal
    val still = LocalInspectionMode.current

    val phase = if (still) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "livePulse")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(Motion.PULSE + Motion.STANDARD),
                repeatMode = RepeatMode.Restart,
            ),
            label = "livePulsePhase",
        )
        v
    }

    Canvas(
        modifier
            .size(size * 2.4f)
            .clearAndSetSemantics { },
    ) {
        val core = min(this.size.width, this.size.height) / 2f * 0.42f
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        if (phase > 0f) {
            drawCircle(
                color = dot.copy(alpha = (1f - phase) * 0.45f),
                radius = core + (this.size.width / 2f - core) * phase,
                center = centre,
            )
        }
        drawCircle(color = dot, radius = core, center = centre)
    }
}

// ----------------------------------------------------------------- rules

/**
 * A dashed rule for "nothing here yet" boundaries — the outline of an empty-state
 * container, a slot waiting to be filled. Reads as provisional in a way a solid border
 * does not.
 */
@Composable
fun DashedRule(modifier: Modifier = Modifier, color: Color? = null) {
    val line = color ?: MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .clearAndSetSemantics { },
    ) {
        drawLine(
            color = line,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
            ),
        )
    }
}
