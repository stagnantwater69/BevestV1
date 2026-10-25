import React, { useMemo } from 'react';
import { FiShield, FiClock, FiFileText, FiUserCheck } from 'react-icons/fi';
import {
  STATUS_COLOR, buildMonthlyReports, monthLabel, plural, useContractorSsos,
} from './contractorData';
import {
  PageHeader, SectionHeader, StatTile, Insight, TrendBars, ErrorState,
  Skeleton,
} from './ui';

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

    const word = (n) => plural(n, 'incident', 'incidents');
    const current = trends[trends.length - 1]?.totalIncidents;
    const previous = trends[trends.length - 2]?.totalIncidents;
    let incidentInsight;
    if (!trends.length) incidentInsight = 'Not enough history yet to show a trend.';
    else if (current == null || previous == null) incidentInsight = `${monthlyIncidents} ${word(monthlyIncidents)} in the last 30 days.`;
    else if (current === previous) incidentInsight = `Level with last month at ${current} ${word(current)}.`;
    else if (current < previous) incidentInsight = `Down from ${previous} to ${current} ${word(current)} since last month.`;
    else incidentInsight = `Up from ${previous} to ${current} ${word(current)} since last month.`;

    const currentMonthKey = trends[trends.length - 1]?.monthKey;

    return {
      activeWorkers, monthlyIncidents,
      trends, safetyScore, incidentInsight, currentMonthKey,
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
          <Insight icon={FiFileText} text={state.incidentInsight} accent={STATUS_COLOR.WARNING} />
          <TrendBars
            data={state.trends.map((t) => ({
              label: monthLabel(t.monthKey).slice(0, 3),
              value: t.totalIncidents,
            }))}
          />
        </div>
      </section>
    </>
  );
};

export default ContractorOverview;
