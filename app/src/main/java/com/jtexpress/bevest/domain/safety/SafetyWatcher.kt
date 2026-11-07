package com.jtexpress.bevest.domain.safety

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.mapper.toWorker
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class FallPopupState(
    val alert: Alert,
    val workerName: String,
    val vestId: String?,
    val siteName: String? = null,
    val floorNumber: String? = null,
)

/**
 * Fall detection, Worker Trigger (3s button press on D15), No Safety Response (15s timeout),
 * and escalation, run on the SSO's phone.
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alertMutex = Mutex()
    private var jobs: List<Job> = emptyList()
    private var watchingSite: String? = null
    private var isStarted: Boolean = false

    /** Alerts already notified about, keyed by id, with the severity they had then. */
    private val notified = mutableMapOf<String, AlertSeverity>()

    /** Last fall reading timestamp already handled per worker, preventing resolved falls from re-opening. */
    private val lastHandledFallTimestamp = mutableMapOf<String, Long>()

    /** Workers currently in a continuous fall-reading burst (rising-edge detector). */
    private val currentlyFalling = mutableSetOf<String>()

    /** Per-worker cooldown window (epoch ms) during which repeated ESP32 fall packets are ignored. */
    private val fallCooldownUntil = mutableMapOf<String, Long>()

    /** Tracks workerId for each active fall alertId so resolving from any screen starts cooldown. */
    private val activeFallAlertWorkers = mutableMapOf<String, String>()

    /** Workers currently in a Worker Trigger (3s button hold) burst. */
    private val currentlyTriggering = mutableSetOf<String>()

    /** Per-worker cooldown window for Worker Trigger button alerts. */
    private val triggerCooldownUntil = mutableMapOf<String, Long>()

    /** Tracks workerId for each active Worker Trigger alertId. */
    private val activeTriggerAlertWorkers = mutableMapOf<String, String>()

    /** Workers currently in a No Safety Response (15s timeout) burst. */
    private val currentlyNoResponse = mutableSetOf<String>()

    /** Per-worker cooldown window for No Safety Response alerts. */
    private val noResponseCooldownUntil = mutableMapOf<String, Long>()

    /** Tracks workerId for each active No Safety Response alertId. */
    private val activeNoResponseAlertWorkers = mutableMapOf<String, String>()

    /** Alert IDs whose pop-up was dismissed at the given severity. */
    private val dismissedPopups = mutableMapOf<String, AlertSeverity>()

    /** Cached siteId -> siteName from Firestore `projects` collection. */
    private val siteNamesCache = mutableMapOf<String, String>()

    private val _fallPopup = MutableStateFlow<FallPopupState?>(null)
    val fallPopup: StateFlow<FallPopupState?> = _fallPopup.asStateFlow()

    private suspend fun resolveSiteName(siteId: String?): String? {
        val clean = siteId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        siteNamesCache[clean]?.let { return it }
        val fetched = runCatching {
            firestore.collection(FirebasePaths.PROJECTS).document(clean).get().await()
                .getString("name")?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: clean
        siteNamesCache[clean] = fetched
        return fetched
    }

    fun start(siteId: String?) {
        if (isStarted && siteId == watchingSite && jobs.isNotEmpty()) return
        stop()
        isStarted = true
        watchingSite = siteId
        jobs = listOf(watchReadings(siteId), watchAlerts(siteId))
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        isStarted = false
        watchingSite = null
        notified.clear()
        lastHandledFallTimestamp.clear()
        currentlyFalling.clear()
        fallCooldownUntil.clear()
        activeFallAlertWorkers.clear()
        currentlyTriggering.clear()
        triggerCooldownUntil.clear()
        activeTriggerAlertWorkers.clear()
        currentlyNoResponse.clear()
        noResponseCooldownUntil.clear()
        activeNoResponseAlertWorkers.clear()
        dismissedPopups.clear()
        _fallPopup.value = null
    }

    fun dismissFallPopup(alert: Alert) {
        dismissedPopups[alert.alertId] = alert.severity
        val cooldown = System.currentTimeMillis() + FALL_COOLDOWN_MS
        when (alert.type) {
            AlertType.EMERGENCY_REQUEST -> triggerCooldownUntil[alert.workerId] = cooldown
            AlertType.NO_SAFETY_RESPONSE -> noResponseCooldownUntil[alert.workerId] = cooldown
            else -> fallCooldownUntil[alert.workerId] = cooldown
        }
        NotificationHelper.cancel(context, alert.alertId)
        if (_fallPopup.value?.alert?.alertId == alert.alertId) {
            _fallPopup.value = null
        }
    }

    fun acknowledgeFall(alert: Alert, byUserId: String) {
        dismissedPopups[alert.alertId] = AlertSeverity.EMERGENCY
        val cooldown = System.currentTimeMillis() + FALL_COOLDOWN_MS
        when (alert.type) {
            AlertType.EMERGENCY_REQUEST -> {
                triggerCooldownUntil[alert.workerId] = cooldown
                currentlyTriggering.add(alert.workerId)
            }
            AlertType.NO_SAFETY_RESPONSE -> {
                noResponseCooldownUntil[alert.workerId] = cooldown
                currentlyNoResponse.add(alert.workerId)
            }
            else -> {
                fallCooldownUntil[alert.workerId] = cooldown
                currentlyFalling.add(alert.workerId)
            }
        }
        dismissFallPopup(alert)
        scope.launch {
            runCatching {
                alertRepository.acknowledgeAlert(alert.alertId, byUserId)
                monitoringRepository.setSafetyResponse(alert.workerId, SafetyResponseState.NONE)
            }
        }
    }

    fun resolveFall(alert: Alert, byUserId: String) {
        dismissedPopups[alert.alertId] = AlertSeverity.EMERGENCY
        val cooldown = System.currentTimeMillis() + FALL_COOLDOWN_MS
        when (alert.type) {
            AlertType.EMERGENCY_REQUEST -> {
                triggerCooldownUntil[alert.workerId] = cooldown
                currentlyTriggering.add(alert.workerId)
            }
            AlertType.NO_SAFETY_RESPONSE -> {
                noResponseCooldownUntil[alert.workerId] = cooldown
                currentlyNoResponse.add(alert.workerId)
            }
            else -> {
                fallCooldownUntil[alert.workerId] = cooldown
                currentlyFalling.add(alert.workerId)
            }
        }
        dismissFallPopup(alert)
        scope.launch {
            runCatching {
                alertRepository.resolveAlert(alert.alertId, byUserId, "Resolved from alert pop-up")
                monitoringRepository.setSafetyResponse(alert.workerId, SafetyResponseState.NONE)
            }
        }
    }

    // ------------------------------------------------------------ detection

    private fun watchReadings(siteId: String?) = scope.launch {
        val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(TICK_MS) } }
        val targetSite = siteId?.takeIf { it.isNotBlank() }
        workerRepository.observeWorkers(contractorId = null, siteId = targetSite)
            .flatMapLatest { outcome ->
                val workers = (outcome as? Outcome.Success)?.data.orEmpty()
                if (workers.isEmpty()) return@flatMapLatest flowOf(null)
                combine(
                    monitoringRepository.observeReadings(workers.map { it.workerId }),
                    settingsRepository.observeThresholds(),
                    alertRepository.observeActiveAlerts(null),
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

            // 1. Fall Detection
            val fell = reading.fallDetected || reading.motionState == MotionState.FALL_DETECTED
            if (!fell) {
                currentlyFalling.remove(worker.workerId)
                lastHandledFallTimestamp.remove(worker.workerId)
            }

            val fallAlert = s.alerts.firstOrNull {
                it.workerId == worker.workerId &&
                    it.type == AlertType.FALL_DETECTED &&
                    it.status == AlertStatus.ACTIVE
            }
            when {
                fallAlert == null -> {
                    val lastHandled = lastHandledFallTimestamp[worker.workerId]
                    val cooldownUntil = fallCooldownUntil[worker.workerId] ?: 0L
                    val inBurst = worker.workerId in currentlyFalling
                    if (fell && !inBurst && s.now >= cooldownUntil && (reading.timestamp <= 0L || lastHandled != reading.timestamp)) {
                        val opened = openFallAlert(worker, reading)
                        if (opened) {
                            currentlyFalling.add(worker.workerId)
                            if (reading.timestamp > 0L) {
                                lastHandledFallTimestamp[worker.workerId] = reading.timestamp
                            }
                            fallCooldownUntil[worker.workerId] = s.now + FALL_COOLDOWN_MS
                        }
                    } else if (fell) {
                        currentlyFalling.add(worker.workerId)
                        if (reading.timestamp > 0L) {
                            lastHandledFallTimestamp[worker.workerId] = reading.timestamp
                        }
                    }
                }

                fallAlert.status == AlertStatus.ACTIVE &&
                    fallAlert.severity != AlertSeverity.EMERGENCY &&
                    reading.safetyResponse != SafetyResponseState.ACKNOWLEDGED &&
                    s.now - fallAlert.createdAt > s.thresholds.responseTimeoutSeconds * 1000L ->
                    escalate(worker, fallAlert, reading)
            }

            // 2. Function 2: Worker Trigger (3-second hold on D15 -> safetyResponse == EMERGENCY_REQUESTED)
            val workerTriggered = reading.safetyResponse == SafetyResponseState.EMERGENCY_REQUESTED
            if (!workerTriggered) {
                currentlyTriggering.remove(worker.workerId)
            }

            val triggerAlert = s.alerts.firstOrNull {
                it.workerId == worker.workerId &&
                    it.type == AlertType.EMERGENCY_REQUEST &&
                    it.status == AlertStatus.ACTIVE
            }
            if (triggerAlert == null) {
                val cooldownUntil = triggerCooldownUntil[worker.workerId] ?: 0L
                val inTriggerBurst = worker.workerId in currentlyTriggering
                if (workerTriggered && !inTriggerBurst && s.now >= cooldownUntil) {
                    val opened = openWorkerTriggerAlert(worker, reading)
                    if (opened) {
                        currentlyTriggering.add(worker.workerId)
                        triggerCooldownUntil[worker.workerId] = s.now + FALL_COOLDOWN_MS
                    }
                } else if (workerTriggered) {
                    currentlyTriggering.add(worker.workerId)
                }
            } else if (workerTriggered) {
                val cooldownUntil = triggerCooldownUntil[worker.workerId] ?: 0L
                val inTriggerBurst = worker.workerId in currentlyTriggering
                if (!inTriggerBurst && s.now >= cooldownUntil) {
                    currentlyTriggering.add(worker.workerId)
                    triggerCooldownUntil[worker.workerId] = s.now + FALL_COOLDOWN_MS
                    dismissedPopups.remove(triggerAlert.alertId)
                    showPopupAndNotify(triggerAlert, worker, reading.vestId)
                    scheduleResetSafetyResponse(worker.workerId)
                }
            }

            // 3. Function 1 Timeout: No Response to Initiate Safety Response (15-second timeout -> NO_RESPONSE)
            val noSafetyResponse = reading.safetyResponse == SafetyResponseState.NO_RESPONSE
            if (!noSafetyResponse) {
                currentlyNoResponse.remove(worker.workerId)
            }

            val noResponseAlert = s.alerts.firstOrNull {
                it.workerId == worker.workerId &&
                    it.type == AlertType.NO_SAFETY_RESPONSE &&
                    it.status == AlertStatus.ACTIVE
            }
            if (noResponseAlert == null) {
                val cooldownUntil = noResponseCooldownUntil[worker.workerId] ?: 0L
                val inNoRespBurst = worker.workerId in currentlyNoResponse
                if (noSafetyResponse && !inNoRespBurst && s.now >= cooldownUntil) {
                    val opened = openNoSafetyResponseAlert(worker, reading)
                    if (opened) {
                        currentlyNoResponse.add(worker.workerId)
                        noResponseCooldownUntil[worker.workerId] = s.now + FALL_COOLDOWN_MS
                    }
                } else if (noSafetyResponse) {
                    currentlyNoResponse.add(worker.workerId)
                }
            } else if (noSafetyResponse) {
                val cooldownUntil = noResponseCooldownUntil[worker.workerId] ?: 0L
                val inNoRespBurst = worker.workerId in currentlyNoResponse
                if (!inNoRespBurst && s.now >= cooldownUntil) {
                    currentlyNoResponse.add(worker.workerId)
                    noResponseCooldownUntil[worker.workerId] = s.now + FALL_COOLDOWN_MS
                    dismissedPopups.remove(noResponseAlert.alertId)
                    showPopupAndNotify(noResponseAlert, worker, reading.vestId)
                    scheduleResetSafetyResponse(worker.workerId)
                }
            }
        }
    }

    /**
     * Directly ensures a No Safety Response alert is registered and its pop-up shown when the
     * 15-second Initiate Safety Response countdown expires without worker acknowledgment.
     */
    suspend fun ensureNoSafetyResponseAlert(workerId: String, currentReading: SensorReading? = null) {
        val worker = runCatching {
            val snap = firestore.collection(FirebasePaths.WORKERS).document(workerId).get().await()
            if (snap.exists()) snap.toWorker() else null
        }.getOrNull() ?: Worker(
            workerId = workerId,
            firstName = "Worker",
            lastName = workerId,
            phone = "",
            birthday = null,
            age = null,
            floorNumber = null,
            jobPosition = null,
            photoUrl = null,
            contractorId = null,
            siteId = watchingSite,
            assignedVestId = null,
            currentStatus = SafetyStatus.EMERGENCY,
            active = true,
        )

        val reading = currentReading ?: SensorReading(
            workerId = workerId,
            vestId = worker.assignedVestId.orEmpty(),
            heartRate = null,
            temperature = null,
            motionState = MotionState.STATIONARY,
            fallDetected = false,
            latitude = null,
            longitude = null,
            batteryPercent = null,
            wifiRssi = null,
            safetyResponse = SafetyResponseState.NO_RESPONSE,
            timestamp = System.currentTimeMillis(),
        )
        val now = System.currentTimeMillis()
        val opened = openNoSafetyResponseAlert(worker, reading)
        if (opened) {
            currentlyNoResponse.add(workerId)
            noResponseCooldownUntil[workerId] = now + FALL_COOLDOWN_MS
        }
    }

    private fun scheduleResetSafetyResponse(workerId: String) {
        scope.launch {
            delay(3_500L)
            runCatching {
                monitoringRepository.setSafetyResponse(workerId, SafetyResponseState.NONE)
            }
        }
    }

    private suspend fun showPopupAndNotify(alert: Alert, worker: Worker, fallbackVestId: String?) {
        val workerName = worker.fullName.ifBlank { "Worker ${worker.workerId}" }
        val resolvedSiteName = alert.siteName?.takeIf { it.isNotBlank() }
            ?: resolveSiteName(alert.siteId?.takeIf { it.isNotBlank() } ?: worker.siteId)
        val resolvedFloor = alert.floorNumber?.takeIf { it.isNotBlank() }
            ?: worker.floorNumber?.takeIf { it.isNotBlank() }
        val vestId = alert.vestId.ifBlank { worker.assignedVestId ?: fallbackVestId }

        notified[alert.alertId] = alert.severity
        dismissedPopups.remove(alert.alertId)
        _fallPopup.value = FallPopupState(
            alert = alert,
            workerName = workerName,
            vestId = vestId,
            siteName = resolvedSiteName,
            floorNumber = resolvedFloor,
        )
        runCatching { notify(alert, workerName) }
    }

    /**
     * Opens the fall alert and its linked incident record, and immediately triggers the pop-up.
     */
    private suspend fun openFallAlert(worker: Worker, reading: SensorReading): Boolean = alertMutex.withLock {
        runCatching {
            val siteName = resolveSiteName(worker.siteId)
            val workerRef = firestore.collection(FirebasePaths.WORKERS).document(worker.workerId)
            val now = System.currentTimeMillis()

            val existingId = runCatching { workerRef.get().await().getString("openFallAlertId") }.getOrNull()
            if (!existingId.isNullOrBlank()) {
                val existing = runCatching {
                    firestore.collection(FirebasePaths.ALERTS).document(existingId).get().await()
                }.getOrNull()
                if (existing != null && existing.exists()) {
                    val status = AlertStatus.fromRaw(existing.getString("status"))
                    val existingCreatedAt = (existing.get("createdAt") as? Number)?.toLong() ?: 0L
                    if (status == AlertStatus.ACTIVE && (now - existingCreatedAt) < FALL_COOLDOWN_MS) {
                        return@withLock false
                    }
                }
            }

            val alertRef = firestore.collection(FirebasePaths.ALERTS).document()
            val incidentRef = firestore.collection(FirebasePaths.INCIDENTS).document(alertRef.id)
            val vestId = (worker.assignedVestId ?: reading.vestId).orEmpty()

            val batch = firestore.batch()
            batch.set(
                alertRef,
                mapOf(
                    "workerId" to worker.workerId,
                    "vestId" to vestId,
                    "floorNumber" to worker.floorNumber,
                    "siteId" to worker.siteId,
                    "siteName" to siteName,
                    "contractorId" to worker.contractorId,
                    "type" to AlertType.FALL_DETECTED.name,
                    "severity" to AlertSeverity.DANGER.name,
                    "status" to AlertStatus.ACTIVE.name,
                    "message" to "Fall detected",
                    "incidentId" to alertRef.id,
                    "createdAt" to now,
                ),
            )
            batch.set(
                incidentRef,
                mapOf(
                    "workerId" to worker.workerId,
                    "vestId" to vestId,
                    "floorNumber" to worker.floorNumber,
                    "siteId" to worker.siteId,
                    "siteName" to siteName,
                    "contractorId" to worker.contractorId,
                    "type" to AlertType.FALL_DETECTED.name,
                    "severity" to AlertSeverity.DANGER.name,
                    "alertId" to alertRef.id,
                    "heartRate" to reading.heartRate,
                    "temperature" to reading.temperature,
                    "latitude" to reading.latitude,
                    "longitude" to reading.longitude,
                    "createdAt" to now,
                    "outcome" to null,
                    "resolutionNotes" to null,
                ),
            )
            batch.commit().await()
            runCatching { workerRef.update("openFallAlertId", alertRef.id).await() }

            val createdAlert = Alert(
                alertId = alertRef.id,
                workerId = worker.workerId,
                vestId = vestId,
                siteId = worker.siteId,
                siteName = siteName,
                floorNumber = worker.floorNumber,
                type = AlertType.FALL_DETECTED,
                severity = AlertSeverity.DANGER,
                status = AlertStatus.ACTIVE,
                message = "Fall detected",
                createdAt = now,
                acknowledgedAt = null,
                resolvedAt = null,
                resolvedBy = null,
                resolutionNotes = null,
            )
            activeFallAlertWorkers[createdAlert.alertId] = worker.workerId
            showPopupAndNotify(createdAlert, worker, reading.vestId)
            true
        }.getOrDefault(false)
    }

    /**
     * Opens the Worker Trigger alert (Function 2: worker held D15 button for 3 seconds).
     * Title: "Worker Trigger" (via AlertType.EMERGENCY_REQUEST.label()).
     * Message from the vest: "Worker Trigger. Please check with Worker."
     */
    private suspend fun openWorkerTriggerAlert(worker: Worker, reading: SensorReading): Boolean = alertMutex.withLock {
        runCatching {
            val siteName = resolveSiteName(worker.siteId)
            val workerRef = firestore.collection(FirebasePaths.WORKERS).document(worker.workerId)
            val now = System.currentTimeMillis()

            val existingId = runCatching { workerRef.get().await().getString("openTriggerAlertId") }.getOrNull()
            if (!existingId.isNullOrBlank()) {
                val existing = runCatching {
                    firestore.collection(FirebasePaths.ALERTS).document(existingId).get().await()
                }.getOrNull()
                if (existing != null && existing.exists()) {
                    val status = AlertStatus.fromRaw(existing.getString("status"))
                    val existingCreatedAt = (existing.get("createdAt") as? Number)?.toLong() ?: 0L
                    if (status == AlertStatus.ACTIVE && (now - existingCreatedAt) < FALL_COOLDOWN_MS) {
                        return@withLock false
                    }
                }
            }

            val alertRef = firestore.collection(FirebasePaths.ALERTS).document()
            val incidentRef = firestore.collection(FirebasePaths.INCIDENTS).document(alertRef.id)
            val vestId = (worker.assignedVestId ?: reading.vestId).orEmpty()

            val batch = firestore.batch()
            batch.set(
                alertRef,
                mapOf(
                    "workerId" to worker.workerId,
                    "vestId" to vestId,
                    "floorNumber" to worker.floorNumber,
                    "siteId" to worker.siteId,
                    "siteName" to siteName,
                    "contractorId" to worker.contractorId,
                    "type" to AlertType.EMERGENCY_REQUEST.name,
                    "severity" to AlertSeverity.EMERGENCY.name,
                    "status" to AlertStatus.ACTIVE.name,
                    "message" to "Worker Trigger. Please check with Worker.",
                    "incidentId" to alertRef.id,
                    "createdAt" to now,
                ),
            )
            batch.set(
                incidentRef,
                mapOf(
                    "workerId" to worker.workerId,
                    "vestId" to vestId,
                    "floorNumber" to worker.floorNumber,
                    "siteId" to worker.siteId,
                    "siteName" to siteName,
                    "contractorId" to worker.contractorId,
                    "type" to AlertType.EMERGENCY_REQUEST.name,
                    "severity" to AlertSeverity.EMERGENCY.name,
                    "alertId" to alertRef.id,
                    "heartRate" to reading.heartRate,
                    "temperature" to reading.temperature,
                    "latitude" to reading.latitude,
                    "longitude" to reading.longitude,
                    "createdAt" to now,
                    "outcome" to null,
                    "resolutionNotes" to null,
                ),
            )
            batch.commit().await()
            runCatching { workerRef.update("openTriggerAlertId", alertRef.id).await() }

            val createdAlert = Alert(
                alertId = alertRef.id,
                workerId = worker.workerId,
                vestId = vestId,
                siteId = worker.siteId,
                siteName = siteName,
                floorNumber = worker.floorNumber,
                type = AlertType.EMERGENCY_REQUEST,
                severity = AlertSeverity.EMERGENCY,
                status = AlertStatus.ACTIVE,
                message = "Worker Trigger. Please check with Worker.",
                createdAt = now,
                acknowledgedAt = null,
                resolvedAt = null,
                resolvedBy = null,
                resolutionNotes = null,
            )
            activeTriggerAlertWorkers[createdAlert.alertId] = worker.workerId
            showPopupAndNotify(createdAlert, worker, reading.vestId)
            scheduleResetSafetyResponse(worker.workerId)
            true
        }.getOrDefault(false)
    }

    /**
     * Opens the No Safety Response alert when a worker does not respond within 15 seconds
     * to an initiated safety response check.
     */
    private suspend fun openNoSafetyResponseAlert(worker: Worker, reading: SensorReading): Boolean = alertMutex.withLock {
        runCatching {
            val siteName = resolveSiteName(worker.siteId)
            val workerRef = firestore.collection(FirebasePaths.WORKERS).document(worker.workerId)
            val now = System.currentTimeMillis()

            val existingId = runCatching { workerRef.get().await().getString("openNoResponseAlertId") }.getOrNull()
            if (!existingId.isNullOrBlank()) {
                val existing = runCatching {
                    firestore.collection(FirebasePaths.ALERTS).document(existingId).get().await()
                }.getOrNull()
                if (existing != null && existing.exists()) {
                    val status = AlertStatus.fromRaw(existing.getString("status"))
                    val existingCreatedAt = (existing.get("createdAt") as? Number)?.toLong() ?: 0L
                    if (status == AlertStatus.ACTIVE && (now - existingCreatedAt) < FALL_COOLDOWN_MS) {
                        return@withLock false
                    }
                }
            }

            val alertRef = firestore.collection(FirebasePaths.ALERTS).document()
            val incidentRef = firestore.collection(FirebasePaths.INCIDENTS).document(alertRef.id)
            val vestId = (worker.assignedVestId ?: reading.vestId).orEmpty()

            val batch = firestore.batch()
            batch.set(
                alertRef,
                mapOf(
                    "workerId" to worker.workerId,
                    "vestId" to vestId,
                    "floorNumber" to worker.floorNumber,
                    "siteId" to worker.siteId,
                    "siteName" to siteName,
                    "contractorId" to worker.contractorId,
                    "type" to AlertType.NO_SAFETY_RESPONSE.name,
                    "severity" to AlertSeverity.EMERGENCY.name,
                    "status" to AlertStatus.ACTIVE.name,
                    "message" to "Worker is in danger, please check with worker",
                    "incidentId" to alertRef.id,
                    "createdAt" to now,
                ),
            )
            batch.set(
                incidentRef,
                mapOf(
                    "workerId" to worker.workerId,
                    "vestId" to vestId,
                    "floorNumber" to worker.floorNumber,
                    "siteId" to worker.siteId,
                    "siteName" to siteName,
                    "contractorId" to worker.contractorId,
                    "type" to AlertType.NO_SAFETY_RESPONSE.name,
                    "severity" to AlertSeverity.EMERGENCY.name,
                    "alertId" to alertRef.id,
                    "heartRate" to reading.heartRate,
                    "temperature" to reading.temperature,
                    "latitude" to reading.latitude,
                    "longitude" to reading.longitude,
                    "createdAt" to now,
                    "outcome" to null,
                    "resolutionNotes" to null,
                ),
            )
            batch.commit().await()
            runCatching { workerRef.update("openNoResponseAlertId", alertRef.id).await() }

            val createdAlert = Alert(
                alertId = alertRef.id,
                workerId = worker.workerId,
                vestId = vestId,
                siteId = worker.siteId,
                siteName = siteName,
                floorNumber = worker.floorNumber,
                type = AlertType.NO_SAFETY_RESPONSE,
                severity = AlertSeverity.EMERGENCY,
                status = AlertStatus.ACTIVE,
                message = "Worker is in danger, please check with worker",
                createdAt = now,
                acknowledgedAt = null,
                resolvedAt = null,
                resolvedBy = null,
                resolutionNotes = null,
            )
            activeNoResponseAlertWorkers[createdAlert.alertId] = worker.workerId
            showPopupAndNotify(createdAlert, worker, reading.vestId)
            scheduleResetSafetyResponse(worker.workerId)
            true
        }.getOrDefault(false)
    }

    /** Upgrades the fall alert to EMERGENCY and updates its incident, once. */
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
                    "floorNumber" to worker.floorNumber,
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
    private fun watchAlerts(siteId: String?) = scope.launch {
        var first = true
        val targetSite = siteId?.takeIf { it.isNotBlank() }
        combine(
            alertRepository.observeActiveAlerts(targetSite),
            workerRepository.observeWorkers(contractorId = null, siteId = targetSite),
        ) { alerts, workers -> alerts to workers }
            .collect { (alertsOutcome, workersOutcome) ->
                val alerts = (alertsOutcome as? Outcome.Success)?.data ?: return@collect
                val workerList = (workersOutcome as? Outcome.Success)?.data.orEmpty()
                val workersById = workerList.associateBy { it.workerId }
                val names = workerList.associate { it.workerId to it.fullName.ifBlank { "Worker ${it.workerId}" } }
                val workerVests = workerList.associate { it.workerId to it.assignedVestId }

                for (alert in alerts) {
                    when (alert.type) {
                        AlertType.FALL_DETECTED -> activeFallAlertWorkers[alert.alertId] = alert.workerId
                        AlertType.EMERGENCY_REQUEST -> activeTriggerAlertWorkers[alert.alertId] = alert.workerId
                        AlertType.NO_SAFETY_RESPONSE -> activeNoResponseAlertWorkers[alert.alertId] = alert.workerId
                        else -> Unit
                    }
                    val seen = notified[alert.alertId]
                    notified[alert.alertId] = alert.severity
                    // Once any officer acknowledges it, stop the vibration on every phone.
                    if (alert.status != AlertStatus.ACTIVE) {
                        NotificationHelper.cancel(context, alert.alertId)
                        continue
                    }
                    if (first || seen == alert.severity) continue
                    if (seen != null && alert.severity.ordinal <= seen.ordinal) continue
                    notify(alert, names[alert.workerId] ?: "Worker ${alert.workerId}")
                }
                // Resolved alerts leave the active list; silence those too and start cooldown for that worker.
                val open = alerts.mapTo(HashSet()) { it.alertId }
                (activeFallAlertWorkers.keys - open).forEach { id ->
                    activeFallAlertWorkers.remove(id)?.let { resolvedWorkerId ->
                        fallCooldownUntil[resolvedWorkerId] = System.currentTimeMillis() + FALL_COOLDOWN_MS
                        currentlyFalling.add(resolvedWorkerId)
                    }
                }
                (activeTriggerAlertWorkers.keys - open).forEach { id ->
                    activeTriggerAlertWorkers.remove(id)?.let { resolvedWorkerId ->
                        triggerCooldownUntil[resolvedWorkerId] = System.currentTimeMillis() + FALL_COOLDOWN_MS
                        currentlyTriggering.add(resolvedWorkerId)
                    }
                }
                (activeNoResponseAlertWorkers.keys - open).forEach { id ->
                    activeNoResponseAlertWorkers.remove(id)?.let { resolvedWorkerId ->
                        noResponseCooldownUntil[resolvedWorkerId] = System.currentTimeMillis() + FALL_COOLDOWN_MS
                        currentlyNoResponse.add(resolvedWorkerId)
                    }
                }
                (notified.keys - open).forEach { id ->
                    NotificationHelper.cancel(context, id)
                    notified.remove(id)
                }

                // Update the in-app Alert Pop-Up Screen with the newest unacknowledged Fall, Worker Trigger, or No Safety Response alert
                val activePopupAlert = alerts.firstOrNull { alert ->
                    (alert.type == AlertType.FALL_DETECTED ||
                        alert.type == AlertType.EMERGENCY_REQUEST ||
                        alert.type == AlertType.NO_SAFETY_RESPONSE) &&
                        alert.status == AlertStatus.ACTIVE &&
                        (dismissedPopups[alert.alertId]?.let { alert.severity.ordinal > it.ordinal } ?: true)
                }
                _fallPopup.value = activePopupAlert?.let { alert ->
                    val w = workersById[alert.workerId]
                    val resolvedSiteName = alert.siteName?.takeIf { it.isNotBlank() }
                        ?: resolveSiteName(alert.siteId ?: w?.siteId ?: targetSite)
                    val resolvedFloor = alert.floorNumber?.takeIf { it.isNotBlank() }
                        ?: w?.floorNumber?.takeIf { it.isNotBlank() }
                    FallPopupState(
                        alert = alert,
                        workerName = names[alert.workerId] ?: "Worker ${alert.workerId}",
                        vestId = alert.vestId.ifBlank { workerVests[alert.workerId] },
                        siteName = resolvedSiteName,
                        floorNumber = resolvedFloor,
                    )
                } ?: _fallPopup.value?.takeIf { current ->
                    val isRecent = System.currentTimeMillis() - current.alert.createdAt < 5_000L
                    val notDismissed = dismissedPopups[current.alert.alertId]?.let {
                        current.alert.severity.ordinal > it.ordinal
                    } ?: true
                    isRecent && notDismissed && current.alert.status == AlertStatus.ACTIVE
                }

                first = false
            }
    }

    private fun notify(alert: Alert, workerName: String) {
        val emergency = alert.severity == AlertSeverity.EMERGENCY
        val (title, body) = when {
            alert.type == AlertType.EMERGENCY_REQUEST ->
                "Worker Trigger" to "$workerName: Worker Trigger. Please check with Worker."
            alert.type == AlertType.NO_SAFETY_RESPONSE ->
                "⚠ No Safety Response" to "$workerName: Worker is in danger, please check with worker."
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
        const val TICK_MS = 3_000L
        /** Cooldown window (20s) to absorb repeated hardware packets without opening duplicate alerts. */
        const val FALL_COOLDOWN_MS = 20_000L
    }
}
