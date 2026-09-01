package com.jtexpress.bevest.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.VestStatus

/**
 * One icon per concept, used everywhere that concept appears. Safety status is never
 * communicated by color alone (plan section 23) — these icons carry the same meaning.
 */
object BevestIcons {

    // Navigation
    val Dashboard: ImageVector = Icons.Outlined.Dashboard
    val Workers: ImageVector = Icons.Outlined.Groups
    val Map: ImageVector = Icons.Outlined.Map
    val Alerts: ImageVector = Icons.Outlined.NotificationsActive
    val Vests: ImageVector = Icons.Outlined.Shield
    val Profile: ImageVector = Icons.Outlined.AccountCircle
    val Contractors: ImageVector = Icons.Outlined.Business
    val System: ImageVector = Icons.Outlined.SettingsSuggest
    val Reports: ImageVector = Icons.Outlined.Assessment

    // Sensors
    val HeartRate: ImageVector = Icons.Outlined.Favorite
    val Temperature: ImageVector = Icons.Outlined.DeviceThermostat
    val Motion: ImageVector = Icons.Outlined.DirectionsRun
    val Location: ImageVector = Icons.Outlined.LocationOn
    val Battery: ImageVector = Icons.Outlined.BatteryFull
    val BatteryLow: ImageVector = Icons.Outlined.BatteryAlert
    val Signal: ImageVector = Icons.Outlined.Sensors
    val NoSignal: ImageVector = Icons.Outlined.SignalWifiOff

    // Actions
    val Add: ImageVector = Icons.Outlined.Add
    val Scan: ImageVector = Icons.Outlined.QrCodeScanner
    val Refresh: ImageVector = Icons.Outlined.Refresh
    val Simulator: ImageVector = Icons.Outlined.Build
    val History: ImageVector = Icons.Outlined.History
    val Timer: ImageVector = Icons.Outlined.Timer
    val LastSeen: ImageVector = Icons.Outlined.AccessTime
    val WorkerId: ImageVector = Icons.Outlined.Badge
    val Officer: ImageVector = Icons.Outlined.Engineering

    // Empty / error states
    val EmptyInbox: ImageVector = Icons.Outlined.Inbox
    val NoResults: ImageVector = Icons.Outlined.SearchOff
    val NoWorkers: ImageVector = Icons.Outlined.PersonSearch
    val Offline: ImageVector = Icons.Outlined.CloudOff
    val Error: ImageVector = Icons.Outlined.ErrorOutline

    fun forStatus(status: SafetyStatus): ImageVector = when (status) {
        SafetyStatus.NORMAL -> Icons.Outlined.CheckCircle
        SafetyStatus.WARNING -> Icons.Outlined.WarningAmber
        SafetyStatus.DANGER -> Icons.Filled.Warning
        SafetyStatus.EMERGENCY -> Icons.Filled.Warning
        SafetyStatus.OFFLINE -> Icons.Outlined.CloudOff
    }

    fun forMotion(state: MotionState): ImageVector = when (state) {
        MotionState.MOVING -> Icons.Outlined.DirectionsRun
        MotionState.FALL_DETECTED -> Icons.Filled.Warning
        MotionState.INACTIVE -> Icons.Outlined.Timer
        else -> Icons.Outlined.Sensors
    }

    fun forVestStatus(status: VestStatus): ImageVector = when (status) {
        VestStatus.ACTIVE, VestStatus.ASSIGNED -> Icons.Outlined.CheckCircle
        VestStatus.AVAILABLE -> Icons.Outlined.Shield
        VestStatus.MAINTENANCE -> Icons.Outlined.Build
        VestStatus.OFFLINE -> Icons.Outlined.CloudOff
    }

    fun forAlertType(type: AlertType): ImageVector = when (type) {
        AlertType.HEART_RATE_WARNING -> Icons.Outlined.Favorite
        AlertType.TEMPERATURE_WARNING -> Icons.Outlined.DeviceThermostat
        AlertType.FALL_DETECTED -> Icons.Filled.Warning
        AlertType.INACTIVITY -> Icons.Outlined.Timer
        AlertType.EMERGENCY_REQUEST -> Icons.Outlined.NotificationsActive
        AlertType.NO_SAFETY_RESPONSE -> Icons.Outlined.Bloodtype
        AlertType.VEST_OFFLINE -> Icons.Outlined.CloudOff
        AlertType.LOW_BATTERY -> Icons.Outlined.BatteryAlert
    }
}
