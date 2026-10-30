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
import com.jtexpress.bevest.data.ble.DiscoveredBleVest
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
import androidx.compose.ui.text.font.FontWeight
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
        PairStep.LOOK_FOR_BLE -> "Search for BLE Signals" to "Looking for nearby Bevest smart vests..."
        PairStep.BLE_CONNECTED -> "Vest Connected via Bluetooth" to (state.connectedBle?.name ?: "Connected")
        PairStep.SCAN_QR -> "Scan Hardware QR Code" to "Authenticate hardware identity"
        PairStep.PROVISION_WIFI -> "Site Wi-Fi Setup (Optional)" to "Configure wireless telemetry stream"
        PairStep.SELECT_WORKER -> "Assign Worker (Optional)" to "Assign vest to a site worker"
        PairStep.CONNECTION_TEST -> "Sensor Verification" to "Confirm sensor reporting"
        PairStep.DONE -> "Registration Complete" to "Active in Company Directory"
    }

    BevestScaffold(
        title = "Add & Pair Vest",
        subtitle = "Step ${stepIndex + 1} of 5 · $stepSubtitle",
        onBack = if (state.step == PairStep.LOOK_FOR_BLE || state.step == PairStep.DONE) onDone else viewModel::back,
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
                    // ── 1. Search for nearby BLE Signals ────────────────────────
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
                                        "Android requires Location Services to be active in phone quick settings to detect nearby BLE signals.",
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
                            "Hold your phone near the Bevest smart vest with power turned ON. The app will scan for nearby BLE advertisement signals.",
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
                                        if (state.scanningBle) "Scanning for Bevest signals…" else "Scan paused",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        state.bleStatusMsg ?: "Listening for BEVEST-* BLE beacons",
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
                                        "Ensure your ESP32 Bevest is powered ON and in pairing mode (onboard LED blinking).",
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
                                val signalColor = if (vest.rssi >= -70) LocalStatusPalette.current.normal else LocalStatusPalette.current.warning
                                val signalText = if (vest.rssi >= -65) "Strong" else "Nearby"

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
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
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

                                            StatusBadge(
                                                text = "$signalText (${vest.rssi} dBm)",
                                                color = signalColor,
                                                icon = BevestIcons.Signal
                                            )
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
                            text = if (state.scanningBle) "Scanning…" else "Rescan for Signals",
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
                                    "Bluetooth Low Energy GATT connection established.",
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
