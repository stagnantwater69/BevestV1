package com.jtexpress.bevest.ui.admin

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.navigation.Routes
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.ProfileAction
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.common.DashboardSkeleton
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.StatTile
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.utils.DateTimeUtils
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect

@Composable
fun AdminDashboardScreen(
    user: User,
    navigate: (String) -> Unit,
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = com.jtexpress.bevest.ui.theme.LocalStatusPalette.current
    BevestScaffold(
        title = "Administration",
        subtitle = "System overview",
        actions = { ProfileAction(user, onClick = { navigate(Routes.PROFILE) }) },
    ) { padding ->
        if (state.loading) {
            DashboardSkeleton(Modifier.padding(padding))
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(BevestIcons.Contractors, state.contractors.toString(), "Contractors", Modifier.weight(1f))
                    StatTile(BevestIcons.Officer, state.ssos.toString(), "Officers", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(BevestIcons.Workers, state.workers.toString(), "Workers", Modifier.weight(1f))
                    StatTile(BevestIcons.Vests, state.vests.toString(), "Vests", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        BevestIcons.Signal, state.activeVests.toString(), "Active vests",
                        Modifier.weight(1f), accent = palette.normal,
                    )
                    StatTile(
                        BevestIcons.NoSignal, state.offlineVests.toString(), "Offline vests",
                        Modifier.weight(1f),
                        accent = if (state.offlineVests > 0) palette.offline else null,
                    )
                }
                if (state.maintenanceMode) {
                    Text("Maintenance mode is ON", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { navigate(Routes.ADMIN_CONTRACTORS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage contractors")
                }
                OutlinedButton(onClick = { navigate(Routes.ADMIN_SYSTEM) }, modifier = Modifier.fillMaxWidth()) {
                    Text("System & devices")
                }
                OutlinedButton(onClick = { navigate(Routes.ADMIN_ACTIVITY) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Activity log")
                }
                Text("Recent activity", style = MaterialTheme.typography.titleMedium)
                state.recentActivity.take(5).forEach {
                    Text(
                        "${DateTimeUtils.relativeAge(it.createdAt)} · ${it.message}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AdminContractorsScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: AdminContractorsViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    BevestScaffold(
        title = "Contractors",
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(BevestIcons.Add, contentDescription = "Add contractor") }
        },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.contractors.isEmpty() -> EmptyState("No contractors", "Add a contractor to get started.", Modifier.padding(padding), icon = BevestIcons.Contractors)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.contractors, key = { it.uid }) { c ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(c.uid) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(c.fullName.ifBlank { c.email }, fontWeight = FontWeight.SemiBold)
                            Text(c.email, style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (c.active) "Active" else "Disabled",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (c.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddContractorScreen(
    actorId: String,
    onDone: () -> Unit,
    viewModel: AddContractorViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(state.created) { if (state.created) onDone() }
    BevestScaffold(title = "Add contractor", onBack = onDone) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(state.firstName, { viewModel.update("firstName", it) }, "First name", state.errors["firstName"])
            FormField(state.lastName, { viewModel.update("lastName", it) }, "Last name", state.errors["lastName"])
            FormField(state.email, { viewModel.update("email", it) }, "Email", state.errors["email"], keyboardType = KeyboardType.Email)
            FormField(state.phone, { viewModel.update("phone", it) }, "Phone", state.errors["phone"], keyboardType = KeyboardType.Phone)
            FormField(state.company, { viewModel.update("company", it) }, "Company / project", state.errors["company"])
            FormField(state.tempPassword, { viewModel.update("tempPassword", it) }, "Temporary password", state.errors["tempPassword"], isPassword = true)
            state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { viewModel.submit(actorId) },
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.submitting) "Creating…" else "Create contractor") }
        }
    }
}

@Composable
fun AdminContractorDetailScreen(
    contractorId: String,
    actorId: String,
    onBack: () -> Unit,
    viewModel: AdminContractorDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var confirm by remember { mutableStateOf(false) }

    BevestScaffold(title = "Contractor", onBack = onBack) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.contractor == null -> ErrorState("That contractor no longer exists.", modifier = Modifier.padding(padding))
            else -> {
                val c = state.contractor
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(c.fullName.ifBlank { c.email }, style = MaterialTheme.typography.headlineSmall)
                    Text(c.email)
                    Text(c.phone.ifBlank { "No phone" })
                    Text("Status: ${if (c.active) "Active" else "Disabled"}")
                    if (c.active) {
                        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Disable contractor")
                        }
                    } else {
                        Button(onClick = { viewModel.setActive(true, actorId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Re-enable contractor")
                        }
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }

    if (confirm) {
        ConfirmationDialog(
            title = "Disable this contractor?",
            message = "They and their SSOs lose access on next app open. Records are kept.",
            confirmLabel = "Disable",
            onConfirm = { confirm = false; viewModel.setActive(false, actorId) },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun AdminSystemScreen(
    actorId: String,
    onOpenActivity: () -> Unit,
    viewModel: AdminSystemViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    BevestScaffold(title = "System & devices") { padding ->
        if (state.loading) {
            DetailSkeleton(Modifier.padding(padding))
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Vest fleet", style = MaterialTheme.typography.titleMedium)
                Text("Total: ${state.total}")
                Text("Active: ${state.active}  ·  Available: ${state.available}")
                Text("Offline: ${state.offline}  ·  Maintenance: ${state.maintenance}")

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Maintenance mode", Modifier.weight(1f))
                    Switch(
                        checked = state.maintenanceMode,
                        onCheckedChange = { viewModel.setMaintenance(it, actorId) },
                    )
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                OutlinedButton(onClick = onOpenActivity, modifier = Modifier.fillMaxWidth()) {
                    Text("System activity log")
                }
            }
        }
    }
}

@Composable
fun AdminActivityScreen(
    onBack: () -> Unit,
    viewModel: AdminActivityViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    BevestScaffold(title = "Activity log", onBack = onBack) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.entries.isEmpty() -> EmptyState("No activity", "System actions will appear here.", Modifier.padding(padding), icon = BevestIcons.History)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    Column {
                        Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            DateTimeUtils.formatDateTime(entry.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
