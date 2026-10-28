package com.jtexpress.bevest.ui.contractor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtexpress.bevest.domain.model.BillingCycle
import com.jtexpress.bevest.domain.model.Subscription
import com.jtexpress.bevest.domain.model.SubscriptionPlan
import com.jtexpress.bevest.domain.model.SubscriptionPlans
import com.jtexpress.bevest.domain.model.User
import com.jtexpress.bevest.ui.common.BevestCard
import com.jtexpress.bevest.ui.common.BevestScaffold
import com.jtexpress.bevest.ui.common.DashboardSkeleton
import com.jtexpress.bevest.ui.common.DetailRow
import com.jtexpress.bevest.ui.common.ErrorNote
import com.jtexpress.bevest.ui.common.ErrorState
import com.jtexpress.bevest.ui.common.FilterOption
import com.jtexpress.bevest.ui.common.FilterRow
import com.jtexpress.bevest.ui.common.FormField
import com.jtexpress.bevest.ui.common.PrimaryButton
import com.jtexpress.bevest.ui.common.ProfileAction
import com.jtexpress.bevest.ui.common.SecondaryButton
import com.jtexpress.bevest.ui.common.SectionHeader
import com.jtexpress.bevest.ui.common.StatusBadge
import com.jtexpress.bevest.ui.common.SuccessNote
import com.jtexpress.bevest.ui.theme.BevestIcons
import com.jtexpress.bevest.ui.theme.BevestShapes
import com.jtexpress.bevest.ui.theme.LocalStatusPalette
import com.jtexpress.bevest.ui.theme.Spacing
import com.jtexpress.bevest.utils.DateTimeUtils
import java.text.NumberFormat
import java.util.Locale

/** ₱200,000 — the same formatting as formatPhp() on the web. */
internal fun formatPhp(amount: Long): String = "₱" + NumberFormat.getNumberInstance(Locale.US).format(amount)

private fun BillingCycle.per(): String = if (this == BillingCycle.ANNUAL) "/year" else "/month"

private fun BillingCycle.label(): String = if (this == BillingCycle.ANNUAL) "Annual" else "Monthly"

/**
 * The contractor's plan: what they're on, how much of it they're using, and the tiers
 * they can switch to. Matches the web portal's Subscription page and writes the same
 * `users/{uid}.subscription` object.
 */
@Composable
fun ContractorSubscriptionScreen(
    user: User,
    onOpenProfile: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    LaunchedEffect(user.uid) { viewModel.start(user.contractorId ?: user.uid) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val gutter = Modifier.padding(horizontal = Spacing.gutter)

    BevestScaffold(
        title = "Subscription",
        subtitle = "Your plan, quota and billing",
        actions = { ProfileAction(user, onClick = onOpenProfile) },
    ) { padding ->
        when {
            state.loading -> DashboardSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                state.message?.let { SuccessNote(it, gutter) }

                CurrentPlanCard(state, gutter)

                SectionHeader(
                    title = "Available plans",
                    supporting = "Scale your safety monitoring by choosing or changing your package",
                    modifier = gutter,
                )
                FilterRow(
                    options = BillingCycle.entries.map { FilterOption(it.raw, it.label()) },
                    selectedKey = state.cycle.raw,
                    onSelect = { viewModel.setCycle(BillingCycle.fromRaw(it)) },
                )

                SubscriptionPlans.fixed.forEach { plan ->
                    FixedPlanCard(
                        plan = plan,
                        cycle = state.cycle,
                        current = state.isCurrent(plan.id),
                        currentSelection = state.isCurrentSelection(plan.id),
                        onSelect = { viewModel.selectPlan(plan) },
                        modifier = gutter,
                    )
                }
                CustomPlanCard(
                    current = state.subscription?.takeIf { it.isCustom },
                    onConfigure = viewModel::openCustom,
                    modifier = gutter,
                )

                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    state.pendingPlan?.let { plan ->
        ConfirmPlanDialog(
            plan = plan,
            cycle = state.cycle,
            saving = state.saving,
            error = state.saveError,
            onConfirm = viewModel::confirmPlan,
            onDismiss = viewModel::dismissDialogs,
        )
    }
    state.custom?.let { form ->
        CustomPlanDialog(
            form = form,
            editing = state.subscription?.isCustom == true,
            saving = state.saving,
            error = state.saveError,
            onChange = viewModel::updateCustom,
            onConfirm = viewModel::confirmCustom,
            onDismiss = viewModel::dismissDialogs,
        )
    }
}

@Composable
private fun CurrentPlanCard(state: SubscriptionState, modifier: Modifier) {
    val palette = LocalStatusPalette.current
    val sub = state.subscription
    BevestCard(modifier.fillMaxWidth(), accent = if (sub != null) MaterialTheme.colorScheme.primary else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sub != null) "ACTIVE SUBSCRIPTION" else "SUBSCRIPTION STATUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(
                text = sub?.status ?: "No plan",
                color = if (sub != null) palette.normal else palette.offline,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            if (sub != null) "${sub.planName} Plan" else "No active subscription",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            when {
                sub == null -> "Your account has no subscription package yet. You can still use your " +
                    "dashboard and manage sites. Choose a tier below to activate vest quota and Site " +
                    "Safety Officers."
                sub.isCustom -> "Custom enterprise tier with ${sub.vestLimit} smart vests and " +
                    "${sub.ssoLimit} Site Safety Officers."
                else -> "Includes up to ${sub.vestLimit} smart vests and ${sub.ssoLimit} Site Safety Officers."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatPhp(sub?.currentPrice ?: 0),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                (sub?.billingCycle ?: BillingCycle.MONTHLY).per(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
            )
        }
        if (sub != null && sub.discountPercent > 0) {
            Text(
                "Includes ${sub.discountPercent}% discount",
                style = MaterialTheme.typography.labelSmall,
                color = palette.normal,
            )
        }

        Spacer(Modifier.height(Spacing.md))
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.sm))
        DetailRow("Date assigned", sub?.assignedAt?.let(DateTimeUtils::formatDate) ?: "Not assigned", icon = BevestIcons.History)
        DetailRow("Renews on", sub?.renewDate?.let(DateTimeUtils::formatDate) ?: "Not active", icon = BevestIcons.Timer)
        DetailRow("Cloud storage", if (sub != null) "${sub.storageGb} GB allocated" else "0 GB", icon = BevestIcons.Reports)
        DetailRow("Billing cycle", sub?.let { "${it.billingCycle.label()} renewal" } ?: "None", icon = BevestIcons.Subscription)

        Spacer(Modifier.height(Spacing.md))
        QuotaBar(
            title = "Site Safety Officers",
            used = state.ssosCreated,
            limit = sub?.ssoLimit ?: 0,
            unit = "created",
            note = when {
                sub == null -> "No SSO slots allocated. Choose a plan below."
                sub.ssoLimit > state.ssosCreated -> "${sub.ssoLimit - state.ssosCreated} officer slots available"
                else -> "Maximum officer accounts reached for this plan"
            },
        )
        Spacer(Modifier.height(Spacing.md))
        QuotaBar(
            title = "Smart monitored vests",
            used = state.vestsAssigned,
            limit = sub?.vestLimit ?: 0,
            unit = "assigned",
            note = when {
                sub == null -> "No vest quota allocated. Choose a plan below."
                sub.vestLimit > state.vestsAssigned -> "${sub.vestLimit - state.vestsAssigned} vests available for workers"
                else -> "All allocated vests are assigned"
            },
        )
    }
}

@Composable
private fun QuotaBar(title: String, used: Int, limit: Int, unit: String, note: String) {
    val palette = LocalStatusPalette.current
    val fraction = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "$used / $limit $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(BevestShapes.pill),
            color = if (fraction > 0.9f) palette.danger else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FixedPlanCard(
    plan: SubscriptionPlan,
    cycle: BillingCycle,
    current: Boolean,
    currentSelection: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    PlanCardFrame(
        tag = plan.name.uppercase(),
        current = current,
        modifier = modifier,
        features = plan.features,
        price = {
            if (plan.discountPercent > 0) {
                Text(
                    formatPhp(plan.basePrice(cycle)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatPhp(plan.price(cycle)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    cycle.per(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
        },
        action = {
            if (currentSelection) {
                SecondaryButton(text = "Current plan", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(text = "Get ${plan.name}", onClick = onSelect, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

@Composable
private fun CustomPlanCard(current: Subscription?, onConfigure: () -> Unit, modifier: Modifier) {
    PlanCardFrame(
        tag = "SCALE",
        current = current != null,
        modifier = modifier,
        features = SubscriptionPlans.CUSTOM_FEATURES,
        price = {
            Text(
                current?.let { formatPhp(it.monthlyPrice) } ?: "CUSTOM",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                current?.let { "(${it.vestLimit} vests • ${it.ssoLimit} SSOs)" } ?: "Enterprise solutions (> 125 vests)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        action = {
            PrimaryButton(
                text = if (current != null) "Adjust custom plan" else "Configure custom tier",
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun PlanCardFrame(
    tag: String,
    current: Boolean,
    features: List<String>,
    modifier: Modifier,
    price: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    val palette = LocalStatusPalette.current
    BevestCard(modifier.fillMaxWidth(), accent = if (current) MaterialTheme.colorScheme.primary else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (current) StatusBadge(text = "Current plan", color = palette.normal)
        }
        Spacer(Modifier.height(Spacing.sm))
        price()
        Spacer(Modifier.height(Spacing.md))
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(BevestShapes.pill)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(feature, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        action()
    }
}

@Composable
private fun ConfirmPlanDialog(
    plan: SubscriptionPlan,
    cycle: BillingCycle,
    saving: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalStatusPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm plan selection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("You are selecting the ${plan.name} Plan with ${cycle.raw} billing.")
                Spacer(Modifier.height(Spacing.xs))
                DetailRow("Hardware limit", "${plan.vests} smart vests")
                DetailRow("Site Safety Officers", "${plan.ssos} officers")
                DetailRow("Billing rate", formatPhp(plan.price(cycle)) + cycle.per())
                if (plan.discountPercent > 0) {
                    DetailRow("Savings", "${plan.discountPercent}% off applied", valueColor = palette.normal)
                }
                error?.let { ErrorNote(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !saving) {
                Text(if (saving) "Updating…" else "Switch to ${plan.name}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}

@Composable
private fun CustomPlanDialog(
    form: CustomPlanForm,
    editing: Boolean,
    saving: Boolean,
    error: String?,
    onChange: (CustomPlanForm) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalStatusPalette.current
    val pricing = form.pricing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom tier pricing") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    "For more than 125 vests. Custom plans get an automatic " +
                        "${SubscriptionPlans.CUSTOM_DISCOUNT_PERCENT}% volume discount.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormField(
                    value = form.vests,
                    onValueChange = { onChange(form.copy(vests = it.filter(Char::isDigit))) },
                    label = "Vest count (> 125)",
                    keyboardType = KeyboardType.Number,
                    error = if (form.vestsValid) null else "Custom tier needs at least 126 vests",
                )
                FormField(
                    value = form.ssos,
                    onValueChange = { onChange(form.copy(ssos = it.filter(Char::isDigit))) },
                    label = "Site Safety Officer allowance",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    error = if (form.ssosValid) null else "Enter at least 1 officer",
                )
                FilterRow(
                    options = BillingCycle.entries.map { FilterOption(it.raw, "${it.label()} renewal") },
                    selectedKey = form.cycle.raw,
                    onSelect = { onChange(form.copy(cycle = BillingCycle.fromRaw(it))) },
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow("Base rate", "${formatPhp(SubscriptionPlans.VEST_MONTHLY_RATE)} / vest / month")
                DetailRow("Base price (${pricing.vests} vests)", "${formatPhp(pricing.baseMonthlyPrice)}/mo")
                DetailRow(
                    "Volume discount (${pricing.discountPercent}%)",
                    "-${formatPhp(pricing.baseMonthlyPrice - pricing.monthlyPrice)}/mo",
                    valueColor = palette.normal,
                )
                Text(
                    "${formatPhp(pricing.price(form.cycle))}${form.cycle.per()}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${pricing.vests} vests • ${form.ssos.ifBlank { "0" }} SSOs • " +
                        "${SubscriptionPlans.CUSTOM_STORAGE_GB} GB cloud storage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { ErrorNote(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !saving && form.vestsValid && form.ssosValid) {
                Text(
                    when {
                        saving -> "Saving…"
                        editing -> "Update custom plan"
                        else -> "Subscribe to custom"
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}
