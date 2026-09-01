package com.jtexpress.bevest.domain.safety

/**
 * Configurable safety thresholds. These are stored in Firebase at `settings/thresholds`
 * and read as a flow — the values below are only fallback defaults (plan section 16, 34).
 * Never hard-code these numbers anywhere else in the app.
 */
data class ThresholdConfig(
    val heartRateHigh: Int = 100,
    val temperatureHigh: Double = 38.0,
    val warningDurationSeconds: Int = 5 * 60,
    val responseTimeoutSeconds: Int = 15,
    val offlineTimeoutSeconds: Int = 60,
) {
    companion object {
        val DEFAULT = ThresholdConfig()
    }
}
