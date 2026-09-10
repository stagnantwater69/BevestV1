package com.jtexpress.bevest.ui.sso

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.jtexpress.bevest.ui.common.SiteCount
import com.jtexpress.bevest.ui.common.SiteStatusPanel
import com.jtexpress.bevest.ui.common.StatusChip
import com.jtexpress.bevest.ui.common.WorkerAvatar
import com.jtexpress.bevest.ui.common.label
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.ComponentHeight
import com.jtexpress.bevest.ui.theme.Elevation
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * The officer's home screen.
 *
 * Ordered strictly by the hierarchy in plan section 23 — active emergency, then the
 * site's overall verdict, then anything currently demanding attention, then the roster.
 *
 * Two things were deliberately removed from the previous version. The 2×2 grid of stat
 * tiles is gone, folded into [SiteStatusPanel], which interprets those same numbers
 * instead of just displaying them. The "Live site map" and "Alert history" shortcut
 * cards are gone too: both are bottom-navigation tabs, so the cards were spending the
 * most valuable space on the screen duplicating navigation the user already has. The
 * space they held now shows the active alerts themselves, which are not reachable from
 * anywhere else on this screen.
 */
@Composable
fun SsoDashboardScreen(
    user: User,
    onOpenWorker: (String) -> Unit,
    onOpenAlert: (String) -> Unit,
    onOpenAlerts: () -> Unit,
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
                message = "Your account isn't linked to a project site yet. Ask your " +
                    "contractor to assign one so monitoring can begin.",
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
                // 1. An active emergency outranks everything, including the summary.
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

                // 2. The verdict. One glance should settle whether to act.
                item(key = "site-status") {
                    SiteStatusPanel(
                        status = state.worstStatus,
                        headline = state.headline,
                        supporting = state.supporting,
                        live = state.workers.any { !it.stale },
                        counts = listOf(
                            SiteCount(state.onSiteCount, "On site"),
                            SiteCount(state.warningCount, "Warning", palette.warning),
                            SiteCount(state.dangerCount, "Danger", palette.danger),
                            SiteCount(state.offlineCount, "Offline", palette.offline),
                        ),
                        modifier = Modifier.animateContentSize(),
                    )
                }

                // 3. What needs attention right now, if anything does. Hidden entirely
                //    when clear — an empty "Active alerts" heading is just noise.
                if (state.activeAlerts.isNotEmpty()) {
                    item(key = "alerts-header") {
                        SectionHeader(
                            title = "Needs attention",
                            supporting = "${state.activeAlerts.size} active",
                        ) {
                            TextButton(onClick = onOpenAlerts) { Text("See all") }
                        }
                    }
                    items(
                        state.activeAlerts.take(3),
                        key = { "alert-${it.alertId}" },
                    ) { alert ->
                        val name = state.workers
                            .firstOrNull { it.worker.workerId == alert.workerId }
                            ?.worker?.fullName
                            ?.takeIf { it.isNotBlank() }
                            ?: "Worker ${alert.workerId}"
                        AlertRow(
                            alert = alert,
                            workerName = name,
                            onClick = { onOpenAlert(alert.alertId) },
                        )
                    }
                }

                // 4. The roster, worst status first.
                item(key = "roster-header") {
                    SectionHeader(
                        title = "Workers",
                        supporting = "Sorted by status",
                    ) {
                        Text(
                            "${state.workers.size}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.workers.isEmpty()) {
                    item(key = "roster-empty") {
                        EmptyState(
                            title = "No workers yet",
                            message = "Register a worker and pair them with a vest to " +
                                "start monitoring this site.",
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
    val palette = LocalStatusPalette.current
    // A row for someone in trouble borders in their status color, so the list can be
    // scanned by edge alone before any name is read.
    val urgent = live.status == SafetyStatus.DANGER || live.status == SafetyStatus.EMERGENCY

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ComponentHeight.listRow)
            .clip(BevestShapes.card)
            .clickable(onClick = onClick),
        shape = BevestShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (urgent) {
                palette.forStatus(live.status).copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // The vitals are measured first at their natural width and the ID
                    // takes what is left, ellipsising if it must. Without the weight a
                    // long worker ID starves the vitals instead: they get squeezed to a
                    // few pixels, wrap one character per line, and blow the row's height
                    // out while showing nothing.
                    Text(
                        live.worker.workerId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
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
private fun VitalInline(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.inline),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // A vital is short and must never wrap; if space runs out it should be
            // clipped as a unit rather than stacked one character per line.
            maxLines = 1,
            softWrap = false,
        )
    }
}
