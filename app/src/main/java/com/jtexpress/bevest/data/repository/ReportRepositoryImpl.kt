package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.mapper.toIncident
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.repository.MonthlyReport
import com.jtexpress.bevest.domain.repository.ReportRepository
import com.jtexpress.bevest.utils.DateTimeUtils
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ReportRepository {

    private val incidents get() = firestore.collection(FirebasePaths.INCIDENTS)
    private val workers get() = firestore.collection(FirebasePaths.WORKERS)

    override suspend fun monthlyReports(contractorId: String, months: Int): Outcome<List<MonthlyReport>> = try {
        val since = Calendar.getInstance().apply {
            add(Calendar.MONTH, -(months - 1))
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis

        val incidentDocs = incidents
            .whereEqualTo("contractorId", contractorId)
            .whereGreaterThanOrEqualTo("createdAt", since)
            .get().await()
            .documents.map { it.toIncident() }

        val activeWorkers = workers
            .whereEqualTo("contractorId", contractorId)
            .whereEqualTo("active", true)
            .get().await().size()

        val byMonth = incidentDocs.groupBy { DateTimeUtils.monthKey(it.createdAt) }
        val keys = (0 until months).map { offset ->
            DateTimeUtils.monthKey(
                Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }.timeInMillis,
            )
        }

        val reports = keys.map { key ->
            val list = byMonth[key].orEmpty()
            val danger = list.count { it.severity == AlertSeverity.DANGER }
            val emergency = list.count { it.severity == AlertSeverity.EMERGENCY }
            val warning = list.count { it.severity == AlertSeverity.WARNING }
            val safety = if (activeWorkers == 0) 100
            else (100 - (list.size * 100 / (activeWorkers * 30)).coerceIn(0, 100))
            MonthlyReport(
                monthKey = key,
                totalIncidents = list.size,
                safetyPercentage = safety,
                activeWorkers = activeWorkers,
                warningCount = warning,
                dangerCount = danger,
                emergencyCount = emergency,
            )
        }
        Outcome.Success(reports)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override fun observeIncidentHistory(contractorId: String, limit: Long): Flow<Outcome<List<Incident>>> =
        incidents.whereEqualTo("contractorId", contractorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots()
            .map { qs -> Outcome.Success(qs.documents.map { it.toIncident() }) as Outcome<List<Incident>> }
            .catch { emit(Outcome.Failure(it.toAppError())) }
}
