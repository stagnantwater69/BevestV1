package com.jtexpress.bevest.ui.vest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.ServerClock
import com.jtexpress.bevest.utils.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class PairStep { IDENTIFY, SELECT_WORKER, CONNECTION_TEST, DONE }

data class ConnectionChecks(
    val heartRate: Boolean = false,
    val temperature: Boolean = false,
    val motion: Boolean = false,
    val gps: Boolean = false,
    val connection: Boolean = false,
) {
    val allPass get() = heartRate && temperature && motion && gps && connection
}

data class PairVestState(
    val step: PairStep = PairStep.IDENTIFY,
    val vestIdInput: String = "",
    val vestIdError: String? = null,
    val identifiedVestId: String? = null,
    val checking: Boolean = false,
    val unassignedWorkers: List<Worker> = emptyList(),
    val selectedWorkerId: String? = null,
    val testing: Boolean = false,
    val checks: ConnectionChecks = ConnectionChecks(),
    val submitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PairVestViewModel @Inject constructor(
    private val vestRepository: VestRepository,
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: ServerClock,
) : ViewModel() {

    private val _state = MutableStateFlow(PairVestState())
    val state: StateFlow<PairVestState> = _state.asStateFlow()

    private var siteId: String? = null
    private var pairedBy: String? = null
    fun bind(site: String?) { siteId = site }

    fun onVestIdInput(v: String) = _state.update { it.copy(vestIdInput = v, vestIdError = null, error = null) }

    fun onScanned(value: String) {
        _state.update { it.copy(vestIdInput = value.trim()) }
        identifyVest()
    }

    /** Step 1: validate the vest exists and is not already paired. */
    fun identifyVest() {
        val id = _state.value.vestIdInput.trim()
        val err = Validators.vestId(id)
        if (err != null) {
            _state.update { it.copy(vestIdError = err) }
            return
        }
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            when (val result = vestRepository.getVest(id)) {
                is Outcome.Failure -> _state.update { it.copy(checking = false, error = result.error.message) }
                is Outcome.Success -> {
                    val vest = result.data
                    if (!vest.assignedWorkerId.isNullOrBlank()) {
                        _state.update { it.copy(checking = false, error = "Vest $id is already paired with a worker.") }
                    } else if (vest.status == VestStatus.MAINTENANCE) {
                        _state.update { it.copy(checking = false, error = "Vest $id is marked for maintenance.") }
                    } else {
                        loadUnassignedWorkers(id)
                    }
                }
            }
        }
    }

    private fun loadUnassignedWorkers(vestId: String) {
        viewModelScope.launch {
            val outcome = workerRepository.observeWorkers(contractorId = null, siteId = siteId).first()
            val workers = (outcome as? Outcome.Success)?.data.orEmpty()
                .filter { it.active && it.assignedVestId.isNullOrBlank() }
            _state.update {
                it.copy(
                    checking = false,
                    identifiedVestId = vestId,
                    unassignedWorkers = workers,
                    step = PairStep.SELECT_WORKER,
                )
            }
        }
    }

    /**
     * Step 2 -> 3: pairs the vest straight away. The vest learns its worker from
     * `vests/{id}.assignedWorkerId` (it re-checks every 30 s), so it can only start
     * reporting for this worker — and the test only means something — once this is written.
     */
    fun selectWorker(workerId: String, assignedBy: String) {
        val vestId = _state.value.identifiedVestId ?: return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = vestRepository.assignVest(vestId, workerId, assignedBy)) {
                is Outcome.Success -> {
                    pairedBy = assignedBy
                    _state.update {
                        it.copy(submitting = false, selectedWorkerId = workerId, step = PairStep.CONNECTION_TEST)
                    }
                    runConnectionTest()
                }
                is Outcome.Failure -> _state.update { it.copy(submitting = false, error = result.error.message) }
            }
        }
    }

    /**
     * Step 3: wait for a reading from *this* vest, newer than the offline timeout, and
     * confirm each sensor is in it. A leftover reading from the worker's previous vest
     * doesn't count.
     */
    fun runConnectionTest() {
        val workerId = _state.value.selectedWorkerId ?: return
        val vestId = _state.value.identifiedVestId ?: return
        _state.update { it.copy(testing = true, checks = ConnectionChecks(), error = null) }
        viewModelScope.launch {
            val maxAgeMs = settingsRepository.observeThresholds().first().offlineTimeoutSeconds * 1000L
            val reading: SensorReading? = withTimeoutOrNull(TEST_TIMEOUT_MS) {
                monitoringRepository.observeReading(workerId).first { r ->
                    r != null && r.vestId == vestId && clock.now() - r.timestamp <= maxAgeMs
                }
            }
            if (reading == null) {
                _state.update {
                    it.copy(
                        testing = false,
                        error = "No data from vest $vestId yet. Make sure it is powered on and " +
                            "online — it picks up a new pairing within 30 seconds.",
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        testing = false,
                        checks = ConnectionChecks(
                            heartRate = reading.heartRate != null,
                            temperature = reading.temperature != null,
                            motion = reading.motionState.name != "UNKNOWN",
                            gps = reading.latitude != null && reading.longitude != null,
                            connection = true,
                        ),
                    )
                }
            }
        }
    }

    /** Every sensor reported: the vest goes into service. */
    fun confirmPairing() {
        val vestId = _state.value.identifiedVestId ?: return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = vestRepository.setStatus(vestId, VestStatus.ACTIVE)) {
                is Outcome.Success -> _state.update { it.copy(submitting = false, step = PairStep.DONE) }
                is Outcome.Failure -> _state.update { it.copy(submitting = false, error = result.error.message) }
            }
        }
    }

    fun back() {
        val s = _state.value
        when (s.step) {
            PairStep.SELECT_WORKER -> _state.update { it.copy(step = PairStep.IDENTIFY, identifiedVestId = null) }
            // Leaving the test undoes the pairing it started.
            PairStep.CONNECTION_TEST -> {
                val vestId = s.identifiedVestId ?: return
                viewModelScope.launch {
                    vestRepository.unassignVest(vestId, pairedBy.orEmpty())
                    val workers = (workerRepository.observeWorkers(contractorId = null, siteId = siteId).first() as? Outcome.Success)
                        ?.data.orEmpty().filter { it.active && it.assignedVestId.isNullOrBlank() }
                    _state.update {
                        it.copy(
                            step = PairStep.SELECT_WORKER,
                            selectedWorkerId = null,
                            testing = false,
                            unassignedWorkers = workers,
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    private companion object {
        /** The vest re-checks its pairing every 30 s, then reports every few seconds. */
        const val TEST_TIMEOUT_MS = 45_000L
    }
}
