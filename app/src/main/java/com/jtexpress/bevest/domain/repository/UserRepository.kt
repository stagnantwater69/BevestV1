package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeUser(uid: String): Flow<Outcome<User>>

    suspend fun getUser(uid: String): Outcome<User>

    suspend fun upsertUser(user: User): Outcome<Unit>

    suspend fun setActive(uid: String, active: Boolean): Outcome<Unit>

    /** Site Safety Officers under a contractor. */
    fun observeSsos(contractorId: String): Flow<Outcome<List<User>>>

    /** All contractors (admin only). */
    fun observeContractors(): Flow<Outcome<List<User>>>

    fun observeUsersByRole(role: com.jtexpress.bevest.domain.model.UserRole): Flow<Outcome<List<User>>>

    suspend fun updateProfile(uid: String, firstName: String, lastName: String, phone: String): Outcome<Unit>

    suspend fun addFcmToken(uid: String, token: String): Outcome<Unit>

    suspend fun removeFcmToken(uid: String, token: String): Outcome<Unit>
}
