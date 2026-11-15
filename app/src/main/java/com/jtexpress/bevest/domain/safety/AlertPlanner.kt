package com.jtexpress.bevest.domain.safety

import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.Worker

/** A write the safety engine wants made. [SafetyWatcher] carries these out. */
sealed interface SafetyAction {
    val worker: Worker

    /** Open a [type] alert for the worker, unless one is already open. */
    data class Open(
        override val worker: Worker,
        val reading: SensorReading,
        val type: AlertType,
        val severity: AlertSeverity,
        val message: String,
        /** An emergency from the start (SOS): open its incident with it. */
        val withIncident: Boolean = false,
        /** The condition can clear on its own, and the alert closes with it. */
        val autoResolve: Boolean = false,
    ) : SafetyAction

    /** A fall nobody answered in time: upgrade to EMERGENCY and open the incident. */
    data class Escalate(
        override val worker: Worker,
        val alert: Alert,
        val reading: SensorReading,
    ) : SafetyAction

    /**
     * A fall or emergency no officer has acknowledged in time: flag it, so every officer's
     * phone sounds again and the contractor is told.
     */
    data class Unanswered(
        override val worker: Worker,
        val alert: Alert,
    ) : SafetyAction

    /** The condition behind an [Alert.autoResolve] alert has cleared. */
    data class AutoResolve(
        override val worker: Worker,
        val alert: Alert,
        val reason: String,
    ) : SafetyAction
}

/**
 * Decides, from one snapshot of a site, which alerts should be opened, escalated or
 * closed. Pure apart from remembering when each sustained condition started, so the
 * rules are unit-testable without Firebase.
 *
 *  - **Fall** — DANGER at once; EMERGENCY + incident if unanswered past the response timeout.
 *  - **SOS** (the worker's emergency button) — EMERGENCY + incident at once.
 *  - **Vest offline** — WARNING when a paired vest stops reporting; closes when it's back.
 *  - **High heart rate / high temperature / inactivity** — WARNING once the condition
 *    has lasted [ThresholdConfig.warningDurationSeconds]; closes when it clears.
 *  - **Unanswered** — a fall or emergency still unacknowledged by any officer after
 *    [ThresholdConfig.unansweredTimeoutSeconds] is flagged once.
 *
 * Readings from the in-app simulator are skipped: it raises and escalates its own alerts.
 */
class AlertPlanner {

    /** When each sustained condition was first seen, keyed by "workerId|TYPE". */
    private val since = mutableMapOf<String, Long>()

    fun reset() = since.clear()

    /**
     * @param alerts the site's open (ACTIVE or ACKNOWLEDGED) alerts.
     * @param connectedForMs how long this phone has been connected to the database. A
     *   phone that just came back online holds stale cached readings for a moment; no
     *   vest is called offline until the phone itself has been online long enough.
     */
    fun plan(
        workers: List<Worker>,
        readings: Map<String, SensorReading>,
        alerts: List<Alert>,
        thresholds: ThresholdConfig,
        now: Long,
        connectedForMs: Long,
    ): List<SafetyAction> {
        val engine = SafetyStatusEngine(thresholds)
        val actions = mutableListOf<SafetyAction>()
        val seen = HashSet<String>()

        for (worker in workers) {
            if (!worker.active) continue

            val open = alerts
                .filter { it.workerId == worker.workerId && it.status != AlertStatus.RESOLVED && !it.simulated }
                .associateBy { it.type }

            // ---- unanswered: about the officers, not the vest, so it needs no reading
            for (alert in open.values) {
                if (alert.status == AlertStatus.ACTIVE &&
                    alert.severity != AlertSeverity.WARNING &&
                    alert.unansweredAt == null &&
                    alert.createdAt > 0 &&
                    now - alert.createdAt > thresholds.unansweredTimeoutSeconds * 1000L
                ) {
                    actions += SafetyAction.Unanswered(worker, alert)
                }
            }

            val reading = readings[worker.workerId] ?: continue
            if (reading.simulated) continue
            val fresh = !engine.isStale(reading, now)

            // ---- fall
            val fall = open[AlertType.FALL_DETECTED]
            val falling = fresh && (reading.fallDetected || reading.motionState == MotionState.FALL_DETECTED)
            if (fall == null) {
                if (falling) {
                    actions += SafetyAction.Open(worker, reading, AlertType.FALL_DETECTED, AlertSeverity.DANGER, "Fall detected")
                }
            } else if (
                // The vest clears the fall flag after one reading, so this must not wait
                // for it to still be set.
                fall.status == AlertStatus.ACTIVE &&
                fall.severity != AlertSeverity.EMERGENCY &&
                reading.safetyResponse != SafetyResponseState.ACKNOWLEDGED &&
                now - fall.createdAt > thresholds.responseTimeoutSeconds * 1000L
            ) {
                actions += SafetyAction.Escalate(worker, fall, reading)
            }

            // ---- SOS
            if (fresh && reading.safetyResponse == SafetyResponseState.EMERGENCY_REQUESTED &&
                open[AlertType.EMERGENCY_REQUEST] == null
            ) {
                actions += SafetyAction.Open(
                    worker, reading, AlertType.EMERGENCY_REQUEST, AlertSeverity.EMERGENCY,
                    "Emergency button pressed", withIncident = true,
                )
            }

            // ---- vest offline
            val offlineAlert = open[AlertType.VEST_OFFLINE]
            val paired = !worker.assignedVestId.isNullOrBlank()
            val offlineLongEnough = connectedForMs > thresholds.offlineTimeoutSeconds * 1000L
            if (!fresh && paired && reading.timestamp > 0 && offlineLongEnough) {
                if (offlineAlert == null) {
                    actions += SafetyAction.Open(
                        worker, reading, AlertType.VEST_OFFLINE, AlertSeverity.WARNING,
                        "Vest stopped reporting", autoResolve = true,
                    )
                }
            } else if (fresh && offlineAlert?.autoResolve == true) {
                actions += SafetyAction.AutoResolve(worker, offlineAlert, "Vest reporting again")
            }

            // ---- sustained warnings
            fun sustained(type: AlertType, present: Boolean, message: String, cleared: String) {
                val key = "${worker.workerId}|${type.name}"
                seen += key
                val existing = open[type]
                if (!fresh) return // no fresh data: neither start nor clear the condition
                if (!present) {
                    since.remove(key)
                    if (existing?.autoResolve == true) actions += SafetyAction.AutoResolve(worker, existing, cleared)
                    return
                }
                val start = since.getOrPut(key) { now }
                if (existing == null && now - start >= thresholds.warningDurationSeconds * 1000L) {
                    actions += SafetyAction.Open(worker, reading, type, AlertSeverity.WARNING, message, autoResolve = true)
                }
            }
            sustained(
                AlertType.HEART_RATE_WARNING,
                reading.heartRate?.let { it > thresholds.heartRateHigh } == true,
                "Heart rate high (${reading.heartRate} bpm)",
                "Heart rate back to normal",
            )
            sustained(
                AlertType.TEMPERATURE_WARNING,
                reading.temperature?.let { it > thresholds.temperatureHigh } == true,
                "Temperature high (${reading.temperature} °C)",
                "Temperature back to normal",
            )
            sustained(
                AlertType.INACTIVITY,
                reading.motionState == MotionState.INACTIVE,
                "No movement detected",
                "Moving again",
            )
        }
        // Forget conditions for workers no longer on the site.
        since.keys.retainAll(seen)
        return actions
    }
}
