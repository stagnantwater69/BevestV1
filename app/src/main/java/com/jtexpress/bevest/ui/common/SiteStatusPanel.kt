package com.jtexpress.bevest.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.Elevation
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Motion
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.ui.theme.StatStyle

/** One figure in the panel's count strip. */
data class SiteCount(
    val value: Int,
    val label: String,
    val accent: Color? = null,
)

/**
 * The dashboard hero: a single answer to "is my site all right?".
 *
 * This replaces the grid of stat tiles the dashboard used to open with. Four numbers in
 * four boxes is not a summary — it is four separate readings the officer has to compare
 * and interpret before they know whether to do anything. That interpretation is work the
 * screen can do instead, so this panel leads with a plain-language verdict
 * ("All clear", "2 workers need attention") derived from the worst status on site, and
 * demotes the raw counts to a strip underneath for anyone who wants the detail.
 *
 * The shield and the reflective band are doing real work here too. The shield takes the
 * worst status as its color, so the panel's identity changes with the site's condition
 * and is legible from arm's length before a word is read; the band separates verdict
 * from figures without spending another border or another card.
 */
@Composable
fun SiteStatusPanel(
    status: SafetyStatus,
    headline: String,
    supporting: String,
    counts: List<SiteCount>,
    modifier: Modifier = Modifier,
    live: Boolean = true,
) {
    val palette = LocalStatusPalette.current
    val target = palette.forStatus(status)
    val accent by animateColorAsState(target, tween(Motion.STATUS), label = "sitePanelAccent")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Site status: $headline. $supporting"
            },
        shape = BevestShapes.status,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shadowElevation = Elevation.card,
    ) {
        Column {
            Row(
                Modifier.padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                StatusShield(status = status, size = 58.dp) {
                    Icon(
                        BevestIcons.forStatus(status),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            "SITE STATUS",
                            style = EyebrowStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // The lamp says the readings behind this verdict are arriving
                        // now — the difference between "all clear" and "was all clear".
                        if (live) LivePulse(color = accent, size = 6.dp)
                    }
                    Text(
                        headline,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ReflectiveBand(thickness = 2.dp, emphasis = 0.8f)

            // Four columns across a phone stops working once the user scales text up:
            // at 2x, "ON SITE" and "WARNING" collide and wrap mid-word. Past a threshold
            // the strip reflows to two rows of two, which holds all the way to the
            // largest accessibility size. Keyed off fontScale because that is the actual
            // cause, rather than guessing from screen width.
            val wide = LocalDensity.current.fontScale < 1.5f
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md, horizontal = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                counts.chunked(if (wide) 4 else 2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        row.forEach { count ->
                            CountFigure(count, Modifier.weight(1f))
                        }
                        // Keep a short final row aligned with the one above it.
                        repeat((if (wide) 4 else 2) - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountFigure(count: SiteCount, modifier: Modifier = Modifier) {
    // A zero is good news on this screen, so it stays quiet: only a non-zero count
    // earns its semantic color, and a zero danger count reads as calm rather than red.
    val emphasised = count.value > 0 && count.accent != null
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "${count.value} ${count.label}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            count.value.toString(),
            style = StatStyle,
            color = if (emphasised) count.accent!! else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            count.label.uppercase(),
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Two lines so a long label wraps between words instead of being cut, and
            // ellipsis rather than a mid-word break if it still does not fit.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
