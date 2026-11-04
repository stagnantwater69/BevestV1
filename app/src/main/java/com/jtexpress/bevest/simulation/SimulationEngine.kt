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
    OFFLINE("Vest offline"),
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
    enum class Phase { NORMAL, WARNING, DANGER, EMERGENCY, OFFLINE }
}

data class SimulationState(
    val bound: Boolean = false,
    val streaming: Boolean = false,
    val busy: Boolean = false,
    val canStream: Boolean = false,
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
    /** Every alert this run has opened per worker — a scenario can raise DANGER then EMERGENCY. */
    private val openAlertIds = mutableMapOf<String, MutableList<String>>()
    private val incidentRaised = mutableSetOf<String>()
    /** Workers whose alert an officer acknowledged: they report ACKNOWLEDGED and stop escalating. */
    private val acknowledged = mutableSetOf<String>()

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
    init {
        stop()
    }

    fun bind(contractorId: String? = null, siteId: String? = null, canStream: Boolean = false) {
        // Disabled: simulations have been permanently disabled
        stop()
    }

    // ---------------------------------------------------------------- streaming

    fun start() {
        // Disabled: simulations have been permanently disabled
        stop()
    }
    fun stop() {
        loop?.cancel(); loop = null
        _state.update { it.copy(streaming = false) }
        log("Live stream stopped")
        scope.launch { sim.keys.forEach { monitoring.clearReading(it) } }
        sim.values.forEach {
            val phase = if (it.w.scenario == Scenario.OFFLINE) SimWorker.Phase.OFFLINE else SimWorker.Phase.NORMAL
            it.w = it.w.copy(phase = phase, motion = MotionState.MOVING)
        }
        elapsedTicks.clear()
        publish()
    }

    fun setScenario(workerId: String, scenario: Scenario) {
        // Disabled
    }

    /**
     * An officer acknowledged this worker's alert in the app. A real vest would hear it
     * and stop escalating, so the simulated one does too.
     */
    fun onAlertAcknowledged(workerId: String) {
        if (workerId !in sim) return
        acknowledged += workerId
        log("${sim[workerId]?.w?.name}: alert acknowledged by an officer")
    }

    /** An officer resolved this worker's alert: the condition is over, so the worker recovers. */
    fun onAlertResolved(workerId: String) {
        val s = sim[workerId] ?: return
        if (s.w.scenario != Scenario.HEALTHY && s.w.scenario != Scenario.OFFLINE) {
            setScenario(workerId, Scenario.HEALTHY)
        }
    }

    /** Jump a worker straight to a raised incident, skipping the wait. */
    fun escalateNow(workerId: String) {
        // Disabled
    }

    // ---------------------------------------------------------------- history

    fun seedHistory(months: Int = 6) {
        // Disabled
    }

    // ---------------------------------------------------------------- reset
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
                openAlertIds.clear(); incidentRaised.clear(); elapsedTicks.clear(); acknowledged.clear()
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

    private fun publish() {
        _state.update { it.copy(workers = sim.values.map { item -> item.w }) }
    }

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
