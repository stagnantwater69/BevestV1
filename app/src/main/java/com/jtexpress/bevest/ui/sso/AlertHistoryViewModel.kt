package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AlertHistoryState(
    val loading: Boolean = true,
    val error: String? = null,
    val alerts: List<Alert> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertHistoryViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
) : ViewModel() {

    private val siteId = MutableStateFlow<String?>(null)
    private var started = false

    fun start(site: String?) {
        if (started) return
        started = true
        siteId.value = site
    }

    val state: StateFlow<AlertHistoryState> = siteId.flatMapLatest { site ->
        alertRepository.observeAlertHistory(site).map { outcome ->
            when (outcome) {
                is Outcome.Success -> AlertHistoryState(loading = false, alerts = outcome.data)
                is Outcome.Failure -> AlertHistoryState(loading = false, error = outcome.error.message)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertHistoryState())
}
