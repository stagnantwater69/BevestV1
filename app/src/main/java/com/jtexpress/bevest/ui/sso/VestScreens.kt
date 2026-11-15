package com.jtexpress.bevest.ui.sso

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.window.Dialog
import com.jtexpress.bevest.data.ble.BleConnectionState
import com.jtexpress.bevest.data.ble.DiscoveredBleVest
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.model.Worker
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
                icon = { Icon(BevestIcons.Add, contentDescription = null) },
                text = { Text("Add Vest") },
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
                actionLabel = "Add Vest",
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
    var showAssignWorkerDialog by remember { mutableStateOf(false) }
    var showChangeWifiDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { viewModel.disconnectBle() }
    }

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
                    SectionHeader(
                        title = "Assignment",
                        supporting = "Pair or reassign this vest to an active site worker",
                    )
                    val currentWearerLabel = vest.assignedWorkerId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { state.workerNameById[it] ?: it }
                        ?: "Not assigned to anyone"
                    DetailRow(
                        label = "Current wearer",
                        value = currentWearerLabel,
                        icon = BevestIcons.Workers,
                        valueColor = if (!vest.assignedWorkerId.isNullOrBlank()) palette.normal else null,
                    )

                    if (vest.assignedWorkerId.isNullOrBlank()) {
                        PrimaryButton(
                            text = if (state.assigningWorker) "Assigning vest…" else "Assign vest to worker",
                            onClick = { showAssignWorkerDialog = true },
                            icon = BevestIcons.Workers,
                            loading = state.assigningWorker,
                            enabled = !state.assigningWorker,
                        )
                    } else {
                        SecondaryButton(
                            text = if (state.assigningWorker) "Reassigning…" else "Assign to a different worker",
                            onClick = { showAssignWorkerDialog = true },
                            icon = BevestIcons.Workers,
                            enabled = !state.assigningWorker,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DangerButton(
                            text = "Unassign vest",
                            onClick = { confirmUnassign = true },
                        )
                    }

                    // ---- Site Wi-Fi Network (Option 1: Hybrid Cloud + BLE Re-Provisioning) ----
                    SectionHeader(
                        title = "Site Wi-Fi network",
                        supporting = "Change Wi-Fi SSID & password when moving this vest to another site",
                    )
                    DetailRow(
                        label = "Configured Wi-Fi",
                        value = vest.wifiSsid?.takeIf { it.isNotBlank() }
                            ?: if (vest.online) "Connected (active stream)" else "Not connected",
                        icon = Icons.Outlined.Wifi,
                        valueColor = if (vest.online) palette.normal else null,
                    )
                    SecondaryButton(
                        text = "Change Wi-Fi network",
                        onClick = {
                            viewModel.resetWifiDialogState(vest.wifiSsid)
                            showChangeWifiDialog = true
                        },
                        icon = Icons.Outlined.Wifi,
                        modifier = Modifier.fillMaxWidth(),
                    )

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
                            val workerLabel = state.workerNameById[a.workerId] ?: a.workerId
                            DetailRow(
                                label = if (current) "Current" else "Previous",
                                value = "$workerLabel  ·  ${DateTimeUtils.formatDate(a.assignedAt)} → " +
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

    if (showAssignWorkerDialog && state.vest != null) {
        AssignWorkerToVestDialog(
            vest = state.vest,
            workers = state.activeWorkers,
            assigning = state.assigningWorker,
            onSelectWorker = { selectedWorkerId ->
                showAssignWorkerDialog = false
                viewModel.assignToWorker(selectedWorkerId, actorId)
            },
            onDismiss = { showAssignWorkerDialog = false },
        )
    }

    if (showChangeWifiDialog && state.vest != null) {
        ChangeVestWifiDialog(
            vest = state.vest,
            wifiUi = state.wifiUi,
            onSsidChange = viewModel::onWifiSsidInput,
            onPassChange = viewModel::onWifiPassInput,
            onUpdateViaCloud = viewModel::updateWifiViaCloud,
            onTriggerRemoteBleAndScan = viewModel::triggerRemoteBleModeAndScan,
            onStartBleScan = viewModel::startBleScan,
            onConnectBle = viewModel::connectBleVest,
            onSendOverBle = viewModel::sendWifiOverBle,
            onDismiss = {
                viewModel.disconnectBle()
                showChangeWifiDialog = false
            },
        )
    }
}

@Composable
private fun AssignWorkerToVestDialog(
    vest: Vest,
    workers: List<Worker>,
    assigning: Boolean,
    onSelectWorker: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalStatusPalette.current
    var searchQuery by remember { mutableStateOf("") }

    val candidateWorkers = remember(workers, vest.assignedWorkerId, searchQuery) {
        val q = searchQuery.trim().lowercase()
        workers.filter { w ->
            w.workerId != vest.assignedWorkerId &&
                (q.isBlank() || w.fullName.lowercase().contains(q) || w.workerId.lowercase().contains(q))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = BevestShapes.card,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        BevestIcons.Workers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (vest.assignedWorkerId.isNullOrBlank()) {
                                "Assign ${vest.vestId} to Worker"
                            } else {
                                "Reassign ${vest.vestId} to Another Worker"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Select an active worker to wear and be monitored by this vest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                FormField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Search worker by name or ID",
                    helper = "e.g. Juan or WRK-001",
                    imeAction = ImeAction.Search,
                )

                if (candidateWorkers.isEmpty()) {
                    Surface(
                        shape = BevestShapes.inner,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                text = if (workers.isEmpty()) "No active workers found" else "No matching workers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (workers.isEmpty()) {
                                    "Add an active worker in the Workers tab first."
                                } else {
                                    "Try clearing the search filter or choosing another worker."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        candidateWorkers.forEach { w ->
                            val isUnassigned = w.assignedVestId.isNullOrBlank()
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(BevestShapes.inner)
                                    .clickable(enabled = !assigning) { onSelectWorker(w.workerId) },
                                shape = BevestShapes.inner,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isUnassigned) {
                                        palette.normal.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.md),
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
                                            text = w.fullName.ifBlank { w.workerId },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (isUnassigned) {
                                                "${w.workerId} · Ready to assign"
                                            } else {
                                                "${w.workerId} · Currently wearing ${w.assignedVestId}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    StatusBadge(
                                        text = if (isUnassigned) "Available" else "Reassign",
                                        color = if (isUnassigned) palette.normal else palette.warning,
                                    )
                                }
                            }
                        }
                    }
                }

                SecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ChangeVestWifiDialog(
    vest: Vest,
    wifiUi: com.jtexpress.bevest.ui.vest.WifiReprovisionUiState,
    onSsidChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onUpdateViaCloud: () -> Unit,
    onTriggerRemoteBleAndScan: () -> Unit,
    onStartBleScan: () -> Unit,
    onConnectBle: (DiscoveredBleVest) -> Unit,
    onSendOverBle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalStatusPalette.current
    var selectedTab by remember { mutableStateOf(if (vest.online) 0 else 1) }

    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onStartBleScan() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = BevestShapes.card,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Change Wi-Fi Network",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${vest.vestId} · ${if (vest.online) "Currently Online" else "Currently Offline"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Mode selector: Cloud (when Online) vs Bluetooth (when at new site / BLE mode)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SecondaryButton(
                        text = "Over Cloud",
                        onClick = { selectedTab = 0 },
                        icon = Icons.Outlined.Wifi,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Via Bluetooth",
                        onClick = { selectedTab = 1 },
                        icon = Icons.Outlined.Bluetooth,
                        modifier = Modifier.weight(1f),
                    )
                }

                FormField(
                    label = "New Site Wi-Fi SSID (2.4 GHz)",
                    value = wifiUi.ssid,
                    onValueChange = onSsidChange,
                    helper = "e.g. SiteB-Safety-WiFi",
                    imeAction = ImeAction.Next,
                )

                FormField(
                    label = "New Wi-Fi Password",
                    value = wifiUi.pass,
                    onValueChange = onPassChange,
                    helper = "Enter Wi-Fi password",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                )

                if (selectedTab == 0) {
                    Text(
                        text = if (vest.online) {
                            "Since this vest is currently Online, it will receive the new Wi-Fi credentials immediately over Cloud, save them to Flash, and switch networks (or keep them ready for the next site)."
                        } else {
                            "This vest is currently Offline. Sending via Cloud will queue the new Wi-Fi credentials for the next time it connects, or use 'Via Bluetooth' if the vest is nearby."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    PrimaryButton(
                        text = if (wifiUi.sendingCloud) "Sending to Vest..." else "Save & Switch Wi-Fi via Cloud",
                        onClick = onUpdateViaCloud,
                        loading = wifiUi.sendingCloud,
                        enabled = !wifiUi.sendingCloud,
                    )
                } else {
                    Text(
                        text = "Use Bluetooth when the vest is at a new site without Wi-Fi, or switch an online vest into Bluetooth Pairing Mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (vest.online && wifiUi.connectedBle == null) {
                        SecondaryButton(
                            text = "Switch Online Vest to Bluetooth Mode",
                            onClick = {
                                permissionLauncher.launch(permissionsToRequest)
                                onTriggerRemoteBleAndScan()
                            },
                            icon = Icons.Outlined.Bluetooth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (wifiUi.connectedBle == null) {
                        SecondaryButton(
                            text = if (wifiUi.scanningBle) "Scanning for Vest..." else "Scan Nearby Bluetooth Vests",
                            onClick = { permissionLauncher.launch(permissionsToRequest) },
                            icon = Icons.Outlined.Bluetooth,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val cleanTargetMac = vest.macAddress?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()
                        wifiUi.discoveredBles.forEach { dev ->
                            val isThisVest = !cleanTargetMac.isNullOrBlank() && dev.cleanMac.equals(cleanTargetMac, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(BevestShapes.inner)
                                    .clickable { onConnectBle(dev) },
                                shape = BevestShapes.inner,
                                border = BorderStroke(
                                    1.dp,
                                    if (isThisVest) palette.normal else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    Icon(
                                        Icons.Outlined.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isThisVest) palette.normal else MaterialTheme.colorScheme.primary,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = if (isThisVest) "${dev.name} (Matches ${vest.vestId})" else dev.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "MAC: ${dev.address} · RSSI: ${dev.rssi} dBm",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = "Connect",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = BevestShapes.inner,
                            color = palette.normal.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, palette.normal),
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = palette.normal)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Connected to ${wifiUi.connectedBle.name}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    wifiUi.bleStatusMsg?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        PrimaryButton(
                            text = if (wifiUi.bleConnectionState == BleConnectionState.TRANSMITTING_WIFI) {
                                "Transmitting over BLE..."
                            } else {
                                "Send New Wi-Fi over Bluetooth"
                            },
                            onClick = onSendOverBle,
                            loading = wifiUi.bleConnectionState == BleConnectionState.TRANSMITTING_WIFI,
                            enabled = wifiUi.bleConnectionState != BleConnectionState.TRANSMITTING_WIFI,
                        )
                    }
                }

                wifiUi.bleStatusMsg?.takeIf { selectedTab == 1 && wifiUi.connectedBle == null }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                wifiUi.cloudSuccessMsg?.let { SuccessNote(it) }
                if (wifiUi.bleConnectionState == BleConnectionState.SUCCESS_CONFIRMED) {
                    SuccessNote("Vest confirmed new Wi-Fi connection and resumed live streaming!")
                }
                wifiUi.errorMsg?.let { ErrorNote(it) }

                SecondaryButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ------------------------------------------------------------------- pair

/**
 * Reconstructed Add & Pair Vest Flow:
 * 1. Request runtime Bluetooth Scan, Connect & Location permissions
 * 2. Look for BLE signals nearby from Bevest smart vests
 * 3. Connect over Bluetooth Low Energy GATT
 * 4. Scan the physical QR code on the vest
 * 5. Triple validation (Hardware MAC match + Database validation)
 * 6. Wi-Fi setup & transmission over BLE
 * 7. Assign worker (optional)
 * 8. Instant Admin visibility in System Management
 */
@Composable
fun PairVestScreen(
    user: User,
    onDone: () -> Unit,
    viewModel: PairVestViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(user) { viewModel.bind(user) }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnectIfNotFinished()
        }
    }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    val requiredBlePermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
    }

    var hasBlePermissions by remember {
        mutableStateOf(
            requiredBlePermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsMap ->
            val allGranted = permissionsMap.values.all { it }
            hasBlePermissions = allGranted
            if (allGranted) {
                viewModel.startBleScan()
            }
        }
    )

    LaunchedEffect(hasBlePermissions) {
        if (!hasBlePermissions) {
            blePermissionLauncher.launch(requiredBlePermissions)
        } else {
            viewModel.startBleScan()
        }
    }

    val stepIndex = when (state.step) {
        PairStep.LOOK_FOR_BLE -> 0
        PairStep.BLE_CONNECTED -> 1
        PairStep.SCAN_QR -> 2
        PairStep.PROVISION_WIFI -> 3
        PairStep.SELECT_WORKER -> 4
        PairStep.CONNECTION_TEST -> 4
        PairStep.DONE -> 4
    }

    val (stepTitle, stepSubtitle) = when (state.step) {
        PairStep.LOOK_FOR_BLE -> "Search for Vest" to "Looking for nearby Bevest smart vests..."
        PairStep.BLE_CONNECTED -> "Vest Connected" to (state.connectedBle?.name ?: "Connected")
        PairStep.SCAN_QR -> "Scan Hardware QR Code" to "Authenticate hardware identity"
        PairStep.PROVISION_WIFI -> "Site Wi-Fi Setup (Optional)" to "Configure wireless telemetry stream"
        PairStep.SELECT_WORKER -> "Assign Worker (Optional)" to "Assign vest to a site worker"
        PairStep.CONNECTION_TEST -> "Sensor Verification" to "Confirm sensor reporting"
        PairStep.DONE -> "Registration Complete" to "Active in Company Directory"
    }

    BevestScaffold(
        title = "Add & Pair Vest",
        subtitle = "Step ${stepIndex + 1} of 5 · $stepSubtitle",
                onBack = if (state.step == PairStep.LOOK_FOR_BLE || state.step == PairStep.DONE) {
            {
                viewModel.disconnectIfNotFinished()
                onDone()
            }
        } else {
            viewModel::back
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StepProgress(current = stepIndex, total = 5)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.lg)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(stepTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                when (state.step) {
                    // ── 1. Search for Vest ──────────────────────────────────────
                    PairStep.LOOK_FOR_BLE -> {
                        if (!hasBlePermissions) {
                            Surface(
                                shape = BevestShapes.card,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    Modifier.padding(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Bluetooth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            "Bluetooth & Location Permissions Required",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        "Android requires Bluetooth Scan, Connect, and Location permissions to discover and pair with nearby BeVest smart vests.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    PrimaryButton(
                                        text = "Allow Required Permissions",
                                        onClick = { blePermissionLauncher.launch(requiredBlePermissions) }
                                    )
                                }
                            }
                        }

                        if (!viewModel.isBluetoothEnabled) {
                            Surface(
                                shape = BevestShapes.card,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    Modifier.padding(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Text(
                                        "Bluetooth is Turned Off",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Please enable Bluetooth on your phone so the app can detect your smart vest.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    SecondaryButton(
                                        text = "Open Bluetooth Settings",
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                            } catch (_: Exception) {}
                                        }
                                    )
                                }
                            }
                        }

                        if (!viewModel.isLocationEnabled) {
                            Surface(
                                shape = BevestShapes.card,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    Modifier.padding(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Text(
                                        "Device Location is Turned Off",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Android requires Location Services to be active in phone quick settings to detect nearby vest signals.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    SecondaryButton(
                                        text = "Open Location Settings",
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                            } catch (_: Exception) {}
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            "Hold your phone near the Bevest smart vest with power turned ON. The app will scan for nearby Vest signals.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // ── Scanner Status Banner ──
                        Surface(
                            shape = BevestShapes.card,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(BevestShapes.round)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(IconSize.medium)
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (state.scanningBle) "Searching for nearby vests…" else "Search paused",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        state.bleStatusMsg ?: "Listening for Bevest vest signals",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // ── Discovered Vests List ──
                        if (state.discoveredBles.isEmpty()) {
                            Surface(
                                shape = BevestShapes.card,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.xl),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Text(
                                        "No vests found yet nearby",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Ensure your Bevest smart vest is powered ON and in pairing mode (onboard LED blinking).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Text(
                                "Discovered Smart Vests (${state.discoveredBles.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            state.discoveredBles.forEach { vest ->
                                val deviceDisplayName = vest.name.ifBlank { "BEVEST-${vest.cleanMac.ifBlank { "SMART-VEST" }}" }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(BevestShapes.card)
                                        .clickable { viewModel.connectToBleVest(vest) },
                                    shape = BevestShapes.card,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    shadowElevation = Elevation.card,
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(46.dp)
                                                    .clip(BevestShapes.inner)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Bluetooth,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(IconSize.medium)
                                                )
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = deviceDisplayName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "MAC: ${vest.address}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        PrimaryButton(
                                            text = "Connect to $deviceDisplayName",
                                            onClick = { viewModel.connectToBleVest(vest) },
                                            icon = Icons.Outlined.Bluetooth
                                        )
                                    }
                                }
                            }
                        }

                        SecondaryButton(
                            text = if (state.scanningBle) "Finding Vest…" else "Find Vest",
                            onClick = {
                                if (!hasBlePermissions) {
                                    blePermissionLauncher.launch(requiredBlePermissions)
                                } else {
                                    viewModel.startBleScan()
                                }
                            },
                            icon = BevestIcons.Refresh
                        )
                    }

                    // ── 2. Bluetooth Connected ──────────────────────────────────
                    PairStep.BLE_CONNECTED -> {
                        val deviceName = state.connectedBle?.name?.takeIf { it.isNotBlank() }
                            ?: "BEVEST-${state.connectedBle?.cleanMac ?: "SMART-VEST"}"
                        val deviceMac = state.connectedBle?.address ?: "N/A"

                        Surface(
                            shape = BevestShapes.card,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, LocalStatusPalette.current.normal.copy(alpha = 0.5f)),
                            shadowElevation = Elevation.card,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xl),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .clip(BevestShapes.round)
                                        .background(LocalStatusPalette.current.normal.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = LocalStatusPalette.current.normal,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Text(
                                    "Connected to $deviceName!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    "Connection Established",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Surface(
                                    shape = BevestShapes.pill,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = "Hardware MAC: $deviceMac",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                                    )
                                }

                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    "Next: Scan the physical QR code on the vest to verify hardware authenticity and add it to your company directory.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        PrimaryButton(
                            text = "Proceed to Scan QR Code",
                            onClick = viewModel::proceedToQrScan,
                            icon = BevestIcons.Scan
                        )
                        SecondaryButton(
                            text = "Disconnect & Choose Another Vest",
                            onClick = viewModel::disconnectAndRescan,
                            icon = BevestIcons.Refresh
                        )
                    }

                    // ── 3. Scan Physical QR Code ────────────────────────────────
                    PairStep.SCAN_QR -> {
                        Text(
                            "Point your camera at the QR code printed on the physical vest. The system validates the Vest ID, Serial Number and MAC Address against both the Bluetooth connection and the Bevest database.",
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
                            label = "Vest ID (Manual fallback)",
                            error = state.vestIdError,
                            helper = "Printed under the QR code",
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Go,
                            onImeAction = viewModel::manualValidateAndClaim,
                        )

                        PrimaryButton(
                            text = "Verify & Claim Vest",
                            onClick = viewModel::manualValidateAndClaim,
                            loading = state.checking,
                            loadingText = "Validating hardware…",
                        )
                        SecondaryButton(
                            text = "Cancel & Choose Another Vest",
                            onClick = viewModel::disconnectAndRescan,
                            icon = BevestIcons.Refresh
                        )
                    }

                    // ── 4. Wi-Fi Provisioning ───────────────────────────────────
                    PairStep.PROVISION_WIFI -> {
                        state.successMessage?.let { SuccessNote(it) }

                        Text(
                            "Configure the construction site Wi-Fi connection so the vest can transmit live telemetry to the Bevest Cloud.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        FormField(
                            value = state.wifiSsid,
                            onValueChange = viewModel::onWifiSsidInput,
                            label = "Site Wi-Fi Network Name (SSID)",
                            helper = "e.g. Site-Office-WiFi",
                            keyboardType = KeyboardType.Text,
                        )

                        FormField(
                            value = state.wifiPass,
                            onValueChange = viewModel::onWifiPassInput,
                            label = "Wi-Fi Password",
                            keyboardType = KeyboardType.Password,
                        )

                        if (state.wifiProvisionSuccess) {
                            SuccessNote("✓ Wi-Fi credentials sent! Vest confirmed connection.")
                            PrimaryButton(
                                text = "Continue to Worker Assignment",
                                onClick = viewModel::skipWifiAndProceed
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                SecondaryButton(
                                    text = "Skip Wi-Fi Setup",
                                    onClick = viewModel::skipWifiAndProceed,
                                    modifier = Modifier.weight(1f)
                                )
                                PrimaryButton(
                                    text = "Send Wi-Fi",
                                    onClick = viewModel::sendWifiCredentials,
                                    loading = state.provisioningWifi,
                                    loadingText = "Transmitting…",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // ── 5. Select Worker ────────────────────────────────────────
                    PairStep.SELECT_WORKER -> {
                        Text(
                            "Assign vest ${state.identifiedVestId ?: state.vestIdInput} to a worker now, or keep it available in the company directory:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        SecondaryButton(
                            text = "Save as Available (No Worker Assigned)",
                            onClick = viewModel::finishWithoutWorker,
                            icon = BevestIcons.Vests,
                        )

                        if (state.unassignedWorkers.isNotEmpty()) {
                            Text(
                                "Or assign immediately to an active worker:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
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
                    }

                    // ── 6. Sensor Connection Test ───────────────────────────────
                    PairStep.CONNECTION_TEST -> {
                        Text(
                            "Reading telemetry from vest ${state.identifiedVestId ?: ""} to verify sensor reporting.",
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
                                text = "Confirm & Finish Pairing",
                                onClick = viewModel::confirmWorkerPairing,
                                enabled = state.checks.allPass,
                                loading = state.submitting,
                                loadingText = "Saving…",
                            )
                        }
                    }

                    // ── 7. Done / Success Screen ────────────────────────────────
                    PairStep.DONE -> {
                        Surface(
                            shape = BevestShapes.card,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = LocalStatusPalette.current.normal,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    "✓ Vest Added to Company Directory!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Smart Vest ${state.identifiedVestId ?: state.vestIdInput} is now active. Your company Administrator can now immediately see and manage this vest under System Management.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        PrimaryButton(
                            text = "Back to Vest Directory",
                            onClick = onDone
                        )
                    }
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
