package com.jtexpress.bevest.simulation

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.random.Random

private const val TAG = "BeVestSim"
private const val TICK_MS = 3_000L

/** A safety situation the operator can drive a worker into. */
enum class Scenario(val label: String) {
    HEALTHY("Healthy"),
    HIGH_HEART_RATE("High heart rate"),
    HIGH_TEMPERATURE("High temperature"),
    FALL("Fall detected"),
    INACTIVITY("Prolonged inactivity"),
    EMERGENCY_REQUEST("Manual emergency"),
}

/** What the app is currently showing for one simulated worker. */
data class SimWorker(
    val workerId: String,
    val name: String,
    val vestId: String,
    val siteId: String,
    val contractorId: String,
    val scenario: Scenario = Scenario.HEALTHY,
    val phase: Phase = Phase.NORMAL,
    val heartRate: Int = 78,
    val temperature: Double = 36.8,
    val battery: Int = 96,
    val motion: MotionState = MotionState.MOVING,
) {
    enum class Phase { NORMAL, WARNING, DANGER, EMERGENCY }
}

data class SimulationState(
    val bound: Boolean = false,
    val streaming: Boolean = false,
    val busy: Boolean = false,
    val canStream: Boolean = true,
    val workers: List<SimWorker> = emptyList(),
    val log: List<String> = emptyList(),
    val message: String? = null,
) {
    val liveEmergencies get() = workers.count { it.phase == SimWorker.Phase.EMERGENCY }
    val liveWarnings get() = workers.count {
        it.phase == SimWorker.Phase.WARNING || it.phase == SimWorker.Phase.DANGER
    }
}

/**
 * Stands in for the ESP32 firmware *and* the cloud safety engine while the IoT layer
 * doesn't exist yet, so the app can be demoed as if it were fully connected.
 *
 * - Streams evolving sensor readings to Realtime Database (drives every live screen).
 * - When a scenario escalates, writes the `alerts` / `incidents` documents the cloud
 *   engine would normally create — flagged `simulated = true` so they are isolated from
 *   real data and can be wiped in one tap.
 *
 * A singleton with its own scope: the stream keeps running while you navigate the app.
 */
@Singleton
class SimulationEngine @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val monitoring: MonitoringRepository,
    private val workerRepository: WorkerRepository,
    private val authRepository: AuthRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    /** Mutable per-worker sim data, keyed by workerId. Projected into [state]. */
    private val sim = mutableMapOf<String, MutableSim>()
    private val elapsedTicks = mutableMapOf<String, Int>()
    private val openAlertId = mutableMapOf<String, String>()
    private val incidentRaised = mutableSetOf<String>()

    private var gpsCenter = 10.3157 to 123.8854
    private var boundKey: String? = null
    private var boundContractorId: String? = null
    private var boundSiteId: String? = null

    private val _state = MutableStateFlow(SimulationState())
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    private class MutableSim(
        var w: SimWorker,
        var lat: Double,
        var lng: Double,
    )

    // ---------------------------------------------------------------- bind

    /**
     * Load the workers this operator can drive. Pass a siteId for an SSO (they own
     * live streaming), or a contractorId for a contractor (history + reset only).
     */
    fun bind(contractorId: String?, siteId: String?, canStream: Boolean) {
        val key = "${contractorId.orEmpty()}|${siteId.orEmpty()}"
        if (key == boundKey && sim.isNotEmpty()) {
            _state.update { it.copy(canStream = canStream) }
            return // already bound — keep any running stream intact
        }
        boundKey = key
        boundContractorId = contractorId
        boundSiteId = siteId
        _state.update { it.copy(busy = true, canStream = canStream) }
        scope.launch {
            val outcome = workerRepository.observeWorkers(contractorId, siteId).first()
            val workers = (outcome as? Outcome.Success)?.data.orEmpty().filter { it.active }
            if (workers.isEmpty()) {
                _state.update {
                    it.copy(busy = false, bound = true, message = "No active workers to simulate.")
                }
                return@launch
            }
            workers.firstOrNull()?.let { /* keep default center; could read last GPS */ }
            sim.clear(); elapsedTicks.clear(); openAlertId.clear(); incidentRaised.clear()
            workers.forEach { worker ->
                val sw = SimWorker(
                    workerId = worker.workerId,
                    name = worker.fullName.ifBlank { worker.workerId },
                    vestId = worker.assignedVestId.orEmpty(),
                    siteId = worker.siteId ?: siteId.orEmpty(),
                    contractorId = worker.contractorId ?: contractorId.orEmpty(),
                )
                sim[worker.workerId] = MutableSim(
                    sw,
                    gpsCenter.first + Random.nextDouble(-0.0008, 0.0008),
                    gpsCenter.second + Random.nextDouble(-0.0008, 0.0008),
                )
            }
            publish()
            _state.update { it.copy(busy = false, bound = true) }
        }
    }

    // ---------------------------------------------------------------- streaming

    fun start() {
        if (!state.value.canStream || loop?.isActive == true) return
        val firstId = sim.keys.firstOrNull()
        if (firstId == null) {
            _state.update { it.copy(message = "No workers to stream.") }
            return
        }
        _state.update { it.copy(busy = true) }
        scope.launch {
            // Probe: live streaming needs the Realtime Database to exist and be reachable.
            val probe = monitoring.pushSimulatedReading(
                SensorReading(workerId = firstId, timestamp = System.currentTimeMillis()),
            )
            if (probe is Outcome.Failure) {
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Live streaming needs the Realtime Database. Create it in the " +
                            "Firebase console, re-download google-services.json, and rebuild. " +
                            "Seeding history still works without it.",
                    )
                }
                return@launch
            }
            log("Live stream started — ${sim.size} vest(s) transmitting")
            _state.update { it.copy(streaming = true, busy = false) }
            loop = scope.launch {
                while (isActive) {
                    sim.keys.toList().forEach { id -> tick(id) }
                    publish()
                    delay(TICK_MS)
                }
            }
        }
    }

    fun stop() {
        loop?.cancel(); loop = null
        _state.update { it.copy(streaming = false) }
        log("Live stream stopped")
        scope.launch { sim.keys.forEach { monitoring.clearReading(it) } }
        sim.values.forEach { it.w = it.w.copy(phase = SimWorker.Phase.NORMAL, motion = MotionState.MOVING) }
        elapsedTicks.clear()
        publish()
    }

    fun setScenario(workerId: String, scenario: Scenario) {
        val s = sim[workerId] ?: return
        s.w = s.w.copy(scenario = scenario)
        elapsedTicks[workerId] = 0
        incidentRaised.remove("$workerId:${scenario.name}")
        if (scenario == Scenario.HEALTHY) {
            resolveOpenAlert(workerId, "Condition cleared — worker recovered")
            s.w = s.w.copy(phase = SimWorker.Phase.NORMAL, motion = MotionState.MOVING)
            scope.launch { monitoring.setSafetyResponse(workerId, SafetyResponseState.NONE) }
        }
        log("${s.w.name}: scenario → ${scenario.label}")
        publish()
        if (!state.value.streaming && scenario != Scenario.HEALTHY) start()
    }

    /** Jump a worker straight to a raised incident, skipping the wait. */
    fun escalateNow(workerId: String) {
        val s = sim[workerId] ?: return
        if (s.w.scenario == Scenario.HEALTHY) setScenario(workerId, Scenario.FALL)
        elapsedTicks[workerId] = 999
        scope.launch { tick(workerId); publish() }
    }

    private suspend fun tick(id: String) {
        val s = sim[id] ?: return
        val ticks = (elapsedTicks[id] ?: 0) + 1
        elapsedTicks[id] = ticks
        val sc = s.w.scenario

        // --- evolve vitals ---
        var hr = s.w.heartRate
        var temp = s.w.temperature
        var motion = MotionState.MOVING
        var fall = false
        var response = SafetyResponseState.NONE

        when (sc) {
            Scenario.HEALTHY -> {
                hr = drift(hr, 78, 68, 96, 4)
                temp = drift(temp, 36.8, 36.3, 37.2, 0.1)
            }
            Scenario.HIGH_HEART_RATE -> {
                hr = ramp(hr, target = 132, step = 9).let { drift(it, it, 118, 145, 4) }
                temp = drift(temp, 37.1, 36.6, 37.6, 0.1)
            }
            Scenario.HIGH_TEMPERATURE -> {
                temp = ramp(temp, target = 39.0, step = 0.35).let { drift(it, it, 38.2, 39.8, 0.15) }
                hr = drift(hr, 96, 88, 108, 4)
            }
            Scenario.FALL -> {
                fall = true; motion = MotionState.FALL_DETECTED
                hr = drift(hr, 104, 92, 120, 5)
            }
            Scenario.INACTIVITY -> {
                motion = MotionState.INACTIVE
                hr = drift(hr, 62, 55, 72, 3)
            }
            Scenario.EMERGENCY_REQUEST -> {
                response = SafetyResponseState.EMERGENCY_REQUESTED
                hr = drift(hr, 110, 95, 130, 6)
            }
        }
        val battery = (s.w.battery - if (Random.nextInt(3) == 0) 1 else 0).coerceIn(5, 100)

        // --- gps random walk ---
        s.lat += Random.nextDouble(-0.00025, 0.00025)
        s.lng += Random.nextDouble(-0.00025, 0.00025)

        // --- phase / escalation (fast timeline for demos) ---
        var phase = s.w.phase
        when {
            sc == Scenario.HEALTHY -> phase = SimWorker.Phase.NORMAL
            sc == Scenario.EMERGENCY_REQUEST -> phase = SimWorker.Phase.EMERGENCY
            sc == Scenario.FALL && ticks >= 1 -> phase = if (ticks >= 8) SimWorker.Phase.EMERGENCY else SimWorker.Phase.DANGER
            ticks in 1..2 -> phase = SimWorker.Phase.WARNING
            ticks in 3..9 -> phase = SimWorker.Phase.DANGER
            ticks >= 10 -> phase = SimWorker.Phase.EMERGENCY
        }
        s.w = s.w.copy(
            heartRate = hr, temperature = round1(temp), battery = battery,
            motion = motion, phase = phase,
        )

        // --- write the reading ---
        monitoring.pushSimulatedReading(
            SensorReading(
                workerId = id,
                vestId = s.w.vestId,
                heartRate = hr,
                temperature = round1(temp),
                motionState = motion,
                fallDetected = fall,
                latitude = round5(s.lat),
                longitude = round5(s.lng),
                batteryPercent = battery,
                safetyResponse = if (phase == SimWorker.Phase.EMERGENCY && response == SafetyResponseState.NONE)
                    SafetyResponseState.NO_RESPONSE else response,
                timestamp = System.currentTimeMillis(),
            ),
        )

        // --- raise the records the cloud engine would raise ---
        if (phase == SimWorker.Phase.DANGER && !openAlertId.containsKey(id)) {
            raiseAlert(s.w, AlertSeverity.DANGER)
        }
        if (phase == SimWorker.Phase.EMERGENCY) {
            val key = "$id:${sc.name}"
            if (!incidentRaised.contains(key)) {
                incidentRaised.add(key)
                raiseAlert(s.w, AlertSeverity.EMERGENCY)
                raiseIncident(s.w)
                monitoring.setSafetyResponse(id, SafetyResponseState.NO_RESPONSE)
            }
        }
    }

    private fun publish() {
        _state.update { it.copy(workers = sim.values.map { s -> s.w }) }
    }

    // ---------------------------------------------------------------- records

    private fun typeFor(scenario: Scenario): AlertType = when (scenario) {
        Scenario.HIGH_HEART_RATE -> AlertType.HEART_RATE_WARNING
        Scenario.HIGH_TEMPERATURE -> AlertType.TEMPERATURE_WARNING
        Scenario.FALL -> AlertType.FALL_DETECTED
        Scenario.INACTIVITY -> AlertType.INACTIVITY
        Scenario.EMERGENCY_REQUEST -> AlertType.EMERGENCY_REQUEST
        Scenario.HEALTHY -> AlertType.NO_SAFETY_RESPONSE
    }

    private fun messageFor(w: SimWorker): String = when (w.scenario) {
        Scenario.HIGH_HEART_RATE -> "Elevated heart rate (${w.heartRate} BPM)"
        Scenario.HIGH_TEMPERATURE -> "High body temperature (${round1(w.temperature)} °C)"
        Scenario.FALL -> "Fall detected — no movement since"
        Scenario.INACTIVITY -> "Prolonged inactivity detected"
        Scenario.EMERGENCY_REQUEST -> "Worker pressed the emergency button"
        Scenario.HEALTHY -> "No safety response"
    }

    private fun raiseAlert(w: SimWorker, severity: AlertSeverity) {
        scope.launch {
            try {
                val ref = firestore.collection(FirebasePaths.ALERTS).document()
                ref.set(
                    baseRecord(w) + mapOf(
                        "type" to typeFor(w.scenario).name,
                        "severity" to severity.name,
                        "status" to AlertStatus.ACTIVE.name,
                        "message" to messageFor(w),
                    ),
                ).await()
                openAlertId[w.workerId] = ref.id
                log("${w.name}: ${severity.name} alert raised")
            } catch (e: Exception) {
                Log.e(TAG, "raiseAlert failed", e)
                _state.update { it.copy(message = "Could not write alert: ${e.message}") }
            }
        }
    }

    private fun raiseIncident(w: SimWorker) {
        scope.launch {
            try {
                val ref = firestore.collection(FirebasePaths.INCIDENTS).document()
                ref.set(
                    baseRecord(w) + mapOf(
                        "alertId" to openAlertId[w.workerId],
                        "type" to typeFor(w.scenario).name,
                        "severity" to AlertSeverity.EMERGENCY.name,
                        "heartRate" to w.heartRate,
                        "temperature" to round1(w.temperature),
                        "latitude" to round5(sim[w.workerId]?.lat ?: gpsCenter.first),
                        "longitude" to round5(sim[w.workerId]?.lng ?: gpsCenter.second),
                        "outcome" to null,
                        "resolutionNotes" to null,
                    ),
                ).await()
                log("${w.name}: EMERGENCY incident created")
            } catch (e: Exception) {
                Log.e(TAG, "raiseIncident failed", e)
                _state.update { it.copy(message = "Could not write incident: ${e.message}") }
            }
        }
    }

    private fun resolveOpenAlert(workerId: String, notes: String) {
        val alertId = openAlertId.remove(workerId) ?: return
        scope.launch {
            runCatching {
                firestore.collection(FirebasePaths.ALERTS).document(alertId).update(
                    mapOf(
                        "status" to AlertStatus.RESOLVED.name,
                        "resolvedAt" to System.currentTimeMillis(),
                        "resolutionNotes" to notes,
                        "resolvedBy" to (authRepository.currentUserId() ?: "simulator"),
                    ),
                ).await()
            }
        }
    }

    private fun baseRecord(w: SimWorker): Map<String, Any?> = mapOf(
        "workerId" to w.workerId,
        "vestId" to w.vestId,
        "siteId" to w.siteId,
        "contractorId" to w.contractorId,
        "createdAt" to System.currentTimeMillis(),
        "simulated" to true,
    )

    // ---------------------------------------------------------------- history

    /** Fill the last [months] months with plausible incident + alert history. */
    fun seedHistory(months: Int = 6) {
        if (sim.isEmpty()) {
            _state.update { it.copy(message = "Bind to a site first.") }
            return
        }
        _state.update { it.copy(busy = true) }
        scope.launch {
            var written = 0
            try {
                val batch = firestore.batch()
                val now = System.currentTimeMillis()
                val monthMs = 30L * 24 * 3600 * 1000
                sim.values.map { it.w }.forEach { w ->
                    val count = Random.nextInt(4, 9)
                    repeat(count) {
                        val ageMs = Random.nextLong(0, months * monthMs)
                        val createdAt = now - ageMs
                        val (type, severity) = randomEvent()
                        val resolved = Random.nextInt(100) < 85
                        val hr = if (type == AlertType.HEART_RATE_WARNING) Random.nextInt(118, 150) else Random.nextInt(70, 105)
                        val temp = if (type == AlertType.TEMPERATURE_WARNING) round1(Random.nextDouble(38.2, 39.9)) else round1(Random.nextDouble(36.4, 37.4))

                        val incidentRef = firestore.collection(FirebasePaths.INCIDENTS).document()
                        batch.set(
                            incidentRef,
                            mapOf(
                                "workerId" to w.workerId,
                                "vestId" to w.vestId,
                                "siteId" to w.siteId,
                                "contractorId" to w.contractorId,
                                "type" to type.name,
                                "severity" to severity.name,
                                "heartRate" to hr,
                                "temperature" to temp,
                                "latitude" to round5(gpsCenter.first + Random.nextDouble(-0.001, 0.001)),
                                "longitude" to round5(gpsCenter.second + Random.nextDouble(-0.001, 0.001)),
                                "createdAt" to createdAt,
                                "outcome" to if (resolved) randomOutcome() else null,
                                "resolutionNotes" to if (resolved) "Reviewed by site safety officer." else null,
                                "resolvedBy" to if (resolved) (authRepository.currentUserId() ?: "simulator") else null,
                                "simulated" to true,
                            ),
                        )
                        val alertRef = firestore.collection(FirebasePaths.ALERTS).document()
                        batch.set(
                            alertRef,
                            mapOf(
                                "workerId" to w.workerId,
                                "vestId" to w.vestId,
                                "siteId" to w.siteId,
                                "contractorId" to w.contractorId,
                                "type" to type.name,
                                "severity" to severity.name,
                                "status" to if (resolved) AlertStatus.RESOLVED.name else AlertStatus.ACTIVE.name,
                                "message" to "${type.name.replace('_', ' ').lowercase(Locale.US)} · ${w.name}",
                                "createdAt" to createdAt,
                                "incidentId" to incidentRef.id,
                                "simulated" to true,
                            ),
                        )
                        written += 2
                    }
                }
                batch.commit().await()
                log("Seeded $written history records across ${months} months")
                _state.update { it.copy(busy = false, message = "Added ${written / 2} incidents to history.") }
            } catch (e: Exception) {
                Log.e(TAG, "seedHistory failed", e)
                _state.update { it.copy(busy = false, message = "Seed failed: ${e.message}") }
            }
        }
    }

    // ---------------------------------------------------------------- reset

    /**
     * Stop everything and delete every `simulated == true` record in *this operator's
     * scope*. The scope filter (siteId for an SSO, contractorId for a contractor) is
     * required — a bare `where simulated == true` query is rejected by the Firestore
     * rules because it can't be proven to return only readable documents.
     */
    fun reset() {
        stop()
        val scopeField: String
        val scopeValue: String
        when {
            !boundSiteId.isNullOrBlank() -> {
                scopeField = "siteId"; scopeValue = boundSiteId!!
            }
            !boundContractorId.isNullOrBlank() -> {
                scopeField = "contractorId"; scopeValue = boundContractorId!!
            }
            else -> {
                _state.update { it.copy(message = "Bind to a site or contractor before resetting.") }
                return
            }
        }
        _state.update { it.copy(busy = true) }
        scope.launch {
            var deleted = 0
            try {
                for (collection in listOf(FirebasePaths.ALERTS, FirebasePaths.INCIDENTS)) {
                    val snap = firestore.collection(collection)
                        .whereEqualTo("simulated", true)
                        .whereEqualTo(scopeField, scopeValue)
                        .get().await()
                    snap.documents.chunked(400).forEach { chunk ->
                        val batch = firestore.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                        deleted += chunk.size
                    }
                }
                sim.keys.forEach { monitoring.clearReading(it) }
                sim.values.forEach { it.w = it.w.copy(scenario = Scenario.HEALTHY, phase = SimWorker.Phase.NORMAL) }
                openAlertId.clear(); incidentRaised.clear(); elapsedTicks.clear()
                publish()
                log("Reset — removed $deleted simulated records")
                _state.update { it.copy(busy = false, message = "Cleared all simulated data.") }
            } catch (e: Exception) {
                Log.e(TAG, "reset failed", e)
                _state.update { it.copy(busy = false, message = "Reset failed: ${e.message}") }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---------------------------------------------------------------- helpers

    private fun log(line: String) {
        val stamped = "${time()}  $line"
        Log.d(TAG, line)
        _state.update { it.copy(log = (listOf(stamped) + it.log).take(20)) }
    }

    private fun time(): String {
        val c = Calendar.getInstance()
        return "%02d:%02d:%02d".format(
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
    }

    private fun drift(current: Int, mean: Int, min: Int, max: Int, jitter: Int): Int {
        val pull = (mean - current) / 3
        return (current + pull + Random.nextInt(-jitter, jitter + 1)).coerceIn(min, max)
    }

    private fun drift(current: Double, mean: Double, min: Double, max: Double, jitter: Double): Double {
        val pull = (mean - current) / 3
        return (current + pull + Random.nextDouble(-jitter, jitter)).coerceIn(min, max)
    }

    private fun ramp(current: Int, target: Int, step: Int): Int =
        if (abs(target - current) <= step) target
        else current + if (target > current) step else -step

    private fun ramp(current: Double, target: Double, step: Double): Double =
        if (abs(target - current) <= step) target
        else current + if (target > current) step else -step

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
    private fun round5(v: Double) = Math.round(v * 100000) / 100000.0

    private fun randomEvent(): Pair<AlertType, AlertSeverity> {
        val roll = Random.nextInt(100)
        return when {
            roll < 35 -> AlertType.HEART_RATE_WARNING to AlertSeverity.WARNING
            roll < 60 -> AlertType.TEMPERATURE_WARNING to AlertSeverity.WARNING
            roll < 78 -> AlertType.INACTIVITY to AlertSeverity.WARNING
            roll < 92 -> AlertType.FALL_DETECTED to AlertSeverity.DANGER
            else -> AlertType.NO_SAFETY_RESPONSE to AlertSeverity.EMERGENCY
        }
    }

    private fun randomOutcome(): String = listOf(
        "Worker safe — false alarm",
        "Worker safe — rested and resumed",
        "Attended by first aider, cleared",
        "Escalated to site medic, resolved",
    ).random()
}
