package com.jtexpress.bevest.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.utils.Outcome
import com.jtexpress.bevest.utils.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    userRepository: UserRepository,
) : ViewModel() {
    private val uid = authRepository.currentUserId()

    val user: StateFlow<User?> =
        if (uid == null) MutableStateFlow(null)
        else userRepository.observeUser(uid)
            .map { (it as? Outcome.Success)?.data }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class EditProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val firstNameError: String? = null,
    val lastNameError: String? = null,
    val phoneError: String? = null,
    val loaded: Boolean = false,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val formError: String? = null,
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val uid = authRepository.currentUserId()
    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val id = uid ?: return@launch
            when (val result = userRepository.getUser(id)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        firstName = result.data.firstName,
                        lastName = result.data.lastName,
                        phone = result.data.phone,
                        loaded = true,
                    )
                }
                is Outcome.Failure -> _state.update { it.copy(loaded = true, formError = result.error.message) }
            }
        }
    }

    fun onFirstName(v: String) = _state.update { it.copy(firstName = v, firstNameError = null) }
    fun onLastName(v: String) = _state.update { it.copy(lastName = v, lastNameError = null) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v, phoneError = null) }

    fun save() {
        val s = _state.value
        val fe = Validators.required(s.firstName, "First name")
        val le = Validators.required(s.lastName, "Last name")
        val pe = Validators.phone(s.phone)
        if (fe != null || le != null || pe != null) {
            _state.update { it.copy(firstNameError = fe, lastNameError = le, phoneError = pe) }
            return
        }
        val id = uid ?: return
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            when (val result = userRepository.updateProfile(id, s.firstName.trim(), s.lastName.trim(), s.phone.trim())) {
                is Outcome.Success -> _state.update { it.copy(submitting = false, saved = true) }
                is Outcome.Failure -> _state.update { it.copy(submitting = false, formError = result.error.message) }
            }
        }
    }
}

data class ChangePasswordUiState(
    val current: String = "",
    val next: String = "",
    val confirm: String = "",
    val currentError: String? = null,
    val nextError: String? = null,
    val confirmError: String? = null,
    val submitting: Boolean = false,
    val changed: Boolean = false,
    val formError: String? = null,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun onCurrent(v: String) = _state.update { it.copy(current = v, currentError = null, formError = null) }
    fun onNext(v: String) = _state.update { it.copy(next = v, nextError = null, formError = null) }
    fun onConfirm(v: String) = _state.update { it.copy(confirm = v, confirmError = null, formError = null) }

    fun submit() {
        val s = _state.value
        val ce = Validators.password(s.current)
        val ne = Validators.password(s.next)
        val cfe = if (s.next != s.confirm) "Passwords do not match" else null
        if (ce != null || ne != null || cfe != null) {
            _state.update { it.copy(currentError = ce, nextError = ne, confirmError = cfe) }
            return
        }
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            when (val result = authRepository.changePassword(s.current, s.next)) {
                is Outcome.Success -> _state.update { it.copy(submitting = false, changed = true) }
                is Outcome.Failure -> _state.update { it.copy(submitting = false, formError = result.error.message) }
            }
        }
    }
}
