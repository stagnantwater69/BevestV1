package com.jtexpress.bevest.domain.safety

import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.SensorReading

/**
 * Derives a [SafetyStatus] from a single [SensorReading].
 *
 * IMPORTANT: this is the *client-side view* of safety state. Authoritative detection
 * (timers, escalation, incident creation) runs in Cloud Functions so alerts continue
 * when no phone has the app open (plan section 16 & 31). This engine only decides what
 * to show right now, given the latest reading and how fresh it is.
 */
class SafetyStatusEngine(
    private val config: ThresholdConfig = ThresholdConfig.DEFAULT,
) {

    private companion object {
        val EMERGENCY_RESPONSES = setOf(
            SafetyResponseState.EMERGENCY_REQUESTED,
            SafetyResponseState.NO_RESPONSE,
            SafetyResponseState.ESCALATED,
        )
    }

    fun evaluate(
        reading: SensorReading?,
        nowMillis: Long = System.currentTimeMillis(),
    ): SafetyStatus {
        if (reading == null) return SafetyStatus.OFFLINE

        val ageSeconds = (nowMillis - reading.timestamp) / 1000
        if (reading.timestamp <= 0L || ageSeconds > config.offlineTimeoutSeconds) {
            return SafetyStatus.OFFLINE
        }

        // The escalation flow (manual emergency button, or a DANGER that went
        // unanswered) is the most severe live state — outrank the vitals checks.
        if (reading.safetyResponse in EMERGENCY_RESPONSES) {
            return SafetyStatus.EMERGENCY
        }

        if (reading.fallDetected || reading.motionState == MotionState.FALL_DETECTED) {
            return SafetyStatus.DANGER
        }

        val hrHigh = reading.heartRate?.let { it > config.heartRateHigh } ?: false
        val tempHigh = reading.temperature?.let { it > config.temperatureHigh } ?: false
        if (hrHigh || tempHigh || reading.motionState == MotionState.INACTIVE) {
            return SafetyStatus.WARNING
        }

        return SafetyStatus.NORMAL
    }

    /** True when the reading is too old to be shown as live (plan section 21). */
    fun isStale(reading: SensorReading?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (reading == null || reading.timestamp <= 0L) return true
        return (nowMillis - reading.timestamp) / 1000 > config.offlineTimeoutSeconds
    }
}
