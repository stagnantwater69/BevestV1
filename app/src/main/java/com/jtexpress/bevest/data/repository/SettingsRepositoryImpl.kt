package com.jtexpress.bevest.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.firebase.valueEventsOrNull
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.safety.AgeVitalThreshold
import com.jtexpress.bevest.domain.safety.ThresholdConfig
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: FirebaseDatabase,
) : SettingsRepository {

    private val settings get() = firestore.collection(FirebasePaths.SETTINGS)

    private fun Any?.asInt(fallback: Int): Int = when (this) {
        is Number -> this.toInt()
        is String -> this.toIntOrNull() ?: this.toDoubleOrNull()?.toInt() ?: fallback
        else -> fallback
    }

    private fun Any?.asLong(fallback: Long): Long = when (this) {
        is Number -> this.toLong()
        is String -> this.toLongOrNull() ?: this.toDoubleOrNull()?.toLong() ?: fallback
        else -> fallback
    }

    private fun Any?.asDouble(fallback: Double): Double = when (this) {
        is Number -> this.toDouble()
        is String -> this.toDoubleOrNull() ?: fallback
        else -> fallback
    }

    private fun parseMap(data: Map<String, Any?>): ThresholdConfig {
        val d = ThresholdConfig.DEFAULT
        val rawBrackets = when (val raw = data["ageBrackets"]) {
            is List<*> -> raw
            is Map<*, *> -> raw.entries.sortedBy { it.key?.toString()?.toIntOrNull() ?: Int.MAX_VALUE }.map { it.value }
            else -> null
        }
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

        val rawOffline = data["offlineTimeoutSeconds"].asInt(d.offlineTimeoutSeconds)
        val effectiveOffline = if (rawOffline <= 0 || rawOffline >= 60) d.offlineTimeoutSeconds else rawOffline

        return ThresholdConfig(
            heartRateLowDanger = data["heartRateLowDanger"].asInt(d.heartRateLowDanger),
            heartRateLowWarning = data["heartRateLowWarning"].asInt(d.heartRateLowWarning),
            heartRateHigh = data["heartRateHigh"].asInt(d.heartRateHigh),
            heartRateDanger = data["heartRateDanger"].asInt(d.heartRateDanger),
            temperatureLowDanger = data["temperatureLowDanger"].asDouble(d.temperatureLowDanger),
            temperatureLowWarning = data["temperatureLowWarning"].asDouble(d.temperatureLowWarning),
            temperatureHigh = data["temperatureHigh"].asDouble(d.temperatureHigh),
            temperatureDanger = data["temperatureDanger"].asDouble(d.temperatureDanger),
            fallSensitivity = (data["fallSensitivity"] as? String)?.takeIf { it.isNotBlank() } ?: d.fallSensitivity,
            fallFreefallG = data["fallFreefallG"].asDouble(d.fallFreefallG),
            fallImpactG = data["fallImpactG"].asDouble(d.fallImpactG),
            fallSevereImpactG = data["fallSevereImpactG"].asDouble(d.fallSevereImpactG),
            inactivitySeconds = data["inactivitySeconds"].asInt(d.inactivitySeconds),
            offlineTimeoutSeconds = effectiveOffline,
            responseTimeoutSeconds = data["responseTimeoutSeconds"].asInt(d.responseTimeoutSeconds),
            lowBatteryPercent = data["lowBatteryPercent"].asInt(d.lowBatteryPercent),
            ageBrackets = parsedBrackets,
        )
    }

    private fun extractFirestoreMap(snap: DocumentSnapshot?): Pair<Long, Map<String, Any?>>? {
        if (snap == null || !snap.exists()) return null
        val data = snap.data ?: return null
        val updatedAtMs = data["updatedAtMs"].asLong(0L)
        return updatedAtMs to data
    }

    private fun extractRtdbMap(snap: DataSnapshot?): Pair<Long, Map<String, Any?>>? {
        if (snap == null || !snap.exists()) return null
        val raw = snap.value as? Map<*, *> ?: return null
        val data = raw.entries.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }.toMap()
        val updatedAtMs = data["updatedAtMs"].asLong(0L)
        return updatedAtMs to data
    }

    override fun observeThresholds(): Flow<ThresholdConfig> =
        combine(
            settings.document(FirebasePaths.THRESHOLDS_DOC)
                .snapshots()
                .map<DocumentSnapshot, DocumentSnapshot?> { it }
                .onStart { emit(null) }
                .catch { emit(null) },
            database.getReference("${FirebasePaths.SETTINGS}/${FirebasePaths.THRESHOLDS_DOC}")
                .valueEventsOrNull(),
        ) { fsSnap, rtSnap ->
            val fsEntry = extractFirestoreMap(fsSnap)
            val rtEntry = extractRtdbMap(rtSnap)
            val chosen = when {
                fsEntry != null && rtEntry != null -> if (rtEntry.first >= fsEntry.first) rtEntry.second else fsEntry.second
                rtEntry != null -> rtEntry.second
                fsEntry != null -> fsEntry.second
                else -> null
            }
            if (chosen != null) parseMap(chosen) else ThresholdConfig.DEFAULT
        }.catch { emit(ThresholdConfig.DEFAULT) }

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
