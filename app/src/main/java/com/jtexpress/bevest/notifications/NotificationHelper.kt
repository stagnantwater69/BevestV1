package com.jtexpress.bevest.notifications

import android.app.Notification
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
    /**
     * Site Safety Officers only: full-screen alert, like an NDRRMC emergency alert. The
     * channel itself is silent — [AlarmSoundService] vibrates on a loop until someone
     * stops it.
     */
    const val EMERGENCY = "emergency_alarm"
    /**
     * Used only when Android won't let [AlarmSoundService] start: vibration rides on the
     * notification instead, repeating until it's dealt with (no placeholder sound).
     */
    const val EMERGENCY_FALLBACK = "emergency_alarm_vibrate"
    /** Retired channels that previously had placeholder sound attached. */
    internal const val RETIRED_EMERGENCY = "critical_alarm"
    internal const val RETIRED_EMERGENCY_SOUND = "emergency_alarm_sound"
    internal const val RETIRED_CRITICAL = "critical"
    /** Contractors: a high-priority vibration-only notification for danger on their sites. */
    const val CRITICAL = "critical_vibrate"
    const val WARNING = "warning"
    const val MAINTENANCE = "maintenance"
}

object NotificationHelper {

    /** SOS-style: three short, three long, three short, then a pause. Loops while the alarm is active. */
    internal val ALARM_VIBRATION = longArrayOf(
        0, 250, 150, 250, 150, 250, 300,
        700, 200, 700, 200, 700, 300,
        250, 150, 250, 150, 250, 1000,
    )

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // A channel's sound can't change once created, so delete any retired sound channels.
        manager.deleteNotificationChannel(NotificationChannels.RETIRED_EMERGENCY)
        manager.deleteNotificationChannel(NotificationChannels.RETIRED_EMERGENCY_SOUND)
        manager.deleteNotificationChannel(NotificationChannels.RETIRED_CRITICAL)
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.EMERGENCY,
                "Emergency alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Full-screen alert with looping vibration for falls, no safety " +
                    "response and emergencies"
                // AlarmSoundService vibrates; the channel only shows the alert with no sound.
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = ALARM_VIBRATION
                // Only honoured once the user allows it; AlarmReadiness walks them there.
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.EMERGENCY_FALLBACK,
                "Emergency alarms (backup vibration)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Used if the looping vibration service can't start"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = ALARM_VIBRATION
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.CRITICAL,
                "Safety alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Falls and emergencies on your sites (vibration only)"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = ALARM_VIBRATION
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.WARNING,
                "Warnings",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "High heart rate, high temperature, inactivity, vest offline"
                setSound(null, null)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.MAINTENANCE,
                "Maintenance",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Low battery, device service" },
        )
    }

    fun notificationId(targetId: String): Int = targetId.hashCode()

    /**
     * Shows a notification that deep-links into the app. [type] is INCIDENT or ALERT and
     * [targetId] is the corresponding document id (plan section 22). On the
     * [NotificationChannels.EMERGENCY] channel the vibration loops until someone stops it.
     */
    fun show(
        context: Context,
        title: String,
        body: String,
        channelId: String,
        type: String,
        targetId: String,
        workerId: String?,
        silent: Boolean = false,
    ) {
        ensureChannels(context)
        val alarm = channelId == NotificationChannels.EMERGENCY
        val id = notificationId(targetId)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putDeepLink(type, targetId, workerId)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (channelId == NotificationChannels.CRITICAL || alarm) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setSound(null)
            .setVibrate(if (silent) longArrayOf(0L) else ALARM_VIBRATION)
            .setSilent(silent)

        if (alarm) {
            // When the phone is unlocked Android shows a pop-up instead of the full
            // screen, so the pop-up carries the same two choices.
            val stop = Intent(context, AlarmStopReceiver::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
                putDeepLink(type, targetId, workerId)
            }
            // Swiping the alert away stops the alarm too, rather than leaving it
            // vibrating with nothing on screen to stop it.
            val swiped = Intent(stop).putExtra(EXTRA_SWIPED, true)
            builder
                .addAction(0, "Respond", pending)
                .addAction(
                    0,
                    "Stop alarm",
                    PendingIntent.getBroadcast(
                        context,
                        id,
                        stop,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            builder
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(
                    PendingIntent.getBroadcast(
                        context,
                        id + 1,
                        swiped,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .setOngoing(true)
                .setAutoCancel(false)

            // The looping vibration service. If Android won't start it right now, the
            // notification carries the vibration itself instead.
            if (AlarmSoundService.start(context, id, builder.build())) return
            builder
                .setChannelId(NotificationChannels.EMERGENCY_FALLBACK)
                .setSound(null)
                .setVibrate(ALARM_VIBRATION)
            val fallback = builder.build()
            fallback.flags = fallback.flags or Notification.FLAG_INSISTENT
            runCatching { NotificationManagerCompat.from(context).notify(id, fallback) }
            return
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }

    /**
     * Stops the vibration but keeps the alert in the notification shade, so
     * the officer can still open it once they've silenced the phone.
     */
    fun silence(
        context: Context,
        title: String,
        body: String,
        type: String,
        targetId: String,
        workerId: String?,
    ) {
        AlarmSoundService.stop(context, notificationId(targetId), keepNotification = true)
        show(
            context = context,
            title = title,
            body = body,
            channelId = NotificationChannels.CRITICAL,
            type = type,
            targetId = targetId,
            workerId = workerId,
            silent = true,
        )
    }

    /** Stops the alarm and removes an alert's notification, e.g. once someone has responded. */
    fun cancel(context: Context, targetId: String) = cancel(context, notificationId(targetId))

    fun cancel(context: Context, notificationId: Int) {
        AlarmSoundService.stop(context, notificationId, keepNotification = false)
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    internal fun Intent.putDeepLink(type: String, targetId: String, workerId: String?): Intent = apply {
        putExtra(EXTRA_DEEPLINK_TYPE, type)
        putExtra(EXTRA_DEEPLINK_ID, targetId)
        putExtra(EXTRA_DEEPLINK_WORKER, workerId)
    }

    const val EXTRA_TITLE = "alert_title"
    const val EXTRA_BODY = "alert_body"
    const val EXTRA_SWIPED = "alert_swiped"
    const val EXTRA_DEEPLINK_TYPE = "deeplink_type"
    const val EXTRA_DEEPLINK_ID = "deeplink_id"
    const val EXTRA_DEEPLINK_WORKER = "deeplink_worker"
}
