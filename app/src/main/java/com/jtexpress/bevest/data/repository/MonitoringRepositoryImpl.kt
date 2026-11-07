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
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.VitalLogEntry
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.Validators
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

    /** Tracks when the app explicitly reset a worker's safetyResponse to NONE so stale RTDB echoes are ignored briefly. */
    private val lastClearedAt = ConcurrentHashMap<String, Long>()

    /** Tracks any bridged hardware vestId (e.g. VEST-001) mapped to a workerId so commands reach the ESP32. */
    private val bridgedVestByWorker = ConcurrentHashMap<String, String>()

    /** Tracks any bridged hardware cleanMac mapped to a workerId. */
    private val bridgedMacByWorker = ConcurrentHashMap<String, String>()

    /** Reactive map of the latest authoritative safety response state set by the app or vest. */
    private val safetyResponseOverride = MutableStateFlow<Map<String, SafetyResponseState>>(emptyMap())

    private fun setOverrideState(workerId: String, state: SafetyResponseState) {
        safetyResponseOverride.update { it + (workerId to state) }
    }

    private data class RtdbBundle(
        val liveReadingsSnap: DataSnapshot?,
        val vestReadingsSnap: DataSnapshot?,
        val macReadingsSnap: DataSnapshot?,
        val vestsLiveSnap: DataSnapshot?,
        val vestsLiveByMacSnap: DataSnapshot?,
    )

    private fun observeRtdbBundle(): Flow<RtdbBundle> = combine(
        database.getReference("liveReadings").valueEventsOrNull(),
        database.getReference("liveReadingsByVest").valueEventsOrNull(),
        database.getReference("liveReadingsByMac").valueEventsOrNull(),
        database.getReference("vestsLive").valueEventsOrNull(),
        database.getReference("vestsLiveByMac").valueEventsOrNull(),
    ) { liveReadings, vestReadings, macReadings, vestsLive, vestsLiveByMac ->
        RtdbBundle(liveReadings, vestReadings, macReadings, vestsLive, vestsLiveByMac)
    }

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
                if (safetyResponseOverride.value[workerId] == SafetyResponseState.ACKNOWLEDGED) {
                    setOverrideState(workerId, SafetyResponseState.NONE)
                }
                setSafetyResponse(workerId, SafetyResponseState.NONE)
            }
        }
    }

    private fun scheduleWaitingTimeout(workerId: String, started: Long) {
        repoScope.launch {
            delay(15_200L)
            if (waitingStartedAt[workerId] == started &&
                safetyResponseOverride.value[workerId] == SafetyResponseState.WAITING
            ) {
                setSafetyResponse(workerId, SafetyResponseState.NO_RESPONSE)
            }
        }
    }

    private fun isMacMatch(macA: String?, macB: String?): Boolean {
        val a = macA?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase().orEmpty()
        val b = macB?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase().orEmpty()
        if (a.isBlank() || b.isBlank()) return false
        return Validators.isMatchingEsp32Mac(a, b)
    }

    private fun findReadingForVest(
        workerId: String,
        assignedVestId: String?,
        cleanMac: String?,
        rtdb: RtdbBundle,
        otherVestIds: Set<String> = emptySet(),
        otherMacs: List<String> = emptyList(),
        allowUnclaimedBridge: Boolean = false,
        overrideMap: Map<String, SafetyResponseState> = emptyMap(),
    ): SensorReading? {
        val overrideState = overrideMap[workerId]
        if (assignedVestId.isNullOrBlank()) {
            // Even without a paired vest, if an SSO initiated a safety check that is waiting or timed out,
            // emit a synthetic reading so the 15s countdown and No Safety Response alert work reliably.
            if (overrideState != null && overrideState != SafetyResponseState.NONE) {
                return SensorReading(
                    workerId = workerId,
                    vestId = "",
                    safetyResponse = overrideState,
                    timestamp = System.currentTimeMillis(),
                )
            }
            return null
        }

        val candidates = mutableListOf<SensorReading>()

        fun matchesDirectly(child: DataSnapshot): Boolean {
            val key = child.key.orEmpty()
            val childVestId = child.safeStr("vestId").orEmpty()
            val childWorkerId = (child.safeStr("workerId") ?: child.safeStr("assignedWorkerId")).orEmpty()
            val childMac = (child.safeStr("mac") ?: child.safeStr("macAddress"))
                ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase().orEmpty()

            if (key.equals(assignedVestId, ignoreCase = true) ||
                childVestId.equals(assignedVestId, ignoreCase = true)
            ) {
                return true
            }
            if (isMacMatch(childMac, cleanMac) || isMacMatch(key, cleanMac)) {
                return true
            }
            if ((key.equals(workerId, ignoreCase = true) || childWorkerId.equals(workerId, ignoreCase = true)) &&
                (childVestId.isBlank() || childVestId.uppercase() !in otherVestIds)
            ) {
                return true
            }
            return false
        }

        fun collectDirectFrom(snap: DataSnapshot?) {
            if (snap == null || !snap.exists()) return
            for (child in snap.children) {
                if (matchesDirectly(child)) {
                    val childVest = child.safeStr("vestId") ?: child.key.orEmpty()
                    if (childVest.isNotBlank() && !childVest.equals(assignedVestId, ignoreCase = true)) {
                        bridgedVestByWorker[workerId] = childVest
                    }
                    val childMac = (child.safeStr("mac") ?: child.safeStr("macAddress"))
                        ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase().orEmpty()
                    if (childMac.isNotBlank()) {
                        bridgedMacByWorker[workerId] = childMac
                    }
                    child.toSensorReading(workerId)?.let {
                        candidates.add(it.copy(vestId = assignedVestId))
                    }
                }
            }
        }

        // 1. Direct match across /liveReadings, /liveReadingsByVest, /liveReadingsByMac, /vestsLive, /vestsLiveByMac
        collectDirectFrom(rtdb.liveReadingsSnap)
        collectDirectFrom(rtdb.vestReadingsSnap)
        collectDirectFrom(rtdb.macReadingsSnap)
        collectDirectFrom(rtdb.vestsLiveSnap)
        collectDirectFrom(rtdb.vestsLiveByMacSnap)

        // 2. Fallback: if no direct match was found and this worker is eligible to adopt an unclaimed
        // hardware stream (e.g. ESP32 transmitting under DEFAULT_VEST_ID "VEST-001"), bridge it!
        if (candidates.isEmpty() && allowUnclaimedBridge) {
            fun collectUnclaimedFrom(snap: DataSnapshot?) {
                if (snap == null || !snap.exists()) return
                for (child in snap.children) {
                    val childVestId = (child.safeStr("vestId") ?: child.key.orEmpty()).uppercase()
                    val childMac = (child.safeStr("mac") ?: child.safeStr("macAddress"))
                        ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase().orEmpty()
                    val claimedByOther = childVestId in otherVestIds ||
                        (childMac.isNotBlank() && otherMacs.any { isMacMatch(it, childMac) })
                    if (!claimedByOther) {
                        val rawVestId = child.safeStr("vestId") ?: child.key.orEmpty()
                        if (rawVestId.isNotBlank()) {
                            bridgedVestByWorker[workerId] = rawVestId
                        }
                        if (childMac.isNotBlank()) {
                            bridgedMacByWorker[workerId] = childMac
                            try {
                                database.getReference("macToVest/$childMac").setValue(assignedVestId)
                            } catch (_: Exception) {}
                        }
                        child.toSensorReading(workerId)?.let {
                            candidates.add(it.copy(vestId = assignedVestId))
                        }
                    }
                }
            }
            collectUnclaimedFrom(rtdb.vestReadingsSnap)
            collectUnclaimedFrom(rtdb.liveReadingsSnap)
            collectUnclaimedFrom(rtdb.vestsLiveSnap)
            collectUnclaimedFrom(rtdb.vestsLiveByMacSnap)
        }

        val now = System.currentTimeMillis()
        val waitStart = waitingStartedAt[workerId]
        val clearTs = lastClearedAt[workerId] ?: 0L
        val recentlyCleared = (now - clearTs) < 4_500L

        if (candidates.isEmpty()) {
            if (overrideState != null && overrideState != SafetyResponseState.NONE) {
                return SensorReading(
                    workerId = workerId,
                    vestId = assignedVestId,
                    safetyResponse = overrideState,
                    timestamp = now,
                )
            }
            return null
        }

        // Prefer the candidate with real vitals (heartRate / temperature) and newest timestamp
        val bestTelemetry = candidates
            .filter { it.heartRate != null || it.temperature != null }
            .maxByOrNull { it.timestamp }
        val rawBest = bestTelemetry ?: candidates.maxByOrNull { it.timestamp } ?: candidates.first()

        // Merge fallDetected across all matched nodes (including /vestsLive which holds fallDetected=true for 6s)
        val anyFall = !recentlyCleared && candidates.any {
            it.fallDetected || it.motionState == MotionState.FALL_DETECTED
        }

        // Priority for safetyResponse across all matched RTDB nodes:
        // EMERGENCY_REQUESTED / NO_RESPONSE > ACKNOWLEDGED > WAITING > NONE
        val cloudResponse = when {
            !recentlyCleared && candidates.any { it.safetyResponse == SafetyResponseState.EMERGENCY_REQUESTED } ->
                SafetyResponseState.EMERGENCY_REQUESTED
            !recentlyCleared && candidates.any { it.safetyResponse == SafetyResponseState.NO_RESPONSE } ->
                SafetyResponseState.NO_RESPONSE
            candidates.any { it.safetyResponse == SafetyResponseState.ACKNOWLEDGED } ->
                SafetyResponseState.ACKNOWLEDGED
            candidates.any { it.safetyResponse == SafetyResponseState.WAITING } ->
                SafetyResponseState.WAITING
            else -> SafetyResponseState.NONE
        }

        val effectiveResponse = when {
            cloudResponse == SafetyResponseState.EMERGENCY_REQUESTED ||
                cloudResponse == SafetyResponseState.NO_RESPONSE -> {
                waitingStartedAt.remove(workerId)
                acknowledgedStartedAt.remove(workerId)
                if (overrideState != cloudResponse) {
                    setOverrideState(workerId, cloudResponse)
                }
                cloudResponse
            }
            cloudResponse == SafetyResponseState.ACKNOWLEDGED -> {
                waitingStartedAt.remove(workerId)
                val ackStart = acknowledgedStartedAt.getOrPut(workerId) {
                    now.also {
                        repoScope.launch {
                            delay(5_000L)
                            if (acknowledgedStartedAt[workerId] == it) {
                                setSafetyResponse(workerId, SafetyResponseState.NONE)
                            }
                        }
                    }
                }
                if (now - ackStart >= 5_000L) {
                    acknowledgedStartedAt.remove(workerId)
                    setOverrideState(workerId, SafetyResponseState.NONE)
                    SafetyResponseState.NONE
                } else {
                    setOverrideState(workerId, SafetyResponseState.ACKNOWLEDGED)
                    SafetyResponseState.ACKNOWLEDGED
                }
            }
            overrideState == SafetyResponseState.NO_RESPONSE -> {
                SafetyResponseState.NO_RESPONSE
            }
            overrideState == SafetyResponseState.EMERGENCY_REQUESTED -> {
                SafetyResponseState.EMERGENCY_REQUESTED
            }
            waitStart != null && (now - waitStart) < 15_500L -> {
                SafetyResponseState.WAITING
            }
            overrideState == SafetyResponseState.ACKNOWLEDGED -> {
                val ackStart = acknowledgedStartedAt[workerId]
                if (ackStart != null && (now - ackStart) < 5_000L) {
                    SafetyResponseState.ACKNOWLEDGED
                } else {
                    acknowledgedStartedAt.remove(workerId)
                    setOverrideState(workerId, SafetyResponseState.NONE)
                    SafetyResponseState.NONE
                }
            }
            recentlyCleared -> SafetyResponseState.NONE
            else -> cloudResponse
        }

        val bestTimestamp = candidates.maxOfOrNull { it.timestamp } ?: rawBest.timestamp
        val resolvedReading = rawBest.copy(
            vestId = assignedVestId,
            fallDetected = anyFall,
            motionState = if (anyFall) MotionState.FALL_DETECTED else rawBest.motionState,
            safetyResponse = effectiveResponse,
            timestamp = if (bestTimestamp > 0L) bestTimestamp else now,
        )
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
            firestore.collection(FirebasePaths.VESTS).snapshots()
                .map<com.google.firebase.firestore.QuerySnapshot, com.google.firebase.firestore.QuerySnapshot?> { it }
                .onStart { emit(null) }
                .catch { emit(null) },
            observeRtdbBundle(),
            safetyResponseOverride,
        ) { workerSnap, vestsSnap, rtdb, overrideMap ->
            val allVestDocs = vestsSnap?.documents.orEmpty()
            val validVests = allVestDocs.associateBy { it.id }
            val assignedFromWorker = workerSnap?.getString("assignedVestId")?.takeIf { it.isNotBlank() }
            val assignedFromVest = allVestDocs.firstOrNull { it.getString("assignedWorkerId") == workerId }?.id
            val assignedVestId = assignedFromWorker ?: assignedFromVest

            val vestDoc = assignedVestId?.let { validVests[it] }
            val cleanMac = (vestDoc?.getString("cleanMac") ?: vestDoc?.getString("macAddress"))
                ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()

            val otherVests = allVestDocs.filter {
                val owner = it.getString("assignedWorkerId")?.takeIf { s -> s.isNotBlank() }
                it.id != assignedVestId && owner != null && owner != workerId
            }
            val otherVestIds = otherVests.map { it.id.uppercase() }.toSet()
            val otherMacs = otherVests.mapNotNull {
                (it.getString("cleanMac") ?: it.getString("macAddress"))
                    ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()?.takeIf { m -> m.isNotBlank() }
            }

            findReadingForVest(
                workerId = workerId,
                assignedVestId = assignedVestId,
                cleanMac = cleanMac,
                rtdb = rtdb,
                otherVestIds = otherVestIds,
                otherMacs = otherMacs,
                allowUnclaimedBridge = !assignedVestId.isNullOrBlank(),
                overrideMap = overrideMap,
            )
        }

    override fun observeReadings(workerIds: List<String>): Flow<Map<String, SensorReading>> {
        if (workerIds.isEmpty()) return flowOf(emptyMap())
        val workersFlow = firestore.collection(FirebasePaths.WORKERS).snapshots()
            .map<com.google.firebase.firestore.QuerySnapshot, com.google.firebase.firestore.QuerySnapshot?> { it }
            .onStart { emit(null) }
            .catch { emit(null) }
        val vestsFlow = firestore.collection(FirebasePaths.VESTS).snapshots()
            .map<com.google.firebase.firestore.QuerySnapshot, com.google.firebase.firestore.QuerySnapshot?> { it }
            .onStart { emit(null) }
            .catch { emit(null) }

        return combine(
            workersFlow,
            vestsFlow,
            observeRtdbBundle(),
            safetyResponseOverride,
        ) { workersSnap, vestsSnap, rtdb, overrideMap ->
            val result = mutableMapOf<String, SensorReading>()
            val allVestDocs = vestsSnap?.documents.orEmpty()
            val validVests = allVestDocs.associateBy { it.id }
            val workerDocs = workersSnap?.documents.orEmpty().associateBy { it.id }

            // Determine assignedVestId for each requested worker
            val workerVestMap = mutableMapOf<String, String>()
            for (wId in workerIds) {
                val wDoc = workerDocs[wId]
                val fromWorker = wDoc?.getString("assignedVestId")?.takeIf { it.isNotBlank() }
                val fromVest = allVestDocs.firstOrNull { it.getString("assignedWorkerId") == wId }?.id
                val vId = fromWorker ?: fromVest
                if (!vId.isNullOrBlank()) {
                    workerVestMap[wId] = vId
                }
            }

            var bridgedUsed = false
            for (workerId in workerIds) {
                val assignedVestId = workerVestMap[workerId]
                val vestDoc = assignedVestId?.let { validVests[it] }
                val cleanMac = (vestDoc?.getString("cleanMac") ?: vestDoc?.getString("macAddress"))
                    ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()

                val otherVests = allVestDocs.filter {
                    val owner = it.getString("assignedWorkerId")?.takeIf { s -> s.isNotBlank() }
                    it.id != assignedVestId && owner != null && owner != workerId
                }
                val otherVestIds = (
                    otherVests.map { it.id.uppercase() } +
                        workerVestMap.filterKeys { it != workerId }.values.map { it.uppercase() }
                    ).toSet()
                val otherMacs = otherVests.mapNotNull {
                    (it.getString("cleanMac") ?: it.getString("macAddress"))
                        ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()?.takeIf { m -> m.isNotBlank() }
                }

                val canBridge = !assignedVestId.isNullOrBlank() && !bridgedUsed
                val reading = findReadingForVest(
                    workerId = workerId,
                    assignedVestId = assignedVestId,
                    cleanMac = cleanMac,
                    rtdb = rtdb,
                    otherVestIds = otherVestIds,
                    otherMacs = otherMacs,
                    allowUnclaimedBridge = canBridge,
                    overrideMap = overrideMap,
                )
                if (reading != null) {
                    if (bridgedVestByWorker.containsKey(workerId)) {
                        bridgedUsed = true
                    }
                    result[workerId] = reading
                }
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
        val now = System.currentTimeMillis()
        waitingStartedAt[workerId] = now
        acknowledgedStartedAt.remove(workerId)
        lastClearedAt.remove(workerId)
        setOverrideState(workerId, SafetyResponseState.WAITING)
        scheduleWaitingTimeout(workerId, now)
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
            lastClearedAt.remove(workerId)
        } else {
            waitingStartedAt.remove(workerId)
            if (state == SafetyResponseState.ACKNOWLEDGED) {
                acknowledgedStartedAt[workerId] = now
                lastClearedAt[workerId] = now
                scheduleAckAutoReset(workerId)
            } else {
                acknowledgedStartedAt.remove(workerId)
                if (state == SafetyResponseState.NONE) {
                    lastClearedAt[workerId] = now
                } else {
                    lastClearedAt.remove(workerId)
                }
            }
        }
        setOverrideState(workerId, state)

        val clearFall = state == SafetyResponseState.NONE || state == SafetyResponseState.ACKNOWLEDGED
        val updates = mutableMapOf<String, Any>("safetyResponse" to state.name)
        if (clearFall) {
            updates["fallDetected"] = false
            updates["motionState"] = "STATIONARY"
        }

        // 1. Update /liveReadings/{workerId}
        runCatching {
            val ref = database.getReference(FirebasePaths.liveReading(workerId))
            ref.updateChildren(updates)
        }

        // 2. Also update /liveReadingsByVest/{vestId}, /liveReadingsByMac/{mac}, /vestsLive/{vestId},
        // and any bridged hardware vestId/MAC so the ESP32 vest receives WAITING / ACKNOWLEDGED / NONE / NO_RESPONSE immediately
        runCatching {
            val targetVestIds = mutableSetOf<String>()
            val targetMacs = mutableSetOf<String>()

            bridgedVestByWorker[workerId]?.takeIf { it.isNotBlank() }?.let { targetVestIds.add(it) }
            bridgedMacByWorker[workerId]?.takeIf { it.isNotBlank() }?.let { targetMacs.add(it) }

            val workerDoc = runCatching {
                firestore.collection(FirebasePaths.WORKERS).document(workerId).get().await()
            }.getOrNull()
            val assignedVestId = workerDoc?.getString("assignedVestId")?.takeIf { it.isNotBlank() }
            if (assignedVestId != null) {
                targetVestIds.add(assignedVestId)
                val vestDoc = runCatching {
                    firestore.collection(FirebasePaths.VESTS).document(assignedVestId).get().await()
                }.getOrNull()
                val cleanMac = (vestDoc?.getString("cleanMac") ?: vestDoc?.getString("macAddress"))
                    ?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()
                if (!cleanMac.isNullOrBlank()) {
                    targetMacs.add(cleanMac)
                }
            }

            for (vId in targetVestIds) {
                database.getReference("liveReadingsByVest/$vId").updateChildren(updates)
                database.getReference("vestsLive/$vId/safetyResponse").setValue(state.name)
                if (clearFall) {
                    database.getReference("vestsLive/$vId/fallDetected").setValue(false)
                }
            }

            for (mac in targetMacs) {
                database.getReference("liveReadingsByMac/$mac").updateChildren(updates)
                database.getReference("vestsLiveByMac/$mac/safetyResponse").setValue(state.name)
                if (clearFall) {
                    database.getReference("vestsLiveByMac/$mac/fallDetected").setValue(false)
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
        database.getReference("vestsLive").get().await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
