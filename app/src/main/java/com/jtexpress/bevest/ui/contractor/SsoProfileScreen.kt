package com.jtexpress.bevest.ui.contractor

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.ui.common.BevestCard
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorNote
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.FormSection
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatusBadge
import com.jtexpress.bevest.ui.common.WorkerAvatar
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.IconSize
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * One Site Safety Officer, as their contractor sees them: who they are, the site they
 * monitor and the workers on it — and an edit form to change their details or move
 * them to another site. Mirrors the web contractor's officer profile.
 */
@Composable
fun SsoProfileScreen(
    contractorId: String,
    uid: String,
    onBack: () -> Unit,
    viewModel: SsoProfileViewModel = hiltViewModel(),
) {
    LaunchedEffect(uid) { viewModel.start(contractorId, uid) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val sso = state.sso
    val edit = state.edit

    BevestScaffold(
        title = if (edit != null) "Edit officer" else (sso?.fullName?.ifBlank { null } ?: sso?.email ?: "Officer profile"),
        subtitle = "Site Safety Officer",
        // Backing out of the form returns to the profile rather than leaving it.
        onBack = if (edit != null) viewModel::cancelEdit else onBack,
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            sso == null -> EmptyState(
                title = "Officer not found",
                message = "This officer may have been removed.",
                icon = BevestIcons.Officer,
                modifier = Modifier.padding(padding),
            )

            edit != null -> SsoEditForm(
                form = edit,
                email = sso.email,
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val palette = LocalStatusPalette.current
                BevestCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        WorkerAvatar(name = sso.fullName.ifBlank { sso.email }, photoUrl = null, size = 56.dp)
                        Text(
                            sso.fullName.ifBlank { sso.email },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        StatusBadge(
                            text = if (sso.active) "Active" else "Disabled",
                            color = if (sso.active) palette.normal else palette.offline,
                        )
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        DetailLine(Icons.Outlined.Email, sso.email.ifBlank { "—" })
                        DetailLine(Icons.Outlined.Phone, sso.phone.ifBlank { "No phone number" })
                        val site = state.siteOptions.firstOrNull { it.projectId == sso.siteId }
                        DetailLine(
                            BevestIcons.Location,
                            if (sso.siteId.isNullOrBlank()) {
                                "No site assigned"
                            } else {
                                state.siteName(sso.siteId) +
                                    (site?.location?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                            },
                        )
                    }
                }

                SecondaryButton(
                    text = "Edit profile",
                    onClick = viewModel::startEdit,
                    icon = BevestIcons.Officer,
                )

                val workers = state.siteWorkers
                SectionHeader(
                    title = "Assigned workers",
                    supporting = if (sso.siteId.isNullOrBlank()) {
                        "Assign a site to this officer to see monitored workers"
                    } else {
                        "${workers.size} ${if (workers.size == 1) "worker" else "workers"} at " +
                            state.siteName(sso.siteId)
                    },
                )
                if (workers.isEmpty()) {
                    EmptyState(
                        title = if (sso.siteId.isNullOrBlank()) "No site assigned" else "No workers on this site yet",
                        message = if (sso.siteId.isNullOrBlank()) {
                            "Tap \"Edit profile\" to assign this officer to a site."
                        } else {
                            "Workers added to this site will appear here."
                        },
                        icon = BevestIcons.NoWorkers,
                        modifier = Modifier.heightIn(min = 200.dp),
                    )
                } else {
                    workers.forEach { w ->
                        PersonRow(
                            name = w.fullName.ifBlank { w.workerId },
                            email = "Worker ID ${w.workerId}" + if (w.phone.isNotBlank()) " · ${w.phone}" else "",
                            supporting = w.assignedVestId?.takeIf { it.isNotBlank() }?.let { "Vest $it" }
                                ?: "No vest paired",
                            active = w.active,
                            trailing = {
                                StatusBadge(
                                    text = if (w.active) "Active" else "Inactive",
                                    color = if (w.active) palette.normal else palette.offline,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.medium),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SsoEditForm(
    form: SsoEditForm,
    email: String,
    state: SsoProfileState,
    viewModel: SsoProfileViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        FormSection(title = "Name") {
            FormField(
                form.firstName,
                { viewModel.update("firstName", it) },
                "First name",
                error = form.errors["firstName"],
                required = true,
            )
            FormField(
                form.lastName,
                { viewModel.update("lastName", it) },
                "Last name",
                error = form.errors["lastName"],
                required = true,
            )
        }

        FormSection(title = "Contact") {
            FormField(
                email,
                {},
                "Email",
                helper = "The sign-in email cannot be changed",
                enabled = false,
            )
            FormField(
                form.phone,
                { viewModel.update("phone", it) },
                "Phone",
                error = form.errors["phone"],
                keyboardType = KeyboardType.Phone,
            )
        }

        FormSection(title = "Assigned site") {
            SitePicker(form = form, state = state, onPick = viewModel::pickSite)
            if (form.customSite) {
                FormField(
                    form.siteId,
                    { viewModel.update("siteId", it) },
                    "Custom site ID",
                    error = form.errors["siteId"],
                    imeAction = ImeAction.Done,
                    onImeAction = viewModel::save,
                )
            }
            Text(
                "Moving the officer to another site changes which workers they monitor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        form.formError?.let { ErrorNote(it) }

        PrimaryButton(
            text = "Save changes",
            onClick = viewModel::save,
            loading = form.saving,
            loadingText = "Saving…",
        )
        SecondaryButton(text = "Cancel", onClick = viewModel::cancelEdit)
        Spacer(Modifier.height(Spacing.lg))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SitePicker(
    form: SsoEditForm,
    state: SsoProfileState,
    onPick: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when {
        form.customSite -> "Custom site ID…"
        form.siteId.isBlank() -> "No site assigned"
        else -> state.siteName(form.siteId)
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Site") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = BevestShapes.control,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("No site assigned") },
                onClick = { onPick(""); expanded = false },
            )
            state.siteOptions.forEach { site ->
                DropdownMenuItem(
                    text = {
                        Text(site.name + if (site.location.isNotBlank()) " — ${site.location}" else "")
                    },
                    onClick = { onPick(site.projectId); expanded = false },
                )
            }
            DropdownMenuItem(
                text = { Text("+ Enter custom site ID…") },
                onClick = { onPick(null); expanded = false },
            )
        }
    }
}
