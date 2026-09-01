package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.ui.common.QrScanner
import com.jtexpress.bevest.ui.vest.ConnectionChecks
import com.jtexpress.bevest.ui.vest.PairStep
import com.jtexpress.bevest.ui.vest.PairVestViewModel
import com.jtexpress.bevest.ui.vest.VestDetailViewModel
import com.jtexpress.bevest.ui.vest.VestFilter
import com.jtexpress.bevest.ui.vest.VestListViewModel
import com.jtexpress.bevest.utils.DateTimeUtils

@Composable
fun VestListScreen(
    onOpenVest: (String) -> Unit,
    onPairVest: () -> Unit,
    viewModel: VestListViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    BevestScaffold(
        title = "Vests",
        floatingActionButton = {
            FloatingActionButton(onClick = onPairVest) {
                Icon(BevestIcons.Scan, contentDescription = "Pair vest")
            }
        },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VestFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { viewModel.onFilter(f) },
                            label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                if (state.visible.isEmpty()) {
                    EmptyState("No vests", "No vests have this status right now.", icon = BevestIcons.Vests)
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                    ) {
                        items(state.visible, key = { it.vestId }) { vest ->
                            Card(Modifier.fillMaxWidth().clickable { onOpenVest(vest.vestId) }) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(vest.vestId, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${vest.status.name} · ${vest.assignedWorkerId ?: "unassigned"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "Battery ${vest.batteryPercent?.let { "$it%" } ?: "—"} · ${if (vest.online) "online" else "offline"}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VestDetailScreen(
    vestId: String,
    actorId: String,
    onBack: () -> Unit,
    viewModel: VestDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var confirmUnassign by remember { mutableStateOf(false) }

    BevestScaffold(title = state.vest?.vestId ?: "Vest", onBack = onBack) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.vest == null -> ErrorState("That vest no longer exists.", modifier = Modifier.padding(padding))
            else -> {
                val vest = state.vest
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Status: ${vest.status.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Assigned worker: ${vest.assignedWorkerId ?: "none"}")
                    Text("Battery: ${vest.batteryPercent?.let { "$it%" } ?: "—"}")
                    Text("Last seen: ${DateTimeUtils.relativeAge(vest.lastSeen)}")

                    if (!vest.assignedWorkerId.isNullOrBlank()) {
                        OutlinedButton(onClick = { confirmUnassign = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Unassign vest")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.setStatus(VestStatus.MAINTENANCE) }) { Text("Maintenance") }
                        OutlinedButton(onClick = { viewModel.setStatus(VestStatus.AVAILABLE) }) { Text("Available") }
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

                    Text("Assignment history", style = MaterialTheme.typography.titleMedium)
                    if (state.assignments.isEmpty()) {
                        Text("No assignments recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.assignments.forEach { a ->
                            Text(
                                "${a.workerId}: ${DateTimeUtils.formatDate(a.assignedAt)} → " +
                                    (a.unassignedAt?.let { DateTimeUtils.formatDate(it) } ?: "current"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmUnassign) {
        ConfirmationDialog(
            title = "Unassign this vest?",
            message = "The worker will no longer be monitored by this vest. Incident history is kept.",
            confirmLabel = "Unassign",
            onConfirm = { confirmUnassign = false; viewModel.unassign(actorId) },
            onDismiss = { confirmUnassign = false },
        )
    }
}

@Composable
fun PairVestScreen(
    user: User,
    onDone: () -> Unit,
    viewModel: PairVestViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.siteId) { viewModel.bind(user.siteId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    LaunchedEffect(state.step) { if (state.step == PairStep.DONE) onDone() }

    BevestScaffold(
        title = "Pair vest",
        onBack = if (state.step == PairStep.IDENTIFY) onDone else viewModel::back,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Step ${state.step.ordinal + 1} of 3", style = MaterialTheme.typography.labelLarge)
            when (state.step) {
                PairStep.IDENTIFY -> {
                    Text("Scan the vest QR code or enter its ID.", style = MaterialTheme.typography.titleMedium)
                    QrScanner(onResult = viewModel::onScanned)
                    OutlinedTextField(
                        value = state.vestIdInput,
                        onValueChange = viewModel::onVestIdInput,
                        label = { Text("Vest ID") },
                        isError = state.vestIdError != null,
                        supportingText = state.vestIdError?.let { { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::identifyVest,
                        enabled = !state.checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.checking) "Checking…" else "Continue") }
                }
                PairStep.SELECT_WORKER -> {
                    Text("Assign vest ${state.identifiedVestId} to:", style = MaterialTheme.typography.titleMedium)
                    if (state.unassignedWorkers.isEmpty()) {
                        Text("Every active worker on this site already has a vest.")
                    }
                    state.unassignedWorkers.forEach { w ->
                        Card(Modifier.fillMaxWidth().clickable { viewModel.selectWorker(w.workerId) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(w.fullName.ifBlank { w.workerId }, fontWeight = FontWeight.SemiBold)
                                Text("ID ${w.workerId}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                PairStep.CONNECTION_TEST -> {
                    Text("Connection test", style = MaterialTheme.typography.titleMedium)
                    if (state.testing) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 12.dp))
                            Text("Reading from the vest…")
                        }
                    } else {
                        ConnectionRow("Heart rate sensor", state.checks.heartRate)
                        ConnectionRow("Temperature sensor", state.checks.temperature)
                        ConnectionRow("Motion sensor", state.checks.motion)
                        ConnectionRow("GPS", state.checks.gps)
                        ConnectionRow("Device connection", state.checks.connection)
                        OutlinedButton(onClick = viewModel::runConnectionTest, modifier = Modifier.fillMaxWidth()) {
                            Text("Re-run test")
                        }
                        Button(
                            onClick = { viewModel.confirmPairing(user.uid) },
                            enabled = state.checks.allPass && !state.submitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.submitting) "Pairing…" else "Confirm pairing") }
                    }
                }
                PairStep.DONE -> Text("Paired.")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ConnectionRow(label: String, pass: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(if (pass) "OK" else "—", color = if (pass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

