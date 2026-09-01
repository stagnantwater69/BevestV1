package com.jtexpress.bevest.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale. Gloved hands and sunlight: touch targets never below 48dp,
 * screen gutters generous, related content grouped tighter than unrelated.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal screen gutter. */
    val gutter = 16.dp

    /** Minimum interactive height. */
    val touchTarget = 48.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val pill = 100.dp
}

/** Resting shadow depths. Kept shallow — the app is content-first, not glassy. */
object Elevation {
    val flat = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val sheet = 6.dp
}
