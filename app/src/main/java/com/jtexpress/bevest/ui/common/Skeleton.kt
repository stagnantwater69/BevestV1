package com.jtexpress.bevest.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * Skeleton placeholders. A skeleton is shaped like the content it stands in for, so the
 * layout does not jump when data lands and the user can see what is coming.
 *
 * Only shown on *first* load. Once data exists, refreshes update in place — never blank
 * a screen that already has readings on it (plan section 20/21).
 */

@Composable
private fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    // Respect reduced motion / preview: a flat fill, no animation.
    if (LocalInspectionMode.current) {
        return Brush.linearGradient(listOf(base, base))
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val shift = progress * 1200f - 400f
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shift, 0f),
        end = Offset(shift + 400f, 200f),
    )
}

/** A single shimmering block. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.sm),
) {
    val brush = shimmerBrush()
    Box(
        modifier
            .clip(shape)
            .drawWithCache { onDrawBehind { drawRect(brush) } },
    )
}

/** A shimmering text line of a given width fraction. */
@Composable
fun SkeletonLine(
    widthFraction: Float = 1f,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    SkeletonBox(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = RoundedCornerShape(Radius.sm),
    )
}

/** Placeholder shaped like a WorkerCard: avatar, two lines, status pill. */
@Composable
fun WorkerCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBox(Modifier.size(48.dp), CircleShape)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SkeletonLine(0.55f, 16.dp)
            SkeletonLine(0.8f, 12.dp)
            SkeletonLine(0.35f, 10.dp)
        }
        SkeletonBox(
            Modifier.width(72.dp).height(26.dp),
            RoundedCornerShape(Radius.sm),
        )
    }
}

/** Placeholder shaped like a StatTile. */
@Composable
fun StatTileSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SkeletonBox(Modifier.size(20.dp), CircleShape)
        SkeletonLine(0.5f, 26.dp)
        SkeletonLine(0.75f, 11.dp)
    }
}

/** Full dashboard first-load state: stat grid + a few worker rows. */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTileSkeleton(Modifier.weight(1f))
            StatTileSkeleton(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTileSkeleton(Modifier.weight(1f))
            StatTileSkeleton(Modifier.weight(1f))
        }
        SkeletonLine(0.4f, 18.dp, Modifier.padding(top = Spacing.sm))
        repeat(3) { WorkerCardSkeleton() }
    }
}

/** First-load state for any simple list of cards. */
@Composable
fun ListSkeleton(rows: Int = 5, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(rows) { WorkerCardSkeleton() }
    }
}

/** First-load state for a detail screen: header, then sensor cards. */
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SkeletonBox(Modifier.size(64.dp), CircleShape)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SkeletonLine(0.6f, 20.dp)
                SkeletonLine(0.4f, 13.dp)
            }
        }
        repeat(4) {
            SkeletonBox(
                Modifier.fillMaxWidth().height(88.dp),
                RoundedCornerShape(Radius.md),
            )
        }
    }
}

/** First-load state for a form: a stack of field-shaped blocks. */
@Composable
fun FormSkeleton(fields: Int = 4, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        repeat(fields) {
            SkeletonBox(
                Modifier.fillMaxWidth().height(56.dp),
                RoundedCornerShape(Radius.md),
            )
        }
        SkeletonBox(
            Modifier.fillMaxWidth().height(52.dp),
            RoundedCornerShape(Radius.md),
        )
    }
}
