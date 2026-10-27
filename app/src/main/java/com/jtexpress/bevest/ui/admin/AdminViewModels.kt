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

    // Counted from every account rather than a per-role query: the role field is matched
    // case-insensitively, so accounts written as "Contractor" by the web are counted too.
    private val people = combine(
        userRepository.observeAllUsers(),
        workerRepository.observeWorkers(null, null),
    ) { usersOutcome, workers ->
        val users = (usersOutcome as? Outcome.Success)?.data.orEmpty()
        Triple(
            users.count { it.role == UserRole.CONTRACTOR },
            users.count { it.role == UserRole.SSO },
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

/** Role filter keys for the user list; [ALL_ROLES] shows everyone. */
const val ALL_ROLES = "ALL"

data class UserListState(
    val loading: Boolean = true,
    val error: String? = null,
    val all: List<User> = emptyList(),
    val query: String = "",
    val roleFilter: String = ALL_ROLES,
    val sortAscending: Boolean = true,
) {
    val visible: List<User>
        get() {
            val q = query.trim().lowercase()
            val filtered = all.filter { u ->
                (roleFilter == ALL_ROLES || u.role.name == roleFilter) &&
                    (q.isEmpty() || u.fullName.lowercase().contains(q) || u.email.lowercase().contains(q))
            }
            val sorted = filtered.sortedBy { it.fullName.lowercase() }
            return if (sortAscending) sorted else sorted.reversed()
        }

    val activeCount get() = all.count { it.active }

    fun count(role: UserRole) = all.count { it.role == role }
}

/** User Management: every account in one list, mirroring the web admin. */
@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    userRepository: UserRepository,
) : ViewModel() {

    private val controls = MutableStateFlow(UserListState())

    val state: StateFlow<UserListState> = combine(
        userRepository.observeAllUsers(),
        controls,
    ) { outcome, c ->
        when (outcome) {
            is Outcome.Success -> c.copy(loading = false, error = null, all = outcome.data)
            is Outcome.Failure -> c.copy(loading = false, error = outcome.error.message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserListState())

    fun setQuery(value: String) = controls.update { it.copy(query = value) }

    fun setRoleFilter(key: String) = controls.update { it.copy(roleFilter = key) }

    fun toggleSort() = controls.update { it.copy(sortAscending = !it.sortAscending) }
}

data class UserDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val user: User? = null,
    val message: String? = null,
)

@HiltViewModel
class AdminUserDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val systemRepository: SystemRepository,
) : ViewModel() {

    private val uid: String = savedStateHandle.get<String>("uid").orEmpty()
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<UserDetailState> = combine(
        userRepository.observeUser(uid),
        message,
    ) { outcome, msg ->
        when (outcome) {
            is Outcome.Success -> UserDetailState(loading = false, user = outcome.data, message = msg)
            is Outcome.Failure -> UserDetailState(loading = false, error = outcome.error.message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserDetailState())

    fun setActive(active: Boolean, actorId: String) {
        val email = state.value.user?.email ?: uid
        viewModelScope.launch {
            when (val r = userRepository.setActive(uid, active)) {
                is Outcome.Success -> {
                    message.value = if (active) "Account enabled." else "Account disabled."
                    systemRepository.logActivity(
                        "Account $email ${if (active) "enabled" else "disabled"}", actorId,
                    )
                }
                is Outcome.Failure -> message.value = r.error.message
            }
        }
    }
}

data class AddUserState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.SSO,
    /** The contractor an SSO reports to; only used when [role] is SSO. */
    val contractorId: String? = null,
    val contractors: List<User> = emptyList(),
    val tempPassword: String = "",
    val errors: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
    val created: Boolean = false,
    val formError: String? = null,
)

/** "Register new user" — any role, with the same generated password format as the web. */
@HiltViewModel
class AddUserViewModel @Inject constructor(
    userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val systemRepository: SystemRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddUserState())

    val state: StateFlow<AddUserState> = combine(
        _state,
        userRepository.observeAllUsers(),
    ) { s, outcome ->
        val contractors = (outcome as? Outcome.Success)?.data.orEmpty()
            .filter { it.role == UserRole.CONTRACTOR && it.active }
            .sortedBy { it.fullName.lowercase() }
        s.copy(contractors = contractors)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddUserState())

    fun update(field: String, value: String) = _state.update {
        when (field) {
            "firstName" -> it.copy(firstName = value)
            "lastName" -> it.copy(lastName = value)
            "email" -> it.copy(email = value)
            "phone" -> it.copy(phone = value)
            "tempPassword" -> it.copy(tempPassword = value)
            else -> it
        }.copy(errors = it.errors - field, formError = null)
    }

    fun setRole(role: UserRole) = _state.update {
        it.copy(role = role, errors = it.errors - "role" - "contractorId", formError = null)
    }

    fun setContractor(id: String) = _state.update {
        it.copy(contractorId = id, errors = it.errors - "contractorId", formError = null)
    }

    fun generatePassword() = _state.update {
        it.copy(tempPassword = "GID-2026-${(1000..9999).random()}", errors = it.errors - "tempPassword")
    }

    fun submit(actorId: String) {
        val s = state.value
        val errors = buildMap {
            Validators.required(s.firstName, "First name")?.let { put("firstName", it) }
            Validators.required(s.lastName, "Last name")?.let { put("lastName", it) }
            Validators.email(s.email)?.let { put("email", it) }
            Validators.password(s.tempPassword)?.let { put("tempPassword", it) }
            if (s.role == UserRole.SSO && s.contractorId.isNullOrBlank()) {
                put("contractorId", "Choose the contractor this officer reports to")
            }
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
                role = s.role,
                contractorId = if (s.role == UserRole.SSO) s.contractorId else null,
                siteId = null,
            )
            when (result) {
                is Outcome.Success -> {
                    val label = when (s.role) {
                        UserRole.ADMIN -> "Admin"
                        UserRole.CONTRACTOR -> "Contractor"
                        else -> "SSO"
                    }
                    systemRepository.logActivity("$label account created for ${s.email}", actorId)
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
