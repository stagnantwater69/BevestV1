package com.jtexpress.bevest.notifications

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DeepLink(val type: String, val targetId: String, val workerId: String?)

/**
 * Carries a notification tap from MainActivity's intent into the navigation layer.
 *
 * A channel, not a replaying flow: a tap posted before the UI is ready (cold start) waits
 * for it, and once handled it is gone — rotating the screen or the next person signing in
 * on this phone doesn't open it again.
 */
@Singleton
class DeepLinkBus @Inject constructor() {
    private val channel = Channel<DeepLink>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<DeepLink> = channel.receiveAsFlow()

    fun post(link: DeepLink) {
        channel.trySend(link)
    }

    /** Drops taps nobody has handled yet, e.g. on sign-out. */
    fun clear() {
        while (channel.tryReceive().isSuccess) Unit
    }
}
