package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

data class MonthlyReport(
    val monthKey: String,
    val totalIncidents: Int,
    val safetyPercentage: Int,
    val activeWorkers: Int,
    val warningCount: Int,
    val dangerCount: Int,
    val emergencyCount: Int,
)

interface ReportRepository {
    /** One-time / paginated read of incidents grouped into monthly summaries (plan section 20). */
    suspend fun monthlyReports(contractorId: String, months: Int = 6): Outcome<List<MonthlyReport>>

    fun observeIncidentHistory(contractorId: String, limit: Long = 300): Flow<Outcome<List<Incident>>>
}
