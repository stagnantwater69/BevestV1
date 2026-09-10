package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.DangerButton
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.LastUpdatedLabel
import com.jtexpress.bevest.ui.common.LivePulse
import com.jtexpress.bevest.ui.common.MetricCard
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatusChip
import com.jtexpress.bevest.ui.common.SuccessNote
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

/**
 * One worker, live.
 *
 * The identity header states the verdict in words under the name, so the officer knows
 * what they are looking at before parsing a single reading. Below that the readings run
 * in the order the safety engine actually weighs them — heart rate, temperature, motion —
 * with the emergency control pinned at the bottom where a thumb reaches.
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
                val elevated = state.status == SafetyStatus.WARNING ||
                    state.status == SafetyStatus.DANGER ||
                    state.status == SafetyStatus.EMERGENCY

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
                            "Last known values — the vest has stopped reporting"
                        } else {
                            "Updating as the vest reports"
                        },
                    )

                    MetricCard(
                        icon = BevestIcons.HeartRate,
                        label = "Heart rate",
                        value = reading?.heartRate?.toString() ?: "—",
                        unit = reading?.heartRate?.let { "BPM" },
                        accent = if (elevated) palette.warning else palette.normal,
                        stale = state.stale,
                        // The trend is the diagnosis; the number alone is not.
                        trace = state.heartRateHistory.takeIf { it.size > 1 },
                    )
                    MetricCard(
                        icon = BevestIcons.Temperature,
                        label = "Body temperature",
                        value = reading?.temperature?.let { "%.1f".format(it) } ?: "—",
                        unit = reading?.temperature?.let { "°C" },
                        accent = if (elevated) palette.warning else palette.normal,
                        stale = state.stale,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        MetricCard(
                            icon = if ((reading?.batteryPercent ?: 100) <= 20) {
                                BevestIcons.BatteryLow
                            } else {
                                BevestIcons.Battery
                            },
                            label = "Battery",
                            value = reading?.batteryPercent?.toString() ?: "—",
                            unit = reading?.batteryPercent?.let { "%" },
                            accent = if ((reading?.batteryPercent ?: 100) <= 20) {
                                palette.warning
                            } else {
                                null
                            },
                            stale = state.stale,
                            supporting = if ((reading?.batteryPercent ?: 100) <= 20) {
                                "Needs charging soon"
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    val response = reading?.safetyResponse ?: SafetyResponseState.NONE
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
                        stale = state.stale,
                    )

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
                            "Alerts the vest and starts the acknowledgement countdown.",
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
            message = "This alerts the worker's vest and starts the acknowledgement countdown.",
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
 * Who this is and how they are.
 *
 * Carries the verdict sentence from [SafetyStatus.detail] under the status chip. The
 * chip alone says "DANGER"; the sentence says what that means and what to do, which is
 * the difference between a label and an instruction.
 */
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
                Modifier.padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                WorkerAvatar(
                    name = name,
                    photoUrl = photoUrl,
                    status = status,
                    size = 64.dp,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                StatusChip(status)
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
