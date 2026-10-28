package com.jtexpress.bevest.domain.safety

import android.content.Context
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.notifications.NotificationChannels
import com.jtexpress.bevest.notifications.NotificationHelper
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Danger alerts for a contractor, run on the contractor's phone.
 *
 * Contractors are accountable but usually off site, so they get a normal notification
 * when a fall or emergency opens on any of their sites — never the siren, which is only
 * for the officers on site who can reach the worker.
 *
 * Like [SafetyWatcher], it only runs while the app is open (free Firebase plan, so the
 * cloud functions that would push this can't be deployed).
 */
@Singleton
class ContractorAlertWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alertRepository: AlertRepository,
    private val workerRepository: WorkerRepository,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var job: Job? = null
    private var watching: String? = null

    /** Alerts already notified about, keyed by id, with the severity they had then. */
    private val notified = mutableMapOf<String, AlertSeverity>()

    fun start(contractorId: String?) {
        if (contractorId.isNullOrBlank() || contractorId == watching) return
        stop()
        watching = contractorId
        job = watch(contractorId)
    }

    fun stop() {
        job?.cancel()
        job = null
        watching = null
        notified.clear()
    }

    private fun watch(contractorId: String) = scope.launch {
        var first = true
        combine(
            alertRepository.observeContractorActiveAlerts(contractorId),
            workerRepository.observeWorkers(contractorId = contractorId, siteId = null),
        ) { alerts, workers -> alerts to workers }
            .collect { (alertsOutcome, workersOutcome) ->
                val alerts = (alertsOutcome as? Outcome.Success)?.data ?: return@collect
                val names = (workersOutcome as? Outcome.Success)?.data.orEmpty()
                    .associate { it.workerId to it.fullName.ifBlank { "Worker ${it.workerId}" } }

                val danger = alerts.filter { it.severity != AlertSeverity.WARNING }
                for (alert in danger) {
                    // Opening the app shouldn't replay alerts that were already open.
                    val seen = notified[alert.alertId]
                    notified[alert.alertId] = alert.severity
                    if (first) continue
                    if (seen != null && alert.severity.ordinal <= seen.ordinal) continue
                    notify(alert, names[alert.workerId] ?: "Worker ${alert.workerId}")
                }

                // Resolved alerts drop out of the open list; clear their notifications.
                val open = danger.mapTo(HashSet()) { it.alertId }
                (notified.keys - open).forEach { id ->
                    NotificationHelper.cancel(context, id)
                    notified.remove(id)
                }
                first = false
            }
    }

    private fun notify(alert: Alert, workerName: String) {
        val (title, body) = when {
            alert.severity == AlertSeverity.EMERGENCY -> "⚠ Emergency on your site" to "$workerName: ${alert.message}"
            alert.type == AlertType.FALL_DETECTED -> "Fall detected on your site" to "$workerName may have fallen."
            else -> "Safety alert on your site" to "$workerName: ${alert.message}"
        }
        NotificationHelper.show(
            context = context,
            title = title,
            body = body,
            channelId = NotificationChannels.CRITICAL,
            type = "ALERT",
            targetId = alert.alertId,
            workerId = alert.workerId,
        )
    }
}
