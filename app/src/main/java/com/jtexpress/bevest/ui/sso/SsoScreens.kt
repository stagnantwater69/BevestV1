package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.utils.DateTimeUtils

@Composable
fun AlertDetailScreen(
    alertId: String,
    actorId: String,
    onBack: () -> Unit,
    onOpenWorker: (String) -> Unit,
    viewModel: AlertDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var notes by remember { mutableStateOf("") }
    var confirmResolve by remember { mutableStateOf(false) }

    BevestScaffold(title = "Alert", onBack = onBack) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.alert == null -> ErrorState("That alert no longer exists.", modifier = Modifier.padding(padding))
            else -> {
                val alert = state.alert
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(alert.type.name.replace('_', ' '), style = MaterialTheme.typography.headlineSmall)
                    Text("Severity: ${alert.severity.name}")
                    Text("Status: ${alert.status.name}")
                    Text("Worker: ${alert.workerId}")
                    Text("Vest: ${alert.vestId}")
                    Text("Raised: ${DateTimeUtils.formatDateTime(alert.createdAt)}")
                    alert.acknowledgedAt?.let { Text("Acknowledged: ${DateTimeUtils.formatDateTime(it)}") }
                    alert.resolvedAt?.let { Text("Resolved: ${DateTimeUtils.formatDateTime(it)}") }
                    if (alert.message.isNotBlank()) Text(alert.message)

                    OutlinedButton(onClick = { onOpenWorker(alert.workerId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open worker")
                    }

                    if (alert.status == AlertStatus.ACTIVE) {
                        Button(
                            onClick = { viewModel.acknowledge(actorId) },
                            enabled = !state.working,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Acknowledge") }
                    }
                    if (alert.status != AlertStatus.RESOLVED) {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Resolution notes") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { confirmResolve = true },
                            enabled = !state.working,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Mark resolved") }
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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

@Composable
fun IncidentDetailScreen(
    incidentId: String,
    actorId: String,
    onBack: () -> Unit,
    viewModel: IncidentDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var outcome by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    BevestScaffold(title = "Incident", onBack = onBack) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.incident == null -> ErrorState("That incident no longer exists.", modifier = Modifier.padding(padding))
            else -> {
                val incident = state.incident
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(incident.type.name.replace('_', ' '), style = MaterialTheme.typography.headlineSmall)
                    Text("Severity: ${incident.severity.name}")
                    Text("Worker: ${incident.workerId}")
                    Text("Vest: ${incident.vestId}")
                    Text("When: ${DateTimeUtils.formatDateTime(incident.createdAt)}")
                    incident.heartRate?.let { Text("Heart rate at incident: $it BPM") }
                    incident.temperature?.let { Text("Temperature at incident: ${"%.1f".format(it)} °C") }
                    if (incident.latitude != null && incident.longitude != null) {
                        Text("Location: ${"%.5f".format(incident.latitude)}, ${"%.5f".format(incident.longitude)}")
                    }
                    incident.outcome?.let { Text("Outcome: $it") }
                    incident.resolutionNotes?.let { Text("Notes: $it") }
                    incident.resolvedBy?.let { Text("Resolved by: $it") }

                    if (incident.outcome == null) {
                        OutlinedTextField(
                            value = outcome,
                            onValueChange = { outcome = it },
                            label = { Text("Outcome (e.g. worker safe, false alarm)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Resolution notes") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { confirm = true },
                            enabled = !state.working && outcome.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Resolve incident") }
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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
