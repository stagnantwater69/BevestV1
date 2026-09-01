package com.jtexpress.bevest.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override val authState: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun currentUserId(): String? = auth.currentUser?.uid

    override suspend fun signIn(email: String, password: String): Outcome<String> = try {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user?.uid
        if (uid != null) Outcome.Success(uid)
        else Outcome.Failure(com.jtexpress.bevest.utils.AppError.Unknown())
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override fun currentUserEmail(): String? = auth.currentUser?.email

    override suspend fun changePassword(currentPassword: String, newPassword: String): Outcome<Unit> = try {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) {
            Outcome.Failure(com.jtexpress.bevest.utils.AppError.NotAuthorized("You are not signed in."))
        } else {
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            Outcome.Success(Unit)
        }
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override fun signOut() = auth.signOut()
}
