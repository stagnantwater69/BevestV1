package com.jtexpress.bevest.devgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.ui.common.EmergencyBanner
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.MetricCard
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.SiteCount
import com.jtexpress.bevest.ui.common.SiteStatusPanel
import com.jtexpress.bevest.ui.common.StatusBar
import com.jtexpress.bevest.ui.common.StatusChip
import com.jtexpress.bevest.ui.common.StatusSlice
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestTheme
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * Debug-only visual harness for the design system's custom-drawn pieces — the chamfered
 * shape, the shield, the ECG trace, the hazard stripe and the reflective band. None of
 * these can be checked by the compiler, and all of them are Canvas code.
 *
 * Debug source set, so it never reaches a release build. Launch with:
 *   adb shell am start -n com.jtexpress.bevest/.devgallery.DesignGalleryActivity
 */
class DesignGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BevestTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Gallery()
                }
            }
        }
    }
}

@Composable
private fun Gallery() {
    val palette = LocalStatusPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SectionHeader("Site status panel", supporting = "Chamfer + shield + band")
        SiteStatusPanel(
            status = SafetyStatus.DANGER,
            headline = "2 workers in danger",
            supporting = "Readings are at a dangerous level. Check on them.",
            counts = listOf(
                SiteCount(12, "On site"),
                SiteCount(3, "Warning", palette.warning),
                SiteCount(2, "Danger", palette.danger),
                SiteCount(1, "Offline", palette.offline),
            ),
        )

        SectionHeader("Emergency banner", supporting = "Hazard stripe + deep chamfer")
        EmergencyBanner(
            workerName = "Dela Cruz, Juan",
            reason = "No response to safety check",
            onView = {},
        )

        SectionHeader("Metric card", supporting = "Chamfer + ECG trace")
        MetricCard(
            icon = BevestIcons.HeartRate,
            label = "Heart rate",
            value = "142",
            unit = "BPM",
            accent = palette.warning,
            trace = listOf(88, 92, 96, 104, 99, 112, 121, 118, 130, 127, 138, 142),
        )
        MetricCard(
            icon = BevestIcons.Temperature,
            label = "Body temperature",
            value = "37.4",
            unit = "°C",
            accent = palette.normal,
        )

        SectionHeader("Status chips")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusChip(SafetyStatus.NORMAL, compact = true)
            StatusChip(SafetyStatus.WARNING, compact = true)
            StatusChip(SafetyStatus.DANGER, compact = true)
        }

        SectionHeader("Status bar", supporting = "Replaces the donut")
        StatusBar(
            slices = listOf(
                StatusSlice("Normal", 9, palette.normal),
                StatusSlice("Warning", 3, palette.warning),
                StatusSlice("Danger", 1, palette.danger),
            ),
        )

        SectionHeader("Buttons")
        PrimaryButton(text = "Primary action", onClick = {}, icon = BevestIcons.Add)

        SectionHeader("Empty state")
        EmptyState(
            title = "No workers yet",
            message = "Register your first worker, then pair them with a vest.",
            icon = BevestIcons.NoWorkers,
            actionLabel = "Add worker",
            onAction = {},
        )
        Text("end", style = MaterialTheme.typography.labelSmall)
    }
}
