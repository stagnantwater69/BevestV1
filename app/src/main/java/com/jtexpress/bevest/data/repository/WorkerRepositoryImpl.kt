package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.retryReporting
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

    override fun observeWorkers(contractorId: String?, siteId: String?): Flow<Outcome<List<Worker>>> {
        var query: Query = workers
        if (siteId != null) query = query.whereEqualTo("siteId", siteId)
        else if (contractorId != null) query = query.whereEqualTo("contractorId", contractorId)
        return query.snapshots()
            .map { qs -> Outcome.Success(qs.documents.map { it.toWorker() }) as Outcome<List<Worker>> }
            .retryReporting()
    }

    override fun observeWorker(workerId: String): Flow<Outcome<Worker>> =
        workers.document(workerId).snapshots()
            .map { snap ->
                if (snap.exists()) Outcome.Success(snap.toWorker())
                else Outcome.Failure(AppError.NotFound("This worker no longer exists."))
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    override suspend fun getWorker(workerId: String): Outcome<Worker> = try {
        val snap = workers.document(workerId).get().await()
        if (snap.exists()) Outcome.Success(snap.toWorker())
        else Outcome.Failure(AppError.NotFound())
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    private val workerCounter get() =
        firestore.collection(FirebasePaths.COUNTERS).document(FirebasePaths.WORKER_COUNTER_DOC)

    override suspend fun addWorker(worker: Worker): Outcome<Unit> {
        val taken = Outcome.Failure(AppError.Validation("A worker with ID ${worker.workerId} already exists."))
        val ref = workers.document(worker.workerId)
        return try {
            // In a transaction, so two officers can't both create the same ID.
            val created = firestore.runTransaction { tx ->
                if (tx.get(ref).exists()) return@runTransaction false
                val last = tx.get(workerCounter).getLong("last") ?: 0L
                tx.set(ref, worker.toMap())
                workerNumber(worker.workerId)?.takeIf { it > last }?.let {
                    tx.set(workerCounter, mapOf("last" to it))
                }
                true
            }.await()
            if (created) Outcome.Success(Unit) else taken
        } catch (e: FirebaseFirestoreException) {
            // The rules hide other sites' workers: reading an ID that exists elsewhere is
            // denied, which means it's taken, not that this officer can't add workers.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && idExistsElsewhere(ref)) taken
            else Outcome.Failure(e.toAppError())
        } catch (e: Exception) {
            Outcome.Failure(e.toAppError())
        }
    }

    /** A plain read of a free ID is allowed; only an ID held by another site is denied. */
    private suspend fun idExistsElsewhere(ref: com.google.firebase.firestore.DocumentReference): Boolean = try {
        ref.get().await().exists()
    } catch (e: FirebaseFirestoreException) {
        e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    override suspend fun lastIssuedWorkerNumber(): Int = try {
        workerCounter.get().await().getLong("last")?.toInt() ?: 0
    } catch (e: Exception) {
        0
    }

    private fun workerNumber(id: String): Long? =
        Regex("WRK-(\\d+)").find(id)?.groupValues?.get(1)?.toLongOrNull()

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
