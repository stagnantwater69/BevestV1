package com.jtexpress.bevest.notifications

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jtexpress.bevest.MainActivity
import com.jtexpress.bevest.notifications.NotificationHelper.putDeepLink
import com.jtexpress.bevest.ui.theme.BevestTheme
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * The full-screen alert an emergency notification opens over the lock screen, like an
 * incoming call. Respond opens the alert; Stop alarm or either volume button silences
 * the siren and vibration but keeps the alert in the notification shade.
 */
class EmergencyAlertActivity : ComponentActivity() {

    private val alert = mutableStateOf(AlertContent())
    private val silenced = mutableStateOf(false)

    private data class AlertContent(
        val title: String = "⚠ EMERGENCY",
        val body: String = "",
        val notificationId: Int = 0,
        val type: String? = null,
        val targetId: String? = null,
        val workerId: String? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        alert.value = read(intent)

        setContent {
            BevestTheme {
                val content = alert.value
                val red = LocalStatusPalette.current.emergency
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(red)
                        .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    Text(
                        content.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        content.body,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    if (silenced.value) {
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            "Alarm silenced",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(Spacing.xxl))
                    Button(
                        onClick = ::respond,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = red),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Text("Respond", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(Spacing.md))
                    TextButton(onClick = ::stopAlarm, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Stop alarm", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Text(
                        "Volume buttons also silence the alarm",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }

    /** A second emergency while this one is showing replaces it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alert.value = read(intent)
        silenced.value = false
    }

    /** Like an NDRRMC alert: a volume press silences it without dismissing anything. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            silence()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun read(intent: Intent) = AlertContent(
        title = intent.getStringExtra(EXTRA_TITLE) ?: "⚠ EMERGENCY",
        body = intent.getStringExtra(EXTRA_BODY).orEmpty(),
        notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
        type = intent.getStringExtra(NotificationHelper.EXTRA_DEEPLINK_TYPE),
        targetId = intent.getStringExtra(NotificationHelper.EXTRA_DEEPLINK_ID),
        workerId = intent.getStringExtra(NotificationHelper.EXTRA_DEEPLINK_WORKER),
    )

    /** Stops the siren and vibration; the alert stays in the shade, quiet. */
    private fun silence() {
        val content = alert.value
        if (content.targetId != null) {
            NotificationHelper.silence(
                context = this,
                title = content.title,
                body = content.body,
                type = content.type ?: "ALERT",
                targetId = content.targetId,
                workerId = content.workerId,
            )
        } else {
            NotificationHelper.cancel(this, content.notificationId)
        }
        silenced.value = true
    }

    private fun respond() {
        // Opening the alert is the response, so the notification goes entirely.
        NotificationHelper.cancel(this, alert.value.notificationId)
        val content = alert.value
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (content.type != null && content.targetId != null) {
                putDeepLink(content.type, content.targetId, content.workerId)
            }
        }
        // The app itself needs the phone unlocked; ask for it, then open the alert.
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard != null && keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() { startActivity(open); finish() }
                },
            )
        } else {
            startActivity(open)
            finish()
        }
    }

    private fun stopAlarm() {
        silence()
        finish()
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_TITLE = "emergency_title"
        const val EXTRA_BODY = "emergency_body"
        const val EXTRA_NOTIFICATION_ID = "emergency_notification_id"
    }
}
