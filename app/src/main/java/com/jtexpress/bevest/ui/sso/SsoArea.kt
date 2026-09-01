package com.jtexpress.bevest.ui.sso

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.navigation.Routes
import com.jtexpress.bevest.notifications.DeepLink
import com.jtexpress.bevest.ui.common.BottomTab
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.common.RoleScaffold
import com.jtexpress.bevest.ui.sim.SimulationScreen
import com.jtexpress.bevest.ui.common.profileDestinations
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun SsoArea(
    user: User,
    onSignOut: () -> Unit,
    deepLinks: SharedFlow<DeepLink>? = null,
) {
    val siteId = user.siteId
    val navController = rememberNavController()

    LaunchedEffect(deepLinks) {
        deepLinks?.collect { link ->
            when (link.type) {
                "INCIDENT" -> navController.navigate(Routes.withArg(Routes.SSO_INCIDENT_DETAIL, link.targetId))
                "ALERT" -> navController.navigate(Routes.withArg(Routes.SSO_ALERT_DETAIL, link.targetId))
                else -> link.workerId?.let {
                    navController.navigate(Routes.withArg(Routes.SSO_WORKER_DETAIL, it))
                }
            }
        }
    }

    RoleScaffold(
        tabs = listOf(
            BottomTab(Routes.SSO_DASHBOARD, "Dashboard", BevestIcons.Dashboard),
            BottomTab(Routes.SSO_WORKERS, "Workers", BevestIcons.Workers),
            BottomTab(Routes.SSO_MAP, "Map", BevestIcons.Map),
            BottomTab(Routes.SSO_ALERTS, "Alerts", BevestIcons.Alerts),
            BottomTab(Routes.SSO_VESTS, "Vests", BevestIcons.Vests),
        ),
        startRoute = Routes.SSO_DASHBOARD,
        navController = navController,
    ) { navController ->
        composable(Routes.SSO_DASHBOARD) {
            SsoDashboardScreen(
                user = user,
                onOpenWorker = { id -> navController.navigate(Routes.withArg(Routes.SSO_WORKER_DETAIL, id)) },
                onOpenAlert = { id -> navController.navigate(Routes.withArg(Routes.SSO_ALERT_DETAIL, id)) },
                onOpenAlerts = { navController.navigate(Routes.SSO_ALERTS) },
                onOpenMap = { navController.navigate(Routes.SSO_MAP) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenSimulator = { navController.navigate(Routes.SIMULATION) },
            )
        }
        composable(Routes.SSO_WORKERS) {
            WorkerDirectoryScreen(
                user = user,
                onOpenWorker = { id -> navController.navigate(Routes.withArg(Routes.SSO_WORKER_DETAIL, id)) },
                onAddWorker = { navController.navigate(Routes.SSO_ADD_WORKER) },
            )
        }
        composable(Routes.SSO_ADD_WORKER) {
            AddEditWorkerScreen(user = user, workerId = null, onDone = { navController.popBackStack() })
        }
        composable("${Routes.SSO_EDIT_WORKER}/{workerId}") { entry ->
            AddEditWorkerScreen(
                user = user,
                workerId = entry.arguments?.getString("workerId"),
                onDone = { navController.popBackStack() },
            )
        }
        composable("${Routes.SSO_WORKER_DETAIL}/{workerId}") { entry ->
            WorkerDetailScreen(
                workerId = entry.arguments?.getString("workerId").orEmpty(),
                actorId = user.uid,
                canControl = true,
                onBack = { navController.popBackStack() },
                onOpenAlert = { id -> navController.navigate(Routes.withArg(Routes.SSO_ALERT_DETAIL, id)) },
                onEdit = { id -> navController.navigate(Routes.withArg(Routes.SSO_EDIT_WORKER, id)) },
            )
        }
        composable(Routes.SSO_MAP) {
            LiveMapScreen(
                siteId = siteId,
                onOpenWorker = { id -> navController.navigate(Routes.withArg(Routes.SSO_WORKER_DETAIL, id)) },
            )
        }
        composable(Routes.SSO_ALERTS) {
            AlertHistoryScreen(
                siteId = siteId,
                onOpenAlert = { id -> navController.navigate(Routes.withArg(Routes.SSO_ALERT_DETAIL, id)) },
            )
        }
        composable("${Routes.SSO_ALERT_DETAIL}/{alertId}") { entry ->
            AlertDetailScreen(
                alertId = entry.arguments?.getString("alertId").orEmpty(),
                actorId = user.uid,
                onBack = { navController.popBackStack() },
                onOpenWorker = { id -> navController.navigate(Routes.withArg(Routes.SSO_WORKER_DETAIL, id)) },
            )
        }
        composable("${Routes.SSO_INCIDENT_DETAIL}/{incidentId}") { entry ->
            IncidentDetailScreen(
                incidentId = entry.arguments?.getString("incidentId").orEmpty(),
                actorId = user.uid,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SSO_VESTS) {
            VestListScreen(
                onOpenVest = { id -> navController.navigate(Routes.withArg(Routes.SSO_VEST_DETAIL, id)) },
                onPairVest = { navController.navigate(Routes.SSO_PAIR_VEST) },
            )
        }
        composable("${Routes.SSO_VEST_DETAIL}/{vestId}") { entry ->
            VestDetailScreen(
                vestId = entry.arguments?.getString("vestId").orEmpty(),
                actorId = user.uid,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SSO_PAIR_VEST) {
            PairVestScreen(user = user, onDone = { navController.popBackStack() })
        }
        composable(Routes.SIMULATION) {
            SimulationScreen(user = user, onBack = { navController.popBackStack() })
        }
        profileDestinations(navController, onSignOut)
    }
}
