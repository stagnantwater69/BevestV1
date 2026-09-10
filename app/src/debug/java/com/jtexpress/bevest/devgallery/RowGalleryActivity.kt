package com.jtexpress.bevest.devgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jtexpress.bevest.domain.model.Alert
import com.jtexpress.bevest.domain.model.AlertSeverity
import com.jtexpress.bevest.domain.model.AlertStatus
import com.jtexpress.bevest.domain.model.AlertType
import com.jtexpress.bevest.domain.model.MotionState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.domain.model.SensorReading
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.ui.common.BarDatum
import com.jtexpress.bevest.ui.common.DetailRow
import com.jtexpress.bevest.ui.common.FilterOption
import com.jtexpress.bevest.ui.common.FilterRow
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.FormSection
import com.jtexpress.bevest.ui.common.InsightCard
import com.jtexpress.bevest.ui.common.SearchBar
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatusBar
import com.jtexpress.bevest.ui.common.StatusSlice
import com.jtexpress.bevest.ui.common.TrendBars
import com.jtexpress.bevest.ui.contractor.PersonRow
import com.jtexpress.bevest.ui.sso.AlertRow
import com.jtexpress.bevest.ui.sso.WorkerLive
import com.jtexpress.bevest.ui.sso.WorkerLiveRow
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestTheme
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * The second half of the debug harness: the list rows, forms and charts that the
 * signed-in screens are assembled from, driven with deliberately hostile data.
 *
 * The screens themselves need a Firebase session to reach, so this stands in for that.
 * Every case here is one that real data will eventually produce and that a happy-path
 * check would miss: names far longer than the column, a chart with a single bar, a
 * breakdown where every value is zero.
 *
 *   adb shell am start -n com.jtexpress.bevest/.devgallery.RowGalleryActivity
 */
class RowGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BevestTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RowGallery()
                }
            }
        }
    }
}

private const val LONG_NAME = "Bartholomew Maximilian Fitzgerald-Montgomery III"

private fun worker(name: String, id: String, vest: String?) = Worker(
    workerId = id,
    firstName = name.substringBefore(' '),
    lastName = name.substringAfter(' ', ""),
    assignedVestId = vest,
)

private fun reading(hr: Int?, temp: Double?) = SensorReading(
    heartRate = hr,
    temperature = temp,
    motionState = MotionState.MOVING,
    timestamp = System.currentTimeMillis() - 45_000,
)

@Composable
private fun RowGallery() {
    val palette = LocalStatusPalette.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var field by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SectionHeader("Worker rows", supporting = "Normal, then a name that overflows")
        WorkerLiveRow(
            live = WorkerLive(
                worker = worker("Juan Dela Cruz", "W-0142", "V-0087"),
                reading = reading(78, 36.6),
                status = SafetyStatus.NORMAL,
                stale = false,
            ),
            onClick = {},
        )
        WorkerLiveRow(
            live = WorkerLive(
                worker = worker(LONG_NAME, "W-0143-EXTENDED-IDENTIFIER", null),
                reading = reading(148, 39.2),
                status = SafetyStatus.DANGER,
                stale = false,
            ),
            onClick = {},
        )
        WorkerLiveRow(
            live = WorkerLive(
                worker = worker("Maria Santos", "W-0144", "V-0090"),
                reading = null,
                status = SafetyStatus.OFFLINE,
                stale = true,
            ),
            onClick = {},
        )

        SectionHeader("Alert rows", supporting = "Active vs resolved")
        AlertRow(
            alert = Alert(
                alertId = "a1",
                workerId = "W-0143",
                type = AlertType.NO_SAFETY_RESPONSE,
                severity = AlertSeverity.EMERGENCY,
                status = AlertStatus.ACTIVE,
                createdAt = System.currentTimeMillis() - 300_000,
            ),
            workerName = LONG_NAME,
            onClick = {},
        )
        AlertRow(
            alert = Alert(
                alertId = "a2",
                workerId = "W-0142",
                type = AlertType.LOW_BATTERY,
                severity = AlertSeverity.WARNING,
                status = AlertStatus.RESOLVED,
                createdAt = System.currentTimeMillis() - 86_400_000,
            ),
            workerName = "Juan Dela Cruz",
            onClick = {},
        )

        SectionHeader("Person row", supporting = "Active and disabled")
        PersonRow(
            name = LONG_NAME,
            email = "bartholomew.fitzgerald-montgomery@averylongdomainname.example.com",
            supporting = "Site SITE-NORTH-TOWER-PHASE-2",
            active = true,
            onClick = {},
        )
        PersonRow(
            name = "Ana Reyes",
            email = "ana@example.com",
            supporting = "No site assigned",
            active = false,
            action = {},
            actionLabel = "Disable access",
        )

        SectionHeader("Detail rows")
        DetailRow("Worker", "W-0143", icon = BevestIcons.Workers)
        DetailRow(
            "Message from the vest",
            "Worker did not acknowledge the safety check within 60 seconds. " +
                "Escalated automatically.",
            icon = BevestIcons.Signal,
        )

        SectionHeader("Status bar — all zeros", supporting = "The empty-data case")
        StatusBar(
            slices = listOf(
                StatusSlice("Normal", 0, palette.normal),
                StatusSlice("Warning", 0, palette.warning),
                StatusSlice("Danger", 0, palette.danger),
            ),
        )

        SectionHeader("Trend — single bar", supporting = "Minimum viable series")
        TrendBars(data = listOf(BarDatum("Jan", 3f)))

        SectionHeader("Trend — twelve bars", supporting = "A full year on a phone")
        TrendBars(
            data = listOf(
                "Jan" to 4f, "Feb" to 7f, "Mar" to 2f, "Apr" to 9f,
                "May" to 5f, "Jun" to 0f, "Jul" to 6f, "Aug" to 11f,
                "Sep" to 3f, "Oct" to 8f, "Nov" to 1f, "Dec" to 5f,
            ).map { BarDatum(it.first, it.second) },
        )

        SectionHeader("Insight card")
        InsightCard(
            "Down from 9 to 5 incidents since last month.",
            icon = BevestIcons.Reports,
            accent = palette.normal,
        )

        SectionHeader("Search and filters")
        SearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search name or ID")
        FilterRow(
            options = listOf(
                FilterOption("ALL", "All", 24),
                FilterOption("ACTIVE", "On site", 18),
                FilterOption("WARNING", "Warning", 3, palette.warning),
                FilterOption("DANGER", "Danger", 2, palette.danger),
                FilterOption("OFFLINE", "Offline", 1, palette.offline),
                FilterOption("UNASSIGNED", "No vest", 0),
            ),
            selectedKey = filter,
            onSelect = { filter = it },
        )

        SectionHeader("Form")
        FormSection(title = "Details") {
            FormField(field, { field = it }, "First name", required = true)
            FormField(
                "",
                {},
                "Phone number",
                error = "Enter a valid Philippine mobile number",
                required = true,
            )
            FormField("", {}, "Site", helper = "Optional — can be assigned later")
        }

        Text("end", style = MaterialTheme.typography.labelSmall)
    }
}
