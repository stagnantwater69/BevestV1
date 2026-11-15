package com.jtexpress.bevest.ui.sso

import com.jtexpress.bevest.data.firebase.retryDelayMs
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.ui.common.responseLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SsoDashboardStateTest {

    private fun live(id: String, status: SafetyStatus) =
        WorkerLive(Worker(workerId = id), reading = null, status = status, stale = status == SafetyStatus.OFFLINE)

    @Test
    fun `an emergency is the worst status even with someone offline`() {
        val state = SsoDashboardState(
            loading = false,
            workers = listOf(live("a", SafetyStatus.OFFLINE), live("b", SafetyStatus.EMERGENCY)),
        )
        assertEquals(SafetyStatus.EMERGENCY, state.worstStatus)
    }

    @Test
    fun `offline sorts below every live danger`() {
        val sorted = listOf(SafetyStatus.OFFLINE, SafetyStatus.WARNING, SafetyStatus.EMERGENCY, SafetyStatus.NORMAL, SafetyStatus.DANGER)
            .sortedByDescending { it.severity }
        assertEquals(
            listOf(SafetyStatus.EMERGENCY, SafetyStatus.DANGER, SafetyStatus.WARNING, SafetyStatus.OFFLINE, SafetyStatus.NORMAL),
            sorted,
        )
    }

    @Test
    fun `danger count includes emergencies`() {
        val state = SsoDashboardState(
            loading = false,
            workers = listOf(live("a", SafetyStatus.DANGER), live("b", SafetyStatus.EMERGENCY), live("c", SafetyStatus.NORMAL)),
        )
        assertEquals(2, state.dangerCount)
        assertEquals("Emergency on site", state.headline)
    }

    @Test
    fun `an open alert says who is responding, or that nobody is`() {
        val now = 1_000_000_000L
        val fall = Alert(alertId = "a", severity = AlertSeverity.DANGER, status = AlertStatus.ACTIVE, createdAt = now - 10_000)
        assertEquals("Nobody responding yet", fall.responseLine(now))
        assertEquals(
            "Nobody has responded · raised 2m ago",
            fall.copy(createdAt = now - 120_000, unansweredAt = now).responseLine(now),
        )
        assertEquals(
            "Juan Cruz is responding · just now",
            fall.copy(status = AlertStatus.ACKNOWLEDGED, acknowledgedByName = "Juan Cruz", acknowledgedAt = now - 5_000).responseLine(now),
        )
        assertNull(fall.copy(severity = AlertSeverity.WARNING).responseLine(now))
        assertNull(fall.copy(status = AlertStatus.RESOLVED).responseLine(now))
    }

    @Test
    fun `stream retries back off and cap at thirty seconds`() {
        assertEquals(1_000L, retryDelayMs(0))
        assertEquals(2_000L, retryDelayMs(1))
        assertEquals(16_000L, retryDelayMs(4))
        assertEquals(30_000L, retryDelayMs(5))
        assertEquals(30_000L, retryDelayMs(50))
    }
}
