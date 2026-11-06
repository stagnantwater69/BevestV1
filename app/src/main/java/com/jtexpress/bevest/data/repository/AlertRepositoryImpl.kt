package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.mapper.toAlert
import com.jtexpress.bevest.data.mapper.toIncident
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.repository.AlertRepository
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

    override fun observeContractorActiveAlerts(contractorId: String): Flow<Outcome<List<Alert>>> =
        alerts.whereIn("status", listOf(AlertStatus.ACTIVE.name, AlertStatus.ACKNOWLEDGED.name))
            // The rules only let a contractor read alerts carrying their contractorId.
            .whereEqualTo("contractorId", contractorId)
            .snapshots()
            .map { qs ->
                Outcome.Success(
                    qs.documents.map { it.toAlert() }.sortedByDescending { it.createdAt },
                ) as Outcome<List<Alert>>
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }

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

    override suspend fun resolveAlert(alertId: String, byUserId: String, notes: String): Outcome<Unit> {
        return try {
            val cleanNotes = notes.trim()
            if (cleanNotes.isEmpty()) {
                return Outcome.Failure(AppError.Validation("Resolution Notes are required to resolve an alert."))
            }
            val alertRef = alerts.document(alertId)
            val alertSnap = runCatching { alertRef.get().await() }.getOrNull()
            if (alertSnap != null && alertSnap.exists()) {
                val currentStatus = AlertStatus.fromRaw(alertSnap.getString("status"))
                if (currentStatus == AlertStatus.ACTIVE) {
                    return Outcome.Failure(AppError.Validation("Please acknowledge the alert first before resolving it."))
                }
            }

            alertRef.update(
                mapOf(
                    "status" to AlertStatus.RESOLVED.name,
                    "resolvedAt" to FieldValue.serverTimestamp(),
                    "resolvedBy" to byUserId,
                    "resolutionNotes" to cleanNotes,
                ),
            ).await()

            // Also close/sync the corresponding incident so Contractor views reflect the resolved status & notes immediately.
            runCatching {
                val snap = alertSnap ?: alertRef.get().await()
                val linkedIncidentId = snap.getString("incidentId")?.takeIf { it.isNotBlank() } ?: alertId
                val incidentRef = incidents.document(linkedIncidentId)
                val existingInc = incidentRef.get().await()

                val updatePayload = mutableMapOf<String, Any?>(
                    "status" to AlertStatus.RESOLVED.name,
                    "outcome" to cleanNotes,
                    "resolutionNotes" to cleanNotes,
                    "resolvedBy" to byUserId,
                    "resolvedAt" to FieldValue.serverTimestamp(),
                )
                if (!existingInc.exists() && snap.exists()) {
                    updatePayload["workerId"] = snap.getString("workerId").orEmpty()
                    updatePayload["vestId"] = snap.getString("vestId").orEmpty()
                    updatePayload["siteId"] = snap.getString("siteId")
                    updatePayload["contractorId"] = snap.getString("contractorId")
                    updatePayload["type"] = snap.getString("type") ?: AlertType.FALL_DETECTED.name
                    updatePayload["severity"] = snap.getString("severity") ?: AlertSeverity.DANGER.name
                    updatePayload["alertId"] = alertId
                    updatePayload["createdAt"] = snap.get("createdAt") ?: System.currentTimeMillis()
                }
                incidentRef.set(updatePayload, SetOptions.merge()).await()

                // Also update any incident documents that reference this alertId under a different document ID.
                val matching = incidents.whereEqualTo("alertId", alertId).get().await()
                for (doc in matching.documents) {
                    if (doc.id != linkedIncidentId) {
                        doc.reference.update(
                            mapOf(
                                "status" to AlertStatus.RESOLVED.name,
                                "outcome" to cleanNotes,
                                "resolutionNotes" to cleanNotes,
                                "resolvedBy" to byUserId,
                                "resolvedAt" to FieldValue.serverTimestamp(),
                            ),
                        ).await()
                    }
                }
            }

            Outcome.Success(Unit)
        } catch (e: Exception) {
            Outcome.Failure(e.toAppError())
        }
    }

    override fun observeIncidents(
        contractorId: String?,
        siteId: String?,
        limit: Long,
    ): Flow<Outcome<List<Incident>>> {
        var incQuery: Query = incidents.orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        var alertQuery: Query = alerts.limit(limit)
        if (siteId != null) {
            incQuery = incidents.whereEqualTo("siteId", siteId)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
            alertQuery = alerts.whereEqualTo("siteId", siteId).limit(limit)
        } else if (contractorId != null) {
            incQuery = incidents.whereEqualTo("contractorId", contractorId)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
            alertQuery = alerts.whereEqualTo("contractorId", contractorId).limit(limit)
        }
        val alertsFlow = alertQuery.snapshots()
            .map { qs -> qs.documents.associate { it.id to it.toAlert() } }
            .catch { emit(emptyMap()) }

        return combine(incQuery.snapshots(), alertsFlow) { qs, alertsById ->
            val list = qs.documents.map { doc ->
                val inc = doc.toIncident()
                val matchingAlert = alertsById[inc.alertId ?: inc.incidentId] ?: alertsById[inc.incidentId]
                if (matchingAlert != null && matchingAlert.status == AlertStatus.RESOLVED) {
                    val mergedNotes = inc.resolutionNotes?.takeIf { it.isNotBlank() }
                        ?: matchingAlert.resolutionNotes?.takeIf { it.isNotBlank() }
                        ?: inc.outcome?.takeIf { it.isNotBlank() }
                        ?: "Resolved"
                    inc.copy(
                        outcome = mergedNotes,
                        resolutionNotes = mergedNotes,
                        resolvedBy = inc.resolvedBy ?: matchingAlert.resolvedBy,
                    )
                } else {
                    inc
                }
            }
            Outcome.Success(list) as Outcome<List<Incident>>
        }.catch { emit(Outcome.Failure(it.toAppError())) }
    }

    override fun observeIncident(incidentId: String): Flow<Outcome<Incident>> {
        val alertFlow = alerts.document(incidentId).snapshots()
            .map { if (it.exists()) it.toAlert() else null }
            .catch { emit(null) }

        return combine(incidents.document(incidentId).snapshots(), alertFlow) { snap, directAlert ->
            if (!snap.exists()) {
                Outcome.Failure(AppError.NotFound("This incident no longer exists."))
            } else {
                val inc = snap.toIncident()
                val matchingAlert = directAlert ?: inc.alertId?.takeIf { it.isNotBlank() && it != incidentId }?.let { aId ->
                    runCatching { alerts.document(aId).get().await() }.getOrNull()?.takeIf { it.exists() }?.toAlert()
                }
                if (matchingAlert != null && matchingAlert.status == AlertStatus.RESOLVED) {
                    val mergedNotes = inc.resolutionNotes?.takeIf { it.isNotBlank() }
                        ?: matchingAlert.resolutionNotes?.takeIf { it.isNotBlank() }
                        ?: inc.outcome?.takeIf { it.isNotBlank() }
                        ?: "Resolved"
                    Outcome.Success(
                        inc.copy(
                            outcome = mergedNotes,
                            resolutionNotes = mergedNotes,
                            resolvedBy = inc.resolvedBy ?: matchingAlert.resolvedBy,
                        ),
                    )
                } else {
                    Outcome.Success(inc)
                }
            }
        }.catch { emit(Outcome.Failure(it.toAppError())) }
    }

    override suspend fun resolveIncident(
        incidentId: String,
        byUserId: String,
        outcome: String,
        notes: String,
    ): Outcome<Unit> = try {
        val cleanNotes = notes.trim().ifBlank { outcome.trim() }
        val ref = incidents.document(incidentId)
        ref.update(
            mapOf(
                "status" to AlertStatus.RESOLVED.name,
                "outcome" to cleanNotes,
                "resolutionNotes" to cleanNotes,
                "resolvedBy" to byUserId,
                "resolvedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        // Closing the incident closes the alert it grew from, or that alert stays
        // "active" on the dashboard forever.
        val linkedAlertId = ref.get().await().getString("alertId")?.takeIf { it.isNotBlank() } ?: incidentId
        runCatching {
            alerts.document(linkedAlertId).update(
                mapOf(
                    "status" to AlertStatus.RESOLVED.name,
                    "resolvedAt" to FieldValue.serverTimestamp(),
                    "resolvedBy" to byUserId,
                    "resolutionNotes" to cleanNotes,
                ),
            ).await()
        }
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
