package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Shift
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.ShiftRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.domain.safety.MonitorHealth
import com.jtexpress.bevest.domain.safety.SafetyWatcher
import com.jtexpress.bevest.domain.safety.ShiftCheck
import com.jtexpress.bevest.domain.safety.ShiftCheckRow
import com.jtexpress.bevest.ui.common.label
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.ServerClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShiftCheckState(
    val loading: Boolean = true,
    val error: String? = null,
    val rows: List<ShiftCheckRow> = emptyList(),
    val shift: Shift? = null,
    val saving: Boolean = false,
    val message: String? = null,
    val saveError: String? = null,
) {
    val readyCount: Int get() = rows.count { it.ready }
    val notReady: List<ShiftCheckRow> get() = rows.filterNot { it.ready }

    /** One line per problem, as stored with the shift for the contractor's records. */
    val issues: List<String>
        get() = notReady.map { row ->
            "${row.worker.fullName.ifBlank { row.worker.workerId }}: ${row.problem?.label()}"
        }
}

/** Today's shift for a site, following the date over midnight. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun ShiftRepository.observeToday(siteId: String, clock: ServerClock): Flow<Shift?> =
    clock.ticker(60_000)
        .map { ShiftCheck.dateKey(it) }
        .distinctUntilChanged()
        .flatMapLatest { observeShift(siteId, it) }

private data class Local(val saving: Boolean = false, val message: String? = null, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShiftCheckViewModel @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
    private val shiftRepository: ShiftRepository,
    private val clock: ServerClock,
    safetyWatcher: SafetyWatcher,
) : ViewModel() {

    /** Whether this phone is watching the site: the check covers the phone too. */
    val monitorHealth: StateFlow<MonitorHealth> = safetyWatcher.health

    private val user = MutableStateFlow<User?>(null)
    private val local = MutableStateFlow(Local())

    fun start(u: User) {
        user.value = u
    }

    private val checked: Flow<ShiftCheckState> = user.flatMapLatest { u ->
        val site = u?.siteId
        if (u == null || site.isNullOrBlank()) {
            flowOf(ShiftCheckState(loading = u == null, error = u?.let { "Your account isn't linked to a site yet." }))
        } else {
            workerRepository.observeWorkers(contractorId = null, siteId = site).flatMapLatest { outcome ->
                when (outcome) {
                    is Outcome.Failure -> flowOf(ShiftCheckState(loading = false, error = outcome.error.message))
                    is Outcome.Success -> {
                        val workers = outcome.data
                        combine(
                            monitoringRepository.observeReadings(workers.map { it.workerId }),
                            settingsRepository.observeThresholds(),
                            clock.ticker(),
                            shiftRepository.observeToday(site, clock),
                        ) { readings, thresholds, now, shift ->
                            ShiftCheckState(
                                loading = false,
                                rows = ShiftCheck.evaluate(workers, readings, thresholds, now),
                                shift = shift,
                            )
                        }
                    }
                }
            }
        }
    }

    val state: StateFlow<ShiftCheckState> = combine(checked, local) { s, l ->
        s.copy(saving = l.saving, message = l.message, saveError = l.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShiftCheckState())

    /** Starts today's shift, or records a re-run of the check if it has already started. */
    fun save() {
        val u = user.value ?: return
        val site = u.siteId ?: return
        val s = state.value
        if (s.loading || s.saving) return
        local.value = Local(saving = true)
        viewModelScope.launch {
            val result = shiftRepository.saveCheck(
                siteId = site,
                contractorId = u.contractorId,
                date = ShiftCheck.dateKey(clock.now()),
                byUserId = u.uid,
                byName = u.fullName.ifBlank { u.email },
                workerCount = s.rows.size,
                readyCount = s.readyCount,
                issues = s.issues,
            )
            local.value = when (result) {
                is Outcome.Success -> Local(message = if (s.shift == null) "Shift started." else "Check saved.")
                is Outcome.Failure -> Local(error = result.error.message)
            }
        }
    }
}
