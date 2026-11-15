package com.jtexpress.bevest.domain.safety

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.notifications.NotificationChannels
import com.jtexpress.bevest.notifications.NotificationHelper
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.ServerClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the phone-side safety engine can currently see the site. */
sealed interface MonitorHealth {
    data object Idle : MonitorHealth
    data object Ok : MonitorHealth
    /** A stream is failing and being retried: alerts may be late until it recovers. */
    data class Degraded(val reason: String) : MonitorHealth
}

/**
 * Alert detection and escalation, run on the SSO's phone.
 *
 * The project is on Firebase's free plan, so the cloud functions in `functions/` cannot
 * be deployed. This does their job from inside the app while [EngineMode.DEVICE] is set
 * (the default): [AlertPlanner] decides what to open, escalate or close, and this makes
 * the writes and raises the local notifications for every new or upgraded alert.
 *
 * It runs while an SSO is signed in, kept alive in the background by
 * [com.jtexpress.bevest.notifications.MonitoringService]. Several officers' phones may run
 * it at once, so every opening write is a transaction that checks whether another phone
 * got there first.
 *
 * All state is touched on the main thread only: the collectors run there, and
 * [start]/[stop] are called from the UI or the service.
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
    private val authRepository: AuthRepository,
    private val clock: ServerClock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var watchingSite: String? = null
    private val planner = AlertPlanner()

    /** Alerts already notified about, keyed by id, with the severity they had then. */
    private val notified = mutableMapOf<String, AlertSeverity>()

    /** Alerts already re-sounded for going unanswered. */
    private val nagged = mutableSetOf<String>()

    /** Alerts whose "someone is responding" has already been shown. */
    private val responded = mutableSetOf<String>()

    private val _health = MutableStateFlow<MonitorHealth>(MonitorHealth.Idle)
    val health: StateFlow<MonitorHealth> = _health.asStateFlow()

    fun start(siteId: String?) {
        if (siteId.isNullOrBlank() || siteId == watchingSite) return
        stop()
        watchingSite = siteId
        job = scope.launch { watch(siteId) }
    }

    fun stop() {
        job?.cancel()
        job = null
        watchingSite = null
        notified.clear()
        nagged.clear()
        responded.clear()
        planner.reset()
        _health.value = MonitorHealth.Idle
    }

    private suspend fun watch(siteId: String) = coroutineScope {
        // One subscription each, shared by detection and notification.
        val workers = workerRepository.observeWorkers(contractorId = null, siteId = siteId)
            .stateIn(this, SharingStarted.Eagerly, null)
        val alerts = alertRepository.observeActiveAlerts(siteId)
            .stateIn(this, SharingStarted.Eagerly, null)

        launch { detect(siteId, workers, alerts) }
        launch { notifyOnChange(siteId, workers, alerts) }
        launch {
            combine(workers, alerts) { w, a ->
                val failure = (w as? Outcome.Failure) ?: (a as? Outcome.Failure)
                if (failure != null) MonitorHealth.Degraded(failure.error.message) else MonitorHealth.Ok
            }.collect { _health.value = it }
        }
    }

    // ------------------------------------------------------------ detection

    private class Inputs(
        val workers: List<Worker>,
        val alerts: List<Alert>,
        val mode: EngineMode,
        val thresholds: ThresholdConfig,
    )

    private suspend fun detect(
        siteId: String,
        workersState: StateFlow<Outcome<List<Worker>>?>,
        alertsState: StateFlow<Outcome<List<Alert>>?>,
    ) {
        val workers = workersState.filterNotNull().map { it as? Outcome.Success }
        // Only re-subscribe to the vests when the roster itself changes — not on every
        // worker-document write (including this engine's own openAlerts claims).
        val readings = workers
            .map { it?.data?.map(Worker::workerId).orEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { ids -> monitoringRepository.observeReadings(ids) }
        val inputs = combine(
            workers,
            alertsState.filterNotNull().map { it as? Outcome.Success },
            settingsRepository.observeEngineMode(),
            settingsRepository.observeThresholds(),
        ) { w, a, mode, t ->
            // Never act on a failed stream: an empty alert list would re-open every alert.
            if (w == null || a == null) null else Inputs(w.data, a.data, mode, t)
        }
        combine(inputs, readings, clock.ticker()) { i, r, now -> Triple(i, r, now) }
            .collect { (i, r, now) ->
                if (i == null || i.mode != EngineMode.DEVICE) return@collect
                val actions = planner.plan(
                    workers = i.workers,
                    readings = r,
                    alerts = i.alerts.filter { it.siteId == null || it.siteId == siteId },
                    thresholds = i.thresholds,
                    now = now,
                    connectedForMs = clock.connectedForMs(),
                )
                for (action in actions) {
                    try {
                        perform(action)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Offline or contended: the next tick sees the same state and retries.
                    }
                }
            }
    }

    private suspend fun perform(action: SafetyAction) = when (action) {
        is SafetyAction.Open -> open(action)
        is SafetyAction.Escalate -> escalate(action)
        is SafetyAction.AutoResolve -> autoResolve(action)
        is SafetyAction.Unanswered -> flagUnanswered(action)
    }

    /** Marks the alert unanswered, once, and only while nobody has acknowledged it. */
    private suspend fun flagUnanswered(a: SafetyAction.Unanswered) {
        val ref = firestore.collection(FirebasePaths.ALERTS).document(a.alert.alertId)
        val now = clock.now()
        firestore.runTransaction { tx ->
            val current = tx.get(ref)
            if (AlertStatus.fromRaw(current.getString("status")) == AlertStatus.ACTIVE &&
                current.get("unansweredAt") == null
            ) {
                tx.update(ref, "unansweredAt", now)
            }
        }.await()
    }

    /**
     * Opens the alert, once per type. `openAlerts.<TYPE>` on the worker is claimed in a
     * transaction so two phones seeing the same condition can't both open one.
     */
    private suspend fun open(a: SafetyAction.Open) {
        val worker = a.worker
        val workerRef = firestore.collection(FirebasePaths.WORKERS).document(worker.workerId)
        val alertRef = firestore.collection(FirebasePaths.ALERTS).document()
        val incidentRef = firestore.collection(FirebasePaths.INCIDENTS).document(alertRef.id)
        val claimField = "openAlerts.${a.type.name}"
        val now = clock.now()
        firestore.runTransaction { tx ->
            val workerSnap = tx.get(workerRef)
            val existingId = workerSnap.getString(claimField)
                ?: if (a.type == AlertType.FALL_DETECTED) workerSnap.getString("openFallAlertId") else null
            if (existingId != null) {
                val existing = tx.get(firestore.collection(FirebasePaths.ALERTS).document(existingId))
                if (existing.exists() && AlertStatus.fromRaw(existing.getString("status")) != AlertStatus.RESOLVED) {
                    return@runTransaction
                }
            }
            val base = mapOf(
                "workerId" to worker.workerId,
                "vestId" to (worker.assignedVestId ?: a.reading.vestId),
                "siteId" to worker.siteId,
                "contractorId" to worker.contractorId,
                "type" to a.type.name,
                "severity" to a.severity.name,
                "createdAt" to now,
            )
            tx.set(
                alertRef,
                base + mapOf(
                    "status" to AlertStatus.ACTIVE.name,
                    "message" to a.message,
                    "autoResolve" to a.autoResolve,
                    "source" to "device",
                    "incidentId" to if (a.withIncident) incidentRef.id else null,
                ),
            )
            if (a.withIncident) {
                tx.set(incidentRef, base + incidentFields(alertRef.id, a.reading.heartRate, a.reading.temperature, a.reading.latitude, a.reading.longitude))
            }
            tx.update(workerRef, claimField, alertRef.id)
        }.await()
    }

    /** Upgrades the fall alert to EMERGENCY and opens its incident, once. */
    private suspend fun escalate(a: SafetyAction.Escalate) {
        val worker = a.worker
        val reading = a.reading
        val alertRef = firestore.collection(FirebasePaths.ALERTS).document(a.alert.alertId)
        val incidentRef = firestore.collection(FirebasePaths.INCIDENTS).document(a.alert.alertId)
        val now = clock.now()
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
                    "createdAt" to now,
                ) + incidentFields(a.alert.alertId, reading.heartRate, reading.temperature, reading.latitude, reading.longitude),
            )
            tx.update(
                alertRef,
                mapOf(
                    "severity" to AlertSeverity.EMERGENCY.name,
                    "message" to "Fall detected — no response",
                    "incidentId" to a.alert.alertId,
                ),
            )
            true
        }.await()
        if (escalated) {
            // Puts the worker in EMERGENCY on every screen, as the cloud engine would.
            monitoringRepository.setSafetyResponse(worker.workerId, SafetyResponseState.NO_RESPONSE)
        }
    }

    private fun incidentFields(
        alertId: String,
        heartRate: Int?,
        temperature: Double?,
        latitude: Double?,
        longitude: Double?,
    ) = mapOf(
        "alertId" to alertId,
        "heartRate" to heartRate,
        "temperature" to temperature,
        "latitude" to latitude,
        "longitude" to longitude,
        "outcome" to null,
        "resolutionNotes" to null,
    )

    private suspend fun autoResolve(a: SafetyAction.AutoResolve) {
        firestore.collection(FirebasePaths.ALERTS).document(a.alert.alertId).update(
            mapOf(
                "status" to AlertStatus.RESOLVED.name,
                "resolvedAt" to FieldValue.serverTimestamp(),
                "resolvedBy" to "system",
                "resolutionNotes" to a.reason,
            ),
        ).await()
    }

    // ---------------------------------------------------------- notification

    /**
     * Notifies on every alert that appears, or is upgraded, while watching — whichever
     * phone (or the cloud) created it. The alerts already open when watching starts are
     * recorded without notifying, so opening the app doesn't replay old alerts.
     */
    private suspend fun notifyOnChange(
        siteId: String,
        workersState: StateFlow<Outcome<List<Worker>>?>,
        alertsState: StateFlow<Outcome<List<Alert>>?>,
    ) {
        var first = true
        combine(alertsState, workersState) { alerts, workers -> alerts to workers }
            .collect { (alertsOutcome, workersOutcome) ->
                val alerts = (alertsOutcome as? Outcome.Success)?.data
                    ?.filter { it.siteId == null || it.siteId == siteId }
                    ?: return@collect
                val names = (workersOutcome as? Outcome.Success)?.data.orEmpty()
                    .associate { it.workerId to it.fullName.ifBlank { "Worker ${it.workerId}" } }
                for (alert in alerts) {
                    val seen = notified[alert.alertId]
                    notified[alert.alertId] = alert.severity
                    val name = names[alert.workerId] ?: "Worker ${alert.workerId}"
                    // Once any officer acknowledges it, stop the siren on every phone, and
                    // tell the others who has it so they can stand down.
                    if (alert.status != AlertStatus.ACTIVE) {
                        if (responded.add(alert.alertId)) {
                            NotificationHelper.cancel(context, alert.alertId)
                            if (!first && alert.severity != AlertSeverity.WARNING &&
                                alert.acknowledgedBy != authRepository.currentUserId()
                            ) {
                                notifyResponding(alert, name)
                            }
                        }
                        continue
                    }
                    // Nobody has taken it: sound again on every phone, even if the first
                    // alarm was silenced without anyone acknowledging.
                    if (alert.unansweredAt != null && nagged.add(alert.alertId)) {
                        if (!first) notifyUnanswered(alert, name)
                        continue
                    }
                    if (first || seen == alert.severity) continue
                    if (seen != null && alert.severity.ordinal <= seen.ordinal) continue
                    notify(alert, name)
                }
                // Resolved alerts leave the active list; silence those too.
                val open = alerts.mapTo(HashSet()) { it.alertId }
                (notified.keys - open).forEach { id ->
                    NotificationHelper.cancel(context, id)
                    notified.remove(id)
                }
                nagged.retainAll(open)
                responded.retainAll(open)
                first = false
            }
    }

    private fun notifyResponding(alert: Alert, workerName: String) {
        NotificationHelper.show(
            context = context,
            title = "${alert.acknowledgedByName ?: "An officer"} is responding",
            body = "$workerName: ${alert.message.ifBlank { "safety alert" }}",
            channelId = NotificationChannels.WARNING,
            type = "ALERT",
            targetId = alert.alertId,
            workerId = alert.workerId,
            silent = true,
        )
    }

    private fun notifyUnanswered(alert: Alert, workerName: String) {
        val what = when (alert.type) {
            AlertType.FALL_DETECTED -> "$workerName's fall"
            AlertType.EMERGENCY_REQUEST -> "$workerName's emergency call"
            else -> "$workerName's alert"
        }
        NotificationHelper.show(
            context = context,
            title = "⚠ Nobody has responded",
            body = "No officer has taken $what yet. Tap \"I'm responding\" if you're going.",
            channelId = NotificationChannels.EMERGENCY,
            type = "ALERT",
            targetId = alert.alertId,
            workerId = alert.workerId,
        )
    }

    private fun notify(alert: Alert, workerName: String) {
        val emergency = alert.severity == AlertSeverity.EMERGENCY
        val (title, body) = when {
            alert.type == AlertType.FALL_DETECTED && emergency ->
                "⚠ EMERGENCY" to "$workerName fell and hasn't responded."
            alert.type == AlertType.FALL_DETECTED ->
                "⚠ Fall detected" to "$workerName may have fallen. Check on them now."
            alert.type == AlertType.EMERGENCY_REQUEST ->
                "⚠ EMERGENCY" to "$workerName pressed the emergency button."
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
}
