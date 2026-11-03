package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.ViewModel
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.safety.FallPopupState
import com.jtexpress.bevest.domain.safety.SafetyWatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Gives the SSO area a handle on [SafetyWatcher] and stops it when the area is gone. */
@HiltViewModel
class SafetyWatcherHost @Inject constructor(
    private val watcher: SafetyWatcher,
) : ViewModel() {
    val fallPopup: StateFlow<FallPopupState?> = watcher.fallPopup

    fun start(siteId: String?) = watcher.start(siteId)
    fun stop() = watcher.stop()
    fun dismissFallPopup(alert: Alert) = watcher.dismissFallPopup(alert)
    fun acknowledgeFall(alert: Alert, byUserId: String) = watcher.acknowledgeFall(alert, byUserId)
    fun resolveFall(alert: Alert, byUserId: String) = watcher.resolveFall(alert, byUserId)

    override fun onCleared() = watcher.stop()
}
