package com.jtexpress.bevest.ui.sso

import android.content.Context
import androidx.lifecycle.ViewModel
import com.jtexpress.bevest.domain.safety.SafetyWatcher
import com.jtexpress.bevest.notifications.MonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Starts site monitoring for the signed-in SSO. Monitoring deliberately outlives this
 * screen — [MonitoringService] keeps it running in the background — and only signing
 * out stops it ([com.jtexpress.bevest.navigation.SessionManager]).
 */
@HiltViewModel
class SafetyWatcherHost @Inject constructor(
    private val watcher: SafetyWatcher,
) : ViewModel() {
    fun start(context: Context, siteId: String?) {
        if (siteId.isNullOrBlank()) return
        // Directly too, in case the service can't start on this device right now.
        watcher.start(siteId)
        MonitoringService.start(context.applicationContext, siteId)
    }
}
