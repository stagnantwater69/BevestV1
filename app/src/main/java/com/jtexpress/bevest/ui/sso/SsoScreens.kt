package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.DangerButton
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.common.DetailRow
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.FormSection
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatusBadge
import com.jtexpress.bevest.ui.common.SuccessNote
import com.jtexpress.bevest.ui.common.detail
import com.jtexpress.bevest.ui.common.label
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.Elevation
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.utils.DateTimeUtils

/**
 * Alert detail — the screen someone opens from a push notification when a worker is in
 * trouble, so it is designed to be understood in a few seconds while walking.
 *
 * The previous version was a stack of `Text("Severity: DANGER")` lines: every fact given
 * identical weight, machine constants for values, and the actions buried underneath.
 * This version leads with a headline that states what happened in words, keeps the
 * timeline of the response in one place, and puts the actions where a thumb is.
 */
@Composable
fun AlertDetailScreen(
    alertId: String,
    actorId: String,
    onBack: () -> Unit,
    onOpenWorker: (String) -> Unit,
    viewModel: AlertDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current
    var notes by remember { mutableStateOf("") }
    var confirmResolve by remember { mutableStateOf(false) }

    BevestScaffold(title = "Alert", onBack = onBack) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.alert == null -> ErrorState(
                "That alert no longer exists. It may have been resolved and archived.",
                modifier = Modifier.padding(padding),
            )
            else -> {
                val alert = state.alert
                val severityColor = when (alert.severity) {
                    AlertSeverity.WARNING -> palette.warning
                    AlertSeverity.DANGER -> palette.danger
                    AlertSeverity.EMERGENCY -> palette.emergency
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    AlertHeadline(alert, severityColor)

                    // ---- What we know ----
                    SectionHeader("Details")
                    Column {
                        DetailRow(
                            label = "Worker",
                            value = alert.workerId,
                            icon = BevestIcons.Workers,
                        )
                        DetailRow(
                            label = "Vest",
                            value = alert.vestId.ifBlank { "Not recorded" },
                            icon = BevestIcons.Vests,
                        )
                        if (alert.message.isNotBlank()) {
                            DetailRow(
                                label = "Message from the vest",
                                value = alert.message,
                                icon = BevestIcons.Signal,
                            )
                        }
                    }

                    // ---- Response timeline ----
                    SectionHeader(
                        title = "Response",
                        supporting = "What has happened since the alert was raised",
                    )
                    ResponseTimeline(alert)

                    // ---- Actions ----
                    SecondaryButton(
                        text = "Open worker",
                        onClick = { onOpenWorker(alert.workerId) },
                        icon = BevestIcons.Workers,
                    )

                    if (alert.status == AlertStatus.ACTIVE) {
                        PrimaryButton(
                            text = "Acknowledge",
                            onClick = { viewModel.acknowledge(actorId) },
                            icon = BevestIcons.forStatus(com.jtexpress.bevest.domain.model.SafetyStatus.NORMAL),
                            loading = state.working,
                            loadingText = "Acknowledging…",
                        )
                    }

                    if (alert.status != AlertStatus.RESOLVED) {
                        FormSection(title = "Close this alert") {
                            FormField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = "Resolution notes",
                                helper = "What did you find, and what did you do?",
                                singleLine = false,
                                imeAction = ImeAction.Done,
                            )
                            DangerButton(
                                text = "Mark resolved",
                                onClick = { confirmResolve = true },
                                loading = state.working,
                            )
                        }
                    }

                    state.message?.let { SuccessNote(it) }
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
        }
    }

    if (confirmResolve) {
        ConfirmationDialog(
            title = "Resolve this alert?",
            message = "The alert is archived, not deleted. Make sure the worker is safe first.",
            confirmLabel = "Resolve",
            onConfirm = { confirmResolve = false; viewModel.resolve(actorId, notes) },
            onDismiss = { confirmResolve = false },
        )
    }
}

/**
 * The headline: what happened, to what degree, and how long ago — the three things
 * needed to decide whether to run.
 */
@Composable
private fun AlertHeadline(alert: Alert, severityColor: androidx.compose.ui.graphics.Color) {
    val palette = LocalStatusPalette.current
    val resolved = alert.status == AlertStatus.RESOLVED

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BevestShapes.status,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, severityColor.copy(alpha = 0.35f)),
        shadowElevation = Elevation.card,
    ) {
        Column {
            Row(
                Modifier.padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(BevestShapes.inner)
                        .background(severityColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        BevestIcons.forAlertType(alert.type),
                        contentDescription = null,
                        tint = severityColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        alert.type.label(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        alert.type.detail(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ReflectiveBand(thickness = 2.dp, emphasis = 0.7f)
            Row(
                Modifier.padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(
                    text = alert.severity.label(),
                    color = severityColor,
                    icon = BevestIcons.forAlertType(alert.type),
                )
                StatusBadge(
                    text = alert.status.label(),
                    color = if (resolved) palette.normal else severityColor,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    DateTimeUtils.relativeAge(alert.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The alert's life so far, as a vertical timeline.
 *
 * A timeline rather than three separate timestamp rows because the *gaps* are the
 * interesting part — how long the alert sat unacknowledged is exactly the thing a safety
 * review will ask about, and a list of dates buries it.
 */
@Composable
private fun ResponseTimeline(alert: Alert) {
    val palette = LocalStatusPalette.current
    val steps = buildList {
        add(Triple("Raised", alert.createdAt, palette.danger))
        add(Triple("Acknowledged", alert.acknowledgedAt, palette.warning))
        add(Triple("Resolved", alert.resolvedAt, palette.normal))
    }

    Column(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, (label, at, color) ->
            val done = at != null && at > 0
            Row(Modifier.fillMaxWidth()) {
                // Rail: a filled node for a step that happened, hollow for one pending.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(24.dp),
                ) {
                    Box(
                        Modifier
                            .size(11.dp)
                            .clip(BevestShapes.round)
                            .background(
                                if (done) color else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(34.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = Spacing.sm, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        label.uppercase(),
                        style = EyebrowStyle,
                        color = if (done) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        if (done) DateTimeUtils.formatDateTime(at) else "Not yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Incident detail — the permanent record written after an alert is closed.
 *
 * Unlike an alert, this is read long after the fact, usually during a safety review. It
 * therefore leads with the readings captured at the moment of the incident, which are
 * the evidence, and treats the outcome as the thing being recorded rather than an
 * afterthought at the bottom of a form.
 */
@Composable
fun IncidentDetailScreen(
    incidentId: String,
    actorId: String,
    onBack: () -> Unit,
    viewModel: IncidentDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current
    var outcome by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    BevestScaffold(title = "Incident", onBack = onBack) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.incident == null -> ErrorState(
                "That incident no longer exists.",
                modifier = Modifier.padding(padding),
            )
            else -> {
                val incident = state.incident
                val severityColor = when (incident.severity) {
                    AlertSeverity.WARNING -> palette.warning
                    AlertSeverity.DANGER -> palette.danger
                    AlertSeverity.EMERGENCY -> palette.emergency
                }
                val open = incident.outcome == null

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    // ---- Headline ----
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = BevestShapes.status,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            severityColor.copy(alpha = 0.35f),
                        ),
                        shadowElevation = Elevation.card,
                    ) {
                        Row(
                            Modifier.padding(Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(BevestShapes.inner)
                                    .background(severityColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    BevestIcons.forAlertType(incident.type),
                                    contentDescription = null,
                                    tint = severityColor,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    incident.type.label(),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    DateTimeUtils.formatDateTime(incident.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusBadge(
                                text = if (open) "Open" else "Closed",
                                color = if (open) palette.warning else palette.normal,
                            )
                        }
                    }

                    // ---- Readings at the moment it happened ----
                    SectionHeader(
                        title = "Readings at the time",
                        supporting = "Captured by the vest when the incident was recorded",
                    )
                    Column {
                        DetailRow(
                            label = "Heart rate",
                            value = incident.heartRate?.let { "$it BPM" } ?: "Not recorded",
                            icon = BevestIcons.HeartRate,
                        )
                        DetailRow(
                            label = "Body temperature",
                            value = incident.temperature?.let { "%.1f °C".format(it) }
                                ?: "Not recorded",
                            icon = BevestIcons.Temperature,
                        )
                        DetailRow(
                            label = "Location",
                            value = if (incident.latitude != null && incident.longitude != null) {
                                "%.5f, %.5f".format(incident.latitude, incident.longitude)
                            } else {
                                "No GPS fix at the time"
                            },
                            icon = BevestIcons.Location,
                        )
                        DetailRow(
                            label = "Worker",
                            value = incident.workerId,
                            icon = BevestIcons.Workers,
                        )
                        DetailRow(
                            label = "Vest",
                            value = incident.vestId.ifBlank { "Not recorded" },
                            icon = BevestIcons.Vests,
                        )
                    }

                    // ---- Outcome ----
                    SectionHeader("Outcome")
                    if (open) {
                        FormSection(title = "Record what happened") {
                            FormField(
                                value = outcome,
                                onValueChange = { outcome = it },
                                label = "Outcome",
                                helper = "For example: worker safe, false alarm, taken to first aid",
                                required = true,
                            )
                            FormField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = "Resolution notes",
                                singleLine = false,
                                imeAction = ImeAction.Done,
                            )
                            PrimaryButton(
                                text = "Resolve incident",
                                onClick = { confirm = true },
                                enabled = outcome.isNotBlank(),
                                loading = state.working,
                                loadingText = "Saving…",
                            )
                        }
                    } else {
                        Column {
                            DetailRow(
                                label = "Outcome",
                                value = incident.outcome.orEmpty(),
                                icon = BevestIcons.forStatus(
                                    com.jtexpress.bevest.domain.model.SafetyStatus.NORMAL,
                                ),
                            )
                            incident.resolutionNotes?.takeIf { it.isNotBlank() }?.let {
                                DetailRow(label = "Notes", value = it, icon = BevestIcons.Reports)
                            }
                            incident.resolvedBy?.takeIf { it.isNotBlank() }?.let {
                                DetailRow(label = "Resolved by", value = it, icon = BevestIcons.Officer)
                            }
                        }
                    }

                    state.message?.let { SuccessNote(it) }
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
        }
    }

    if (confirm) {
        ConfirmationDialog(
            title = "Resolve this incident?",
            message = "Incident history is permanent. This records the outcome and notes.",
            confirmLabel = "Resolve",
            onConfirm = { confirm = false; viewModel.resolve(actorId, outcome, notes) },
            onDismiss = { confirm = false },
        )
    }
}
