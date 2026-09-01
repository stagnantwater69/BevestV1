package com.jtexpress.bevest.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * One motion language for every NavHost in the app: a soft horizontal push paired with a
 * fade. The offset is deliberately small (~1/12 of the width) so a bottom-tab switch
 * reads as a gentle cross-fade while a drill-down still feels directional.
 */
object NavMotion {

    private const val ENTER_MS = 260
    private const val EXIT_MS = 190

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { it / 12 }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(EXIT_MS)) + slideOutHorizontally(tween(EXIT_MS)) { -it / 12 }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { -it / 12 }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(EXIT_MS)) + slideOutHorizontally(tween(EXIT_MS)) { it / 12 }
    }
}
