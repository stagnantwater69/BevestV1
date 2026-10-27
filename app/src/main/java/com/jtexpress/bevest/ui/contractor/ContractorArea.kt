package com.jtexpress.bevest.ui.contractor

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.jtexpress.bevest.BuildConfig
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.navigation.Routes
import com.jtexpress.bevest.ui.common.BottomTab
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.common.RoleScaffold
import com.jtexpress.bevest.ui.sim.SimulationScreen
import com.jtexpress.bevest.ui.common.profileDestinations

/**
 * Jump to another bottom tab the way the bar itself does, so a dashboard shortcut lands
 * on the tab (highlighted, with its own back stack) instead of pushing a copy of it.
 */
private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(Routes.CONTRACTOR_DASHBOARD) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun ContractorArea(user: User, onSignOut: () -> Unit) {
    val contractorId = user.contractorId ?: user.uid
    RoleScaffold(
        tabs = buildList {
            add(BottomTab(Routes.CONTRACTOR_DASHBOARD, "Dashboard", BevestIcons.Dashboard))
            add(BottomTab(Routes.CONTRACTOR_SSOS, "SSO", BevestIcons.Officer))
            add(BottomTab(Routes.CONTRACTOR_REPORTS, "Reports", BevestIcons.Reports))
            // Demo tooling: a tab in debug builds, absent from release.
            if (BuildConfig.DEBUG) add(BottomTab(Routes.SIMULATION, "Simulation", BevestIcons.Simulator))
        },
        startRoute = Routes.CONTRACTOR_DASHBOARD,
    ) { navController ->
        composable(Routes.CONTRACTOR_DASHBOARD) {
            ContractorDashboardScreen(
                user = user,
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenSsos = { navController.openTab(Routes.CONTRACTOR_SSOS) },
                onOpenReport = { key -> navController.navigate(Routes.withArg(Routes.CONTRACTOR_REPORT_DETAIL, key)) },
                onOpenIncidents = { navController.navigate(Routes.CONTRACTOR_INCIDENTS) },
            )
        }
        composable(Routes.CONTRACTOR_SSOS) {
            ContractorSsosScreen(
                user = user,
                onAdd = { navController.navigate(Routes.CONTRACTOR_ADD_SSO) },
                onOpen = { uid -> navController.navigate(Routes.withArg(Routes.CONTRACTOR_SSO_DETAIL, uid)) },
            )
        }
        composable("${Routes.CONTRACTOR_SSO_DETAIL}/{uid}") { entry ->
            SsoProfileScreen(
                contractorId = contractorId,
                uid = entry.arguments?.getString("uid").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CONTRACTOR_ADD_SSO) {
            AddSsoScreen(contractor = user, onDone = { navController.popBackStack() })
        }
        composable(Routes.CONTRACTOR_REPORTS) {
            ContractorReportsScreen(
                user = user,
                onOpenReport = { key -> navController.navigate(Routes.withArg(Routes.CONTRACTOR_REPORT_DETAIL, key)) },
                onOpenIncidents = { navController.navigate(Routes.CONTRACTOR_INCIDENTS) },
                onOpenSsos = { navController.openTab(Routes.CONTRACTOR_SSOS) },
            )
        }
        composable("${Routes.CONTRACTOR_REPORT_DETAIL}/{monthKey}") { entry ->
            MonthlyReportDetailScreen(
                contractorId = contractorId,
                monthKey = entry.arguments?.getString("monthKey").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CONTRACTOR_INCIDENTS) {
            ContractorIncidentsScreen(
                contractorId = contractorId,
                onOpenIncident = { id -> navController.navigate(Routes.withArg(Routes.SSO_INCIDENT_DETAIL, id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable("${Routes.SSO_INCIDENT_DETAIL}/{incidentId}") { entry ->
            com.jtexpress.bevest.ui.sso.IncidentDetailScreen(
                incidentId = entry.arguments?.getString("incidentId").orEmpty(),
                actorId = user.uid,
                onBack = { navController.popBackStack() },
            )
        }
        if (BuildConfig.DEBUG) {
            composable(Routes.SIMULATION) {
                SimulationScreen(user = user, onBack = { navController.openTab(Routes.CONTRACTOR_DASHBOARD) })
            }
        }
        profileDestinations(navController, onSignOut)
    }
}
