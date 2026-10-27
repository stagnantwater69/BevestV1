import React, { useMemo } from 'react';
import { FiShield, FiClock, FiUserCheck, FiCreditCard } from 'react-icons/fi';
import {
  STATUS_COLOR, buildMonthlyReports, useContractorSsos,
} from './contractorData';
import {
  PageHeader, SectionHeader, StatTile, ErrorState,
  Skeleton,
} from './ui';
import IncidentTrendChart from './IncidentTrendChart';
import { getContractorSubscription } from '../subscriptionPlans';

const DAY = 24 * 3600 * 1000;

/**
 * Port of ContractorDashboardScreen: the contractor's overview across every site.
 * Focuses on trend — "are we getting safer?" — with monthly incident history.
 */
const ContractorOverview = ({ contractorId, data, profile, onNavigate }) => {
  const { loading, error, workers, incidents, now } = data;
  const { ssos, loading: ssosLoading } = useContractorSsos(contractorId);
  const subscription = getContractorSubscription(profile);

  const activeSsos = useMemo(
    () => ssos.filter((s) => s.active !== false).length,
    [ssos]
  );

  const state = useMemo(() => {
    const activeWorkers = workers.filter((w) => w.active).length;
    const monthlyIncidents = incidents.filter((i) => i.createdAt >= now - 30 * DAY).length;
    const trends = buildMonthlyReports(incidents, activeWorkers, 6).reverse(); // oldest first
    const safetyScore = trends.length ? trends[trends.length - 1].safetyPercentage : 100;
    const currentMonthKey = trends[trends.length - 1]?.monthKey;

    return {
      activeWorkers, monthlyIncidents,
      trends, safetyScore, currentMonthKey,
    };
  }, [workers, incidents, now]);

  const header = (
    <PageHeader
      title="Safety overview"
      subtitle="All sites · live across every project you run"
    />
  );

  if (loading) return (<>{header}<Skeleton tiles={3} rows={2} /></>);
  if (error) return (<>{header}<ErrorState message={error} /></>);

  return (
    <>
      {header}

      {/* No Subscription Plan Notification Banner (Contractor can freely access all dashboard features) */}
      {!subscription.hasPlan && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#fff7ed',
          border: '1px solid #fed7aa',
          borderRadius: '12px',
          padding: '0.85rem 1.25rem',
          marginBottom: '1.5rem',
          gap: '1rem',
          flexWrap: 'wrap'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div style={{ padding: '0.45rem', backgroundColor: '#ffedd5', color: '#ea580c', borderRadius: '8px', display: 'flex' }}>
              <FiCreditCard style={{ fontSize: '1.25rem' }} />
            </div>
            <div>
              <strong style={{ color: '#9a3412', fontSize: '0.9rem', display: 'block' }}>Account Active (No Subscription Plan)</strong>
              <span style={{ color: '#7c2d12', fontSize: '0.825rem' }}>
                You have full access to your safety dashboard, site monitoring, and reports. Choose a subscription package to activate smart vest hardware quotas and Site Safety Officers.
              </span>
            </div>
          </div>
          <button 
            type="button"
            className="btn-primary" 
            onClick={() => onNavigate('Subscription')}
            style={{ padding: '0.45rem 1rem', fontSize: '0.825rem', whiteSpace: 'nowrap' }}
          >
            View Plans
          </button>
        </div>
      )}

      {/* ---- Headline figures ---- */}
      <div className="ct-stat-grid three">
        <StatTile
          id="ct-tile-ssos"
          icon={FiUserCheck}
          value={ssosLoading ? '—' : activeSsos}
          label="Active Site Safety Officers"
          onClick={() => onNavigate('SSOs')}
        />
        <StatTile
          id="ct-tile-safety"
          icon={FiShield}
          value={`${state.safetyScore}%`}
          label="Safety score"
          accent={state.safetyScore >= 90 ? STATUS_COLOR.NORMAL : STATUS_COLOR.WARNING}
          onClick={() => onNavigate('ReportDetail', state.currentMonthKey)}
        />
        <StatTile
          id="ct-tile-incidents"
          icon={FiClock}
          value={state.monthlyIncidents}
          label="Incidents (30d)"
          accent={state.monthlyIncidents > 0 ? STATUS_COLOR.DANGER : undefined}
          onClick={() => onNavigate('Incidents')}
        />
      </div>

      {/* ---- Incident Trend (Full Width) ---- */}
      <section className="ct-incident-trend-section">
        <SectionHeader title="Incident trend" supporting="Recorded incidents by month" />
        <div className="ct-card">
          <IncidentTrendChart incidents={incidents} />
        </div>
      </section>
    </>
  );
};

export default ContractorOverview;
