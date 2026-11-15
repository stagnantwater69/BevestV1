package com.jtexpress.bevest.domain.safety

data class AgeVitalThreshold(
    val id: String = "18-29",
    val label: String = "18 – 29 yrs",
    val minAge: Int = 18,
    val maxAge: Int = 29,
    val hrWarning: Int = 100,
    val hrDanger: Int = 120,
    val tempWarning: Double = 37.8,
    val tempDanger: Double = 38.5,
)

/**
 * Safety threshold parameters, including age-based Warning and Danger thresholds
 * for Heart Rate (BPM) and Body Temperature (°C) configured by the Developer in
 * `settings/thresholds`.
 */
data class ThresholdConfig(
    val heartRateHigh: Int = 100,
    val heartRateDanger: Int = 120,
    val temperatureHigh: Double = 37.8,
    val temperatureDanger: Double = 38.5,
    val warningDurationSeconds: Int = 30,
    val inactivitySeconds: Int = 180,
    val offlineTimeoutSeconds: Int = 60,
    val responseTimeoutSeconds: Int = 60,
    val lowBatteryPercent: Int = 20,
    val ageBrackets: List<AgeVitalThreshold> = DEFAULT_AGE_BRACKETS,
) {
    /**
     * Resolves the Warning and Danger thresholds for a worker's [age].
     * Falls back to global [heartRateHigh]/[heartRateDanger] and [temperatureHigh]/[temperatureDanger]
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
            hrWarning = heartRateHigh,
            hrDanger = heartRateDanger,
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
