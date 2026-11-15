package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.Shift
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.safety.MonitorHealth
import com.jtexpress.bevest.domain.safety.ShiftCheckRow
import com.jtexpress.bevest.domain.safety.ShiftProblem
import com.jtexpress.bevest.ui.common.BevestCard
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.common.ErrorNote
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatusBadge
import com.jtexpress.bevest.ui.common.SuccessNote
import com.jtexpress.bevest.ui.common.label
import com.jtexpress.bevest.ui.profile.rememberAlarmChecks
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.utils.DateTimeUtils

/**
 * Start-of-shift check: before work begins, is every worker wearing a vest that reports
 * and will last the day, and will this phone actually sound the alarm? Problems are
 * listed first, so the officer fixes them while everyone is still at the gate.
 */
@Composable
fun ShiftCheckScreen(
    user: User,
    onBack: () -> Unit,
    onOpenWorker: (String) -> Unit,
    onPairVest: () -> Unit,
    onOpenAlarmSetup: () -> Unit,
    viewModel: ShiftCheckViewModel = hiltViewModel(),
) {
    LaunchedEffect(user) { viewModel.start(user) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val health = viewModel.monitorHealth.collectAsStateWithLifecycle().value
    val alarm = rememberAlarmChecks()
    val palette = LocalStatusPalette.current
    var confirmStart by remember { mutableStateOf(false) }

    BevestScaffold(title = "Start-of-shift check", subtitle = user.siteId, onBack = onBack) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                ShiftSummary(state.readyCount, state.rows.size, state.shift)

                // ---- This phone ----
                SectionHeader("This phone", supporting = "It must be able to sound the alarm")
                CheckLine(
                    icon = if (health is MonitorHealth.Degraded) BevestIcons.NoSignal else BevestIcons.Signal,
                    title = "Live monitoring",
                    detail = when (health) {
                        MonitorHealth.Ok -> "Watching the site"
                        MonitorHealth.Idle -> "Starting…"
                        is MonitorHealth.Degraded -> "Interrupted, reconnecting (${health.reason})"
                    },
                    ok = health !is MonitorHealth.Degraded,
                )
                CheckLine(
                    icon = BevestIcons.Alerts,
                    title = "Emergency alarm",
                    detail = if (alarm.unready == 0) {
                        "Set up to ring over silent mode"
                    } else {
                        "${alarm.unready} setting${if (alarm.unready == 1) "" else "s"} would stop it ringing"
                    },
                    ok = alarm.unready == 0,
                    action = if (alarm.unready == 0) null else ("Fix" to onOpenAlarmSetup),
                )

                // ---- Workers ----
                SectionHeader(
                    "Workers",
                    supporting = if (state.notReady.isEmpty()) {
                        "Every vest is on and reporting"
                    } else {
                        "${state.notReady.size} to sort out before work starts"
                    },
                )
                if (state.rows.isEmpty()) {
                    Text(
                        "No active workers on this site yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.rows.forEach { row ->
                    WorkerCheckRow(row, onClick = { onOpenWorker(row.worker.workerId) })
                }
                if (state.rows.any { it.problem == ShiftProblem.NO_VEST }) {
                    SecondaryButton(text = "Pair a vest", onClick = onPairVest, icon = BevestIcons.Vests)
                }

                state.message?.let { SuccessNote(it) }
                state.saveError?.let { ErrorNote(it) }

                PrimaryButton(
                    text = when {
                        state.shift != null -> "Save this check"
                        state.notReady.isEmpty() -> "Start shift"
                        else -> "Start shift anyway"
                    },
                    onClick = {
                        if (state.shift == null && state.notReady.isNotEmpty()) confirmStart = true else viewModel.save()
                    },
                    loading = state.saving,
                    loadingText = "Saving…",
                )
                if (state.shift == null && state.notReady.isNotEmpty()) {
                    Text(
                        "Workers who aren't ready won't be monitored until their vest reports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.warning,
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    if (confirmStart) {
        val n = state.notReady.size
        ConfirmationDialog(
            title = "Start with $n not ready?",
            message = "$n worker${if (n == 1) " isn't" else "s aren't"} being monitored right now. " +
                "The shift record will list them for your contractor.",
            confirmLabel = "Start shift",
            onConfirm = { confirmStart = false; viewModel.save() },
            onDismiss = { confirmStart = false },
        )
    }
}

@Composable
private fun ShiftSummary(ready: Int, total: Int, shift: Shift?) {
    val palette = LocalStatusPalette.current
    val allReady = ready == total && total > 0
    val color = if (allReady) palette.normal else palette.warning
    BevestCard(accent = color) {
        Text(
            if (total == 0) "No workers" else "$ready of $total ready",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            when {
                shift != null -> "Shift started ${DateTimeUtils.timeOfDay(shift.startedAt)} by ${shift.startedByName}"
                allReady -> "Everyone is set. Start the shift to record it."
                else -> "Fix what you can, then start the shift."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CheckLine(
    icon: ImageVector,
    title: String,
    detail: String,
    ok: Boolean,
    action: Pair<String, () -> Unit>? = null,
) {
    val palette = LocalStatusPalette.current
    val color = if (ok) palette.normal else palette.danger
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(IconSize.medium))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
    }
}

@Composable
private fun WorkerCheckRow(row: ShiftCheckRow, onClick: () -> Unit) {
    val palette = LocalStatusPalette.current
    val color: Color = when (row.problem) {
        null -> palette.normal
        ShiftProblem.LOW_BATTERY -> palette.warning
        else -> palette.danger
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(BevestShapes.card)
            .clickable(onClick = onClick),
        shape = BevestShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (row.ready) MaterialTheme.colorScheme.outlineVariant else color.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.worker.fullName.ifBlank { row.worker.workerId },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        row.worker.workerId,
                        row.worker.assignedVestId?.takeIf { it.isNotBlank() }?.let { "Vest $it" },
                        row.reading?.batteryPercent?.let { "$it%" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadge(
                text = row.problem?.label() ?: "Ready",
                color = color,
                icon = when (row.problem) {
                    null -> BevestIcons.Signal
                    ShiftProblem.LOW_BATTERY -> BevestIcons.BatteryLow
                    ShiftProblem.NO_VEST -> BevestIcons.Vests
                    else -> BevestIcons.NoSignal
                },
            )
        }
    }
}
