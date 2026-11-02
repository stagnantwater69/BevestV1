package com.jtexpress.bevest.ui.sso

import androidx.lifecycle.ViewModel
import com.jtexpress.bevest.domain.safety.SafetyWatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Gives the SSO area a handle on [SafetyWatcher] and stops it when the area is gone. */
@HiltViewModel
class SafetyWatcherHost @Inject constructor(
    private val watcher: SafetyWatcher,
) : ViewModel() {
    fun start(siteId: String?) = watcher.start(siteId)
    fun stop() = watcher.stop()
    override fun onCleared() = watcher.stop()
}
