package com.jtexpress.bevest.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.firebase.valueEvents
import com.jtexpress.bevest.data.firebase.valueEventsOrNull
import com.jtexpress.bevest.data.mapper.toSensorReading
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.VitalLogEntry
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MonitoringRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    private val firestore: FirebaseFirestore,
) : MonitoringRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Tracks when a 15-second WAITING safety check was started per worker. */
    private val waitingStartedAt = ConcurrentHashMap<String, Long>()

    /** Tracks when an ACKNOWLEDGED ("Worker has Responded") state started so it resets to NONE after 5s. */
    private val acknowledgedStartedAt = ConcurrentHashMap<String, Long>()

    /** Tracks the latest authoritative safety response state set by the app or vest. */
    private val safetyResponseOverride = ConcurrentHashMap<String, SafetyResponseState>()

    /** Tracks the last 1-minute bucket logged to RTDB per worker so we write once per minute. */
    private val lastLoggedMinuteByWorker = ConcurrentHashMap<String, Long>()

    /** In-memory 1-minute vital logs per worker so logs are immediately visible even before RTDB round-trip. */
    private val sessionVitalLogs = MutableStateFlow<Map<String, Map<Long, VitalLogEntry>>>(emptyMap())

    private fun DataSnapshot.safeStr(key: String): String? =
        child(key).value?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    private fun DataSnapshot.safeInt(key: String): Int? =
        when (val v = child(key).value) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt()
            else -> null
        }

    private fun DataSnapshot.safeDouble(key: String): Double? =
        when (val v = child(key).value) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }

    private fun DataSnapshot.safeLong(key: String): Long =
        when (val v = child(key).value) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong() ?: 0L
            else -> 0L
        }

    private fun recordMinuteLogIfNeeded(workerId: String, reading: SensorReading) {
        val ts = if (reading.timestamp > 1_000_000_000_000L) reading.timestamp else return
        val ageMs = System.currentTimeMillis() - ts
        if (ageMs > 120_000L) return // Only log fresh live readings
        if (reading.heartRate == null && reading.temperature == null) return

        val minuteBucket = ts / 60_000L
        val minuteEpochMs = minuteBucket * 60_000L
        val entry = VitalLogEntry(
            timestamp = minuteEpochMs,
            heartRate = reading.heartRate,
            temperature = reading.temperature,
        )

        // Always update the in-memory minute bucket with the latest reading in that minute
        sessionVitalLogs.update { current ->
            val workerMap = (current[workerId].orEmpty() + (minuteBucket to entry))
            current + (workerId to workerMap)
        }

        // Persist to Firebase RTDB once per new minute bucket
        val prevBucket = lastLoggedMinuteByWorker[workerId]
        if (prevBucket == null || minuteBucket > prevBucket) {
            lastLoggedMinuteByWorker[workerId] = minuteBucket
            val payload = mapOf(
                "vestId" to reading.vestId,
                "workerId" to workerId,
                "heartRate" to reading.heartRate,
                "temperature" to reading.temperature,
                "timestamp" to minuteEpochMs,
            )
            database.getReference("vitalLogs/$workerId/$minuteBucket").setValue(payload)
            if (reading.vestId.isNotBlank()) {
                database.getReference("vitalLogsByVest/${reading.vestId}/$minuteBucket").setValue(payload)
            }
        }
    }

    private fun scheduleAckAutoReset(workerId: String) {
        val started = acknowledgedStartedAt[workerId] ?: return
        repoScope.launch {
            val remaining = 5_000L - (System.currentTimeMillis() - started)
            if (remaining > 0L) delay(remaining)
            val currentStart = acknowledgedStartedAt[workerId]
            if (currentStart == started) {
                acknowledgedStartedAt.remove(workerId)
                if (safetyResponseOverride[workerId] == SafetyResponseState.ACKNOWLEDGED) {
                    safetyResponseOverride[workerId] = SafetyResponseState.NONE
                }
                setSafetyResponse(workerId, SafetyResponseState.NONE)
            }
        }
    }

    private fun findReadingForVest(
        workerId: String,
        assignedVestId: String,
        cleanMac: String?,
        directReading: SensorReading?,
        vestSnap: DataSnapshot?,
        macSnap: DataSnapshot?,
    ): SensorReading? {
        val candidates = mutableListOf<SensorReading>()

        // 1. Direct reading at /liveReadings/{workerId}, only if it belongs to this worker's assigned vest
        if (directReading != null &&
            directReading.vestId.isNotBlank() &&
            directReading.vestId.equals(assignedVestId, ignoreCase = true)
        ) {
            candidates.add(directReading)
        }

        // 2. Reading from /liveReadingsByVest matching specifically assignedVestId or cleanMac
        if (vestSnap != null && vestSnap.exists()) {
            for (child in vestSnap.children) {
                val key = child.key.orEmpty()
                val childVestId = child.safeStr("vestId").orEmpty()
                val childMac = (child.safeStr("mac") ?: child.safeStr("macAddress"))
                    ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase().orEmpty()

                val matchesVest = key.equals(assignedVestId, ignoreCase = true) ||
                    childVestId.equals(assignedVestId, ignoreCase = true) ||
                    (!cleanMac.isNullOrBlank() && childMac.equals(cleanMac, ignoreCase = true))

                if (matchesVest) {
                    child.toSensorReading(workerId)?.let {
                        candidates.add(it.copy(vestId = assignedVestId))
                    }
                }
            }
        }

        // 3. Reading from /liveReadingsByMac matching specifically cleanMac or assignedVestId
        if (macSnap != null && macSnap.exists()) {
            for (child in macSnap.children) {
                val key = child.key?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase().orEmpty()
                val childVestId = child.safeStr("vestId").orEmpty()
                val childMac = (child.safeStr("mac") ?: child.safeStr("macAddress"))
                    ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase().orEmpty()

                val matchesMac = (!cleanMac.isNullOrBlank() && (key.equals(cleanMac, ignoreCase = true) || childMac.equals(cleanMac, ignoreCase = true))) ||
                    childVestId.equals(assignedVestId, ignoreCase = true)

                if (matchesMac) {
                    child.toSensorReading(workerId)?.let {
                        candidates.add(it.copy(vestId = assignedVestId))
                    }
                }
            }
        }

        // Prefer the candidate that carries an active non-NONE safetyResponse if timestamps are close,
        // or the most recent reading overall.
        val rawBest = candidates.maxByOrNull { it.timestamp } ?: return null
        val nonNoneResponse = candidates
            .map { it.safetyResponse }
            .firstOrNull { it != SafetyResponseState.NONE }

        val cloudResponse = nonNoneResponse ?: rawBest.safetyResponse
        val now = System.currentTimeMillis()
        val waitStart = waitingStartedAt[workerId]

        val effectiveResponse = when {
            cloudResponse == SafetyResponseState.EMERGENCY_REQUESTED ||
                cloudResponse == SafetyResponseState.NO_RESPONSE -> {
                waitingStartedAt.remove(workerId)
                acknowledgedStartedAt.remove(workerId)
                safetyResponseOverride[workerId] = cloudResponse
                cloudResponse
            }
            cloudResponse == SafetyResponseState.ACKNOWLEDGED -> {
                waitingStartedAt.remove(workerId)
                val ackStart = acknowledgedStartedAt.getOrPut(workerId) {
                    now.also { repoScope.launch { delay(5_000L); if (acknowledgedStartedAt[workerId] == it) setSafetyResponse(workerId, SafetyResponseState.NONE) } }
                }
                if (now - ackStart >= 5_000L) {
                    acknowledgedStartedAt.remove(workerId)
                    safetyResponseOverride[workerId] = SafetyResponseState.NONE
                    SafetyResponseState.NONE
                } else {
                    safetyResponseOverride[workerId] = SafetyResponseState.ACKNOWLEDGED
                    SafetyResponseState.ACKNOWLEDGED
                }
            }
            waitStart != null && (now - waitStart) < 15_500L -> {
                SafetyResponseState.WAITING
            }
            safetyResponseOverride[workerId] == SafetyResponseState.NO_RESPONSE -> {
                SafetyResponseState.NO_RESPONSE
            }
            safetyResponseOverride[workerId] == SafetyResponseState.ACKNOWLEDGED -> {
                val ackStart = acknowledgedStartedAt[workerId]
                if (ackStart != null && (now - ackStart) < 5_000L) {
                    SafetyResponseState.ACKNOWLEDGED
                } else {
                    acknowledgedStartedAt.remove(workerId)
                    safetyResponseOverride[workerId] = SafetyResponseState.NONE
                    SafetyResponseState.NONE
                }
            }
            else -> cloudResponse
        }

        val resolvedReading = rawBest.copy(safetyResponse = effectiveResponse)
        recordMinuteLogIfNeeded(workerId, resolvedReading)
        return resolvedReading
    }

    /**
     * Emits null immediately, then real readings as they arrive. Screens must render
     * their loaded state right away and mark data as offline/stale — never block the UI
     * waiting for a vest that may never transmit (plan section 20 & 21).
     */
    override fun observeReading(workerId: String): Flow<SensorReading?> =
        combine(
            firestore.collection(FirebasePaths.WORKERS).document(workerId).snapshots()
                .map<com.google.firebase.firestore.DocumentSnapshot, com.google.firebase.firestore.DocumentSnapshot?> { it }
                .onStart { emit(null) }
                .catch { emit(null) },
            firestore.collection(FirebasePaths.VESTS).whereEqualTo("inCompanyDirectory", true).snapshots()
                .map<com.google.firebase.firestore.QuerySnapshot, com.google.firebase.firestore.QuerySnapshot?> { it }
                .onStart { emit(null) }
                .catch { emit(null) },
            database.getReference(FirebasePaths.liveReading(workerId))
                .valueEvents()
                .map { it.toSensorReading(workerId) }
                .catch { emit(null) }
                .onStart { emit(null) },
            database.getReference("liveReadingsByVest")
                .valueEventsOrNull(),
            database.getReference("liveReadingsByMac")
                .valueEventsOrNull(),
        ) { workerSnap, vestsSnap, directReading, vestSnap, macSnap ->
            val validVests = vestsSnap?.documents.orEmpty().associateBy { it.id }
            val assignedFromWorker = workerSnap?.getString("assignedVestId")?.takeIf { it.isNotBlank() && validVests[it]?.getString("assignedWorkerId") == workerId }
            val assignedFromVest = vestsSnap?.documents?.firstOrNull { it.getString("assignedWorkerId") == workerId }?.id
            val assignedVestId = assignedFromWorker ?: assignedFromVest

            // If worker has no assigned vest, do not attach or return any readings
            if (assignedVestId.isNullOrBlank()) {
                return@combine null
            }

            val vestDoc = validVests[assignedVestId]
            val cleanMac = (vestDoc?.getString("cleanMac") ?: vestDoc?.getString("macAddress"))
                ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()

            findReadingForVest(
                workerId = workerId,
                assignedVestId = assignedVestId,
                cleanMac = cleanMac,
                directReading = directReading,
                vestSnap = vestSnap,
                macSnap = macSnap,
            )
        }

    override fun observeReadings(workerIds: List<String>): Flow<Map<String, SensorReading>> {
        if (workerIds.isEmpty()) return flowOf(emptyMap())
        val flows = workerIds.map { id ->
            database.getReference(FirebasePaths.liveReading(id)).valueEvents()
                .map { id to it.toSensorReading(id) }
                .catch { emit(id to null) }
                .onStart { emit(id to null) }
        }
        val directFlow = combine(flows) { pairs ->
            pairs.mapNotNull { (id, reading) -> reading?.let { id to it } }.toMap()
        }
        val workersFlow = firestore.collection(FirebasePaths.WORKERS).snapshots()
            .map<com.google.firebase.firestore.QuerySnapshot, com.google.firebase.firestore.QuerySnapshot?> { it }
            .onStart { emit(null) }
            .catch { emit(null) }
        val vestsFlow = firestore.collection(FirebasePaths.VESTS).whereEqualTo("inCompanyDirectory", true).snapshots()
            .map<com.google.firebase.firestore.QuerySnapshot, com.google.firebase.firestore.QuerySnapshot?> { it }
            .onStart { emit(null) }
            .catch { emit(null) }
        val vestFlow = database.getReference("liveReadingsByVest").valueEventsOrNull()
        val macFlow = database.getReference("liveReadingsByMac").valueEventsOrNull()

        return combine(
            workersFlow,
            vestsFlow,
            directFlow,
            vestFlow,
            macFlow,
        ) { workersSnap, vestsSnap, directMap, vestSnap, macSnap ->
            val result = mutableMapOf<String, SensorReading>()
            val validVests = vestsSnap?.documents.orEmpty().associateBy { it.id }
            val workerDocs = workersSnap?.documents.orEmpty().associateBy { it.id }

            for (workerId in workerIds) {
                val workerDoc = workerDocs[workerId]
                val assignedFromWorker = workerDoc?.getString("assignedVestId")?.takeIf { it.isNotBlank() && validVests[it]?.getString("assignedWorkerId") == workerId }
                val assignedFromVest = vestsSnap?.documents?.firstOrNull { it.getString("assignedWorkerId") == workerId }?.id
                val assignedVestId = assignedFromWorker ?: assignedFromVest

                val directReading = directMap[workerId]
                if (assignedVestId.isNullOrBlank()) {
                    // Worker has no assigned vest: never attach telemetry or alerts
                    continue
                }

                val vestDoc = validVests[assignedVestId]
                val cleanMac = (vestDoc?.getString("cleanMac") ?: vestDoc?.getString("macAddress"))
                    ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()

                findReadingForVest(
                    workerId = workerId,
                    assignedVestId = assignedVestId,
                    cleanMac = cleanMac,
                    directReading = directReading,
                    vestSnap = vestSnap,
                    macSnap = macSnap,
                )?.let { result[workerId] = it }
            }
            result
        }
    }

    override fun observeVitalLogs(workerId: String): Flow<List<VitalLogEntry>> =
        combine(
            database.getReference("vitalLogs/$workerId")
                .valueEventsOrNull(),
            sessionVitalLogs,
        ) { snap, sessionMap ->
            val merged = mutableMapOf<Long, VitalLogEntry>()

            // 1. Load persisted minute logs from Firebase RTDB
            if (snap != null && snap.exists()) {
                for (child in snap.children) {
                    val ts = child.safeLong("timestampServer").takeIf { it > 1_000_000_000_000L }
                        ?: child.safeLong("timestamp").takeIf { it > 1_000_000_000_000L }
                        ?: (child.key?.toLongOrNull()?.let { it * 60_000L } ?: 0L)
                    if (ts > 0L) {
                        val bucket = ts / 60_000L
                        merged[bucket] = VitalLogEntry(
                            timestamp = bucket * 60_000L,
                            heartRate = child.safeInt("heartRate"),
                            temperature = child.safeDouble("temperature"),
                        )
                    }
                }
            }

            // 2. Overlay current session 1-minute buckets
            sessionMap[workerId]?.forEach { (bucket, entry) ->
                merged[bucket] = entry
            }

            merged.values.sortedByDescending { it.timestamp }.take(120)
        }

    override suspend fun initiateSafetyResponse(workerId: String, byUserId: String): Outcome<Unit> {
        waitingStartedAt[workerId] = System.currentTimeMillis()
        acknowledgedStartedAt.remove(workerId)
        safetyResponseOverride[workerId] = SafetyResponseState.WAITING
        return setSafetyResponse(workerId, SafetyResponseState.WAITING)
    }

    override suspend fun setSafetyResponse(
        workerId: String,
        state: SafetyResponseState,
    ): Outcome<Unit> = try {
        val now = System.currentTimeMillis()
        if (state == SafetyResponseState.WAITING) {
            waitingStartedAt[workerId] = now
            acknowledgedStartedAt.remove(workerId)
        } else {
            waitingStartedAt.remove(workerId)
            if (state == SafetyResponseState.ACKNOWLEDGED) {
                acknowledgedStartedAt[workerId] = now
                scheduleAckAutoReset(workerId)
            } else {
                acknowledgedStartedAt.remove(workerId)
            }
        }
        safetyResponseOverride[workerId] = state

        val clearFall = state == SafetyResponseState.NONE || state == SafetyResponseState.ACKNOWLEDGED
        val updates = mutableMapOf<String, Any>("safetyResponse" to state.name)
        if (clearFall) {
            updates["fallDetected"] = false
            updates["motionState"] = "STATIONARY"
        }

        // 1. Update /liveReadings/{workerId}
        runCatching {
            val ref = database.getReference(FirebasePaths.liveReading(workerId))
            if ((ref.get().await()).exists()) {
                ref.updateChildren(updates).await()
            }
        }

        // 2. Also update /liveReadingsByVest/{vestId}, /liveReadingsByMac/{mac}, and /vestsLive/{vestId}
        // so the ESP32 vest receives WAITING / ACKNOWLEDGED / NONE / NO_RESPONSE immediately
        runCatching {
            val workerDoc = firestore.collection(FirebasePaths.WORKERS).document(workerId).get().await()
            val assignedVestId = workerDoc.getString("assignedVestId")?.takeIf { it.isNotBlank() }
            if (assignedVestId != null) {
                val vestRef = database.getReference("liveReadingsByVest/$assignedVestId")
                if (vestRef.get().await().exists()) {
                    vestRef.updateChildren(updates).await()
                }
                database.getReference("vestsLive/$assignedVestId/safetyResponse").setValue(state.name)
                if (clearFall) {
                    database.getReference("vestsLive/$assignedVestId/fallDetected").setValue(false)
                }
                val vestDoc = firestore.collection(FirebasePaths.VESTS).document(assignedVestId).get().await()
                val cleanMac = (vestDoc.getString("cleanMac") ?: vestDoc.getString("macAddress"))
                    ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()
                if (!cleanMac.isNullOrBlank()) {
                    val macRef = database.getReference("liveReadingsByMac/$cleanMac")
                    if (macRef.get().await().exists()) {
                        macRef.updateChildren(updates).await()
                    }
                    database.getReference("vestsLiveByMac/$cleanMac/safetyResponse").setValue(state.name)
                    if (clearFall) {
                        database.getReference("vestsLiveByMac/$cleanMac/fallDetected").setValue(false)
                    }
                }
            }
        }

        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun clearReading(workerId: String): Outcome<Unit> {
        // Disabled: live telemetry is owned by authentic IoT vests
        return Outcome.Success(Unit)
    }

    override suspend fun pushSimulatedReading(reading: SensorReading): Outcome<Unit> {
        // Simulations permanently disabled
        return Outcome.Success(Unit)
    }

    override suspend fun refreshReadings(): Outcome<Unit> = try {
        database.getReference("liveReadingsByVest").get().await()
        database.getReference("liveReadingsByMac").get().await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
