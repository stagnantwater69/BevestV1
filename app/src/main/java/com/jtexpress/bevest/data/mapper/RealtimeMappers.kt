package com.jtexpress.bevest.data.mapper

import com.google.firebase.database.DataSnapshot
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.safety.ThresholdConfig

private fun DataSnapshot.int(key: String): Int? =
    when (val v = child(key).value) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt()
        else -> null
    }

private fun DataSnapshot.dbl(key: String): Double? =
    when (val v = child(key).value) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

private fun DataSnapshot.str(key: String): String? =
    child(key).value?.toString()?.takeIf { it.isNotBlank() && it != "null" }

private fun DataSnapshot.bool(key: String): Boolean =
    when (val v = child(key).value) {
        is Boolean -> v
        is Number -> v.toLong() > 0L
        is String -> v.equals("true", ignoreCase = true) || v == "1"
        else -> false
    }

private fun DataSnapshot.long(key: String): Long =
    when (val v = child(key).value) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong() ?: 0L
        else -> 0L
    }

/** Maps the agreed IoT payload (plan section 29). Confirm field names with the firmware team. */
fun DataSnapshot.toSensorReading(workerId: String): SensorReading? {
    if (!exists()) return null
    val effectiveTimestamp = long("timestampServer").takeIf { it > 1_000_000_000_000L }
        ?: long("timestamp").takeIf { it > 1_000_000_000_000L }
        ?: long("lastSeenServer").takeIf { it > 1_000_000_000_000L }
        ?: 0L
    return SensorReading(
        workerId = workerId,
        vestId = str("vestId").orEmpty(),
        heartRate = int("heartRate"),
        temperature = dbl("temperature"),
        motionState = MotionState.fromRaw(str("motionState")),
        fallDetected = bool("fallDetected"),
        latitude = dbl("latitude"),
        longitude = dbl("longitude"),
        batteryPercent = int("battery"),
        safetyResponse = SafetyResponseState.fromRaw(str("safetyResponse")),
        timestamp = effectiveTimestamp,
    )
}

fun DataSnapshot.toThresholdConfig(): ThresholdConfig {
    val d = ThresholdConfig.DEFAULT
    return ThresholdConfig(
        heartRateHigh = int("heartRateHigh") ?: d.heartRateHigh,
        temperatureHigh = dbl("temperatureHigh") ?: d.temperatureHigh,
        warningDurationSeconds = int("warningDurationSeconds") ?: d.warningDurationSeconds,
        responseTimeoutSeconds = int("responseTimeoutSeconds") ?: d.responseTimeoutSeconds,
        offlineTimeoutSeconds = int("offlineTimeoutSeconds") ?: d.offlineTimeoutSeconds,
    )
}
