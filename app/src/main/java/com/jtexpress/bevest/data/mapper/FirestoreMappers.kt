package com.jtexpress.bevest.data.mapper

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.model.ProjectSite
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.domain.model.Vest
import com.jtexpress.bevest.domain.model.VestAssignment
import com.jtexpress.bevest.domain.model.VestStatus
import com.jtexpress.bevest.domain.model.Worker

private fun DocumentSnapshot.str(key: String): String = getString(key).orEmpty()
private fun DocumentSnapshot.strOrNull(key: String): String? = getString(key)
private fun DocumentSnapshot.int(key: String): Int? = getLong(key)?.toInt()
private fun DocumentSnapshot.dbl(key: String): Double? = getDouble(key)
private fun DocumentSnapshot.bool(key: String, default: Boolean = false): Boolean = getBoolean(key) ?: default
private fun DocumentSnapshot.millis(key: String): Long? = when (val v = get(key)) {
    is Timestamp -> v.toDate().time
    is Long -> v
    is Double -> v.toLong()
    else -> null
}

fun DocumentSnapshot.toUser(): User = User(
    uid = id,
    firstName = str("firstName"),
    lastName = str("lastName"),
    email = str("email"),
    phone = str("phone"),
    role = UserRole.fromRaw(strOrNull("role")),
    contractorId = strOrNull("contractorId"),
    siteId = strOrNull("siteId"),
    active = bool("active", true),
)

fun DocumentSnapshot.toWorker(): Worker = Worker(
    workerId = id,
    firstName = str("firstName"),
    lastName = str("lastName"),
    phone = str("phone"),
    photoUrl = strOrNull("photoUrl"),
    contractorId = strOrNull("contractorId"),
    siteId = strOrNull("siteId"),
    assignedVestId = strOrNull("assignedVestId"),
    currentStatus = SafetyStatus.fromRaw(strOrNull("currentStatus")),
    active = bool("active", true),
)

fun DocumentSnapshot.toVest(): Vest = Vest(
    vestId = id,
    assignedWorkerId = strOrNull("assignedWorkerId"),
    status = VestStatus.fromRaw(strOrNull("status")),
    batteryPercent = int("battery"),
    online = bool("online"),
    lastSeen = millis("lastSeen"),
)

fun DocumentSnapshot.toAlert(): Alert = Alert(
    alertId = id,
    workerId = str("workerId"),
    vestId = str("vestId"),
    type = AlertType.fromRaw(strOrNull("type")),
    severity = AlertSeverity.fromRaw(strOrNull("severity")),
    status = AlertStatus.fromRaw(strOrNull("status")),
    message = str("message"),
    createdAt = millis("createdAt") ?: 0L,
    acknowledgedAt = millis("acknowledgedAt"),
    resolvedAt = millis("resolvedAt"),
    resolvedBy = strOrNull("resolvedBy"),
)

fun DocumentSnapshot.toIncident(): Incident = Incident(
    incidentId = id,
    workerId = str("workerId"),
    vestId = str("vestId"),
    alertId = strOrNull("alertId"),
    type = AlertType.fromRaw(strOrNull("type")),
    severity = AlertSeverity.fromRaw(strOrNull("severity")),
    heartRate = int("heartRate"),
    temperature = dbl("temperature"),
    latitude = dbl("latitude"),
    longitude = dbl("longitude"),
    createdAt = millis("createdAt") ?: 0L,
    outcome = strOrNull("outcome"),
    resolutionNotes = strOrNull("resolutionNotes"),
    resolvedBy = strOrNull("resolvedBy"),
)

fun DocumentSnapshot.toProjectSite(): ProjectSite = ProjectSite(
    projectId = id,
    contractorId = str("contractorId"),
    name = str("name"),
    location = str("location"),
    active = bool("active", true),
)

fun DocumentSnapshot.toVestAssignment(): VestAssignment = VestAssignment(
    assignmentId = id,
    workerId = str("workerId"),
    vestId = str("vestId"),
    assignedAt = millis("assignedAt") ?: 0L,
    unassignedAt = millis("unassignedAt"),
    assignedBy = str("assignedBy"),
)
