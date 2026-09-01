package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val incident: Incident? = null,
    val message: String? = null,
    val working: Boolean = false,
)

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alertRepository: AlertRepository,
) : ViewModel() {

    private val incidentId: String = savedStateHandle.get<String>("incidentId").orEmpty()
    private val local = MutableStateFlow(Pair<String?, Boolean>(null, false))

    val state: StateFlow<IncidentDetailState> = combine(
        alertRepository.observeIncident(incidentId),
        local,
    ) { outcome, (msg, working) ->
        when (outcome) {
            is Outcome.Success -> IncidentDetailState(loading = false, incident = outcome.data, message = msg, working = working)
            is Outcome.Failure -> IncidentDetailState(loading = false, error = outcome.error.message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidentDetailState())

    fun resolve(byUserId: String, outcome: String, notes: String) {
        local.value = null to true
        viewModelScope.launch {
            local.value = when (val r = alertRepository.resolveIncident(incidentId, byUserId, outcome, notes)) {
                is Outcome.Success -> "Incident resolved." to false
                is Outcome.Failure -> r.error.message to false
            }
        }
    }
}
