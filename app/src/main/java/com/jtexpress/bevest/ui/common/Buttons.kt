package com.jtexpress.bevest.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.ComponentHeight
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * The app's three button weights. Screens pick a weight by what the action *is*, not by
 * how it should look, which is what keeps the hierarchy consistent between screens
 * written months apart.
 *
 * Every one of them:
 *  - is at least [ComponentHeight.control] tall, for gloved hands;
 *  - swaps its label for a spinner while working, rather than going dead and silent;
 *  - stays disabled while working, so a double tap cannot fire twice.
 */

/** The one thing this screen is for. At most one per screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = BevestShapes.control,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ComponentHeight.button),
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            loading = loading,
            loadingText = loadingText,
            spinnerColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** A supporting action — somewhere else to go, something optional to do. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = BevestShapes.control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ComponentHeight.control),
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            loading = loading,
            loadingText = null,
            spinnerColor = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Destructive or escalating: unassign a vest, deactivate a worker, start a safety
 * response. Carries a haptic thump on press — the physical confirmation that you have
 * committed to something, before any dialog appears.
 *
 * Set [filled] for an action that starts an emergency procedure; leave it off for one
 * that merely removes or disables something.
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    filled: Boolean = false,
) {
    val danger = LocalStatusPalette.current.danger
    val haptics = LocalHapticFeedback.current
    val press = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }

    if (filled) {
        Button(
            onClick = press,
            enabled = enabled && !loading,
            shape = BevestShapes.control,
            colors = ButtonDefaults.buttonColors(
                containerColor = danger,
                contentColor = Color.White,
            ),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = ComponentHeight.emergency),
        ) {
            ButtonContent(text, icon, loading, null, Color.White)
        }
    } else {
        OutlinedButton(
            onClick = press,
            enabled = enabled && !loading,
            shape = BevestShapes.control,
            border = BorderStroke(1.dp, danger.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = danger),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = ComponentHeight.control),
        ) {
            ButtonContent(text, icon, loading, null, danger)
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
    loading: Boolean,
    loadingText: String?,
    spinnerColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(IconSize.medium),
                strokeWidth = 2.dp,
                color = spinnerColor,
            )
            // A working button that also says what it is doing beats a bare spinner.
            if (loadingText != null) {
                Text(loadingText, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.medium),
                )
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
