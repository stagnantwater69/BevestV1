package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.SkeletonBox

@Composable
fun LiveMapScreen(
    siteId: String?,
    onOpenWorker: (String) -> Unit,
    viewModel: LiveMapViewModel = hiltViewModel(),
) {
    LaunchedEffect(siteId) { viewModel.start(siteId) }
    val state = viewModel.state.collectAsStateWithLifecycle().value

    BevestScaffold(title = "Live map") { padding ->
        when {
            state.loading -> SkeletonBox(
                Modifier.fillMaxSize().padding(padding),
            )
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            state.noSite -> EmptyState("No site assigned", "Ask your contractor to assign a site.", Modifier.padding(padding))
            state.located.isEmpty() -> EmptyState(
                "No worker positions",
                "No workers are reporting GPS right now.",
                Modifier.padding(padding),
            )
            else -> {
                val first = state.located.first()
                val center = LatLng(first.reading!!.latitude!!, first.reading.longitude!!)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(center, 15f)
                }
                Box(Modifier.fillMaxSize().padding(padding)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                    ) {
                        state.located.forEach { live ->
                            val pos = LatLng(live.reading!!.latitude!!, live.reading.longitude!!)
                            Marker(
                                state = MarkerState(position = pos),
                                title = live.worker.fullName.ifBlank { live.worker.workerId },
                                snippet = "${live.status.name}${if (live.stale) " · stale" else ""}",
                                onInfoWindowClick = { onOpenWorker(live.worker.workerId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
