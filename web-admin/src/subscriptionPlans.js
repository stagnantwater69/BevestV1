import { useState, useEffect } from 'react';
import { doc, onSnapshot, setDoc } from 'firebase/firestore';
import { ref, onValue, set } from 'firebase/database';
import { db, rtdb } from './firebase';
import { logActivity } from './activityLogger';

/**
 * Subscription plan definitions and pricing calculation logic for Bevest.
 * Shared across Admin User Management and Contractor Portal.
 * 
 * Tiers: Business, Enterprise, and Custom (Scale).
 */

export const VEST_MONTHLY_RATE = 4000; // ₱4,000 per vest per month

export const DEFAULT_SUBSCRIPTION_PLANS = {
  BUSINESS: {
    id: 'BUSINESS',
    name: 'Business',
    tag: 'BUSINESS',
    vests: 50,
    ssos: 10,
    storage: '20gb Data Storage',
    storageGb: 20,
    discountPercent: 0,
    baseMonthlyPrice: 50 * 4000,
    monthlyPrice: 200000,
    baseAnnualPrice: 50 * 4000 * 12,
    annualPrice: 2400000,
    features: [
      'Assign 10 Site Safety Officers',
      '50 Vests for Workers',
      '20gb Data Storage',
      'Site-wide live vitals telemetry',
      'Team Safety Analytics'
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
    discountPercent: 10,
    baseMonthlyPrice: 100 * 4000,
    monthlyPrice: 360000,
    baseAnnualPrice: 100 * 4000 * 12,
    annualPrice: 4320000,
    features: [
      'Assign 20 Site Safety Officers',
      '100 Vests for Workers',
      '50gb Data Storage',
      'Automated PDF Safety Reports',
      'Priority 24/7 Support'
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
    discountPercent: 10,
    ratePerVest: 4000,
    isCustom: true,
    features: [
      'Custom quota for > 125 Vests & SSOs',
      'Everything in Enterprise included',
      'Unlimited Data Storage (100 GB)',
      'Dedicated Safety Account Manager'
    ],
    buttonText: 'Talk to Sales',
    highlight: false
  }
};

export const SUBSCRIPTION_PLANS = { ...DEFAULT_SUBSCRIPTION_PLANS };

/**
 * Real-time hook for listening to cloud-configured subscription tiers.
 * If the developer edits prices or inclusions in Firestore, all screens update automatically.
 */
export function useSubscriptionPlans() {
  const [plans, setPlans] = useState(DEFAULT_SUBSCRIPTION_PLANS);
  const [vestRate, setVestRate] = useState(VEST_MONTHLY_RATE);
  const [loading, setLoading] = useState(true);
  const [isCustomConfig, setIsCustomConfig] = useState(false);

  useEffect(() => {
    let latestUpdatedAtMs = 0;

    const applyPlanData = (data) => {
      if (!data) return;
      const incomingTs = Number(data.updatedAtMs) || 0;
      if (latestUpdatedAtMs > 0 && incomingTs > 0 && incomingTs < latestUpdatedAtMs) {
        return;
      }
      if (incomingTs > latestUpdatedAtMs) {
        latestUpdatedAtMs = incomingTs;
      }

      setPlans({
        BUSINESS: {
          ...DEFAULT_SUBSCRIPTION_PLANS.BUSINESS,
          ...(data.BUSINESS || {})
        },
        ENTERPRISE: {
          ...DEFAULT_SUBSCRIPTION_PLANS.ENTERPRISE,
          ...(data.ENTERPRISE || {})
        },
        CUSTOM: {
          ...DEFAULT_SUBSCRIPTION_PLANS.CUSTOM,
          ...(data.CUSTOM || {})
        }
      });
      if (data.vestMonthlyRate != null) {
        setVestRate(Number(data.vestMonthlyRate));
      }
      setIsCustomConfig(true);
    };

    const unsubFirestore = onSnapshot(doc(db, 'settings', 'subscriptionPlans'), (snap) => {
      if (snap.exists()) {
        applyPlanData(snap.data());
      }
      setLoading(false);
    }, (err) => {
      console.warn('Error fetching subscription plans settings from Firestore:', err);
      setLoading(false);
    });

    const unsubRtdb = onValue(ref(rtdb, 'settings/subscriptionPlans'), (snap) => {
      if (snap.exists()) {
        applyPlanData(snap.val());
      }
      setLoading(false);
    }, (err) => {
      console.warn('Error fetching subscription plans settings from RTDB:', err);
    });

    return () => {
      unsubFirestore();
      unsubRtdb();
    };
  }, []);

  return { plans, vestRate, loading, isCustomConfig };
}

/**
 * Saves modified subscription tiers, rates, and inclusions to Firestore and RTDB.
 */
export async function saveSubscriptionPlans(plansData, vestMonthlyRate, userEmail = 'Developer') {
  const rate = Math.max(0, Number(vestMonthlyRate) || VEST_MONTHLY_RATE);
  const cleanObject = (obj) => JSON.parse(JSON.stringify(obj || {}));

  const sanitizeTier = (tier, fallback) => {
    const merged = { ...fallback, ...(tier || {}) };
    if (merged.id === 'CUSTOM' || merged.isCustom) {
      return cleanObject({
        ...merged,
        minVests: Number(merged.minVests) || 125,
        storageGb: Number(merged.storageGb) || 100,
        discountPercent: Number(merged.discountPercent ?? 10),
        ratePerVest: rate,
      });
    }
    return cleanObject({
      ...merged,
      vests: Number(merged.vests) || 0,
      ssos: Number(merged.ssos) || 0,
      storageGb: Number(merged.storageGb) || 0,
      discountPercent: Number(merged.discountPercent ?? 0),
      baseMonthlyPrice: Number(merged.baseMonthlyPrice ?? merged.monthlyPrice ?? 0),
      monthlyPrice: Number(merged.monthlyPrice) || 0,
      baseAnnualPrice: Number(merged.baseAnnualPrice ?? merged.annualPrice ?? 0),
      annualPrice: Number(merged.annualPrice) || 0,
    });
  };

  const payload = {
    BUSINESS: sanitizeTier(plansData?.BUSINESS, DEFAULT_SUBSCRIPTION_PLANS.BUSINESS),
    ENTERPRISE: sanitizeTier(plansData?.ENTERPRISE, DEFAULT_SUBSCRIPTION_PLANS.ENTERPRISE),
    CUSTOM: sanitizeTier(plansData?.CUSTOM, DEFAULT_SUBSCRIPTION_PLANS.CUSTOM),
    vestMonthlyRate: rate,
    updatedAtMs: Date.now(),
    updatedBy: userEmail || 'Developer'
  };

  let savedFirestore = false;
  let savedRtdb = false;
  let lastError = null;

  try {
    await setDoc(doc(db, 'settings', 'subscriptionPlans'), {
      ...payload,
      updatedAt: new Date()
    }, { merge: true });
    savedFirestore = true;
  } catch (err) {
    lastError = err;
    console.warn('Firestore saveSubscriptionPlans warning:', err.message);
  }

  try {
    await set(ref(rtdb, 'settings/subscriptionPlans'), payload);
    savedRtdb = true;
  } catch (err) {
    if (!lastError) lastError = err;
    console.warn('RTDB saveSubscriptionPlans warning:', err.message);
  }

  if (!savedFirestore && !savedRtdb && lastError) {
    throw lastError;
  }

  await logActivity(`Updated subscription tiers, prices, and inclusions configuration`, userEmail);
}

/**
 * Resets subscription tiers in Firestore and RTDB back to factory defaults.
 */
export async function resetSubscriptionPlans(userEmail = 'Developer') {
  const payload = {
    ...DEFAULT_SUBSCRIPTION_PLANS,
    vestMonthlyRate: VEST_MONTHLY_RATE,
    updatedAtMs: Date.now(),
    updatedBy: userEmail
  };

  let savedFirestore = false;
  let savedRtdb = false;
  let lastError = null;

  try {
    await setDoc(doc(db, 'settings', 'subscriptionPlans'), {
      ...payload,
      updatedAt: new Date()
    });
    savedFirestore = true;
  } catch (err) {
    lastError = err;
    console.warn('Firestore resetSubscriptionPlans warning:', err.message);
  }

  try {
    await set(ref(rtdb, 'settings/subscriptionPlans'), payload);
    savedRtdb = true;
  } catch (err) {
    if (!lastError) lastError = err;
    console.warn('RTDB resetSubscriptionPlans warning:', err.message);
  }

  if (!savedFirestore && !savedRtdb && lastError) {
    throw lastError;
  }

  await logActivity(`Reset subscription tiers to factory default pricing and inclusions`, userEmail);
}

/**
 * Calculates live pricing for custom deployments (> 125 vests).
 */
export function calculateCustomPricing(vestCount = 130, discountPercent = 10, ratePerVest = VEST_MONTHLY_RATE) {
  const vests = Math.max(0, parseInt(vestCount, 10) || 0);
  const discount = Math.max(0, Math.min(100, Number(discountPercent) || 0));
  const rate = Number(ratePerVest) || VEST_MONTHLY_RATE;
  
  const baseMonthlyPrice = vests * rate;
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
export function getContractorSubscription(userDocData = {}, customPlans = null, vestRate = null) {
  const activePlans = customPlans || SUBSCRIPTION_PLANS;
  if (userDocData && userDocData.subscription && userDocData.subscription.planId && userDocData.subscription.planId !== 'NONE') {
    const sub = userDocData.subscription;
    const plan = activePlans[sub.planId] || activePlans.BUSINESS || DEFAULT_SUBSCRIPTION_PLANS.BUSINESS;
    const assigned = sub.assignedAt || userDocData.createdAt || new Date();
    const billingCycle = sub.billingCycle || 'monthly';
    const renew = sub.renewDate || calculateRenewalDate(assigned, billingCycle);
    const isCustom = Boolean(plan.isCustom || sub.planId === 'CUSTOM');

    let monthlyPrice = plan.monthlyPrice ?? sub.monthlyPrice;
    let annualPrice = plan.annualPrice ?? sub.annualPrice;
    const discountPercent = plan.discountPercent ?? sub.discountPercent ?? 0;

    if (isCustom) {
      const customVestLimit = Number(sub.vestLimit ?? plan.minVests ?? 125) || 125;
      const effectiveRate = Number(vestRate ?? plan.ratePerVest ?? VEST_MONTHLY_RATE) || VEST_MONTHLY_RATE;
      const recalculated = calculateCustomPricing(customVestLimit, discountPercent, effectiveRate);
      monthlyPrice = recalculated.monthlyPrice;
      annualPrice = recalculated.annualPrice;
    }

    return {
      hasPlan: true,
      planId: plan.id,
      planName: plan.name,
      billingCycle,
      vestLimit: isCustom ? (sub.vestLimit ?? plan.vests) : (plan.vests ?? sub.vestLimit),
      ssoLimit: isCustom ? (sub.ssoLimit ?? plan.ssos) : (plan.ssos ?? sub.ssoLimit),
      storageGb: isCustom ? (sub.storageGb ?? plan.storageGb) : (plan.storageGb ?? sub.storageGb),
      monthlyPrice,
      annualPrice,
      discountPercent,
      assignedAt: assigned,
      renewDate: renew,
      status: sub.status || 'Active',
      isCustom,
      customNotes: sub.customNotes || ''
    };
  }

  // Contractors start with no subscription plan
  return {
    hasPlan: false,
    planId: 'NONE',
    planName: 'No Subscription Plan',
    billingCycle: 'none',
    vestLimit: 0,
    ssoLimit: 0,
    storageGb: 0,
    monthlyPrice: 0,
    annualPrice: 0,
    discountPercent: 0,
    assignedAt: null,
    renewDate: null,
    status: 'No Plan',
    isCustom: false,
    customNotes: ''
  };
}
