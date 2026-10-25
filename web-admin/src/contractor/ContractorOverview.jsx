import React, { useMemo } from 'react';
import { FiUsers, FiShield, FiBell, FiClock, FiFileText, FiUserCheck, FiChevronRight } from 'react-icons/fi';
import {
  STATUS_COLOR, buildMonthlyReports, monthLabel, plural,
} from './contractorData';
import {
  PageHeader, SectionHeader, StatTile, Insight, StatusBar, TrendBars, EmptyState, ErrorState,
  IncidentRow, Skeleton,
} from './ui';

const DAY = 24 * 3600 * 1000;

/**
 * Port of ContractorDashboardScreen: the contractor's overview across every site.
 * It is about *trend* — "are we getting safer?" — so each block leads with a
 * sentence stating the answer and puts the chart underneath as evidence.
 */
const ContractorOverview = ({ data, onManageSsos, onNavigate }) => {
  const { loading, error, workers, liveWorkers, incidents, now } = data;

  const state = useMemo(() => {
    const activeWorkers = workers.filter((w) => w.active).length;
    const normal = liveWorkers.filter((l) => l.status === 'NORMAL').length;
    const warning = liveWorkers.filter((l) => l.status === 'WARNING').length;
    const danger = liveWorkers.filter((l) => l.status === 'DANGER' || l.status === 'EMERGENCY').length;
    const todaysAlerts = incidents.filter((i) => i.createdAt >= now - DAY).length;
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

    const wWord = (n) => plural(n, 'worker is', 'workers are');
    let statusInsight;
    if (activeWorkers === 0) statusInsight = 'No workers are being monitored right now.';
    else if (danger > 0) statusInsight = `${danger} of ${activeWorkers} monitored ${wWord(danger)} at a dangerous level.`;
    else if (warning > 0) statusInsight = `${warning} of ${activeWorkers} monitored ${wWord(warning)} above a warning threshold.`;
    else statusInsight = `All ${activeWorkers} monitored workers are within safe limits.`;

    return {
      activeWorkers, normal, warning, danger, todaysAlerts, monthlyIncidents,
      trends, safetyScore, incidentInsight, statusInsight, recent: incidents.slice(0, 5),
    };
  }, [workers, liveWorkers, incidents, now]);

  const nameById = useMemo(
    () => Object.fromEntries(workers.map((w) => [w.id, w.fullName])),
    [workers],
  );

  const header = (
    <PageHeader
      title="Safety overview"
      subtitle="All sites · live across every project you run"
      actions={(
        <button id="ct-manage-ssos-top" className="btn-secondary ct-btn-icon" onClick={onManageSsos}>
          <FiUserCheck /> Site Safety Officers
        </button>
      )}
    />
  );

  if (loading) return (<>{header}<Skeleton tiles={4} rows={3} /></>);
  if (error) return (<>{header}<ErrorState message={error} /></>);

  const statusAccent = state.danger > 0 ? STATUS_COLOR.DANGER
    : state.warning > 0 ? STATUS_COLOR.WARNING : STATUS_COLOR.NORMAL;

  return (
    <>
      {header}

      {/* ---- Headline figures ---- */}
      <div className="ct-stat-grid">
        <StatTile icon={FiUsers} value={state.activeWorkers} label="Active workers" />
        <StatTile
          icon={FiShield}
          value={`${state.safetyScore}%`}
          label="Safety score"
          accent={state.safetyScore >= 90 ? STATUS_COLOR.NORMAL : STATUS_COLOR.WARNING}
        />
        <StatTile
          icon={FiBell}
          value={state.todaysAlerts}
          label="Alerts today"
          accent={state.todaysAlerts > 0 ? STATUS_COLOR.WARNING : undefined}
        />
        <StatTile
          icon={FiClock}
          value={state.monthlyIncidents}
          label="Incidents (30d)"
          accent={state.monthlyIncidents > 0 ? STATUS_COLOR.DANGER : undefined}
        />
      </div>

      <div className="ct-two-col">
        {/* ---- Who is where, right now ---- */}
        <section>
          <SectionHeader title="Worker status" supporting="Across every site you run" />
          <div className="ct-card">
            <Insight icon={FiUsers} text={state.statusInsight} accent={statusAccent} />
            <StatusBar
              slices={[
                { label: 'Normal', count: state.normal, color: STATUS_COLOR.NORMAL },
                { label: 'Warning', count: state.warning, color: STATUS_COLOR.WARNING },
                { label: 'Danger', count: state.danger, color: STATUS_COLOR.DANGER },
              ]}
            />
            <button id="ct-open-workers" className="ct-link-btn" onClick={() => onNavigate('Workers')}>
              Open worker directory <FiChevronRight />
            </button>
          </div>
        </section>

        {/* ---- Trend ---- */}
        <section>
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
      </div>

      {/* ---- Recent incidents ---- */}
      <SectionHeader
        title="Recent incidents"
        supporting="Newest first"
        trailing={incidents.length > 0 && (
          <button id="ct-view-all-incidents" className="view-all ct-plain-btn" onClick={() => onNavigate('Incidents')}>
            Incident history
          </button>
        )}
      />
      {state.recent.length === 0 ? (
        <EmptyState
          icon={FiClock}
          title="No incidents recorded"
          message="Incidents appear here once an alert is closed with an outcome."
        />
      ) : (
        <div className="ct-list">
          {state.recent.map((i) => (
            <IncidentRow key={i.id} incident={i} workerName={nameById[i.workerId]} />
          ))}
        </div>
      )}

      <button id="ct-manage-ssos" className="ct-wide-btn" onClick={onManageSsos}>
        <FiUserCheck /> Manage Site Safety Officers
      </button>
    </>
  );
};

export default ContractorOverview;
