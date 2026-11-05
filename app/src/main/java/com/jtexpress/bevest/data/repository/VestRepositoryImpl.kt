package com.jtexpress.bevest.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.firebase.valueEventsOrNull
import com.jtexpress.bevest.data.mapper.toVest
import com.jtexpress.bevest.data.mapper.toVestAssignment
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestAssignment
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.utils.AppError
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.Validators
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VestRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: FirebaseDatabase,
) : VestRepository {

    private val vests get() = firestore.collection(FirebasePaths.VESTS)
    private val workers get() = firestore.collection(FirebasePaths.WORKERS)
    private val assignments get() = firestore.collection(FirebasePaths.VEST_ASSIGNMENTS)
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(10_000L) } }

    private data class LiveInfo(
        val online: Boolean,
        val battery: Int?,
        val lastSeen: Long?,
        val mac: String? = null,
        val serialNumber: String? = null,
        val vestId: String? = null,
        val wifiSsid: String? = null,
        val wifiRssi: Int? = null,
    )

    private fun DataSnapshot.safeStr(key: String): String? =
        child(key).value?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    private fun DataSnapshot.safeLong(key: String): Long? =
        when (val v = child(key).value) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
            else -> null
        }

    private fun DataSnapshot.safeInt(key: String): Int? =
        when (val v = child(key).value) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt()
            else -> null
        }

    private fun DataSnapshot.safeBool(key: String, default: Boolean = false): Boolean =
        when (val v = child(key).value) {
            is Boolean -> v
            is Number -> v.toLong() > 0L
            is String -> v.equals("true", ignoreCase = true) || v == "1"
            else -> default
        }

    private fun processSnapshot(snap: DataSnapshot?, map: MutableMap<String, LiveInfo>, now: Long) {
        if (snap == null || !snap.exists()) return
        snap.children.forEach { child ->
            val key = child.key ?: return@forEach
            val vestIdField = child.safeStr("vestId")
            val mac = child.safeStr("mac")
                ?: child.safeStr("macAddress")?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()
            val serial = child.safeStr("serialNumber")
            val wifiSsid = child.safeStr("wifiSsid")
            val wifiRssi = child.safeInt("wifiRssi") ?: child.safeInt("rssi")
            val rawOnline = child.safeBool("online", default = false)
            val battery = child.safeInt("battery")
            val lastSeen = child.safeLong("lastSeenServer")
                ?: child.safeLong("timestampServer")
                ?: child.safeLong("timestamp")
                ?: child.safeLong("lastSeen")
            val diff = if (lastSeen != null) now - lastSeen else Long.MAX_VALUE
            val isRecent = lastSeen != null && diff <= 45_000L && diff >= -120_000L
            val isLive = rawOnline && isRecent

            val info = LiveInfo(
                online = isLive,
                battery = battery,
                lastSeen = lastSeen,
                mac = mac,
                serialNumber = serial,
                vestId = vestIdField ?: key,
                wifiSsid = wifiSsid,
                wifiRssi = wifiRssi,
            )

            fun mergeInto(targetKey: String) {
                val prev = map[targetKey]
                if (prev == null) {
                    map[targetKey] = info
                } else {
                    val bestLastSeen = listOfNotNull(prev.lastSeen, info.lastSeen).maxOrNull()
                    map[targetKey] = LiveInfo(
                        online = prev.online || info.online,
                        battery = info.battery ?: prev.battery,
                        lastSeen = bestLastSeen,
                        mac = info.mac ?: prev.mac,
                        serialNumber = info.serialNumber ?: prev.serialNumber,
                        vestId = info.vestId ?: prev.vestId,
                        wifiSsid = info.wifiSsid ?: prev.wifiSsid,
                        wifiRssi = info.wifiRssi ?: prev.wifiRssi,
                    )
                }
            }

            // Register under key
            mergeInto(key)
            // Also register under vestId if present
            if (vestIdField != null) mergeInto(vestIdField)
            // Also register under clean MAC if present
            if (!mac.isNullOrBlank()) mergeInto(mac)
        }
    }

    private fun buildLiveMap(rtSnap: DataSnapshot?, readingsSnap: DataSnapshot?): Map<String, LiveInfo> {
        val map = mutableMapOf<String, LiveInfo>()
        val now = System.currentTimeMillis()
        processSnapshot(rtSnap, map, now)
        processSnapshot(readingsSnap, map, now)
        return map
    }

    private fun resolveLive(base: Vest, liveMap: Map<String, LiveInfo>, totalDirectoryVests: Int): LiveInfo? {
        // 1. Direct key match (case-insensitive)
        liveMap.entries.firstOrNull { it.key.equals(base.vestId, ignoreCase = true) }?.value?.let { return it }

        // 2. Hardware MAC match
        val cleanMac = base.macAddress?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase().orEmpty()
        if (cleanMac.isNotBlank()) {
            liveMap[cleanMac]?.let { return it }
            liveMap.values.firstOrNull { it.mac.equals(cleanMac, ignoreCase = true) }?.let { return it }
        }

        // 3. Serial Number match
        val cleanSerial = base.serialNumber?.replace(Regex("[^A-Za-z0-9]"), "")?.uppercase().orEmpty()
        if (cleanSerial.isNotBlank()) {
            liveMap.values.firstOrNull {
                it.serialNumber?.replace(Regex("[^A-Za-z0-9]"), "")?.equals(cleanSerial, ignoreCase = true) == true
            }?.let { return it }
        }

        // 4. Normalized alphanumeric match (e.g. "VEST2026XXXX" vs "VEST-2026-XXXX")
        val cleanBaseId = base.vestId.replace(Regex("[^A-Za-z0-9]"), "").uppercase()
        if (cleanBaseId.isNotBlank()) {
            liveMap.entries.firstOrNull {
                it.key.replace(Regex("[^A-Za-z0-9]"), "").equals(cleanBaseId, ignoreCase = true)
            }?.value?.let { return it }
        }

        return null
    }

    override fun observeVests(): Flow<Outcome<List<Vest>>> =
        combine(
            vests.whereEqualTo("inCompanyDirectory", true).snapshots(),
            database.getReference(FirebasePaths.VESTS_RT).valueEventsOrNull(),
            database.getReference("liveReadingsByVest").valueEventsOrNull(),
            ticker,
        ) { qs, rtSnap, readingsSnap, _ ->
            // Auto-publish MAC to Vest ID and Worker mapping for every claimed vest
            qs.documents.forEach { doc ->
                val vId = doc.id
                val mac = doc.getString("cleanMac")
                    ?: doc.getString("macAddress")?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()
                if (!mac.isNullOrBlank()) {
                    try {
                        database.getReference("macToVest/$mac").setValue(vId)
                    } catch (_: Exception) {}
                }
                val assignedWorker = doc.getString("assignedWorkerId")?.takeIf { it.isNotBlank() }
                if (assignedWorker != null) {
                    try {
                        database.getReference("vestToWorker/$vId").setValue(assignedWorker)
                        database.getReference("workerToVest/$assignedWorker").setValue(vId)
                        database.getReference("vestsLive/$vId/assignedWorkerId").setValue(assignedWorker)
                    } catch (_: Exception) {}
                }
            }

            val rtLoaded = rtSnap != null || readingsSnap != null
            val liveMap = buildLiveMap(rtSnap, readingsSnap)
            val mapped = qs.documents.map { doc ->
                val base = doc.toVest()
                val live = resolveLive(base, liveMap, qs.documents.size)
                // Device is online if either RTDB or Firestore reports a fresh heartbeat within 45s
                val liveOnline = (live?.online == true) || base.online
                val effectiveStatus = when {
                    base.status == VestStatus.MAINTENANCE -> VestStatus.MAINTENANCE
                    liveOnline && !base.assignedWorkerId.isNullOrBlank() -> VestStatus.ACTIVE
                    liveOnline -> VestStatus.AVAILABLE
                    !base.assignedWorkerId.isNullOrBlank() -> VestStatus.ASSIGNED
                    else -> VestStatus.OFFLINE
                }
                val validLastSeen = live?.lastSeen?.takeIf { it > 100_000_000_000L }
                    ?: base.lastSeen?.takeIf { it > 100_000_000_000L }
                // Automatically sync Cloud Firestore document so Firebase Console accurately reflects online vs offline
                // Only write offline state once RTDB has actually loaded, preventing initial null emission from flipping online->false
                if ((liveOnline || rtLoaded) && (doc.getBoolean("online") != liveOnline || doc.getString("status") != effectiveStatus.name)) {
                    try {
                        val updateMap = mutableMapOf<String, Any>(
                            "online" to liveOnline,
                            "status" to effectiveStatus.name,
                        )
                        if (validLastSeen != null) {
                            updateMap["lastSeen"] = validLastSeen
                        }
                        if (live?.wifiRssi != null) {
                            updateMap["wifiRssi"] = live.wifiRssi
                        }
                        doc.reference.update(updateMap)
                    } catch (_: Exception) {}
                }
                base.copy(
                    online = liveOnline,
                    status = effectiveStatus,
                    batteryPercent = live?.battery ?: base.batteryPercent,
                    lastSeen = validLastSeen ?: base.lastSeen,
                    wifiSsid = live?.wifiSsid ?: base.wifiSsid,
                    wifiRssi = if (liveOnline) (live?.wifiRssi ?: base.wifiRssi) else null,
                )
            }
            Outcome.Success(mapped) as Outcome<List<Vest>>
        }.catch { emit(Outcome.Failure(it.toAppError())) }

    override fun observeVest(vestId: String): Flow<Outcome<Vest>> =
        combine(
            vests.document(vestId).snapshots(),
            database.getReference(FirebasePaths.VESTS_RT).valueEventsOrNull(),
            database.getReference("liveReadingsByVest").valueEventsOrNull(),
            ticker,
        ) { snap, rtSnap, readingsSnap, _ ->
            if (!snap.exists()) {
                Outcome.Failure(AppError.NotFound("This vest is not registered."))
            } else {
                val base = snap.toVest()
                val cleanMac = base.macAddress?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()
                if (!cleanMac.isNullOrBlank()) {
                    try {
                        database.getReference("macToVest/$cleanMac").setValue(base.vestId)
                    } catch (_: Exception) {}
                }
                val rtLoaded = rtSnap != null || readingsSnap != null
                val liveMap = buildLiveMap(rtSnap, readingsSnap)
                val live = resolveLive(base, liveMap, 1)
                val liveOnline = (live?.online == true) || base.online
                val effectiveStatus = when {
                    base.status == VestStatus.MAINTENANCE -> VestStatus.MAINTENANCE
                    liveOnline && !base.assignedWorkerId.isNullOrBlank() -> VestStatus.ACTIVE
                    liveOnline -> VestStatus.AVAILABLE
                    !base.assignedWorkerId.isNullOrBlank() -> VestStatus.ASSIGNED
                    else -> VestStatus.OFFLINE
                }
                val validLastSeen = live?.lastSeen?.takeIf { it > 100_000_000_000L }
                    ?: base.lastSeen?.takeIf { it > 100_000_000_000L }
                if ((liveOnline || rtLoaded) && (snap.getBoolean("online") != liveOnline || snap.getString("status") != effectiveStatus.name)) {
                    try {
                        val updateMap = mutableMapOf<String, Any>(
                            "online" to liveOnline,
                            "status" to effectiveStatus.name,
                        )
                        if (validLastSeen != null) {
                            updateMap["lastSeen"] = validLastSeen
                        }
                        if (live?.wifiRssi != null) {
                            updateMap["wifiRssi"] = live.wifiRssi
                        }
                        snap.reference.update(updateMap)
                    } catch (_: Exception) {}
                }
                Outcome.Success(
                    base.copy(
                        online = liveOnline,
                        status = effectiveStatus,
                        batteryPercent = live?.battery ?: base.batteryPercent,
                        lastSeen = validLastSeen ?: base.lastSeen,
                        wifiSsid = live?.wifiSsid ?: base.wifiSsid,
                        wifiRssi = if (liveOnline) (live?.wifiRssi ?: base.wifiRssi) else null,
                    )
                )
            }
        }.catch { emit(Outcome.Failure(it.toAppError())) }

    override suspend fun getVest(vestId: String): Outcome<Vest> = try {
        val snap = vests.document(vestId).get().await()
        if (snap.exists()) Outcome.Success(snap.toVest())
        else Outcome.Failure(AppError.NotFound("This vest is not registered."))
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun registerVest(vestId: String): Outcome<Unit> = try {
        val existing = vests.document(vestId).get().await()
        if (existing.exists()) {
            Outcome.Failure(AppError.Validation("Vest $vestId is already registered."))
        } else {
            vests.document(vestId).set(
                mapOf(
                    "assignedWorkerId" to null,
                    "status" to VestStatus.AVAILABLE.name,
                    "battery" to null,
                    "online" to false,
                    "lastSeen" to null,
                    "openAssignmentId" to null,
                ),
            ).await()
            Outcome.Success(Unit)
        }
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun claimVest(
        vestId: String,
        serialNumber: String,
        macAddress: String,
        claimedByUid: String,
        claimedByName: String,
        claimedByEmail: String,
        companyId: String?,
        siteId: String?,
    ): Outcome<Unit> = try {
        val snap = vests.document(vestId).get().await()
        if (!snap.exists()) {
            Outcome.Failure(AppError.NotFound("Vest '$vestId' was not found in the Bevest hardware database. Only authentic Bevest smart vests generated by the Developer can be registered."))
        } else {
            val dbSerial = snap.getString("serialNumber") ?: snap.getString("esp32Serial") ?: ""
            val dbMac = snap.getString("macAddress") ?: snap.getString("cleanMac") ?: ""
            val cleanDbMac = dbMac.replace(Regex("[^A-Fa-f0-9]"), "").uppercase()
            val cleanInputMac = macAddress.replace(Regex("[^A-Fa-f0-9]"), "").uppercase()

            if (cleanDbMac.isNotBlank() && cleanInputMac.isNotBlank() && !Validators.isMatchingEsp32Mac(cleanDbMac, cleanInputMac)) {
                Outcome.Failure(AppError.Validation("MAC address mismatch! Database reports $dbMac, but scanned $macAddress."))
            } else if (dbSerial.isNotBlank() && serialNumber.isNotBlank() && !dbSerial.equals(serialNumber.trim(), ignoreCase = true)) {
                Outcome.Failure(AppError.Validation("Serial number mismatch! Database reports $dbSerial, but scanned $serialNumber."))
            } else {
                val alreadyInDir = snap.getBoolean("inCompanyDirectory") == true
                val existingCompany = snap.getString("companyId")
                if (alreadyInDir && !existingCompany.isNullOrBlank() && companyId != null && !existingCompany.equals(companyId, ignoreCase = true)) {
                    Outcome.Failure(AppError.Validation("This vest is already claimed by another company organization."))
                } else {
                    val now = com.google.firebase.Timestamp.now()
                    vests.document(vestId).update(
                        mapOf(
                            "inCompanyDirectory" to true,
                            "status" to (snap.getString("status") ?: VestStatus.AVAILABLE.name),
                            "companyId" to companyId,
                            "siteId" to siteId,
                            "addedBySsoUid" to claimedByUid,
                            "addedBySsoName" to claimedByName,
                            "addedBySsoEmail" to claimedByEmail,
                            "registeredToAdmin" to (companyId ?: claimedByEmail),
                            "claimedAt" to now,
                            "addedAt" to now,
                        )
                    ).await()

                    // Auto-sync MAC to Vest ID in Realtime Database for firmware auto-detection
                    val effectiveMac = if (cleanInputMac.isNotBlank()) cleanInputMac else cleanDbMac
                    if (effectiveMac.isNotBlank()) {
                        try {
                            database.getReference("macToVest/$effectiveMac").setValue(vestId)
                        } catch (_: Exception) {}
                    }

                    Outcome.Success(Unit)
                }
            }
        }
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun setStatus(vestId: String, status: VestStatus): Outcome<Unit> = try {
        vests.document(vestId).update("status", status.name).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun assignVest(vestId: String, workerId: String, assignedBy: String): Outcome<Unit> = try {
        val preVestSnap = try { vests.document(vestId).get().await() } catch (_: Exception) { null }
        val prevWorkerId = preVestSnap?.getString("assignedWorkerId")?.takeIf { it.isNotBlank() }
        val preWorkerSnap = try { workers.document(workerId).get().await() } catch (_: Exception) { null }
        val prevVestId = preWorkerSnap?.getString("assignedVestId")?.takeIf { it.isNotBlank() }

        firestore.runTransaction { txn ->
            openAssignment(txn, vestId, workerId, assignedBy)
        }.await()
        try {
            if (prevWorkerId != null && prevWorkerId != workerId) {
                database.getReference("workerToVest/$prevWorkerId").removeValue()
            }
            if (prevVestId != null && prevVestId != vestId) {
                database.getReference("vestsLive/$prevVestId/assignedWorkerId").removeValue()
                database.getReference("vestToWorker/$prevVestId").removeValue()
            }
            database.getReference("vestsLive/$vestId/assignedWorkerId").setValue(workerId)
            database.getReference("vestsLive/$vestId/assignedAt").setValue(System.currentTimeMillis())
            database.getReference("vestToWorker/$vestId").setValue(workerId)
            database.getReference("workerToVest/$workerId").setValue(vestId)
            // If cleanMac is known, also push to vestsLiveByMac
            val vSnap = preVestSnap ?: vests.document(vestId).get().await()
            val mac = vSnap.getString("cleanMac")
                ?: vSnap.getString("macAddress")?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()
            if (!mac.isNullOrBlank()) {
                database.getReference("vestsLiveByMac/$mac/assignedWorkerId").setValue(workerId)
                database.getReference("macToVest/$mac").setValue(vestId)
            }
        } catch (_: Exception) {}
        Outcome.Success(Unit)
    } catch (e: IllegalStateException) {
        Outcome.Failure(AppError.Validation(e.message ?: "Could not pair this vest."))
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun unassignVest(vestId: String, unassignedBy: String): Outcome<Unit> = try {
        val vSnap = try { vests.document(vestId).get().await() } catch (_: Exception) { null }
        val prevWorkerId = vSnap?.getString("assignedWorkerId")
        val mac = vSnap?.getString("cleanMac")
            ?: vSnap?.getString("macAddress")?.replace(Regex("[^A-Fa-f0-9]"), "")?.uppercase()

        firestore.runTransaction { txn ->
            closeAssignment(txn, vestId)
        }.await()
        try {
            database.getReference("vestsLive/$vestId/assignedWorkerId").removeValue()
            database.getReference("vestToWorker/$vestId").removeValue()
            if (!prevWorkerId.isNullOrBlank()) {
                database.getReference("workerToVest/$prevWorkerId").removeValue()
            }
            if (!mac.isNullOrBlank()) {
                database.getReference("vestsLiveByMac/$mac/assignedWorkerId").removeValue()
            }
        } catch (_: Exception) {}
        Outcome.Success(Unit)
    } catch (e: IllegalStateException) {
        Outcome.Failure(AppError.Validation(e.message ?: "Could not unassign this vest."))
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    /**
     * Swaps [oldVestId] for [newVestId] on the same worker in a single transaction, so a
     * failure can never leave the worker with no vest. The assignment history keeps a
     * closed record for the old vest and a fresh open record for the new one.
     */
    override suspend fun replaceVest(oldVestId: String, newVestId: String, assignedBy: String): Outcome<Unit> = try {
        firestore.runTransaction { txn ->
            val oldVestRef = vests.document(oldVestId)
            val newVestRef = vests.document(newVestId)

            // --- reads (all before any write, per Firestore transaction rules) ---
            val oldVestSnap = txn.get(oldVestRef)
            if (!oldVestSnap.exists()) throw IllegalStateException("Vest $oldVestId is not registered.")
            val workerId = oldVestSnap.getString("assignedWorkerId")?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("The old vest is not assigned to anyone.")

            val newVestSnap = txn.get(newVestRef)
            if (!newVestSnap.exists()) throw IllegalStateException("Vest $newVestId is not registered.")
            val newVestAssigned = newVestSnap.getString("assignedWorkerId")
            if (!newVestAssigned.isNullOrBlank() && newVestAssigned != workerId) {
                throw IllegalStateException("Vest $newVestId is already assigned to another worker.")
            }

            val workerRef = workers.document(workerId)
            if (!txn.get(workerRef).exists()) throw IllegalStateException("Worker $workerId no longer exists.")

            val openAssignmentSnap = oldVestSnap.getString("openAssignmentId")
                ?.let { txn.get(assignments.document(it)) }

            // --- writes ---
            val now = System.currentTimeMillis()
            val newAssignmentRef = assignments.document()
            txn.update(
                oldVestRef,
                mapOf(
                    "assignedWorkerId" to null,
                    "status" to VestStatus.AVAILABLE.name,
                    "openAssignmentId" to null,
                ),
            )
            if (openAssignmentSnap != null && openAssignmentSnap.exists()) {
                txn.update(openAssignmentSnap.reference, "unassignedAt", now)
            }
            txn.update(
                newVestRef,
                mapOf(
                    "assignedWorkerId" to workerId,
                    "status" to VestStatus.ASSIGNED.name,
                    "openAssignmentId" to newAssignmentRef.id,
                ),
            )
            txn.update(workerRef, "assignedVestId", newVestId)
            txn.set(
                newAssignmentRef,
                mapOf(
                    "workerId" to workerId,
                    "vestId" to newVestId,
                    "assignedAt" to now,
                    "unassignedAt" to null,
                    "assignedBy" to assignedBy,
                ),
            )
        }.await()
        Outcome.Success(Unit)
    } catch (e: IllegalStateException) {
        Outcome.Failure(AppError.Validation(e.message ?: "Could not replace this vest."))
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override fun observeAssignments(vestId: String): Flow<Outcome<List<VestAssignment>>> =
        assignments.whereEqualTo("vestId", vestId).snapshots()
            .map { qs ->
                Outcome.Success(
                    qs.documents.map { it.toVestAssignment() }.sortedByDescending { it.assignedAt },
                ) as Outcome<List<VestAssignment>>
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    // ---- transaction helpers ----
    // Firestore transactions require every read before any write, so callers that combine
    // these must not interleave other reads. Each helper reads only what it needs, up front.

    /** Pairs [vestId] to [workerId] and writes a fresh open assignment record. Supports clean reassignment. */
    private fun openAssignment(txn: Transaction, vestId: String, workerId: String, assignedBy: String) {
        val vestRef = vests.document(vestId)
        val workerRef = workers.document(workerId)
        val vestSnap = txn.get(vestRef)
        val workerSnap = txn.get(workerRef)

        if (!vestSnap.exists()) throw IllegalStateException("Vest $vestId is not registered.")
        if (!workerSnap.exists()) throw IllegalStateException("Worker $workerId no longer exists.")

        // All reads must precede any writes in a transaction:
        val vestAssigned = vestSnap.getString("assignedWorkerId")?.takeIf { it.isNotBlank() }
        val prevWorkerSnap = if (vestAssigned != null && vestAssigned != workerId) {
            txn.get(workers.document(vestAssigned))
        } else null

        val prevOpenId = vestSnap.getString("openAssignmentId")?.takeIf { it.isNotBlank() }
        val prevOpenSnap = if (prevOpenId != null && vestAssigned != null && vestAssigned != workerId) {
            txn.get(assignments.document(prevOpenId))
        } else null

        val workerVest = workerSnap.getString("assignedVestId")?.takeIf { it.isNotBlank() }
        val prevVestSnap = if (workerVest != null && workerVest != vestId) {
            txn.get(vests.document(workerVest))
        } else null
        val prevVestOpenId = prevVestSnap?.getString("openAssignmentId")?.takeIf { it.isNotBlank() }
        val prevVestOpenSnap = if (prevVestOpenId != null && prevVestOpenId != prevOpenId) {
            txn.get(assignments.document(prevVestOpenId))
        } else null

        val now = System.currentTimeMillis()

        // 1. Unassign previous worker if vest was already assigned to someone else
        if (prevWorkerSnap != null && prevWorkerSnap.exists()) {
            txn.update(prevWorkerSnap.reference, "assignedVestId", null)
        }
        if (prevOpenSnap != null && prevOpenSnap.exists()) {
            txn.update(
                prevOpenSnap.reference,
                mapOf(
                    "unassignedAt" to now,
                    "unassignedBy" to assignedBy,
                ),
            )
        }

        // 2. Unassign previous vest if new worker already held a different vest
        if (prevVestSnap != null && prevVestSnap.exists()) {
            txn.update(
                prevVestSnap.reference,
                mapOf(
                    "assignedWorkerId" to null,
                    "status" to VestStatus.AVAILABLE.name,
                    "openAssignmentId" to null,
                ),
            )
        }
        if (prevVestOpenSnap != null && prevVestOpenSnap.exists()) {
            txn.update(
                prevVestOpenSnap.reference,
                mapOf(
                    "unassignedAt" to now,
                    "unassignedBy" to assignedBy,
                ),
            )
        }

        // 3. Complete new assignment
        val assignmentRef = assignments.document()
        txn.update(
            vestRef,
            mapOf(
                "assignedWorkerId" to workerId,
                "status" to VestStatus.ASSIGNED.name,
                "openAssignmentId" to assignmentRef.id,
            ),
        )
        txn.update(workerRef, "assignedVestId", vestId)
        txn.set(
            assignmentRef,
            mapOf(
                "workerId" to workerId,
                "vestId" to vestId,
                "assignedAt" to now,
                "unassignedAt" to null,
                "assignedBy" to assignedBy,
            ),
        )
    }

    /**
     * Unpairs [vestId], clears the worker's back-reference, and stamps the open assignment
     * record closed. Returns the worker id that held the vest, or null if it was unassigned.
     */
    private fun closeAssignment(txn: Transaction, vestId: String): String? {
        val vestRef = vests.document(vestId)
        val vestSnap = txn.get(vestRef)
        if (!vestSnap.exists()) return null

        val assignedWorker = vestSnap.getString("assignedWorkerId")?.takeIf { it.isNotBlank() }
        val workerSnap = assignedWorker?.let { txn.get(workers.document(it)) }
        val openAssignmentId = vestSnap.getString("openAssignmentId")?.takeIf { it.isNotBlank() }
        val openAssignmentSnap = openAssignmentId
            ?.let { assignments.document(it) }
            ?.let { ref -> txn.get(ref) }

        txn.update(
            vestRef,
            mapOf(
                "assignedWorkerId" to null,
                "status" to VestStatus.AVAILABLE.name,
                "openAssignmentId" to null,
            ),
        )
        if (workerSnap != null && workerSnap.exists()) {
            txn.update(workerSnap.reference, "assignedVestId", null)
        }
        if (openAssignmentSnap != null && openAssignmentSnap.exists()) {
            txn.update(openAssignmentSnap.reference, "unassignedAt", System.currentTimeMillis())
        }
        return assignedWorker
    }

    override suspend fun updateVestWifiRemotely(
        vestId: String,
        ssid: String,
        pass: String,
    ): Outcome<Unit> = try {
        val trimmedSsid = ssid.trim()
        if (trimmedSsid.isBlank()) {
            Outcome.Failure(AppError.Validation("Please enter a valid Wi-Fi SSID."))
        } else {
            val cmdPayload = mapOf(
                "pending" to true,
                "mode" to "DIRECT_WIFI",
                "ssid" to trimmedSsid,
                "pass" to pass.trim(),
                "timestamp" to System.currentTimeMillis(),
            )
            database.getReference("vestsLive/$vestId/wifiCommand").setValue(cmdPayload).await()
            runCatching { vests.document(vestId).update("wifiSsid", trimmedSsid) }
            Outcome.Success(Unit)
        }
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun triggerVestBleModeRemotely(vestId: String): Outcome<Unit> = try {
        val cmdPayload = mapOf(
            "pending" to true,
            "mode" to "BLE_PAIRING",
            "timestamp" to System.currentTimeMillis(),
        )
        database.getReference("vestsLive/$vestId/wifiCommand").setValue(cmdPayload).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
