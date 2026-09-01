package com.jtexpress.bevest.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.firebase.valueEvents
import com.jtexpress.bevest.data.mapper.toSensorReading
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MonitoringRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
) : MonitoringRepository {

    /**
     * Emits null immediately, then real readings as they arrive. Screens must render
     * their loaded state right away and mark data as offline/stale — never block the UI
     * waiting for a vest that may never transmit (plan section 20 & 21).
     */
    override fun observeReading(workerId: String): Flow<SensorReading?> =
        database.getReference(FirebasePaths.liveReading(workerId))
            .valueEvents()
            .map { it.toSensorReading(workerId) }
            .catch { emit(null) }
            .onStart { emit(null) }

    override fun observeReadings(workerIds: List<String>): Flow<Map<String, SensorReading>> {
        if (workerIds.isEmpty()) return flowOf(emptyMap())
        val flows = workerIds.map { id ->
            database.getReference(FirebasePaths.liveReading(id)).valueEvents()
                .map { id to it.toSensorReading(id) }
                .catch { emit(id to null) }
                .onStart { emit(id to null) }
        }
        return combine(flows) { pairs ->
            pairs.mapNotNull { (id, reading) -> reading?.let { id to it } }.toMap()
        }
    }

    override suspend fun initiateSafetyResponse(workerId: String, byUserId: String): Outcome<Unit> =
        setSafetyResponse(workerId, SafetyResponseState.WAITING)

    override suspend fun setSafetyResponse(
        workerId: String,
        state: SafetyResponseState,
    ): Outcome<Unit> = try {
        database.getReference(FirebasePaths.liveReading(workerId))
            .child("safetyResponse").setValue(state.name).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun clearReading(workerId: String): Outcome<Unit> = try {
        database.getReference(FirebasePaths.liveReading(workerId)).removeValue().await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun pushSimulatedReading(reading: SensorReading): Outcome<Unit> = try {
        database.getReference(FirebasePaths.liveReading(reading.workerId)).setValue(
            mapOf(
                "vestId" to reading.vestId,
                "heartRate" to reading.heartRate,
                "temperature" to reading.temperature,
                "motionState" to reading.motionState.name,
                "fallDetected" to reading.fallDetected,
                "latitude" to reading.latitude,
                "longitude" to reading.longitude,
                "battery" to reading.batteryPercent,
                "safetyResponse" to reading.safetyResponse.name,
                "timestamp" to reading.timestamp,
            ),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
