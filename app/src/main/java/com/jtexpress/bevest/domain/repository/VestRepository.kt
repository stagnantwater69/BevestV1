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

    /**
     * Claims a vest into the company directory after BLE + QR validation.
     */
    suspend fun claimVest(
        vestId: String,
        serialNumber: String,
        macAddress: String,
        claimedByUid: String,
        claimedByName: String,
        claimedByEmail: String,
        companyId: String?,
        siteId: String?,
    ): Outcome<Unit>

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

    /**
     * Option 1 (Hybrid Cloud + BLE): Remotely sends new Wi-Fi credentials to an online vest via RTDB
     * so it can switch networks immediately or pre-save credentials for the next construction site.
     */
    suspend fun updateVestWifiRemotely(vestId: String, ssid: String, pass: String): Outcome<Unit>

    /**
     * Remotely instructs an online vest to disconnect from its current Wi-Fi and enter BLE Pairing Mode.
     */
    suspend fun triggerVestBleModeRemotely(vestId: String): Outcome<Unit>
}
