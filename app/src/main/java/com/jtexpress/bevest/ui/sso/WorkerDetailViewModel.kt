package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.Worker
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
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val workerId: String = savedStateHandle.get<String>("workerId").orEmpty()
    private val actionMessage = MutableStateFlow<String?>(null)

    /**
     * A rolling window of heart-rate samples, collected as readings arrive.
     *
     * The repository only ever exposes the latest reading, so there is no history to
     * query — but the trend is what actually tells an officer whether a worker is
     * recovering or getting worse, and a single number cannot. Holding the last
     * [HISTORY_SIZE] samples for the life of this screen is enough to draw that, and
     * costs nothing: the buffer dies with the ViewModel.
     */
    private val heartRateHistory = MutableStateFlow<List<Int>>(emptyList())

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
                    },
                    settingsRepository.observeThresholds(),
                    actionMessage,
                    heartRateHistory,
                ) { reading, thresholds, message, history ->
                    val engine = SafetyStatusEngine(thresholds)
                    val now = System.currentTimeMillis()
                    WorkerDetailState(
                        loading = false,
                        worker = workerOutcome.data,
                        reading = reading,
                        status = engine.evaluate(reading, now),
                        stale = engine.isStale(reading, now),
                        actionMessage = message,
                        heartRateHistory = history,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkerDetailState())

    fun initiateSafetyResponse(byUserId: String) {
        viewModelScope.launch {
            when (val r = monitoringRepository.initiateSafetyResponse(workerId, byUserId)) {
                is Outcome.Success -> actionMessage.value = "Safety response started. Waiting for the worker to acknowledge."
                is Outcome.Failure -> actionMessage.value = r.error.message
            }
        }
    }

    fun clearMessage() { actionMessage.value = null }

    private companion object {
        /** Roughly the last few minutes at the vest's reporting rate. */
        const val HISTORY_SIZE = 40
    }
}
