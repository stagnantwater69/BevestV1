package com.jtexpress.bevest.ui.sso

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.FormSkeleton
import com.jtexpress.bevest.ui.common.WorkerAvatar
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.EyebrowStyle
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing

@Composable
fun AddEditWorkerScreen(
    user: User,
    workerId: String?,
    onDone: () -> Unit,
    viewModel: WorkerFormViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var confirmDeactivate by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(user.uid) {
        val contractorId =
            if (user.role == UserRole.SSO) user.contractorId else user.contractorId ?: user.uid
        viewModel.bind(contractorId, user.siteId)
    }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            photoUri = uri
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) viewModel.onPhotoPicked(bytes)
        }
    }

    BevestScaffold(
        title = if (state.isEdit) "Edit worker" else "Add worker",
        subtitle = if (state.isEdit) null else "New profile",
        onBack = onDone,
    ) { padding ->
        if (!state.loaded) {
            FormSkeleton(fields = 5, modifier = Modifier.padding(padding))
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                // ---- Photo + auto ID ----
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PhotoPicker(
                        name = "${state.firstName} ${state.lastName}".trim(),
                        localUri = photoUri,
                        existingUrl = state.photoUrl,
                        onPick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                    Text(
                        if (photoUri != null || state.photoUrl != null) "Change photo" else "Add a photo (optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    WorkerIdBadge(state.workerId, generated = !state.isEdit)
                }

                // ---- Details ----
                FormCard(title = "Details") {
                    FormField(state.firstName, viewModel::onFirstName, "First name", state.firstNameError)
                    FormField(state.lastName, viewModel::onLastName, "Last name", state.lastNameError)
                    FormField(
                        state.phone, viewModel::onPhone, "Phone number (optional)",
                        state.phoneError, keyboardType = KeyboardType.Phone,
                    )
                }

                // ---- Project / site ----
                FormCard(title = "Project site") {
                    ProjectPicker(
                        projects = state.projects,
                        selectedId = state.selectedProjectId,
                        onSelect = viewModel::onProjectSelected,
                        error = state.projectError,
                    )
                }

                state.formError?.let { FormErrorBar(it) }

                Button(
                    onClick = viewModel::save,
                    enabled = !state.submitting,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            if (state.isEdit) "Save changes" else "Add worker",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                if (state.isEdit && state.active) {
                    OutlinedButton(
                        onClick = { confirmDeactivate = true },
                        shape = RoundedCornerShape(Radius.md),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = LocalStatusPalette.current.danger,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Deactivate worker") }
                }

                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    if (confirmDeactivate) {
        ConfirmationDialog(
            title = "Deactivate this worker?",
            message = "They stop appearing in active lists. Incident history is kept.",
            confirmLabel = "Deactivate",
            onConfirm = { confirmDeactivate = false; viewModel.deactivate() },
            onDismiss = { confirmDeactivate = false },
        )
    }
}

@Composable
private fun PhotoPicker(
    name: String,
    localUri: Uri?,
    existingUrl: String?,
    onPick: () -> Unit,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier
                .size(104.dp)
                .clip(CircleShape)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                localUri != null -> AsyncImage(
                    model = localUri,
                    contentDescription = "Worker photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
                existingUrl != null -> AsyncImage(
                    model = existingUrl,
                    contentDescription = "Worker photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
                else -> WorkerAvatar(name = name.ifBlank { "?" }, photoUrl = null, size = 104.dp)
            }
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = "Choose photo",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun WorkerIdBadge(id: String, generated: Boolean) {
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                BevestIcons.WorkerId,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Text(
                id.ifBlank { "…" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (generated) "· auto-assigned" else "· fixed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FormCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            title.uppercase(),
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        Surface(
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPicker(
    projects: List<com.jtexpress.bevest.domain.model.ProjectSite>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    error: String?,
) {
    val selected = projects.firstOrNull { it.projectId == selectedId }

    if (projects.isEmpty()) {
        // Fall back to whatever the operator is scoped to.
        Text(
            selectedId?.let { "Assigned to $it" }
                ?: "No project site available. Ask your contractor to create one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.let { "${it.name} · ${it.location}" } ?: "Select a project site",
            onValueChange = {},
            readOnly = true,
            label = { Text("Project site") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            leadingIcon = { Icon(BevestIcons.Location, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { project ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(project.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                project.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = {
                        if (project.projectId == selectedId) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        onSelect(project.projectId)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FormErrorBar(message: String) {
    val palette = LocalStatusPalette.current
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = palette.danger.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(BevestIcons.Error, contentDescription = null, tint = palette.danger, modifier = Modifier.size(18.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = palette.danger,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
