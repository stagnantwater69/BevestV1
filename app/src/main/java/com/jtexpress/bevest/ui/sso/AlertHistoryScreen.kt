package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.utils.DateTimeUtils

@Composable
fun AlertHistoryScreen(
    siteId: String?,
    onOpenAlert: (String) -> Unit,
    viewModel: AlertHistoryViewModel = hiltViewModel(),
) {
    LaunchedEffect(siteId) { viewModel.start(siteId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    BevestScaffold(
        title = "Alerts",
        subtitle = siteId,
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.alerts.isEmpty() -> EmptyState(
                title = "All clear",
                message = "No alerts have been raised on this site.",
                icon = BevestIcons.forStatus(com.jtexpress.bevest.domain.model.SafetyStatus.NORMAL),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.md,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.alerts, key = { it.alertId }) { alert ->
                    AlertRow(alert, onClick = { onOpenAlert(alert.alertId) })
                }
            }
        }
    }
}

/**
 * Alert row with a severity stripe down the left edge — severity is readable before
 * any text is parsed, and the icon repeats the meaning without relying on color.
 */
@Composable
fun AlertRow(
    alert: Alert,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStatusPalette.current
    val color = when (alert.severity) {
        AlertSeverity.WARNING -> palette.warning
        AlertSeverity.DANGER -> palette.danger
        AlertSeverity.EMERGENCY -> palette.emergency
    }
    val resolved = alert.status == AlertStatus.RESOLVED

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (resolved) 0.35f else 0.6f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Severity stripe
            Box(
                Modifier
                    .size(width = 4.dp, height = 76.dp)
                    .background(if (resolved) palette.offline.copy(alpha = 0.4f) else color),
            )
            Row(
                Modifier.padding(Spacing.lg).weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        BevestIcons.forAlertType(alert.type),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        alert.type.name.replace('_', ' '),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Worker ${alert.workerId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        DateTimeUtils.relativeAge(alert.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    alert.status.name,
                    style = EyebrowStyle,
                    color = if (resolved) palette.normal else color,
                )
            }
        }
    }
}
