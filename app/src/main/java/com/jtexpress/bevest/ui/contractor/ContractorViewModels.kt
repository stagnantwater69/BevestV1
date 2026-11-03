package com.jtexpress.bevest.ui.contractor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.model.ProjectSite
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.domain.repository.AccountRepository
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.MonthlyReport
import com.jtexpress.bevest.domain.repository.ProjectRepository
import com.jtexpress.bevest.domain.repository.ReportRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
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
import java.security.SecureRandom
import javax.inject.Inject

// ---------------- Dashboard ----------------

data class ContractorDashboardState(
    val loading: Boolean = true,
    val error: String? = null,
    val activeSsos: Int = 0,
    val monthlyIncidents: Int = 0,
    val safetyScore: Int = 100,
    val trends: List<MonthlyReport> = emptyList(),
    /** Raw incidents behind the trend chart, which re-buckets them by month or by day. */
    val incidents: List<Incident> = emptyList(),
) {
    /** The month the safety score belongs to — the tile opens that month's report. */
    val currentMonthKey: String? get() = trends.lastOrNull()?.monthKey
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContractorDashboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val alertRepository: AlertRepository,
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
        combine(
            userRepository.observeSsos(id),
            alertRepository.observeIncidents(contractorId = id, siteId = null, limit = 500),
            trends,
        ) { ssosOutcome, incidentsOutcome, trend ->
            when (ssosOutcome) {
                is Outcome.Failure -> ContractorDashboardState(loading = false, error = ssosOutcome.error.message)
                is Outcome.Success -> {
                    val now = System.currentTimeMillis()
                    val incidents = (incidentsOutcome as? Outcome.Success)?.data.orEmpty()
                    val monthAgo = now - 30L * 24 * 3600 * 1000
                    ContractorDashboardState(
                        loading = false,
                        activeSsos = ssosOutcome.data.count { it.active },
                        monthlyIncidents = incidents.count { it.createdAt >= monthAgo },
                        safetyScore = trend.lastOrNull()?.safetyPercentage ?: 100,
                        trends = trend,
                        incidents = incidents,
                    )
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
    val sites: List<ProjectSite> = emptyList(),
    val query: String = "",
) {
    fun siteName(siteId: String?): String = siteLabel(sites, siteId)

    val visible get() = ssos.filter {
        val q = query.trim().lowercase()
        q.isEmpty() || it.fullName.lowercase().contains(q) || it.email.lowercase().contains(q)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SsoListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
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
        combine(
            userRepository.observeSsos(id),
            projectRepository.observeProjects(id),
            query,
        ) { outcome, sitesOutcome, q ->
            // Site names are a nicety: without them the raw site ID is shown instead.
            val sites = (sitesOutcome as? Outcome.Success)?.data.orEmpty()
            when (outcome) {
                is Outcome.Success -> SsoListState(
                    loading = false,
                    ssos = outcome.data.sortedBy { it.fullName.ifBlank { it.email }.lowercase() },
                    sites = sites,
                    query = q,
                )
                is Outcome.Failure -> SsoListState(loading = false, error = outcome.error.message, query = q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SsoListState())
}

/** A site's display name, falling back to its raw ID when it isn't a known project. */
fun siteLabel(sites: List<ProjectSite>, siteId: String?): String {
    if (siteId.isNullOrBlank()) return "No site assigned"
    return sites.firstOrNull { it.projectId == siteId }?.name?.ifBlank { null } ?: siteId
}

// ---------------- SSO profile ----------------

data class SsoEditForm(
    val firstName: String,
    val lastName: String,
    val phone: String,
    /** Blank means "no site assigned". */
    val siteId: String,
    /** True when the contractor is typing a site ID that isn't one of their projects. */
    val customSite: Boolean,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val formError: String? = null,
)

data class SsoProfileState(
    val loading: Boolean = true,
    val error: String? = null,
    val sso: User? = null,
    val sites: List<ProjectSite> = emptyList(),
    val workers: List<Worker> = emptyList(),
    val edit: SsoEditForm? = null,
) {
    /** Workers belong to a site; the officer looks after everyone at theirs. */
    val siteWorkers: List<Worker>
        get() {
            val site = sso?.siteId ?: return emptyList()
            return workers.filter { it.siteId == site }.sortedBy { it.fullName.ifBlank { it.workerId }.lowercase() }
        }

    /**
     * Sites to offer in the picker: the contractor's projects, plus any site a worker or
     * this officer already uses that isn't a registered project, so nothing assigned
     * today silently disappears from the list.
     */
    val siteOptions: List<ProjectSite>
        get() {
            val known = sites.associateBy { it.projectId }.toMutableMap()
            (workers.mapNotNull { it.siteId } + listOfNotNull(sso?.siteId))
                .filter { it.isNotBlank() && it !in known }
                .forEach { known[it] = ProjectSite(projectId = it, name = "Site $it") }
            return known.values.sortedBy { it.name.lowercase() }
        }

    fun siteName(siteId: String?): String = siteLabel(siteOptions, siteId)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SsoProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val workerRepository: WorkerRepository,
) : ViewModel() {

    private val args = MutableStateFlow<Pair<String, String>?>(null) // contractorId to uid
    private val edit = MutableStateFlow<SsoEditForm?>(null)

    fun start(contractorId: String, uid: String) {
        if (args.value == null) args.value = contractorId to uid
    }

    val state: StateFlow<SsoProfileState> = args.flatMapLatest { a ->
        if (a == null) return@flatMapLatest flowOf(SsoProfileState())
        val (contractorId, uid) = a
        combine(
            userRepository.observeSsos(contractorId),
            projectRepository.observeProjects(contractorId),
            workerRepository.observeWorkers(contractorId = contractorId, siteId = null),
            edit,
        ) { ssosOutcome, sitesOutcome, workersOutcome, form ->
            when (ssosOutcome) {
                is Outcome.Failure -> SsoProfileState(loading = false, error = ssosOutcome.error.message)
                is Outcome.Success -> SsoProfileState(
                    loading = false,
                    sso = ssosOutcome.data.firstOrNull { it.uid == uid },
                    sites = (sitesOutcome as? Outcome.Success)?.data.orEmpty(),
                    workers = (workersOutcome as? Outcome.Success)?.data.orEmpty(),
                    edit = form,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SsoProfileState())

    fun startEdit() {
        val s = state.value
        val sso = s.sso ?: return
        val site = sso.siteId.orEmpty()
        edit.value = SsoEditForm(
            firstName = sso.firstName,
            lastName = sso.lastName,
            phone = sso.phone,
            siteId = site,
            customSite = site.isNotBlank() && s.siteOptions.none { it.projectId == site },
        )
    }

    fun cancelEdit() { edit.value = null }

    fun update(field: String, value: String) = edit.update { f ->
        f ?: return@update null
        when (field) {
            "firstName" -> f.copy(firstName = value)
            "lastName" -> f.copy(lastName = value)
            "phone" -> f.copy(phone = value)
            "siteId" -> f.copy(siteId = value)
            else -> f
        }.copy(errors = f.errors - field, formError = null)
    }

    /** Pick a known site, or null to switch to typing a custom site ID. */
    fun pickSite(siteId: String?) = edit.update { f ->
        f?.copy(
            siteId = siteId.orEmpty(),
            customSite = siteId == null,
            errors = f.errors - "siteId",
            formError = null,
        )
    }

    fun save() {
        val f = edit.value ?: return
        val uid = args.value?.second ?: return
        val errors = buildMap {
            Validators.required(f.firstName, "First name")?.let { put("firstName", it) }
            Validators.required(f.lastName, "Last name")?.let { put("lastName", it) }
            Validators.phone(f.phone, required = false)?.let { put("phone", it) }
            if (f.customSite && f.siteId.isBlank()) put("siteId", "Enter a site ID, or pick one from the list")
        }
        if (errors.isNotEmpty()) {
            edit.value = f.copy(errors = errors)
            return
        }
        edit.value = f.copy(saving = true, formError = null)
        viewModelScope.launch {
            val result = userRepository.updateSso(
                uid = uid,
                firstName = f.firstName.trim(),
                lastName = f.lastName.trim(),
                phone = f.phone.trim(),
                siteId = f.siteId.trim().ifBlank { null },
            )
            edit.value = when (result) {
                is Outcome.Success -> null
                is Outcome.Failure -> f.copy(saving = false, formError = result.error.message)
            }
        }
    }
}

// ---------------- Add SSO ----------------

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

    /**
     * Fill in a temporary password. Drawn from a secure source and skipping look-alike
     * characters (0/O, 1/l/I), since it is usually read out or copied by hand.
     */
    fun generatePassword() {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        val chunk = { (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") }
        update("tempPassword", "SSO-${chunk()}-${chunk()}")
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
    /** Shown on the incident-history link; null until it loads. */
    val incidentCount: Int? = null,
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
            val r = reportRepository.monthlyReports(contractorId, months = 6)
            _state.update {
                when (r) {
                    is Outcome.Success -> it.copy(loading = false, reports = r.data)
                    is Outcome.Failure -> it.copy(loading = false, error = r.error.message)
                }
            }
        }
        viewModelScope.launch {
            reportRepository.observeIncidentHistory(contractorId).collect { outcome ->
                (outcome as? Outcome.Success)?.let { s -> _state.update { it.copy(incidentCount = s.data.size) } }
            }
        }
    }
}

/** Worker ID → display name, so incident rows can say who rather than which ID. */
private fun List<Worker>.namesById(): Map<String, String> =
    associate { it.workerId to it.fullName.ifBlank { it.workerId } }

data class ReportDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val report: MonthlyReport? = null,
    val incidents: List<Incident> = emptyList(),
    val workerNames: Map<String, String> = emptyMap(),
)

@HiltViewModel
class MonthlyReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportRepository: ReportRepository,
    private val alertRepository: AlertRepository,
    private val workerRepository: WorkerRepository,
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
            combine(
                alertRepository.observeIncidents(contractorId = contractorId, siteId = null, limit = 500),
                workerRepository.observeWorkers(contractorId = contractorId, siteId = null),
            ) { outcome, workersOutcome ->
                val incidents = (outcome as? Outcome.Success)?.data.orEmpty()
                    .filter { com.jtexpress.bevest.utils.DateTimeUtils.monthKey(it.createdAt) == monthKey }
                ReportDetailState(
                    loading = false,
                    report = report,
                    incidents = incidents,
                    workerNames = (workersOutcome as? Outcome.Success)?.data.orEmpty().namesById(),
                )
            }.collect { _state.value = it }
        }
    }
}

data class IncidentHistoryState(
    val loading: Boolean = true,
    val error: String? = null,
    val incidents: List<Incident> = emptyList(),
    val severityFilter: AlertSeverity? = null,
    val workerNames: Map<String, String> = emptyMap(),
) {
    val visible get() = incidents.filter { severityFilter == null || it.severity == severityFilter }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContractorIncidentsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val workerRepository: WorkerRepository,
) : ViewModel() {

    private val contractorId = MutableStateFlow<String?>(null)
    private val filter = MutableStateFlow<AlertSeverity?>(null)
    private var started = false

    fun start(id: String) { if (!started) { started = true; contractorId.value = id } }
    fun onFilter(severity: AlertSeverity?) { filter.value = severity }

    val state: StateFlow<IncidentHistoryState> = contractorId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(IncidentHistoryState())
        combine(
            reportRepository.observeIncidentHistory(id),
            workerRepository.observeWorkers(contractorId = id, siteId = null),
            filter,
        ) { outcome, workersOutcome, f ->
            when (outcome) {
                is Outcome.Success -> IncidentHistoryState(
                    loading = false,
                    incidents = outcome.data,
                    severityFilter = f,
                    workerNames = (workersOutcome as? Outcome.Success)?.data.orEmpty().namesById(),
                )
                is Outcome.Failure -> IncidentHistoryState(loading = false, error = outcome.error.message, severityFilter = f)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidentHistoryState())
}
