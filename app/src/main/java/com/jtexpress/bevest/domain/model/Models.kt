package com.jtexpress.bevest.domain.model

/**
 * Domain models — clean Kotlin types used by the UI and business logic.
 * Firebase DTOs and their mappers live in the data layer, not here.
 */

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
    /** Signed up with a temporary password that hasn't been replaced yet. */
    val mustChangePassword: Boolean = false,
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

data class Worker(
    val workerId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val photoUrl: String? = null,
    val contractorId: String? = null,
    val siteId: String? = null,
    val assignedVestId: String? = null,
    val currentStatus: SafetyStatus = SafetyStatus.OFFLINE,
    val active: Boolean = true,
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

data class Vest(
    val vestId: String = "",
    val assignedWorkerId: String? = null,
    val status: VestStatus = VestStatus.AVAILABLE,
    val batteryPercent: Int? = null,
    val online: Boolean = false,
    val lastSeen: Long? = null,
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
    val safetyResponse: SafetyResponseState = SafetyResponseState.NONE,
    val timestamp: Long = 0L,
    /** Written by the in-app simulator, which raises and escalates its own alerts. */
    val simulated: Boolean = false,
)

data class Alert(
    val alertId: String = "",
    val workerId: String = "",
    val vestId: String = "",
    val type: AlertType = AlertType.VEST_OFFLINE,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val status: AlertStatus = AlertStatus.ACTIVE,
    val message: String = "",
    val createdAt: Long = 0L,
    val acknowledgedAt: Long? = null,
    /** The officer who said they're responding: uid, and their name at that moment. */
    val acknowledgedBy: String? = null,
    val acknowledgedByName: String? = null,
    /** When the engine flagged that no officer had responded in time, if it has. */
    val unansweredAt: Long? = null,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null,
    val siteId: String? = null,
    val incidentId: String? = null,
    val simulated: Boolean = false,
    /** Opened by the safety engine for a condition that can clear on its own (vest offline, a warning). */
    val autoResolve: Boolean = false,
)

data class Incident(
    val incidentId: String = "",
    val workerId: String = "",
    val vestId: String = "",
    val alertId: String? = null,
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

/** A site's working day, opened by the start-of-shift check. One per site per date. */
data class Shift(
    val siteId: String = "",
    val date: String = "",
    val startedAt: Long? = null,
    val startedBy: String = "",
    val startedByName: String = "",
    /** The latest run of the check (the first run is the one that started the shift). */
    val checkedAt: Long? = null,
    val workerCount: Int = 0,
    val readyCount: Int = 0,
    val issues: List<String> = emptyList(),
)

data class VestAssignment(
    val assignmentId: String = "",
    val workerId: String = "",
    val vestId: String = "",
    val assignedAt: Long = 0L,
    val unassignedAt: Long? = null,
    val assignedBy: String = "",
)
