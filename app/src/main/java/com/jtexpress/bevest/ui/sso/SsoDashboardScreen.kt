package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.IconButton
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
import com.jtexpress.bevest.BuildConfig
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.DashboardSkeleton
import com.jtexpress.bevest.ui.common.EmergencyBanner
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.LastUpdatedLabel
import com.jtexpress.bevest.ui.common.ProfileAction
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatTile
import com.jtexpress.bevest.ui.common.StatusChip
import com.jtexpress.bevest.ui.common.WorkerAvatar
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.Elevation
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing

@Composable
fun SsoDashboardScreen(
    user: User,
    onOpenWorker: (String) -> Unit,
    onOpenAlert: (String) -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSimulator: () -> Unit,
    viewModel: SsoDashboardViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.siteId) { viewModel.start(user.siteId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current

    BevestScaffold(
        title = "Safety dashboard",
        subtitle = user.siteId,
        actions = {
            if (BuildConfig.DEBUG) {
                IconButton(onClick = onOpenSimulator) {
                    Icon(BevestIcons.Simulator, contentDescription = "Reading simulator")
                }
            }
            ProfileAction(user, onClick = onOpenProfile)
        },
    ) { padding ->
        when {
            state.loading -> DashboardSkeleton(Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            state.noSite -> EmptyState(
                title = "No site assigned",
                message = "Your account isn't linked to a project site yet. Ask your contractor to assign one.",
                icon = BevestIcons.Location,
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.md,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // 1. Active emergency always sits at the very top.
                state.topEmergencyAlert?.let { alert ->
                    item(key = "emergency") {
                        val name = state.workers
                            .firstOrNull { it.worker.workerId == alert.workerId }
                            ?.worker?.fullName
                            ?.takeIf { it.isNotBlank() }
                            ?: alert.workerId
                        EmergencyBanner(
                            workerName = name,
                            reason = alert.message,
                            onView = { onOpenAlert(alert.alertId) },
                        )
                    }
                }

                // 2. Summary — danger and warning get semantic accents so they pop.
                item(key = "stats") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatTile(
                            icon = BevestIcons.Workers,
                            value = state.onSiteCount.toString(),
                            label = "On site",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            icon = BevestIcons.forStatus(SafetyStatus.WARNING),
                            value = state.warningCount.toString(),
                            label = "Warnings",
                            accent = if (state.warningCount > 0) palette.warning else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item(key = "stats2") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatTile(
                            icon = BevestIcons.forStatus(SafetyStatus.DANGER),
                            value = state.dangerCount.toString(),
                            label = "Danger",
                            accent = if (state.dangerCount > 0) palette.danger else null,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            icon = BevestIcons.Alerts,
                            value = state.activeAlerts.size.toString(),
                            label = "Active alerts",
                            accent = if (state.activeAlerts.isNotEmpty()) palette.warning else null,
                            onClick = onOpenAlerts,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 3. Quick entries into the live views.
                item(key = "shortcuts") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        ShortcutCard(
                            icon = BevestIcons.Map,
                            label = "Live site map",
                            onClick = onOpenMap,
                            modifier = Modifier.weight(1f),
                        )
                        ShortcutCard(
                            icon = BevestIcons.Alerts,
                            label = "Alert history",
                            onClick = onOpenAlerts,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 4. The roster, worst status first.
                item(key = "roster-header") {
                    SectionHeader("Workers") {
                        Text(
                            "${state.workers.size} total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.workers.isEmpty()) {
                    item(key = "roster-empty") {
                        EmptyState(
                            title = "No workers yet",
                            message = "Register a worker to start monitoring this site.",
                            icon = BevestIcons.NoWorkers,
                            modifier = Modifier.heightIn(min = 220.dp),
                        )
                    }
                } else {
                    items(state.workers, key = { it.worker.workerId }) { live ->
                        WorkerLiveRow(live, onClick = { onOpenWorker(live.worker.workerId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A worker in a list: avatar ringed with their status color, name, ID, live vitals,
 * freshness, and a status chip. Recognition first, detail second.
 */
@Composable
fun WorkerLiveRow(
    live: WorkerLive,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = Elevation.card,
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            WorkerAvatar(
                name = live.worker.fullName.ifBlank { live.worker.workerId },
                photoUrl = live.worker.photoUrl,
                status = live.status,
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    live.worker.fullName.ifBlank { live.worker.workerId },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        live.worker.workerId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    live.reading?.heartRate?.let { hr ->
                        VitalInline(BevestIcons.HeartRate, "$hr")
                    }
                    live.reading?.temperature?.let { t ->
                        VitalInline(BevestIcons.Temperature, "%.1f°".format(t))
                    }
                }
                LastUpdatedLabel(live.lastUpdate, live.stale)
            }
            StatusChip(live.status, compact = true)
        }
    }
}

@Composable
private fun VitalInline(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
