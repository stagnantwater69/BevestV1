package com.jtexpress.bevest.ui.contractor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.domain.repository.AccountRepository
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.MonthlyReport
import com.jtexpress.bevest.domain.repository.ReportRepository
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.domain.safety.SafetyStatusEngine
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------- Dashboard ----------------

data class ContractorDashboardState(
    val loading: Boolean = true,
    val error: String? = null,
    val activeWorkers: Int = 0,
    val normal: Int = 0,
    val warning: Int = 0,
    val danger: Int = 0,
    val todaysAlerts: Int = 0,
    val monthlyIncidents: Int = 0,
    val safetyScore: Int = 100,
    val trends: List<MonthlyReport> = emptyList(),
    val recentIncidents: List<Incident> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContractorDashboardViewModel @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val monitoringRepository: MonitoringRepository,
    private val alertRepository: AlertRepository,
    private val settingsRepository: SettingsRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val contractorId = MutableStateFlow<String?>(null)
    private val trends = MutableStateFlow<List<MonthlyReport>>(emptyList())
    private var started = false

    fun start(id: String) {
        if (started) return
        started = true
        contractorId.value = id
        viewModelScope.launch {
            (reportRepository.monthlyReports(id) as? Outcome.Success)?.let { trends.value = it.data.reversed() }
        }
    }

    val state: StateFlow<ContractorDashboardState> = contractorId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(ContractorDashboardState())
        workerRepository.observeWorkers(contractorId = id, siteId = null).flatMapLatest { workersOutcome ->
            when (workersOutcome) {
                is Outcome.Failure -> flowOf(ContractorDashboardState(loading = false, error = workersOutcome.error.message))
                is Outcome.Success -> {
                    val workers = workersOutcome.data
                    combine(
                        monitoringRepository.observeReadings(workers.map { it.workerId }),
                        settingsRepository.observeThresholds(),
                        alertRepository.observeIncidents(contractorId = id, siteId = null),
                        trends,
                    ) { readings, thresholds, incidentsOutcome, trend ->
                        val engine = SafetyStatusEngine(thresholds)
                        val now = System.currentTimeMillis()
                        val statuses = workers.map { engine.evaluate(readings[it.workerId], now) }
                        val incidents = (incidentsOutcome as? Outcome.Success)?.data.orEmpty()
                        val monthAgo = now - 30L * 24 * 3600 * 1000
                        val monthly = incidents.count { it.createdAt >= monthAgo }
                        val dayAgo = now - 24L * 3600 * 1000
                        ContractorDashboardState(
                            loading = false,
                            activeWorkers = workers.count { it.active },
                            normal = statuses.count { it == SafetyStatus.NORMAL },
                            warning = statuses.count { it == SafetyStatus.WARNING },
                            danger = statuses.count { it == SafetyStatus.DANGER || it == SafetyStatus.EMERGENCY },
                            todaysAlerts = incidents.count { it.createdAt >= dayAgo },
                            monthlyIncidents = monthly,
                            safetyScore = trend.lastOrNull()?.safetyPercentage ?: 100,
                            trends = trend,
                            recentIncidents = incidents.take(5),
                        )
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContractorDashboardState())
}

// ---------------- SSO management ----------------

data class SsoListState(
    val loading: Boolean = true,
    val error: String? = null,
    val ssos: List<User> = emptyList(),
    val query: String = "",
) {
    val visible get() = ssos.filter {
        val q = query.trim().lowercase()
        q.isEmpty() || it.fullName.lowercase().contains(q) || it.email.lowercase().contains(q)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SsoListViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val contractorId = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private var started = false

    fun start(id: String) { if (!started) { started = true; contractorId.value = id } }
    fun onQuery(v: String) { query.value = v }

    fun disable(uid: String) {
        viewModelScope.launch { userRepository.setActive(uid, false) }
    }

    val state: StateFlow<SsoListState> = contractorId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(SsoListState())
        combine(userRepository.observeSsos(id), query) { outcome, q ->
            when (outcome) {
                is Outcome.Success -> SsoListState(loading = false, ssos = outcome.data, query = q)
                is Outcome.Failure -> SsoListState(loading = false, error = outcome.error.message, query = q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SsoListState())
}

data class AddSsoState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val tempPassword: String = "",
    val siteId: String = "",
    val errors: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
    val created: Boolean = false,
    val formError: String? = null,
)

@HiltViewModel
class AddSsoViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private var contractorId: String = ""
    fun bind(id: String) { contractorId = id }

    private val _state = MutableStateFlow(AddSsoState())
    val state: StateFlow<AddSsoState> = _state.asStateFlow()

    fun update(field: String, value: String) = _state.update {
        when (field) {
            "firstName" -> it.copy(firstName = value)
            "lastName" -> it.copy(lastName = value)
            "email" -> it.copy(email = value)
            "phone" -> it.copy(phone = value)
            "tempPassword" -> it.copy(tempPassword = value)
            "siteId" -> it.copy(siteId = value)
            else -> it
        }.copy(errors = it.errors - field, formError = null)
    }

    fun submit() {
        val s = _state.value
        val errors = buildMap {
            Validators.required(s.firstName, "First name")?.let { put("firstName", it) }
            Validators.required(s.lastName, "Last name")?.let { put("lastName", it) }
            Validators.email(s.email)?.let { put("email", it) }
            Validators.password(s.tempPassword)?.let { put("tempPassword", it) }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val result = accountRepository.createUser(
                email = s.email,
                tempPassword = s.tempPassword,
                firstName = s.firstName,
                lastName = s.lastName,
                phone = s.phone,
                role = UserRole.SSO,
                contractorId = contractorId,
                siteId = s.siteId.ifBlank { null },
            )
            when (result) {
                is Outcome.Success -> _state.update { it.copy(submitting = false, created = true) }
                is Outcome.Failure -> _state.update { it.copy(submitting = false, formError = result.error.message) }
            }
        }
    }
}

// ---------------- Reports ----------------

data class ReportsState(
    val loading: Boolean = true,
    val error: String? = null,
    val reports: List<MonthlyReport> = emptyList(),
)

@HiltViewModel
class ContractorReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()
    private var started = false

    fun start(contractorId: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            _state.value = when (val r = reportRepository.monthlyReports(contractorId, months = 6)) {
                is Outcome.Success -> ReportsState(loading = false, reports = r.data)
                is Outcome.Failure -> ReportsState(loading = false, error = r.error.message)
            }
        }
    }
}

data class ReportDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val report: MonthlyReport? = null,
    val incidents: List<Incident> = emptyList(),
)

@HiltViewModel
class MonthlyReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportRepository: ReportRepository,
    private val alertRepository: AlertRepository,
) : ViewModel() {

    private val monthKey: String = savedStateHandle.get<String>("monthKey").orEmpty()
    private val _state = MutableStateFlow(ReportDetailState())
    val state: StateFlow<ReportDetailState> = _state.asStateFlow()
    private var started = false

    fun start(contractorId: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            val reports = (reportRepository.monthlyReports(contractorId, months = 12) as? Outcome.Success)?.data.orEmpty()
            val report = reports.firstOrNull { it.monthKey == monthKey }
            alertRepository.observeIncidents(contractorId = contractorId, siteId = null, limit = 500)
                .collect { outcome ->
                    val incidents = (outcome as? Outcome.Success)?.data.orEmpty()
                        .filter { com.jtexpress.bevest.utils.DateTimeUtils.monthKey(it.createdAt) == monthKey }
                    _state.value = ReportDetailState(loading = false, report = report, incidents = incidents)
                }
        }
    }
}

data class IncidentHistoryState(
    val loading: Boolean = true,
    val error: String? = null,
    val incidents: List<Incident> = emptyList(),
    val severityFilter: AlertSeverity? = null,
) {
    val visible get() = incidents.filter { severityFilter == null || it.severity == severityFilter }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContractorIncidentsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val contractorId = MutableStateFlow<String?>(null)
    private val filter = MutableStateFlow<AlertSeverity?>(null)
    private var started = false

    fun start(id: String) { if (!started) { started = true; contractorId.value = id } }
    fun onFilter(severity: AlertSeverity?) { filter.value = severity }

    val state: StateFlow<IncidentHistoryState> = contractorId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(IncidentHistoryState())
        combine(reportRepository.observeIncidentHistory(id), filter) { outcome, f ->
            when (outcome) {
                is Outcome.Success -> IncidentHistoryState(loading = false, incidents = outcome.data, severityFilter = f)
                is Outcome.Failure -> IncidentHistoryState(loading = false, error = outcome.error.message, severityFilter = f)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidentHistoryState())
}
