package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestAssignment
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

interface VestRepository {
    fun observeVests(): Flow<Outcome<List<Vest>>>

    fun observeVest(vestId: String): Flow<Outcome<Vest>>

    suspend fun getVest(vestId: String): Outcome<Vest>

    suspend fun registerVest(vestId: String): Outcome<Unit>

    suspend fun setStatus(vestId: String, status: VestStatus): Outcome<Unit>

    /**
     * Pairs a vest to a worker. Enforces one active vest per worker and one worker per vest
     * (plan section 31.5 & 31.6). Writes a [VestAssignment] history record.
     */
    suspend fun assignVest(vestId: String, workerId: String, assignedBy: String): Outcome<Unit>

    suspend fun unassignVest(vestId: String, unassignedBy: String): Outcome<Unit>

    /** Unassign [oldVestId], assign [newVestId] to the same worker, keep incident history. */
    suspend fun replaceVest(oldVestId: String, newVestId: String, assignedBy: String): Outcome<Unit>

    fun observeAssignments(vestId: String): Flow<Outcome<List<VestAssignment>>>
}
