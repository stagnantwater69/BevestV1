package com.jtexpress.bevest.domain.safety

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.notifications.NotificationChannels
import com.jtexpress.bevest.notifications.NotificationHelper
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fall detection and escalation, run on the SSO's phone.
 *
 * The project is on Firebase's free plan, so the cloud functions in `functions/` cannot
 * be deployed. This does the part of their job that matters most, from inside the app:
 *
 *  - a fall opens one FALL_DETECTED alert (DANGER) straight away;
 *  - if nobody responds within the response timeout — the vest's wearer did not
 *    acknowledge and no officer acknowledged the alert — it is upgraded to EMERGENCY,
 *    an incident is opened and the reading's `safetyResponse` set to NO_RESPONSE;
 *  - every new or upgraded alert on the site raises a local notification.
 *
 * It only runs while an SSO has the app open. Several officers' phones may run it at
 * once, so every write is a transaction that checks whether another phone got there
 * first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SafetyWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
    private val alertRepository: AlertRepository,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var jobs: List<Job> = emptyList()
    private var watchingSite: String? = null

    /** Alerts already notified about, keyed by id, with the severity they had then. */
    private val notified = mutableMapOf<String, AlertSeverity>()

    fun start(siteId: String?) {
        if (siteId.isNullOrBlank() || siteId == watchingSite) return
        stop()
        watchingSite = siteId
        jobs = listOf(watchReadings(siteId), watchAlerts(siteId))
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        watchingSite = null
        notified.clear()
    }

    // ------------------------------------------------------------ detection

    private fun watchReadings(siteId: String) = scope.launch {
        val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(TICK_MS) } }
        workerRepository.observeWorkers(contractorId = null, siteId = siteId)
            .flatMapLatest { outcome ->
                val workers = (outcome as? Outcome.Success)?.data.orEmpty()
                if (workers.isEmpty()) return@flatMapLatest flowOf(null)
                combine(
                    monitoringRepository.observeReadings(workers.map { it.workerId }),
                    settingsRepository.observeThresholds(),
                    alertRepository.observeActiveAlerts(siteId),
                    ticker,
                ) { readings, thresholds, alerts, now ->
                    Snapshot(workers, readings, thresholds, (alerts as? Outcome.Success)?.data.orEmpty(), now)
                }
            }
            .collect { snap -> snap?.let { runCatching { evaluate(it) } } }
    }

    private class Snapshot(
        val workers: List<Worker>,
        val readings: Map<String, SensorReading>,
        val thresholds: ThresholdConfig,
        val alerts: List<Alert>,
        val now: Long,
    )

    private suspend fun evaluate(s: Snapshot) {
        val engine = SafetyStatusEngine(s.thresholds)
        for (worker in s.workers) {
            val reading = s.readings[worker.workerId] ?: continue
            val status = engine.evaluate(reading, s.now)
            val fell = (reading.fallDetected || reading.motionState == MotionState.FALL_DETECTED) &&
                (status == SafetyStatus.DANGER || status == SafetyStatus.EMERGENCY)

            val fallAlert = s.alerts.firstOrNull {
                it.workerId == worker.workerId && it.type == AlertType.FALL_DETECTED
            }
            when {
                // A fall with no open alert yet (the simulator raises its own, so a
                // simulated fall already has one and is left alone).
                fallAlert == null -> if (fell) openFallAlert(worker, reading)

                // Still unanswered after the timeout: escalate. The vest clears the fall
                // flag after one reading, so this must not wait for it to still be set.
                fallAlert.status == AlertStatus.ACTIVE &&
                    fallAlert.severity != AlertSeverity.EMERGENCY &&
                    reading.safetyResponse != SafetyResponseState.ACKNOWLEDGED &&
                    s.now - fallAlert.createdAt > s.thresholds.responseTimeoutSeconds * 1000L ->
                    escalate(worker, fallAlert, reading)
            }
        }
    }

    /**
     * Opens the fall alert, once. `openFallAlertId` on the worker is claimed in a
     * transaction so two phones seeing the same fall can't both open one.
     */
    private suspend fun openFallAlert(worker: Worker, reading: SensorReading) {
        val workerRef = firestore.collection(FirebasePaths.WORKERS).document(worker.workerId)
        val alertRef = firestore.collection(FirebasePaths.ALERTS).document()
        firestore.runTransaction { tx ->
            val existingId = tx.get(workerRef).getString("openFallAlertId")
            if (existingId != null) {
                val existing = tx.get(firestore.collection(FirebasePaths.ALERTS).document(existingId))
                val status = AlertStatus.fromRaw(existing.getString("status"))
                if (existing.exists() && status != AlertStatus.RESOLVED) return@runTransaction
            }
            tx.set(
                alertRef,
                mapOf(
                    "workerId" to worker.workerId,
                    "vestId" to (worker.assignedVestId ?: reading.vestId),
                    "siteId" to worker.siteId,
                    "contractorId" to worker.contractorId,
                    "type" to AlertType.FALL_DETECTED.name,
                    "severity" to AlertSeverity.DANGER.name,
                    "status" to AlertStatus.ACTIVE.name,
                    "message" to "Fall detected",
                    "createdAt" to System.currentTimeMillis(),
                ),
            )
            tx.update(workerRef, "openFallAlertId", alertRef.id)
        }.await()
    }

    /** Upgrades the fall alert to EMERGENCY and opens its incident, once. */
    private suspend fun escalate(worker: Worker, alert: Alert, reading: SensorReading) {
        val alertRef = firestore.collection(FirebasePaths.ALERTS).document(alert.alertId)
        val incidentRef = firestore.collection(FirebasePaths.INCIDENTS).document(alert.alertId)
        val now = System.currentTimeMillis()
        val escalated = firestore.runTransaction { tx ->
            val current = tx.get(alertRef)
            if (AlertSeverity.fromRaw(current.getString("severity")) == AlertSeverity.EMERGENCY ||
                AlertStatus.fromRaw(current.getString("status")) != AlertStatus.ACTIVE
            ) {
                return@runTransaction false
            }
            tx.set(
                incidentRef,
                mapOf(
                    "workerId" to worker.workerId,
                    "vestId" to (worker.assignedVestId ?: reading.vestId),
                    "siteId" to worker.siteId,
                    "contractorId" to worker.contractorId,
                    "type" to AlertType.FALL_DETECTED.name,
                    "severity" to AlertSeverity.EMERGENCY.name,
                    "alertId" to alert.alertId,
                    "heartRate" to reading.heartRate,
                    "temperature" to reading.temperature,
                    "latitude" to reading.latitude,
                    "longitude" to reading.longitude,
                    "createdAt" to now,
                    "outcome" to null,
                    "resolutionNotes" to null,
                ),
            )
            tx.update(
                alertRef,
                mapOf(
                    "severity" to AlertSeverity.EMERGENCY.name,
                    "message" to "Fall detected — no response",
                    "incidentId" to alert.alertId,
                ),
            )
            true
        }.await()
        if (escalated) {
            // Puts the worker in EMERGENCY on every screen, as the cloud engine would.
            monitoringRepository.setSafetyResponse(worker.workerId, SafetyResponseState.NO_RESPONSE)
        }
    }

    // ---------------------------------------------------------- notification

    /**
     * Notifies on every alert that appears, or is upgraded, while the app is open —
     * whichever phone created it. The alerts already open when watching starts are
     * recorded without notifying, so opening the app doesn't replay old alerts.
     */
    private fun watchAlerts(siteId: String) = scope.launch {
        var first = true
        combine(
            alertRepository.observeActiveAlerts(siteId),
            workerRepository.observeWorkers(contractorId = null, siteId = siteId),
        ) { alerts, workers -> alerts to workers }
            .collect { (alertsOutcome, workersOutcome) ->
                val alerts = (alertsOutcome as? Outcome.Success)?.data ?: return@collect
                val names = (workersOutcome as? Outcome.Success)?.data.orEmpty()
                    .associate { it.workerId to it.fullName.ifBlank { "Worker ${it.workerId}" } }
                for (alert in alerts) {
                    val seen = notified[alert.alertId]
                    notified[alert.alertId] = alert.severity
                    // Once any officer acknowledges it, stop the siren on every phone.
                    if (alert.status != AlertStatus.ACTIVE) {
                        NotificationHelper.cancel(context, alert.alertId)
                        continue
                    }
                    if (first || seen == alert.severity) continue
                    if (seen != null && alert.severity.ordinal <= seen.ordinal) continue
                    notify(alert, names[alert.workerId] ?: "Worker ${alert.workerId}")
                }
                // Resolved alerts leave the active list; silence those too.
                val open = alerts.mapTo(HashSet()) { it.alertId }
                (notified.keys - open).forEach { id ->
                    NotificationHelper.cancel(context, id)
                    notified.remove(id)
                }
                first = false
            }
    }

    private fun notify(alert: Alert, workerName: String) {
        val emergency = alert.severity == AlertSeverity.EMERGENCY
        val (title, body) = when {
            alert.type == AlertType.FALL_DETECTED && emergency ->
                "⚠ EMERGENCY" to "$workerName fell and hasn't responded."
            alert.type == AlertType.FALL_DETECTED ->
                "⚠ Fall detected" to "$workerName may have fallen. Check on them now."
            emergency -> "⚠ EMERGENCY" to "$workerName: ${alert.message}"
            else -> "Safety alert" to "$workerName: ${alert.message}"
        }
        NotificationHelper.show(
            context = context,
            title = title,
            body = body,
            // This watcher only runs for SSOs, the people on site: danger gets the siren.
            channelId = if (alert.severity == AlertSeverity.WARNING) {
                NotificationChannels.WARNING
            } else {
                NotificationChannels.EMERGENCY
            },
            type = "ALERT",
            targetId = alert.alertId,
            workerId = alert.workerId,
        )
    }

    private companion object {
        const val TICK_MS = 5_000L
    }
}
