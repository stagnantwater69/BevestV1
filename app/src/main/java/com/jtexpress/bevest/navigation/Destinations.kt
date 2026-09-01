package com.jtexpress.bevest.navigation

/** Route constants for the app's navigation graphs. */
object Graph {
    const val AUTH = "auth_graph"
}

object Routes {
    // Auth
    const val LOGIN = "login"
    const val FORGOT_PASSWORD = "forgot_password"

    // Shared profile (used inside every role scaffold)
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "profile_edit"
    const val CHANGE_PASSWORD = "profile_password"

    // Admin
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_CONTRACTORS = "admin_contractors"
    const val ADMIN_CONTRACTOR_DETAIL = "admin_contractor_detail" // /{contractorId}
    const val ADMIN_ADD_CONTRACTOR = "admin_add_contractor"
    const val ADMIN_SYSTEM = "admin_system"
    const val ADMIN_ACTIVITY = "admin_activity"

    // Contractor
    const val CONTRACTOR_DASHBOARD = "contractor_dashboard"
    const val CONTRACTOR_WORKERS = "contractor_workers"
    const val CONTRACTOR_SSOS = "contractor_ssos"
    const val CONTRACTOR_ADD_SSO = "contractor_add_sso"
    const val CONTRACTOR_REPORTS = "contractor_reports"
    const val CONTRACTOR_REPORT_DETAIL = "contractor_report_detail" // /{monthKey}
    const val CONTRACTOR_INCIDENTS = "contractor_incidents"

    // SSO
    const val SSO_DASHBOARD = "sso_dashboard"
    const val SSO_WORKERS = "sso_workers"
    const val SSO_ADD_WORKER = "sso_add_worker"
    const val SSO_EDIT_WORKER = "sso_edit_worker" // /{workerId}
    const val SSO_WORKER_DETAIL = "sso_worker_detail" // /{workerId}
    const val SSO_MAP = "sso_map"
    const val SSO_ALERTS = "sso_alerts"
    const val SSO_ALERT_DETAIL = "sso_alert_detail" // /{alertId}
    const val SSO_INCIDENT_DETAIL = "sso_incident_detail" // /{incidentId}
    const val SSO_VESTS = "sso_vests"
    const val SSO_VEST_DETAIL = "sso_vest_detail" // /{vestId}
    const val SSO_PAIR_VEST = "sso_pair_vest"

    // Debug — IoT simulation (SSO: live stream + scenarios; Contractor: seed history)
    const val SIMULATION = "simulation"

    fun withArg(base: String, arg: String) = "$base/$arg"
}
