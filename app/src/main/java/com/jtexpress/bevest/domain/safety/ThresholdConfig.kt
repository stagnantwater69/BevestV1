package com.jtexpress.bevest.domain.safety

data class AgeVitalThreshold(
    val id: String = "18-29",
    val label: String = "18 – 29 yrs",
    val minAge: Int = 18,
    val maxAge: Int = 29,
    val hrLowDanger: Int = 45,
    val hrLowWarning: Int = 55,
    val hrWarning: Int = 100,
    val hrDanger: Int = 120,
    val tempLowDanger: Double = 35.0,
    val tempLowWarning: Double = 35.8,
    val tempWarning: Double = 37.8,
    val tempDanger: Double = 38.5,
)

/**
 * Safety threshold parameters, including age-based Low/High Warning and Danger thresholds
 * for Heart Rate (BPM), Body Temperature (°C), and Fall Detection sensitivity configured by
 * the Developer in `settings/thresholds`.
 */
data class ThresholdConfig(
    val heartRateLowDanger: Int = 45,
    val heartRateLowWarning: Int = 55,
    val heartRateHigh: Int = 100,
    val heartRateDanger: Int = 120,
    val temperatureLowDanger: Double = 35.0,
    val temperatureLowWarning: Double = 35.8,
    val temperatureHigh: Double = 37.8,
    val temperatureDanger: Double = 38.5,
    val fallSensitivity: String = "MEDIUM",
    val fallFreefallG: Double = 0.65,
    val fallImpactG: Double = 2.8,
    val fallSevereImpactG: Double = 4.5,
    val warningDurationSeconds: Int = 30,
    val inactivitySeconds: Int = 180,
    val offlineTimeoutSeconds: Int = 60,
    val responseTimeoutSeconds: Int = 60,
    val lowBatteryPercent: Int = 20,
    val ageBrackets: List<AgeVitalThreshold> = DEFAULT_AGE_BRACKETS,
) {
    /**
     * Resolves the Warning and Danger thresholds for a worker's [age].
     * Falls back to global [heartRateLowDanger]/[heartRateLowWarning]/[heartRateHigh]/[heartRateDanger]
     * and [temperatureLowDanger]/[temperatureLowWarning]/[temperatureHigh]/[temperatureDanger]
     * when age is not provided.
     */
    fun forAge(age: Int?): AgeVitalThreshold {
        if (age != null && ageBrackets.isNotEmpty()) {
            ageBrackets.firstOrNull { age in it.minAge..it.maxAge }?.let { return it }
            val sorted = ageBrackets.sortedBy { it.minAge }
            if (age < sorted.first().minAge) return sorted.first()
            if (age > sorted.last().maxAge) return sorted.last()
        }
        return AgeVitalThreshold(
            id = "default",
            label = "Default",
            minAge = 18,
            maxAge = 99,
            hrLowDanger = heartRateLowDanger,
            hrLowWarning = heartRateLowWarning,
            hrWarning = heartRateHigh,
            hrDanger = heartRateDanger,
            tempLowDanger = temperatureLowDanger,
            tempLowWarning = temperatureLowWarning,
            tempWarning = temperatureHigh,
            tempDanger = temperatureDanger,
        )
    }

    companion object {
        val DEFAULT_AGE_BRACKETS = listOf(
            AgeVitalThreshold(
                id = "18-29",
                label = "18 – 29 yrs",
                minAge = 18,
                maxAge = 29,
                hrWarning = 100,
                hrDanger = 120,
                tempWarning = 37.8,
                tempDanger = 38.5,
            ),
            AgeVitalThreshold(
                id = "30-39",
                label = "30 – 39 yrs",
                minAge = 30,
                maxAge = 39,
                hrWarning = 98,
                hrDanger = 118,
                tempWarning = 37.8,
                tempDanger = 38.5,
            ),
            AgeVitalThreshold(
                id = "40-49",
                label = "40 – 49 yrs",
                minAge = 40,
                maxAge = 49,
                hrWarning = 95,
                hrDanger = 115,
                tempWarning = 37.7,
                tempDanger = 38.4,
            ),
            AgeVitalThreshold(
                id = "50-59",
                label = "50 – 59 yrs",
                minAge = 50,
                maxAge = 59,
                hrWarning = 92,
                hrDanger = 110,
                tempWarning = 37.6,
                tempDanger = 38.3,
            ),
            AgeVitalThreshold(
                id = "60+",
                label = "60+ yrs",
                minAge = 60,
                maxAge = 120,
                hrWarning = 88,
                hrDanger = 105,
                tempWarning = 37.5,
                tempDanger = 38.2,
            ),
        )

        val DEFAULT = ThresholdConfig()
    }
}
