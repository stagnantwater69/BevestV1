package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.safety.FallPopupState
import com.jtexpress.bevest.navigation.Routes
import com.jtexpress.bevest.notifications.DeepLink
import com.jtexpress.bevest.ui.common.BottomTab
import com.jtexpress.bevest.ui.common.RoleScaffold
import com.jtexpress.bevest.ui.common.profileDestinations
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun SsoArea(
    user: User,
    onSignOut: () -> Unit,
    deepLinks: SharedFlow<DeepLink>? = null,
) {
    val siteId = user.siteId
    val navController = rememberNavController()

    // Fall detection + escalation runs on the phone while the SSO is signed in (the
    // project's free Firebase plan can't run the cloud functions that would do it).
    val safetyWatcher: SafetyWatcherHost = hiltViewModel()
    val fallPopup by safetyWatcher.fallPopup.collectAsState()

    DisposableEffect(siteId) {
        safetyWatcher.start(siteId)
        onDispose { safetyWatcher.stop() }
    }

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

    fallPopup?.let { popup ->
        FallDetectedPopupDialog(
            popup = popup,
            onOpenAlert = {
                safetyWatcher.dismissFallPopup(popup.alert)
                navController.navigate(Routes.withArg(Routes.SSO_ALERT_DETAIL, popup.alert.alertId))
            },
        )
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
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
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
                user = user,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SSO_PAIR_VEST) {
            PairVestScreen(user = user, onDone = { navController.popBackStack() })
        }
        profileDestinations(navController, onSignOut)
    }
}

@Composable
private fun FallDetectedPopupDialog(
    popup: FallPopupState,
    onOpenAlert: () -> Unit,
) {
    val red = LocalStatusPalette.current.emergency
    val isWorkerTrigger = popup.alert.type == AlertType.EMERGENCY_REQUEST
    val isNoSafetyResponse = popup.alert.type == AlertType.NO_SAFETY_RESPONSE
    val isEmergency = popup.alert.severity == AlertSeverity.EMERGENCY

    Dialog(
        onDismissRequest = onOpenAlert,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(red)
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = when {
                    isWorkerTrigger -> "Worker Trigger"
                    isNoSafetyResponse -> "⚠ NO SAFETY RESPONSE"
                    isEmergency -> "⚠ EMERGENCY: NO RESPONSE"
                    else -> "⚠ WORKER FALL DETECTED"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = when {
                            isWorkerTrigger -> popup.workerName
                            isNoSafetyResponse -> "${popup.workerName} has not responded!"
                            else -> "${popup.workerName} has fallen!"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = buildString {
                            append("Worker ID: ${popup.alert.workerId}")
                            if (!popup.vestId.isNullOrBlank()) {
                                append(" • Vest: ${popup.vestId}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                    if (!popup.siteName.isNullOrBlank() || !popup.floorNumber.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = buildString {
                                if (!popup.siteName.isNullOrBlank()) {
                                    append("Site: ${popup.siteName}")
                                }
                                if (!popup.floorNumber.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append("Floor: ${popup.floorNumber}")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = when {
                            isWorkerTrigger -> "Worker Trigger. Please check with Worker."
                            isNoSafetyResponse -> "Worker is in danger, please check with worker."
                            isEmergency -> "Worker fell and has not responded within the safety timeout. Immediate action required!"
                            else -> "Impact / fall detected by the smart vest sensor. Check on this worker immediately."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
            Button(
                onClick = onOpenAlert,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = red,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = "Open & View Alert",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
