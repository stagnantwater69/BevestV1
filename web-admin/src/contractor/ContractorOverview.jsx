import React, { useMemo } from 'react';
import { FiShield, FiClock, FiUserCheck } from 'react-icons/fi';
import {
  STATUS_COLOR, buildMonthlyReports, useContractorSsos,
} from './contractorData';
import {
  PageHeader, SectionHeader, StatTile, ErrorState,
  Skeleton,
} from './ui';
import IncidentTrendChart from './IncidentTrendChart';

const DAY = 24 * 3600 * 1000;

/**
 * Port of ContractorDashboardScreen: the contractor's overview across every site.
 * Focuses on trend — "are we getting safer?" — with monthly incident history.
 */
const ContractorOverview = ({ contractorId, data, onNavigate }) => {
  const { loading, error, workers, incidents, now } = data;
  const { ssos, loading: ssosLoading } = useContractorSsos(contractorId);

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
