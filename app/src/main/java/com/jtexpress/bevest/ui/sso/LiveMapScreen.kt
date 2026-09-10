package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.ReflectiveBand
import com.jtexpress.bevest.ui.common.SkeletonBox
import com.jtexpress.bevest.ui.common.label
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.Elevation
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * Everyone on site, positioned.
 *
 * Two things a bare map of pins does not give you, both added here as overlays rather
 * than as chrome that eats the map:
 *
 *  - a **legend**, because a colored pin means nothing until you know the scale, and the
 *    map is the one screen where the status chip's written label cannot travel with the
 *    marker;
 *  - a **count of who is missing**, because the most dangerous thing about a live map is
 *    quietly not showing the worker whose GPS dropped out. A map that looks complete
 *    when it is not is worse than no map.
 */
@Composable
fun LiveMapScreen(
    siteId: String?,
    onOpenWorker: (String) -> Unit,
    viewModel: LiveMapViewModel = hiltViewModel(),
) {
    LaunchedEffect(siteId) { viewModel.start(siteId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    BevestScaffold(title = "Live map", subtitle = siteId) { padding ->
        when {
            state.loading -> SkeletonBox(Modifier.fillMaxSize().padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            state.noSite -> EmptyState(
                title = "No site assigned",
                message = "Ask your contractor to assign you to a project site.",
                icon = BevestIcons.Location,
                modifier = Modifier.padding(padding),
            )

            state.located.isEmpty() -> EmptyState(
                title = "No positions yet",
                message = if (state.markers.isEmpty()) {
                    "No workers are registered on this site."
                } else {
                    "None of the ${state.markers.size} workers on this site are reporting " +
                        "GPS right now. Check their vests are powered on and outdoors."
                },
                icon = BevestIcons.Location,
                modifier = Modifier.padding(padding),
            )

            else -> {
                val first = state.located.first()
                val center = LatLng(first.reading!!.latitude!!, first.reading.longitude!!)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(center, 15f)
                }
                val missing = state.markers.size - state.located.size

                Box(Modifier.fillMaxSize().padding(padding)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false),
                    ) {
                        state.located.forEach { live ->
                            val pos = LatLng(live.reading!!.latitude!!, live.reading.longitude!!)
                            Marker(
                                state = MarkerState(position = pos),
                                title = live.worker.fullName.ifBlank { live.worker.workerId },
                                snippet = buildString {
                                    append(live.status.label())
                                    if (live.stale) append(" · last known position")
                                },
                                // Hue-matched to the status palette so a pin means the
                                // same thing here as a chip does everywhere else.
                                icon = BitmapDescriptorFactory.defaultMarker(
                                    markerHue(live.status),
                                ),
                                onInfoWindowClick = { onOpenWorker(live.worker.workerId) },
                            )
                        }
                    }

                    MapLegend(
                        located = state.located.size,
                        missing = missing,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(Spacing.lg),
                    )
                }
            }
        }
    }
}

/**
 * Google Maps only accepts a hue for its default marker, so the exact palette colors
 * cannot be used. These are the nearest hues that keep the same ordering and the same
 * red/amber/green reading an officer expects.
 */
private fun markerHue(status: SafetyStatus): Float = when (status) {
    SafetyStatus.NORMAL -> BitmapDescriptorFactory.HUE_GREEN
    SafetyStatus.WARNING -> BitmapDescriptorFactory.HUE_ORANGE
    SafetyStatus.DANGER -> BitmapDescriptorFactory.HUE_RED
    SafetyStatus.EMERGENCY -> BitmapDescriptorFactory.HUE_ROSE
    SafetyStatus.OFFLINE -> BitmapDescriptorFactory.HUE_AZURE
}

@Composable
private fun MapLegend(located: Int, missing: Int, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BevestShapes.card,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Elevation.sheet,
    ) {
        Column {
            ReflectiveBand(thickness = 2.dp, emphasis = 0.7f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendKey("Normal", palette.normal)
                LegendKey("Warning", palette.warning)
                LegendKey("Danger", palette.danger)
                LegendKey("Offline", palette.offline)
            }
            // The honest footnote: this map is not everyone.
            if (missing > 0) {
                Text(
                    text = if (missing == 1) {
                        "1 worker has no GPS fix and is not shown."
                    } else {
                        "$missing workers have no GPS fix and are not shown."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.warning,
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.md,
                    ),
                )
            } else {
                Text(
                    text = if (located == 1) {
                        "1 worker positioned."
                    } else {
                        "All $located workers positioned."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.md,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LegendKey(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label marker color"
        },
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(BevestShapes.round)
                .background(color),
        )
        Text(
            label.uppercase(),
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
