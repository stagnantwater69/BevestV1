package com.jtexpress.bevest.ui.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
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
import androidx.compose.runtime.remember
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
import java.io.File

data class OsmPin(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String? = null,
    val status: SafetyStatus = SafetyStatus.NORMAL,
)

private class OsmMapHolder {
    var mapView: MapView? = null
    var hasInitialCentered: Boolean = false
    var lastPins: List<OsmPin> = emptyList()
}

/**
 * Wraps a Context so legacy receiver registrations inside OSMDroid always supply
 * [Context.RECEIVER_NOT_EXPORTED] on Android 13/14+ (API 33/34), preventing
 * SecurityException crashes when opening any screen containing a MapView.
 */
private class OsmSafeContextWrapper(base: Context) : ContextWrapper(base) {
    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?,
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler, Context.RECEIVER_NOT_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }
    }
}

private fun ensureOsmConfigured(context: Context) {
    val appCtx = context.applicationContext
    val basePath = File(appCtx.cacheDir, "osmdroid").apply { mkdirs() }
    val tileCache = File(basePath, "tiles").apply { mkdirs() }
    Configuration.getInstance().apply {
        userAgentValue = appCtx.packageName.ifBlank { "com.jtexpress.bevest" }
        osmdroidBasePath = basePath
        osmdroidTileCache = tileCache
    }
}

private fun OsmPin.hasValidCoords(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -85.05..85.05 &&
        longitude in -180.0..180.0

/**
 * Opens the given GPS coordinates directly in Google Maps (new browser tab or Google Maps app).
 */
fun openInGoogleMaps(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String = "Worker Location",
) {
    val googleMapsWebUri = Uri.parse(
        "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude",
    )
    val webIntent = Intent(Intent.ACTION_VIEW, googleMapsWebUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(webIntent)
    } catch (_: Exception) {
        val encodedLabel = Uri.encode(label)
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, geoUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/**
 * Launches the phone's external map app (Google Maps / Waze / default handler)
 * pinned to the given GPS coordinates.
 */
fun openExternalMap(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String = "Worker Location",
) = openInGoogleMaps(context, latitude, longitude, label)

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

    // Configure OSMDroid user-agent & app-private tile cache before creating MapView
    remember(context) {
        runCatching { ensureOsmConfigured(context) }
        true
    }

    val holder = remember { OsmMapHolder() }
    val validPins = remember(pins) { pins.filter { it.hasValidCoords() } }

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

    val primaryPoint = remember(validPins) {
        validPins.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
    }

    // Smoothly animate camera when single pin coordinates update live
    LaunchedEffect(primaryPoint?.latitude, primaryPoint?.longitude, validPins.size) {
        val map = holder.mapView ?: return@LaunchedEffect
        val first = primaryPoint ?: return@LaunchedEffect
        runCatching {
            if (!holder.hasInitialCentered) {
                holder.hasInitialCentered = true
                map.controller.setZoom(initialZoom)
                map.controller.setCenter(first)
            } else if (followSinglePin && validPins.size == 1) {
                map.controller.animateTo(first)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { holder.mapView?.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { holder.mapView?.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                runCatching { ensureOsmConfigured(ctx) }
                val safeCtx = OsmSafeContextWrapper(ctx)
                MapView(safeCtx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    minZoomLevel = 4.0
                    maxZoomLevel = 20.0
                    controller.setZoom(initialZoom)
                    primaryPoint?.let {
                        controller.setCenter(it)
                        holder.hasInitialCentered = true
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
                    holder.mapView = this
                }
            },
            update = { map ->
                if (holder.lastPins == validPins) return@AndroidView
                holder.lastPins = validPins
                runCatching {
                    val existingMarkers = map.overlays.filterIsInstance<Marker>()
                    existingMarkers.forEach { m ->
                        runCatching { m.closeInfoWindow() }
                        runCatching { m.onDetach(map) }
                    }
                    map.overlays.removeAll(existingMarkers.toSet())

                    validPins.forEach { pin ->
                        val marker = Marker(map).apply {
                            id = pin.id
                            position = GeoPoint(pin.latitude, pin.longitude)
                            title = pin.title
                            snippet = pin.snippet
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = markerIcons[pin.status] ?: markerIcons[SafetyStatus.NORMAL]
                            setOnMarkerClickListener { m, _ ->
                                runCatching {
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
                                }
                                true
                            }
                        }
                        map.overlays.add(marker)
                    }
                    map.invalidate()
                }
            },
            onRelease = { map ->
                runCatching {
                    map.overlays.filterIsInstance<Marker>().forEach { m ->
                        runCatching { m.closeInfoWindow() }
                        runCatching { m.onDetach(map) }
                    }
                    map.overlays.clear()
                    map.onPause()
                    map.onDetach()
                }
                if (holder.mapView === map) {
                    holder.mapView = null
                }
                holder.lastPins = emptyList()
                holder.hasInitialCentered = false
            },
        )

        if (showControls && validPins.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        val map = holder.mapView ?: return@SmallFloatingActionButton
                        runCatching {
                            if (validPins.size == 1) {
                                val p = validPins.first()
                                map.controller.animateTo(GeoPoint(p.latitude, p.longitude), initialZoom, 600L)
                            } else if (validPins.size > 1) {
                                val lats = validPins.map { it.latitude }
                                val lngs = validPins.map { it.longitude }
                                val box = BoundingBox(
                                    ((lats.maxOrNull() ?: 0.0) + 0.0008).coerceAtMost(85.05),
                                    ((lngs.maxOrNull() ?: 0.0) + 0.0008).coerceAtMost(180.0),
                                    ((lats.minOrNull() ?: 0.0) - 0.0008).coerceAtLeast(-85.05),
                                    ((lngs.minOrNull() ?: 0.0) - 0.0008).coerceAtLeast(-180.0),
                                )
                                map.zoomToBoundingBox(box, true, 64)
                            }
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
                    onClick = { runCatching { holder.mapView?.controller?.zoomIn() } },
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
                    onClick = { runCatching { holder.mapView?.controller?.zoomOut() } },
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
