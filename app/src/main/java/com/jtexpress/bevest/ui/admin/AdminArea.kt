package com.jtexpress.bevest.ui.admin

import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.navigation.Routes
import com.jtexpress.bevest.ui.common.BottomTab
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.common.PlaceholderScreen
import com.jtexpress.bevest.ui.common.RoleScaffold
import com.jtexpress.bevest.ui.common.profileDestinations

@Composable
fun AdminArea(user: User, onSignOut: () -> Unit) {
    RoleScaffold(
        tabs = listOf(
            BottomTab(Routes.ADMIN_DASHBOARD, "Dashboard", BevestIcons.Dashboard),
            BottomTab(Routes.ADMIN_CONTRACTORS, "Contractors", BevestIcons.Contractors),
            BottomTab(Routes.ADMIN_SYSTEM, "System", BevestIcons.System),
        ),
        startRoute = Routes.ADMIN_DASHBOARD,
    ) { navController ->
        composable(Routes.ADMIN_DASHBOARD) {
            AdminDashboardScreen(user = user, navigate = { route -> navController.navigate(route) })
        }
        composable(Routes.ADMIN_CONTRACTORS) {
            AdminContractorsScreen(
                onAdd = { navController.navigate(Routes.ADMIN_ADD_CONTRACTOR) },
                onOpen = { id -> navController.navigate(Routes.withArg(Routes.ADMIN_CONTRACTOR_DETAIL, id)) },
            )
        }
        composable(Routes.ADMIN_ADD_CONTRACTOR) {
            AddContractorScreen(actorId = user.uid, onDone = { navController.popBackStack() })
        }
        composable("${Routes.ADMIN_CONTRACTOR_DETAIL}/{contractorId}") { entry ->
            AdminContractorDetailScreen(
                contractorId = entry.arguments?.getString("contractorId").orEmpty(),
                actorId = user.uid,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ADMIN_SYSTEM) {
            AdminSystemScreen(actorId = user.uid, onOpenActivity = { navController.navigate(Routes.ADMIN_ACTIVITY) })
        }
        composable(Routes.ADMIN_ACTIVITY) { AdminActivityScreen(onBack = { navController.popBackStack() }) }
        profileDestinations(navController, onSignOut)
    }
}
