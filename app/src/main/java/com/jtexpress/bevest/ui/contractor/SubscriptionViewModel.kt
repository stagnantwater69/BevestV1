package com.jtexpress.bevest.ui.contractor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtexpress.bevest.domain.model.BillingCycle
import com.jtexpress.bevest.domain.model.CustomPricing
import com.jtexpress.bevest.domain.model.Subscription
import com.jtexpress.bevest.domain.model.SubscriptionPlan
import com.jtexpress.bevest.domain.model.SubscriptionPlans
import com.jtexpress.bevest.domain.repository.SystemRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import com.jtexpress.bevest.utils.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Scale tier calculator; fields stay as typed text until saved. */
data class CustomPlanForm(
    val vests: String,
    val ssos: String,
    val cycle: BillingCycle,
) {
    val vestCount: Int? get() = vests.trim().toIntOrNull()
    val ssoCount: Int? get() = ssos.trim().toIntOrNull()
    val vestsValid: Boolean get() = (vestCount ?: 0) > SubscriptionPlans.CUSTOM_MIN_VESTS_EXCLUSIVE
    val ssosValid: Boolean get() = (ssoCount ?: 0) > 0
    val pricing: CustomPricing get() = SubscriptionPlans.customPricing(vestCount ?: 0)
}

/** Plan changes that are waiting for the contractor to confirm, or being saved. */
private data class SubscriptionUi(
    val cycle: BillingCycle = BillingCycle.MONTHLY,
    val pendingPlan: SubscriptionPlan? = null,
    val custom: CustomPlanForm? = null,
    val saving: Boolean = false,
    val message: String? = null,
    val saveError: String? = null,
)

data class SubscriptionState(
    val loading: Boolean = true,
    val error: String? = null,
    val subscription: Subscription? = null,
    val ssosCreated: Int = 0,
    val vestsAssigned: Int = 0,
    /** The monthly/annual toggle above the plan cards. */
    val cycle: BillingCycle = BillingCycle.MONTHLY,
    val pendingPlan: SubscriptionPlan? = null,
    val custom: CustomPlanForm? = null,
    val saving: Boolean = false,
    val message: String? = null,
    val saveError: String? = null,
) {
    val hasPlan: Boolean get() = subscription != null

    fun isCurrent(planId: String): Boolean = subscription?.planId == planId

    /** Already on this plan *and* this billing cycle — nothing to switch to. */
    fun isCurrentSelection(planId: String): Boolean = isCurrent(planId) && subscription?.billingCycle == cycle
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val workerRepository: WorkerRepository,
    private val systemRepository: SystemRepository,
) : ViewModel() {

    private val contractorId = MutableStateFlow<String?>(null)
    private val ui = MutableStateFlow(SubscriptionUi())

    fun start(id: String) { if (contractorId.value == null) contractorId.value = id }

    val state: StateFlow<SubscriptionState> = contractorId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(SubscriptionState())
        // The contractor's own doc is observed here rather than taken from the session,
        // so a plan saved on the web shows up without reopening the screen.
        combine(
            userRepository.observeUser(id),
            userRepository.observeSsos(id),
            workerRepository.observeWorkers(contractorId = id, siteId = null),
            ui,
        ) { userOutcome, ssosOutcome, workersOutcome, u ->
            when (userOutcome) {
                is Outcome.Failure -> SubscriptionState(loading = false, error = userOutcome.error.message)
                is Outcome.Success -> SubscriptionState(
                    loading = false,
                    subscription = userOutcome.data.subscription,
                    ssosCreated = (ssosOutcome as? Outcome.Success)?.data.orEmpty().count { it.active },
                    vestsAssigned = (workersOutcome as? Outcome.Success)?.data.orEmpty()
                        .count { it.active && !it.assignedVestId.isNullOrBlank() },
                    cycle = u.cycle,
                    pendingPlan = u.pendingPlan,
                    custom = u.custom,
                    saving = u.saving,
                    message = u.message,
                    saveError = u.saveError,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionState())

    fun setCycle(cycle: BillingCycle) = ui.update { it.copy(cycle = cycle) }

    fun selectPlan(plan: SubscriptionPlan) {
        if (state.value.isCurrentSelection(plan.id)) return
        ui.update { it.copy(pendingPlan = plan, saveError = null, message = null) }
    }

    fun openCustom() {
        val current = state.value.subscription?.takeIf { it.isCustom }
        ui.update {
            it.copy(
                custom = CustomPlanForm(
                    vests = (current?.vestLimit?.takeIf { v -> v > 100 } ?: 130).toString(),
                    ssos = (current?.ssoLimit ?: 25).toString(),
                    cycle = current?.billingCycle ?: BillingCycle.MONTHLY,
                ),
                saveError = null,
                message = null,
            )
        }
    }

    fun updateCustom(form: CustomPlanForm) = ui.update { it.copy(custom = form, saveError = null) }

    fun dismissDialogs() {
        if (ui.value.saving) return
        ui.update { it.copy(pendingPlan = null, custom = null, saveError = null) }
    }

    fun clearMessage() = ui.update { it.copy(message = null) }

    fun confirmPlan() {
        val plan = ui.value.pendingPlan ?: return
        val cycle = ui.value.cycle
        val now = System.currentTimeMillis()
        save(
            Subscription(
                planId = plan.id,
                planName = plan.name,
                billingCycle = cycle,
                vestLimit = plan.vests,
                ssoLimit = plan.ssos,
                storageGb = plan.storageGb,
                monthlyPrice = plan.monthlyPrice,
                annualPrice = plan.annualPrice,
                discountPercent = plan.discountPercent,
                assignedAt = now,
                renewDate = SubscriptionPlans.renewalDate(now, cycle),
            ),
            activity = "Contractor subscribed to ${plan.name} Plan (${cycle.raw})",
            success = "Switched to the ${plan.name} Plan (${cycle.raw}).",
        )
    }

    fun confirmCustom() {
        val form = ui.value.custom ?: return
        if (!form.vestsValid || !form.ssosValid) return
        val vests = form.vestCount ?: return
        val ssos = form.ssoCount ?: return
        val pricing = form.pricing
        val now = System.currentTimeMillis()
        save(
            Subscription(
                planId = SubscriptionPlans.CUSTOM_ID,
                planName = "Custom",
                billingCycle = form.cycle,
                vestLimit = vests,
                ssoLimit = ssos,
                storageGb = SubscriptionPlans.CUSTOM_STORAGE_GB,
                monthlyPrice = pricing.monthlyPrice,
                annualPrice = pricing.annualPrice,
                discountPercent = SubscriptionPlans.CUSTOM_DISCOUNT_PERCENT,
                assignedAt = now,
                renewDate = SubscriptionPlans.renewalDate(now, form.cycle),
            ),
            activity = "Contractor subscribed to Custom Plan ($vests vests, $ssos SSOs, ${form.cycle.raw})",
            success = "Custom Plan active: $vests vests, $ssos SSOs (${form.cycle.raw}).",
        )
    }

    private fun save(subscription: Subscription, activity: String, success: String) {
        val id = contractorId.value ?: return
        ui.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = userRepository.updateSubscription(id, subscription)) {
                is Outcome.Success -> {
                    // Best effort, as on the web: a missed log line shouldn't fail the change.
                    systemRepository.logActivity(activity, id)
                    ui.update { it.copy(saving = false, pendingPlan = null, custom = null, message = success) }
                }
                is Outcome.Failure -> ui.update { it.copy(saving = false, saveError = result.error.message) }
            }
        }
    }
}
