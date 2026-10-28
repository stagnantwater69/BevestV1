package com.jtexpress.bevest.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The "Stop alarm" button on an emergency notification: silence it, keep the alert.
 * Also hears the alert being swiped away, which stops the alarm with nothing left.
 */
class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val targetId = intent.getStringExtra(NotificationHelper.EXTRA_DEEPLINK_ID) ?: return
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_SWIPED, false)) {
            NotificationHelper.cancel(context, targetId)
            return
        }
        NotificationHelper.silence(
            context = context,
            title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Safety alert",
            body = intent.getStringExtra(NotificationHelper.EXTRA_BODY).orEmpty(),
            type = intent.getStringExtra(NotificationHelper.EXTRA_DEEPLINK_TYPE) ?: "ALERT",
            targetId = targetId,
            workerId = intent.getStringExtra(NotificationHelper.EXTRA_DEEPLINK_WORKER),
        )
    }
}
