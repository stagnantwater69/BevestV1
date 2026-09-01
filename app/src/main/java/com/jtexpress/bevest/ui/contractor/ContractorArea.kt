package com.jtexpress.bevest.ui.contractor

import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.navigation.Routes
import com.jtexpress.bevest.ui.common.BottomTab
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.common.RoleScaffold
import com.jtexpress.bevest.ui.sim.SimulationScreen
import com.jtexpress.bevest.ui.common.profileDestinations

@Composable
fun ContractorArea(user: User, onSignOut: () -> Unit) {
    RoleScaffold(
        tabs = listOf(
            BottomTab(Routes.CONTRACTOR_DASHBOARD, "Dashboard", BevestIcons.Dashboard),
            BottomTab(Routes.CONTRACTOR_WORKERS, "Workers", BevestIcons.Workers),
            BottomTab(Routes.CONTRACTOR_REPORTS, "Reports", BevestIcons.Reports),
        ),
        startRoute = Routes.CONTRACTOR_DASHBOARD,
    ) { navController ->
        composable(Routes.CONTRACTOR_DASHBOARD) {
            ContractorDashboardScreen(user = user, navigate = { route -> navController.navigate(route) })
        }
        composable(Routes.CONTRACTOR_WORKERS) {
            ContractorWorkersScreen(
                user = user,
                onOpenWorker = { id -> navController.navigate(Routes.withArg(Routes.SSO_WORKER_DETAIL, id)) },
            )
        }
        composable(Routes.CONTRACTOR_SSOS) {
            ContractorSsosScreen(user = user, onAdd = { navController.navigate(Routes.CONTRACTOR_ADD_SSO) })
        }
        composable(Routes.CONTRACTOR_ADD_SSO) {
            AddSsoScreen(contractor = user, onDone = { navController.popBackStack() })
        }
        composable(Routes.CONTRACTOR_REPORTS) {
            ContractorReportsScreen(
                user = user,
                onOpenReport = { key -> navController.navigate(Routes.withArg(Routes.CONTRACTOR_REPORT_DETAIL, key)) },
                onOpenIncidents = { navController.navigate(Routes.CONTRACTOR_INCIDENTS) },
                onOpenSsos = { navController.navigate(Routes.CONTRACTOR_SSOS) },
            )
        }
        composable("${Routes.CONTRACTOR_REPORT_DETAIL}/{monthKey}") { entry ->
            MonthlyReportDetailScreen(
                contractorId = user.contractorId ?: user.uid,
                monthKey = entry.arguments?.getString("monthKey").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CONTRACTOR_INCIDENTS) {
            ContractorIncidentsScreen(
                contractorId = user.contractorId ?: user.uid,
                onOpenIncident = { id -> navController.navigate(Routes.withArg(Routes.SSO_INCIDENT_DETAIL, id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable("${Routes.SSO_WORKER_DETAIL}/{workerId}") { entry ->
            com.jtexpress.bevest.ui.sso.WorkerDetailScreen(
                workerId = entry.arguments?.getString("workerId").orEmpty(),
                actorId = user.uid,
                canControl = false,
                onBack = { navController.popBackStack() },
                onOpenAlert = {},
            )
        }
        composable("${Routes.SSO_INCIDENT_DETAIL}/{incidentId}") { entry ->
            com.jtexpress.bevest.ui.sso.IncidentDetailScreen(
                incidentId = entry.arguments?.getString("incidentId").orEmpty(),
                actorId = user.uid,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SIMULATION) {
            SimulationScreen(user = user, onBack = { navController.popBackStack() })
        }
        profileDestinations(navController, onSignOut)
    }
}
