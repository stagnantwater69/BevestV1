package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.FilterOption
import com.jtexpress.bevest.ui.common.FilterRow
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.common.SearchBar
import com.jtexpress.bevest.ui.theme.BevestIcons
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

    BevestScaffold(
        title = "Workers",
        subtitle = if (user.role == UserRole.SSO) user.siteId else null,
        floatingActionButton = {
            if (canAdd) {
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
                            WorkerLiveRow(live, onClick = { onOpenWorker(live.worker.workerId) })
                        }
                    }
                }
            }
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
