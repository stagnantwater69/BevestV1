package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.retryReporting
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
            .retryReporting()
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
            .retryReporting()

    override fun observeAlertHistory(siteId: String?, limit: Long): Flow<Outcome<List<Alert>>> {
        var query: Query = alerts.orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        if (siteId != null) query = alerts.whereEqualTo("siteId", siteId)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        return query.snapshots()
            .map { qs -> Outcome.Success(qs.documents.map { it.toAlert() }) as Outcome<List<Alert>> }
            .retryReporting()
    }

    override fun observeAlert(alertId: String): Flow<Outcome<Alert>> =
        alerts.document(alertId).snapshots()
            .map { snap ->
                if (snap.exists()) Outcome.Success(snap.toAlert())
                else Outcome.Failure(AppError.NotFound("This alert no longer exists."))
            }
            .retryReporting()

    override suspend fun acknowledgeAlert(alertId: String, byUserId: String): Outcome<Unit> = try {
        val ref = alerts.document(alertId)
        val acknowledged = firestore.runTransaction { tx ->
            // Only an unanswered alert can be acknowledged — never reopen a resolved one.
            if (AlertStatus.fromRaw(tx.get(ref).getString("status")) != AlertStatus.ACTIVE) {
                return@runTransaction false
            }
            // Stored with the alert so every officer sees who has it, without needing to
            // read each other's profiles.
            val me = tx.get(firestore.collection(FirebasePaths.USERS).document(byUserId))
            val name = listOfNotNull(me.getString("firstName"), me.getString("lastName"))
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { me.getString("email") ?: "An officer" }
            tx.update(
                ref,
                mapOf(
                    "status" to AlertStatus.ACKNOWLEDGED.name,
                    "acknowledgedAt" to FieldValue.serverTimestamp(),
                    "acknowledgedBy" to byUserId,
                    "acknowledgedByName" to name,
                ),
            )
            true
        }.await()
        if (acknowledged) Outcome.Success(Unit)
        else Outcome.Failure(AppError.Validation("This alert has already been handled."))
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun resolveAlert(alertId: String, byUserId: String, notes: String): Outcome<Unit> = try {
        val alertRef = alerts.document(alertId)
        firestore.runTransaction { tx ->
            // An escalated alert has an incident; closing the alert closes it too, or it
            // stays open in the contractor's reports forever.
            val incidentRef = tx.get(alertRef).getString("incidentId")
                ?.takeIf { it.isNotBlank() }
                ?.let { incidents.document(it) }
            val incident = incidentRef?.let { tx.get(it) }
            tx.update(
                alertRef,
                mapOf(
                    "status" to AlertStatus.RESOLVED.name,
                    "resolvedAt" to FieldValue.serverTimestamp(),
                    "resolvedBy" to byUserId,
                    "resolutionNotes" to notes,
                ),
            )
            if (incident != null && incident.exists() && incident.get("resolvedAt") == null) {
                tx.update(
                    incident.reference,
                    mapOf(
                        "outcome" to "Resolved",
                        "resolutionNotes" to notes,
                        "resolvedBy" to byUserId,
                        "resolvedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
        }.await()
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
            .retryReporting()
    }

    override fun observeIncident(incidentId: String): Flow<Outcome<Incident>> =
        incidents.document(incidentId).snapshots()
            .map { snap ->
                if (snap.exists()) Outcome.Success(snap.toIncident())
                else Outcome.Failure(AppError.NotFound("This incident no longer exists."))
            }
            .retryReporting()

    override suspend fun resolveIncident(
        incidentId: String,
        byUserId: String,
        outcome: String,
        notes: String,
    ): Outcome<Unit> = try {
        val ref = incidents.document(incidentId)
        ref.update(
            mapOf(
                "outcome" to outcome,
                "resolutionNotes" to notes,
                "resolvedBy" to byUserId,
                "resolvedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        // Closing the incident closes the alert it grew from, or that alert stays
        // "active" on the dashboard forever.
        ref.get().await().getString("alertId")?.takeIf { it.isNotBlank() }?.let { alertId ->
            runCatching { resolveAlert(alertId, byUserId, notes.ifBlank { outcome }) }
        }
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
