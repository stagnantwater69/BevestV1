package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

interface MonitoringRepository {
    /** Live sensor stream for one worker (Realtime Database). Null while no data has arrived. */
    fun observeReading(workerId: String): Flow<SensorReading?>

    /** Live readings for many workers at once, keyed by workerId. */
    fun observeReadings(workerIds: List<String>): Flow<Map<String, SensorReading>>

    /** Manually start a safety response for a worker (plan section 12 & 17). */
    suspend fun initiateSafetyResponse(workerId: String, byUserId: String): Outcome<Unit>

    suspend fun setSafetyResponse(workerId: String, state: SafetyResponseState): Outcome<Unit>

    /** Debug simulator only — writes a reading to the live path. */
    suspend fun pushSimulatedReading(reading: SensorReading): Outcome<Unit>

    /** Debug simulator only — removes the live reading so the worker reads as OFFLINE. */
    suspend fun clearReading(workerId: String): Outcome<Unit>
}
