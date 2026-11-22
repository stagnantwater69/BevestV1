package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jtexpress.bevest.ui.common.ConfirmationDialog
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
    var showTempPassPrompt by remember(user.uid, user.mustChangePassword) {
        mutableStateOf(user.mustChangePassword)
    }

    if (showTempPassPrompt) {
        ConfirmationDialog(
            title = "Change temporary password?",
            message = "Welcome! Since this is your first time signing in with a temporary password, would you like to set a new personal password now?",
            confirmLabel = "Change password",
            onConfirm = {
                showTempPassPrompt = false
                runCatching {
                    FirebaseFirestore.getInstance().collection("users").document(user.uid)
                        .update("mustChangePassword", false)
                }
                navController.navigate(Routes.CHANGE_PASSWORD)
            },
            onDismiss = {
                showTempPassPrompt = false
                runCatching {
                    FirebaseFirestore.getInstance().collection("users").document(user.uid)
                        .update("mustChangePassword", false)
                }
            },
        )
    }

    // Fall detection + escalation runs on the phone while the SSO is signed in (the
    // project's free Firebase plan can't run the cloud functions that would do it).
    val safetyWatcher: SafetyWatcherHost = hiltViewModel()
    val fallPopup by safetyWatcher.fallPopup.collectAsState()

    DisposableEffect(user.uid, siteId) {
        safetyWatcher.start(user.uid, siteId)
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

    fallPopup?.takeIf { it.ownerSsoUid == null || it.ownerSsoUid == user.uid }?.let { popup ->
        FallDetectedPopupDialog(
            popup = popup,
            onOpenAlert = {
                safetyWatcher.dismissFallPopup(popup.alert)
                navController.navigate(Routes.withArg(Routes.SSO_ALERT_DETAIL, popup.alert.alertId))
            },
            onDismiss = {
                safetyWatcher.dismissFallPopup(popup.alert)
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
    onDismiss: () -> Unit,
) {
    val red = LocalStatusPalette.current.emergency
    val isWorkerTrigger = popup.alert.type == AlertType.EMERGENCY_REQUEST
    val isNoSafetyResponse = popup.alert.type == AlertType.NO_SAFETY_RESPONSE
    val isEmergency = popup.alert.severity == AlertSeverity.EMERGENCY

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.5.dp, red.copy(alpha = 0.45f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // High-urgency modal header band
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(red)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = when {
                            isWorkerTrigger -> "WORKER TRIGGER ALERT"
                            isNoSafetyResponse -> "NO SAFETY RESPONSE"
                            isEmergency -> "EMERGENCY: NO RESPONSE"
                            else -> "WORKER FALL DETECTED"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }

                // Modal body with worker & alert details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = when {
                            isWorkerTrigger -> popup.workerName
                            isNoSafetyResponse -> "${popup.workerName} has not responded!"
                            else -> "${popup.workerName} has fallen!"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = buildString {
                                    append("Worker ID: ${popup.alert.workerId}")
                                    if (!popup.vestId.isNullOrBlank()) {
                                        append(" • Vest: ${popup.vestId}")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            if (!popup.siteName.isNullOrBlank() || !popup.floorNumber.isNullOrBlank()) {
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
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = when {
                            isWorkerTrigger -> "Worker Trigger. Please check with Worker."
                            isNoSafetyResponse -> "Worker is in danger, please check with worker."
                            isEmergency -> "Worker fell and has not responded within the safety timeout. Immediate action required!"
                            else -> "Impact / fall detected by the smart vest sensor. Check on this worker immediately."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Button(
                            onClick = onOpenAlert,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = red,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "View Alert",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
