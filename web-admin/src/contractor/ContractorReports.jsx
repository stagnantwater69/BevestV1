import React, { useMemo, useState } from 'react';
import {
  FiClock, FiChevronRight, FiFileText, FiDownload, FiX, FiUserCheck, FiShield, FiUsers, FiCheckCircle,
} from 'react-icons/fi';
import {
  STATUS_COLOR, SEVERITY_COLOR, SEVERITY_LABEL, ALERT_TYPE_LABEL, ALERT_TYPE_DETAIL,
  buildMonthlyReports, monthKey as toMonthKey, monthLabel, formatDateTime, plural,
} from './contractorData';
import {
  PageHeader, SectionHeader, StatTile, StatusBar, Badge, EmptyState, ErrorState, Skeleton,
  IncidentRow, FilterChips, ALERT_TYPE_ICON,
} from './ui';

const useNameById = (workers) => useMemo(
  () => Object.fromEntries(workers.map((w) => [w.id, w.fullName])),
  [workers],
);

// ------------------------------------------------------------------- reports

/** Port of ContractorReportsScreen. */
const ContractorReports = ({ data, onOpenReport, onOpenIncidents, onOpenSsos }) => {
  const { loading, error, workers, incidents } = data;
  const reports = useMemo(
    () => buildMonthlyReports(incidents, workers.filter((w) => w.active).length, 6),
    [incidents, workers],
  );

  const header = (
    <PageHeader
      title="Reports"
      subtitle="Monthly safety summaries and the full incident record."
    />
  );

  if (loading) return (<>{header}<Skeleton rows={6} /></>);
  if (error) return (<>{header}<ErrorState message={error} /></>);

  return (
    <>
      {header}

      <div className="ct-quick-links">
        <button id="ct-open-incidents" className="ct-quick-link" onClick={onOpenIncidents}>
          <div className="ct-quick-icon"><FiClock /></div>
          <div>
            <strong>Incident history</strong>
            <span>{incidents.length} {plural(incidents.length, 'incident', 'incidents')} on record</span>
          </div>
          <FiChevronRight className="ct-row-chevron" />
        </button>
        <button id="ct-open-ssos" className="ct-quick-link" onClick={onOpenSsos}>
          <div className="ct-quick-icon"><FiUserCheck /></div>
          <div>
            <strong>Site Safety Officers</strong>
            <span>Manage who monitors your sites</span>
          </div>
          <FiChevronRight className="ct-row-chevron" />
        </button>
      </div>

      <SectionHeader title="Monthly reports" supporting="Select a month for detail and a PDF export" />

      {reports.length === 0 ? (
        <EmptyState
          icon={FiFileText}
          title="No reports yet"
          message="A monthly report is generated once a full month of monitoring data exists."
        />
      ) : (
        <div className="ct-list">
          {reports.map((r) => {
            const healthy = r.safetyPercentage >= 90;
            return (
              <div
                key={r.monthKey}
                id={`ct-report-${r.monthKey}`}
                className="ct-row clickable"
                role="button"
                tabIndex={0}
                onClick={() => onOpenReport(r.monthKey)}
              >
                <div className="ct-row-icon" style={{ '--accent': 'var(--primary-color)' }}><FiFileText /></div>
                <div className="ct-row-main">
                  <span className="ct-row-title">{monthLabel(r.monthKey)}</span>
                  <span className="ct-row-sub">
                    {r.totalIncidents} {plural(r.totalIncidents, 'incident', 'incidents')}
                  </span>
                </div>
                <div className="ct-mini-sev">
                  <span style={{ '--c': SEVERITY_COLOR.WARNING }}>{r.warningCount}</span>
                  <span style={{ '--c': SEVERITY_COLOR.DANGER }}>{r.dangerCount}</span>
                  <span style={{ '--c': SEVERITY_COLOR.EMERGENCY }}>{r.emergencyCount}</span>
                </div>
                <Badge
                  text={`${r.safetyPercentage}% safe`}
                  color={healthy ? STATUS_COLOR.NORMAL : STATUS_COLOR.WARNING}
                />
                <FiChevronRight className="ct-row-chevron" />
              </div>
            );
          })}
        </div>
      )}
    </>
  );
};

// ------------------------------------------------------------- report detail

const escapeHtml = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));

/** Web stand-in for PdfExporter.monthlyReport — opens a print-ready page (Save as PDF). */
const exportReportPdf = (report, incidents, nameById, contractorName) => {
  const rows = incidents.map((i) => `
    <tr>
      <td>${escapeHtml(formatDateTime(i.createdAt))}</td>
      <td>${escapeHtml(ALERT_TYPE_LABEL[i.type] || i.type)}</td>
      <td>${escapeHtml(SEVERITY_LABEL[i.severity] || i.severity)}</td>
      <td>${escapeHtml(nameById[i.workerId] || i.workerId)}</td>
      <td>${escapeHtml(i.outcome || 'Open')}</td>
    </tr>`).join('');

  const html = `<!doctype html><html><head><meta charset="utf-8" />
    <title>BeVest Safety Report – ${escapeHtml(monthLabel(report.monthKey))}</title>
    <style>
      body{font-family:Inter,Arial,sans-serif;color:#0f172a;margin:40px;}
      h1{margin:0 0 4px;font-size:22px} .muted{color:#64748b;font-size:13px}
      .brand{color:#ea580c;font-weight:700;letter-spacing:.08em;font-size:12px}
      .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:24px 0}
      .tile{border:1px solid #e2e8f0;border-radius:10px;padding:14px}
      .tile b{display:block;font-size:22px;margin-bottom:4px}
      table{width:100%;border-collapse:collapse;font-size:12px;margin-top:12px}
      th,td{border-bottom:1px solid #e2e8f0;padding:8px;text-align:left}
      th{background:#f8fafc;text-transform:uppercase;font-size:10px;letter-spacing:.05em;color:#64748b}
    </style></head><body>
    <div class="brand">BEVEST · MONTHLY SAFETY REPORT</div>
    <h1>${escapeHtml(monthLabel(report.monthKey))}</h1>
    <div class="muted">${escapeHtml(contractorName)} · Generated ${escapeHtml(formatDateTime(Date.now()))}</div>
    <div class="grid">
      <div class="tile"><b>${report.safetyPercentage}%</b><span class="muted">Safety score</span></div>
      <div class="tile"><b>${report.activeWorkers}</b><span class="muted">Active workers</span></div>
      <div class="tile"><b>${report.totalIncidents}</b><span class="muted">Incidents</span></div>
      <div class="tile"><b>${report.warningCount} / ${report.dangerCount} / ${report.emergencyCount}</b><span class="muted">Warning / Danger / Emergency</span></div>
    </div>
    <h3>Incidents this month</h3>
    ${incidents.length ? `<table><thead><tr><th>Date</th><th>Type</th><th>Severity</th><th>Worker</th><th>Outcome</th></tr></thead><tbody>${rows}</tbody></table>`
    : '<p class="muted">Nothing was recorded in this period — a good month.</p>'}
    <script>window.onload=function(){window.print();}</script>
    </body></html>`;

  const w = window.open('', '_blank');
  if (!w) {
    alert('Please allow pop-ups to export the PDF.');
    return;
  }
  w.document.open();
  w.document.write(html);
  w.document.close();
};

/** Port of MonthlyReportDetailScreen. */
export const MonthlyReportDetail = ({ data, monthKey, contractorName, onBack }) => {
  const { loading, error, workers, incidents } = data;
  const nameById = useNameById(workers);

  const report = useMemo(
    () => buildMonthlyReports(incidents, workers.filter((w) => w.active).length, 12)
      .find((r) => r.monthKey === monthKey),
    [incidents, workers, monthKey],
  );
  const monthIncidents = useMemo(
    () => incidents.filter((i) => toMonthKey(i.createdAt) === monthKey).sort((a, b) => a.createdAt - b.createdAt),
    [incidents, monthKey],
  );

  const header = (
    <PageHeader
      breadcrumb={[{ label: 'Reports', onClick: onBack }, { label: 'Monthly report' }]}
      title={monthLabel(monthKey)}
      subtitle="Monthly report"
      actions={report && (
        <button
          id="ct-export-pdf"
          className="btn-primary"
          onClick={() => exportReportPdf(report, monthIncidents, nameById, contractorName)}
        >
          <FiDownload /> Export PDF
        </button>
      )}
    />
  );

  if (loading) return (<>{header}<Skeleton tiles={2} rows={3} /></>);
  if (error) return (<>{header}<ErrorState message={error} /></>);
  if (!report) {
    return (
      <>
        {header}
        <EmptyState icon={FiFileText} title="No report for this month" message="There isn't enough monitoring data for this period yet." />
      </>
    );
  }

  return (
    <>
      {header}
      <div className="ct-stat-grid two">
        <StatTile
          icon={FiShield}
          value={`${report.safetyPercentage}%`}
          label="Safety score"
          accent={report.safetyPercentage >= 90 ? STATUS_COLOR.NORMAL : STATUS_COLOR.WARNING}
        />
        <StatTile icon={FiUsers} value={report.activeWorkers} label="Active workers" />
      </div>

      <SectionHeader title="Severity breakdown" supporting={`${report.totalIncidents} incidents in total`} />
      <div className="ct-card">
        <StatusBar
          slices={[
            { label: 'Warning', count: report.warningCount, color: SEVERITY_COLOR.WARNING },
            { label: 'Danger', count: report.dangerCount, color: SEVERITY_COLOR.DANGER },
            { label: 'Emergency', count: report.emergencyCount, color: SEVERITY_COLOR.EMERGENCY },
          ]}
        />
      </div>

      <SectionHeader title="Incidents this month" supporting="Oldest first" />
      {monthIncidents.length === 0 ? (
        <EmptyState icon={FiCheckCircle} title="No incidents" message="Nothing was recorded in this period — a good month." />
      ) : (
        <div className="ct-list">
          {monthIncidents.map((i) => <IncidentRow key={i.id} incident={i} workerName={nameById[i.workerId]} />)}
        </div>
      )}
    </>
  );
};

// ---------------------------------------------------------- incident history

const DetailItem = ({ label, value }) => (
  <div className="ct-detail-item">
    <span>{label}</span>
    <strong>{value ?? '—'}</strong>
  </div>
);

const IncidentDetailModal = ({ incident, workerName, onClose }) => {
  const color = SEVERITY_COLOR[incident.severity];
  const Icon = ALERT_TYPE_ICON[incident.type] || FiClock;
  const hasLocation = incident.latitude != null && incident.longitude != null;
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content ct-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="ct-modal-title">
            <div className="ct-row-icon lg" style={{ '--accent': color }}><Icon /></div>
            <div>
              <h2>{ALERT_TYPE_LABEL[incident.type] || incident.type}</h2>
              <span className="ct-row-sub">{formatDateTime(incident.createdAt)}</span>
            </div>
          </div>
          <button id="ct-close-incident" className="close-btn" onClick={onClose} aria-label="Close"><FiX /></button>
        </div>
        <div className="modal-body ct-modal-body">
          <div className="ct-status-banner" style={{ '--accent': color }}>
            <Badge text={SEVERITY_LABEL[incident.severity]} color={color} />
            <p>{ALERT_TYPE_DETAIL[incident.type]}</p>
          </div>
          <div className="ct-detail-grid">
            <DetailItem label="Worker" value={workerName || incident.workerId} />
            <DetailItem label="Vest" value={incident.vestId || '—'} />
            <DetailItem label="Heart rate" value={incident.heartRate != null ? `${incident.heartRate} bpm` : '—'} />
            <DetailItem label="Temperature" value={incident.temperature != null ? `${incident.temperature.toFixed(1)} °C` : '—'} />
            <DetailItem label="Status" value={incident.outcome ? 'Closed' : 'Open'} />
            <DetailItem label="Site" value={incident.siteId || '—'} />
          </div>
          <div className="ct-card ct-card-tight">
            <h3 className="ct-subheading">Outcome</h3>
            <p className="ct-muted">{incident.outcome || 'This incident has not been closed yet.'}</p>
            {incident.resolutionNotes && (
              <>
                <h3 className="ct-subheading" style={{ marginTop: '1rem' }}>Resolution notes</h3>
                <p className="ct-muted">{incident.resolutionNotes}</p>
              </>
            )}
          </div>
          {hasLocation && (
            <a
              className="ct-wide-btn"
              href={`https://www.google.com/maps?q=${incident.latitude},${incident.longitude}`}
              target="_blank"
              rel="noreferrer"
            >
              Open location in Maps
            </a>
          )}
        </div>
      </div>
    </div>
  );
};

/** Port of ContractorIncidentsScreen. */
export const IncidentHistory = ({ data, onBack }) => {
  const { loading, error, workers, incidents } = data;
  const nameById = useNameById(workers);
  const [severity, setSeverity] = useState('ALL');
  const [openId, setOpenId] = useState(null);

  const visible = incidents.filter((i) => severity === 'ALL' || i.severity === severity);
  const open = incidents.find((i) => i.id === openId);

  const header = (
    <PageHeader
      breadcrumb={[{ label: 'Reports', onClick: onBack }, { label: 'Incident history' }]}
      title="Incident history"
      subtitle="Every recorded incident across your sites, newest first."
    />
  );

  if (loading) return (<>{header}<Skeleton rows={6} /></>);
  if (error) return (<>{header}<ErrorState message={error} /></>);

  return (
    <>
      {header}
      <FilterChips
        options={[
          { key: 'ALL', label: 'All', count: incidents.length },
          ...['WARNING', 'DANGER', 'EMERGENCY'].map((s) => ({
            key: s,
            label: SEVERITY_LABEL[s],
            count: incidents.filter((i) => i.severity === s).length,
            accent: SEVERITY_COLOR[s],
          })),
        ]}
        selected={severity}
        onSelect={setSeverity}
      />
      {visible.length === 0 ? (
        <EmptyState
          icon={FiClock}
          title={incidents.length === 0 ? 'No incidents' : 'Nothing at this severity'}
          message={incidents.length === 0
            ? 'Incidents appear here once an alert is closed with an outcome.'
            : 'No incident on record has this severity.'}
        />
      ) : (
        <div className="ct-list">
          {visible.map((i) => (
            <IncidentRow key={i.id} incident={i} workerName={nameById[i.workerId]} onClick={() => setOpenId(i.id)} />
          ))}
        </div>
      )}
      {open && <IncidentDetailModal incident={open} workerName={nameById[open.workerId]} onClose={() => setOpenId(null)} />}
    </>
  );
};

export default ContractorReports;
