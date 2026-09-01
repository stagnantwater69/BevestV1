package com.jtexpress.bevest.data.firebase

/**
 * Single source of truth for Firebase locations. Realtime Database holds fast-changing
 * data; Firestore holds records (plan section 18 & 20). Confirm every path with the IoT
 * team before wiring listeners (plan section 29).
 */
object FirebasePaths {

    // ---- Realtime Database (live) ----
    const val LIVE_READINGS = "liveReadings"          // liveReadings/{workerId}
    const val VESTS_RT = "vestsLive"                   // vestsLive/{vestId}
    fun liveReading(workerId: String) = "$LIVE_READINGS/$workerId"
    fun vestRealtime(vestId: String) = "$VESTS_RT/$vestId"

    // ---- Firestore (records) ----
    const val USERS = "users"
    const val WORKERS = "workers"
    const val VESTS = "vests"
    const val ALERTS = "alerts"
    const val INCIDENTS = "incidents"
    const val VEST_ASSIGNMENTS = "vestAssignments"
    const val PROJECTS = "projects"
    const val ACTIVITY_LOG = "activityLog"

    // ---- Settings (Firestore collection `settings`) ----
    const val SETTINGS = "settings"
    const val THRESHOLDS_DOC = "thresholds"           // settings/thresholds
    const val SYSTEM_DOC = "system"                   // settings/system  (maintenance mode)

    // ---- Storage ----
    fun workerPhoto(workerId: String) = "workerPhotos/$workerId.jpg"
    fun reportPdf(contractorId: String, monthKey: String) = "reports/$contractorId/$monthKey.pdf"
}
