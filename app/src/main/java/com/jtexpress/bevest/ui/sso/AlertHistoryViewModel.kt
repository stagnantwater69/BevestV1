package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.utils.DateTimeUtils
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Which slice of the history the user is looking at. */
enum class AlertFilter { ALL, ACTIVE, RESOLVED }

data class AlertHistoryState(
    val loading: Boolean = true,
    val error: String? = null,
    val alerts: List<Alert> = emptyList(),
    val filter: AlertFilter = AlertFilter.ALL,
) {
    val activeCount get() = alerts.count { it.status != AlertStatus.RESOLVED }
    val resolvedCount get() = alerts.count { it.status == AlertStatus.RESOLVED }

    val visible: List<Alert>
        get() = when (filter) {
            AlertFilter.ALL -> alerts
            AlertFilter.ACTIVE -> alerts.filter { it.status != AlertStatus.RESOLVED }
            AlertFilter.RESOLVED -> alerts.filter { it.status == AlertStatus.RESOLVED }
        }

    fun countFor(f: AlertFilter): Int = when (f) {
        AlertFilter.ALL -> alerts.size
        AlertFilter.ACTIVE -> activeCount
        AlertFilter.RESOLVED -> resolvedCount
    }

    /**
     * The visible alerts bucketed under a day heading, newest day first.
     *
     * Grouping is done here rather than in the composable so the list can be built with
     * stable keys and so the screen stays free of date arithmetic.
     */
    val grouped: List<Pair<String, List<Alert>>>
        get() = visible
            .sortedByDescending { it.createdAt }
            .groupBy { DateTimeUtils.dayLabel(it.createdAt) }
            .toList()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertHistoryViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
) : ViewModel() {

    private val siteId = MutableStateFlow<String?>(null)
    private val filter = MutableStateFlow(AlertFilter.ALL)
    private var started = false

    fun start(site: String?) {
        if (started) return
        started = true
        siteId.value = site
    }

    fun onFilter(f: AlertFilter) {
        filter.value = f
    }

    val state: StateFlow<AlertHistoryState> = siteId.flatMapLatest { site ->
        // Filtering is combined in rather than re-queried, so changing tabs never
        // re-hits the network or blanks a list the user is already reading.
        combine(alertRepository.observeAlertHistory(site), filter) { outcome, f ->
            when (outcome) {
                is Outcome.Success -> AlertHistoryState(
                    loading = false,
                    alerts = outcome.data,
                    filter = f,
                )
                is Outcome.Failure -> AlertHistoryState(
                    loading = false,
                    error = outcome.error.message,
                    filter = f,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertHistoryState())
}
