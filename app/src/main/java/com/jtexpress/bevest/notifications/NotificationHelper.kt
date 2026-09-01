package com.jtexpress.bevest.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jtexpress.bevest.MainActivity
import com.jtexpress.bevest.R

object NotificationChannels {
    const val CRITICAL = "critical"
    const val WARNING = "warning"
    const val MAINTENANCE = "maintenance"
}

object NotificationHelper {

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.CRITICAL,
                "Critical safety alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Fall, no safety response, emergency requests, danger status"
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.WARNING,
                "Warnings",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "High heart rate, high temperature, inactivity, vest offline" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.MAINTENANCE,
                "Maintenance",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Low battery, device service" },
        )
    }

    /**
     * Shows a notification that deep-links into the app. [type] is INCIDENT or ALERT and
     * [targetId] is the corresponding document id (plan section 22).
     */
    fun show(
        context: Context,
        title: String,
        body: String,
        channelId: String,
        type: String,
        targetId: String,
        workerId: String?,
    ) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEPLINK_TYPE, type)
            putExtra(EXTRA_DEEPLINK_ID, targetId)
            putExtra(EXTRA_DEEPLINK_WORKER, workerId)
        }
        val pending = PendingIntent.getActivity(
            context,
            targetId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(
                if (channelId == NotificationChannels.CRITICAL) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(targetId.hashCode(), notification)
        }
    }

    const val EXTRA_DEEPLINK_TYPE = "deeplink_type"
    const val EXTRA_DEEPLINK_ID = "deeplink_id"
    const val EXTRA_DEEPLINK_WORKER = "deeplink_worker"
}
