package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.VitalLogEntry
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.domain.safety.SafetyStatusEngine
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkerDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val worker: Worker? = null,
    val reading: SensorReading? = null,
    val status: SafetyStatus = SafetyStatus.OFFLINE,
    val stale: Boolean = true,
    val actionMessage: String? = null,
    /** Recent heart-rate samples, oldest first — drawn as an ECG trace on the card. */
    val heartRateHistory: List<Int> = emptyList(),
    /** Per-minute Heart Rate & Body Temperature live reading logs, newest first. */
    val vitalLogs: List<VitalLogEntry> = emptyList(),
    /** Remaining seconds in the 15-second safety response countdown when waiting for worker. */
    val safetyCountdownSec: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
    private val alertRepository: AlertRepository,
) : ViewModel() {

    private val workerId: String = savedStateHandle.get<String>("workerId").orEmpty()
    private val actionMessage = MutableStateFlow<String?>(null)
    private val safetyCountdownSec = MutableStateFlow<Int?>(null)
    private var safetyTimerJob: Job? = null

    private val heartRateHistory = MutableStateFlow<List<Int>>(emptyList())

    private val metaFlow = combine(
        actionMessage,
        heartRateHistory,
        safetyCountdownSec,
    ) { msg, hrHistory, countdown ->
        Triple(msg, hrHistory, countdown)
    }

    val state: StateFlow<WorkerDetailState> =
        workerRepository.observeWorker(workerId).flatMapLatest { workerOutcome ->
            when (workerOutcome) {
                is Outcome.Failure -> flowOf(
                    WorkerDetailState(loading = false, error = workerOutcome.error.message),
                )
                is Outcome.Success -> combine(
                    monitoringRepository.observeReading(workerId).onEach { reading ->
                        reading?.heartRate?.let { hr ->
                            heartRateHistory.update { (it + hr).takeLast(HISTORY_SIZE) }
                        }
                        if (reading?.safetyResponse == SafetyResponseState.ACKNOWLEDGED &&
                            safetyCountdownSec.value != null
                        ) {
                            safetyTimerJob?.cancel()
                            safetyCountdownSec.value = null
                            actionMessage.value = null
                        }
                    },
                    settingsRepository.observeThresholds(),
                    alertRepository.observeActiveAlerts(workerOutcome.data.siteId),
                    monitoringRepository.observeVitalLogs(workerId),
                    metaFlow,
                ) { reading, thresholds, alertsOutcome, vitalLogs, (message, history, countdown) ->
                    val engine = SafetyStatusEngine(thresholds)
                    val now = System.currentTimeMillis()
                    WorkerDetailState(
                        loading = false,
                        worker = workerOutcome.data,
                        reading = reading,
                        status = engine.evaluate(
                            reading,
                            (alertsOutcome as? Outcome.Success)?.data.orEmpty(),
                            workerId,
                            now,
                        ),
                        stale = engine.isStale(reading, now),
                        actionMessage = message,
                        heartRateHistory = history,
                        vitalLogs = vitalLogs,
                        safetyCountdownSec = countdown,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkerDetailState())

    fun initiateSafetyResponse(byUserId: String) {
        safetyTimerJob?.cancel()
        safetyTimerJob = viewModelScope.launch {
            when (val r = monitoringRepository.initiateSafetyResponse(workerId, byUserId)) {
                is Outcome.Success -> {
                    actionMessage.value = "Buzzer triggered on vest for 15 seconds. Waiting for worker to press button."
                    for (remaining in SAFETY_TIMEOUT_SECONDS downTo 1) {
                        safetyCountdownSec.value = remaining
                        delay(1_000L)
                        if (state.value.reading?.safetyResponse == SafetyResponseState.ACKNOWLEDGED) {
                            safetyCountdownSec.value = null
                            actionMessage.value = null
                            return@launch
                        }
                    }
                    safetyCountdownSec.value = null
                    actionMessage.value = null
                    if (state.value.reading?.safetyResponse != SafetyResponseState.ACKNOWLEDGED) {
                        monitoringRepository.setSafetyResponse(workerId, SafetyResponseState.NO_RESPONSE)
                    }
                }
                is Outcome.Failure -> actionMessage.value = r.error.message
            }
        }
    }

    fun clearMessage() { actionMessage.value = null }

    private companion object {
        const val HISTORY_SIZE = 40
        const val SAFETY_TIMEOUT_SECONDS = 15
    }
}
