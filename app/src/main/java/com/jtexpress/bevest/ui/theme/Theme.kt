package com.jtexpress.bevest.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BevestOrange,
    onPrimary = Color.White,
    primaryContainer = BevestOrangeLightContainer,
    onPrimaryContainer = Color(0xFF3A1500),
    secondary = Color(0xFF775A44),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E2D4),
    onSecondaryContainer = Color(0xFF2B1B0E),
    background = AppWhite,
    onBackground = WarmInk,
    surface = AppWhite,
    onSurface = WarmInk,
    surfaceVariant = WarmSurfaceLight,
    onSurfaceVariant = WarmInkMuted,
    outline = WarmOutlineLight,
    outlineVariant = WarmOutlineVariantLight,
    scrim = Color(0xFF000000),
    error = StatusDangerLight,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BevestOrangeDark,
    onPrimary = Color(0xFF3A1500),
    primaryContainer = BevestOrangeDarkContainer,
    onPrimaryContainer = BevestOrangeLightContainer,
    secondary = Color(0xFFE6BEA6),
    onSecondary = Color(0xFF432B1B),
    secondaryContainer = Color(0xFF3E2A1B),
    onSecondaryContainer = Color(0xFFF6E2D4),
    background = WarmInkDark,
    onBackground = WarmOnDark,
    surface = WarmSurfaceDark,
    onSurface = WarmOnDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = WarmOnDarkMuted,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,
    scrim = Color(0xFF000000),
    error = StatusDangerDark,
    onError = Color(0xFF3A0A05),
)

/**
 * BeVest is a light-themed app: warm-white ground, dark ink, orange accent. The system
 * dark-mode setting is intentionally ignored so the safety UI reads the same for
 * everyone, in any lighting. (A complete [DarkColors] scheme is kept below in case that
 * decision is revisited — pass `darkTheme = true` to preview it.)
 * System bars are transparent — the app draws edge to edge (see [MainActivity]).
 */
@Composable
fun BevestTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusPalette = if (darkTheme) DarkStatusPalette else LightStatusPalette
    val brandPalette = if (darkTheme) DarkBrandPalette else LightBrandPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Pre-Q can't tint the nav-bar icons, so keep a readable scrim.
                window.navigationBarColor = colorScheme.surface.copy(alpha = 0.6f).toArgbCompat()
            }
        }
    }

    CompositionLocalProvider(
        LocalStatusPalette provides statusPalette,
        LocalBrandPalette provides brandPalette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = MaterialShapes,
            content = content,
        )
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)
