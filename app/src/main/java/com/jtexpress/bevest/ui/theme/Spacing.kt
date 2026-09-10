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

/**
 * Icon sizes. Icons carry safety meaning here, so they scale with the importance of
 * what they mark rather than with whatever looked right on the screen being built.
 */
object IconSize {
    /** Inline with body or label text — a vital beside a name. */
    val inline = 13.dp

    /** Beside a label: freshness, battery, connectivity. */
    val small = 16.dp

    /** Default action and list icons. */
    val medium = 20.dp

    /** Icon inside a tile, or a section marker. */
    val large = 24.dp

    /** Empty- and error-state glyphs. */
    val display = 34.dp
}

/** Fixed heights, so a control is the same size on every screen. */
object ComponentHeight {
    /** Chips and compact toggles. */
    val chip = 36.dp

    /** Secondary buttons and text fields. */
    val control = 48.dp

    /** Primary actions — a gloved thumb, not a mouse. */
    val button = 52.dp

    /** The emergency action. Deliberately the largest target in the app. */
    val emergency = 60.dp

    /** A list row with an avatar and two lines. */
    val listRow = 76.dp

    /** Bottom navigation. */
    val navBar = 64.dp
}

/**
 * Motion durations. The rule is that motion explains a change — where something came
 * from, that a value updated, that an action landed. Nothing animates for delight.
 */
object Motion {
    /** Press feedback, chip selection. Barely perceptible, but the tap feels answered. */
    const val INSTANT = 90

    /** Standard state change: color, size, visibility. */
    const val QUICK = 180

    /** Screen and container transitions. */
    const val STANDARD = 260

    /** A safety status changing — slow enough to catch the eye in peripheral vision. */
    const val STATUS = 320

    /** One emergency pulse cycle. */
    const val PULSE = 900

    /** Skeleton shimmer sweep. */
    const val SHIMMER = 1400
}
