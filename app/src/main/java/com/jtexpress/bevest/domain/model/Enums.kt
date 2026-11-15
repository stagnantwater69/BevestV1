package com.jtexpress.bevest.domain.model

/** Authorized mobile roles. Workers and foremen are monitored via the vest, not an account. */
enum class UserRole {
    ADMIN,
    CONTRACTOR,
    SSO,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

enum class SafetyStatus {
    NORMAL,
    WARNING,
    DANGER,
    EMERGENCY,
    OFFLINE;

    /**
     * How urgent this status is, for sorting and "worst on site". Never use [ordinal]
     * for that: OFFLINE sits last in the enum but is not more severe than an open danger.
     */
    val severity: Int
        get() = when (this) {
            NORMAL -> 0
            OFFLINE -> 1
            WARNING -> 2
            DANGER -> 3
            EMERGENCY -> 4
        }

    companion object {
        fun fromRaw(raw: String?): SafetyStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OFFLINE
    }
}

enum class MotionState {
    MOVING,
    STATIONARY,
    INACTIVE,
    FALL_DETECTED,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): MotionState =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

enum class VestStatus {
    AVAILABLE,
    ASSIGNED,
    ACTIVE,
    OFFLINE,
    MAINTENANCE;

    companion object {
        fun fromRaw(raw: String?): VestStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OFFLINE
    }
}

enum class AlertSeverity {
    WARNING,
    DANGER,
    EMERGENCY;

    companion object {
        fun fromRaw(raw: String?): AlertSeverity =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: WARNING
    }
}

enum class AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED;

    companion object {
        fun fromRaw(raw: String?): AlertStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ACTIVE
    }
}

/** Result of the physical safety-response button / escalation flow (plan section 17). */
enum class SafetyResponseState {
    NONE,
    WAITING,
    ACKNOWLEDGED,
    EMERGENCY_REQUESTED,
    NO_RESPONSE,
    ESCALATED;

    companion object {
        fun fromRaw(raw: String?): SafetyResponseState =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}

enum class AlertType {
    HEART_RATE_WARNING,
    TEMPERATURE_WARNING,
    FALL_DETECTED,
    INACTIVITY,
    EMERGENCY_REQUEST,
    NO_SAFETY_RESPONSE,
    VEST_OFFLINE,
    LOW_BATTERY;

    companion object {
        fun fromRaw(raw: String?): AlertType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: VEST_OFFLINE
    }
}
