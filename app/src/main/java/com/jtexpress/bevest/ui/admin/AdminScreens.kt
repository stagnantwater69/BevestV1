package com.jtexpress.bevest.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.navigation.Routes
import com.jtexpress.bevest.ui.common.BevestCard
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.DangerButton
import com.jtexpress.bevest.ui.common.DashboardSkeleton
import com.jtexpress.bevest.ui.common.DetailRow
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorNote
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.FormSection
import com.jtexpress.bevest.ui.common.InsightCard
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.ProfileAction
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatTile
import com.jtexpress.bevest.ui.common.StatusBadge
import com.jtexpress.bevest.ui.common.StatusBar
import com.jtexpress.bevest.ui.common.StatusSlice
import com.jtexpress.bevest.ui.common.SuccessNote
import com.jtexpress.bevest.ui.contractor.PersonRow
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.utils.DateTimeUtils

// -------------------------------------------------------------- dashboard

/**
 * The administrator's view of the whole deployment.
 *
 * Split into "people" and "hardware", because those are the two things an administrator
 * actually manages and they fail in completely different ways. Fleet health gets a bar
 * and a sentence rather than two more counters: whether a third of the vests are offline
 * is the real question, and a pair of raw numbers does not answer it.
 */
@Composable
fun AdminDashboardScreen(
    user: User,
    navigate: (String) -> Unit,
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current

    BevestScaffold(
        title = "Administration",
        subtitle = "System overview",
        actions = { ProfileAction(user, onClick = { navigate(Routes.PROFILE) }) },
    ) { padding ->
        if (state.loading) {
            DashboardSkeleton(Modifier.padding(padding))
        } else {
            val otherVests = (state.vests - state.activeVests - state.offlineVests)
                .coerceAtLeast(0)

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Maintenance mode changes how the whole system behaves, so it is
                // announced at the top rather than mentioned in a line of body text.
                if (state.maintenanceMode) {
                    MaintenanceBanner()
                }

                SectionHeader(
                    title = "People",
                    supporting = "Accounts and monitored workers",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        icon = BevestIcons.Contractors,
                        value = state.contractors.toString(),
                        label = "Contractors",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = BevestIcons.Officer,
                        value = state.ssos.toString(),
                        label = "Officers",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        icon = BevestIcons.Workers,
                        value = state.workers.toString(),
                        label = "Workers",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = BevestIcons.Vests,
                        value = state.vests.toString(),
                        label = "Vests",
                        modifier = Modifier.weight(1f),
                    )
                }

                SectionHeader(
                    title = "Fleet health",
                    supporting = "How the vest hardware is doing",
                )
                BevestCard {
                    InsightCard(
                        text = when {
                            state.vests == 0 -> "No vests are registered yet."
                            state.offlineVests == 0 ->
                                "All ${state.vests} vests are reachable."
                            else ->
                                "${state.offlineVests} of ${state.vests} vests are offline " +
                                    "and not monitoring anyone."
                        },
                        icon = BevestIcons.Signal,
                        accent = if (state.offlineVests > 0) palette.warning else palette.normal,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    StatusBar(
                        slices = listOf(
                            StatusSlice("Active", state.activeVests, palette.normal),
                            StatusSlice("Offline", state.offlineVests, palette.offline),
                            StatusSlice("Other", otherVests, palette.warning),
                        ),
                    )
                }

                SectionHeader("Manage")
                PrimaryButton(
                    text = "Contractors",
                    onClick = { navigate(Routes.ADMIN_CONTRACTORS) },
                    icon = BevestIcons.Contractors,
                )
                SecondaryButton(
                    text = "System & devices",
                    onClick = { navigate(Routes.ADMIN_SYSTEM) },
                    icon = BevestIcons.System,
                )

                SectionHeader(
                    title = "Recent activity",
                    supporting = "Administrative actions across the system",
                )
                if (state.recentActivity.isEmpty()) {
                    EmptyState(
                        title = "Nothing logged yet",
                        message = "Account changes and system settings appear here as they happen.",
                        icon = BevestIcons.History,
                        modifier = Modifier.heightIn(min = 200.dp),
                    )
                } else {
                    state.recentActivity.take(5).forEach { entry ->
                        ActivityRow(entry.message, entry.createdAt, relative = true)
                    }
                    SecondaryButton(
                        text = "Full activity log",
                        onClick = { navigate(Routes.ADMIN_ACTIVITY) },
                        icon = BevestIcons.History,
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

/**
 * System-wide maintenance mode. Uses the warning color and the reflective band rather
 * than the emergency treatment: it is a deliberate operator choice, not an incident, and
 * dressing it up as an emergency would erode what an emergency looks like.
 */
@Composable
private fun MaintenanceBanner() {
    val palette = LocalStatusPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BevestShapes.status,
        color = palette.warning.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, palette.warning.copy(alpha = 0.40f)),
    ) {
        Column {
            ReflectiveBand(thickness = 3.dp, emphasis = 1f, tint = palette.warning)
            Row(
                Modifier.padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    BevestIcons.Simulator,
                    contentDescription = null,
                    tint = palette.warning,
                    modifier = Modifier.size(IconSize.large),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("MAINTENANCE MODE", style = EyebrowStyle, color = palette.warning)
                    Text(
                        "The system is in maintenance. Monitoring may be interrupted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One line of the activity log. */
@Composable
private fun ActivityRow(
    message: String,
    at: Long,
    relative: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            BevestIcons.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.small).padding(top = 2.dp),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (relative) {
                    DateTimeUtils.relativeAge(at)
                } else {
                    DateTimeUtils.formatDateTime(at)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------ contractors

@Composable
fun AdminContractorsScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: AdminContractorsViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current

    BevestScaffold(
        title = "Contractors",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(BevestIcons.Add, contentDescription = null) },
                text = { Text("Add contractor") },
            )
        },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            state.contractors.isEmpty() -> EmptyState(
                title = "No contractors yet",
                message = "Add a contractor so they can register sites, officers and workers.",
                icon = BevestIcons.Contractors,
                actionLabel = "Add contractor",
                onAction = onAdd,
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.md,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.contractors, key = { it.uid }) { c ->
                    PersonRow(
                        name = c.fullName.ifBlank { c.email },
                        email = c.email,
                        supporting = c.phone.ifBlank { "No phone on file" },
                        active = c.active,
                        trailing = {
                            StatusBadge(
                                text = if (c.active) "Active" else "Disabled",
                                color = if (c.active) palette.normal else palette.offline,
                            )
                        },
                        onClick = { onOpen(c.uid) },
                    )
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

    BevestScaffold(
        title = "Add contractor",
        subtitle = "New account",
        onBack = onDone,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            FormSection(title = "Name") {
                FormField(
                    state.firstName,
                    { viewModel.update("firstName", it) },
                    "First name",
                    error = state.errors["firstName"],
                    required = true,
                )
                FormField(
                    state.lastName,
                    { viewModel.update("lastName", it) },
                    "Last name",
                    error = state.errors["lastName"],
                    required = true,
                )
            }

            FormSection(title = "Organisation") {
                FormField(
                    state.company,
                    { viewModel.update("company", it) },
                    "Company / project",
                    error = state.errors["company"],
                    required = true,
                )
            }

            FormSection(title = "Contact") {
                FormField(
                    state.email,
                    { viewModel.update("email", it) },
                    "Email",
                    error = state.errors["email"],
                    helper = "They sign in with this address",
                    keyboardType = KeyboardType.Email,
                    required = true,
                )
                FormField(
                    state.phone,
                    { viewModel.update("phone", it) },
                    "Phone",
                    error = state.errors["phone"],
                    keyboardType = KeyboardType.Phone,
                )
            }

            FormSection(title = "Access") {
                FormField(
                    state.tempPassword,
                    { viewModel.update("tempPassword", it) },
                    "Temporary password",
                    error = state.errors["tempPassword"],
                    helper = "They change this from their profile after signing in",
                    isPassword = true,
                    required = true,
                    imeAction = ImeAction.Done,
                    onImeAction = { viewModel.submit(actorId) },
                )
            }

            state.formError?.let { ErrorNote(it) }

            PrimaryButton(
                text = "Create contractor",
                onClick = { viewModel.submit(actorId) },
                loading = state.submitting,
                loadingText = "Creating…",
            )
            Spacer(Modifier.height(Spacing.lg))
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
    val palette = LocalStatusPalette.current
    var confirm by remember { mutableStateOf(false) }

    BevestScaffold(title = "Contractor", onBack = onBack) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            state.contractor == null -> ErrorState(
                "That contractor no longer exists.",
                modifier = Modifier.padding(padding),
            )

            else -> {
                val c = state.contractor
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    PersonRow(
                        name = c.fullName.ifBlank { c.email },
                        email = c.email,
                        supporting = c.phone.ifBlank { "No phone on file" },
                        active = c.active,
                        trailing = {
                            StatusBadge(
                                text = if (c.active) "Active" else "Disabled",
                                color = if (c.active) palette.normal else palette.offline,
                            )
                        },
                    )

                    SectionHeader("Access")
                    DetailRow(
                        label = "Account status",
                        value = if (c.active) {
                            "Active — they and their officers can sign in."
                        } else {
                            "Disabled — they and their officers cannot sign in."
                        },
                        icon = if (c.active) {
                            BevestIcons.forStatus(SafetyStatus.NORMAL)
                        } else {
                            BevestIcons.Error
                        },
                        valueColor = if (c.active) null else palette.offline,
                    )

                    if (c.active) {
                        DangerButton(
                            text = "Disable contractor",
                            onClick = { confirm = true },
                        )
                    } else {
                        PrimaryButton(
                            text = "Re-enable contractor",
                            onClick = { viewModel.setActive(true, actorId) },
                            icon = BevestIcons.forStatus(SafetyStatus.NORMAL),
                        )
                    }

                    state.message?.let { SuccessNote(it) }
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
        }
    }

    if (confirm) {
        ConfirmationDialog(
            title = "Disable this contractor?",
            message = "They and their officers lose access on next app open. Records are kept.",
            confirmLabel = "Disable",
            onConfirm = { confirm = false; viewModel.setActive(false, actorId) },
            onDismiss = { confirm = false },
        )
    }
}

// ----------------------------------------------------------------- system

@Composable
fun AdminSystemScreen(
    actorId: String,
    onOpenActivity: () -> Unit,
    viewModel: AdminSystemViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current
    var confirmMaintenance by remember { mutableStateOf(false) }

    BevestScaffold(title = "System & devices", subtitle = "Fleet") { padding ->
        if (state.loading) {
            DetailSkeleton(Modifier.padding(padding))
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                SectionHeader(
                    title = "Vest fleet",
                    supporting = "${state.total} devices registered",
                )
                BevestCard {
                    StatusBar(
                        slices = listOf(
                            StatusSlice("Active", state.active, palette.normal),
                            StatusSlice(
                                "Available",
                                state.available,
                                palette.normal.copy(alpha = 0.55f),
                            ),
                            StatusSlice("Offline", state.offline, palette.offline),
                            StatusSlice("Maintenance", state.maintenance, palette.warning),
                        ),
                    )
                }

                SectionHeader(
                    title = "System settings",
                    supporting = "Affects every site and every user",
                )
                Surface(
                    shape = BevestShapes.card,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Maintenance mode", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Pauses monitoring across the whole system while work is done.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.maintenanceMode,
                            // Turning this on stops monitoring for everyone, so it is
                            // confirmed. Turning it off restores safety cover, and that
                            // must never be behind an extra tap.
                            onCheckedChange = { on ->
                                if (on) confirmMaintenance = true
                                else viewModel.setMaintenance(false, actorId)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = palette.warning,
                            ),
                        )
                    }
                }

                state.message?.let { SuccessNote(it) }

                SecondaryButton(
                    text = "System activity log",
                    onClick = onOpenActivity,
                    icon = BevestIcons.History,
                )
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    if (confirmMaintenance) {
        ConfirmationDialog(
            title = "Turn on maintenance mode?",
            message = "Monitoring pauses across every site while maintenance mode is on. " +
                "Workers will not be covered until you turn it off.",
            confirmLabel = "Turn on",
            onConfirm = {
                confirmMaintenance = false
                viewModel.setMaintenance(true, actorId)
            },
            onDismiss = { confirmMaintenance = false },
        )
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

            state.entries.isEmpty() -> EmptyState(
                title = "Nothing logged yet",
                message = "Account changes and system settings are recorded here as they happen.",
                icon = BevestIcons.History,
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
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    ActivityRow(entry.message, entry.createdAt, relative = false)
                }
            }
        }
    }
}
