package com.jtexpress.bevest.ui.contractor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.ui.common.BevestCard
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.DangerButton
import com.jtexpress.bevest.ui.common.DashboardSkeleton
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorNote
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.FilterOption
import com.jtexpress.bevest.ui.common.FilterRow
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.FormSection
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.ProfileAction
import com.jtexpress.bevest.ui.common.SearchBar
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatTile
import com.jtexpress.bevest.ui.common.StatusBadge
import com.jtexpress.bevest.ui.common.StatusBar
import com.jtexpress.bevest.ui.common.StatusSlice
import com.jtexpress.bevest.ui.common.WorkerAvatar
import com.jtexpress.bevest.ui.common.label
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.ComponentHeight
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.utils.DateTimeUtils
import com.jtexpress.bevest.utils.PdfExporter

private fun contractorId(user: User) = user.contractorId ?: user.uid

// -------------------------------------------------------------- dashboard

/**
 * The contractor's overview across every site.
 *
 * Where the officer's dashboard is about *now*, this one is about *trend* — a contractor
 * is answering "are we getting safer?", not "who needs help this minute". Each headline
 * figure opens the screen that explains it, the same as on the web dashboard.
 */
@Composable
fun ContractorDashboardScreen(
    user: User,
    onOpenProfile: () -> Unit,
    onOpenSsos: () -> Unit,
    onOpenReport: (monthKey: String) -> Unit,
    onOpenIncidents: () -> Unit,
    viewModel: ContractorDashboardViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.uid) { viewModel.start(contractorId(user)) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current

    BevestScaffold(
        title = "Safety overview",
        subtitle = "All sites",
        actions = { ProfileAction(user, onClick = onOpenProfile) },
    ) { padding ->
        when {
            state.loading -> DashboardSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // ---- Headline figures ----
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        icon = BevestIcons.Officer,
                        value = state.activeSsos.toString(),
                        label = "Active SSO",
                        onClick = onOpenSsos,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = BevestIcons.Vests,
                        value = "${state.safetyScore}%",
                        label = "Safety score",
                        accent = if (state.safetyScore >= 90) palette.normal else palette.warning,
                        onClick = state.currentMonthKey?.let { key -> { onOpenReport(key) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                StatTile(
                    icon = BevestIcons.History,
                    value = state.monthlyIncidents.toString(),
                    label = "Incidents (30d)",
                    accent = if (state.monthlyIncidents > 0) palette.danger else null,
                    onClick = onOpenIncidents,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---- Trend ----
                SectionHeader(
                    title = "Incident trend",
                    supporting = "Recorded incidents by month or by day",
                )
                BevestCard {
                    IncidentTrendChart(incidents = state.incidents)
                }

                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

// -------------------------------------------------------------------- SSOs

@Composable
fun ContractorSsosScreen(
    user: User,
    onAdd: () -> Unit,
    onOpen: (uid: String) -> Unit,
    viewModel: SsoListViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.uid) { viewModel.start(contractorId(user)) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current
    // Disabling an officer cuts off the person monitoring a live site, so it is
    // confirmed rather than fired straight from the list row.
    var pendingDisable by remember { mutableStateOf<User?>(null) }

    BevestScaffold(
        title = "Site Safety Officers",
        subtitle = "Officers monitoring workers on your sites",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(BevestIcons.Add, contentDescription = null) },
                text = { Text("Add officer") },
            )
        },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                SearchBar(
                    query = state.query,
                    onQueryChange = viewModel::onQuery,
                    placeholder = "Search officers by name or email",
                    modifier = Modifier.padding(
                        horizontal = Spacing.gutter,
                        vertical = Spacing.sm,
                    ),
                )

                if (state.visible.isEmpty()) {
                    val searching = state.query.isNotBlank()
                    EmptyState(
                        title = if (searching) "No matches" else "No officers yet",
                        message = if (searching) {
                            "Nothing matches \"${state.query}\"."
                        } else {
                            "Add a Site Safety Officer and assign them to a site so they " +
                                "can monitor the workers there."
                        },
                        icon = if (searching) BevestIcons.NoResults else BevestIcons.Officer,
                        actionLabel = if (searching) null else "Add officer",
                        onAction = if (searching) null else onAdd,
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
                        items(state.visible, key = { it.uid }) { sso ->
                            PersonRow(
                                name = sso.fullName.ifBlank { sso.email },
                                email = sso.email,
                                supporting = state.siteName(sso.siteId),
                                active = sso.active,
                                onClick = { onOpen(sso.uid) },
                                trailing = {
                                    StatusBadge(
                                        text = if (sso.active) "Active" else "Disabled",
                                        color = if (sso.active) palette.normal else palette.offline,
                                    )
                                },
                                action = if (sso.active) {
                                    { pendingDisable = sso }
                                } else {
                                    null
                                },
                                actionLabel = "Disable access",
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDisable?.let { sso ->
        ConfirmationDialog(
            title = "Disable this officer?",
            message = "${sso.fullName.ifBlank { sso.email }} will lose access on their " +
                "next app open and will stop receiving safety alerts for their site. " +
                "Their records are kept.",
            confirmLabel = "Disable access",
            onConfirm = {
                pendingDisable = null
                viewModel.disable(sso.uid)
            },
            onDismiss = { pendingDisable = null },
        )
    }
}

/**
 * A person in a management list — officer or contractor.
 *
 * Deliberately identical between the contractor and admin screens: the two lists do the
 * same job for different roles, and there is no reason for an administrator to have to
 * learn a second layout to read the same information.
 */
@Composable
fun PersonRow(
    name: String,
    email: String,
    supporting: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    action: (() -> Unit)? = null,
    actionLabel: String = "Disable",
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(BevestShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = BevestShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                WorkerAvatar(name = name, photoUrl = null, size = 44.dp)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // A disabled account is history, and should read that way.
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trailing?.invoke()
                if (onClick != null) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.large),
                    )
                }
            }
            if (action != null) {
                Spacer(Modifier.height(Spacing.md))
                // The only thing this slot is ever used for is revoking someone's
                // access, so it gets the danger treatment rather than reading as a
                // neutral option in brand orange.
                DangerButton(text = actionLabel, onClick = action)
            }
        }
    }
}

// ------------------------------------------------------------------ add SSO

@Composable
fun AddSsoScreen(
    contractor: User,
    onDone: () -> Unit,
    viewModel: AddSsoViewModel = hiltViewModel(),
) {
    LaunchedEffect(contractor.uid) { viewModel.bind(contractorId(contractor)) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(state.created) { if (state.created) onDone() }

    BevestScaffold(title = "Add officer", subtitle = "New account", onBack = onDone) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            // Grouped rather than one long stack of inputs: who they are, how to reach
            // them, and how they first sign in are three separate decisions.
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
                    state.siteId,
                    { viewModel.update("siteId", it) },
                    "Assigned site ID",
                    error = state.errors["siteId"],
                    helper = "Optional — a site can be assigned later",
                )
                FormField(
                    state.tempPassword,
                    { viewModel.update("tempPassword", it) },
                    "Temporary password",
                    error = state.errors["tempPassword"],
                    helper = "They change this from their profile after signing in",
                    // Shown, not masked: the contractor has to pass it on to the officer.
                    required = true,
                    imeAction = ImeAction.Done,
                    onImeAction = viewModel::submit,
                )
                SecondaryButton(
                    text = if (state.tempPassword.isBlank()) "Generate password" else "Generate a new one",
                    onClick = viewModel::generatePassword,
                    icon = BevestIcons.Refresh,
                )
            }

            state.formError?.let { ErrorNote(it) }

            PrimaryButton(
                text = "Create account",
                onClick = viewModel::submit,
                loading = state.submitting,
                loadingText = "Creating…",
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

// ---------------------------------------------------------------- reports

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
    val palette = LocalStatusPalette.current

    BevestScaffold(
        title = "Reports",
        subtitle = "Monthly summaries and the full incident record",
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.md,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "incidents-link") {
                    QuickLink(
                        icon = BevestIcons.History,
                        title = "Incident history",
                        supporting = state.incidentCount?.let {
                            "$it ${if (it == 1) "incident" else "incidents"} on record"
                        } ?: "Every recorded incident",
                        onClick = onOpenIncidents,
                    )
                }
                item(key = "ssos-link") {
                    QuickLink(
                        icon = BevestIcons.Officer,
                        title = "Site Safety Officers",
                        supporting = "Manage who monitors your sites",
                        onClick = onOpenSsos,
                    )
                }
                item(key = "monthly-header") {
                    SectionHeader(
                        title = "Monthly reports",
                        supporting = "Tap a month for detail and a PDF export",
                    )
                }
                if (state.reports.isEmpty()) {
                    item(key = "monthly-empty") {
                        EmptyState(
                            title = "No reports yet",
                            message = "A monthly report is generated once a full month of " +
                                "monitoring data exists.",
                            icon = BevestIcons.Reports,
                            modifier = Modifier.heightIn(min = 240.dp),
                        )
                    }
                } else {
                    items(state.reports, key = { it.monthKey }) { report ->
                        val healthy = report.safetyPercentage >= 90
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ComponentHeight.listRow)
                                .clip(BevestShapes.card)
                                .clickable { onOpenReport(report.monthKey) },
                            shape = BevestShapes.card,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.padding(Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        DateTimeUtils.monthLabel(report.monthKey),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "${report.totalIncidents} " +
                                            if (report.totalIncidents == 1) "incident" else "incidents",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                SeverityCounts(
                                    warning = report.warningCount,
                                    danger = report.dangerCount,
                                    emergency = report.emergencyCount,
                                )
                                StatusBadge(
                                    text = "${report.safetyPercentage}% safe",
                                    color = if (healthy) palette.normal else palette.warning,
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(IconSize.large),
                                )
                            }
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
    val palette = LocalStatusPalette.current

    BevestScaffold(title = "Monthly report", onBack = onBack) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            state.report == null -> EmptyState(
                title = "No report for this month",
                message = "There isn't enough monitoring data for this period yet.",
                icon = BevestIcons.Reports,
                modifier = Modifier.padding(padding),
            )

            else -> {
                val report = state.report
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        DateTimeUtils.monthLabel(report.monthKey),
                        style = MaterialTheme.typography.headlineSmall,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatTile(
                            icon = BevestIcons.Vests,
                            value = "${report.safetyPercentage}%",
                            label = "Safety score",
                            accent = if (report.safetyPercentage >= 90) {
                                palette.normal
                            } else {
                                palette.warning
                            },
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            icon = BevestIcons.Workers,
                            value = report.activeWorkers.toString(),
                            label = "Active workers",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    SectionHeader(
                        title = "Severity breakdown",
                        supporting = "${report.totalIncidents} incidents in total",
                    )
                    BevestCard {
                        StatusBar(
                            slices = listOf(
                                StatusSlice("Warning", report.warningCount, palette.warning),
                                StatusSlice("Danger", report.dangerCount, palette.danger),
                                StatusSlice("Emergency", report.emergencyCount, palette.emergency),
                            ),
                        )
                    }

                    PrimaryButton(
                        text = "Export & share PDF",
                        onClick = {
                            val file = PdfExporter.monthlyReport(context, report, state.incidents)
                            PdfExporter.share(context, file)
                        },
                        icon = BevestIcons.Reports,
                    )

                    SectionHeader(
                        title = "Incidents this month",
                        supporting = "Oldest first",
                    )
                    if (state.incidents.isEmpty()) {
                        EmptyState(
                            title = "No incidents",
                            message = "Nothing was recorded in this period — a good month.",
                            icon = BevestIcons.forStatus(SafetyStatus.NORMAL),
                            modifier = Modifier.heightIn(min = 200.dp),
                        )
                    } else {
                        state.incidents.forEach { incident ->
                            IncidentRow(
                                incident,
                                workerName = state.workerNames[incident.workerId],
                                onClick = null,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
        }
    }
}

// -------------------------------------------------------------- incidents

@Composable
fun ContractorIncidentsScreen(
    contractorId: String,
    onOpenIncident: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ContractorIncidentsViewModel = hiltViewModel(),
) {
    LaunchedEffect(contractorId) { viewModel.start(contractorId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current

    BevestScaffold(title = "Incident history", onBack = onBack) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                FilterRow(
                    options = buildList {
                        add(FilterOption("ALL", "All", state.incidents.size))
                        AlertSeverity.entries.forEach { sev ->
                            add(
                                FilterOption(
                                    key = sev.name,
                                    label = sev.label(),
                                    count = state.incidents.count { it.severity == sev },
                                    accent = when (sev) {
                                        AlertSeverity.WARNING -> palette.warning
                                        AlertSeverity.DANGER -> palette.danger
                                        AlertSeverity.EMERGENCY -> palette.emergency
                                    },
                                ),
                            )
                        }
                    },
                    selectedKey = state.severityFilter?.name ?: "ALL",
                    onSelect = { key ->
                        viewModel.onFilter(
                            if (key == "ALL") null else AlertSeverity.valueOf(key),
                        )
                    },
                )

                if (state.visible.isEmpty()) {
                    EmptyState(
                        title = if (state.incidents.isEmpty()) {
                            "No incidents"
                        } else {
                            "Nothing at this severity"
                        },
                        message = if (state.incidents.isEmpty()) {
                            "Incidents appear here once an alert is closed with an outcome."
                        } else {
                            "No incident on record has this severity."
                        },
                        icon = BevestIcons.History,
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.gutter,
                            end = Spacing.gutter,
                            top = Spacing.sm,
                            bottom = Spacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(state.visible, key = { it.incidentId }) { incident ->
                            IncidentRow(
                                incident,
                                workerName = state.workerNames[incident.workerId],
                                onClick = { onOpenIncident(incident.incidentId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * An incident in a list. Shares its shape and severity language with the alert rows on
 * the officer's side, because the two describe the same events at different points in
 * their lifecycle and should be recognisable as the same kind of thing.
 */
@Composable
private fun IncidentRow(
    incident: Incident,
    workerName: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStatusPalette.current
    val color = when (incident.severity) {
        AlertSeverity.WARNING -> palette.warning
        AlertSeverity.DANGER -> palette.danger
        AlertSeverity.EMERGENCY -> palette.emergency
    }
    val open = incident.outcome == null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ComponentHeight.listRow)
            .clip(BevestShapes.alert)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = BevestShapes.alert,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(BevestShapes.tile)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    BevestIcons.forAlertType(incident.type),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(IconSize.medium),
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    incident.type.label(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    workerName ?: "Worker ${incident.workerId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DateTimeUtils.formatDateTime(incident.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(
                text = if (open) "Open" else "Closed",
                color = if (open) palette.warning else palette.normal,
            )
        }
    }
}

/** A tappable row that opens another screen: icon, title, one line of context, chevron. */
@Composable
private fun QuickLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ComponentHeight.listRow)
            .clip(BevestShapes.card)
            .clickable(onClick = onClick),
        shape = BevestShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(BevestShapes.tile)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.medium),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.large),
            )
        }
    }
}

/** A month's incidents split by severity, as three small colored counts. */
@Composable
private fun SeverityCounts(warning: Int, danger: Int, emergency: Int) {
    val palette = LocalStatusPalette.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$warning warning, $danger danger, $emergency emergency"
        },
    ) {
        listOf(warning to palette.warning, danger to palette.danger, emergency to palette.emergency)
            .forEach { (count, color) ->
                Box(
                    Modifier
                        .clip(BevestShapes.tile)
                        .background(color.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
    }
}
