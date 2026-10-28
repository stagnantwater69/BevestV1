package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class LiveMapState(
    val loading: Boolean = true,
    val error: String? = null,
    val noSite: Boolean = false,
    val markers: List<WorkerLive> = emptyList(),
) {
    /** Workers whose vests are reporting; offline workers are left off the map. */
    val online get() = markers.filter { it.status != SafetyStatus.OFFLINE }
    val located get() = online.filter { it.reading?.latitude != null && it.reading.longitude != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveMapViewModel @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
    private val alertRepository: AlertRepository,
) : ViewModel() {

    private val siteId = MutableStateFlow<String?>(null)
    private var started = false

    fun start(site: String?) {
        if (started) return
        started = true
        siteId.value = site
    }

    val state: StateFlow<LiveMapState> = siteId.flatMapLatest { site ->
        if (site.isNullOrBlank()) return@flatMapLatest flowOf(LiveMapState(loading = false, noSite = true))
        workerRepository.observeWorkers(contractorId = null, siteId = site).flatMapLatest { outcome ->
            when (outcome) {
                is Outcome.Failure -> flowOf(LiveMapState(loading = false, error = outcome.error.message))
                is Outcome.Success -> {
                    val workers = outcome.data
                    combine(
                        monitoringRepository.observeReadings(workers.map { it.workerId }),
                        settingsRepository.observeThresholds(),
                        alertRepository.observeActiveAlerts(site),
                    ) { readings, thresholds, alertsOutcome ->
                        val engine = SafetyStatusEngine(thresholds)
                        val now = System.currentTimeMillis()
                        val alerts = (alertsOutcome as? Outcome.Success)?.data.orEmpty()
                        LiveMapState(
                            loading = false,
                            markers = workers.map { w ->
                                val r = readings[w.workerId]
                                WorkerLive(w, r, engine.evaluate(r, alerts, w.workerId, now), engine.isStale(r, now))
                            },
                        )
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveMapState())
}
