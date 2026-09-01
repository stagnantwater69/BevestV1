package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    fun observeActiveAlerts(siteId: String?): Flow<Outcome<List<Alert>>>

    fun observeAlertHistory(siteId: String?, limit: Long = 100): Flow<Outcome<List<Alert>>>

    fun observeAlert(alertId: String): Flow<Outcome<Alert>>

    suspend fun acknowledgeAlert(alertId: String, byUserId: String): Outcome<Unit>

    suspend fun resolveAlert(alertId: String, byUserId: String, notes: String): Outcome<Unit>

    fun observeIncidents(contractorId: String?, siteId: String?, limit: Long = 200): Flow<Outcome<List<Incident>>>

    fun observeIncident(incidentId: String): Flow<Outcome<Incident>>

    suspend fun resolveIncident(incidentId: String, byUserId: String, outcome: String, notes: String): Outcome<Unit>
}
