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
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MonitoringRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    private val firestore: FirebaseFirestore,
) : MonitoringRepository {

    private fun DataSnapshot.safeStr(key: String): String? =
        child(key).value?.toString()?.takeIf { it.isNotBlank() && it != "null" }

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

        return candidates.maxByOrNull { it.timestamp }
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

    override suspend fun initiateSafetyResponse(workerId: String, byUserId: String): Outcome<Unit> =
        setSafetyResponse(workerId, SafetyResponseState.WAITING)

    override suspend fun setSafetyResponse(
        workerId: String,
        state: SafetyResponseState,
    ): Outcome<Unit> = try {
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
        // for this worker's assigned vest so a resolved fall flag never re-triggers
        runCatching {
            val workerDoc = firestore.collection(FirebasePaths.WORKERS).document(workerId).get().await()
            val assignedVestId = workerDoc.getString("assignedVestId")?.takeIf { it.isNotBlank() }
            if (assignedVestId != null) {
                val vestRef = database.getReference("liveReadingsByVest/$assignedVestId")
                if (vestRef.get().await().exists()) {
                    vestRef.updateChildren(updates).await()
                }
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

    override suspend fun clearReading(workerId: String): Outcome<Unit> = try {
        database.getReference(FirebasePaths.liveReading(workerId)).removeValue().await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun pushSimulatedReading(reading: SensorReading): Outcome<Unit> {
        // Simulations permanently disabled
        return Outcome.Success(Unit)
    }
}