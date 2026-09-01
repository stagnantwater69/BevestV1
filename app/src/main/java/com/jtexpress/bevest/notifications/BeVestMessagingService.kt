package com.jtexpress.bevest.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.repository.UserRepository
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

        val channel = when (type) {
            "INCIDENT" -> NotificationChannels.CRITICAL
            "WARNING" -> NotificationChannels.WARNING
            "MAINTENANCE" -> NotificationChannels.MAINTENANCE
            else -> NotificationChannels.CRITICAL
        }

        NotificationHelper.show(
            context = this,
            title = message.notification?.title ?: "BeVest safety alert",
            body = message.notification?.body ?: "Open the app for details",
            channelId = channel,
            type = if (type == "INCIDENT") "INCIDENT" else "ALERT",
            targetId = targetId,
            workerId = workerId,
        )
    }
}
