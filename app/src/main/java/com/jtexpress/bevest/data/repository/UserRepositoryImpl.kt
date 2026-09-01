package com.jtexpress.bevest.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.snapshots
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.data.mapper.toUser
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.utils.AppError
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserRepository {

    private val users get() = firestore.collection(FirebasePaths.USERS)

    private companion object {
        const val RETRY_LIMIT = 6L
        const val RETRY_DELAY_MS = 1_000L
    }

    override fun observeUser(uid: String): Flow<Outcome<User>> =
        users.document(uid).snapshots()
            .map { snap ->
                if (snap.exists()) Outcome.Success(snap.toUser())
                else Outcome.Failure(AppError.NotFound("Your user profile was not found."))
            }
            // A brand-new sign-up races the profile write: the auth user exists (so this
            // stream starts) a beat before its /users doc is committed and before the
            // Firestore client's auth token refreshes. That can make the snapshot listener
            // error out (PERMISSION_DENIED) and, because the listener closes the flow on
            // any error, the session would stay wedged on "Can't load your profile".
            // Re-subscribe a few times with backoff so it recovers on its own.
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                if (attempt < RETRY_LIMIT) { delay(RETRY_DELAY_MS); true } else false
            }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    override suspend fun getUser(uid: String): Outcome<User> = try {
        val snap = users.document(uid).get().await()
        if (snap.exists()) Outcome.Success(snap.toUser())
        else Outcome.Failure(AppError.NotFound("Your user profile was not found."))
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun upsertUser(user: User): Outcome<Unit> = try {
        users.document(user.uid).set(
            mapOf(
                "firstName" to user.firstName,
                "lastName" to user.lastName,
                "email" to user.email,
                "phone" to user.phone,
                "role" to user.role.name,
                "contractorId" to user.contractorId,
                "siteId" to user.siteId,
                "active" to user.active,
            ),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun setActive(uid: String, active: Boolean): Outcome<Unit> = try {
        users.document(uid).update("active", active).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override fun observeSsos(contractorId: String): Flow<Outcome<List<User>>> =
        users.whereEqualTo("role", UserRole.SSO.name)
            .whereEqualTo("contractorId", contractorId)
            .snapshots()
            .map { qs -> Outcome.Success(qs.documents.map { it.toUser() }) as Outcome<List<User>> }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    override fun observeContractors(): Flow<Outcome<List<User>>> =
        observeUsersByRole(UserRole.CONTRACTOR)

    override fun observeUsersByRole(role: UserRole): Flow<Outcome<List<User>>> =
        users.whereEqualTo("role", role.name)
            .snapshots()
            .map { qs -> Outcome.Success(qs.documents.map { it.toUser() }) as Outcome<List<User>> }
            .catch { emit(Outcome.Failure(it.toAppError())) }

    override suspend fun updateProfile(
        uid: String,
        firstName: String,
        lastName: String,
        phone: String,
    ): Outcome<Unit> = try {
        users.document(uid).update(
            mapOf("firstName" to firstName, "lastName" to lastName, "phone" to phone),
        ).await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun addFcmToken(uid: String, token: String): Outcome<Unit> = try {
        users.document(uid)
            .set(mapOf("fcmTokens" to com.google.firebase.firestore.FieldValue.arrayUnion(token)),
                com.google.firebase.firestore.SetOptions.merge())
            .await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }

    override suspend fun removeFcmToken(uid: String, token: String): Outcome<Unit> = try {
        users.document(uid)
            .update("fcmTokens", com.google.firebase.firestore.FieldValue.arrayRemove(token))
            .await()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Failure(e.toAppError())
    }
}
