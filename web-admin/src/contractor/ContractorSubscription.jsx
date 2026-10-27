import React, { useState } from 'react';
import { 
  FiCalendar, FiClock, FiShield, FiUsers, FiCpu, 
  FiHardDrive, FiPhoneCall, FiCheckCircle,
  FiX, FiTrendingUp
} from 'react-icons/fi';
import { doc, updateDoc } from 'firebase/firestore';
import { db } from '../firebase';
import { 
  SUBSCRIPTION_PLANS, 
  formatPhp, 
  formatDisplayDate, 
  calculateRenewalDate, 
  getContractorSubscription,
  calculateCustomPricing 
} from '../subscriptionPlans';
import { logActivity } from '../activityLogger';
import { PageHeader } from './ui';

const ContractorSubscription = ({ user, profile, data }) => {
  const [billingCycle, setBillingCycle] = useState('monthly'); // 'monthly' | 'annual'
  const [selectedPlanModal, setSelectedPlanModal] = useState(null);
  const [showCustomModal, setShowCustomModal] = useState(false);
  const [showSalesModal, setShowSalesModal] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [toastMsg, setToastMsg] = useState('');

  const contractorId = profile?.contractorId || user?.uid;
  const subscription = getContractorSubscription(profile || user?.profile || {});

  // Custom Tier state for > 125 vests and customizable SSOs
  const [customVests, setCustomVests] = useState(
    subscription.isCustom && typeof subscription.vestLimit === 'number' && subscription.vestLimit > 100 
      ? subscription.vestLimit 
      : 130
  );
  const [customSsos, setCustomSsos] = useState(
    subscription.isCustom && typeof subscription.ssoLimit === 'number' 
      ? subscription.ssoLimit 
      : 25
  );
  const [customBillingCycle, setCustomBillingCycle] = useState(
    subscription.isCustom && subscription.billingCycle ? subscription.billingCycle : 'monthly'
  );

  // Real-time custom pricing calculation (10% volume discount applied for custom enterprise scale)
  const customPricing = calculateCustomPricing(customVests, 10);

  // Current utilization from real-time contractor data
  const ssoCount = data?.officers?.length || 0;
  const vestAssignedCount = data?.workers?.filter(w => w.assignedVestId)?.length || 0;
  const maxSso = subscription.hasPlan
    ? (subscription.ssoLimit === 'Custom' ? 'Custom' : Number(subscription.ssoLimit || 10))
    : 0;
  const maxVests = subscription.hasPlan
    ? (subscription.vestLimit === 'Custom' ? 'Custom' : Number(subscription.vestLimit || 50))
    : 0;

  const ssoPct = typeof maxSso === 'number' && maxSso > 0 ? Math.min(100, Math.round((ssoCount / maxSso) * 100)) : 0;
  const vestPct = typeof maxVests === 'number' && maxVests > 0 ? Math.min(100, Math.round((vestAssignedCount / maxVests) * 100)) : 0;

  const isCurrent = (planId) => subscription.hasPlan && subscription.planId === planId;

  const handleSelectPlan = (plan) => {
    if (plan.id === 'CUSTOM') {
      setShowCustomModal(true);
      return;
    }
    if (isCurrent(plan.id) && subscription.billingCycle === billingCycle) {
      return;
    }
    setSelectedPlanModal(plan);
  };

  const handleConfirmUpgrade = async () => {
    if (!selectedPlanModal || !contractorId) return;
    setUpdating(true);
    try {
      const now = new Date();
      const renew = calculateRenewalDate(now, billingCycle);
      const userRef = doc(db, 'users', contractorId);

      await updateDoc(userRef, {
        subscription: {
          planId: selectedPlanModal.id,
          planName: selectedPlanModal.name,
          billingCycle: billingCycle,
          monthlyPrice: selectedPlanModal.monthlyPrice,
          annualPrice: selectedPlanModal.annualPrice,
          vestLimit: selectedPlanModal.vests,
          ssoLimit: selectedPlanModal.ssos,
          storageGb: selectedPlanModal.storageGb,
          discountPercent: selectedPlanModal.discountPercent,
          assignedAt: now,
          renewDate: renew,
          status: 'Active'
        }
      });

      const userEmail = profile?.email || user?.email || 'Contractor';
      logActivity(`Contractor subscribed to ${selectedPlanModal.name} Plan (${billingCycle}) - ${userEmail}`, userEmail);

      setToastMsg(`Successfully upgraded to ${selectedPlanModal.name} Plan (${billingCycle})!`);
      setSelectedPlanModal(null);
      setTimeout(() => setToastMsg(''), 5000);
    } catch (err) {
      console.error('Failed to update subscription:', err);
      alert('Could not update subscription: ' + err.message);
    } finally {
      setUpdating(false);
    }
  };

  const handleConfirmCustomPlan = async () => {
    if (!contractorId) return;
    const vestCount = parseInt(customVests, 10);
    const ssoAllowance = parseInt(customSsos, 10);

    if (isNaN(vestCount) || vestCount <= 125) {
      alert('Custom tier calculation is specifically for deployments of more than 125 vests (minimum 126 vests). For up to 100 vests, please choose the Business or Enterprise tiers.');
      return;
    }
    if (isNaN(ssoAllowance) || ssoAllowance <= 0) {
      alert('Please enter a valid number of Site Safety Officers.');
      return;
    }

    const pricing = calculateCustomPricing(vestCount, 10);
    setUpdating(true);
    try {
      const now = new Date();
      const renew = calculateRenewalDate(now, customBillingCycle);
      const userRef = doc(db, 'users', contractorId);

      await updateDoc(userRef, {
        subscription: {
          planId: 'CUSTOM',
          planName: 'Custom',
          billingCycle: customBillingCycle,
          vestLimit: vestCount,
          ssoLimit: ssoAllowance,
          storageGb: 100,
          monthlyPrice: pricing.monthlyPrice,
          annualPrice: pricing.annualPrice,
          discountPercent: 10,
          assignedAt: now,
          renewDate: renew,
          status: 'Active'
        }
      });

      const userEmail = profile?.email || user?.email || 'Contractor';
      logActivity(`Contractor subscribed to Custom Plan (${vestCount} vests, ${ssoAllowance} SSOs, ${customBillingCycle}) - ${userEmail}`, userEmail);

      setToastMsg(`Successfully activated Custom Plan (${vestCount} vests, ${ssoAllowance} SSOs, ${customBillingCycle})!`);
      setShowCustomModal(false);
      setTimeout(() => setToastMsg(''), 5000);
    } catch (err) {
      console.error('Failed to update custom subscription:', err);
      alert('Could not update custom subscription: ' + err.message);
    } finally {
      setUpdating(false);
    }
  };

  return (
    <div className="subscription-container">
      <PageHeader
        title="Subscription"
        subtitle="Manage your safety monitoring tier, hardware allowance, and billing renewal."
      />

      {toastMsg && (
        <div className="sub-toast-success">
          <FiCheckCircle style={{ fontSize: '1.25rem', flexShrink: 0 }} />
          <span>{toastMsg}</span>
        </div>
      )}

      {/* Current Active Plan Overview Card */}
      <div className="current-sub-card">
        <div className="current-sub-header">
          <div className="current-sub-title-group">
            <span className="current-sub-kicker">
              {subscription.hasPlan ? 'ACTIVE SUBSCRIPTION' : 'SUBSCRIPTION STATUS'}
            </span>
            <div className="current-sub-heading-row">
              <h2>{subscription.hasPlan ? `${subscription.planName} Plan` : 'No Active Subscription'}</h2>
              <span 
                className={`sub-status-pill ${subscription.hasPlan ? 'active' : ''}`}
                style={!subscription.hasPlan ? { backgroundColor: '#f1f5f9', color: '#64748b' } : {}}
              >
                <span 
                  className="sub-status-dot" 
                  style={!subscription.hasPlan ? { backgroundColor: '#94a3b8' } : {}}
                ></span>
                {subscription.hasPlan ? (subscription.status || 'Active') : 'No Plan'}
              </span>
            </div>
            <p className="current-sub-desc">
              {!subscription.hasPlan
                ? 'Your contractor account currently has no subscription package. You can still access your dashboard and manage sites. Choose or configure a tier below to activate hardware quota and Site Safety Officers.'
                : subscription.planId === 'CUSTOM'
                ? `Custom enterprise tier with ${subscription.vestLimit} smart vests and ${subscription.ssoLimit} Site Safety Officers.`
                : `Assigned tier includes up to ${subscription.vestLimit} smart vests and ${subscription.ssoLimit} Site Safety Officers.`}
            </p>
          </div>

          <div className="current-sub-pricing-badge">
            <span className="current-sub-price-label">Current Rate</span>
            <div className="current-sub-price-val">
              {!subscription.hasPlan
                ? '₱0'
                : subscription.billingCycle === 'annual'
                ? formatPhp(subscription.annualPrice)
                : formatPhp(subscription.monthlyPrice)}
              <span className="current-sub-price-unit">
                {!subscription.hasPlan ? '/month' : `/${subscription.billingCycle === 'annual' ? 'annual' : 'month'}`}
              </span>
            </div>
            {subscription.hasPlan && subscription.discountPercent > 0 && (
              <span className="sub-discount-tag">Includes {subscription.discountPercent}% Discount</span>
            )}
          </div>
        </div>

        {/* Plan Dates & Metadata Grid */}
        <div className="sub-meta-grid">
          <div className="sub-meta-item">
            <div className="sub-meta-icon"><FiCalendar /></div>
            <div>
              <span className="sub-meta-label">Date Assigned</span>
              <span className="sub-meta-value">
                {subscription.hasPlan ? formatDisplayDate(subscription.assignedAt) : 'Not assigned'}
              </span>
            </div>
          </div>

          <div className="sub-meta-item">
            <div className="sub-meta-icon"><FiClock /></div>
            <div>
              <span className="sub-meta-label">Renews On</span>
              <span className="sub-meta-value">
                {subscription.hasPlan ? formatDisplayDate(subscription.renewDate) : 'Not active'}
              </span>
            </div>
          </div>

          <div className="sub-meta-item">
            <div className="sub-meta-icon"><FiHardDrive /></div>
            <div>
              <span className="sub-meta-label">Cloud Storage</span>
              <span className="sub-meta-value">
                {subscription.hasPlan ? `${subscription.storageGb} GB Allocated` : '0 GB'}
              </span>
            </div>
          </div>

          <div className="sub-meta-item">
            <div className="sub-meta-icon"><FiShield /></div>
            <div>
              <span className="sub-meta-label">Billing Cycle</span>
              <span className="sub-meta-value" style={{ textTransform: 'capitalize' }}>
                {subscription.hasPlan ? `${subscription.billingCycle} Renewal` : 'None'}
              </span>
            </div>
          </div>
        </div>

        {/* Quota Progress Utilization */}
        <div className="sub-quota-row">
          <div className="sub-quota-item">
            <div className="sub-quota-labels">
              <span className="sub-quota-title">
                <FiUsers style={{ marginRight: '0.4rem', verticalAlign: '-1px' }} />
                Site Safety Officers (SSO)
              </span>
              <span className="sub-quota-count">
                <strong>{ssoCount}</strong> / {subscription.hasPlan ? maxSso : 0} Created
              </span>
            </div>
            <div className="sub-progress-track">
              <div 
                className="sub-progress-fill" 
                style={{ 
                  width: `${subscription.hasPlan ? ssoPct : 0}%`, 
                  backgroundColor: ssoPct > 90 ? '#ef4444' : 'var(--primary-color, #f97316)' 
                }} 
              />
            </div>
            <span className="sub-quota-subtext">
              {!subscription.hasPlan
                ? 'No active SSO slots allocated. Select a plan below.'
                : typeof maxSso === 'number' && maxSso > ssoCount
                ? `${maxSso - ssoCount} SSO account slots available to invite`
                : 'Maximum SSO accounts reached for this plan'}
            </span>
          </div>

          <div className="sub-quota-item">
            <div className="sub-quota-labels">
              <span className="sub-quota-title">
                <FiCpu style={{ marginRight: '0.4rem', verticalAlign: '-1px' }} />
                Smart Monitored Vests
              </span>
              <span className="sub-quota-count">
                <strong>{vestAssignedCount}</strong> / {subscription.hasPlan ? maxVests : 0} Assigned
              </span>
            </div>
            <div className="sub-progress-track">
              <div 
                className="sub-progress-fill" 
                style={{ 
                  width: `${subscription.hasPlan ? vestPct : 0}%`, 
                  backgroundColor: vestPct > 90 ? '#ef4444' : 'var(--primary-color, #f97316)' 
                }} 
              />
            </div>
            <span className="sub-quota-subtext">
              {!subscription.hasPlan
                ? 'No active vest quota allocated. Select a plan below.'
                : typeof maxVests === 'number' && maxVests > vestAssignedCount
                ? `${maxVests - vestAssignedCount} smart vests available for site workers`
                : 'All allocated vests currently assigned'}
            </span>
          </div>
        </div>
      </div>

      {/* Plan Selection Section - 3 Tiers: Business, Enterprise, Custom */}
      <div className="sub-plans-section">
        <div className="sub-plans-header">
          <div>
            <h2 className="sub-plans-title">Available Plans & Tiers</h2>
            <p className="sub-plans-subtitle">
              Scale your worker safety monitoring by selecting or upgrading your package.
            </p>
          </div>

          {/* Monthly / Annual Pill Toggle */}
          <div className="sub-toggle-pill-container" role="radiogroup" aria-label="Billing cycle">
            <button
              type="button"
              className={`sub-toggle-pill-btn ${billingCycle === 'monthly' ? 'active' : ''}`}
              onClick={() => setBillingCycle('monthly')}
            >
              MONTHLY
            </button>
            <button
              type="button"
              className={`sub-toggle-pill-btn ${billingCycle === 'annual' ? 'active' : ''}`}
              onClick={() => setBillingCycle('annual')}
            >
              ANNUAL
            </button>
          </div>
        </div>

        {/* 3 Cards Grid - Business, Enterprise, Scale/Custom */}
        <div className="sub-cards-grid">

          {/* 1. BUSINESS CARD (No discount) */}
          <div className={`sub-tier-card ${isCurrent('BUSINESS') ? 'is-active-plan' : ''}`}>
            {isCurrent('BUSINESS') && <span className="sub-card-badge">Current Plan</span>}
            <div className="sub-tier-header">
              <span className="sub-tier-tag">BUSINESS</span>
              <div className="sub-price-block">
                <div className="sub-price-main">
                  {billingCycle === 'annual'
                    ? formatPhp(SUBSCRIPTION_PLANS.BUSINESS.annualPrice)
                    : formatPhp(SUBSCRIPTION_PLANS.BUSINESS.monthlyPrice)}
                </div>
                <div className="sub-price-cycle">
                  /{billingCycle === 'annual' ? 'annual' : 'monthly'}
                </div>
              </div>
            </div>

            <hr className="sub-tier-divider" />

            <ul className="sub-features-list">
              {SUBSCRIPTION_PLANS.BUSINESS.features.map((feat, idx) => (
                <li key={idx} className="sub-feature-item">
                  <span className="sub-bullet-dot"></span>
                  <span>{feat}</span>
                </li>
              ))}
            </ul>

            <div className="sub-tier-footer">
              <button
                type="button"
                className={`sub-action-pill-btn ${isCurrent('BUSINESS') ? 'btn-current' : 'btn-active'}`}
                onClick={() => handleSelectPlan(SUBSCRIPTION_PLANS.BUSINESS)}
                disabled={isCurrent('BUSINESS') && subscription.billingCycle === billingCycle}
              >
                {isCurrent('BUSINESS') && subscription.billingCycle === billingCycle
                  ? 'Current Plan'
                  : 'Get Business'}
              </button>
            </div>
          </div>

          {/* 2. ENTERPRISE CARD (10% discount) */}
          <div className={`sub-tier-card ${isCurrent('ENTERPRISE') ? 'is-active-plan' : ''}`}>
            {isCurrent('ENTERPRISE') && <span className="sub-card-badge">Current Plan</span>}
            <div className="sub-tier-header">
              <span className="sub-tier-tag">ENTERPRISE</span>
              <div className="sub-price-block">
                {/* Strikethrough base price */}
                <div className="sub-price-original">
                  {billingCycle === 'annual'
                    ? formatPhp(SUBSCRIPTION_PLANS.ENTERPRISE.baseAnnualPrice)
                    : formatPhp(SUBSCRIPTION_PLANS.ENTERPRISE.baseMonthlyPrice)}
                </div>
                {/* Discounted price */}
                <div className="sub-price-main">
                  {billingCycle === 'annual'
                    ? formatPhp(SUBSCRIPTION_PLANS.ENTERPRISE.annualPrice)
                    : formatPhp(SUBSCRIPTION_PLANS.ENTERPRISE.monthlyPrice)}
                </div>
                <div className="sub-price-cycle">
                  /{billingCycle === 'annual' ? 'annual' : 'monthly'}
                </div>
              </div>
            </div>

            <hr className="sub-tier-divider" />

            <ul className="sub-features-list">
              {SUBSCRIPTION_PLANS.ENTERPRISE.features.map((feat, idx) => (
                <li key={idx} className="sub-feature-item">
                  <span className="sub-bullet-dot"></span>
                  <span>{feat}</span>
                </li>
              ))}
            </ul>

            <div className="sub-tier-footer">
              <button
                type="button"
                className={`sub-action-pill-btn ${isCurrent('ENTERPRISE') ? 'btn-current' : 'btn-active'}`}
                onClick={() => handleSelectPlan(SUBSCRIPTION_PLANS.ENTERPRISE)}
                disabled={isCurrent('ENTERPRISE') && subscription.billingCycle === billingCycle}
              >
                {isCurrent('ENTERPRISE') && subscription.billingCycle === billingCycle
                  ? 'Current Plan'
                  : 'Get Enterprise'}
              </button>
            </div>
          </div>

          {/* 3. SCALE / CUSTOM CARD (> 125 Vests) */}
          <div className={`sub-tier-card ${isCurrent('CUSTOM') ? 'is-active-plan' : ''}`}>
            {isCurrent('CUSTOM') && <span className="sub-card-badge">Current Plan</span>}
            <div className="sub-tier-header">
              <span className="sub-tier-tag">SCALE</span>
              <div className="sub-price-block">
                <div className="sub-price-main" style={{ letterSpacing: '-0.02em' }}>
                  {isCurrent('CUSTOM') 
                    ? formatPhp(subscription.monthlyPrice) 
                    : 'CUSTOM'}
                </div>
                <div className="sub-price-cycle" style={{ color: '#64748b', fontWeight: 500 }}>
                  {isCurrent('CUSTOM')
                    ? `(${subscription.vestLimit} Vests • ${subscription.ssoLimit} SSOs)`
                    : 'Enterprise Solutions (> 125 Vests)'}
                </div>
              </div>
            </div>

            <hr className="sub-tier-divider" />

            <ul className="sub-features-list">
              <li className="sub-feature-item">
                <span className="sub-bullet-dot"></span>
                <span>Custom quota for &gt; 125 Vests &amp; SSOs</span>
              </li>
              <li className="sub-feature-item">
                <span className="sub-bullet-dot"></span>
                <span>Custom calculation live pricing</span>
              </li>
              <li className="sub-feature-item">
                <span className="sub-bullet-dot"></span>
                <span>Everything in Enterprise included</span>
              </li>
              <li className="sub-feature-item">
                <span className="sub-bullet-dot"></span>
                <span>Unlimited Data Storage (100 GB)</span>
              </li>
              <li className="sub-feature-item">
                <span className="sub-bullet-dot"></span>
                <span>Dedicated Safety Account Manager</span>
              </li>
            </ul>

            <div className="sub-tier-footer">
              <button
                type="button"
                className={`sub-action-pill-btn ${isCurrent('CUSTOM') ? 'btn-current' : 'btn-active'}`}
                onClick={() => setShowCustomModal(true)}
              >
                {isCurrent('CUSTOM') ? 'Adjust Custom Plan' : 'Configure Custom Tier'}
              </button>
            </div>
          </div>

        </div>
      </div>

      {/* Upgrade Confirmation Modal for Fixed Plans (Business, Enterprise) */}
      {selectedPlanModal && (
        <div className="modal-backdrop" onClick={() => !updating && setSelectedPlanModal(null)}>
          <div className="modal-content sub-modal" onClick={e => e.stopPropagation()}>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '0.5rem' }}>
              Confirm Plan Selection
            </h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginBottom: '1.5rem' }}>
              You are selecting the <strong>{selectedPlanModal.name} Plan</strong> with{' '}
              <strong>{billingCycle}</strong> billing.
            </p>

            <div className="sub-modal-summary">
              <div className="sub-modal-row">
                <span>Hardware Limit:</span>
                <strong>{selectedPlanModal.vests} Smart Vests</strong>
              </div>
              <div className="sub-modal-row">
                <span>Site Safety Officers:</span>
                <strong>{selectedPlanModal.ssos} Officers</strong>
              </div>
              <div className="sub-modal-row">
                <span>Billing Rate:</span>
                <strong style={{ color: 'var(--primary-color)' }}>
                  {billingCycle === 'annual'
                    ? `${formatPhp(selectedPlanModal.annualPrice)} / year`
                    : `${formatPhp(selectedPlanModal.monthlyPrice)} / month`}
                </strong>
              </div>
              {selectedPlanModal.discountPercent > 0 && (
                <div className="sub-modal-row">
                  <span>Savings Discount:</span>
                  <strong style={{ color: '#16a34a' }}>{selectedPlanModal.discountPercent}% Off Applied</strong>
                </div>
              )}
            </div>

            <div className="modal-actions" style={{ marginTop: '1.5rem' }}>
              <button
                className="btn-secondary"
                onClick={() => setSelectedPlanModal(null)}
                disabled={updating}
              >
                Cancel
              </button>
              <button
                className="btn-primary"
                onClick={handleConfirmUpgrade}
                disabled={updating}
              >
                {updating ? 'Updating...' : `Confirm & Switch to ${selectedPlanModal.name}`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Custom Tier Interactive Calculator Modal (> 125 Vests & Custom SSOs) */}
      {showCustomModal && (
        <div className="modal-backdrop" onClick={() => !updating && setShowCustomModal(false)}>
          <div className="modal-content sub-modal" onClick={e => e.stopPropagation()} style={{ maxWidth: '540px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
                <div style={{ padding: '0.45rem', backgroundColor: '#ffedd5', color: '#ea580c', borderRadius: '8px' }}>
                  <FiTrendingUp style={{ fontSize: '1.25rem' }} />
                </div>
                <div>
                  <h3 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0, color: '#0f172a' }}>
                    Custom Tier Pricing Calculator
                  </h3>
                  <span style={{ fontSize: '0.78rem', color: '#64748b' }}>Enterprise scale for &gt; 125 Vests</span>
                </div>
              </div>
              <button 
                type="button" 
                onClick={() => setShowCustomModal(false)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.35rem', color: '#94a3b8' }}
              >
                <FiX />
              </button>
            </div>

            <p style={{ color: '#64748b', fontSize: '0.85rem', lineHeight: 1.5, margin: '0.75rem 0 1.25rem 0' }}>
              Specify your project's custom requirements. Custom plans apply an automatic 10% volume discount for large deployments.
            </p>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.25rem' }}>
              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, color: '#334155' }}>
                  CUSTOM VEST COUNT (&gt; 125)
                </label>
                <input
                  type="number"
                  className="form-input"
                  min="1"
                  value={customVests}
                  onChange={(e) => setCustomVests(e.target.value)}
                  placeholder="e.g. 150"
                  style={{ width: '100%', padding: '0.65rem 0.85rem' }}
                />
                {Number(customVests) <= 125 ? (
                  <span style={{ fontSize: '0.72rem', color: '#dc2626', display: 'block', marginTop: '0.25rem' }}>
                    ⚠️ Custom tier requires more than 125 vests (minimum 126).
                  </span>
                ) : (
                  <span style={{ fontSize: '0.72rem', color: '#16a34a', display: 'block', marginTop: '0.25rem' }}>
                    ✓ Custom scale tier ({customVests} vests)
                  </span>
                )}
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, color: '#334155' }}>
                  CUSTOM SSO ALLOWANCE
                </label>
                <input
                  type="number"
                  className="form-input"
                  min="1"
                  value={customSsos}
                  onChange={(e) => setCustomSsos(e.target.value)}
                  placeholder="e.g. 30"
                  style={{ width: '100%', padding: '0.65rem 0.85rem' }}
                />
                <span style={{ fontSize: '0.72rem', color: '#64748b', display: 'block', marginTop: '0.25rem' }}>
                  Number of officer accounts allowed
                </span>
              </div>
            </div>

            <div style={{ marginBottom: '1.25rem' }}>
              <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, color: '#334155' }}>
                BILLING CYCLE
              </label>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button
                  type="button"
                  className={`btn-secondary ${customBillingCycle === 'monthly' ? 'btn-active' : ''}`}
                  onClick={() => setCustomBillingCycle('monthly')}
                  style={{
                    flex: 1,
                    padding: '0.65rem',
                    borderRadius: '8px',
                    borderColor: customBillingCycle === 'monthly' ? '#ea580c' : '#cbd5e1',
                    backgroundColor: customBillingCycle === 'monthly' ? '#fff7ed' : '#ffffff',
                    color: customBillingCycle === 'monthly' ? '#ea580c' : '#475569',
                    fontWeight: 700
                  }}
                >
                  Monthly Renewal
                </button>
                <button
                  type="button"
                  className={`btn-secondary ${customBillingCycle === 'annual' ? 'btn-active' : ''}`}
                  onClick={() => setCustomBillingCycle('annual')}
                  style={{
                    flex: 1,
                    padding: '0.65rem',
                    borderRadius: '8px',
                    borderColor: customBillingCycle === 'annual' ? '#ea580c' : '#cbd5e1',
                    backgroundColor: customBillingCycle === 'annual' ? '#fff7ed' : '#ffffff',
                    color: customBillingCycle === 'annual' ? '#ea580c' : '#475569',
                    fontWeight: 700
                  }}
                >
                  Annual (Yearly)
                </button>
              </div>
            </div>

            {/* Custom Pricing Live Calculation Breakdown */}
            <div style={{ backgroundColor: '#f8fafc', border: '1px solid #cbd5e1', borderRadius: '10px', padding: '1rem', marginBottom: '1.5rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.825rem', color: '#64748b', marginBottom: '0.4rem' }}>
                <span>Base Hardware Rate:</span>
                <span>₱4,000 / vest / month</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.825rem', color: '#64748b', marginBottom: '0.4rem' }}>
                <span>Base Price ({customPricing.vests} vests × ₱4,000):</span>
                <span>{formatPhp(customPricing.baseMonthlyPrice)} /mo</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.825rem', color: '#16a34a', marginBottom: '0.5rem' }}>
                <span>Scale Volume Discount (10% off):</span>
                <span>-{formatPhp(customPricing.baseMonthlyPrice - customPricing.monthlyPrice)} /mo</span>
              </div>
              <hr style={{ border: 'none', borderTop: '1px dashed #cbd5e1', margin: '0.5rem 0' }} />
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <span style={{ fontSize: '0.78rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600, display: 'block' }}>
                    Calculated {customBillingCycle} Rate
                  </span>
                  <span style={{ fontSize: '1.35rem', fontWeight: 800, color: '#ea580c' }}>
                    {customBillingCycle === 'annual'
                      ? `${formatPhp(customPricing.annualPrice)} /yr`
                      : `${formatPhp(customPricing.monthlyPrice)} /mo`}
                  </span>
                </div>
                <div style={{ textAlign: 'right', fontSize: '0.75rem', color: '#64748b' }}>
                  <span>{customPricing.vests} Vests</span> • <span>{customSsos} SSOs</span><br />
                  <span>100 GB Cloud Storage</span>
                </div>
              </div>
            </div>

            <div className="modal-actions">
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setShowCustomModal(false)}
                disabled={updating}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary"
                onClick={handleConfirmCustomPlan}
                disabled={updating || Number(customVests) <= 125 || Number(customSsos) <= 0}
              >
                {updating ? 'Saving...' : (isCurrent('CUSTOM') ? 'Update Custom Plan' : 'Confirm & Subscribe to Custom')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Talk to Sales Modal */}
      {showSalesModal && (
        <div className="modal-backdrop" onClick={() => setShowSalesModal(false)}>
          <div className="modal-content sub-modal" onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
              <div style={{ padding: '0.5rem', background: '#ffedd5', color: '#ea580c', borderRadius: '8px' }}>
                <FiPhoneCall style={{ fontSize: '1.25rem' }} />
              </div>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700 }}>Custom Enterprise Solutions</h3>
            </div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginBottom: '1.25rem', lineHeight: 1.5 }}>
              Need multi-site custom integrations, dedicated telemetry servers, or hardware leasing?
            </p>

            <div className="sub-modal-summary">
              <div className="sub-modal-row">
                <span>Enterprise Contact:</span>
                <strong>sales@bevest.com</strong>
              </div>
              <div className="sub-modal-row">
                <span>Hotline:</span>
                <strong>+63 (2) 8800-VEST</strong>
              </div>
              <div className="sub-modal-row">
                <span>Response Time:</span>
                <strong>Within 24 Hours</strong>
              </div>
            </div>

            <div className="modal-actions" style={{ marginTop: '1.5rem' }}>
              <button className="btn-primary" onClick={() => setShowSalesModal(false)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ContractorSubscription;
