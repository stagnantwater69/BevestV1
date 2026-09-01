package com.jtexpress.bevest.ui.sso

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.domain.model.UserRole
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.EmptyState
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.ListSkeleton
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.Radius
import com.jtexpress.bevest.ui.theme.Spacing

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

    BevestScaffold(
        title = "Workers",
        subtitle = if (user.role == UserRole.SSO) user.siteId else null,
        floatingActionButton = {
            if (user.role == UserRole.SSO) {
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

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    placeholder = { Text("Search name or worker ID") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQuery("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                )

                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    WorkerFilter.entries.forEach { f ->
                        val count = state.countFor(f)
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { viewModel.onFilter(f) },
                            label = {
                                Text(
                                    buildString {
                                        append(f.name.lowercase().replaceFirstChar { it.uppercase() })
                                        if (count > 0) append("  $count")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            shape = RoundedCornerShape(Radius.pill),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }

                if (state.visible.isEmpty()) {
                    EmptyState(
                        title = if (state.query.isNotBlank()) "No matches" else "No workers here",
                        message = if (state.query.isNotBlank()) {
                            "Nothing matches \"${state.query}\". Try a different name or ID."
                        } else {
                            "No workers have this status right now."
                        },
                        icon = if (state.query.isNotBlank()) BevestIcons.NoResults else BevestIcons.NoWorkers,
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
