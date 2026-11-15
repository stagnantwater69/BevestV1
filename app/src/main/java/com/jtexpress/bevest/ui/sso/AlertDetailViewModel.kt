package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.ProjectRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.domain.safety.AlertResponder
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val alert: Alert? = null,
    val workerName: String? = null,
    val siteName: String? = null,
    val floorNumber: String? = null,
    val message: String? = null,
    val working: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alertRepository: AlertRepository,
    private val workerRepository: WorkerRepository,
    private val projectRepository: ProjectRepository,
    private val responder: AlertResponder,
) : ViewModel() {

    private val alertId: String = savedStateHandle.get<String>("alertId").orEmpty()
    private val local = MutableStateFlow(Pair<String?, Boolean>(null, false))

    val state: StateFlow<AlertDetailState> = alertRepository.observeAlert(alertId)
        .flatMapLatest { outcome ->
            when (outcome) {
                is Outcome.Failure -> flowOf(AlertDetailState(loading = false, error = outcome.error.message))
                is Outcome.Success -> {
                    val alert = outcome.data
                    val workerFlow = if (alert.workerId.isNotBlank()) {
                        workerRepository.observeWorker(alert.workerId)
                    } else {
                        flowOf(Outcome.Failure(com.jtexpress.bevest.utils.AppError.NotFound()))
                    }
                    combine(workerFlow, local) { workerOutcome, (msg, working) ->
                        Triple(workerOutcome, msg, working)
                    }.flatMapLatest { (workerOutcome, msg, working) ->
                        val worker = (workerOutcome as? Outcome.Success)?.data
                        val effectiveSiteId = alert.siteId?.takeIf { it.isNotBlank() }
                            ?: worker?.siteId?.takeIf { it.isNotBlank() }
                        val effectiveFloor = alert.floorNumber?.takeIf { it.isNotBlank() }
                            ?: worker?.floorNumber?.takeIf { it.isNotBlank() }
                        flow {
                            val resolvedSiteName = alert.siteName?.takeIf { it.isNotBlank() }
                                ?: effectiveSiteId?.let { sId ->
                                    (projectRepository.getProject(sId) as? Outcome.Success)?.data?.name?.takeIf { it.isNotBlank() }
                                        ?: sId
                                }
                            emit(
                                AlertDetailState(
                                    loading = false,
                                    alert = alert,
                                    workerName = worker?.fullName?.takeIf { it.isNotBlank() },
                                    siteName = resolvedSiteName,
                                    floorNumber = effectiveFloor,
                                    message = msg,
                                    working = working,
                                ),
                            )
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertDetailState())

    fun acknowledge(byUserId: String) {
        local.value = null to true
        viewModelScope.launch {
            local.value = when (val r = alertRepository.acknowledgeAlert(alertId, byUserId)) {
                is Outcome.Success -> {
                    state.value.alert?.workerId?.let { responder.acknowledged(it) }
                    "Alert acknowledged." to false
                }
                is Outcome.Failure -> r.error.message to false
            }
        }
    }

    fun resolve(byUserId: String, notes: String) {
        local.value = null to true
        viewModelScope.launch {
            local.value = when (val r = alertRepository.resolveAlert(alertId, byUserId, notes)) {
                is Outcome.Success -> {
                    state.value.alert?.workerId?.let { responder.resolved(it) }
                    "Alert resolved." to false
                }
                is Outcome.Failure -> r.error.message to false
            }
        }
    }
}
