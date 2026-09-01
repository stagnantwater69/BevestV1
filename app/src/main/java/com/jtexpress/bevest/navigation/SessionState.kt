package com.jtexpress.bevest.navigation

import com.jtexpress.bevest.domain.model.User

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class Disabled(val email: String) : SessionState
    data class ProfileError(val message: String) : SessionState
    data class Active(val user: User) : SessionState
}
