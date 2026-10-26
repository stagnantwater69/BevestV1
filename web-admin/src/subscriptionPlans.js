/**
 * Subscription plan definitions and pricing calculation logic for Bevest.
 * Shared across Admin User Management and Contractor Portal.
 * 
 * Tiers: Business, Enterprise, and Custom (Scale).
 */

export const VEST_MONTHLY_RATE = 4000; // ₱4,000 per vest per month

export const SUBSCRIPTION_PLANS = {
  BUSINESS: {
    id: 'BUSINESS',
    name: 'Business',
    tag: 'BUSINESS',
    vests: 50,
    ssos: 10,
    storage: '20gb Data Storage',
    storageGb: 20,
    discountPercent: 0, // No discount for Business
    baseMonthlyPrice: 50 * VEST_MONTHLY_RATE, // ₱200,000
    monthlyPrice: 200000,
    baseAnnualPrice: 50 * VEST_MONTHLY_RATE * 12, // ₱2,400,000
    annualPrice: 2400000,
    features: [
      'Assign 10 Site Safety Officers',
      '50 Vests for Workers',
      '20gb Data Storage',
      'Placeholder',
      'Team Analytics'
    ],
    buttonText: 'Get Business',
    highlight: false
  },
  ENTERPRISE: {
    id: 'ENTERPRISE',
    name: 'Enterprise',
    tag: 'ENTERPRISE',
    vests: 100,
    ssos: 20,
    storage: '50gb Data Storage',
    storageGb: 50,
    discountPercent: 10, // 10% discount on total monthly
    baseMonthlyPrice: 100 * VEST_MONTHLY_RATE, // ₱400,000
    monthlyPrice: 360000, // ₱400,000 - 10% = ₱360,000
    baseAnnualPrice: 100 * VEST_MONTHLY_RATE * 12, // ₱4,800,000
    annualPrice: 4320000, // ₱4,800,000 - 10% = ₱4,320,000
    features: [
      'Assign 20 Site Safety Officers',
      '100 Vests for Workers',
      '50gb Data Storage',
      'Placeholder',
      'Priority Support'
    ],
    buttonText: 'Get Enterprise',
    highlight: true
  },
  CUSTOM: {
    id: 'CUSTOM',
    name: 'Custom',
    tag: 'SCALE',
    title: 'CUSTOM',
    subtitle: 'Enterprise Solutions (> 125 Vests)',
    minVests: 125,
    vests: 'Custom',
    ssos: 'Custom',
    storage: 'Unlimited Data Storage',
    storageGb: 100,
    discountPercent: 10, // Same enterprise tier discount of 10% applied
    isCustom: true,
    features: [
      'Custom quota for > 125 Vests & SSOs',
      'Everything in Enterprise',
      'Unlimited Data Storage',
      'Dedicated Account Manager'
    ],
    buttonText: 'Talk to Sales',
    highlight: false
  }
};

/**
 * Calculates live pricing for custom deployments (> 125 vests).
 */
export function calculateCustomPricing(vestCount = 130, discountPercent = 10) {
  const vests = Math.max(0, parseInt(vestCount, 10) || 0);
  const discount = Math.max(0, Math.min(100, Number(discountPercent) || 0));
  
  const baseMonthlyPrice = vests * VEST_MONTHLY_RATE;
  const discountMonthlyAmount = (baseMonthlyPrice * discount) / 100;
  const monthlyPrice = Math.round(baseMonthlyPrice - discountMonthlyAmount);

  const baseAnnualPrice = baseMonthlyPrice * 12;
  const annualPrice = monthlyPrice * 12;

  return {
    vests,
    discountPercent: discount,
    baseMonthlyPrice,
    monthlyPrice,
    baseAnnualPrice,
    annualPrice
  };
}

/**
 * Format a numeric amount into Philippine Peso representation: ₱XX,XXX
 */
export function formatPhp(amount) {
  if (amount == null || isNaN(amount)) return '₱0';
  return '₱' + Number(amount).toLocaleString('en-US');
}

/**
 * Format a Firestore timestamp, Date object, or date string into readable text.
 */
export function formatDisplayDate(dateInput) {
  if (!dateInput) return 'N/A';
  let d;
  if (typeof dateInput.toDate === 'function') {
    d = dateInput.toDate();
  } else if (dateInput instanceof Date) {
    d = dateInput;
  } else {
    d = new Date(dateInput);
  }
  if (isNaN(d.getTime())) return 'N/A';

  return d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  });
}

/**
 * Calculate renewal date given an assigned date and billing cycle.
 */
export function calculateRenewalDate(assignedDate, billingCycle = 'monthly') {
  const d = assignedDate ? (assignedDate.toDate ? assignedDate.toDate() : new Date(assignedDate)) : new Date();
  const renew = new Date(d);
  if (billingCycle === 'annual') {
    renew.setFullYear(renew.getFullYear() + 1);
  } else {
    renew.setMonth(renew.getMonth() + 1);
  }
  return renew;
}

/**
 * Safe fallback subscription for contractor accounts.
 */
export function getContractorSubscription(userDocData = {}) {
  if (userDocData && userDocData.subscription && userDocData.subscription.planId) {
    const sub = userDocData.subscription;
    const plan = SUBSCRIPTION_PLANS[sub.planId] || SUBSCRIPTION_PLANS.BUSINESS;
    const assigned = sub.assignedAt || userDocData.createdAt || new Date();
    const billingCycle = sub.billingCycle || 'monthly';
    const renew = sub.renewDate || calculateRenewalDate(assigned, billingCycle);

    return {
      planId: plan.id,
      planName: plan.name,
      billingCycle,
      vestLimit: sub.vestLimit ?? plan.vests,
      ssoLimit: sub.ssoLimit ?? plan.ssos,
      storageGb: sub.storageGb ?? plan.storageGb,
      monthlyPrice: sub.monthlyPrice ?? plan.monthlyPrice,
      annualPrice: sub.annualPrice ?? plan.annualPrice,
      discountPercent: sub.discountPercent ?? plan.discountPercent,
      assignedAt: assigned,
      renewDate: renew,
      status: sub.status || 'Active',
      isCustom: plan.isCustom || false,
      customNotes: sub.customNotes || ''
    };
  }

  // Default Business Plan fallback
  const plan = SUBSCRIPTION_PLANS.BUSINESS;
  const assigned = userDocData.createdAt || new Date();
  const renew = calculateRenewalDate(assigned, 'monthly');

  return {
    planId: plan.id,
    planName: plan.name,
    billingCycle: 'monthly',
    vestLimit: plan.vests,
    ssoLimit: plan.ssos,
    storageGb: plan.storageGb,
    monthlyPrice: plan.monthlyPrice,
    annualPrice: plan.annualPrice,
    discountPercent: plan.discountPercent,
    assignedAt: assigned,
    renewDate: renew,
    status: 'Active',
    isCustom: false
  };
}
