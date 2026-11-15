package com.jtexpress.bevest.utils

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The time according to Firebase's servers, not this phone.
 *
 * Vest readings are stamped by the vest (NTP) and alerts by whichever phone opened them,
 * so comparing either against a phone clock that is a minute off would mark live vests
 * offline or escalate a fall early. Realtime Database publishes this device's offset
 * from server time at `.info/serverTimeOffset`; everything that ages a reading or an
 * alert goes through [now].
 */
@Singleton
class ServerClock @Inject constructor(database: FirebaseDatabase) {

    @Volatile
    private var offsetMs = 0L

    /** When this phone last connected to the database; null while disconnected. */
    @Volatile
    private var connectedAt: Long? = null

    init {
        database.getReference(".info/connected").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) == true
                    connectedAt = if (connected) connectedAt ?: System.currentTimeMillis() else null
                }

                override fun onCancelled(error: DatabaseError) = Unit
            },
        )
        database.getReference(".info/serverTimeOffset").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    offsetMs = (snapshot.getValue(Double::class.java) ?: 0.0).toLong()
                }

                override fun onCancelled(error: DatabaseError) = Unit
            },
        )
    }

    fun now(): Long = System.currentTimeMillis() + offsetMs

    /**
     * How long this phone has been connected, or 0 while it isn't. Until it has been
     * online for a while its cached readings are stale through no fault of the vests.
     */
    fun connectedForMs(): Long = connectedAt?.let { System.currentTimeMillis() - it } ?: 0L

    /**
     * Emits [now] every [periodMs]. Live screens combine this in so a vest that stops
     * transmitting turns OFFLINE on its own, instead of waiting for some other update.
     */
    fun ticker(periodMs: Long = TICK_MS): Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(periodMs)
        }
    }

    companion object {
        const val TICK_MS = 5_000L
    }
}
