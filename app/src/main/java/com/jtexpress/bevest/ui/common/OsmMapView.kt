package com.jtexpress.bevest.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jtexpress.bevest.domain.model.SafetyStatus
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class OsmPin(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String? = null,
    val status: SafetyStatus = SafetyStatus.NORMAL,
)

/**
 * Launches the phone's external map app (Google Maps / Waze / default handler)
 * pinned to the given GPS coordinates.
 */
fun openExternalMap(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String = "Worker Location",
) {
    val encodedLabel = Uri.encode(label)
    val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        val webUri = Uri.parse("https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=18/$latitude/$longitude")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Free, zero-API-key interactive OpenStreetMap view powered by OSMDroid.
 * Supports single-worker live tracking (`WorkerDetailScreen`) and multi-worker site maps (`LiveMapScreen`).
 */
@Composable
fun OsmMapView(
    pins: List<OsmPin>,
    modifier: Modifier = Modifier,
    initialZoom: Double = 17.5,
    followSinglePin: Boolean = true,
    showControls: Boolean = true,
    onPinClick: ((OsmPin) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = LocalStatusPalette.current

    // Configure OSMDroid user-agent once so OpenStreetMap tile servers allow requests
    remember(context) {
        Configuration.getInstance().userAgentValue = context.packageName.ifBlank { "com.jtexpress.bevest" }
        true
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var hasInitialCentered by remember { mutableStateOf(false) }

    // Pre-build status marker drawables for crisp rendering
    val markerIcons = remember(context, palette) {
        mapOf(
            SafetyStatus.NORMAL to createPinDrawable(context, palette.normal),
            SafetyStatus.WARNING to createPinDrawable(context, palette.warning),
            SafetyStatus.DANGER to createPinDrawable(context, palette.danger),
            SafetyStatus.EMERGENCY to createPinDrawable(context, palette.emergency),
            SafetyStatus.OFFLINE to createPinDrawable(context, palette.offline),
        )
    }

    val primaryPoint = remember(pins) {
        pins.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
    }

    // Smoothly animate camera when single pin coordinates update live
    LaunchedEffect(primaryPoint?.latitude, primaryPoint?.longitude, pins.size) {
        val map = mapViewRef ?: return@LaunchedEffect
        val first = primaryPoint ?: return@LaunchedEffect
        if (!hasInitialCentered) {
            hasInitialCentered = true
            map.controller.setZoom(initialZoom)
            map.controller.setCenter(first)
        } else if (followSinglePin && pins.size == 1) {
            map.controller.animateTo(first)
        }
    }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    minZoomLevel = 4.0
                    maxZoomLevel = 20.0
                    controller.setZoom(initialZoom)
                    primaryPoint?.let {
                        controller.setCenter(it)
                        hasInitialCentered = true
                    }

                    // Prevent parent verticalScroll from stealing touch gestures while panning map
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE,
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                    mapViewRef = this
                }
            },
            update = { map ->
                map.overlays.removeAll { it is Marker }
                pins.forEach { pin ->
                    val marker = Marker(map).apply {
                        id = pin.id
                        position = GeoPoint(pin.latitude, pin.longitude)
                        title = pin.title
                        snippet = pin.snippet
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = markerIcons[pin.status] ?: markerIcons[SafetyStatus.NORMAL]
                        setOnMarkerClickListener { m, _ ->
                            if (onPinClick != null) {
                                if (m.isInfoWindowShown) {
                                    onPinClick(pin)
                                } else {
                                    m.showInfoWindow()
                                    map.controller.animateTo(m.position)
                                }
                            } else {
                                if (m.isInfoWindowShown) m.closeInfoWindow() else m.showInfoWindow()
                                map.controller.animateTo(m.position)
                            }
                            true
                        }
                    }
                    map.overlays.add(marker)
                }
                map.invalidate()
            },
        )

        if (showControls && pins.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        val map = mapViewRef ?: return@SmallFloatingActionButton
                        if (pins.size == 1) {
                            val p = pins.first()
                            map.controller.animateTo(GeoPoint(p.latitude, p.longitude), initialZoom, 600L)
                        } else if (pins.size > 1) {
                            val lats = pins.map { it.latitude }
                            val lngs = pins.map { it.longitude }
                            val box = BoundingBox(
                                (lats.maxOrNull() ?: 0.0) + 0.0008,
                                (lngs.maxOrNull() ?: 0.0) + 0.0008,
                                (lats.minOrNull() ?: 0.0) - 0.0008,
                                (lngs.minOrNull() ?: 0.0) - 0.0008,
                            )
                            map.zoomToBoundingBox(box, true, 64)
                        }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MyLocation,
                        contentDescription = "Center on GPS location",
                        modifier = Modifier.size(18.dp),
                    )
                }

                SmallFloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomIn() },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Zoom in",
                        modifier = Modifier.size(18.dp),
                    )
                }

                SmallFloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomOut() },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Zoom out",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Renders a high-contrast teardrop map pin in the status color with a white border and inner dot.
 */
private fun createPinDrawable(context: Context, color: Color): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (36f * density).toInt().coerceAtLeast(36)
    val height = (46f * density).toInt().coerceAtLeast(46)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val cy = width / 2f
    val radius = (width / 2f) - (3f * density)

    val pinPath = Path().apply {
        addCircle(cx, cy, radius, Path.Direction.CW)
        moveTo(cx - radius * 0.65f, cy + radius * 0.55f)
        lineTo(cx, height - 2f * density)
        lineTo(cx + radius * 0.65f, cy + radius * 0.55f)
        close()
    }

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = android.graphics.Color.argb(55, 0, 0, 0)
    }
    canvas.save()
    canvas.translate(0f, 1.5f * density)
    canvas.drawPath(pinPath, shadowPaint)
    canvas.restore()

    // White outer border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = android.graphics.Color.WHITE
    }
    canvas.drawPath(pinPath, borderPaint)

    // Colored inner body
    val innerRadius = radius - (2.2f * density)
    val innerPath = Path().apply {
        addCircle(cx, cy, innerRadius, Path.Direction.CW)
        moveTo(cx - innerRadius * 0.62f, cy + innerRadius * 0.52f)
        lineTo(cx, height - 4.8f * density)
        lineTo(cx + innerRadius * 0.62f, cy + innerRadius * 0.52f)
        close()
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color.toArgb()
    }
    canvas.drawPath(innerPath, fillPaint)

    // White center dot
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(cx, cy, innerRadius * 0.38f, dotPaint)

    return BitmapDrawable(context.resources, bitmap)
}

