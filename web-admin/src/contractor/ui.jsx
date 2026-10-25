/**
 * Web equivalents of the mobile app's shared Compose components
 * (StatTile, InsightCard, StatusBar, TrendBars, SectionHeader, EmptyState,
 * StatusBadge, FilterRow, SearchBar, IncidentRow).
 */
import React from 'react';
import { FiSearch, FiChevronRight, FiInbox } from 'react-icons/fi';
import {
  TbHeartbeat, TbTemperature, TbFall, TbZzz, TbSos, TbBellOff, TbWifiOff, TbBatteryOff,
} from 'react-icons/tb';
import {
  ALERT_TYPE_LABEL, SEVERITY_COLOR, STATUS_COLOR, formatDateTime,
} from './contractorData';

export const ALERT_TYPE_ICON = {
  HEART_RATE_WARNING: TbHeartbeat,
  TEMPERATURE_WARNING: TbTemperature,
  FALL_DETECTED: TbFall,
  INACTIVITY: TbZzz,
  EMERGENCY_REQUEST: TbSos,
  NO_SAFETY_RESPONSE: TbBellOff,
  VEST_OFFLINE: TbWifiOff,
  LOW_BATTERY: TbBatteryOff,
};

export const PageHeader = ({ title, subtitle, actions, breadcrumb }) => (
  <div className="ct-page-header">
    {breadcrumb && (
      <div className="breadcrumb">
        {breadcrumb.map((b, i) => (
          <React.Fragment key={b.label}>
            {i > 0 && <span className="bc-separator">&gt;</span>}
            {b.onClick
              ? <span className="bc-link" onClick={b.onClick}>{b.label}</span>
              : <span className="bc-current">{b.label}</span>}
          </React.Fragment>
        ))}
      </div>
    )}
    <div className="ct-page-header-row">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div className="ct-page-actions">{actions}</div>}
    </div>
  </div>
);

export const SectionHeader = ({ title, supporting, trailing }) => (
  <div className="ct-section-header">
    <div>
      <h2>{title}</h2>
      {supporting && <p>{supporting}</p>}
    </div>
    {trailing}
  </div>
);

export const StatTile = ({ icon: Icon, value, label, accent, onClick, id }) => (
  <div
    id={id}
    className={`ct-stat-tile ${onClick ? 'clickable' : ''}`}
    style={{ '--accent': accent || 'var(--primary-color)' }}
    onClick={onClick}
    role={onClick ? 'button' : undefined}
    tabIndex={onClick ? 0 : undefined}
    onKeyDown={onClick ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(); } } : undefined}
  >
    <div className="ct-stat-icon"><Icon /></div>
    <div className="ct-stat-body">
      <div className={`ct-stat-value ${accent ? 'accented' : ''}`}>{value}</div>
      <div className="ct-stat-label">{label}</div>
    </div>
  </div>
);

export const Insight = ({ icon: Icon, text, accent }) => (
  <div className="ct-insight" style={{ '--accent': accent }}>
    <div className="ct-insight-icon"><Icon /></div>
    <p>{text}</p>
  </div>
);

export const StatusBar = ({ slices }) => {
  const total = slices.reduce((s, x) => s + x.count, 0);
  return (
    <div className="ct-status-bar-wrap">
      <div className="ct-status-bar">
        {total === 0
          ? <div className="ct-status-seg empty" style={{ width: '100%' }} />
          : slices.filter((s) => s.count > 0).map((s) => (
            <div
              key={s.label}
              className="ct-status-seg"
              title={`${s.label}: ${s.count}`}
              style={{ width: `${(s.count / total) * 100}%`, background: s.color }}
            />
          ))}
      </div>
      <div className="ct-legend">
        {slices.map((s) => (
          <div key={s.label} className="ct-legend-item">
            <span className="ct-legend-dot" style={{ background: s.color }} />
            <span className="ct-legend-label">{s.label}</span>
            <span className="ct-legend-count">{s.count}</span>
          </div>
        ))}
      </div>
    </div>
  );
};

export const TrendBars = ({ data }) => {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div className="ct-trend">
      {data.map((d, i) => (
        <div key={d.label + i} className="ct-trend-col">
          <span className="ct-trend-value">{d.value}</span>
          <div className="ct-trend-track">
            <div
              className={`ct-trend-bar ${i === data.length - 1 ? 'current' : ''}`}
              style={{ height: `${Math.max(4, (d.value / max) * 100)}%` }}
            />
          </div>
          <span className="ct-trend-label">{d.label}</span>
        </div>
      ))}
    </div>
  );
};

export const Badge = ({ text, color }) => (
  <span className="ct-badge" style={{ '--badge': color }}>
    <span className="ct-badge-dot" />
    {text}
  </span>
);

export const EmptyState = ({ icon: Icon = FiInbox, title, message, action }) => (
  <div className="ct-empty">
    <div className="ct-empty-icon"><Icon /></div>
    <h3>{title}</h3>
    {message && <p>{message}</p>}
    {action}
  </div>
);

export const ErrorState = ({ message }) => (
  <div className="error-message" style={{ textAlign: 'left' }}>
    Something went wrong: {message}
  </div>
);

export const SearchBox = ({ value, onChange, placeholder, id }) => (
  <div className="ct-search">
    <FiSearch />
    <input id={id} type="text" value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
  </div>
);

export const FilterChips = ({ options, selected, onSelect }) => (
  <div className="ct-chips" role="tablist">
    {options.map((o) => (
      <button
        key={o.key}
        id={`filter-${o.key.toLowerCase()}`}
        type="button"
        className={`ct-chip ${selected === o.key ? 'selected' : ''}`}
        style={o.accent ? { '--chip': o.accent } : undefined}
        onClick={() => onSelect(o.key)}
      >
        {o.accent && <span className="ct-chip-dot" />}
        {o.label}
        <span className="ct-chip-count">{o.count}</span>
      </button>
    ))}
  </div>
);

export const IncidentRow = ({ incident, onClick, workerName }) => {
  const color = SEVERITY_COLOR[incident.severity] || STATUS_COLOR.WARNING;
  const Icon = ALERT_TYPE_ICON[incident.type] || TbWifiOff;
  const open = !incident.outcome;
  return (
    <div
      className={`ct-row ${onClick ? 'clickable' : ''}`}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      <div className="ct-row-icon" style={{ '--accent': color }}><Icon /></div>
      <div className="ct-row-main">
        <span className="ct-row-title">{ALERT_TYPE_LABEL[incident.type] || incident.type}</span>
        <span className="ct-row-sub">{workerName || `Worker ${incident.workerId}`}</span>
        <span className="ct-row-meta">{formatDateTime(incident.createdAt)}</span>
      </div>
      <Badge text={open ? 'Open' : 'Closed'} color={open ? STATUS_COLOR.WARNING : STATUS_COLOR.NORMAL} />
      {onClick && <FiChevronRight className="ct-row-chevron" />}
    </div>
  );
};

export const Skeleton = ({ rows = 4, tiles = 0 }) => (
  <div className="ct-skeleton-wrap">
    {tiles > 0 && (
      <div className="ct-stat-grid">
        {Array.from({ length: tiles }).map((_, i) => <div key={i} className="ct-skeleton tile" />)}
      </div>
    )}
    {Array.from({ length: rows }).map((_, i) => <div key={i} className="ct-skeleton row" />)}
  </div>
);
