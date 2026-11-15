package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.Shift
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.ShiftRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.domain.safety.MonitorHealth
import com.jtexpress.bevest.domain.safety.SafetyStatusEngine
import com.jtexpress.bevest.domain.safety.SafetyWatcher
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.ServerClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SsoDashboardState(
    val loading: Boolean = true,
    val error: String? = null,
    val noSite: Boolean = false,
    val workers: List<WorkerLive> = emptyList(),
    val activeAlerts: List<Alert> = emptyList(),
) {
    val emergencies get() = workers.filter { it.status == SafetyStatus.EMERGENCY }
    // An emergency is danger that went unanswered — it must not drop out of the count.
    val dangerCount get() = workers.count {
        it.status == SafetyStatus.DANGER || it.status == SafetyStatus.EMERGENCY
    }
    val warningCount get() = workers.count { it.status == SafetyStatus.WARNING }
    val offlineCount get() = workers.count { it.status == SafetyStatus.OFFLINE }
    val onSiteCount get() = workers.count { it.status != SafetyStatus.OFFLINE }
    /**
     * The alert behind a worker's danger — the one an officer needs to open to respond.
     * Unanswered first, then the most severe, then the newest.
     */
    fun dangerAlertFor(workerId: String): Alert? = activeAlerts
        .filter { it.workerId == workerId && it.severity != AlertSeverity.WARNING }
        .sortedWith(
            compareBy<Alert>({ it.status != AlertStatus.ACTIVE }, { -it.severity.ordinal }, { -it.createdAt }),
        )
        .firstOrNull()

    /** The emergency worth a banner: an open EMERGENCY alert whose worker is in trouble. */
    val topEmergencyAlert: Alert?
        get() {
            val inTrouble = workers
                .filter { it.status == SafetyStatus.EMERGENCY || it.status == SafetyStatus.DANGER }
                .map { it.worker.workerId }
                .toSet()
            return activeAlerts.firstOrNull {
                it.severity == AlertSeverity.EMERGENCY && it.workerId in inTrouble
            }
        }

    /**
     * The worst state anyone on site is in. Drives the dashboard's headline and the
     * color of its shield, so the panel's whole identity tracks the site's condition.
     */
    val worstStatus: SafetyStatus
        get() = workers.maxByOrNull { it.status.severity }?.status ?: SafetyStatus.OFFLINE

    /**
     * The verdict, in the words an officer would use.
     *
     * Deriving this here rather than in the composable keeps the wording testable and
     * stops two screens from ever disagreeing about what the same numbers mean.
     */
    val headline: String
        get() = when {
            workers.isEmpty() -> "No workers yet"
            emergencies.isNotEmpty() -> "Emergency on site"
            dangerCount > 0 -> "$dangerCount ${plural(dangerCount)} in danger"
            warningCount > 0 -> "$warningCount ${plural(warningCount)} to check"
            onSiteCount == 0 -> "Nobody reporting"
            else -> "All clear"
        }

    val supporting: String
        get() = when {
            workers.isEmpty() -> "Register a worker to start monitoring."
            emergencies.isNotEmpty() -> "Open the alert and respond now."
            dangerCount > 0 -> "Readings are at a dangerous level. Check on them."
            warningCount > 0 -> "Readings crossed a warning threshold."
            onSiteCount == 0 -> "No vest on this site is sending readings."
            else -> "$onSiteCount of ${workers.size} monitored and within safe limits."
        }

    private fun plural(n: Int) = if (n == 1) "worker" else "workers"
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SsoDashboardViewModel @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val alertRepository: AlertRepository,
    private val settingsRepository: SettingsRepository,
    private val shiftRepository: ShiftRepository,
    private val clock: ServerClock,
    safetyWatcher: SafetyWatcher,
) : ViewModel() {

    /** Whether this phone's safety engine can currently see the site. */
    val monitorHealth: StateFlow<MonitorHealth> = safetyWatcher.health

    private val siteId = MutableStateFlow<String?>(null)

    /** Follows the officer's site, so a reassignment takes effect without a restart. */
    fun start(site: String?) {
        siteId.value = site
    }

    /** Today's shift, or null until someone runs the start-of-shift check. */
    val shift: StateFlow<Shift?> = siteId.flatMapLatest { site ->
        if (site.isNullOrBlank()) flowOf(null) else shiftRepository.observeToday(site, clock)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<SsoDashboardState> = siteId.flatMapLatest { site ->
        if (site.isNullOrBlank()) {
            flowOf(SsoDashboardState(loading = false, noSite = true))
        } else {
            val workersFlow = workerRepository.observeWorkers(contractorId = null, siteId = site)
            val thresholdsFlow = settingsRepository.observeThresholds()
            val alertsFlow = alertRepository.observeActiveAlerts(site)

            workersFlow.flatMapLatest { workersOutcome ->
                when (workersOutcome) {
                    is Outcome.Failure -> flowOf(
                        SsoDashboardState(loading = false, error = workersOutcome.error.message),
                    )
                    is Outcome.Success -> {
                        val workers = workersOutcome.data
                        val ids = workers.map { it.workerId }
                        combine(
                            monitoringRepository.observeReadings(ids),
                            thresholdsFlow,
                            alertsFlow,
                            clock.ticker(),
                        ) { readings, thresholds, alertsOutcome, now ->
                            val engine = SafetyStatusEngine(thresholds)
                            val alerts = (alertsOutcome as? Outcome.Success)?.data.orEmpty()
                            val live = workers.map { w ->
                                val r = readings[w.workerId]
                                WorkerLive(
                                    worker = w,
                                    reading = r,
                                    status = engine.evaluate(r, alerts, w.workerId, now),
                                    stale = engine.isStale(r, now),
                                )
                            }.sortedByDescending { it.status.severity }
                            SsoDashboardState(
                                loading = false,
                                workers = live,
                                activeAlerts = alerts,
                            )
                        }
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SsoDashboardState())
}
