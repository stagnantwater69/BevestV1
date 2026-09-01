package com.jtexpress.bevest.domain.safety

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
}
