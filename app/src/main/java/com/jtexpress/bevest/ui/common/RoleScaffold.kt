package com.jtexpress.bevest.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jtexpress.bevest.navigation.NavMotion
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.ComponentHeight
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Motion
import com.jtexpress.bevest.ui.theme.Spacing

/** A bottom-navigation destination. [badge] shows an unread/active count when > 0. */
data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
)

/**
 * Shared scaffold for the three role areas: a bottom navigation bar over a nested NavHost.
 */
@Composable
fun RoleScaffold(
    tabs: List<BottomTab>,
    startRoute: String,
    navController: NavHostController = rememberNavController(),
    builder: NavGraphBuilder.(NavHostController) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            BevestBottomBar(
                tabs = tabs,
                currentRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(startRoute) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            // Pad for the bottom bar, then consume that inset so each screen's own
            // BevestScaffold does not add it a second time.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = NavMotion.enter,
            exitTransition = NavMotion.exit,
            popEnterTransition = NavMotion.popEnter,
            popExitTransition = NavMotion.popExit,
        ) {
            builder(navController)
        }
    }
}

/**
 * The app's bottom navigation.
 *
 * Material's default bar marks the active tab with a filled pill behind the icon, which
 * is both instantly recognisable as stock Android and, on this palette, a blob of brand
 * orange competing with the safety colors on screen.
 *
 * This one marks the active tab with the reflective band instead — the same device that
 * underlines every section header — sitting directly above the icon like the stripe
 * across a vest. It reads as *this* app, it costs no color, and it leaves orange free to
 * mean "action" everywhere else.
 *
 * The whole bar is capped by a full-width band, so the navigation is visibly part of the
 * same object as the content above it rather than a tray bolted underneath.
 */
@Composable
private fun BevestBottomBar(
    tabs: List<BottomTab>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            ReflectiveBand(thickness = 2.dp, emphasis = 0.55f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(ComponentHeight.navBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    BottomBarItem(
                        tab = tab,
                        selected = currentRoute == tab.route,
                        onClick = { onSelect(tab.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStatusPalette.current
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant

    val contentColor by animateColorAsState(
        targetValue = if (selected) active else inactive,
        animationSpec = tween(Motion.QUICK),
        label = "navItemColor",
    )
    // The indicator grows from nothing rather than cross-fading, so the eye tracks it
    // moving between tabs.
    val indicator by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(Motion.STANDARD),
        label = "navItemIndicator",
    )

    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            )
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // The reflective band, sized to the tab's content. Reserved space (not
        // conditional) so selecting a tab never shifts the row.
        Box(
            Modifier.height(3.dp).width(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (indicator > 0.01f) {
                ReflectiveBand(
                    modifier = Modifier
                        .width(26.dp * indicator)
                        .clip(BevestShapes.pill),
                    thickness = 3.dp,
                    emphasis = indicator,
                    tint = active,
                )
            }
        }

        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                tab.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(IconSize.large),
            )
            // A count badge is news; it should arrive, not just appear. Fully qualified
            // because the enclosing Column would otherwise bind the ColumnScope overload.
            androidx.compose.animation.AnimatedVisibility(
                visible = tab.badge > 0,
                enter = scaleIn(tween(Motion.QUICK)) + fadeIn(tween(Motion.QUICK)),
                exit = scaleOut(tween(Motion.INSTANT)) + fadeOut(tween(Motion.INSTANT)),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Box(
                    Modifier
                        .offsetBadge()
                        .background(palette.danger, BevestShapes.pill)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        tab.badge.coerceAtMost(99).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
        }

        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Nudges the badge off the icon's corner without changing the icon's layout slot. */
private fun Modifier.offsetBadge(): Modifier = this.then(
    Modifier.padding(start = 10.dp),
)
