package com.jtexpress.bevest.ui.theme

import androidx.compose.ui.graphics.Color

// ---- BeVest brand ----
val BevestOrange = Color(0xFFE0561A)
val BevestOrangeLightContainer = Color(0xFFFFDBCA)
val BevestOrangeDark = Color(0xFFF0722E)
val BevestOrangeDarkContainer = Color(0xFF5A2400)

// ---- Neutral surfaces, light (slight warm bias) ----
val WarmWhite = Color(0xFFFDFBF8)
val AppWhite = Color(0xFFFFFFFF)          // app background / surface
val WarmSurfaceCard = Color(0xFFF4F1EC)   // subtle card fill on white
val WarmSurfaceLight = Color(0xFFF6F2EC)
val WarmInk = Color(0xFF1C1815)
val WarmInkMuted = Color(0xFF52443B)      // onSurfaceVariant
val WarmOutlineLight = Color(0xFFCFC6BC)
val WarmOutlineVariantLight = Color(0xFFE6DFD6)

// ---- Neutral surfaces, dark (warm charcoal, never pure black) ----
val WarmInkDark = Color(0xFF15120F)       // background
val WarmSurfaceDark = Color(0xFF1D1915)   // surface
val WarmSurfaceCardDark = Color(0xFF272220)
val WarmSurfaceVariantDark = Color(0xFF2A2520)
val WarmOnDark = Color(0xFFF0EAE0)
val WarmOnDarkMuted = Color(0xFFCBBFB2)
val WarmOutlineDark = Color(0xFF57504A)
val WarmOutlineVariantDark = Color(0xFF3A342F)

// ---- Retroreflective band ----
// The silver stripe across a hi-vis vest, the app's signature divider. Three stops
// because a real reflective band is edge / bright core / edge, never a flat fill.
val BandEdgeLight = Color(0xFFB9B0A5)
val BandCoreLight = Color(0xFFF3F0EB)
val BandEdgeDark = Color(0xFF4E4740)
val BandCoreDark = Color(0xFF8C8479)

// ---- Hazard ----
// Caution-tape diagonals. Reserved for EMERGENCY only — used anywhere else it stops
// meaning anything.
val HazardStripeLight = Color(0xFF2A1410)
val HazardStripeDark = Color(0xFF120807)

// ---- Safety status tokens (never use raw colors per screen) ----
val StatusNormalLight = Color(0xFF1F7A46)
val StatusNormalDark = Color(0xFF57C083)
val StatusWarningLight = Color(0xFFB87400)
val StatusWarningDark = Color(0xFFE7AC53)
val StatusDangerLight = Color(0xFFB23B2E)
val StatusDangerDark = Color(0xFFE9776A)
val StatusEmergencyLight = Color(0xFF8E1B12)
val StatusEmergencyDark = Color(0xFFF25C48)
val StatusOfflineLight = Color(0xFF7C736A)
val StatusOfflineDark = Color(0xFF9A9187)
