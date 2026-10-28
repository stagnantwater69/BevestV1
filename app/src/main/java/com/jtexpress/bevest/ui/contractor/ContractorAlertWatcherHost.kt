package com.jtexpress.bevest.ui.contractor

import androidx.lifecycle.ViewModel
import com.jtexpress.bevest.domain.safety.ContractorAlertWatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Gives the contractor area a handle on [ContractorAlertWatcher] and stops it when the area is gone. */
@HiltViewModel
class ContractorAlertWatcherHost @Inject constructor(
    private val watcher: ContractorAlertWatcher,
) : ViewModel() {
    fun start(contractorId: String?) = watcher.start(contractorId)
    fun stop() = watcher.stop()
    override fun onCleared() = watcher.stop()
}
