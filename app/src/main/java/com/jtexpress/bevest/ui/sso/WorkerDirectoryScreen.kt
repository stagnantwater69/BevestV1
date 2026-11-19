package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.domain.model.Worker
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.ConfirmationDialog
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.FilterOption
import com.jtexpress.bevest.ui.common.FilterRow
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.SearchBar
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing

/**
 * The roster.
 *
 * Search and filters both narrow the same list, so the empty state has to say which of
 * the two is responsible — being told "no workers here" when the real answer is "your
 * search matched nothing" sends the user looking for a problem that isn't there.
 */
@Composable
fun WorkerDirectoryScreen(
    user: User,
    onOpenWorker: (String) -> Unit,
    onAddWorker: () -> Unit,
    viewModel: WorkerDirectoryViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.uid) {
        if (user.role == UserRole.SSO) viewModel.start(contractorId = null, siteId = user.siteId)
        else viewModel.start(contractorId = user.contractorId ?: user.uid, siteId = null)
    }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val palette = LocalStatusPalette.current
    val canAdd = user.role == UserRole.SSO
    var pendingDeleteWorker by remember { mutableStateOf<Worker?>(null) }

    BevestScaffold(
        title = "Workers",
        subtitle = if (user.role == UserRole.SSO) user.siteId else null,
        floatingActionButton = {
            if (canAdd && !state.loading && state.error == null && !state.noSite && state.all.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddWorker,
                    icon = { Icon(BevestIcons.Add, contentDescription = null) },
                    text = { Text("Add worker") },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))

            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))

            state.noSite -> EmptyState(
                title = "No site assigned",
                message = "Ask your contractor to assign you to a project site.",
                icon = BevestIcons.Location,
                modifier = Modifier.padding(padding),
            )

            state.all.isEmpty() -> EmptyState(
                title = "No workers yet",
                message = if (canAdd) {
                    "Register your first worker, then pair them with a vest to start monitoring."
                } else {
                    "No workers have been registered on this site yet."
                },
                icon = BevestIcons.NoWorkers,
                actionLabel = if (canAdd) "Add worker" else null,
                onAction = if (canAdd) onAddWorker else null,
                modifier = Modifier.padding(padding),
            )

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                SearchBar(
                    query = state.query,
                    onQueryChange = viewModel::onQuery,
                    placeholder = "Search name or worker ID",
                    modifier = Modifier.padding(
                        horizontal = Spacing.gutter,
                        vertical = Spacing.sm,
                    ),
                )

                FilterRow(
                    options = WorkerFilter.entries.map { f ->
                        FilterOption(
                            key = f.name,
                            label = f.label(),
                            count = state.countFor(f),
                            accent = when (f) {
                                WorkerFilter.WARNING -> palette.warning
                                WorkerFilter.DANGER -> palette.danger
                                WorkerFilter.OFFLINE -> palette.offline
                                else -> null
                            },
                        )
                    },
                    selectedKey = state.filter.name,
                    onSelect = { viewModel.onFilter(WorkerFilter.valueOf(it)) },
                )

                if (state.visible.isEmpty()) {
                    val searching = state.query.isNotBlank()
                    EmptyState(
                        title = if (searching) "No matches" else "Nothing in this view",
                        message = if (searching) {
                            "Nothing matches \"${state.query}\". Try a different name or ID, " +
                                "or clear the filter."
                        } else {
                            "No worker currently has this status."
                        },
                        icon = if (searching) BevestIcons.NoResults else BevestIcons.NoWorkers,
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.gutter,
                            end = Spacing.gutter,
                            top = Spacing.sm,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(state.visible, key = { it.worker.workerId }) { live ->
                            if (canAdd) {
                                SwipeToDeleteWorkerRow(
                                    live = live,
                                    onClick = { onOpenWorker(live.worker.workerId) },
                                    onRequestDelete = { pendingDeleteWorker = live.worker },
                                )
                            } else {
                                WorkerLiveRow(live, onClick = { onOpenWorker(live.worker.workerId) })
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteWorker?.let { w ->
        ConfirmationDialog(
            title = "Delete ${w.fullName.ifBlank { w.workerId }}?",
            message = "This will permanently remove this worker profile and unassign any paired vest.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteWorker(w.workerId)
                pendingDeleteWorker = null
            },
            onDismiss = { pendingDeleteWorker = null },
        )
    }
}

@Composable
private fun SwipeToDeleteWorkerRow(
    live: WorkerLive,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    var offsetX by remember(live.worker.workerId) { mutableFloatStateOf(0f) }
    val thresholdPx = -180f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BevestShapes.card),
    ) {
        if (offsetX < 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(LocalStatusPalette.current.danger)
                    .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete worker",
                        tint = Color.White,
                    )
                    Text(
                        text = "Delete",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(live.worker.workerId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX <= thresholdPx) {
                                onRequestDelete()
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-320f, 0f)
                        },
                    )
                },
        ) {
            WorkerLiveRow(live, onClick = onClick)
        }
    }
}

/** Filter names as an officer would say them, not as the enum spells them. */
private fun WorkerFilter.label(): String = when (this) {
    WorkerFilter.ALL -> "All"
    WorkerFilter.ACTIVE -> "On site"
    WorkerFilter.WARNING -> "Warning"
    WorkerFilter.DANGER -> "Danger"
    WorkerFilter.OFFLINE -> "Offline"
    WorkerFilter.UNASSIGNED -> "No vest"
}
