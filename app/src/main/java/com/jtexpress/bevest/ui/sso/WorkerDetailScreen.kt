package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.LastUpdatedLabel
import com.jtexpress.bevest.ui.common.MetricCard
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatusChip
import com.jtexpress.bevest.ui.common.WorkerAvatar
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing

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
    val haptics = LocalHapticFeedback.current
    var confirmResponse by remember { mutableStateOf(false) }

    BevestScaffold(
        title = state.worker?.fullName?.ifBlank { "Worker" } ?: "Worker",
        subtitle = state.worker?.let { "Worker ${it.workerId}" },
        onBack = onBack,
        actions = {
            if (canControl && state.worker != null) {
                IconButton(onClick = { onEdit(workerId) }) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit worker",
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> DetailSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.worker == null -> ErrorState("That worker no longer exists.", modifier = Modifier.padding(padding))
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
                    // ---- Identity header ----
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            WorkerAvatar(
                                name = worker.fullName.ifBlank { worker.workerId },
                                photoUrl = worker.photoUrl,
                                status = state.status,
                                size = 64.dp,
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    worker.fullName.ifBlank { worker.workerId },
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
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        worker.assignedVestId ?: "No vest assigned",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                LastUpdatedLabel(
                                    reading?.timestamp?.takeIf { it > 0 },
                                    state.stale,
                                )
                            }
                            StatusChip(state.status)
                        }
                    }

                    // ---- Live vitals ----
                    SectionHeader("Live readings")

                    MetricCard(
                        icon = BevestIcons.HeartRate,
                        label = "Heart rate",
                        value = reading?.heartRate?.toString() ?: "—",
                        unit = reading?.heartRate?.let { "BPM" },
                        accent = if (state.status == SafetyStatus.WARNING ||
                            state.status == SafetyStatus.DANGER
                        ) palette.warning else palette.normal,
                        stale = state.stale,
                    )
                    MetricCard(
                        icon = BevestIcons.Temperature,
                        label = "Body temperature",
                        value = reading?.temperature?.let { "%.1f".format(it) } ?: "—",
                        unit = reading?.temperature?.let { "°C" },
                        accent = if (state.status == SafetyStatus.WARNING ||
                            state.status == SafetyStatus.DANGER
                        ) palette.warning else palette.normal,
                        stale = state.stale,
                    )
                    MetricCard(
                        icon = BevestIcons.forMotion(reading?.motionState ?: MotionState.UNKNOWN),
                        label = "Motion",
                        value = (reading?.motionState ?: MotionState.UNKNOWN).name.replace('_', ' '),
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
                            accent = if ((reading?.batteryPercent ?: 100) <= 20) palette.warning else null,
                            stale = state.stale,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    MetricCard(
                        icon = BevestIcons.Signal,
                        label = "Safety response",
                        value = (reading?.safetyResponse?.name ?: "NONE").replace('_', ' '),
                        accent = when (reading?.safetyResponse?.name) {
                            "ACKNOWLEDGED" -> palette.normal
                            "WAITING" -> palette.warning
                            "NO_RESPONSE", "EMERGENCY_REQUESTED", "ESCALATED" -> palette.danger
                            else -> null
                        },
                        stale = state.stale,
                    )

                    // ---- Location ----
                    SectionHeader("Location")
                    LocationCard(reading, state.stale)

                    // ---- Emergency control ----
                    if (canControl) {
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                confirmResponse = true
                            },
                            shape = RoundedCornerShape(Radius.md),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.danger,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(top = Spacing.sm),
                        ) {
                            Icon(
                                BevestIcons.forStatus(SafetyStatus.DANGER),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                "Initiate safety response",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }

                    state.actionMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(Spacing.md),
                            )
                        }
                    }
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

@Composable
private fun LocationCard(reading: SensorReading?, stale: Boolean) {
    val palette = LocalStatusPalette.current
    val lat = reading?.latitude
    val lng = reading?.longitude

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
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
                        .clip(RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md)),
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
                        Text("COORDINATES", style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.5f, %.5f".format(lat, lng), style = MaterialTheme.typography.bodyMedium)
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
                    Text(
                        "No GPS fix",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
