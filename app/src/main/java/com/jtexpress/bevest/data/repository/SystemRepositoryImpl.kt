package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.domain.repository.ActivityEntry
import com.jtexpress.bevest.domain.repository.SystemRepository
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SystemRepository {

    private val activity get() = firestore.collection(FirebasePaths.ACTIVITY_LOG)

    override fun observeActivity(limit: Long): Flow<Outcome<List<ActivityEntry>>> =
        activity.orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).snapshots()
            .map { qs ->
                Outcome.Success(
                    qs.documents.map {
                        ActivityEntry(
                            id = it.id,
                            message = it.getString("message").orEmpty(),
                            actorId = it.getString("actorId").orEmpty(),
                            createdAt = it.getLong("createdAt") ?: 0L,
                        )
                    },
                ) as Outcome<List<ActivityEntry>>
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    override suspend fun logActivity(message: String, actorId: String): Outcome<Unit> = try {
        activity.add(
            mapOf(
                "message" to message,
                "actorId" to actorId,
                "createdAt" to System.currentTimeMillis(),
            ),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
