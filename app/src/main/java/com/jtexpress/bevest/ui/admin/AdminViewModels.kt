package com.jtexpress.bevest.ui.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.repository.AccountRepository
import com.jtexpress.bevest.domain.repository.ActivityEntry
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.SystemRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminDashboardState(
    val loading: Boolean = true,
    val contractors: Int = 0,
    val ssos: Int = 0,
    val workers: Int = 0,
    val vests: Int = 0,
    val activeVests: Int = 0,
    val offlineVests: Int = 0,
    val maintenanceMode: Boolean = false,
    val recentActivity: List<ActivityEntry> = emptyList(),
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    userRepository: UserRepository,
    workerRepository: WorkerRepository,
    vestRepository: VestRepository,
    settingsRepository: SettingsRepository,
    systemRepository: SystemRepository,
) : ViewModel() {

    private val people = combine(
        userRepository.observeUsersByRole(UserRole.CONTRACTOR),
        userRepository.observeUsersByRole(UserRole.SSO),
        workerRepository.observeWorkers(null, null),
    ) { contractors, ssos, workers ->
        Triple(
            (contractors as? Outcome.Success)?.data.orEmpty().size,
            (ssos as? Outcome.Success)?.data.orEmpty().size,
            (workers as? Outcome.Success)?.data.orEmpty().size,
        )
    }

    private val system = combine(
        vestRepository.observeVests(),
        settingsRepository.observeMaintenanceMode(),
        systemRepository.observeActivity(20),
    ) { vestsOutcome, maintenance, activityOutcome ->
        val vests = (vestsOutcome as? Outcome.Success)?.data.orEmpty()
        val activity = (activityOutcome as? Outcome.Success)?.data.orEmpty()
        Triple(vests, maintenance, activity)
    }

    val state: StateFlow<AdminDashboardState> = combine(people, system) { (contractors, ssos, workers), (vests, maintenance, activity) ->
        AdminDashboardState(
            loading = false,
            contractors = contractors,
            ssos = ssos,
            workers = workers,
            vests = vests.size,
            activeVests = vests.count { it.status == VestStatus.ACTIVE || it.status == VestStatus.ASSIGNED },
            offlineVests = vests.count { it.status == VestStatus.OFFLINE },
            maintenanceMode = maintenance,
            recentActivity = activity,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminDashboardState())
}

data class ContractorListState(
    val loading: Boolean = true,
    val error: String? = null,
    val contractors: List<User> = emptyList(),
)

@HiltViewModel
class AdminContractorsViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    val state: StateFlow<ContractorListState> =
        userRepository.observeContractors().map { outcome ->
            when (outcome) {
                is Outcome.Success -> ContractorListState(loading = false, contractors = outcome.data)
                is Outcome.Failure -> ContractorListState(loading = false, error = outcome.error.message)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContractorListState())
}

data class ContractorDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val contractor: User? = null,
    val message: String? = null,
)

@HiltViewModel
class AdminContractorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val systemRepository: SystemRepository,
) : ViewModel() {

    private val contractorId: String = savedStateHandle.get<String>("contractorId").orEmpty()
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<ContractorDetailState> = combine(
        userRepository.observeUser(contractorId),
        message,
    ) { outcome, msg ->
        when (outcome) {
            is Outcome.Success -> ContractorDetailState(loading = false, contractor = outcome.data, message = msg)
            is Outcome.Failure -> ContractorDetailState(loading = false, error = outcome.error.message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContractorDetailState())

    fun setActive(active: Boolean, actorId: String) {
        viewModelScope.launch {
            when (val r = userRepository.setActive(contractorId, active)) {
                is Outcome.Success -> {
                    message.value = if (active) "Contractor enabled." else "Contractor disabled."
                    systemRepository.logActivity(
                        "Contractor $contractorId ${if (active) "enabled" else "disabled"}", actorId,
                    )
                }
                is Outcome.Failure -> message.value = r.error.message
            }
        }
    }
}

data class AddContractorState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val company: String = "",
    val tempPassword: String = "",
    val errors: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
    val created: Boolean = false,
    val formError: String? = null,
)

@HiltViewModel
class AddContractorViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val systemRepository: SystemRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddContractorState())
    val state: StateFlow<AddContractorState> = _state.asStateFlow()

    fun update(field: String, value: String) = _state.update {
        when (field) {
            "firstName" -> it.copy(firstName = value)
            "lastName" -> it.copy(lastName = value)
            "email" -> it.copy(email = value)
            "phone" -> it.copy(phone = value)
            "company" -> it.copy(company = value)
            "tempPassword" -> it.copy(tempPassword = value)
            else -> it
        }.copy(errors = it.errors - field, formError = null)
    }

    fun submit(actorId: String) {
        val s = _state.value
        val errors = buildMap {
            Validators.required(s.firstName, "First name")?.let { put("firstName", it) }
            Validators.required(s.lastName, "Last name")?.let { put("lastName", it) }
            Validators.email(s.email)?.let { put("email", it) }
            Validators.password(s.tempPassword)?.let { put("tempPassword", it) }
        }
        if (errors.isNotEmpty()) { _state.update { it.copy(errors = errors) }; return }
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val result = accountRepository.createUser(
                email = s.email,
                tempPassword = s.tempPassword,
                firstName = s.firstName,
                lastName = s.lastName,
                phone = s.phone,
                role = UserRole.CONTRACTOR,
                contractorId = null,
                siteId = null,
            )
            when (result) {
                is Outcome.Success -> {
                    systemRepository.logActivity("Contractor account created for ${s.email}", actorId)
                    _state.update { it.copy(submitting = false, created = true) }
                }
                is Outcome.Failure -> _state.update { it.copy(submitting = false, formError = result.error.message) }
            }
        }
    }
}

data class AdminSystemState(
    val loading: Boolean = true,
    val vests: List<Vest> = emptyList(),
    val maintenanceMode: Boolean = false,
    val message: String? = null,
) {
    val total get() = vests.size
    val active get() = vests.count { it.status == VestStatus.ACTIVE || it.status == VestStatus.ASSIGNED }
    val available get() = vests.count { it.status == VestStatus.AVAILABLE }
    val offline get() = vests.count { it.status == VestStatus.OFFLINE }
    val maintenance get() = vests.count { it.status == VestStatus.MAINTENANCE }
}

@HiltViewModel
class AdminSystemViewModel @Inject constructor(
    vestRepository: VestRepository,
    private val settingsRepository: SettingsRepository,
    private val systemRepository: SystemRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<AdminSystemState> = combine(
        vestRepository.observeVests(),
        settingsRepository.observeMaintenanceMode(),
        message,
    ) { vestsOutcome, maintenance, msg ->
        AdminSystemState(
            loading = false,
            vests = (vestsOutcome as? Outcome.Success)?.data.orEmpty(),
            maintenanceMode = maintenance,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminSystemState())

    fun setMaintenance(enabled: Boolean, actorId: String) {
        viewModelScope.launch {
            when (val r = settingsRepository.setMaintenanceMode(enabled)) {
                is Outcome.Success -> {
                    message.value = if (enabled) "Maintenance mode on." else "Maintenance mode off."
                    systemRepository.logActivity("Maintenance mode ${if (enabled) "enabled" else "disabled"}", actorId)
                }
                is Outcome.Failure -> message.value = r.error.message
            }
        }
    }
}

data class ActivityState(
    val loading: Boolean = true,
    val error: String? = null,
    val entries: List<ActivityEntry> = emptyList(),
)

@HiltViewModel
class AdminActivityViewModel @Inject constructor(
    systemRepository: SystemRepository,
) : ViewModel() {
    val state: StateFlow<ActivityState> =
        systemRepository.observeActivity(100).map { outcome ->
            when (outcome) {
                is Outcome.Success -> ActivityState(loading = false, entries = outcome.data)
                is Outcome.Failure -> ActivityState(loading = false, error = outcome.error.message)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityState())
}
