package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.FilterOption
import com.jtexpress.bevest.ui.common.FilterRow
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.common.label
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.ComponentHeight
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.utils.DateTimeUtils

/**
 * Alert history.
 *
 * Grouped by day rather than presented as one undifferentiated stream: a run of six
 * alerts is a very different situation depending on whether it happened this morning or
 * across three weeks, and a flat list hides that. Day headings carry the shape of the
 * history; rows only need the time.
 */
@Composable
fun AlertHistoryScreen(
    siteId: String?,
    onOpenAlert: (String) -> Unit,
    viewModel: AlertHistoryViewModel = hiltViewModel(),
) {
    LaunchedEffect(siteId) { viewModel.start(siteId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current

    BevestScaffold(
        title = "Alerts",
        subtitle = siteId,
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            state.alerts.isEmpty() -> EmptyState(
                title = "All clear",
                message = "No alerts have been raised on this site. They'll appear here " +
                    "as soon as a vest reports something outside safe limits.",
                icon = BevestIcons.forStatus(SafetyStatus.NORMAL),
                modifier = Modifier.padding(padding),
            )

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                FilterRow(
                    options = listOf(
                        FilterOption(AlertFilter.ALL.name, "All", state.countFor(AlertFilter.ALL)),
                        FilterOption(
                            AlertFilter.ACTIVE.name,
                            "Active",
                            state.countFor(AlertFilter.ACTIVE),
                            palette.danger,
                        ),
                        FilterOption(
                            AlertFilter.RESOLVED.name,
                            "Resolved",
                            state.countFor(AlertFilter.RESOLVED),
                            palette.normal,
                        ),
                    ),
                    selectedKey = state.filter.name,
                    onSelect = { viewModel.onFilter(AlertFilter.valueOf(it)) },
                )

                if (state.visible.isEmpty()) {
                    EmptyState(
                        title = "Nothing in this view",
                        message = when (state.filter) {
                            AlertFilter.ACTIVE -> "Every alert on this site has been resolved."
                            AlertFilter.RESOLVED -> "No alerts have been resolved yet."
                            AlertFilter.ALL -> "No alerts to show."
                        },
                        icon = BevestIcons.NoResults,
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.gutter,
                            end = Spacing.gutter,
                            top = Spacing.sm,
                            bottom = Spacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        state.grouped.forEach { (day, alerts) ->
                            item(key = "day-$day") {
                                DayHeading(day, alerts.size)
                            }
                            items(alerts, key = { it.alertId }) { alert ->
                                AlertRow(
                                    alert = alert,
                                    workerName = "Worker ${alert.workerId}",
                                    onClick = { onOpenAlert(alert.alertId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sticky-feeling day separator: the band again, so grouping looks native to the app. */
@Composable
private fun DayHeading(day: String, count: Int) {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                day.uppercase(),
                style = EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (count == 1) "1 alert" else "$count alerts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ReflectiveBand(thickness = 2.dp, emphasis = 0.5f)
    }
}

/**
 * An alert in a list.
 *
 * Severity is carried three ways at once — a colored stripe down the leading edge, the
 * type icon, and the written status — because this list gets read in sunlight, at
 * arm's length, by someone who may be color blind. The stripe is what makes it scannable
 * without reading: a column of red edges is a bad morning, visible before any word is.
 *
 * Resolved rows are deliberately drained of color and weight. They are history, and
 * should not compete with anything still open.
 */
@Composable
fun AlertRow(
    alert: Alert,
    workerName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStatusPalette.current
    val severityColor = when (alert.severity) {
        AlertSeverity.WARNING -> palette.warning
        AlertSeverity.DANGER -> palette.danger
        AlertSeverity.EMERGENCY -> palette.emergency
    }
    val resolved = alert.status == AlertStatus.RESOLVED
    val color = if (resolved) palette.offline else severityColor

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ComponentHeight.listRow)
            .clip(BevestShapes.alert)
            .clickable(onClick = onClick),
        shape = BevestShapes.alert,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (resolved) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                color.copy(alpha = 0.35f)
            },
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Severity stripe — readable before any text is parsed.
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .heightIn(min = ComponentHeight.listRow)
                    .background(color.copy(alpha = if (resolved) 0.35f else 1f)),
            )
            Row(
                Modifier.padding(Spacing.lg).weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(BevestShapes.tile)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        BevestIcons.forAlertType(alert.type),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(IconSize.medium),
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        alert.type.label(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        workerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        DateTimeUtils.timeOfDay(alert.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        alert.status.label().uppercase(),
                        style = EyebrowStyle,
                        color = if (resolved) palette.normal else color,
                    )
                }
            }
        }
    }
}
