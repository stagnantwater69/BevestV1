package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

data class ActivityEntry(
    val id: String = "",
    val message: String = "",
    val actorId: String = "",
    val createdAt: Long = 0L,
    val organizationId: String? = null,
)

interface SystemRepository {
    fun observeActivity(limit: Long = 50): Flow<Outcome<List<ActivityEntry>>>

    suspend fun logActivity(message: String, actorId: String, organizationId: String? = null): Outcome<Unit>
}
