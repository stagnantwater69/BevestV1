package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val alert: Alert? = null,
    val message: String? = null,
    val working: Boolean = false,
)

@HiltViewModel
class AlertDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alertRepository: AlertRepository,
) : ViewModel() {

    private val alertId: String = savedStateHandle.get<String>("alertId").orEmpty()
    private val local = MutableStateFlow(Pair<String?, Boolean>(null, false))

    val state: StateFlow<AlertDetailState> = combine(
        alertRepository.observeAlert(alertId),
        local,
    ) { outcome, (msg, working) ->
        when (outcome) {
            is Outcome.Success -> AlertDetailState(loading = false, alert = outcome.data, message = msg, working = working)
            is Outcome.Failure -> AlertDetailState(loading = false, error = outcome.error.message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertDetailState())

    fun acknowledge(byUserId: String) {
        local.value = null to true
        viewModelScope.launch {
            local.value = when (val r = alertRepository.acknowledgeAlert(alertId, byUserId)) {
                is Outcome.Success -> "Alert acknowledged." to false
                is Outcome.Failure -> r.error.message to false
            }
        }
    }

    fun resolve(byUserId: String, notes: String) {
        local.value = null to true
        viewModelScope.launch {
            local.value = when (val r = alertRepository.resolveAlert(alertId, byUserId, notes)) {
                is Outcome.Success -> "Alert resolved." to false
                is Outcome.Failure -> r.error.message to false
            }
        }
    }
}
