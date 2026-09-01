package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

interface WorkerRepository {
    /** Workers visible to a scope. Pass siteId for an SSO, contractorId for a contractor. */
    fun observeWorkers(contractorId: String?, siteId: String?): Flow<Outcome<List<Worker>>>

    fun observeWorker(workerId: String): Flow<Outcome<Worker>>

    suspend fun getWorker(workerId: String): Outcome<Worker>

    suspend fun addWorker(worker: Worker): Outcome<Unit>

    suspend fun updateWorker(worker: Worker): Outcome<Unit>

    suspend fun setActive(workerId: String, active: Boolean): Outcome<Unit>

    suspend fun setCurrentStatus(workerId: String, status: SafetyStatus): Outcome<Unit>

    suspend fun setAssignedVest(workerId: String, vestId: String?): Outcome<Unit>

    suspend fun uploadPhoto(workerId: String, bytes: ByteArray): Outcome<String>
}
