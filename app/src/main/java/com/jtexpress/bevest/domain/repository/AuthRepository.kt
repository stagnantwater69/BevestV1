package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Emits the signed-in user id, or null when signed out. */
    val authState: Flow<String?>

    fun currentUserId(): String?

    suspend fun signIn(email: String, password: String): Outcome<String>

    suspend fun sendPasswordReset(email: String): Outcome<Unit>

    fun currentUserEmail(): String?

    suspend fun changePassword(currentPassword: String, newPassword: String): Outcome<Unit>

    fun signOut()
}
