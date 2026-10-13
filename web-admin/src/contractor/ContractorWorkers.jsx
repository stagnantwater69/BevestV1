import React, { useMemo, useState } from 'react';
import { FiX, FiMapPin, FiChevronRight, FiUsers, FiSearch } from 'react-icons/fi';
import {
  TbHeartbeat, TbTemperature, TbRun, TbBattery2, TbBellRinging, TbDeviceWatch, TbBuildingFactory2, TbClockHour4,
} from 'react-icons/tb';
import {
  STATUS_COLOR, STATUS_LABEL, STATUS_DETAIL, MOTION_LABEL, RESPONSE_LABEL,
  timeAgo, initialsOf,
} from './contractorData';
import {
  PageHeader, SearchBox, FilterChips, Badge, EmptyState, ErrorState, Skeleton, IncidentRow,
} from './ui';

// WorkerFilter from WorkerDirectoryViewModel.kt, labelled as in WorkerDirectoryScreen.kt
const FILTERS = [
  { key: 'ALL', label: 'All', test: () => true },
  { key: 'ACTIVE', label: 'On site', test: (l) => l.status !== 'OFFLINE' },
  { key: 'WARNING', label: 'Warning', test: (l) => l.status === 'WARNING', accent: STATUS_COLOR.WARNING },
  { key: 'DANGER', label: 'Danger', test: (l) => l.status === 'DANGER' || l.status === 'EMERGENCY', accent: STATUS_COLOR.DANGER },
  { key: 'OFFLINE', label: 'Offline', test: (l) => l.status === 'OFFLINE', accent: STATUS_COLOR.OFFLINE },
  { key: 'UNASSIGNED', label: 'No vest', test: (l) => !l.worker.assignedVestId },
];

const WorkerRow = ({ live, onClick }) => {
  const { worker, reading, status } = live;
  const color = STATUS_COLOR[status];
  return (
    <div className="ct-row clickable" onClick={onClick} role="button" tabIndex={0} id={`ct-worker-${worker.id}`}>
      <div className="ct-avatar" style={{ '--accent': color }}>
        {worker.photoUrl ? <img src={worker.photoUrl} alt="" /> : initialsOf(worker.fullName, 'W')}
        <span className="ct-avatar-status" />
      </div>
      <div className="ct-row-main">
        <span className="ct-row-title">{worker.fullName}</span>
        <span className="ct-row-sub">
          {worker.id}{worker.siteId ? ` · Site ${worker.siteId}` : ''}
        </span>
        <span className="ct-row-meta">
          {worker.assignedVestId ? `Vest ${worker.assignedVestId}` : 'No vest assigned'}
        </span>
      </div>
      <div className="ct-vitals-inline">
        <span title="Heart rate"><TbHeartbeat /> {reading?.heartRate ?? '—'}</span>
        <span title="Temperature"><TbTemperature /> {reading?.temperature != null ? reading.temperature.toFixed(1) : '—'}</span>
      </div>
      <Badge text={STATUS_LABEL[status]} color={color} />
      <FiChevronRight className="ct-row-chevron" />
    </div>
  );
};

const Vital = ({ icon: Icon, label, value, unit, alert }) => (
  <div className={`ct-vital ${alert ? 'alert' : ''}`}>
    <div className="ct-vital-icon"><Icon /></div>
    <div>
      <div className="ct-vital-label">{label}</div>
      <div className="ct-vital-value">
        {value}
        {unit && value !== '—' && <small>{unit}</small>}
      </div>
    </div>
  </div>
);

/** Read-only version of the mobile WorkerDetailScreen (canControl = false for contractors). */
const WorkerDetailModal = ({ live, incidents, thresholds, now, onClose }) => {
  const { worker, reading, status } = live;
  const color = STATUS_COLOR[status];
  const workerIncidents = incidents.filter((i) => i.workerId === worker.id).slice(0, 5);
  const hasLocation = reading?.latitude != null && reading?.longitude != null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content ct-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="ct-modal-title">
            <div className="ct-avatar lg" style={{ '--accent': color }}>
              {worker.photoUrl ? <img src={worker.photoUrl} alt="" /> : initialsOf(worker.fullName, 'W')}
              <span className="ct-avatar-status" />
            </div>
            <div>
              <h2>{worker.fullName}</h2>
              <span className="ct-row-sub">Worker {worker.id}</span>
            </div>
          </div>
          <button id="ct-close-worker" className="close-btn" onClick={onClose} aria-label="Close"><FiX /></button>
        </div>
        <div className="modal-body ct-modal-body">
          <div className="ct-status-banner" style={{ '--accent': color }}>
            <Badge text={STATUS_LABEL[status]} color={color} />
            <p>{STATUS_DETAIL[status]}</p>
          </div>

          <div className="ct-vitals-grid">
            <Vital
              icon={TbHeartbeat}
              label="Heart rate"
              value={reading?.heartRate ?? '—'}
              unit="bpm"
              alert={reading?.heartRate != null && reading.heartRate > thresholds.heartRateHigh}
            />
            <Vital
              icon={TbTemperature}
              label="Body temperature"
              value={reading?.temperature != null ? reading.temperature.toFixed(1) : '—'}
              unit="°C"
              alert={reading?.temperature != null && reading.temperature > thresholds.temperatureHigh}
            />
            <Vital
              icon={TbRun}
              label="Motion"
              value={MOTION_LABEL[reading?.motionState] || 'No data'}
              alert={reading?.fallDetected || reading?.motionState === 'FALL_DETECTED' || reading?.motionState === 'INACTIVE'}
            />
            <Vital
              icon={TbBattery2}
              label="Vest battery"
              value={reading?.battery ?? '—'}
              unit="%"
              alert={reading?.battery != null && reading.battery < 20}
            />
            <Vital
              icon={TbBellRinging}
              label="Safety check"
              value={RESPONSE_LABEL[reading?.safetyResponse] || 'No check running'}
              alert={['EMERGENCY_REQUESTED', 'NO_RESPONSE', 'ESCALATED'].includes(reading?.safetyResponse)}
            />
            <Vital icon={TbClockHour4} label="Last update" value={reading?.timestamp ? timeAgo(reading.timestamp, now) : 'Never'} />
          </div>

          <div className="profile-list-card">
            <div className="profile-list-item">
              <TbDeviceWatch className="item-icon" />
              <div className="item-details">
                <span className="item-label">Assigned vest</span>
                <span className="item-value">{worker.assignedVestId || 'No vest assigned'}</span>
              </div>
            </div>
            <div className="profile-list-item">
              <TbBuildingFactory2 className="item-icon" />
              <div className="item-details">
                <span className="item-label">Site</span>
                <span className="item-value">{worker.siteId || 'No site assigned'}</span>
              </div>
            </div>
            <div className="profile-list-item">
              <FiMapPin className="item-icon" />
              <div className="item-details">
                <span className="item-label">Last known location</span>
                <span className="item-value">
                  {hasLocation ? `${reading.latitude.toFixed(5)}, ${reading.longitude.toFixed(5)}` : 'No location yet'}
                </span>
              </div>
              {hasLocation && (
                <a
                  className="view-all"
                  href={`https://www.google.com/maps?q=${reading.latitude},${reading.longitude}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Open map
                </a>
              )}
            </div>
          </div>

          <div>
            <h3 className="ct-subheading">Recent incidents</h3>
            {workerIncidents.length === 0 ? (
              <p className="ct-muted">No incidents recorded for this worker.</p>
            ) : (
              <div className="ct-list">
                {workerIncidents.map((i) => <IncidentRow key={i.id} incident={i} workerName={worker.fullName} />)}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

/** Port of ContractorWorkersScreen → WorkerDirectoryScreen (read-only, no "Add worker"). */
const ContractorWorkers = ({ data }) => {
  const { loading, error, liveWorkers, incidents, thresholds, now } = data;
  const [queryText, setQueryText] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [openId, setOpenId] = useState(null);

  const visible = useMemo(() => {
    const f = FILTERS.find((x) => x.key === filter) || FILTERS[0];
    const q = queryText.trim().toLowerCase();
    return liveWorkers
      .filter(f.test)
      .filter((l) => !q || l.worker.fullName.toLowerCase().includes(q) || l.worker.id.toLowerCase().includes(q));
  }, [liveWorkers, filter, queryText]);

  const openLive = liveWorkers.find((l) => l.worker.id === openId);

  const header = <PageHeader title="Workers" subtitle="Everyone wearing a BeVest across your sites, with live status." />;

  if (loading) return (<>{header}<Skeleton rows={5} /></>);
  if (error) return (<>{header}<ErrorState message={error} /></>);

  if (liveWorkers.length === 0) {
    return (
      <>
        {header}
        <EmptyState
          icon={FiUsers}
          title="No workers yet"
          message="No workers have been registered on this site yet."
        />
      </>
    );
  }

  return (
    <>
      {header}
      <div className="ct-toolbar">
        <SearchBox id="ct-worker-search" value={queryText} onChange={setQueryText} placeholder="Search name or worker ID" />
      </div>
      <FilterChips
        options={FILTERS.map((f) => ({ ...f, count: liveWorkers.filter(f.test).length }))}
        selected={filter}
        onSelect={setFilter}
      />

      {visible.length === 0 ? (
        <EmptyState
          icon={queryText ? FiSearch : FiUsers}
          title={queryText ? 'No matches' : 'Nothing in this view'}
          message={queryText
            ? `Nothing matches "${queryText}". Try a different name or ID, or clear the filter.`
            : 'No worker currently has this status.'}
        />
      ) : (
        <div className="ct-list">
          {visible.map((l) => <WorkerRow key={l.worker.id} live={l} onClick={() => setOpenId(l.worker.id)} />)}
        </div>
      )}

      {openLive && (
        <WorkerDetailModal
          live={openLive}
          incidents={incidents}
          thresholds={thresholds}
          now={now}
          onClose={() => setOpenId(null)}
        />
      )}
    </>
  );
};

export default ContractorWorkers;
