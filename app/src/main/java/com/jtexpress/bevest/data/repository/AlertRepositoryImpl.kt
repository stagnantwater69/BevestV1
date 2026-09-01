package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.mapper.toAlert
import com.jtexpress.bevest.data.mapper.toIncident
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.utils.AppError
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : AlertRepository {

    private val alerts get() = firestore.collection(FirebasePaths.ALERTS)
    private val incidents get() = firestore.collection(FirebasePaths.INCIDENTS)

    override fun observeActiveAlerts(siteId: String?): Flow<Outcome<List<Alert>>> {
        var query: Query = alerts.whereIn("status", listOf(AlertStatus.ACTIVE.name, AlertStatus.ACKNOWLEDGED.name))
        if (siteId != null) query = query.whereEqualTo("siteId", siteId)
        return query.snapshots()
            .map { qs ->
                Outcome.Success(
                    qs.documents.map { it.toAlert() }.sortedByDescending { it.createdAt },
                ) as Outcome<List<Alert>>
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }
    }

    override fun observeAlertHistory(siteId: String?, limit: Long): Flow<Outcome<List<Alert>>> {
        var query: Query = alerts.orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        if (siteId != null) query = alerts.whereEqualTo("siteId", siteId)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        return query.snapshots()
            .map { qs -> Outcome.Success(qs.documents.map { it.toAlert() }) as Outcome<List<Alert>> }
            .catch { emit(Outcome.Failure(it.toAppError())) }
    }

    override fun observeAlert(alertId: String): Flow<Outcome<Alert>> =
        alerts.document(alertId).snapshots()
            .map { snap ->
                if (snap.exists()) Outcome.Success(snap.toAlert())
                else Outcome.Failure(AppError.NotFound("This alert no longer exists."))
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    override suspend fun acknowledgeAlert(alertId: String, byUserId: String): Outcome<Unit> = try {
        alerts.document(alertId).update(
            mapOf(
                "status" to AlertStatus.ACKNOWLEDGED.name,
                "acknowledgedAt" to FieldValue.serverTimestamp(),
                "acknowledgedBy" to byUserId,
            ),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun resolveAlert(alertId: String, byUserId: String, notes: String): Outcome<Unit> = try {
        alerts.document(alertId).update(
            mapOf(
                "status" to AlertStatus.RESOLVED.name,
                "resolvedAt" to FieldValue.serverTimestamp(),
                "resolvedBy" to byUserId,
                "resolutionNotes" to notes,
            ),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override fun observeIncidents(
        contractorId: String?,
        siteId: String?,
        limit: Long,
    ): Flow<Outcome<List<Incident>>> {
        var query: Query = incidents.orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        if (siteId != null) {
            query = incidents.whereEqualTo("siteId", siteId)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        } else if (contractorId != null) {
            query = incidents.whereEqualTo("contractorId", contractorId)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        }
        return query.snapshots()
            .map { qs -> Outcome.Success(qs.documents.map { it.toIncident() }) as Outcome<List<Incident>> }
            .catch { emit(Outcome.Failure(it.toAppError())) }
    }

    override fun observeIncident(incidentId: String): Flow<Outcome<Incident>> =
        incidents.document(incidentId).snapshots()
            .map { snap ->
                if (snap.exists()) Outcome.Success(snap.toIncident())
                else Outcome.Failure(AppError.NotFound("This incident no longer exists."))
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    override suspend fun resolveIncident(
        incidentId: String,
        byUserId: String,
        outcome: String,
        notes: String,
    ): Outcome<Unit> = try {
        incidents.document(incidentId).update(
            mapOf(
                "outcome" to outcome,
                "resolutionNotes" to notes,
                "resolvedBy" to byUserId,
                "resolvedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
