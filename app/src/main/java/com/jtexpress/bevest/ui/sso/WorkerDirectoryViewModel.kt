package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.SafetyStatus
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
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class WorkerFilter { ALL, ACTIVE, WARNING, DANGER, OFFLINE, UNASSIGNED }

data class WorkerDirectoryState(
    val loading: Boolean = true,
    val error: String? = null,
    val noSite: Boolean = false,
    val all: List<WorkerLive> = emptyList(),
    val query: String = "",
    val filter: WorkerFilter = WorkerFilter.ALL,
) {
    private fun matchesFilter(live: WorkerLive, f: WorkerFilter): Boolean = when (f) {
        WorkerFilter.ALL -> true
        WorkerFilter.ACTIVE -> live.status != SafetyStatus.OFFLINE
        WorkerFilter.WARNING -> live.status == SafetyStatus.WARNING
        WorkerFilter.DANGER -> live.status == SafetyStatus.DANGER || live.status == SafetyStatus.EMERGENCY
        WorkerFilter.OFFLINE -> live.status == SafetyStatus.OFFLINE
        WorkerFilter.UNASSIGNED -> live.worker.assignedVestId.isNullOrBlank()
    }

    /** Count shown on each filter chip so the roster's shape is readable at a glance. */
    fun countFor(f: WorkerFilter): Int = all.count { matchesFilter(it, f) }

    val visible: List<WorkerLive>
        get() = all
            .filter { matchesFilter(it, filter) }
            .filter { live ->
                val q = query.trim().lowercase()
                q.isEmpty() ||
                    live.worker.fullName.lowercase().contains(q) ||
                    live.worker.workerId.lowercase().contains(q)
            }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkerDirectoryViewModel @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val scope = MutableStateFlow<Pair<String?, String?>?>(null) // contractorId, siteId
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(WorkerFilter.ALL)
    private var started = false

    fun start(contractorId: String?, siteId: String?) {
        if (started) return
        started = true
        scope.value = contractorId to siteId
    }

    fun onQuery(value: String) { query.value = value }
    fun onFilter(value: WorkerFilter) { filter.value = value }

    val state: StateFlow<WorkerDirectoryState> = scope.flatMapLatest { s ->
        if (s == null) return@flatMapLatest flowOf(WorkerDirectoryState())
        val (contractorId, siteId) = s
        if (contractorId == null && siteId == null) {
            return@flatMapLatest flowOf(WorkerDirectoryState(loading = false, noSite = true))
        }
        workerRepository.observeWorkers(contractorId, siteId).flatMapLatest { outcome ->
            when (outcome) {
                is Outcome.Failure -> flowOf(WorkerDirectoryState(loading = false, error = outcome.error.message))
                is Outcome.Success -> {
                    val workers = outcome.data
                    combine(
                        monitoringRepository.observeReadings(workers.map { it.workerId }),
                        settingsRepository.observeThresholds(),
                        query,
                        filter,
                    ) { readings, thresholds, q, f ->
                        val engine = SafetyStatusEngine(thresholds)
                        val now = System.currentTimeMillis()
                        val live = workers.map { w ->
                            val r = readings[w.workerId]
                            WorkerLive(w, r, engine.evaluate(r, now), engine.isStale(r, now))
                        }.sortedBy { it.worker.fullName }
                        WorkerDirectoryState(loading = false, all = live, query = q, filter = f)
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkerDirectoryState())
}
