package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.mapper.toVest
import com.jtexpress.bevest.data.mapper.toVestAssignment
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestAssignment
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.utils.AppError
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VestRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : VestRepository {

    private val vests get() = firestore.collection(FirebasePaths.VESTS)
    private val workers get() = firestore.collection(FirebasePaths.WORKERS)
    private val assignments get() = firestore.collection(FirebasePaths.VEST_ASSIGNMENTS)

    override fun observeVests(): Flow<Outcome<List<Vest>>> =
        vests.snapshots()
            .map { qs -> Outcome.Success(qs.documents.map { it.toVest() }) as Outcome<List<Vest>> }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    override fun observeVest(vestId: String): Flow<Outcome<Vest>> =
        vests.document(vestId).snapshots()
            .map { snap ->
                if (snap.exists()) Outcome.Success(snap.toVest())
                else Outcome.Failure(AppError.NotFound("This vest is not registered."))
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }

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

    override suspend fun setStatus(vestId: String, status: VestStatus): Outcome<Unit> = try {
        vests.document(vestId).update("status", status.name).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun assignVest(vestId: String, workerId: String, assignedBy: String): Outcome<Unit> = try {
        firestore.runTransaction { txn ->
            openAssignment(txn, vestId, workerId, assignedBy)
        }.await()
        Outcome.Success(Unit)
    } catch (e: IllegalStateException) {
        Outcome.Failure(AppError.Validation(e.message ?: "Could not pair this vest."))
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun unassignVest(vestId: String, unassignedBy: String): Outcome<Unit> = try {
        firestore.runTransaction { txn ->
            closeAssignment(txn, vestId)
        }.await()
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

    /** Pairs [vestId] to [workerId] and writes a fresh open assignment record. */
    private fun openAssignment(txn: Transaction, vestId: String, workerId: String, assignedBy: String) {
        val vestRef = vests.document(vestId)
        val workerRef = workers.document(workerId)
        val vestSnap = txn.get(vestRef)
        val workerSnap = txn.get(workerRef)

        if (!vestSnap.exists()) throw IllegalStateException("Vest $vestId is not registered.")
        if (!workerSnap.exists()) throw IllegalStateException("Worker $workerId no longer exists.")

        val vestAssigned = vestSnap.getString("assignedWorkerId")
        if (!vestAssigned.isNullOrBlank() && vestAssigned != workerId) {
            throw IllegalStateException("Vest $vestId is already assigned to another worker.")
        }
        val workerVest = workerSnap.getString("assignedVestId")
        if (!workerVest.isNullOrBlank() && workerVest != vestId) {
            throw IllegalStateException("This worker already has vest $workerVest. Unassign it first.")
        }

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
                "assignedAt" to System.currentTimeMillis(),
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
        if (!vestSnap.exists()) throw IllegalStateException("Vest $vestId is not registered.")

        val assignedWorker = vestSnap.getString("assignedWorkerId")
        val openAssignmentId = vestSnap.getString("openAssignmentId")
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
        if (!assignedWorker.isNullOrBlank()) {
            txn.update(workers.document(assignedWorker), "assignedVestId", null)
        }
        if (openAssignmentSnap != null && openAssignmentSnap.exists()) {
            txn.update(openAssignmentSnap.reference, "unassignedAt", System.currentTimeMillis())
        }
        return assignedWorker?.takeIf { it.isNotBlank() }
    }
}
