package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.AlertRepository
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val incident: Incident? = null,
    val worker: Worker? = null,
    val message: String? = null,
    val working: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alertRepository: AlertRepository,
    private val workerRepository: WorkerRepository,
    private val responder: AlertResponder,
) : ViewModel() {

    private val incidentId: String = savedStateHandle.get<String>("incidentId").orEmpty()
    private val local = MutableStateFlow(Pair<String?, Boolean>(null, false))

    private val incidentFlow = alertRepository.observeIncident(incidentId)

    private val workerFlow = incidentFlow.flatMapLatest { outcome ->
        val workerId = (outcome as? Outcome.Success)?.data?.workerId?.takeIf { it.isNotBlank() }
        if (workerId == null) flowOf(null)
        else workerRepository.observeWorker(workerId).map { (it as? Outcome.Success)?.data }
    }

    val state: StateFlow<IncidentDetailState> = combine(
        incidentFlow,
        workerFlow,
        local,
    ) { outcome, worker, (msg, working) ->
        when (outcome) {
            is Outcome.Success -> IncidentDetailState(
                loading = false,
                incident = outcome.data,
                worker = worker,
                message = msg,
                working = working,
            )
            is Outcome.Failure -> IncidentDetailState(loading = false, error = outcome.error.message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidentDetailState())

    fun resolve(byUserId: String, outcome: String, notes: String) {
        local.value = null to true
        viewModelScope.launch {
            local.value = when (val r = alertRepository.resolveIncident(incidentId, byUserId, outcome, notes)) {
                is Outcome.Success -> {
                    state.value.incident?.workerId?.let { responder.resolved(it) }
                    "Incident resolved." to false
                }
                is Outcome.Failure -> r.error.message to false
            }
        }
    }
}
