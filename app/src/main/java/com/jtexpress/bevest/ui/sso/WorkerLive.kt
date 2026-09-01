package com.jtexpress.bevest.ui.sso

import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.Worker

/** A worker paired with their latest live reading and the derived safety state. */
data class WorkerLive(
    val worker: Worker,
    val reading: SensorReading?,
    val status: SafetyStatus,
    val stale: Boolean,
) {
    val lastUpdate: Long? get() = reading?.timestamp?.takeIf { it > 0 }
}
