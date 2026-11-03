package com.jtexpress.bevest.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jtexpress.bevest.notifications.AlarmCheck
import com.jtexpress.bevest.notifications.AlarmReadiness
import com.jtexpress.bevest.notifications.AlarmSetupPrefs
import com.jtexpress.bevest.ui.common.BevestCard
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.InsightCard
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.SuccessNote
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The alarm checks, plus a way to re-read them after something the app can't observe. */
class AlarmChecks(val checks: List<AlarmCheck>, val refresh: () -> Unit) {
    val unready: Int get() = checks.count { !it.ok }
}

/**
 * The alarm checks, re-read every time the screen comes back to the front — which is
 * what happens when the user returns from the settings page a "Fix" button opened.
 */
@Composable
fun rememberAlarmChecks(): AlarmChecks {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var checks by remember { mutableStateOf(AlarmReadiness.checks(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checks = AlarmReadiness.checks(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return AlarmChecks(checks) { checks = AlarmReadiness.checks(context) }
}

/**
 * "Fix" for one check. Notifications are asked for with Android's own prompt while it
 * will still show one; everything else opens the matching settings page.
 */
@Composable
private fun rememberFixer(onResult: () -> Unit): (AlarmCheck) -> Unit {
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult()
    }
    return { check ->
        if (check.key == AlarmReadiness.KEY_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 &&
            !AlarmSetupPrefs.notificationPromptUsed(context)
        ) {
            AlarmSetupPrefs.markNotificationPromptUsed(context)
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AlarmReadiness.openFix(context, check.fixes)
        }
    }
}

// --------------------------------------------------------------- checklist

/** Profile → Emergency alarm: every check at once, to come back to any time. */
@Composable
fun AlarmSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val alarm = rememberAlarmChecks()
    val fix = rememberFixer(alarm.refresh)

    BevestScaffold(title = "Emergency alarm", subtitle = "Make sure alerts get through", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            InsightCard(
                text = "When a worker falls or doesn't respond, Bevest sounds a siren, vibrates " +
                    "until you act and shows a full-screen alert — even on silent. Your phone " +
                    "has to allow each of these.",
                icon = BevestIcons.Alerts,
            )

            if (alarm.unready == 0) {
                SuccessNote("All set. Emergency alarms will get through on this phone.")
            }

            SectionHeader(title = "Phone settings")
            alarm.checks.forEach { check ->
                CheckCard(
                    check = check,
                    onFix = { fix(check) },
                    onConfirm = { done -> AlarmSetupPrefs.setBrandConfirmed(context, done); alarm.refresh() },
                )
            }

            SectionHeader(
                title = "Try it",
                supporting = "Lock your phone after tapping to see the full-screen alert",
            )
            TestAlarmButton()
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun CheckCard(
    check: AlarmCheck,
    onFix: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    val palette = LocalStatusPalette.current
    BevestCard(Modifier.fillMaxWidth(), accent = if (check.ok) null else palette.warning) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(
                if (check.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = if (check.ok) "Done" else "Needs attention",
                tint = if (check.ok) palette.normal else palette.warning,
                modifier = Modifier.size(IconSize.large),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(check.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!check.ok && !check.confirmable) TextButton(onClick = onFix) { Text("Fix") }
        }

        if (check.steps.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            check.steps.forEachIndexed { i, step ->
                Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (check.confirmable) {
            Spacer(Modifier.height(Spacing.sm))
            SecondaryButton(text = "Open settings", onClick = onFix, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = check.ok, onCheckedChange = onConfirm)
                Text("I've done this", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Counts down so there's time to lock the phone and see the full-screen alert. */
@Composable
private fun TestAlarmButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var countdown by remember { mutableStateOf<Int?>(null) }
    PrimaryButton(
        text = countdown?.let { "Alarm in $it…" } ?: "Test the alarm",
        icon = BevestIcons.Alerts,
        enabled = countdown == null,
        onClick = {
            scope.launch {
                for (s in TEST_DELAY_SECONDS downTo 1) { countdown = s; delay(1_000) }
                countdown = null
                AlarmReadiness.test(context)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val TEST_DELAY_SECONDS = 5
