package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.safety.AgeVitalThreshold
import com.jtexpress.bevest.domain.safety.ThresholdConfig
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SettingsRepository {

    private val settings get() = firestore.collection(FirebasePaths.SETTINGS)

    private fun Any?.asInt(fallback: Int): Int = when (this) {
        is Number -> this.toInt()
        is String -> this.toIntOrNull() ?: this.toDoubleOrNull()?.toInt() ?: fallback
        else -> fallback
    }

    private fun Any?.asDouble(fallback: Double): Double = when (this) {
        is Number -> this.toDouble()
        is String -> this.toDoubleOrNull() ?: fallback
        else -> fallback
    }

    override fun observeThresholds(): Flow<ThresholdConfig> =
        settings.document(FirebasePaths.THRESHOLDS_DOC)
            .snapshots()
            .map { snap ->
                val d = ThresholdConfig.DEFAULT
                if (!snap.exists()) return@map d

                val rawBrackets = snap.get("ageBrackets") as? List<*>
                val parsedBrackets = rawBrackets?.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val minAge = map["minAge"].asInt(18)
                    val maxAge = map["maxAge"].asInt(99)
                    AgeVitalThreshold(
                        id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: "$minAge-$maxAge",
                        label = (map["label"] as? String)?.takeIf { it.isNotBlank() } ?: "$minAge – $maxAge yrs",
                        minAge = minAge,
                        maxAge = maxAge,
                        hrLowDanger = map["hrLowDanger"].asInt(d.heartRateLowDanger),
                        hrLowWarning = map["hrLowWarning"].asInt(d.heartRateLowWarning),
                        hrWarning = map["hrWarning"].asInt(d.heartRateHigh),
                        hrDanger = map["hrDanger"].asInt(d.heartRateDanger),
                        tempLowDanger = map["tempLowDanger"].asDouble(d.temperatureLowDanger),
                        tempLowWarning = map["tempLowWarning"].asDouble(d.temperatureLowWarning),
                        tempWarning = map["tempWarning"].asDouble(d.temperatureHigh),
                        tempDanger = map["tempDanger"].asDouble(d.temperatureDanger),
                    )
                }?.takeIf { it.isNotEmpty() } ?: d.ageBrackets

                ThresholdConfig(
                    heartRateLowDanger = snap.get("heartRateLowDanger").asInt(d.heartRateLowDanger),
                    heartRateLowWarning = snap.get("heartRateLowWarning").asInt(d.heartRateLowWarning),
                    heartRateHigh = snap.get("heartRateHigh").asInt(d.heartRateHigh),
                    heartRateDanger = snap.get("heartRateDanger").asInt(d.heartRateDanger),
                    temperatureLowDanger = snap.get("temperatureLowDanger").asDouble(d.temperatureLowDanger),
                    temperatureLowWarning = snap.get("temperatureLowWarning").asDouble(d.temperatureLowWarning),
                    temperatureHigh = snap.get("temperatureHigh").asDouble(d.temperatureHigh),
                    temperatureDanger = snap.get("temperatureDanger").asDouble(d.temperatureDanger),
                    fallSensitivity = (snap.get("fallSensitivity") as? String)?.takeIf { it.isNotBlank() } ?: d.fallSensitivity,
                    fallFreefallG = snap.get("fallFreefallG").asDouble(d.fallFreefallG),
                    fallImpactG = snap.get("fallImpactG").asDouble(d.fallImpactG),
                    fallSevereImpactG = snap.get("fallSevereImpactG").asDouble(d.fallSevereImpactG),
                    inactivitySeconds = snap.get("inactivitySeconds").asInt(d.inactivitySeconds),
                    offlineTimeoutSeconds = snap.get("offlineTimeoutSeconds").asInt(d.offlineTimeoutSeconds),
                    responseTimeoutSeconds = snap.get("responseTimeoutSeconds").asInt(d.responseTimeoutSeconds),
                    lowBatteryPercent = snap.get("lowBatteryPercent").asInt(d.lowBatteryPercent),
                    ageBrackets = parsedBrackets,
                )
            }
            .catch { emit(ThresholdConfig.DEFAULT) }

    override suspend fun updateThresholds(config: ThresholdConfig): Outcome<Unit> = try {
        settings.document(FirebasePaths.THRESHOLDS_DOC).set(
            mapOf(
                "heartRateLowDanger" to config.heartRateLowDanger,
                "heartRateLowWarning" to config.heartRateLowWarning,
                "heartRateHigh" to config.heartRateHigh,
                "heartRateDanger" to config.heartRateDanger,
                "temperatureLowDanger" to config.temperatureLowDanger,
                "temperatureLowWarning" to config.temperatureLowWarning,
                "temperatureHigh" to config.temperatureHigh,
                "temperatureDanger" to config.temperatureDanger,
                "fallSensitivity" to config.fallSensitivity,
                "fallFreefallG" to config.fallFreefallG,
                "fallImpactG" to config.fallImpactG,
                "fallSevereImpactG" to config.fallSevereImpactG,
                "inactivitySeconds" to config.inactivitySeconds,
                "offlineTimeoutSeconds" to config.offlineTimeoutSeconds,
                "responseTimeoutSeconds" to config.responseTimeoutSeconds,
                "lowBatteryPercent" to config.lowBatteryPercent,
                "ageBrackets" to config.ageBrackets.map { b ->
                    mapOf(
                        "id" to b.id,
                        "label" to b.label,
                        "minAge" to b.minAge,
                        "maxAge" to b.maxAge,
                        "hrLowDanger" to b.hrLowDanger,
                        "hrLowWarning" to b.hrLowWarning,
                        "hrWarning" to b.hrWarning,
                        "hrDanger" to b.hrDanger,
                        "tempLowDanger" to b.tempLowDanger,
                        "tempLowWarning" to b.tempLowWarning,
                        "tempWarning" to b.tempWarning,
                        "tempDanger" to b.tempDanger,
                    )
                },
            ),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override fun observeMaintenanceMode(): Flow<Boolean> =
        settings.document(FirebasePaths.SYSTEM_DOC)
            .snapshots()
            .map { it.getBoolean("maintenanceMode") ?: false }
            .catch { emit(false) }

    override suspend fun setMaintenanceMode(enabled: Boolean): Outcome<Unit> = try {
        settings.document(FirebasePaths.SYSTEM_DOC)
            .set(mapOf("maintenanceMode" to enabled))
            .await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
