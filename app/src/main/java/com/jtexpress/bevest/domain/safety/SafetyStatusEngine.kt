package com.jtexpress.bevest.domain.safety

import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
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

    /**
     * [evaluate], adjusted for the worker's open DANGER/EMERGENCY alerts:
     *
     *  - **Unanswered (ACTIVE)** — held at the alert's severity. The vest reports a fall
     *    in a single reading and clears it on the next, so the live reading alone would
     *    drop the worker back to NORMAL seconds later while nobody has responded.
     *  - **Acknowledged** — an officer is handling it, so the worker leaves the danger
     *    count and shows as WARNING ("to check") until the alert is resolved. A fresh
     *    SOS from the worker still counts as an emergency.
     *  - **Resolved** — no effect; the live reading decides.
     */
    fun evaluate(
        reading: SensorReading?,
        openAlerts: List<Alert>,
        workerId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): SafetyStatus {
        val live = evaluate(reading, nowMillis)
        val mine = openAlerts.filter {
            it.workerId == workerId && it.status != AlertStatus.RESOLVED && it.severity != AlertSeverity.WARNING
        }
        val unanswered = mine
            .filter { it.status == AlertStatus.ACTIVE }
            .map { if (it.severity == AlertSeverity.EMERGENCY) SafetyStatus.EMERGENCY else SafetyStatus.DANGER }
            .maxByOrNull { severityRank(it) }
        if (unanswered != null) {
            return if (severityRank(unanswered) > severityRank(live)) unanswered else live
        }
        if (mine.any { it.status == AlertStatus.ACKNOWLEDGED }) {
            return if (reading?.safetyResponse == SafetyResponseState.EMERGENCY_REQUESTED &&
                live == SafetyStatus.EMERGENCY
            ) {
                SafetyStatus.EMERGENCY
            } else {
                SafetyStatus.WARNING
            }
        }
        return live
    }

    /** OFFLINE sits last in the enum but is not more severe than an open danger. */
    private fun severityRank(s: SafetyStatus): Int = when (s) {
        SafetyStatus.NORMAL, SafetyStatus.OFFLINE -> 0
        SafetyStatus.WARNING -> 1
        SafetyStatus.DANGER -> 2
        SafetyStatus.EMERGENCY -> 3
    }

    /** True when the reading is too old to be shown as live (plan section 21). */
    fun isStale(reading: SensorReading?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (reading == null || reading.timestamp <= 0L) return true
        return (nowMillis - reading.timestamp) / 1000 > config.offlineTimeoutSeconds
    }
}
