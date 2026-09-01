package com.jtexpress.bevest.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query as RtQuery
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

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
