package com.jtexpress.bevest

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.jtexpress.bevest.navigation.AppRoot
import com.jtexpress.bevest.notifications.DeepLink
import com.jtexpress.bevest.notifications.DeepLinkBus
import com.jtexpress.bevest.notifications.NotificationHelper
import com.jtexpress.bevest.ui.theme.BevestTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var deepLinkBus: DeepLinkBus

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        NotificationHelper.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleDeepLink(intent)

        setContent {
            BevestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val type = intent?.getStringExtra(NotificationHelper.EXTRA_DEEPLINK_TYPE) ?: return
        val id = intent.getStringExtra(NotificationHelper.EXTRA_DEEPLINK_ID) ?: return
        val worker = intent.getStringExtra(NotificationHelper.EXTRA_DEEPLINK_WORKER)
        deepLinkBus.post(DeepLink(type, id, worker))
    }
}
