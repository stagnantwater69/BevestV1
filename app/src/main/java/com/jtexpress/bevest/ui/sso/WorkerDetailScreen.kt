package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.VitalLogEntry
import com.jtexpress.bevest.domain.safety.AgeVitalThreshold
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.DangerButton
import com.jtexpress.bevest.ui.common.DetailRow
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.LastUpdatedLabel
import com.jtexpress.bevest.ui.common.LivePulse
import com.jtexpress.bevest.ui.common.MetricCard
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatusBadge
import com.jtexpress.bevest.ui.common.StatusChip
import com.jtexpress.bevest.ui.common.SuccessNote
import com.jtexpress.bevest.ui.common.WifiSignalLevel
import com.jtexpress.bevest.ui.common.WorkerAvatar
import com.jtexpress.bevest.ui.common.detail
import com.jtexpress.bevest.ui.common.label
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.Elevation
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.ui.theme.VitalStyle
import com.jtexpress.bevest.utils.DateTimeUtils

/**
 * One worker, live.
 */
@Composable
fun WorkerDetailScreen(
    workerId: String,
    actorId: String,
    canControl: Boolean,
    onBack: () -> Unit,
    onOpenAlert: (String) -> Unit,
    onEdit: (String) -> Unit = {},
    viewModel: WorkerDetailViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current
    var confirmResponse by remember { mutableStateOf(false) }
    var showVitalLogs by remember { mutableStateOf(false) }

    if (showVitalLogs) {
        val logsToShow = remember(state.vitalLogs, state.reading) {
            val r = state.reading
            if (state.vitalLogs.isEmpty() && r != null && (r.heartRate != null || r.temperature != null)) {
                listOf(
                    VitalLogEntry(
                        timestamp = if (r.timestamp > 0L) r.timestamp else System.currentTimeMillis(),
                        heartRate = r.heartRate,
                        temperature = r.temperature,
                    ),
                )
            } else {
                state.vitalLogs
            }
        }
        VitalLogsSheet(
            workerName = state.worker?.fullName?.ifBlank { workerId } ?: workerId,
            ageBracket = state.ageBracket,
            logs = logsToShow,
            onDismiss = { showVitalLogs = false },
        )
    }

    BevestScaffold(
        title = state.worker?.fullName?.ifBlank { "Worker" } ?: "Worker",
        subtitle = state.worker?.let { "Worker ${it.workerId}" },
        onBack = onBack,
        actions = {
            if (canControl && state.worker != null) {
                IconButton(onClick = { onEdit(workerId) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit worker")
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.worker == null -> ErrorState(
                "That worker no longer exists.",
                modifier = Modifier.padding(padding),
            )
            else -> {
                val worker = state.worker
                val reading = state.reading

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // ---- Identity ----
                    IdentityHeader(
                        name = worker.fullName.ifBlank { worker.workerId },
                        photoUrl = worker.photoUrl,
                        vestId = worker.assignedVestId,
                        status = state.status,
                        stale = state.stale,
                        lastUpdate = reading?.timestamp?.takeIf { it > 0 },
                    )

                    // ---- Live vitals ----
                    SectionHeader(
                        title = "Live readings",
                        supporting = if (state.stale) {
                            "Last known values — tap the card below to view 1-min logs"
                        } else {
                            "Updating live — tap the card below to view 1-min logs"
                        },
                    )

                    VitalSignsCombinedCard(
                        heartRate = reading?.heartRate,
                        temperature = reading?.temperature,
                        hrStatus = state.hrStatus,
                        tempStatus = state.tempStatus,
                        age = worker.effectiveAge,
                        ageBracket = state.ageBracket,
                        stale = state.stale,
                        onClick = { showVitalLogs = true },
                    )

                    MetricCard(
                        icon = BevestIcons.forMotion(reading?.motionState ?: MotionState.UNKNOWN),
                        label = "Motion",
                        value = (reading?.motionState ?: MotionState.UNKNOWN).label(),
                        accent = when (reading?.motionState) {
                            MotionState.FALL_DETECTED -> palette.danger
                            MotionState.INACTIVE -> palette.warning
                            MotionState.MOVING -> palette.normal
                            else -> null
                        },
                        stale = state.stale,
                    )

                    // ---- Vest ----
                    SectionHeader("Vest")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        val batteryLow = (reading?.batteryPercent ?: 100) <= 20
                        MetricCard(
                            icon = if (batteryLow) {
                                BevestIcons.BatteryLow
                            } else {
                                BevestIcons.Battery
                            },
                            label = "Battery",
                            value = reading?.batteryPercent?.toString() ?: "—",
                            unit = reading?.batteryPercent?.let { "%" },
                            accent = if (batteryLow) palette.warning else palette.normal,
                            stale = state.stale,
                            supporting = when {
                                state.stale || reading?.batteryPercent == null -> null
                                batteryLow -> "Needs charging soon"
                                else -> "Normal"
                            },
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        val wifiLevel = WifiSignalLevel.fromRssi(reading?.wifiRssi, online = !state.stale && reading != null)
                        MetricCard(
                            icon = wifiLevel.icon(),
                            label = "Wi-Fi Signal",
                            value = if (state.stale || reading == null) "Offline" else (reading.wifiRssi?.toString() ?: "—"),
                            unit = if (!state.stale && reading?.wifiRssi != null) "dBm" else null,
                            accent = if (!state.stale && reading != null) wifiLevel.color(palette) else null,
                            stale = state.stale,
                            supporting = if (!state.stale && reading != null && reading.wifiRssi != null) wifiLevel.label else null,
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }

                    val response = if (state.safetyCountdownSec != null && reading?.safetyResponse != SafetyResponseState.ACKNOWLEDGED) {
                        SafetyResponseState.WAITING
                    } else {
                        reading?.safetyResponse ?: SafetyResponseState.NONE
                    }
                    val hasActiveSafetyResult = response == SafetyResponseState.WAITING ||
                        response == SafetyResponseState.ACKNOWLEDGED ||
                        response == SafetyResponseState.NO_RESPONSE ||
                        response == SafetyResponseState.ESCALATED ||
                        response == SafetyResponseState.EMERGENCY_REQUESTED

                    MetricCard(
                        icon = BevestIcons.Signal,
                        label = "Safety response",
                        value = response.label(),
                        accent = when (response) {
                            SafetyResponseState.ACKNOWLEDGED -> palette.normal
                            SafetyResponseState.WAITING -> palette.warning
                            SafetyResponseState.NO_RESPONSE,
                            SafetyResponseState.EMERGENCY_REQUESTED,
                            SafetyResponseState.ESCALATED,
                            -> palette.danger
                            SafetyResponseState.NONE -> null
                        },
                        stale = state.stale && !hasActiveSafetyResult,
                        supporting = state.safetyCountdownSec?.let { sec ->
                            "Buzzer active on vest — waiting for button press (${sec}s)"
                        },
                    )

                    // ---- Worker Info ----
                    if (!worker.jobPosition.isNullOrBlank() || !worker.birthday.isNullOrBlank() || worker.effectiveAge != null || !worker.floorNumber.isNullOrBlank() || worker.phone.isNotBlank()) {
                        SectionHeader(
                            title = "Worker details",
                            supporting = "Profile and work assignment information",
                        )
                        if (!worker.jobPosition.isNullOrBlank()) {
                            DetailRow(
                                label = "Job position",
                                value = worker.jobPosition,
                                icon = Icons.Outlined.WorkOutline,
                            )
                        }
                        if (worker.phone.isNotBlank()) {
                            DetailRow(
                                label = "Phone",
                                value = worker.phone,
                                icon = Icons.Outlined.Phone,
                            )
                        }
                        if (!worker.birthday.isNullOrBlank()) {
                            DetailRow(
                                label = "Birthdate",
                                value = worker.birthday,
                                icon = Icons.Outlined.Cake,
                            )
                        }
                        worker.effectiveAge?.let { age ->
                            DetailRow(
                                label = "Age",
                                value = "$age years old",
                                icon = Icons.Outlined.Badge,
                            )
                        }
                        if (!worker.floorNumber.isNullOrBlank()) {
                            DetailRow(
                                label = "Floor number",
                                value = worker.floorNumber,
                                icon = Icons.Outlined.Apartment,
                            )
                        }
                    }

                    // ---- Location ----
                    SectionHeader("Location")
                    LocationCard(reading, state.stale)

                    // ---- Emergency control ----
                    if (canControl) {
                        Spacer(Modifier.height(Spacing.xs))
                        DangerButton(
                            text = "Initiate safety response",
                            onClick = { confirmResponse = true },
                            icon = BevestIcons.forStatus(SafetyStatus.DANGER),
                            filled = true,
                        )
                        Text(
                            "Triggers the vest buzzer for 15 seconds and waits for the worker to press the button.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.actionMessage?.let { SuccessNote(it) }
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
        }
    }

    if (confirmResponse) {
        ConfirmationDialog(
            title = "Start safety response?",
            message = "This triggers the buzzer on the worker's vest for 15 seconds and waits for the worker to respond by clicking the button.",
            confirmLabel = "Start",
            onConfirm = {
                confirmResponse = false
                viewModel.initiateSafetyResponse(actorId)
            },
            onDismiss = { confirmResponse = false },
        )
    }
}

/**
 * Combined single card for Heart Rate and Body Temperature.
 * Shows individual yellow Warning and red Danger highlights based on the worker's age thresholds,
 * and opens the 1-minute Heart Rate & Body Temperature logs when clicked.
 */
@Composable
private fun VitalSignsCombinedCard(
    heartRate: Int?,
    temperature: Double?,
    hrStatus: SafetyStatus,
    tempStatus: SafetyStatus,
    age: Int?,
    ageBracket: AgeVitalThreshold?,
    stale: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalStatusPalette.current
    val hrColor = when (hrStatus) {
        SafetyStatus.DANGER, SafetyStatus.EMERGENCY -> palette.danger
        SafetyStatus.WARNING -> palette.warning
        else -> palette.normal
    }
    val tempColor = when (tempStatus) {
        SafetyStatus.DANGER, SafetyStatus.EMERGENCY -> palette.danger
        SafetyStatus.WARNING -> palette.warning
        else -> palette.normal
    }
    val worstVitalStatus = when {
        hrStatus == SafetyStatus.DANGER || tempStatus == SafetyStatus.DANGER -> SafetyStatus.DANGER
        hrStatus == SafetyStatus.WARNING || tempStatus == SafetyStatus.WARNING -> SafetyStatus.WARNING
        else -> SafetyStatus.NORMAL
    }
    val borderColor = when {
        stale -> MaterialTheme.colorScheme.outlineVariant
        worstVitalStatus == SafetyStatus.DANGER -> palette.danger.copy(alpha = 0.65f)
        worstVitalStatus == SafetyStatus.WARNING -> palette.warning.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (stale) 0.55f else 1f)
            .clip(BevestShapes.card)
            .clickable(onClick = onClick),
        shape = BevestShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (!stale && worstVitalStatus != SafetyStatus.NORMAL) 1.5.dp else 1.dp,
            color = borderColor,
        ),
        shadowElevation = Elevation.card,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "HEART RATE & BODY TEMPERATURE",
                    style = EyebrowStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!stale && worstVitalStatus != SafetyStatus.NORMAL) {
                    StatusBadge(
                        text = if (worstVitalStatus == SafetyStatus.DANGER) "Danger" else "Warning",
                        color = if (worstVitalStatus == SafetyStatus.DANGER) palette.danger else palette.warning,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left column: Heart Rate
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = BevestIcons.HeartRate,
                            contentDescription = null,
                            tint = if (stale) palette.offline else hrColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Heart rate",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = heartRate?.toString() ?: "—",
                            style = VitalStyle,
                            color = if (!stale && hrStatus != SafetyStatus.NORMAL) {
                                hrColor
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (heartRate != null) {
                            Text(
                                text = " BPM",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (!stale && hrStatus != SafetyStatus.NORMAL) {
                                    hrColor
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                    if (!stale && hrStatus != SafetyStatus.NORMAL) {
                        Text(
                            text = if (hrStatus == SafetyStatus.DANGER) "Danger threshold reached" else "Warning threshold reached",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = hrColor,
                        )
                    } else if (ageBracket != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "Warn ≥${ageBracket.hrWarning}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = "Danger ≥${ageBracket.hrDanger}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                )

                // Right column: Body Temperature
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = BevestIcons.Temperature,
                            contentDescription = null,
                            tint = if (stale) palette.offline else tempColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Body temp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = temperature?.let { "%.1f".format(it) } ?: "—",
                            style = VitalStyle,
                            color = if (!stale && tempStatus != SafetyStatus.NORMAL) {
                                tempColor
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (temperature != null) {
                            Text(
                                text = " °C",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (!stale && tempStatus != SafetyStatus.NORMAL) {
                                    tempColor
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                    if (!stale && tempStatus != SafetyStatus.NORMAL) {
                        Text(
                            text = if (tempStatus == SafetyStatus.DANGER) "Danger threshold reached" else "Warning threshold reached",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tempColor,
                        )
                    } else if (ageBracket != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "Warn ≥${"%.1f".format(ageBracket.tempWarning)}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = "Danger ≥${"%.1f".format(ageBracket.tempDanger)}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (stale) "Last known reading · Tap for 1-minute logs" else "Tap to view 1-minute Heart Rate & Body Temp logs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VitalLogsSheet(
    workerName: String,
    ageBracket: AgeVitalThreshold?,
    logs: List<VitalLogEntry>,
    onDismiss: () -> Unit,
) {
    val palette = LocalStatusPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionHeader(
                title = "Heart Rate & Body Temp Logs",
                supporting = "$workerName · Recorded every 1 minute",
            )

            if (logs.isEmpty()) {
                EmptyState(
                    title = "No 1-minute logs yet",
                    message = "Heart rate and body temperature logs are recorded automatically every minute while the vest is online.",
                    icon = BevestIcons.HeartRate,
                    modifier = Modifier.heightIn(min = 180.dp),
                )
                Spacer(Modifier.height(Spacing.xl))
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "TIME",
                        style = EyebrowStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.3f),
                    )
                    Text(
                        "HEART RATE",
                        style = EyebrowStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "BODY TEMP",
                        style = EyebrowStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    contentPadding = PaddingValues(bottom = Spacing.xxl),
                ) {
                    items(logs, key = { it.timestamp }) { entry ->
                        val hrTint = when {
                            entry.heartRate == null || ageBracket == null -> palette.normal
                            entry.heartRate >= ageBracket.hrDanger -> palette.danger
                            entry.heartRate >= ageBracket.hrWarning -> palette.warning
                            else -> palette.normal
                        }
                        val tempTint = when {
                            entry.temperature == null || ageBracket == null -> palette.normal
                            entry.temperature >= ageBracket.tempDanger -> palette.danger
                            entry.temperature >= ageBracket.tempWarning -> palette.warning
                            else -> palette.normal
                        }
                        Surface(
                            shape = BevestShapes.card,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1.3f)) {
                                    Text(
                                        DateTimeUtils.timeOfDay(entry.timestamp),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        DateTimeUtils.dayLabel(entry.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        BevestIcons.HeartRate,
                                        contentDescription = null,
                                        tint = hrTint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        entry.heartRate?.let { "$it BPM" } ?: "—",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (hrTint != palette.normal) hrTint else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        BevestIcons.Temperature,
                                        contentDescription = null,
                                        tint = tempTint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        entry.temperature?.let { "%.1f °C".format(it) } ?: "—",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (tempTint != palette.normal) tempTint else MaterialTheme.colorScheme.onSurface,
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
private fun IdentityHeader(
    name: String,
    photoUrl: String?,
    vestId: String?,
    status: SafetyStatus,
    stale: Boolean,
    lastUpdate: Long?,
) {
    val palette = LocalStatusPalette.current
    val accent = palette.forStatus(status)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BevestShapes.status,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shadowElevation = Elevation.card,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                WorkerAvatar(
                    name = name,
                    photoUrl = photoUrl,
                    status = status,
                    size = 56.dp,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        StatusChip(status, compact = true)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            BevestIcons.Vests,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.inline),
                        )
                        Text(
                            vestId ?: "No vest assigned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (!stale) LivePulse(color = accent, size = 5.dp)
                        LastUpdatedLabel(lastUpdate, stale)
                    }
                }
            }
            ReflectiveBand(thickness = 2.dp, emphasis = 0.7f)
            Text(
                status.detail(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

@Composable
private fun LocationCard(reading: SensorReading?, stale: Boolean) {
    val palette = LocalStatusPalette.current
    val lat = reading?.latitude
    val lng = reading?.longitude

    Surface(
        shape = BevestShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (lat != null && lng != null) {
                val position = LatLng(lat, lng)
                val cameraPositionState = rememberCameraPositionState {
                    this.position = CameraPosition.fromLatLngZoom(position, 16f)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg)),
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false),
                    ) {
                        Marker(state = MarkerState(position = position), title = "Worker")
                    }
                }
                Row(
                    Modifier.padding(Spacing.lg).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "COORDINATES",
                            style = EyebrowStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "%.5f, %.5f".format(lat, lng),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (stale) {
                        Text(
                            "Last known",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.offline,
                        )
                    }
                }
            } else {
                Row(
                    Modifier.padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(
                        BevestIcons.Location,
                        contentDescription = null,
                        tint = palette.offline,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            "No GPS fix",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "The vest hasn't reported a position yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
