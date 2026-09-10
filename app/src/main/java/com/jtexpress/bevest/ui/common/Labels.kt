package com.jtexpress.bevest.ui.common

import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyResponseState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.domain.model.VestStatus

/**
 * User-facing copy for every domain enum.
 *
 * Enum constants are database values, not language. Screens used to render them raw
 * ("NO_SAFETY_RESPONSE") or with a cosmetic `.replace('_', ' ')`, which still reads as
 * shouted machine output. An officer scanning a site in bad light should get a phrase,
 * not a constant.
 *
 * [label] is the short form for chips and rows. [detail] adds the sentence of context a
 * detail screen has room for — what actually happened, and what it means.
 */

fun SafetyStatus.label(): String = when (this) {
    SafetyStatus.NORMAL -> "Normal"
    SafetyStatus.WARNING -> "Warning"
    SafetyStatus.DANGER -> "Danger"
    SafetyStatus.EMERGENCY -> "Emergency"
    SafetyStatus.OFFLINE -> "Offline"
}

fun SafetyStatus.detail(): String = when (this) {
    SafetyStatus.NORMAL -> "Readings are within safe limits."
    SafetyStatus.WARNING -> "A reading has crossed its warning threshold."
    SafetyStatus.DANGER -> "A reading is at a dangerous level. Check on this worker."
    SafetyStatus.EMERGENCY -> "Help has been requested or a fall went unanswered."
    SafetyStatus.OFFLINE -> "The vest has stopped reporting. Last known data shown."
}

fun AlertType.label(): String = when (this) {
    AlertType.HEART_RATE_WARNING -> "High heart rate"
    AlertType.TEMPERATURE_WARNING -> "High body temperature"
    AlertType.FALL_DETECTED -> "Fall detected"
    AlertType.INACTIVITY -> "No movement"
    AlertType.EMERGENCY_REQUEST -> "Help requested"
    AlertType.NO_SAFETY_RESPONSE -> "No response to check"
    AlertType.VEST_OFFLINE -> "Vest offline"
    AlertType.LOW_BATTERY -> "Low vest battery"
}

fun AlertType.detail(): String = when (this) {
    AlertType.HEART_RATE_WARNING -> "Heart rate stayed above the safe range."
    AlertType.TEMPERATURE_WARNING -> "Body temperature stayed above the safe range."
    AlertType.FALL_DETECTED -> "The vest's motion sensor registered a fall."
    AlertType.INACTIVITY -> "No movement for longer than the inactivity limit."
    AlertType.EMERGENCY_REQUEST -> "The worker pressed the safety button on their vest."
    AlertType.NO_SAFETY_RESPONSE -> "The worker did not acknowledge a safety check."
    AlertType.VEST_OFFLINE -> "The vest stopped sending readings."
    AlertType.LOW_BATTERY -> "The vest battery needs charging to keep monitoring."
}

fun AlertSeverity.label(): String = when (this) {
    AlertSeverity.WARNING -> "Warning"
    AlertSeverity.DANGER -> "Danger"
    AlertSeverity.EMERGENCY -> "Emergency"
}

fun AlertStatus.label(): String = when (this) {
    AlertStatus.ACTIVE -> "Active"
    AlertStatus.ACKNOWLEDGED -> "Acknowledged"
    AlertStatus.RESOLVED -> "Resolved"
}

fun MotionState.label(): String = when (this) {
    MotionState.MOVING -> "Moving"
    MotionState.STATIONARY -> "Stationary"
    MotionState.INACTIVE -> "Inactive"
    MotionState.FALL_DETECTED -> "Fall detected"
    MotionState.UNKNOWN -> "No data"
}

fun VestStatus.label(): String = when (this) {
    VestStatus.AVAILABLE -> "Available"
    VestStatus.ASSIGNED -> "Assigned"
    VestStatus.ACTIVE -> "Active"
    VestStatus.OFFLINE -> "Offline"
    VestStatus.MAINTENANCE -> "Maintenance"
}

fun VestStatus.detail(): String = when (this) {
    VestStatus.AVAILABLE -> "Ready to pair with a worker."
    VestStatus.ASSIGNED -> "Paired to a worker but not currently reporting."
    VestStatus.ACTIVE -> "Paired and sending live readings."
    VestStatus.OFFLINE -> "Not reachable. Check the vest is charged and on site."
    VestStatus.MAINTENANCE -> "Withdrawn from use until serviced."
}

fun SafetyResponseState.label(): String = when (this) {
    SafetyResponseState.NONE -> "No check running"
    SafetyResponseState.WAITING -> "Waiting for worker"
    SafetyResponseState.ACKNOWLEDGED -> "Worker responded"
    SafetyResponseState.EMERGENCY_REQUESTED -> "Help requested"
    SafetyResponseState.NO_RESPONSE -> "No response"
    SafetyResponseState.ESCALATED -> "Escalated"
}

fun UserRole.label(): String = when (this) {
    UserRole.ADMIN -> "Administrator"
    UserRole.CONTRACTOR -> "Contractor"
    UserRole.SSO -> "Site Safety Officer"
    UserRole.UNKNOWN -> "No role assigned"
}
