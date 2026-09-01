package com.jtexpress.bevest.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DeepLink(val type: String, val targetId: String, val workerId: String?)

/** Carries a notification tap from MainActivity's intent into the navigation layer. */
@Singleton
class DeepLinkBus @Inject constructor() {
    private val _events = MutableSharedFlow<DeepLink>(replay = 1, extraBufferCapacity = 4)
    val events: SharedFlow<DeepLink> = _events.asSharedFlow()

    fun post(link: DeepLink) {
        _events.tryEmit(link)
    }

    fun clear() {
        _events.resetReplayCache()
    }
}
