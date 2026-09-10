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

/**
 * Colors for the app's signature devices, kept apart from [StatusPalette] because they
 * carry brand meaning rather than safety meaning. See `Insignia.kt` for how they're used.
 */
@Immutable
data class BrandPalette(
    /** Outer stops of the retroreflective band. */
    val bandEdge: Color,
    /** Bright core of the band — the part that catches a headlight. */
    val bandCore: Color,
    /** Dark diagonal of hazard tape. Pairs with the emergency color. */
    val hazard: Color,
)

val LightBrandPalette = BrandPalette(
    bandEdge = BandEdgeLight,
    bandCore = BandCoreLight,
    hazard = HazardStripeLight,
)

val DarkBrandPalette = BrandPalette(
    bandEdge = BandEdgeDark,
    bandCore = BandCoreDark,
    hazard = HazardStripeDark,
)

val LocalBrandPalette = staticCompositionLocalOf { LightBrandPalette }
