package com.jtexpress.bevest.domain.model

import java.util.Calendar

/**
 * Domain models — clean Kotlin types used by the UI and business logic.
 * Firebase DTOs and their mappers live in the data layer, not here.
 */

fun calculateAgeFromBirthdate(birthdate: String?): Int? {
    val raw = birthdate?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parts = raw.split("-", "/")
    if (parts.size != 3) return null
    val (year, month, day) = if (parts[0].length == 4) {
        Triple(parts[0].toIntOrNull(), parts[1].toIntOrNull(), parts[2].toIntOrNull())
    } else if (parts[2].length == 4) {
        Triple(parts[2].toIntOrNull(), parts[0].toIntOrNull(), parts[1].toIntOrNull())
    } else {
        return null
    }
    if (year == null || month == null || day == null) return null
    if (year < 1900 || month !in 1..12 || day !in 1..31) return null

    val today = Calendar.getInstance()
    val currentYear = today.get(Calendar.YEAR)
    val currentMonth = today.get(Calendar.MONTH) + 1
    val currentDay = today.get(Calendar.DAY_OF_MONTH)

    var age = currentYear - year
    if (currentMonth < month || (currentMonth == month && currentDay < day)) {
        age -= 1
    }
    return age.takeIf { it in 0..120 }
}

data class User(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.UNKNOWN,
    val contractorId: String? = null,
    val siteId: String? = null,
    val active: Boolean = true,
    /** Contractors only; null means no plan chosen yet. */
    val subscription: Subscription? = null,
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

val WORKER_JOB_POSITIONS = listOf(
    "General Laborer",
    "Carpenter",
    "Electrician",
    "Mason",
    "Plumber",
    "Welder",
    "Steelman / Rebar Worker",
    "Painter",
    "Heavy Equipment Operator",
    "Scaffolder",
    "Foreman",
    "Site Engineer",
    "Safety Assistant",
    "Other",
)

data class Worker(
    val workerId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val birthday: String? = null,
    val age: Int? = null,
    val floorNumber: String? = null,
    val jobPosition: String? = null,
    val photoUrl: String? = null,
    val contractorId: String? = null,
    val siteId: String? = null,
    val assignedVestId: String? = null,
    val currentStatus: SafetyStatus = SafetyStatus.OFFLINE,
    val active: Boolean = true,
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    val effectiveAge: Int? get() = age ?: calculateAgeFromBirthdate(birthday)
}

data class Vest(
    val vestId: String = "",
    val assignedWorkerId: String? = null,
    val status: VestStatus = VestStatus.AVAILABLE,
    val batteryPercent: Int? = null,
    val online: Boolean = false,
    val lastSeen: Long? = null,
    val inCompanyDirectory: Boolean = false,
    val companyId: String? = null,
    val siteId: String? = null,
    val macAddress: String? = null,
    val serialNumber: String? = null,
    val wifiSsid: String? = null,
    val wifiRssi: Int? = null,
)

data class SensorReading(
    val workerId: String = "",
    val vestId: String = "",
    val heartRate: Int? = null,
    val temperature: Double? = null,
    val motionState: MotionState = MotionState.UNKNOWN,
    val fallDetected: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val batteryPercent: Int? = null,
    val wifiRssi: Int? = null,
    val safetyResponse: SafetyResponseState = SafetyResponseState.NONE,
    val timestamp: Long = 0L,
)

data class VitalLogEntry(
    val timestamp: Long = 0L,
    val heartRate: Int? = null,
    val temperature: Double? = null,
)

data class Alert(
    val alertId: String = "",
    val workerId: String = "",
    val vestId: String = "",
    val siteId: String? = null,
    val siteName: String? = null,
    val floorNumber: String? = null,
    val type: AlertType = AlertType.VEST_OFFLINE,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val status: AlertStatus = AlertStatus.ACTIVE,
    val message: String = "",
    val createdAt: Long = 0L,
    val acknowledgedAt: Long? = null,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null,
    val resolutionNotes: String? = null,
)

data class Incident(
    val incidentId: String = "",
    val workerId: String = "",
    val vestId: String = "",
    val alertId: String? = null,
    val siteId: String? = null,
    val siteName: String? = null,
    val floorNumber: String? = null,
    val type: AlertType = AlertType.VEST_OFFLINE,
    val severity: AlertSeverity = AlertSeverity.DANGER,
    val heartRate: Int? = null,
    val temperature: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = 0L,
    val outcome: String? = null,
    val resolutionNotes: String? = null,
    val resolvedBy: String? = null,
)

data class ProjectSite(
    val projectId: String = "",
    val contractorId: String = "",
    val name: String = "",
    val location: String = "",
    val active: Boolean = true,
)

data class VestAssignment(
    val assignmentId: String = "",
    val workerId: String = "",
    val vestId: String = "",
    val assignedAt: Long = 0L,
    val unassignedAt: Long? = null,
    val assignedBy: String = "",
)
