package com.jtexpress.bevest.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jtexpress.bevest.MainActivity
import com.jtexpress.bevest.R
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.safety.SafetyWatcher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the phone-side safety engine ([SafetyWatcher]) running while a Site Safety
 * Officer is signed in — with the app in the background, the screen off or the app
 * swiped away — as a foreground service with a quiet "Monitoring" notification.
 *
 * On the free Firebase plan there are no Cloud Functions, so this is what makes falls
 * and SOS presses raise alarms when nobody is looking at the app. It still needs at
 * least one officer on the site signed in; with the Cloud Functions deployed (engine
 * mode "cloud") it only keeps the alerts arriving promptly.
 */
@AndroidEntryPoint
class MonitoringService : Service() {

    @Inject lateinit var safetyWatcher: SafetyWatcher
    @Inject lateinit var authRepository: AuthRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSite(this)
            safetyWatcher.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // A sticky restart after the process was killed comes with no intent: resume
        // the last site, but only if the officer is still signed in.
        val siteId = intent?.getStringExtra(EXTRA_SITE) ?: lastSite(this)
        if (siteId.isNullOrBlank() || authRepository.currentUserId() == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        rememberSite(this, siteId)
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(siteId), serviceType())
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        safetyWatcher.start(siteId)
        return START_STICKY
    }

    private fun serviceType(): Int =
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun notification(siteId: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Safety monitoring", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while BeVest watches your site for falls and emergencies"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bevest)
            .setContentTitle("Monitoring $siteId")
            .setContentText("Alarms will sound for falls and emergencies on your site.")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.jtexpress.bevest.monitoring.STOP"
        private const val EXTRA_SITE = "site"
        private const val CHANNEL = "monitoring"
        private const val NOTIFICATION_ID = 0x5AFE
        private const val PREFS = "monitoring"
        private const val KEY_SITE = "site"

        /** Starts (or retargets) monitoring. Call while the app is in the foreground. */
        fun start(context: Context, siteId: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MonitoringService::class.java).putExtra(EXTRA_SITE, siteId),
                )
            }
        }

        fun stop(context: Context) {
            stopSite(context)
            runCatching {
                context.startService(Intent(context, MonitoringService::class.java).setAction(ACTION_STOP))
            }
        }

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun lastSite(context: Context) = prefs(context).getString(KEY_SITE, null)
        private fun rememberSite(context: Context, siteId: String) =
            prefs(context).edit().putString(KEY_SITE, siteId).apply()
        private fun stopSite(context: Context) = prefs(context).edit().remove(KEY_SITE).apply()
    }
}
