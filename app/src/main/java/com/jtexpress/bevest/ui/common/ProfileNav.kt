package com.jtexpress.bevest.ui.common

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.jtexpress.bevest.navigation.Routes
import com.jtexpress.bevest.ui.profile.ChangePasswordScreen
import com.jtexpress.bevest.ui.profile.EditProfileScreen
import com.jtexpress.bevest.ui.profile.ProfileScreen

/**
 * Shared profile destinations included in every role scaffold (plan section 25).
 * Profile is reached from the top-right avatar action on every role's root screen, so it
 * is always a pushed destination with a back arrow — never a bottom-nav tab.
 */
fun NavGraphBuilder.profileDestinations(navController: NavHostController, onSignOut: () -> Unit) {
    composable(Routes.PROFILE) {
        ProfileScreen(
            onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
            onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
            onSignOut = onSignOut,
            onBack = { navController.popBackStack() },
        )
    }
    composable(Routes.EDIT_PROFILE) {
        EditProfileScreen(onDone = { navController.popBackStack() })
    }
    composable(Routes.CHANGE_PASSWORD) {
        ChangePasswordScreen(onDone = { navController.popBackStack() })
    }
}
