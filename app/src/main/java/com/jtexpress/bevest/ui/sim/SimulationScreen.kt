package com.jtexpress.bevest.ui.sim

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.simulation.Scenario
import com.jtexpress.bevest.simulation.SimWorker
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing

@Composable
fun SimulationScreen(
    user: User,
    onBack: () -> Unit,
    viewModel: SimulationViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val isSso = user.role == UserRole.SSO
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(user.uid) {
        viewModel.bind(
            contractorId = if (isSso) null else (user.contractorId ?: user.uid),
            siteId = if (isSso) user.siteId else null,
            canStream = isSso,
        )
    }
    BevestScaffold(title = "IoT simulation", subtitle = "Demo mode", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                if (isSso) {
                    "The vests aren't built yet. This streams realistic sensor data and raises the alerts " +
                        "and incidents the cloud engine would create — so you can see the app fully connected."
                } else {
                    "Populate this project with realistic incident history so the dashboard, charts and " +
                        "reports show what they'll look like once the vests are live."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.message?.let { MessageBar(it, onDismiss = viewModel::consumeMessage) }

            if (isSso) {
                StreamToggle(
                    streaming = state.streaming,
                    busy = state.busy,
                    workerCount = state.workers.size,
                    liveWarnings = state.liveWarnings,
                    liveEmergencies = state.liveEmergencies,
                    onToggle = viewModel::toggleStream,
                )

                SectionHeader("Workers")
                if (state.workers.isEmpty()) {
                    EmptyState(
                        title = "No workers on this site",
                        message = "Add a worker and pair a vest, then come back.",
                        icon = BevestIcons.NoWorkers,
                        modifier = Modifier.heightIn(min = 160.dp),
                    )
                } else {
                    state.workers.forEach { w ->
                        SimWorkerCard(
                            worker = w,
                            onScenario = { viewModel.setScenario(w.workerId, it) },
                            onEscalate = { viewModel.escalate(w.workerId) },
                        )
                    }
                }
            }

            SectionHeader("History")
            Text(
                "Adds ~4–8 past incidents per worker across the last 6 months. Safe to run more than once.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = viewModel::seedHistory,
                enabled = !state.busy && state.bound,
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(BevestIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Seed 6 months of history", Modifier.padding(start = Spacing.sm))
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = { confirmReset = true },
                enabled = !state.busy,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LocalStatusPalette.current.danger,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget),
            ) {
                Icon(BevestIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Reset — remove all simulated data", Modifier.padding(start = Spacing.sm))
            }

            if (state.log.isNotEmpty()) {
                SectionHeader("Activity")
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.log.forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (confirmReset) {
        ConfirmationDialog(
            title = "Reset simulation?",
            message = "Stops the live stream and permanently deletes every simulated alert and incident. " +
                "Real data is untouched.",
            confirmLabel = "Reset",
            onConfirm = { confirmReset = false; viewModel.reset() },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun StreamToggle(
    streaming: Boolean,
    busy: Boolean,
    workerCount: Int,
    liveWarnings: Int,
    liveEmergencies: Int,
    onToggle: () -> Unit,
) {
    val palette = LocalStatusPalette.current
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = if (streaming) palette.normal.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (streaming) "LIVE" else "OFFLINE",
                    style = EyebrowStyle,
                    color = if (streaming) palette.normal else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (streaming) "$workerCount vests transmitting" else "Stream is stopped",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (streaming && (liveWarnings > 0 || liveEmergencies > 0)) {
                    Text(
                        buildString {
                            if (liveEmergencies > 0) append("$liveEmergencies emergency ")
                            if (liveWarnings > 0) append("$liveWarnings elevated")
                        }.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.danger,
                    )
                }
            }
            Button(
                onClick = onToggle,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (streaming) palette.danger else palette.normal,
                ),
            ) {
                Icon(
                    if (streaming) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(if (streaming) "Stop" else "Go live", Modifier.padding(start = Spacing.xs))
            }
        }
    }
}

@Composable
private fun SimWorkerCard(
    worker: SimWorker,
    onScenario: (Scenario) -> Unit,
    onEscalate: () -> Unit,
) {
    val palette = LocalStatusPalette.current
    val phaseColor = when (worker.phase) {
        SimWorker.Phase.NORMAL -> palette.normal
        SimWorker.Phase.WARNING -> palette.warning
        SimWorker.Phase.DANGER -> palette.danger
        SimWorker.Phase.EMERGENCY -> palette.emergency
    }
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(worker.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "ID ${worker.workerId}" + if (worker.vestId.isNotBlank()) " · Vest ${worker.vestId}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .background(phaseColor.copy(alpha = 0.14f), RoundedCornerShape(Radius.sm))
                        .padding(horizontal = Spacing.sm, vertical = 4.dp),
                ) {
                    Text(worker.phase.name, style = EyebrowStyle, color = phaseColor)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Vital(BevestIcons.HeartRate, "${worker.heartRate}", "BPM")
                Vital(BevestIcons.Temperature, "%.1f".format(worker.temperature), "°C")
                Vital(BevestIcons.Battery, "${worker.battery}", "%")
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Scenario.entries.forEach { sc ->
                    FilterChip(
                        selected = worker.scenario == sc,
                        onClick = { onScenario(sc) },
                        label = { Text(sc.label, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(Radius.pill),
                    )
                }
            }

            if (worker.scenario != Scenario.HEALTHY && worker.phase != SimWorker.Phase.EMERGENCY) {
                OutlinedButton(
                    onClick = onEscalate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Escalate to emergency now", Modifier.padding(start = Spacing.xs))
                }
            }
        }
    }
}

@Composable
private fun Vital(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageBar(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
