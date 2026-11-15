package com.jtexpress.bevest.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query as RtQuery
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.retryWhen

/**
 * Cold flows over Firebase listeners. The listener is always removed when the collector
 * stops (plan section 20 & 31.3) — no leaked listeners.
 *
 * Each stream is [conflate]d: only the latest snapshot matters, so a slow collector
 * should skip stale intermediates rather than let `trySend` silently drop them under a
 * burst of updates.
 */

fun Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { value, error ->
        when {
            error != null -> close(error)
            value != null -> trySend(value)
        }
    }
    awaitClose { registration.remove() }
}.conflate()

fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { value, error ->
        when {
            error != null -> close(error)
            value != null -> trySend(value)
        }
    }
    awaitClose { registration.remove() }
}.conflate()

fun RtQuery.valueEvents(): Flow<DataSnapshot> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            close(error.toException())
        }
    }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}.conflate()

/**
 * Keeps a listener alive through errors. A Firebase listener that errors is closed for
 * good, so without this one dropped connection or transient PERMISSION_DENIED (a token
 * refresh) would freeze a live screen — or silently stop the safety watcher — until the
 * app restarts. [onError] runs before each re-subscribe so callers can surface the
 * failure; the retry backs off from 1 s to 30 s.
 */
fun <T> Flow<T>.retryForever(
    onError: suspend FlowCollector<T>.(Throwable) -> Unit = {},
): Flow<T> = retryWhen { cause, attempt ->
    if (cause is CancellationException) return@retryWhen false
    onError(cause)
    delay(retryDelayMs(attempt))
    true
}

/** [retryForever] for repository streams: each failure is emitted as an [Outcome.Failure]. */
fun <T> Flow<Outcome<T>>.retryReporting(): Flow<Outcome<T>> =
    retryForever { emit(Outcome.Failure(it.toAppError())) }

internal fun retryDelayMs(attempt: Long): Long =
    (1_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30_000L)
