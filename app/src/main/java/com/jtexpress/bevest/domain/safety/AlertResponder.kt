package com.jtexpress.bevest.domain.safety

import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.simulation.SimulationEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What an officer acknowledging or resolving an alert means for the worker's live
 * reading. Without this, a reading escalated to NO_RESPONSE stays an emergency on every
 * screen after the alert itself is closed.
 */
@Singleton
class AlertResponder @Inject constructor(
    private val monitoringRepository: MonitoringRepository,
    private val simulation: SimulationEngine,
) {
    /** An officer is on it: the vest stops escalating and the reading says so. */
    suspend fun acknowledged(workerId: String) {
        if (workerId.isBlank()) return
        simulation.onAlertAcknowledged(workerId)
        monitoringRepository.setSafetyResponse(workerId, SafetyResponseState.ACKNOWLEDGED)
    }

    /** Over: clear the escalation from the reading so the worker's live vitals decide again. */
    suspend fun resolved(workerId: String) {
        if (workerId.isBlank()) return
        simulation.onAlertResolved(workerId)
        monitoringRepository.setSafetyResponse(workerId, SafetyResponseState.NONE)
    }
}
