package com.jtexpress.bevest.data.mapper

import com.google.firebase.database.DataSnapshot
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.safety.ThresholdConfig

private fun DataSnapshot.int(key: String): Int? =
    child(key).getValue(Long::class.java)?.toInt() ?: child(key).getValue(Double::class.java)?.toInt()

private fun DataSnapshot.dbl(key: String): Double? =
    child(key).getValue(Double::class.java) ?: child(key).getValue(Long::class.java)?.toDouble()

private fun DataSnapshot.str(key: String): String? = child(key).getValue(String::class.java)
private fun DataSnapshot.bool(key: String): Boolean = child(key).getValue(Boolean::class.java) ?: false
private fun DataSnapshot.long(key: String): Long =
    child(key).getValue(Long::class.java) ?: child(key).getValue(Double::class.java)?.toLong() ?: 0L

/** Maps the agreed IoT payload (plan section 29). Confirm field names with the firmware team. */
fun DataSnapshot.toSensorReading(workerId: String): SensorReading? {
    if (!exists()) return null
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
        timestamp = long("timestamp"),
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
