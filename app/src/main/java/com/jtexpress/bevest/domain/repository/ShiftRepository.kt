package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.model.Shift
import com.jtexpress.bevest.utils.Outcome
import kotlinx.coroutines.flow.Flow

interface ShiftRepository {
    /** The site's shift for [date] (yyyy-MM-dd), or null if nobody has started it. */
    fun observeShift(siteId: String, date: String): Flow<Shift?>

    /**
     * Records the start-of-shift check. The first save starts the shift; later saves on
     * the same day update the result of the check but keep who started it and when.
     */
    suspend fun saveCheck(
        siteId: String,
        contractorId: String?,
        date: String,
        byUserId: String,
        byName: String,
        workerCount: Int,
        readyCount: Int,
        issues: List<String>,
    ): Outcome<Unit>
}
