package com.jtexpress.bevest.domain.model

import java.util.Calendar

/**
 * Contractor subscription tiers. Mirrors web-admin/src/subscriptionPlans.js so both
 * clients read and write the same `users/{uid}.subscription` object — keep them in step.
 */

enum class BillingCycle(val raw: String) {
    MONTHLY("monthly"),
    ANNUAL("annual");

    companion object {
        fun fromRaw(raw: String?): BillingCycle = entries.firstOrNull { it.raw == raw } ?: MONTHLY
    }
}

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val vests: Int,
    val ssos: Int,
    val storageGb: Int,
    val discountPercent: Int,
    val baseMonthlyPrice: Long,
    val monthlyPrice: Long,
    val baseAnnualPrice: Long,
    val annualPrice: Long,
    val features: List<String>,
) {
    fun price(cycle: BillingCycle): Long = if (cycle == BillingCycle.ANNUAL) annualPrice else monthlyPrice
    fun basePrice(cycle: BillingCycle): Long = if (cycle == BillingCycle.ANNUAL) baseAnnualPrice else baseMonthlyPrice
}

/** Live pricing for a custom (Scale) deployment. */
data class CustomPricing(
    val vests: Int,
    val discountPercent: Int,
    val baseMonthlyPrice: Long,
    val monthlyPrice: Long,
    val baseAnnualPrice: Long,
    val annualPrice: Long,
) {
    fun price(cycle: BillingCycle): Long = if (cycle == BillingCycle.ANNUAL) annualPrice else monthlyPrice
}

object SubscriptionPlans {
    const val VEST_MONTHLY_RATE = 4_000L
    const val BUSINESS_ID = "BUSINESS"
    const val ENTERPRISE_ID = "ENTERPRISE"
    const val CUSTOM_ID = "CUSTOM"

    /** A custom tier is only for deployments larger than this many vests. */
    const val CUSTOM_MIN_VESTS_EXCLUSIVE = 125
    const val CUSTOM_STORAGE_GB = 100
    const val CUSTOM_DISCOUNT_PERCENT = 10

    val BUSINESS = SubscriptionPlan(
        id = BUSINESS_ID,
        name = "Business",
        vests = 50,
        ssos = 10,
        storageGb = 20,
        discountPercent = 0,
        baseMonthlyPrice = 50 * VEST_MONTHLY_RATE,
        monthlyPrice = 200_000,
        baseAnnualPrice = 50 * VEST_MONTHLY_RATE * 12,
        annualPrice = 2_400_000,
        features = listOf(
            "Assign 10 Site Safety Officers",
            "50 Vests for Workers",
            "20gb Data Storage",
            "Placeholder",
            "Team Analytics",
        ),
    )

    val ENTERPRISE = SubscriptionPlan(
        id = ENTERPRISE_ID,
        name = "Enterprise",
        vests = 100,
        ssos = 20,
        storageGb = 50,
        discountPercent = 10,
        baseMonthlyPrice = 100 * VEST_MONTHLY_RATE,
        monthlyPrice = 360_000,
        baseAnnualPrice = 100 * VEST_MONTHLY_RATE * 12,
        annualPrice = 4_320_000,
        features = listOf(
            "Assign 20 Site Safety Officers",
            "100 Vests for Workers",
            "50gb Data Storage",
            "Placeholder",
            "Priority Support",
        ),
    )

    val CUSTOM_FEATURES = listOf(
        "Custom quota for > 125 Vests & SSOs",
        "Custom calculation live pricing",
        "Everything in Enterprise included",
        "Unlimited Data Storage (100 GB)",
        "Dedicated Safety Account Manager",
    )

    val fixed: List<SubscriptionPlan> = listOf(BUSINESS, ENTERPRISE)

    fun fixedById(id: String?): SubscriptionPlan? = fixed.firstOrNull { it.id == id }

    fun customPricing(vestCount: Int, discountPercent: Int = CUSTOM_DISCOUNT_PERCENT): CustomPricing {
        val vests = vestCount.coerceAtLeast(0)
        val discount = discountPercent.coerceIn(0, 100)
        val baseMonthly = vests * VEST_MONTHLY_RATE
        val monthly = Math.round(baseMonthly - baseMonthly * discount / 100.0)
        return CustomPricing(
            vests = vests,
            discountPercent = discount,
            baseMonthlyPrice = baseMonthly,
            monthlyPrice = monthly,
            baseAnnualPrice = baseMonthly * 12,
            annualPrice = monthly * 12,
        )
    }

    fun renewalDate(fromMillis: Long, cycle: BillingCycle): Long = Calendar.getInstance().apply {
        timeInMillis = fromMillis
        if (cycle == BillingCycle.ANNUAL) add(Calendar.YEAR, 1) else add(Calendar.MONTH, 1)
    }.timeInMillis
}

/** A contractor's active plan. A contractor without one has `User.subscription == null`. */
data class Subscription(
    val planId: String,
    val planName: String,
    val billingCycle: BillingCycle,
    val vestLimit: Int,
    val ssoLimit: Int,
    val storageGb: Int,
    val monthlyPrice: Long,
    val annualPrice: Long,
    val discountPercent: Int,
    val assignedAt: Long?,
    val renewDate: Long?,
    val status: String = "Active",
) {
    val isCustom: Boolean get() = planId == SubscriptionPlans.CUSTOM_ID
    val currentPrice: Long get() = if (billingCycle == BillingCycle.ANNUAL) annualPrice else monthlyPrice
}
