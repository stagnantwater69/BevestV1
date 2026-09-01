package com.jtexpress.bevest.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BevestOrange,
    onPrimary = Color.White,
    primaryContainer = BevestOrangeLightContainer,
    onPrimaryContainer = Color(0xFF3A1500),
    secondary = Color(0xFF775A44),
    onSecondary = Color.White,
    background = AppWhite,
    onBackground = WarmInk,
    surface = AppWhite,
    onSurface = WarmInk,
    surfaceVariant = WarmSurfaceLight,
    onSurfaceVariant = Color(0xFF52443B),
    outline = Color(0xFFCFC6BC),
    outlineVariant = Color(0xFFE6DFD6),
    error = StatusDangerLight,
    onError = Color.White,
)

// Kept for a possible future dark option; the app currently ships light only.
private val DarkColors = darkColorScheme(
    primary = BevestOrangeDark,
    onPrimary = Color(0xFF3A1500),
    primaryContainer = BevestOrangeDarkContainer,
    onPrimaryContainer = BevestOrangeLightContainer,
    secondary = Color(0xFFE6BEA6),
    onSecondary = Color(0xFF432B1B),
    background = WarmInkDark,
    onBackground = WarmOnDark,
    surface = WarmInkDark,
    onSurface = WarmOnDark,
    surfaceVariant = WarmSurfaceDark,
    onSurfaceVariant = Color(0xFFCBBFB2),
    error = StatusDangerDark,
    onError = Color(0xFF3A0A05),
)

/**
 * BeVest is a light-themed app: white ground, dark ink, orange accent. The system
 * dark-mode setting is intentionally ignored so the safety UI reads the same for
 * everyone, in any lighting.
 */
@Composable
fun BevestTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusPalette = if (darkTheme) DarkStatusPalette else LightStatusPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalStatusPalette provides statusPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
