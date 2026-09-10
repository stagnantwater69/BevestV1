package com.jtexpress.bevest.ui.sso

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.ui.common.BatteryIndicator
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.ConnectionIndicator
import com.jtexpress.bevest.ui.common.DangerButton
import com.jtexpress.bevest.ui.common.DetailRow
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorNote
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.FilterOption
import com.jtexpress.bevest.ui.common.FilterRow
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.QrScanner
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatusBadge
import com.jtexpress.bevest.ui.common.StatusShield
import com.jtexpress.bevest.ui.common.SuccessNote
import com.jtexpress.bevest.ui.common.detail
import com.jtexpress.bevest.ui.common.label
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.ComponentHeight
import com.jtexpress.bevest.ui.theme.Elevation
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.ui.vest.PairStep
import com.jtexpress.bevest.ui.vest.PairVestViewModel
import com.jtexpress.bevest.ui.vest.VestDetailViewModel
import com.jtexpress.bevest.ui.vest.VestFilter
import com.jtexpress.bevest.ui.vest.VestListViewModel
import com.jtexpress.bevest.utils.DateTimeUtils

/**
 * Maps a vest's condition onto the safety palette, so a vest list and a worker list use
 * the same colors for the same severity of problem. An offline vest and an offline
 * worker are the same kind of bad news and should not look different.
 */
@Composable
private fun vestColor(status: VestStatus): Color {
    val palette = LocalStatusPalette.current
    return when (status) {
        VestStatus.ACTIVE -> palette.normal
        VestStatus.ASSIGNED -> palette.normal
        VestStatus.AVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
        VestStatus.MAINTENANCE -> palette.warning
        VestStatus.OFFLINE -> palette.offline
    }
}

// ------------------------------------------------------------------- list

@Composable
fun VestListScreen(
    onOpenVest: (String) -> Unit,
    onPairVest: () -> Unit,
    viewModel: VestListViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current

    BevestScaffold(
        title = "Vests",
        floatingActionButton = {
            // Extended rather than a bare icon: "pair a vest" is not a universally
            // understood glyph, and this is the screen's primary action.
            ExtendedFloatingActionButton(
                onClick = onPairVest,
                icon = { Icon(BevestIcons.Scan, contentDescription = null) },
                text = { Text("Pair vest") },
            )
        },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            state.all.isEmpty() -> EmptyState(
                title = "No vests registered",
                message = "Pair a vest with a worker to start monitoring. You'll need " +
                    "the vest's QR code or its printed ID.",
                icon = BevestIcons.Vests,
                actionLabel = "Pair a vest",
                onAction = onPairVest,
                modifier = Modifier.padding(padding),
            )

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                FilterRow(
                    options = listOf(
                        FilterOption(VestFilter.ALL.name, "All", state.countFor(VestFilter.ALL)),
                        FilterOption(
                            VestFilter.ACTIVE.name, "In use",
                            state.countFor(VestFilter.ACTIVE), palette.normal,
                        ),
                        FilterOption(
                            VestFilter.AVAILABLE.name, "Available",
                            state.countFor(VestFilter.AVAILABLE),
                        ),
                        FilterOption(
                            VestFilter.OFFLINE.name, "Offline",
                            state.countFor(VestFilter.OFFLINE), palette.offline,
                        ),
                        FilterOption(
                            VestFilter.MAINTENANCE.name, "Service",
                            state.countFor(VestFilter.MAINTENANCE), palette.warning,
                        ),
                    ),
                    selectedKey = state.filter.name,
                    onSelect = { viewModel.onFilter(VestFilter.valueOf(it)) },
                )

                if (state.visible.isEmpty()) {
                    EmptyState(
                        title = "No vests here",
                        message = "No vest currently has this status.",
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
                        items(state.visible, key = { it.vestId }) { vest ->
                            VestRow(vest, onClick = { onOpenVest(vest.vestId) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * A vest in a list.
 *
 * Shows only what distinguishes one vest from another at a glance — its ID, who has it,
 * whether it is talking, and how much charge is left. Everything else waits for the
 * detail screen. The old version put status, assignment, battery and connectivity into
 * one run-on line of text, which made every row look identical.
 */
@Composable
private fun VestRow(vest: Vest, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = vestColor(vest.status)
    val lowBattery = (vest.batteryPercent ?: 100) <= 20

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ComponentHeight.listRow)
            .clip(BevestShapes.card)
            .clickable(onClick = onClick),
        shape = BevestShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = Elevation.card,
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(BevestShapes.inner)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    BevestIcons.forVestStatus(vest.status),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(IconSize.medium),
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    vest.vestId,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    vest.assignedWorkerId?.let { "Worn by $it" } ?: "Not assigned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ConnectionIndicator(vest.online)
                        Text(
                            if (vest.online) "Online" else DateTimeUtils.relativeAge(vest.lastSeen),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BatteryIndicator(vest.batteryPercent)
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                StatusBadge(text = vest.status.label(), color = color)
                if (lowBattery) {
                    StatusBadge(
                        text = "Charge",
                        color = LocalStatusPalette.current.warning,
                        icon = BevestIcons.BatteryLow,
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- detail

@Composable
fun VestDetailScreen(
    vestId: String,
    actorId: String,
    onBack: () -> Unit,
    viewModel: VestDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current
    var confirmUnassign by remember { mutableStateOf(false) }

    BevestScaffold(
        title = state.vest?.vestId ?: "Vest",
        subtitle = "Device",
        onBack = onBack,
    ) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.vest == null -> ErrorState(
                "That vest no longer exists.",
                modifier = Modifier.padding(padding),
            )
            else -> {
                val vest = state.vest
                val color = vestColor(vest.status)

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    // ---- Headline: the device's condition, in the shield ----
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = BevestShapes.status,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
                        shadowElevation = Elevation.card,
                    ) {
                        Column {
                            Row(
                                Modifier.padding(Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                            ) {
                                StatusShield(
                                    status = when (vest.status) {
                                        VestStatus.ACTIVE, VestStatus.ASSIGNED -> SafetyStatus.NORMAL
                                        VestStatus.MAINTENANCE -> SafetyStatus.WARNING
                                        VestStatus.OFFLINE -> SafetyStatus.OFFLINE
                                        VestStatus.AVAILABLE -> SafetyStatus.OFFLINE
                                    },
                                    size = 56.dp,
                                ) {
                                    Icon(
                                        BevestIcons.forVestStatus(vest.status),
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        vest.status.label(),
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                    Text(
                                        vest.status.detail(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            ReflectiveBand(thickness = 2.dp, emphasis = 0.7f)
                            Row(
                                Modifier.padding(Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    ConnectionIndicator(vest.online)
                                    Text(
                                        if (vest.online) "Online" else "Offline",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                BatteryIndicator(vest.batteryPercent)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    DateTimeUtils.relativeAge(vest.lastSeen),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ---- Assignment ----
                    SectionHeader("Assignment")
                    DetailRow(
                        label = "Current wearer",
                        value = vest.assignedWorkerId ?: "Not assigned to anyone",
                        icon = BevestIcons.Workers,
                    )

                    if (!vest.assignedWorkerId.isNullOrBlank()) {
                        DangerButton(
                            text = "Unassign vest",
                            onClick = { confirmUnassign = true },
                        )
                    }

                    // ---- Service ----
                    SectionHeader(
                        title = "Service",
                        supporting = "Take a vest out of use while it is repaired",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SecondaryButton(
                            text = "Needs service",
                            onClick = { viewModel.setStatus(VestStatus.MAINTENANCE) },
                            icon = BevestIcons.Simulator,
                            enabled = vest.status != VestStatus.MAINTENANCE,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "Back in use",
                            onClick = { viewModel.setStatus(VestStatus.AVAILABLE) },
                            icon = BevestIcons.forStatus(SafetyStatus.NORMAL),
                            enabled = vest.status == VestStatus.MAINTENANCE,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    state.message?.let { SuccessNote(it) }

                    // ---- History ----
                    SectionHeader(
                        title = "Assignment history",
                        supporting = "Who has worn this vest",
                    )
                    if (state.assignments.isEmpty()) {
                        Text(
                            "This vest has never been assigned.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.assignments.forEach { a ->
                            val current = a.unassignedAt == null
                            DetailRow(
                                label = if (current) "Current" else "Previous",
                                value = "${a.workerId}  ·  ${DateTimeUtils.formatDate(a.assignedAt)} → " +
                                    (a.unassignedAt?.let { DateTimeUtils.formatDate(it) } ?: "now"),
                                icon = BevestIcons.History,
                                valueColor = if (current) palette.normal else null,
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.lg))
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

// ------------------------------------------------------------------- pair

/**
 * Pairing a vest to a worker, in three steps.
 *
 * The step indicator is the reflective band, segmented — the same device that separates
 * sections elsewhere, here doing duty as progress. It replaces the old "Step 2 of 3"
 * text label, which told the user where they were but not how far they had to go.
 */
@Composable
fun PairVestScreen(
    user: User,
    onDone: () -> Unit,
    viewModel: PairVestViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.siteId) { viewModel.bind(user.siteId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    LaunchedEffect(state.step) { if (state.step == PairStep.DONE) onDone() }

    val stepTitle = when (state.step) {
        PairStep.IDENTIFY -> "Identify the vest"
        PairStep.SELECT_WORKER -> "Choose a worker"
        PairStep.CONNECTION_TEST -> "Test the connection"
        PairStep.DONE -> "Paired"
    }

    BevestScaffold(
        title = "Pair vest",
        subtitle = "Step ${state.step.ordinal + 1} of 3",
        onBack = if (state.step == PairStep.IDENTIFY) onDone else viewModel::back,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StepProgress(current = state.step.ordinal, total = 3)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.lg)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(stepTitle, style = MaterialTheme.typography.headlineSmall)

                when (state.step) {
                    PairStep.IDENTIFY -> {
                        Text(
                            "Scan the QR code printed on the vest, or type its ID below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = BevestShapes.card,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            QrScanner(onResult = viewModel::onScanned)
                        }
                        FormField(
                            value = state.vestIdInput,
                            onValueChange = viewModel::onVestIdInput,
                            label = "Vest ID",
                            error = state.vestIdError,
                            helper = "Printed under the QR code",
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Go,
                            onImeAction = viewModel::identifyVest,
                        )
                        PrimaryButton(
                            text = "Continue",
                            onClick = viewModel::identifyVest,
                            loading = state.checking,
                            loadingText = "Checking…",
                        )
                    }

                    PairStep.SELECT_WORKER -> {
                        Text(
                            "Assign vest ${state.identifiedVestId} to:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.unassignedWorkers.isEmpty()) {
                            EmptyState(
                                title = "Everyone already has a vest",
                                message = "Every active worker on this site is paired. " +
                                    "Unassign a vest first, or register a new worker.",
                                icon = BevestIcons.NoWorkers,
                                modifier = Modifier.heightIn(min = 240.dp),
                            )
                        }
                        state.unassignedWorkers.forEach { w ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = ComponentHeight.control)
                                    .clip(BevestShapes.card)
                                    .clickable { viewModel.selectWorker(w.workerId) },
                                shape = BevestShapes.card,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    Modifier.padding(Spacing.lg),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                ) {
                                    com.jtexpress.bevest.ui.common.WorkerAvatar(
                                        name = w.fullName.ifBlank { w.workerId },
                                        photoUrl = w.photoUrl,
                                        size = 40.dp,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            w.fullName.ifBlank { w.workerId },
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            w.workerId,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PairStep.CONNECTION_TEST -> {
                        Text(
                            "Reading from the vest to confirm every sensor is reporting.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val checks = listOf(
                            "Heart rate sensor" to state.checks.heartRate,
                            "Body temperature sensor" to state.checks.temperature,
                            "Motion sensor" to state.checks.motion,
                            "GPS" to state.checks.gps,
                            "Device connection" to state.checks.connection,
                        )
                        Surface(
                            shape = BevestShapes.card,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(Spacing.lg)) {
                                checks.forEach { (label, pass) ->
                                    ConnectionRow(label, pass, testing = state.testing)
                                }
                            }
                        }
                        if (!state.testing) {
                            SecondaryButton(
                                text = "Run the test again",
                                onClick = viewModel::runConnectionTest,
                                icon = BevestIcons.Refresh,
                            )
                            PrimaryButton(
                                text = "Confirm pairing",
                                onClick = { viewModel.confirmPairing(user.uid) },
                                enabled = state.checks.allPass,
                                loading = state.submitting,
                                loadingText = "Pairing…",
                            )
                            if (!state.checks.allPass) {
                                Text(
                                    "Every sensor has to report before a vest can be paired — " +
                                        "monitoring depends on all of them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    PairStep.DONE -> SuccessNote("Vest paired.")
                }

                state.error?.let { ErrorNote(it) }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

/** Segmented reflective band as a progress indicator. */
@Composable
private fun StepProgress(current: Int, total: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(total) { index ->
            val done = index <= current
            Box(Modifier.weight(1f).clip(BevestShapes.pill)) {
                ReflectiveBand(
                    thickness = 4.dp,
                    emphasis = if (done) 1f else 0.25f,
                    tint = if (done) MaterialTheme.colorScheme.primary else null,
                )
            }
        }
    }
}

/**
 * One sensor's result. While the test runs every row reads "Checking…" rather than
 * showing a stale pass/fail from the previous attempt.
 */
@Composable
private fun ConnectionRow(label: String, pass: Boolean, testing: Boolean) {
    val palette = LocalStatusPalette.current
    val color = when {
        testing -> MaterialTheme.colorScheme.onSurfaceVariant
        pass -> palette.normal
        else -> palette.warning
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!testing) {
                Icon(
                    imageVector = if (pass) {
                        BevestIcons.forStatus(SafetyStatus.NORMAL)
                    } else {
                        BevestIcons.forStatus(SafetyStatus.WARNING)
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(IconSize.small),
                )
            }
            Text(
                when {
                    testing -> "Checking…"
                    pass -> "Reporting"
                    else -> "No signal"
                },
                style = EyebrowStyle,
                color = color,
            )
        }
    }
}
