package com.jtexpress.bevest.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.BuildConfig
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.DetailSkeleton
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.FormSkeleton
import com.jtexpress.bevest.ui.common.WorkerAvatar
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing

/** Human-readable role name — enum constants are not user-facing copy. */
private fun UserRole.label(): String = when (this) {
    UserRole.ADMIN -> "Administrator"
    UserRole.CONTRACTOR -> "Contractor"
    UserRole.SSO -> "Site Safety Officer"
    UserRole.UNKNOWN -> "No role assigned"
}

@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var confirmSignOut by remember { mutableStateOf(false) }

    BevestScaffold(title = "Profile", onBack = onBack) { padding ->
        val u = user
        if (u == null) {
            DetailSkeleton(Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                IdentityHeader(u)

                SettingsGroup(title = "Account") {
                    InfoRow(Icons.Outlined.Email, "Email", u.email.ifBlank { "—" })
                    RowDivider()
                    InfoRow(Icons.Outlined.Phone, "Phone", u.phone.ifBlank { "Not set" })
                    if (u.role == UserRole.SSO) {
                        RowDivider()
                        InfoRow(BevestIcons.Location, "Assigned site", u.siteId ?: "Unassigned")
                    }
                }

                SettingsGroup(title = "Settings") {
                    ActionRow(
                        icon = Icons.Outlined.Edit,
                        label = "Edit profile",
                        supporting = "Name and phone number",
                        onClick = onEditProfile,
                    )
                    RowDivider()
                    ActionRow(
                        icon = Icons.Outlined.Lock,
                        label = "Change password",
                        supporting = "Update your sign-in password",
                        onClick = onChangePassword,
                    )
                }

                SignOutButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        confirmSignOut = true
                    },
                )

                AppVersionFooter()
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    if (confirmSignOut) {
        ConfirmationDialog(
            title = "Sign out?",
            message = "You will stop receiving safety alerts on this device until you sign back in.",
            confirmLabel = "Sign out",
            onConfirm = { confirmSignOut = false; onSignOut() },
            onDismiss = { confirmSignOut = false },
        )
    }
}

/** Avatar, name, and a role badge — who you are, at a glance. */
@Composable
private fun IdentityHeader(user: User) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    ) {
        Column(
            Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            WorkerAvatar(
                name = user.fullName.ifBlank { user.email },
                photoUrl = null,
                size = 84.dp,
            )
            Text(
                user.fullName.ifBlank { "Unnamed user" },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RoleBadge(user.role)
        }
    }
}

@Composable
private fun RoleBadge(role: UserRole) {
    val palette = LocalStatusPalette.current
    val color = if (role == UserRole.UNKNOWN) palette.warning else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(Radius.pill))
            .padding(horizontal = Spacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = when (role) {
                UserRole.ADMIN -> BevestIcons.System
                UserRole.CONTRACTOR -> BevestIcons.Contractors
                UserRole.SSO -> BevestIcons.Officer
                UserRole.UNKNOWN -> BevestIcons.Error
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp),
        )
        Text(role.label().uppercase(), style = EyebrowStyle, color = color)
    }
}

/** A titled card that groups related rows, the way settings screens are expected to read. */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            title.uppercase(),
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun RowDivider() {
    Divider(
        modifier = Modifier.padding(start = 60.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
    )
}

/** Read-only fact: icon, label, value. */
@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tappable row with a chevron — looks interactive because it is. */
@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    supporting: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    RoundedCornerShape(Radius.sm),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
    val palette = LocalStatusPalette.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.danger),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Logout,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
        )
        Text(
            "Sign out",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

@Composable
private fun AppVersionFooter() {
    Text(
        "BeVest ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ------------------------------------------------------------------ edit

@Composable
fun EditProfileScreen(
    onDone: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    BevestScaffold(title = "Edit profile", onBack = onDone) { padding ->
        if (!state.loaded) {
            FormSkeleton(modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                FormField(state.firstName, viewModel::onFirstName, "First name", state.firstNameError)
                FormField(state.lastName, viewModel::onLastName, "Last name", state.lastNameError)
                FormField(
                    state.phone,
                    viewModel::onPhone,
                    "Phone number",
                    state.phoneError,
                    keyboardType = KeyboardType.Phone,
                )
                state.formError?.let { FormError(it) }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.submitting,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text(
                        if (state.submitting) "Saving…" else "Save changes",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    "Your email and role are managed by your administrator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -------------------------------------------------------------- password

@Composable
fun ChangePasswordScreen(
    onDone: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(state.changed) { if (state.changed) onDone() }

    BevestScaffold(title = "Change password", onBack = onDone) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            FormField(state.current, viewModel::onCurrent, "Current password", state.currentError, isPassword = true)
            FormField(state.next, viewModel::onNext, "New password", state.nextError, isPassword = true)
            FormField(state.confirm, viewModel::onConfirm, "Confirm new password", state.confirmError, isPassword = true)
            Text(
                "Use at least 6 characters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.formError?.let { FormError(it) }
            Button(
                onClick = viewModel::submit,
                enabled = !state.submitting,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(
                    if (state.submitting) "Updating…" else "Update password",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Inline form error, styled the same everywhere it appears. */
@Composable
private fun FormError(message: String) {
    val palette = LocalStatusPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.sm),
        color = palette.danger.copy(alpha = 0.10f),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                BevestIcons.Error,
                contentDescription = null,
                tint = palette.danger,
                modifier = Modifier.size(18.dp),
            )
            Text(message, style = MaterialTheme.typography.bodySmall, color = palette.danger)
        }
    }
}
