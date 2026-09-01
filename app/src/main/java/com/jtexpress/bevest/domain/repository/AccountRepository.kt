package com.jtexpress.bevest.domain.repository

import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.utils.Outcome

/**
 * Creates authorized user accounts (Contractor creates SSOs, Admin creates Contractors).
 * For the prototype this uses a secondary Firebase app so the caller stays signed in.
 * A production build should move this to a Cloud Function with the Admin SDK.
 */
interface AccountRepository {
    suspend fun createUser(
        email: String,
        tempPassword: String,
        firstName: String,
        lastName: String,
        phone: String,
        role: UserRole,
        contractorId: String?,
        siteId: String?,
    ): Outcome<String>
}
