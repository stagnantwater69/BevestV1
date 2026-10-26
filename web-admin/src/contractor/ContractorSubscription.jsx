import React, { useState } from 'react';
import { 
  FiCheck, FiCalendar, FiClock, FiShield, FiUsers, FiCpu, 
  FiHardDrive, FiAlertCircle, FiPhoneCall, FiChevronRight, FiCheckCircle
} from 'react-icons/fi';
import { doc, updateDoc } from 'firebase/firestore';
import { db } from '../firebase';
import { 
  SUBSCRIPTION_PLANS, 
  formatPhp, 
  formatDisplayDate, 
  calculateRenewalDate, 
  getContractorSubscription 
} from '../subscriptionPlans';
import { PageHeader } from './ui';

const ContractorSubscription = ({ user, profile, data }) => {
  const [billingCycle, setBillingCycle] = useState('monthly'); // 'monthly' | 'annual'
  const [selectedPlanModal, setSelectedPlanModal] = useState(null);
  const [showSalesModal, setShowSalesModal] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [toastMsg, setToastMsg] = useState('');

  const contractorId = profile?.contractorId || user?.uid;
  const subscription = getContractorSubscription(profile || user?.profile || {});

  // Current utilization from real-time contractor data
  const ssoCount = data?.officers?.length || 0;
  const vestAssignedCount = data?.workers?.filter(w => w.assignedVestId)?.length || 0;
  const maxSso = subscription.ssoLimit === 'Custom' ? 'Custom' : Number(subscription.ssoLimit || 10);
  const maxVests = subscription.vestLimit === 'Custom' ? 'Custom' : Number(subscription.vestLimit || 50);

  const ssoPct = typeof maxSso === 'number' && maxSso > 0 ? Math.min(100, Math.round((ssoCount / maxSso) * 100)) : 0;
  const vestPct = typeof maxVests === 'number' && maxVests > 0 ? Math.min(100, Math.round((vestAssignedCount / maxVests) * 100)) : 0;

  const isCurrent = (planId) => subscription.planId === planId;

  const handleSelectPlan = (plan) => {
    if (plan.id === 'CUSTOM') {
      setShowSalesModal(true);
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
            <span className="current-sub-kicker">ACTIVE SUBSCRIPTION</span>
            <div className="current-sub-heading-row">
              <h2>{subscription.planName} Plan</h2>
              <span className="sub-status-pill active">
                <span className="sub-status-dot"></span>
                {subscription.status || 'Active'}
              </span>
            </div>
            <p className="current-sub-desc">
              {subscription.planId === 'CUSTOM'
                ? `Custom enterprise tier for ${subscription.vestLimit} smart vests and ${subscription.ssoLimit} Site Safety Officers.`
                : `Assigned tier includes up to ${subscription.vestLimit} smart vests and ${subscription.ssoLimit} Site Safety Officers.`}
            </p>
          </div>

          <div className="current-sub-pricing-badge">
            <span className="current-sub-price-label">Current Rate</span>
            <div className="current-sub-price-val">
              {subscription.billingCycle === 'annual'
                ? formatPhp(subscription.annualPrice)
                : formatPhp(subscription.monthlyPrice)}
              <span className="current-sub-price-unit">/{subscription.billingCycle === 'annual' ? 'annual' : 'month'}</span>
            </div>
            {subscription.discountPercent > 0 && (
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
              <span className="sub-meta-value">{formatDisplayDate(subscription.assignedAt)}</span>
            </div>
          </div>

          <div className="sub-meta-item">
            <div className="sub-meta-icon"><FiClock /></div>
            <div>
              <span className="sub-meta-label">Renews On</span>
              <span className="sub-meta-value">{formatDisplayDate(subscription.renewDate)}</span>
            </div>
          </div>

          <div className="sub-meta-item">
            <div className="sub-meta-icon"><FiHardDrive /></div>
            <div>
              <span className="sub-meta-label">Cloud Storage</span>
              <span className="sub-meta-value">{subscription.storageGb} GB Allocated</span>
            </div>
          </div>

          <div className="sub-meta-item">
            <div className="sub-meta-icon"><FiShield /></div>
            <div>
              <span className="sub-meta-label">Billing Cycle</span>
              <span className="sub-meta-value" style={{ textTransform: 'capitalize' }}>
                {subscription.billingCycle} Renewal
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
                <strong>{ssoCount}</strong> / {maxSso} Created
              </span>
            </div>
            <div className="sub-progress-track">
              <div 
                className="sub-progress-fill" 
                style={{ 
                  width: `${ssoPct}%`, 
                  backgroundColor: ssoPct > 90 ? '#ef4444' : 'var(--primary-color, #f97316)' 
                }} 
              />
            </div>
            <span className="sub-quota-subtext">
              {typeof maxSso === 'number' && maxSso > ssoCount
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
                <strong>{vestAssignedCount}</strong> / {maxVests} Assigned
              </span>
            </div>
            <div className="sub-progress-track">
              <div 
                className="sub-progress-fill" 
                style={{ 
                  width: `${vestPct}%`, 
                  backgroundColor: vestPct > 90 ? '#ef4444' : 'var(--primary-color, #f97316)' 
                }} 
              />
            </div>
            <span className="sub-quota-subtext">
              {typeof maxVests === 'number' && maxVests > vestAssignedCount
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
                  CUSTOM
                </div>
                <div className="sub-price-cycle" style={{ color: '#64748b', fontWeight: 500 }}>
                  Enterprise Solutions (&gt; 125 Vests)
                </div>
              </div>
            </div>

            <hr className="sub-tier-divider" />

            <ul className="sub-features-list">
              {SUBSCRIPTION_PLANS.CUSTOM.features.map((feat, idx) => (
                <li key={idx} className="sub-feature-item">
                  <span className="sub-bullet-dot"></span>
                  <span>{feat}</span>
                </li>
              ))}
            </ul>

            <div className="sub-tier-footer">
              <button
                type="button"
                className="sub-action-pill-btn btn-active"
                onClick={() => handleSelectPlan(SUBSCRIPTION_PLANS.CUSTOM)}
              >
                Talk to Sales
              </button>
            </div>
          </div>

        </div>
      </div>

      {/* Upgrade Confirmation Modal */}
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
              Need more than 125 vests, multi-site enterprise monitoring, dedicated telemetry servers, or custom integrations?
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
