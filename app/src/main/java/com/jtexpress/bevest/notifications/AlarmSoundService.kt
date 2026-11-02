package com.jtexpress.bevest.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jtexpress.bevest.R

/**
 * Plays the emergency alarm on a loop until someone stops it.
 *
 * A notification's own sound plays once, and even an "insistent" one stops the moment
 * the notification shade is pulled down. This plays `res/raw/bevest_alarm.mp3` on the
 * alarm stream (not muted by silent mode), looping, with the vibration pattern looping
 * alongside, and keeps going until every alarm it was started for has been stopped —
 * by Respond, Stop alarm, a volume key, swiping the alert away, or an officer
 * acknowledging the alert.
 *
 * The alarm's notification is the service's foreground notification, so Android keeps
 * the process (and the sound) alive while it's showing.
 */
class AlarmSoundService : Service() {

    /** The alarms sounding, by notification id, newest last. */
    private val alarms = LinkedHashMap<Int, Notification>()
    private var foregroundId: Int? = null
    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getIntExtra(EXTRA_ID, 0)
                @Suppress("DEPRECATION")
                val notification = intent.getParcelableExtra<Notification>(EXTRA_NOTIFICATION)
                if (notification == null) {
                    if (alarms.isEmpty()) stopSelf()
                    return START_NOT_STICKY
                }
                alarms[id] = notification
                // Every startForegroundService() must be answered with startForeground();
                // the newest alarm becomes the foreground one, earlier ones stay posted.
                promote(id, notification)
                startSounding()
            }
            ACTION_STOP -> {
                val keep = intent.getBooleanExtra(EXTRA_KEEP, false)
                if (intent.hasExtra(EXTRA_ID)) {
                    remove(intent.getIntExtra(EXTRA_ID, 0), keep)
                } else {
                    alarms.keys.toList().forEach { remove(it, keep) }
                }
            }
        }
        // Nothing sounding (a stop for an alarm that already ended): don't linger.
        if (alarms.isEmpty() && foregroundId == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun promote(id: Int, notification: Notification) {
        ServiceCompat.startForeground(
            this,
            id,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        foregroundId = id
    }

    private fun remove(id: Int, keepNotification: Boolean) {
        if (alarms.remove(id) == null) return
        val manager = getSystemService(NotificationManager::class.java)
        if (alarms.isEmpty()) {
            stopSounding()
            ServiceCompat.stopForeground(
                this,
                if (keepNotification) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE,
            )
            foregroundId = null
            if (!keepNotification) manager?.cancel(id)
            stopSelf()
            return
        }
        // Others still sounding: hand the foreground role to the newest of them.
        if (id == foregroundId) {
            val (nextId, next) = alarms.entries.last().toPair()
            promote(nextId, next)
        }
        if (!keepNotification) manager?.cancel(id)
    }

    private fun startSounding() {
        if (player != null) return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(ALARM_AUDIO)
                setDataSource(this@AlarmSoundService, Uri.parse("android.resource://$packageName/${R.raw.bevest_alarm}"))
                isLooping = true
                setWakeMode(this@AlarmSoundService, PowerManager.PARTIAL_WAKE_LOCK)
                prepare()
                start()
            }
        }.getOrNull()
        vibrator()?.let { v ->
            @Suppress("DEPRECATION")
            v.vibrate(VibrationEffect.createWaveform(NotificationHelper.ALARM_VIBRATION, 0), ALARM_AUDIO)
        }
    }

    private fun stopSounding() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator()?.cancel()
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    override fun onDestroy() {
        running = false
        stopSounding()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.jtexpress.bevest.alarm.START"
        private const val ACTION_STOP = "com.jtexpress.bevest.alarm.STOP"
        private const val EXTRA_ID = "id"
        private const val EXTRA_NOTIFICATION = "notification"
        private const val EXTRA_KEEP = "keep"

        /** Whether an alarm is sounding, so stopping one that isn't costs nothing. */
        @Volatile
        private var running = false

        private val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /**
         * Starts the looping alarm with [notification] shown. Returns false when Android
         * won't let a foreground service start right now (the app is in the background
         * without an exemption) — the caller then falls back to a sounding notification.
         */
        fun start(context: Context, id: Int, notification: Notification): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmSoundService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_NOTIFICATION, notification),
            )
        }.isSuccess

        /**
         * Stops the alarm for notification [id], or every alarm when null. With
         * [keepNotification] the alert stays in the shade (the caller re-posts it quietly).
         */
        fun stop(context: Context, id: Int?, keepNotification: Boolean) {
            if (!running) return
            val intent = Intent(context, AlarmSoundService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_KEEP, keepNotification)
            if (id != null) intent.putExtra(EXTRA_ID, id)
            // A plain start: the service is already in the foreground if it's sounding,
            // and if it isn't there's nothing to stop.
            runCatching { context.startService(intent) }
        }
    }
}
