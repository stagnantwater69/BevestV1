package com.jtexpress.bevest.domain.safety

import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.SensorReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyStatusEngineTest {

    private val now = 1_000_000_000L
    private val config = ThresholdConfig()
    private val engine = SafetyStatusEngine(config)

    private fun reading(
        hr: Int? = 80,
        temp: Double? = 36.8,
        motion: MotionState = MotionState.MOVING,
        fall: Boolean = false,
        ageSeconds: Long = 0,
        safetyResponse: SafetyResponseState = SafetyResponseState.NONE,
    ) = SensorReading(
        heartRate = hr,
        temperature = temp,
        motionState = motion,
        fallDetected = fall,
        safetyResponse = safetyResponse,
        timestamp = now - ageSeconds * 1000,
    )

    @Test
    fun `null reading is offline`() {
        assertEquals(SafetyStatus.OFFLINE, engine.evaluate(null, now))
    }

    @Test
    fun `reading older than offline timeout is offline`() {
        val r = reading(ageSeconds = config.offlineTimeoutSeconds + 5L)
        assertEquals(SafetyStatus.OFFLINE, engine.evaluate(r, now))
    }

    @Test
    fun `normal vitals are normal`() {
        assertEquals(SafetyStatus.NORMAL, engine.evaluate(reading(), now))
    }

    @Test
    fun `high heart rate is warning`() {
        assertEquals(SafetyStatus.WARNING, engine.evaluate(reading(hr = 130), now))
    }

    @Test
    fun `high temperature is warning`() {
        assertEquals(SafetyStatus.WARNING, engine.evaluate(reading(temp = 39.1), now))
    }

    @Test
    fun `fall detection is danger even with normal vitals`() {
        assertEquals(SafetyStatus.DANGER, engine.evaluate(reading(fall = true), now))
    }

    @Test
    fun `manual emergency request outranks normal vitals`() {
        val r = reading(safetyResponse = SafetyResponseState.EMERGENCY_REQUESTED)
        assertEquals(SafetyStatus.EMERGENCY, engine.evaluate(r, now))
    }

    @Test
    fun `unanswered escalation is emergency even over a fall`() {
        val r = reading(fall = true, safetyResponse = SafetyResponseState.NO_RESPONSE)
        assertEquals(SafetyStatus.EMERGENCY, engine.evaluate(r, now))
    }

    @Test
    fun `stale emergency reading is still offline`() {
        val r = reading(
            ageSeconds = config.offlineTimeoutSeconds + 5L,
            safetyResponse = SafetyResponseState.EMERGENCY_REQUESTED,
        )
        assertEquals(SafetyStatus.OFFLINE, engine.evaluate(r, now))
    }

    @Test
    fun `in-progress response is not an emergency`() {
        val r = reading(safetyResponse = SafetyResponseState.WAITING)
        assertEquals(SafetyStatus.NORMAL, engine.evaluate(r, now))
    }

    @Test
    fun `stale detection matches offline timeout`() {
        assertTrue(engine.isStale(reading(ageSeconds = config.offlineTimeoutSeconds + 1L), now))
    }

    private fun alert(
        severity: AlertSeverity,
        status: AlertStatus = AlertStatus.ACTIVE,
        workerId: String = "w1",
    ) = Alert(workerId = workerId, severity = severity, status = status)

    @Test
    fun `open danger alert holds danger after the fall reading clears`() {
        val alerts = listOf(alert(AlertSeverity.DANGER))
        assertEquals(SafetyStatus.DANGER, engine.evaluate(reading(), alerts, "w1", now))
    }

    @Test
    fun `acknowledged alert moves the worker out of danger to warning`() {
        val alerts = listOf(alert(AlertSeverity.DANGER, AlertStatus.ACKNOWLEDGED))
        assertEquals(SafetyStatus.WARNING, engine.evaluate(reading(), alerts, "w1", now))
    }

    @Test
    fun `acknowledged alert also covers a fall still in the reading`() {
        val r = reading(fall = true, motion = MotionState.FALL_DETECTED)
        val alerts = listOf(alert(AlertSeverity.EMERGENCY, AlertStatus.ACKNOWLEDGED))
        assertEquals(SafetyStatus.WARNING, engine.evaluate(r, alerts, "w1", now))
    }

    @Test
    fun `a fresh SOS after acknowledgement is still an emergency`() {
        val r = reading(safetyResponse = SafetyResponseState.EMERGENCY_REQUESTED)
        val alerts = listOf(alert(AlertSeverity.DANGER, AlertStatus.ACKNOWLEDGED))
        assertEquals(SafetyStatus.EMERGENCY, engine.evaluate(r, alerts, "w1", now))
    }

    @Test
    fun `an unanswered alert outranks an acknowledged one`() {
        val alerts = listOf(
            alert(AlertSeverity.DANGER, AlertStatus.ACKNOWLEDGED),
            alert(AlertSeverity.EMERGENCY, AlertStatus.ACTIVE),
        )
        assertEquals(SafetyStatus.EMERGENCY, engine.evaluate(reading(), alerts, "w1", now))
    }

    @Test
    fun `resolved alert releases the hold`() {
        val alerts = listOf(alert(AlertSeverity.DANGER, AlertStatus.RESOLVED))
        assertEquals(SafetyStatus.NORMAL, engine.evaluate(reading(), alerts, "w1", now))
    }

    @Test
    fun `another worker's alert does not hold this worker`() {
        val alerts = listOf(alert(AlertSeverity.EMERGENCY, workerId = "w2"))
        assertEquals(SafetyStatus.NORMAL, engine.evaluate(reading(), alerts, "w1", now))
    }

    @Test
    fun `live emergency outranks a held danger`() {
        val r = reading(safetyResponse = SafetyResponseState.EMERGENCY_REQUESTED)
        val alerts = listOf(alert(AlertSeverity.DANGER))
        assertEquals(SafetyStatus.EMERGENCY, engine.evaluate(r, alerts, "w1", now))
    }
}
