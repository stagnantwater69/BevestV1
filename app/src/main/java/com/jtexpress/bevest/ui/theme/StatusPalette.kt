package com.jtexpress.bevest.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.jtexpress.bevest.domain.model.SafetyStatus

/**
 * Named colors for safety states. Access via [LocalStatusPalette] so screens never
 * hard-code status colors (see plan section 31.8 / section 23).
 */
@Immutable
data class StatusPalette(
    val normal: Color,
    val warning: Color,
    val danger: Color,
    val emergency: Color,
    val offline: Color,
) {
    fun forStatus(status: SafetyStatus): Color = when (status) {
        SafetyStatus.NORMAL -> normal
        SafetyStatus.WARNING -> warning
        SafetyStatus.DANGER -> danger
        SafetyStatus.EMERGENCY -> emergency
        SafetyStatus.OFFLINE -> offline
    }
}

val LightStatusPalette = StatusPalette(
    normal = StatusNormalLight,
    warning = StatusWarningLight,
    danger = StatusDangerLight,
    emergency = StatusEmergencyLight,
    offline = StatusOfflineLight,
)

val DarkStatusPalette = StatusPalette(
    normal = StatusNormalDark,
    warning = StatusWarningDark,
    danger = StatusDangerDark,
    emergency = StatusEmergencyDark,
    offline = StatusOfflineDark,
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }
