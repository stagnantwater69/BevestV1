package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.domain.safety.SafetyStatusEngine
import com.jtexpress.bevest.utils.Outcome
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
    val dangerCount get() = workers.count { it.status == SafetyStatus.DANGER }
    val warningCount get() = workers.count { it.status == SafetyStatus.WARNING }
    val onSiteCount get() = workers.count { it.status != SafetyStatus.OFFLINE }
    val topEmergencyAlert: Alert?
        get() = activeAlerts.firstOrNull { it.severity == AlertSeverity.EMERGENCY }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SsoDashboardViewModel @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val alertRepository: AlertRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val siteId = MutableStateFlow<String?>(null)
    private var started = false

    fun start(site: String?) {
        if (started) return
        started = true
        siteId.value = site
    }

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
                        ) { readings, thresholds, alertsOutcome ->
                            val engine = SafetyStatusEngine(thresholds)
                            val now = System.currentTimeMillis()
                            val live = workers.map { w ->
                                val r = readings[w.workerId]
                                WorkerLive(
                                    worker = w,
                                    reading = r,
                                    status = engine.evaluate(r, now),
                                    stale = engine.isStale(r, now),
                                )
                            }.sortedByDescending { it.status.ordinal }
                            SsoDashboardState(
                                loading = false,
                                workers = live,
                                activeAlerts = (alertsOutcome as? Outcome.Success)?.data.orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SsoDashboardState())
}
