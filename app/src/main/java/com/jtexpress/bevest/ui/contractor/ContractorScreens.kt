package com.jtexpress.bevest.ui.contractor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.navigation.Routes
import com.jtexpress.bevest.ui.common.BarChart
import com.jtexpress.bevest.ui.common.BarDatum
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.DonutChart
import com.jtexpress.bevest.ui.common.DonutSlice
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.ProfileAction
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.LoadingState
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.DashboardSkeleton
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.sso.WorkerDirectoryScreen
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.utils.DateTimeUtils
import com.jtexpress.bevest.utils.PdfExporter

private fun contractorId(user: User) = user.contractorId ?: user.uid

@Composable
fun ContractorDashboardScreen(
    user: User,
    navigate: (String) -> Unit,
    viewModel: ContractorDashboardViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.uid) { viewModel.start(contractorId(user)) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current

    BevestScaffold(
        title = "Safety overview",
        actions = {
            if (com.jtexpress.bevest.BuildConfig.DEBUG) {
                androidx.compose.material3.IconButton(onClick = { navigate(Routes.SIMULATION) }) {
                    androidx.compose.material3.Icon(
                        BevestIcons.Simulator,
                        contentDescription = "IoT simulation",
                    )
                }
            }
            ProfileAction(user, onClick = { navigate(Routes.PROFILE) })
        },
    ) { padding ->
        when {
            state.loading -> DashboardSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.jtexpress.bevest.ui.common.StatTile(
                        BevestIcons.Workers, state.activeWorkers.toString(), "Active workers", Modifier.weight(1f),
                    )
                    com.jtexpress.bevest.ui.common.StatTile(
                        BevestIcons.Alerts, state.todaysAlerts.toString(), "Today's alerts", Modifier.weight(1f),
                        accent = if (state.todaysAlerts > 0) palette.warning else null,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.jtexpress.bevest.ui.common.StatTile(
                        BevestIcons.History, state.monthlyIncidents.toString(), "Incidents (30d)", Modifier.weight(1f),
                        accent = if (state.monthlyIncidents > 0) palette.danger else null,
                    )
                    com.jtexpress.bevest.ui.common.StatTile(
                        BevestIcons.Vests, "${state.safetyScore}%", "Safety score", Modifier.weight(1f),
                        accent = if (state.safetyScore >= 90) palette.normal else palette.warning,
                    )
                }

                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            "WORKER STATUS",
                            style = com.jtexpress.bevest.ui.theme.EyebrowStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DonutChart(
                            slices = listOf(
                                DonutSlice("Normal", state.normal.toFloat(), palette.normal),
                                DonutSlice("Warning", state.warning.toFloat(), palette.warning),
                                DonutSlice("Danger", state.danger.toFloat(), palette.danger),
                            ),
                            centerValue = (state.normal + state.warning + state.danger).toString(),
                            centerLabel = "workers",
                        )
                    }
                }

                if (state.trends.isNotEmpty()) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                "MONTHLY INCIDENTS",
                                style = com.jtexpress.bevest.ui.theme.EyebrowStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            BarChart(
                                data = state.trends.map {
                                    BarDatum(
                                        DateTimeUtils.monthLabel(it.monthKey).take(3),
                                        it.totalIncidents.toFloat(),
                                    )
                                },
                            )
                        }
                    }
                }

                Text("Recent incidents", style = MaterialTheme.typography.titleMedium)
                if (state.recentIncidents.isEmpty()) {
                    Text("No incidents recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.recentIncidents.forEach { incident ->
                        Text(
                            "${DateTimeUtils.formatDate(incident.createdAt)} · ${incident.workerId} · ${incident.type.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedButton(onClick = { navigate(Routes.CONTRACTOR_SSOS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage Site Safety Officers")
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ContractorWorkersScreen(user: User, onOpenWorker: (String) -> Unit) {
    WorkerDirectoryScreen(user = user, onOpenWorker = onOpenWorker, onAddWorker = {})
}

@Composable
fun ContractorSsosScreen(
    user: User,
    onAdd: () -> Unit,
    viewModel: SsoListViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.uid) { viewModel.start(contractorId(user)) }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    BevestScaffold(
        title = "Site Safety Officers",
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(BevestIcons.Add, contentDescription = "Add SSO") }
        },
    ) { padding ->
        when {
            state.loading -> DashboardSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                if (state.visible.isEmpty()) {
                    EmptyState("No officers", "Add a Site Safety Officer to get started.", icon = BevestIcons.Officer)
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.visible, key = { it.uid }) { sso ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(sso.fullName.ifBlank { sso.email }, fontWeight = FontWeight.SemiBold)
                                    Text(sso.email, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Site: ${sso.siteId ?: "unassigned"} · ${if (sso.active) "active" else "disabled"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (sso.active) {
                                        OutlinedButton(onClick = { viewModel.disable(sso.uid) }) { Text("Disable") }
                                    }
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
fun AddSsoScreen(
    contractor: User,
    onDone: () -> Unit,
    viewModel: AddSsoViewModel = hiltViewModel(),
) {
    LaunchedEffect(contractor.uid) { viewModel.bind(contractorId(contractor)) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(state.created) { if (state.created) onDone() }

    BevestScaffold(title = "Add SSO", onBack = onDone) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(state.firstName, { viewModel.update("firstName", it) }, "First name", state.errors["firstName"])
            FormField(state.lastName, { viewModel.update("lastName", it) }, "Last name", state.errors["lastName"])
            FormField(state.email, { viewModel.update("email", it) }, "Email", state.errors["email"], keyboardType = KeyboardType.Email)
            FormField(state.phone, { viewModel.update("phone", it) }, "Phone", state.errors["phone"], keyboardType = KeyboardType.Phone)
            FormField(state.siteId, { viewModel.update("siteId", it) }, "Assigned site ID (optional)", state.errors["siteId"])
            FormField(state.tempPassword, { viewModel.update("tempPassword", it) }, "Temporary password", state.errors["tempPassword"], isPassword = true)
            state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::submit, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.submitting) "Creating…" else "Create account")
            }
            Text(
                "The officer signs in with this email and temporary password, then changes it from their profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ContractorReportsScreen(
    user: User,
    onOpenReport: (String) -> Unit,
    onOpenIncidents: () -> Unit,
    onOpenSsos: () -> Unit,
    viewModel: ContractorReportsViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.uid) { viewModel.start(contractorId(user)) }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    BevestScaffold(title = "Reports") { padding ->
        when {
            state.loading -> DashboardSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedButton(onClick = onOpenIncidents, modifier = Modifier.fillMaxWidth()) {
                        Text("Incident history")
                    }
                }
                items(state.reports, key = { it.monthKey }) { report ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenReport(report.monthKey) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(DateTimeUtils.monthLabel(report.monthKey), fontWeight = FontWeight.SemiBold)
                            Text(
                                "${report.totalIncidents} incidents · ${report.safetyPercentage}% safety",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyReportDetailScreen(
    contractorId: String,
    monthKey: String,
    onBack: () -> Unit,
    viewModel: MonthlyReportDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(contractorId) { viewModel.start(contractorId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    BevestScaffold(title = "Monthly report", onBack = onBack) { padding ->
        when {
            state.loading -> DashboardSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.report == null -> EmptyState("No data", "No report for this month.", Modifier.padding(padding), icon = BevestIcons.Reports)
            else -> {
                val report = state.report
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(DateTimeUtils.monthLabel(report.monthKey), style = MaterialTheme.typography.headlineSmall)
                    Text("Total incidents: ${report.totalIncidents}")
                    Text("Safety percentage: ${report.safetyPercentage}%")
                    Text("Active workers: ${report.activeWorkers}")
                    Text("Warnings: ${report.warningCount} · Danger: ${report.dangerCount} · Emergency: ${report.emergencyCount}")

                    Button(
                        onClick = {
                            val file = PdfExporter.monthlyReport(context, report, state.incidents)
                            PdfExporter.share(context, file)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Export & share PDF") }

                    Text("Incidents this month", style = MaterialTheme.typography.titleMedium)
                    state.incidents.forEach { incident ->
                        Text(
                            "${DateTimeUtils.formatDate(incident.createdAt)} · ${incident.workerId} · ${incident.type.name} · ${incident.severity.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContractorIncidentsScreen(
    contractorId: String,
    onOpenIncident: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ContractorIncidentsViewModel = hiltViewModel(),
) {
    LaunchedEffect(contractorId) { viewModel.start(contractorId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    BevestScaffold(title = "Incident history", onBack = onBack) { padding ->
        when {
            state.loading -> DashboardSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(state.severityFilter == null, { viewModel.onFilter(null) }, { Text("All") })
                    AlertSeverity.entries.forEach { sev ->
                        FilterChip(
                            state.severityFilter == sev,
                            { viewModel.onFilter(sev) },
                            { Text(sev.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                if (state.visible.isEmpty()) {
                    EmptyState("No incidents", "Nothing matches this filter.", icon = BevestIcons.History)
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.visible, key = { it.incidentId }) { incident ->
                            Card(Modifier.fillMaxWidth().clickable { onOpenIncident(incident.incidentId) }) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(incident.type.name.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                                    Text("Worker ${incident.workerId} · ${incident.severity.name}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        DateTimeUtils.formatDateTime(incident.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        incident.outcome?.let { "Resolved: $it" } ?: "Open",
                                        style = MaterialTheme.typography.labelMedium,
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
