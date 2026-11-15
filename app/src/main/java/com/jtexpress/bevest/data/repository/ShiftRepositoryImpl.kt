package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.retryForever
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.mapper.toShift
import com.jtexpress.bevest.domain.model.Shift
import com.jtexpress.bevest.domain.repository.ShiftRepository
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ShiftRepository {

    private val shifts get() = firestore.collection(FirebasePaths.SHIFTS)

    /** One document per site per day, so two officers can't start the same shift twice. */
    private fun shiftId(siteId: String, date: String) = "${siteId}_$date"

    override fun observeShift(siteId: String, date: String): Flow<Shift?> =
        shifts.document(shiftId(siteId, date)).snapshots()
            .map { snap -> if (snap.exists()) snap.toShift() else null }
            .retryForever()

    override suspend fun saveCheck(
        siteId: String,
        contractorId: String?,
        date: String,
        byUserId: String,
        byName: String,
        workerCount: Int,
        readyCount: Int,
        issues: List<String>,
    ): Outcome<Unit> = try {
        val ref = shifts.document(shiftId(siteId, date))
        val check = mapOf(
            "checkedAt" to FieldValue.serverTimestamp(),
            "checkedBy" to byUserId,
            "workerCount" to workerCount,
            "readyCount" to readyCount,
            "issues" to issues,
        )
        firestore.runTransaction { tx ->
            if (tx.get(ref).exists()) {
                tx.update(ref, check)
            } else {
                tx.set(
                    ref,
                    check + mapOf(
                        "siteId" to siteId,
                        "contractorId" to contractorId,
                        "date" to date,
                        "startedAt" to FieldValue.serverTimestamp(),
                        "startedBy" to byUserId,
                        "startedByName" to byName,
                    ),
                )
            }
        }.await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
