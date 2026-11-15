package com.jtexpress.bevest.navigation

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.domain.safety.ContractorAlertWatcher
import com.jtexpress.bevest.domain.safety.SafetyWatcher
import com.jtexpress.bevest.notifications.AlarmSoundService
import com.jtexpress.bevest.notifications.DeepLinkBus
import com.jtexpress.bevest.notifications.MonitoringService
import com.jtexpress.bevest.simulation.SimulationEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ends a session so nothing of it outlives the sign-out: this phone stops receiving the
 * account's pushes, the watchers and the simulator stop, and no alarm, notification or
 * pending notification tap is left for whoever signs in next.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val messaging: FirebaseMessaging,
    private val safetyWatcher: SafetyWatcher,
    private val contractorAlertWatcher: ContractorAlertWatcher,
    private val simulation: SimulationEngine,
    private val deepLinkBus: DeepLinkBus,
) {
    suspend fun signOut() {
        val uid = authRepository.currentUserId()
        // While still signed in, so the rules allow these writes. Bounded: being offline
        // must never stop someone signing out.
        withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
            if (uid != null) {
                runCatching { userRepository.removeFcmToken(uid, messaging.token.await()) }
            }
            runCatching { simulation.shutdown() }
        }
        // A fresh token next time, so a token that missed removal is never reused.
        runCatching { messaging.deleteToken() }

        safetyWatcher.stop()
        contractorAlertWatcher.stop()
        MonitoringService.stop(context)
        AlarmSoundService.stop(context, id = null, keepNotification = false)
        NotificationManagerCompat.from(context).cancelAll()
        deepLinkBus.clear()

        authRepository.signOut()
    }

    private companion object {
        const val CLEANUP_TIMEOUT_MS = 5_000L
    }
}
