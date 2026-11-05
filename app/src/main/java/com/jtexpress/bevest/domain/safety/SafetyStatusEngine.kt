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
 * Evaluates Heart Rate and Body Temperature against the worker's age-based
 * Warning (yellow) and Danger (red) thresholds configured by the Developer.
 */
class SafetyStatusEngine(
    val config: ThresholdConfig = ThresholdConfig.DEFAULT,
) {

    private companion object {
        val EMERGENCY_RESPONSES = setOf(
            SafetyResponseState.EMERGENCY_REQUESTED,
            SafetyResponseState.NO_RESPONSE,
            SafetyResponseState.ESCALATED,
        )
    }

    /** Evaluates Heart Rate against the worker's age-adjusted Warning and Danger thresholds. */
    fun evaluateHeartRate(heartRate: Int?, workerAge: Int? = null): SafetyStatus {
        if (heartRate == null || heartRate <= 0) return SafetyStatus.NORMAL
        val bracket = config.forAge(workerAge)
        return when {
            heartRate >= bracket.hrDanger -> SafetyStatus.DANGER
            heartRate >= bracket.hrWarning -> SafetyStatus.WARNING
            else -> SafetyStatus.NORMAL
        }
    }

    /** Evaluates Body Temperature against the worker's age-adjusted Warning and Danger thresholds. */
    fun evaluateTemperature(temperature: Double?, workerAge: Int? = null): SafetyStatus {
        if (temperature == null || temperature <= 0.0) return SafetyStatus.NORMAL
        val bracket = config.forAge(workerAge)
        return when {
            temperature >= bracket.tempDanger -> SafetyStatus.DANGER
            temperature >= bracket.tempWarning -> SafetyStatus.WARNING
            else -> SafetyStatus.NORMAL
        }
    }

    fun evaluate(
        reading: SensorReading?,
        nowMillis: Long = System.currentTimeMillis(),
        workerAge: Int? = null,
    ): SafetyStatus {
        if (reading == null) return SafetyStatus.OFFLINE

        // The escalation flow (manual emergency button, or a safety check / fall that went
        // unanswered) is the most severe live state — outrank the vitals checks.
        if (reading.safetyResponse in EMERGENCY_RESPONSES) {
            return SafetyStatus.EMERGENCY
        }

        if (reading.fallDetected || reading.motionState == MotionState.FALL_DETECTED) {
            return SafetyStatus.DANGER
        }

        val ageSeconds = (nowMillis - reading.timestamp) / 1000
        if (reading.timestamp <= 0L || ageSeconds > config.offlineTimeoutSeconds) {
            return SafetyStatus.OFFLINE
        }

        val hrStatus = evaluateHeartRate(reading.heartRate, workerAge)
        val tempStatus = evaluateTemperature(reading.temperature, workerAge)

        if (hrStatus == SafetyStatus.DANGER || tempStatus == SafetyStatus.DANGER) {
            return SafetyStatus.DANGER
        }

        if (hrStatus == SafetyStatus.WARNING ||
            tempStatus == SafetyStatus.WARNING ||
            reading.motionState == MotionState.INACTIVE
        ) {
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
        workerAge: Int? = null,
    ): SafetyStatus {
        val live = evaluate(reading, nowMillis, workerAge)
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
            } else if (live == SafetyStatus.DANGER) {
                SafetyStatus.DANGER
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
