package com.jtexpress.bevest.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.ComponentHeight
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Motion
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * Search input.
 *
 * Two details that the previous per-screen copies each got slightly differently, and are
 * now settled in one place: the clear button only exists when there is something to
 * clear, and the search action dismisses the keyboard instead of leaving it covering the
 * results the user just asked for.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(IconSize.medium),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(IconSize.medium),
                    )
                }
            }
        },
        singleLine = true,
        shape = BevestShapes.control,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboard?.hide()
                focus.clearFocus()
            },
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = ComponentHeight.control),
    )
}

/** One option in a [FilterRow]. [count] is shown when positive. */
data class FilterOption(
    val key: String,
    val label: String,
    val count: Int = 0,
    val accent: Color? = null,
)

/**
 * Horizontal filter chips.
 *
 * Chips carry their result count so the user can see what a filter will give them before
 * they spend a tap on it — on a busy site, "Danger 0" is itself the answer. A chip with
 * a semantic [FilterOption.accent] tints when selected, so filtering to Danger looks
 * like danger rather than like brand orange.
 */
@Composable
fun FilterRow(
    options: List<FilterOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            val selected = option.key == selectedKey
            val accent = option.accent ?: MaterialTheme.colorScheme.primary
            val container by animateColorAsState(
                targetValue = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                animationSpec = tween(Motion.QUICK),
                label = "filterChipContainer",
            )
            FilterChip(
                selected = selected,
                onClick = { if (!selected) onSelect(option.key) },
                label = {
                    Text(
                        buildString {
                            append(option.label)
                            if (option.count > 0) append("  ${option.count}")
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                shape = BevestShapes.pill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = container,
                    selectedLabelColor = accent,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = accent.copy(alpha = 0.45f),
                    selectedBorderWidth = 1.dp,
                ),
                modifier = Modifier.heightIn(min = ComponentHeight.chip),
            )
        }
    }
}

/**
 * The app's text field.
 *
 * Everything a form field needs is decided here rather than at each call site: required
 * fields are marked in the label (not by color, and not only in an error that appears
 * after the fact), the error message replaces the helper text in the same slot so the
 * layout never jumps, and the IME action moves to the next field or submits on the last
 * one.
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    required: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onImeAction: (() -> Unit)? = null,
) {
    val palette = LocalStatusPalette.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = {
            Text(if (required) "$label *" else label)
        },
        isError = error != null,
        supportingText = when {
            // One slot, two purposes — so validating a field cannot reflow the form.
            error != null -> {
                { Text(error, color = palette.danger) }
            }
            helper != null -> {
                { Text(helper) }
            }
            else -> null
        },
        singleLine = singleLine,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
            onDone = {
                keyboard?.hide()
                onImeAction?.invoke()
            },
            onGo = {
                keyboard?.hide()
                onImeAction?.invoke()
            },
        ),
        shape = BevestShapes.control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            errorBorderColor = palette.danger,
            errorLabelColor = palette.danger,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A titled group of form fields. Long forms are broken into these so a user filling one
 * in on site can see how much is left, and so a validation error can be traced to a
 * section rather than to a wall of inputs.
 */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(start = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                title.uppercase(),
                style = com.jtexpress.bevest.ui.theme.EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Surface(
            shape = BevestShapes.card,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.foundation.layout.Column(
                Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) { content() }
        }
    }
}
