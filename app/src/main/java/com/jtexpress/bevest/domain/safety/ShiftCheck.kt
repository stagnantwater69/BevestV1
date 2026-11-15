package com.jtexpress.bevest.domain.safety

import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.Worker
import java.time.Instant
import java.time.ZoneId

/** Why a worker isn't ready to be monitored, worst first. */
enum class ShiftProblem {
    /** No vest paired: nothing will ever be detected for them. */
    NO_VEST,
    /** Paired, but the vest has never sent a reading. */
    NEVER_REPORTED,
    /** Paired, but the vest has gone quiet: off, out of range or flat. */
    NOT_REPORTING,
    /** Reporting, but unlikely to last the shift. */
    LOW_BATTERY,
}

data class ShiftCheckRow(
    val worker: Worker,
    val reading: SensorReading?,
    val problem: ShiftProblem?,
) {
    val ready: Boolean get() = problem == null
}

/**
 * The start-of-shift check: is every active worker on the site wearing a vest that is
 * reporting and charged? Catches the dead vest at 7am instead of at the first fall.
 */
object ShiftCheck {
    const val LOW_BATTERY_PERCENT = 30

    /** The shift's day on this phone's calendar, as yyyy-MM-dd. */
    fun dateKey(now: Long): String =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** Active workers only, problems first (worst first), then by name. */
    fun evaluate(
        workers: List<Worker>,
        readings: Map<String, SensorReading>,
        thresholds: ThresholdConfig,
        now: Long,
    ): List<ShiftCheckRow> {
        val engine = SafetyStatusEngine(thresholds)
        return workers
            .filter { it.active }
            .map { worker ->
                val reading = readings[worker.workerId]?.takeIf { it.timestamp > 0 }
                val problem = when {
                    worker.assignedVestId.isNullOrBlank() -> ShiftProblem.NO_VEST
                    reading == null -> ShiftProblem.NEVER_REPORTED
                    engine.isStale(reading, now) -> ShiftProblem.NOT_REPORTING
                    reading.batteryPercent?.let { it < LOW_BATTERY_PERCENT } == true -> ShiftProblem.LOW_BATTERY
                    else -> null
                }
                ShiftCheckRow(worker, reading, problem)
            }
            .sortedWith(
                compareBy<ShiftCheckRow>({ it.problem?.ordinal ?: Int.MAX_VALUE })
                    .thenBy { it.worker.fullName.ifBlank { it.worker.workerId } },
            )
    }
}
