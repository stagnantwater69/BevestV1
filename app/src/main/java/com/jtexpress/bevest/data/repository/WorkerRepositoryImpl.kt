package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.mapper.toWorker
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.utils.AppError
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkerRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : WorkerRepository {

    private val workers get() = firestore.collection(FirebasePaths.WORKERS)
    private val vests get() = firestore.collection(FirebasePaths.VESTS)

    override fun observeWorkers(contractorId: String?, siteId: String?): Flow<Outcome<List<Worker>>> {
        var query: Query = workers
        if (siteId != null) query = query.whereEqualTo("siteId", siteId)
        else if (contractorId != null) query = query.whereEqualTo("contractorId", contractorId)
        return combine(
            query.snapshots(),
            vests.snapshots(),
        ) { workersSnap, vestsSnap ->
            val vestOwners = vestOwnersOf(vestsSnap)
            val list = workersSnap.documents.map { doc ->
                val w = doc.toWorker()
                val assigned = w.assignedVestId
                if (assigned != null && !isValidAssignment(vestOwners, assigned, w.workerId)) {
                    // Vest was deleted from database! Automatically self-heal worker record
                    // Only persist the repair when the vest list came from the server, so a
                    // partial offline cache can never wipe a valid assignment.
                    if (!vestsSnap.metadata.isFromCache) doc.reference.update("assignedVestId", null)
                    w.copy(assignedVestId = null)
                } else {
                    w
                }
            }
            Outcome.Success(list) as Outcome<List<Worker>>
        }.catch { emit(Outcome.Failure(it.toAppError())) }
    }

    override fun observeWorker(workerId: String): Flow<Outcome<Worker>> =
        combine(
            workers.document(workerId).snapshots(),
            vests.snapshots(),
        ) { snap, vestsSnap ->
            if (!snap.exists()) {
                Outcome.Failure(AppError.NotFound("This worker no longer exists."))
            } else {
                val vestOwners = vestOwnersOf(vestsSnap)
                val w = snap.toWorker()
                val assigned = w.assignedVestId
                if (assigned != null && !isValidAssignment(vestOwners, assigned, w.workerId)) {
                    if (!vestsSnap.metadata.isFromCache) snap.reference.update("assignedVestId", null)
                    Outcome.Success(w.copy(assignedVestId = null))
                } else {
                    Outcome.Success(w)
                }
            }
        }.catch { emit(Outcome.Failure(it.toAppError())) }

    override suspend fun getWorker(workerId: String): Outcome<Worker> = try {
        val snap = workers.document(workerId).get().await()
        if (snap.exists()) {
            val w = snap.toWorker()
            val assigned = w.assignedVestId
            if (assigned != null) {
                val vestSnap = vests.document(assigned).get().await()
                val owner = vestSnap.getString("assignedWorkerId")?.takeIf { it.isNotBlank() }
                val inDirectory = vestSnap.getBoolean("inCompanyDirectory") == true
                if (!vestSnap.exists() || !inDirectory || owner != w.workerId) {
                    workers.document(workerId).update("assignedVestId", null)
                    Outcome.Success(w.copy(assignedVestId = null))
                } else {
                    Outcome.Success(w)
                }
            } else {
                Outcome.Success(w)
            }
        } else {
            Outcome.Failure(AppError.NotFound())
        }
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    /**
     * Map of vestId -> assignedWorkerId for vests that are actually registered in a company
     * directory. A vest that was deleted by the Developer, or returned to inventory, is absent.
     */
    private fun vestOwnersOf(vestsSnap: com.google.firebase.firestore.QuerySnapshot): Map<String, String?> =
        vestsSnap.documents
            .filter { it.getBoolean("inCompanyDirectory") == true }
            .associate { it.id to it.getString("assignedWorkerId")?.takeIf { s -> s.isNotBlank() } }

    /**
     * A worker's vest only counts if the vest still exists in the directory AND the vest
     * points back to the same worker. Anything else is a stale/orphaned link.
     */
    private fun isValidAssignment(vestOwners: Map<String, String?>, vestId: String, workerId: String): Boolean =
        vestOwners.containsKey(vestId) && vestOwners[vestId] == workerId

    override suspend fun addWorker(worker: Worker): Outcome<Unit> = try {
        val existing = workers.document(worker.workerId).get().await()
        if (existing.exists()) {
            Outcome.Failure(AppError.Validation("A worker with ID ${worker.workerId} already exists."))
        } else {
            workers.document(worker.workerId).set(worker.toMap()).await()
            Outcome.Success(Unit)
        }
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun updateWorker(worker: Worker): Outcome<Unit> = try {
        // Only the fields editable from the worker form. Vest assignment and live status
        // are owned by the pairing flow and the safety engine (plan section 31.5-31.7).
        workers.document(worker.workerId).update(
            mapOf(
                "firstName" to worker.firstName,
                "lastName" to worker.lastName,
                "phone" to worker.phone,
                "photoUrl" to worker.photoUrl,
                "siteId" to worker.siteId,
                "active" to worker.active,
            ),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun setActive(workerId: String, active: Boolean): Outcome<Unit> = try {
        workers.document(workerId).update("active", active).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun setCurrentStatus(workerId: String, status: SafetyStatus): Outcome<Unit> = try {
        workers.document(workerId).update("currentStatus", status.name).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun setAssignedVest(workerId: String, vestId: String?): Outcome<Unit> = try {
        workers.document(workerId).update("assignedVestId", vestId).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun uploadPhoto(workerId: String, bytes: ByteArray): Outcome<String> = try {
        val ref = storage.reference.child(FirebasePaths.workerPhoto(workerId))
        ref.putBytes(bytes).await()
        val url = ref.downloadUrl.await().toString()
        workers.document(workerId).update("photoUrl", url).await()
        Outcome.Success(url)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    private fun Worker.toMap() = mapOf(
        "firstName" to firstName,
        "lastName" to lastName,
        "phone" to phone,
        "photoUrl" to photoUrl,
        "contractorId" to contractorId,
        "siteId" to siteId,
        "assignedVestId" to assignedVestId,
        "currentStatus" to currentStatus.name,
        "active" to active,
    )
}
