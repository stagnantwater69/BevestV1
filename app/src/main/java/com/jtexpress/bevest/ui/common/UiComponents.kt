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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.Elevation
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.ui.theme.StatStyle
import com.jtexpress.bevest.ui.theme.VitalStyle
import com.jtexpress.bevest.utils.DateTimeUtils

// ---------------------------------------------------------------- status

/**
 * Safety status pill. Carries an icon *and* a text label so status is never conveyed by
 * color alone (plan section 23).
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
    val color by animateColorAsState(target, tween(320), label = "statusChipColor")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = if (compact) Spacing.sm else Spacing.md, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = BevestIcons.forStatus(status),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(if (compact) 13.dp else 15.dp),
        )
        Text(
            text = status.name,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** Small live/offline dot with an accessible description. */
@Composable
fun ConnectionIndicator(online: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current
    Box(
        modifier
            .size(9.dp)
            .clip(CircleShape)
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
 * (which reads in both themes, unlike an alpha-blended fill), a shallow resting shadow,
 * and the standard large radius. Pass [accent] to tint the border for a card that needs
 * to pull the eye (a warning tile, a danger row).
 */
@Composable
fun BevestCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.lg)
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
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
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

/** A live sensor reading: icon, label, big tabular value with its unit, status tint. */
@Composable
fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String? = null,
    accent: Color? = null,
    stale: Boolean = false,
    modifier: Modifier = Modifier,
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
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Radius.md))
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
                if (stale) {
                    Text(
                        "Not live",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.offline,
                    )
                }
            }
        }
    }
}

/** Circular worker avatar — photo when available, initials otherwise. */
@Composable
fun WorkerAvatar(
    name: String,
    photoUrl: String?,
    status: SafetyStatus? = null,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    modifier: Modifier = Modifier,
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
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank() && !LocalInspectionMode.current) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
 * The single most important element in the app. It is also the only thing that animates:
 * a slow pulse so it is unmissable in peripheral vision. Tapping opens the emergency —
 * no confirmation, ever (plan section 23).
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
                animation = tween(900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "emergencyPulseValue",
        )
        v
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onView()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "Emergency: $workerName. ${reason.ifBlank { "No safety response" }}. Open."
            },
        shape = RoundedCornerShape(Radius.lg),
        color = palette.emergency,
        shadowElevation = Elevation.sheet,
    ) {
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
                contentDescription = "View emergency",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- states

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        trailing?.invoke()
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading…") {
    // Kept for compatibility; skeletons are preferred on first load.
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
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
            Button(onClick = onAction, modifier = Modifier.heightIn(min = Spacing.touchTarget)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStatusPalette.current
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(palette.danger.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                BevestIcons.Error,
                contentDescription = null,
                tint = palette.danger,
                modifier = Modifier.size(34.dp),
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
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = Spacing.touchTarget),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(BevestIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Try again", modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}
