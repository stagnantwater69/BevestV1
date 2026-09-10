package com.jtexpress.bevest.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.ComponentHeight
import com.jtexpress.bevest.ui.theme.Elevation
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Motion
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.ui.theme.StatStyle
import com.jtexpress.bevest.ui.theme.VitalStyle
import com.jtexpress.bevest.utils.DateTimeUtils

// ---------------------------------------------------------------- status

/**
 * Safety status pill. Carries an icon *and* a written label, so status survives
 * greyscale, sunlight, and color blindness (plan section 23).
 */
@Composable
fun StatusChip(
    status: SafetyStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val target = LocalStatusPalette.current.forStatus(status)
    // Ease between status colors so a worker flipping WARNING -> DANGER draws the eye
    // with movement, not just a hard swap.
    val color by animateColorAsState(target, tween(Motion.STATUS), label = "statusChipColor")
    Row(
        modifier = modifier
            .clip(BevestShapes.tile)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), BevestShapes.tile)
            .padding(horizontal = if (compact) Spacing.sm else Spacing.md, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = BevestIcons.forStatus(status),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(if (compact) IconSize.inline else IconSize.small),
        )
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * Generic state badge for things that are not a [SafetyStatus] — a vest's condition, an
 * alert's lifecycle. Same shape and weight as [StatusChip] so the two read as siblings.
 */
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(BevestShapes.tile)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.30f), BevestShapes.tile)
            .padding(horizontal = Spacing.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(IconSize.inline))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Small live/offline dot with an accessible description. */
@Composable
fun ConnectionIndicator(online: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current
    Box(
        modifier
            .size(9.dp)
            .clip(BevestShapes.round)
            .background(if (online) palette.normal else palette.offline)
            .clearAndSetSemantics {
                contentDescription = if (online) "Online" else "Offline"
            },
    )
}

/** "Updated 2m ago · connection lost" — always beside live data (plan section 21). */
@Composable
fun LastUpdatedLabel(
    millis: Long?,
    stale: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStatusPalette.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (stale) BevestIcons.NoSignal else BevestIcons.LastSeen,
            contentDescription = null,
            tint = if (stale) palette.offline else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = buildString {
                append(DateTimeUtils.relativeAge(millis))
                if (stale) append(" · connection lost")
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (stale) palette.offline else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Battery level with a low-battery icon swap. */
@Composable
fun BatteryIndicator(percent: Int?, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current
    val low = percent != null && percent <= 20
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (low) BevestIcons.BatteryLow else BevestIcons.Battery,
            contentDescription = "Vest battery",
            tint = if (low) palette.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = percent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = if (low) palette.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- cards

/**
 * The one card surface used everywhere: [surface] fill, a hairline border for definition
 * (which reads in both themes, unlike an alpha-blended fill), a shallow resting shadow.
 *
 * [shape] defaults to the neutral rounded card. Pass a chamfered shape from
 * [BevestShapes] for a safety-critical surface — see `Shapes.kt` for why that
 * distinction is worth keeping. Pass [accent] to tint the border for a card that needs
 * to pull the eye.
 */
@Composable
fun BevestCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    shape: Shape = BevestShapes.card,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            accent?.copy(alpha = 0.45f) ?: MaterialTheme.colorScheme.outlineVariant,
        ),
        shadowElevation = Elevation.card,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Dashboard summary tile: icon, big tabular number, label. */
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val tint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
    BevestCard(
        modifier = modifier.heightIn(min = 96.dp),
        onClick = onClick,
        accent = accent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSize.medium))
            Text(
                value,
                style = StatStyle,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label.uppercase(),
                style = EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A live sensor reading: icon, label, big tabular value with its unit, status tint.
 *
 * Chamfered, because this is a safety readout rather than browsable content. Pass
 * [trace] to draw the recent history as an ECG line beneath the number — worth doing for
 * heart rate, where the trend says more than the instantaneous value.
 */
@Composable
fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    accent: Color? = null,
    stale: Boolean = false,
    supporting: String? = null,
    trace: List<Int>? = null,
) {
    val palette = LocalStatusPalette.current
    val tint = when {
        stale -> palette.offline
        accent != null -> accent
        else -> MaterialTheme.colorScheme.onSurface
    }
    BevestCard(
        modifier = modifier.fillMaxWidth(),
        accent = if (!stale) accent else null,
        shape = BevestShapes.status,
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(BevestShapes.inner)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label.uppercase(),
                    style = EyebrowStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        value,
                        style = VitalStyle,
                        color = tint,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (unit != null) {
                        Text(
                            unit,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                when {
                    stale -> Text(
                        "Not live",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.offline,
                    )
                    supporting != null -> Text(
                        supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trace != null && !stale) {
                EcgTrace(
                    samples = trace,
                    color = tint,
                    modifier = Modifier.width(64.dp).height(36.dp),
                )
            }
        }
    }
}

/** Circular worker avatar — photo when available, initials otherwise. */
@Composable
fun WorkerAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    status: SafetyStatus? = null,
    size: Dp = 48.dp,
) {
    val palette = LocalStatusPalette.current
    val ring = status?.let { palette.forStatus(it) }
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    Box(
        modifier
            .size(size)
            .clip(BevestShapes.round)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .then(if (ring != null) Modifier.border(2.dp, ring, BevestShapes.round) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank() && !LocalInspectionMode.current) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(BevestShapes.round),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---------------------------------------------------------------- emergency

/**
 * The single most important element in the app.
 *
 * It carries every emphasis device the design system has, and it is the only place any
 * of them appear together: the deepest chamfer, a hazard stripe, a pulsing glyph, and
 * the emergency color as a full fill. That concentration is deliberate — nothing else
 * competing for attention means an officer glancing at their phone cannot miss it.
 *
 * Tapping opens the emergency. No confirmation, ever (plan section 23).
 */
@Composable
fun EmergencyBanner(
    workerName: String,
    reason: String,
    onView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStatusPalette.current
    val haptics = LocalHapticFeedback.current
    val pulse = if (LocalInspectionMode.current) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "emergencyPulse")
        val v by transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(Motion.PULSE),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "emergencyPulseValue",
        )
        v
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(BevestShapes.emergency)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onView()
            }
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Emergency: $workerName. ${reason.ifBlank { "No safety response" }}. Open."
            },
        shape = BevestShapes.emergency,
        color = palette.emergency,
        shadowElevation = Elevation.sheet,
    ) {
        Column {
            HazardStripe()
            Row(
                Modifier.padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Icon(
                    imageVector = BevestIcons.forStatus(SafetyStatus.EMERGENCY),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp).alpha(pulse),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "EMERGENCY",
                        style = EyebrowStyle,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Text(
                        workerName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        reason.ifBlank { "No safety response" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(IconSize.large),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- structure

/**
 * Section heading, underlined by the reflective band.
 *
 * The band is what makes a BeVest screen recognisable at a glance, and it does real work
 * too: on a long scrolling screen it gives the eye a hard stop between groups that a
 * bare text heading does not.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
        ReflectiveBand(emphasis = 0.7f, thickness = 2.dp)
    }
}

/**
 * A labelled fact in a detail screen: quiet label above, the value below.
 *
 * This replaces the `Text("Severity: ${alert.severity.name}")` pattern that the detail
 * screens were built with. Separating label from value lets the value take the weight,
 * so the screen can be skimmed for what changed rather than read line by line.
 */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: Color? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.small).padding(top = 2.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label.uppercase(),
                style = EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * A read of the data rather than the data itself — "3 workers above safe heart rate",
 * "no incidents in 12 days". Analytics screens lead with these and put the chart
 * underneath, because a number a user has to interpret has not finished its job.
 */
@Composable
fun InsightCard(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = BevestIcons.Reports,
    accent: Color? = null,
) {
    val tint = accent ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BevestShapes.inner,
        color = tint.copy(alpha = 0.08f),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSize.medium))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------- states

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading…") {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * An empty state that says what to do next.
 *
 * The glyph sits inside a dashed outline rather than a filled circle: the container is
 * visibly a slot waiting to be filled, which is the actual message. Every caller passes
 * a [message] naming the specific reason this list is empty, because "No data available"
 * tells a user nothing they had not already worked out.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = BevestIcons.EmptyInbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(BevestShapes.round)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = BevestShapes.round,
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.display),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xs))
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                icon = BevestIcons.Add,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }
}

/**
 * Failure state. Always offers a way forward: retry when the caller can retry, and a
 * plain description of what failed when it cannot.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val palette = LocalStatusPalette.current
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(BevestShapes.round)
                .background(palette.danger.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                BevestIcons.Error,
                contentDescription = null,
                tint = palette.danger,
                modifier = Modifier.size(IconSize.display),
            )
        }
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (onRetry != null) {
            Spacer(Modifier.height(Spacing.xs))
            PrimaryButton(
                text = "Try again",
                onClick = onRetry,
                icon = BevestIcons.Refresh,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }
}

/**
 * Inline confirmation that something worked, shown in place rather than as a toast that
 * can be missed. Fades to the normal status color so it reads as reassurance, not alarm.
 */
@Composable
fun SuccessNote(message: String, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BevestShapes.inner,
        color = palette.normal.copy(alpha = 0.10f),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                BevestIcons.forStatus(SafetyStatus.NORMAL),
                contentDescription = null,
                tint = palette.normal,
                modifier = Modifier.size(IconSize.small),
            )
            Text(message, style = MaterialTheme.typography.bodySmall, color = palette.normal)
        }
    }
}

/** Inline error, styled the same everywhere it appears. */
@Composable
fun ErrorNote(message: String, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BevestShapes.inner,
        color = palette.danger.copy(alpha = 0.10f),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                BevestIcons.Error,
                contentDescription = null,
                tint = palette.danger,
                modifier = Modifier.size(IconSize.small),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = palette.danger,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
