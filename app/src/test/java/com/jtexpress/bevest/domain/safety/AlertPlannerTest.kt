package com.jtexpress.bevest.domain.safety

import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.Worker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPlannerTest {

    private val now = 1_000_000_000L
    private val config = ThresholdConfig()
    private val worker = Worker(workerId = "WRK-001", siteId = "SITE-1", contractorId = "C1", assignedVestId = "VEST-001")
    private val online = 10 * 60_000L

    private fun reading(
        hr: Int? = 80,
        temp: Double? = 36.8,
        motion: MotionState = MotionState.MOVING,
        fall: Boolean = false,
        ageSeconds: Long = 0,
        safetyResponse: SafetyResponseState = SafetyResponseState.NONE,
        simulated: Boolean = false,
    ) = SensorReading(
        workerId = worker.workerId,
        vestId = "VEST-001",
        heartRate = hr,
        temperature = temp,
        motionState = motion,
        fallDetected = fall,
        safetyResponse = safetyResponse,
        timestamp = now - ageSeconds * 1000,
        simulated = simulated,
    )

    private fun alert(
        type: AlertType,
        severity: AlertSeverity = AlertSeverity.DANGER,
        status: AlertStatus = AlertStatus.ACTIVE,
        ageSeconds: Long = 0,
        autoResolve: Boolean = false,
        unansweredAt: Long? = null,
    ) = Alert(
        alertId = "A-${type.name}",
        workerId = worker.workerId,
        type = type,
        severity = severity,
        status = status,
        createdAt = now - ageSeconds * 1000,
        siteId = "SITE-1",
        autoResolve = autoResolve,
        unansweredAt = unansweredAt,
    )

    private fun plan(
        planner: AlertPlanner = AlertPlanner(),
        r: SensorReading? = reading(),
        alerts: List<Alert> = emptyList(),
        at: Long = now,
        connectedForMs: Long = online,
    ) = planner.plan(
        workers = listOf(worker),
        readings = r?.let { mapOf(worker.workerId to it) }.orEmpty(),
        alerts = alerts,
        thresholds = config,
        now = at,
        connectedForMs = connectedForMs,
    )

    private inline fun <reified T : SafetyAction> List<SafetyAction>.only(): T {
        assertEquals("expected exactly one action, got $this", 1, size)
        return single() as T
    }

    @Test
    fun `healthy worker needs nothing`() {
        assertTrue(plan().isEmpty())
    }

    @Test
    fun `fall opens a danger alert`() {
        val open = plan(r = reading(fall = true)).only<SafetyAction.Open>()
        assertEquals(AlertType.FALL_DETECTED, open.type)
        assertEquals(AlertSeverity.DANGER, open.severity)
    }

    @Test
    fun `fall with an alert already open opens nothing`() {
        assertTrue(plan(r = reading(fall = true), alerts = listOf(alert(AlertType.FALL_DETECTED))).isEmpty())
    }

    @Test
    fun `unanswered fall escalates after the response timeout even once the flag clears`() {
        val fall = alert(AlertType.FALL_DETECTED, ageSeconds = config.responseTimeoutSeconds + 1L)
        val escalate = plan(alerts = listOf(fall)).only<SafetyAction.Escalate>()
        assertEquals(fall.alertId, escalate.alert.alertId)
    }

    @Test
    fun `fall within the response timeout does not escalate yet`() {
        val fall = alert(AlertType.FALL_DETECTED, ageSeconds = config.responseTimeoutSeconds - 1L)
        assertTrue(plan(alerts = listOf(fall)).isEmpty())
    }

    @Test
    fun `acknowledged fall never escalates`() {
        val fall = alert(AlertType.FALL_DETECTED, status = AlertStatus.ACKNOWLEDGED, ageSeconds = 600)
        assertTrue(plan(alerts = listOf(fall)).isEmpty())
    }

    @Test
    fun `wearer acknowledging on the vest stops escalation`() {
        val fall = alert(AlertType.FALL_DETECTED, ageSeconds = 600, unansweredAt = now)
        val r = reading(safetyResponse = SafetyResponseState.ACKNOWLEDGED)
        assertTrue(plan(r = r, alerts = listOf(fall)).isEmpty())
    }

    @Test
    fun `SOS opens an emergency with its incident at once`() {
        val open = plan(r = reading(safetyResponse = SafetyResponseState.EMERGENCY_REQUESTED)).only<SafetyAction.Open>()
        assertEquals(AlertType.EMERGENCY_REQUEST, open.type)
        assertEquals(AlertSeverity.EMERGENCY, open.severity)
        assertTrue(open.withIncident)
    }

    @Test
    fun `SOS with its alert already open opens nothing`() {
        val r = reading(safetyResponse = SafetyResponseState.EMERGENCY_REQUESTED)
        val sos = alert(AlertType.EMERGENCY_REQUEST, AlertSeverity.EMERGENCY)
        assertTrue(plan(r = r, alerts = listOf(sos)).isEmpty())
    }

    @Test
    fun `stale SOS from an offline vest is not a new emergency`() {
        val r = reading(safetyResponse = SafetyResponseState.EMERGENCY_REQUESTED, ageSeconds = 3600)
        assertTrue(plan(r = r).none { it is SafetyAction.Open && it.type == AlertType.EMERGENCY_REQUEST })
    }

    @Test
    fun `paired vest that stops reporting raises vest offline`() {
        val open = plan(r = reading(ageSeconds = config.offlineTimeoutSeconds + 10L)).only<SafetyAction.Open>()
        assertEquals(AlertType.VEST_OFFLINE, open.type)
        assertEquals(AlertSeverity.WARNING, open.severity)
        assertTrue(open.autoResolve)
    }

    @Test
    fun `no vest offline alert while this phone has only just reconnected`() {
        val r = reading(ageSeconds = config.offlineTimeoutSeconds + 10L)
        assertTrue(plan(r = r, connectedForMs = 5_000).isEmpty())
    }

    @Test
    fun `unpaired worker going quiet is not a vest offline`() {
        val planner = AlertPlanner()
        val actions = planner.plan(
            workers = listOf(worker.copy(assignedVestId = null)),
            readings = mapOf(worker.workerId to reading(ageSeconds = 3600)),
            alerts = emptyList(),
            thresholds = config,
            now = now,
            connectedForMs = online,
        )
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `vest reporting again closes its offline alert`() {
        val offline = alert(AlertType.VEST_OFFLINE, AlertSeverity.WARNING, autoResolve = true)
        val resolve = plan(alerts = listOf(offline)).only<SafetyAction.AutoResolve>()
        assertEquals(offline.alertId, resolve.alert.alertId)
    }

    @Test
    fun `high heart rate only alerts once it has lasted the warning duration`() {
        val planner = AlertPlanner()
        assertTrue(plan(planner, r = reading(hr = 140)).isEmpty())
        val later = now + config.warningDurationSeconds * 1000L
        val open = plan(planner, r = reading(hr = 140).copy(timestamp = later), at = later).only<SafetyAction.Open>()
        assertEquals(AlertType.HEART_RATE_WARNING, open.type)
    }

    @Test
    fun `a dip back to normal restarts the warning clock`() {
        val planner = AlertPlanner()
        plan(planner, r = reading(hr = 140))
        val mid = now + 60_000
        plan(planner, r = reading(hr = 80).copy(timestamp = mid), at = mid)
        val later = now + config.warningDurationSeconds * 1000L
        assertTrue(plan(planner, r = reading(hr = 140).copy(timestamp = later), at = later).isEmpty())
    }

    @Test
    fun `cleared condition closes its auto-resolving warning`() {
        val hot = alert(AlertType.TEMPERATURE_WARNING, AlertSeverity.WARNING, autoResolve = true)
        val resolve = plan(alerts = listOf(hot)).only<SafetyAction.AutoResolve>()
        assertEquals(hot.alertId, resolve.alert.alertId)
    }

    @Test
    fun `an officer-raised warning is never auto-closed`() {
        val manual = alert(AlertType.TEMPERATURE_WARNING, AlertSeverity.WARNING, autoResolve = false)
        assertTrue(plan(alerts = listOf(manual)).isEmpty())
    }

    @Test
    fun `simulated readings are left to the simulator`() {
        assertTrue(plan(r = reading(fall = true, simulated = true)).isEmpty())
    }

    @Test
    fun `a fall no officer has taken is flagged unanswered, even with the vest quiet`() {
        val fall = alert(AlertType.FALL_DETECTED, AlertSeverity.EMERGENCY, ageSeconds = config.unansweredTimeoutSeconds + 1L)
        val flagged = plan(r = null, alerts = listOf(fall)).only<SafetyAction.Unanswered>()
        assertEquals(fall.alertId, flagged.alert.alertId)
    }

    @Test
    fun `unanswered is flagged only once, and never once someone responds`() {
        val age = config.unansweredTimeoutSeconds + 30L
        val flagged = alert(AlertType.EMERGENCY_REQUEST, AlertSeverity.EMERGENCY, ageSeconds = age, unansweredAt = now)
        val taken = alert(AlertType.EMERGENCY_REQUEST, AlertSeverity.EMERGENCY, AlertStatus.ACKNOWLEDGED, ageSeconds = age)
        assertTrue(plan(r = null, alerts = listOf(flagged)).isEmpty())
        assertTrue(plan(r = null, alerts = listOf(taken)).isEmpty())
    }

    @Test
    fun `a warning nobody acknowledged is not chased`() {
        val hot = alert(AlertType.TEMPERATURE_WARNING, AlertSeverity.WARNING, ageSeconds = 3600)
        assertTrue(plan(r = null, alerts = listOf(hot)).isEmpty())
    }

    @Test
    fun `inactive worker is not monitored`() {
        val planner = AlertPlanner()
        val actions = planner.plan(
            workers = listOf(worker.copy(active = false)),
            readings = mapOf(worker.workerId to reading(fall = true)),
            alerts = emptyList(),
            thresholds = config,
            now = now,
            connectedForMs = online,
        )
        assertTrue(actions.isEmpty())
    }
}
