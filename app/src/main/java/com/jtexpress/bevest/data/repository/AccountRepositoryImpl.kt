package com.jtexpress.bevest.data.repository

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.data.firebase.FirebasePaths
import com.jtexpress.bevest.data.firebase.toAppError
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.domain.repository.AccountRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
) : AccountRepository {

    override suspend fun createUser(
        email: String,
        tempPassword: String,
        firstName: String,
        lastName: String,
        phone: String,
        role: UserRole,
        contractorId: String?,
        siteId: String?,
    ): Outcome<String> {
        val name = "account-creator"
        val primary = FirebaseApp.getInstance()
        val options: FirebaseOptions = primary.options
        val secondary = try {
            FirebaseApp.getInstance(name)
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(context, options, name)
        }
        val secondaryAuth = FirebaseAuth.getInstance(secondary)
        return try {
            val result = secondaryAuth.createUserWithEmailAndPassword(email.trim(), tempPassword).await()
            val uid = result.user?.uid ?: return Outcome.Failure(com.jtexpress.bevest.utils.AppError.Unknown())
            // A contractor's own uid is their contractorId (see firestore.rules).
            val resolvedContractorId =
                if (role == UserRole.CONTRACTOR) uid else contractorId
            firestore.collection(FirebasePaths.USERS).document(uid).set(
                mapOf(
                    "firstName" to firstName.trim(),
                    "lastName" to lastName.trim(),
                    "email" to email.trim(),
                    "phone" to phone.trim(),
                    "role" to role.name,
                    "contractorId" to resolvedContractorId,
                    "siteId" to siteId,
                    "active" to true,
                ),
            ).await()
            secondaryAuth.signOut()
            Outcome.Success(uid)
        } catch (e: Exception) {
            Outcome.Failure(e.toAppError())
        } finally {
            runCatching { secondary.delete() }
        }
    }
}
