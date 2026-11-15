package com.jtexpress.bevest.domain.safety

import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.Worker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftCheckTest {

    private val now = 1_000_000_000L
    private val config = ThresholdConfig()

    private fun worker(id: String, vest: String? = "V-$id", active: Boolean = true) =
        Worker(workerId = id, firstName = id, assignedVestId = vest, active = active)

    private fun reading(id: String, ageSeconds: Long = 0, battery: Int? = 90) =
        SensorReading(workerId = id, vestId = "V-$id", timestamp = now - ageSeconds * 1000, batteryPercent = battery)

    @Test
    fun `each problem is told apart, worst first`() {
        val rows = ShiftCheck.evaluate(
            workers = listOf(worker("ok"), worker("flat"), worker("quiet"), worker("never"), worker("bare", vest = null)),
            readings = mapOf(
                "ok" to reading("ok"),
                "flat" to reading("flat", battery = 12),
                "quiet" to reading("quiet", ageSeconds = config.offlineTimeoutSeconds + 5L),
            ),
            thresholds = config,
            now = now,
        )
        assertEquals(
            listOf(
                "bare" to ShiftProblem.NO_VEST,
                "never" to ShiftProblem.NEVER_REPORTED,
                "quiet" to ShiftProblem.NOT_REPORTING,
                "flat" to ShiftProblem.LOW_BATTERY,
                "ok" to null,
            ),
            rows.map { it.worker.workerId to it.problem },
        )
    }

    @Test
    fun `inactive workers aren't part of the shift`() {
        val rows = ShiftCheck.evaluate(listOf(worker("gone", active = false)), emptyMap(), config, now)
        assertEquals(0, rows.size)
    }

    @Test
    fun `an unknown battery level isn't a problem`() {
        val row = ShiftCheck.evaluate(listOf(worker("a")), mapOf("a" to reading("a", battery = null)), config, now).single()
        assertNull(row.problem)
    }
}
