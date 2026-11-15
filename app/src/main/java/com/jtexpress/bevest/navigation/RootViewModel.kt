package com.jtexpress.bevest.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.notifications.DeepLink
import com.jtexpress.bevest.notifications.DeepLinkBus
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val messaging: FirebaseMessaging,
    private val sessionManager: SessionManager,
    deepLinkBus: DeepLinkBus,
) : ViewModel() {

    val deepLinks: Flow<DeepLink> = deepLinkBus.events

    val session: StateFlow<SessionState> = authRepository.authState
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(SessionState.SignedOut)
            } else {
                userRepository.observeUser(uid).map { outcome ->
                    when (outcome) {
                        is Outcome.Success -> {
                            val user = outcome.data
                            if (!user.active) SessionState.Disabled(user.email)
                            else SessionState.Active(user)
                        }
                        is Outcome.Failure -> SessionState.ProfileError(outcome.error.message)
                    }
                }
            }
        }
        .onEach { state -> if (state is SessionState.Active) registerFcmToken(state.user.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    /** The account this phone's push token was last registered for. */
    private var tokenRegisteredFor: String? = null

    /** Once per signed-in account — not on every change to the user's profile. */
    private suspend fun registerFcmToken(uid: String) {
        if (tokenRegisteredFor == uid) return
        runCatching {
            val token = messaging.token.await()
            if (userRepository.addFcmToken(uid, token) is Outcome.Success) tokenRegisteredFor = uid
        }
    }

    fun signOut() {
        tokenRegisteredFor = null
        viewModelScope.launch { sessionManager.signOut() }
    }
}
