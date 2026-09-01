package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.safety.ThresholdConfig
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SettingsRepository {

    private val settings get() = firestore.collection(FirebasePaths.SETTINGS)

    override fun observeThresholds(): Flow<ThresholdConfig> =
        settings.document(FirebasePaths.THRESHOLDS_DOC).snapshots()
            .map { snap ->
                if (!snap.exists()) ThresholdConfig.DEFAULT
                else ThresholdConfig(
                    heartRateHigh = snap.getLong("heartRateHigh")?.toInt() ?: ThresholdConfig.DEFAULT.heartRateHigh,
                    temperatureHigh = snap.getDouble("temperatureHigh") ?: ThresholdConfig.DEFAULT.temperatureHigh,
                    warningDurationSeconds = snap.getLong("warningDurationSeconds")?.toInt()
                        ?: ThresholdConfig.DEFAULT.warningDurationSeconds,
                    responseTimeoutSeconds = snap.getLong("responseTimeoutSeconds")?.toInt()
                        ?: ThresholdConfig.DEFAULT.responseTimeoutSeconds,
                    offlineTimeoutSeconds = snap.getLong("offlineTimeoutSeconds")?.toInt()
                        ?: ThresholdConfig.DEFAULT.offlineTimeoutSeconds,
                )
            }
            .catch { emit(ThresholdConfig.DEFAULT) }
            .onStart { emit(ThresholdConfig.DEFAULT) }

    override suspend fun updateThresholds(config: ThresholdConfig): Outcome<Unit> = try {
        settings.document(FirebasePaths.THRESHOLDS_DOC).set(
            mapOf(
                "heartRateHigh" to config.heartRateHigh,
                "temperatureHigh" to config.temperatureHigh,
                "warningDurationSeconds" to config.warningDurationSeconds,
                "responseTimeoutSeconds" to config.responseTimeoutSeconds,
                "offlineTimeoutSeconds" to config.offlineTimeoutSeconds,
            ),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override fun observeMaintenanceMode(): Flow<Boolean> =
        settings.document(FirebasePaths.SYSTEM_DOC).snapshots()
            .map { it.getBoolean("maintenanceMode") ?: false }
            .catch { emit(false) }
            .onStart { emit(false) }

    override suspend fun setMaintenanceMode(enabled: Boolean): Outcome<Unit> = try {
        settings.document(FirebasePaths.SYSTEM_DOC)
            .set(mapOf("maintenanceMode" to enabled), com.google.firebase.firestore.SetOptions.merge())
            .await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
