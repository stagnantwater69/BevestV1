package com.jtexpress.bevest.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class BeVestMessagingService : FirebaseMessagingService() {

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var authRepository: AuthRepository

    /**
     * [onNewToken] is already dispatched on a background thread and the process is kept
     * alive for its duration, so block here rather than launching into a scope that the
     * system may tear down before the write lands. If the user is signed out the token is
     * dropped on purpose — [com.jtexpress.bevest.navigation.RootViewModel] re-registers
     * the current token on the next sign-in.
     */
    override fun onNewToken(token: String) {
        val uid = authRepository.currentUserId() ?: return
        runCatching { runBlocking { userRepository.addFcmToken(uid, token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: "ALERT"
        val targetId = data["incidentId"] ?: data["alertId"] ?: return
        val workerId = data["workerId"]

        // The siren is for Site Safety Officers only. The server marks their pushes with
        // `alarm`; the role check here makes sure a contractor or admin signed in on this
        // phone never gets it, whatever the push says.
        val channel = when {
            data["alarm"] == "true" && !signedInAsNonSso() -> NotificationChannels.EMERGENCY
            type == "WARNING" -> NotificationChannels.WARNING
            type == "MAINTENANCE" -> NotificationChannels.MAINTENANCE
            else -> NotificationChannels.CRITICAL
        }

        // Danger pushes are data-only so this always runs, even with the app closed;
        // the title and body ride in the data.
        NotificationHelper.show(
            context = this,
            title = data["title"] ?: message.notification?.title ?: "BeVest safety alert",
            body = data["body"] ?: message.notification?.body ?: "Open the app for details",
            channelId = channel,
            type = if (type == "INCIDENT") "INCIDENT" else "ALERT",
            targetId = targetId,
            workerId = workerId,
        )
    }

    /**
     * True only when this phone is known to be signed in as someone other than an SSO.
     * If the role can't be read (offline, no cache) the server's flag stands, so an
     * officer never misses a siren over a lookup failure. Runs on a background thread.
     */
    private fun signedInAsNonSso(): Boolean {
        val uid = authRepository.currentUserId() ?: return true
        val user = runCatching { runBlocking { userRepository.getUser(uid) } }.getOrNull()
        val role = (user as? Outcome.Success)?.data?.role ?: return false
        return role != UserRole.SSO
    }
}
