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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
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
import com.jtexpress.bevest.ui.common.FilterOption
import com.jtexpress.bevest.ui.common.FilterRow
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.FormSection
import com.jtexpress.bevest.ui.common.InsightCard
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.ProfileAction
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.common.SearchBar
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatTile
import com.jtexpress.bevest.ui.common.StatusBadge
import com.jtexpress.bevest.ui.common.StatusBar
import com.jtexpress.bevest.ui.common.StatusSlice
import com.jtexpress.bevest.ui.common.SuccessNote
import com.jtexpress.bevest.ui.common.label
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
        title = "System Dashboard",
        subtitle = "Real-time oversight across all sites",
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

// ------------------------------------------------------------------ users

/**
 * User Management — the same list the web admin shows: every account, searchable by name
 * or email, filterable by role and sortable by name.
 */
@Composable
fun AdminUsersScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: AdminUsersViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current

    BevestScaffold(
        title = "User Management",
        subtitle = "${state.activeCount} active users",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(BevestIcons.Add, contentDescription = null) },
                text = { Text("Register user") },
            )
        },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                SearchBar(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    placeholder = "Search users by name or email",
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                )
                FilterRow(
                    options = listOf(
                        FilterOption(ALL_ROLES, "All roles", state.all.size),
                        FilterOption(UserRole.SSO.name, "SSO", state.count(UserRole.SSO)),
                        FilterOption(UserRole.CONTRACTOR.name, "Contractor", state.count(UserRole.CONTRACTOR)),
                        FilterOption(UserRole.ADMIN.name, "Admin", state.count(UserRole.ADMIN)),
                    ),
                    selectedKey = state.roleFilter,
                    onSelect = viewModel::setRoleFilter,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${state.visible.size} users",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::toggleSort) {
                        Text(if (state.sortAscending) "Name ↑" else "Name ↓")
                    }
                }

                if (state.visible.isEmpty()) {
                    EmptyState(
                        title = if (state.all.isEmpty()) "No users yet" else "No matching users",
                        message = if (state.all.isEmpty()) {
                            "Register a user so they can sign in."
                        } else {
                            "Try a different name, email or role."
                        },
                        icon = BevestIcons.NoResults,
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.gutter,
                            end = Spacing.gutter,
                            top = Spacing.sm,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(state.visible, key = { it.uid }) { u ->
                            PersonRow(
                                name = u.fullName.ifBlank { u.email },
                                email = u.email,
                                supporting = u.role.label(),
                                active = u.active,
                                trailing = {
                                    StatusBadge(
                                        text = if (u.active) "Active" else "Disabled",
                                        color = if (u.active) palette.normal else palette.offline,
                                    )
                                },
                                onClick = { onOpen(u.uid) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddUserScreen(
    actorId: String,
    onDone: () -> Unit,
    viewModel: AddUserViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(state.created) { if (state.created) onDone() }

    BevestScaffold(
        title = "Register new user",
        subtitle = "User Management",
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
            FormSection(title = "Personal data") {
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
                FormField(
                    state.phone,
                    { viewModel.update("phone", it) },
                    "Phone",
                    error = state.errors["phone"],
                    keyboardType = KeyboardType.Phone,
                )
                FormField(
                    state.email,
                    { viewModel.update("email", it) },
                    "Email",
                    error = state.errors["email"],
                    helper = "They sign in with this address",
                    keyboardType = KeyboardType.Email,
                    required = true,
                )
            }

            FormSection(title = "Access") {
                PickerField(
                    label = "Assign role",
                    selected = state.role.label(),
                    options = listOf(UserRole.SSO, UserRole.CONTRACTOR, UserRole.ADMIN)
                        .map { it.label() to { viewModel.setRole(it) } },
                )
                if (state.role == UserRole.SSO) {
                    PickerField(
                        label = "Contractor",
                        selected = state.contractors.firstOrNull { it.uid == state.contractorId }
                            ?.let { it.fullName.ifBlank { it.email } }
                            ?: "Choose a contractor",
                        options = state.contractors.map { c ->
                            c.fullName.ifBlank { c.email } to { viewModel.setContractor(c.uid) }
                        },
                        error = state.errors["contractorId"],
                    )
                }
                FormField(
                    state.tempPassword,
                    { viewModel.update("tempPassword", it) },
                    "Password",
                    error = state.errors["tempPassword"],
                    helper = "They change this from their profile after signing in",
                    required = true,
                    imeAction = ImeAction.Done,
                    onImeAction = { viewModel.submit(actorId) },
                )
                SecondaryButton(
                    text = "Generate password",
                    onClick = viewModel::generatePassword,
                    icon = BevestIcons.Refresh,
                )
            }

            state.formError?.let { ErrorNote(it) }

            PrimaryButton(
                text = "Create user",
                onClick = { viewModel.submit(actorId) },
                loading = state.submitting,
                loadingText = "Creating…",
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** A read-only dropdown field; each option is a label and what picking it does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(
    label: String,
    selected: String,
    options: List<Pair<String, () -> Unit>>,
    error: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = BevestShapes.control,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, onPick) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onPick(); expanded = false },
                )
            }
        }
    }
}

@Composable
fun AdminUserDetailScreen(
    actorId: String,
    onBack: () -> Unit,
    viewModel: AdminUserDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current
    var confirm by remember { mutableStateOf(false) }

    BevestScaffold(title = "User", onBack = onBack) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            state.user == null -> ErrorState(
                "That user no longer exists.",
                modifier = Modifier.padding(padding),
            )

            else -> {
                val u = state.user
                val isSelf = u.uid == actorId
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    PersonRow(
                        name = u.fullName.ifBlank { u.email },
                        email = u.email,
                        supporting = u.phone.ifBlank { "No phone on file" },
                        active = u.active,
                        trailing = {
                            StatusBadge(
                                text = if (u.active) "Active" else "Disabled",
                                color = if (u.active) palette.normal else palette.offline,
                            )
                        },
                    )

                    SectionHeader("Access")
                    DetailRow(
                        label = "Role",
                        value = u.role.label(),
                        icon = BevestIcons.WorkerId,
                    )
                    DetailRow(
                        label = "Account status",
                        value = if (u.active) {
                            "Active — they can sign in."
                        } else {
                            "Disabled — they cannot sign in."
                        },
                        icon = if (u.active) {
                            BevestIcons.forStatus(SafetyStatus.NORMAL)
                        } else {
                            BevestIcons.Error
                        },
                        valueColor = if (u.active) null else palette.offline,
                    )

                    // An admin disabling their own account would lock themselves out.
                    if (!isSelf) {
                        if (u.active) {
                            DangerButton(
                                text = "Disable user",
                                onClick = { confirm = true },
                            )
                        } else {
                            PrimaryButton(
                                text = "Re-enable user",
                                onClick = { viewModel.setActive(true, actorId) },
                                icon = BevestIcons.forStatus(SafetyStatus.NORMAL),
                            )
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
            title = "Disable this user?",
            message = "They lose access on next app open. Records are kept.",
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

    BevestScaffold(
        title = "System Management",
        subtitle = "System configuration and devices",
    ) { padding ->
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
