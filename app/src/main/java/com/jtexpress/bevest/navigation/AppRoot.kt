package com.jtexpress.bevest.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jtexpress.bevest.R
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.ui.admin.AdminArea
import com.jtexpress.bevest.ui.auth.ForgotPasswordScreen
import com.jtexpress.bevest.ui.auth.LoginScreen
import com.jtexpress.bevest.ui.contractor.ContractorArea
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.sso.SsoArea
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing

@Composable
fun AppRoot(
    viewModel: RootViewModel = hiltViewModel(),
    networkViewModel: NetworkViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val online by networkViewModel.isOnline.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // Network state is app-wide context, so it sits above every screen and
        // animates in rather than shoving the layout (plan section 21). When shown it
        // takes the status-bar space; the content below then consumes that inset so the
        // screen's own top bar does not pad for it twice.
        AnimatedVisibility(
            visible = !online,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            OfflineBanner()
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(if (!online) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
        ) {
            AppRootContent(session, viewModel)
        }
    }
}

@Composable
private fun OfflineBanner() {
    val palette = LocalStatusPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.offline.copy(alpha = 0.18f))
            .statusBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            BevestIcons.Offline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "Offline — showing last known data",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppRootContent(session: SessionState, viewModel: RootViewModel) {
    when (val s = session) {
        SessionState.Loading -> BrandedSplash()

        SessionState.SignedOut -> AuthNavHost()

        is SessionState.Disabled -> BlockedScreen(
            title = "Account disabled",
            message = "The account for ${s.email} has been disabled. Contact your administrator to regain access.",
            onSignOut = viewModel::signOut,
        )

        is SessionState.ProfileError -> BlockedScreen(
            title = "Can't load your profile",
            message = s.message,
            onSignOut = viewModel::signOut,
        )

        is SessionState.Active -> when (s.user.role) {
            UserRole.ADMIN -> AdminArea(s.user, viewModel::signOut)
            UserRole.CONTRACTOR -> ContractorArea(s.user, viewModel::signOut)
            UserRole.SSO -> SsoArea(s.user, viewModel::signOut, viewModel.deepLinks)
            UserRole.UNKNOWN -> BlockedScreen(
                title = "No role assigned",
                message = "Your account doesn't have a role yet. Ask an administrator to assign one.",
                onSignOut = viewModel::signOut,
            )
        }
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
        enterTransition = NavMotion.enter,
        exitTransition = NavMotion.exit,
        popEnterTransition = NavMotion.popEnter,
        popExitTransition = NavMotion.popExit,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Continues the system splash visually so there is no white flash or logo jump. */
@Composable
private fun BrandedSplash() {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.bevest_logo),
            contentDescription = "BeVest",
            modifier = Modifier.size(160.dp),
        )
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
        )
    }
}

/**
 * A dead end the user cannot navigate out of — a disabled account, a missing role.
 *
 * Framed with the reflective band above and below the message so that even the screen
 * telling someone they cannot get in still looks like BeVest, and always offers the one
 * action that is available rather than leaving them stuck.
 */
@Composable
private fun BlockedScreen(title: String, message: String, onSignOut: () -> Unit) {
    val palette = LocalStatusPalette.current
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            BevestIcons.Error,
            contentDescription = null,
            tint = palette.warning,
            modifier = Modifier.size(52.dp),
        )
        ReflectiveBand(thickness = 2.dp, emphasis = 0.8f, modifier = Modifier.widthIn(max = 300.dp))
        Text("BEVEST", style = EyebrowStyle, color = MaterialTheme.colorScheme.primary)
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        SecondaryButton(
            text = "Sign out",
            onClick = onSignOut,
            modifier = Modifier.widthIn(max = 260.dp).padding(top = Spacing.sm),
        )
    }
}
