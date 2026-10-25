/**
 * Contractor data layer for the web.
 *
 * Mirrors the mobile app's contractor ViewModels (ContractorViewModels.kt),
 * SafetyStatusEngine.kt and ReportRepositoryImpl.kt so a contractor sees the same
 * numbers on the web as on their phone.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  collection, query, where, orderBy, limit, onSnapshot, doc,
} from 'firebase/firestore';
import { ref, onValue } from 'firebase/database';
import { db, rtdb } from '../firebase';

// ------------------------------------------------------------------ constants

export const DEFAULT_THRESHOLDS = {
  heartRateHigh: 100,
  temperatureHigh: 38.0,
  offlineTimeoutSeconds: 60,
};

export const STATUS_COLOR = {
  NORMAL: '#16a34a',
  WARNING: '#d97706',
  DANGER: '#dc2626',
  EMERGENCY: '#9f1239',
  OFFLINE: '#94a3b8',
};

export const SEVERITY_COLOR = {
  WARNING: STATUS_COLOR.WARNING,
  DANGER: STATUS_COLOR.DANGER,
  EMERGENCY: STATUS_COLOR.EMERGENCY,
};

// --------------------------------------------------------------------- labels
// Same user-facing copy as ui/common/Labels.kt

export const STATUS_LABEL = {
  NORMAL: 'Normal', WARNING: 'Warning', DANGER: 'Danger', EMERGENCY: 'Emergency', OFFLINE: 'Offline',
};

export const STATUS_DETAIL = {
  NORMAL: 'Readings are within safe limits.',
  WARNING: 'A reading has crossed its warning threshold.',
  DANGER: 'A reading is at a dangerous level. Check on this worker.',
  EMERGENCY: 'Help has been requested or a fall went unanswered.',
  OFFLINE: 'The vest has stopped reporting. Last known data shown.',
};

export const ALERT_TYPE_LABEL = {
  HEART_RATE_WARNING: 'High heart rate',
  TEMPERATURE_WARNING: 'High body temperature',
  FALL_DETECTED: 'Fall detected',
  INACTIVITY: 'No movement',
  EMERGENCY_REQUEST: 'Help requested',
  NO_SAFETY_RESPONSE: 'No response to check',
  VEST_OFFLINE: 'Vest offline',
  LOW_BATTERY: 'Low vest battery',
};

export const ALERT_TYPE_DETAIL = {
  HEART_RATE_WARNING: 'Heart rate stayed above the safe range.',
  TEMPERATURE_WARNING: 'Body temperature stayed above the safe range.',
  FALL_DETECTED: "The vest's motion sensor registered a fall.",
  INACTIVITY: 'No movement for longer than the inactivity limit.',
  EMERGENCY_REQUEST: 'The worker pressed the safety button on their vest.',
  NO_SAFETY_RESPONSE: 'The worker did not acknowledge a safety check.',
  VEST_OFFLINE: 'The vest stopped sending readings.',
  LOW_BATTERY: 'The vest battery needs charging to keep monitoring.',
};

export const SEVERITY_LABEL = { WARNING: 'Warning', DANGER: 'Danger', EMERGENCY: 'Emergency' };

export const MOTION_LABEL = {
  MOVING: 'Moving', STATIONARY: 'Stationary', INACTIVE: 'Inactive',
  FALL_DETECTED: 'Fall detected', UNKNOWN: 'No data',
};

export const RESPONSE_LABEL = {
  NONE: 'No check running',
  WAITING: 'Waiting for worker',
  ACKNOWLEDGED: 'Worker responded',
  EMERGENCY_REQUESTED: 'Help requested',
  NO_RESPONSE: 'No response',
  ESCALATED: 'Escalated',
};

// -------------------------------------------------------------------- helpers

const upper = (v, fallback) => {
  const s = String(v ?? '').trim().toUpperCase();
  return s || fallback;
};

const num = (v) => {
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};

export const toMillis = (v) => {
  if (v == null) return 0;
  if (typeof v === 'number') return v;
  if (typeof v.toMillis === 'function') return v.toMillis();
  if (v instanceof Date) return v.getTime();
  if (typeof v === 'string') {
    const t = Date.parse(v);
    return Number.isNaN(t) ? 0 : t;
  }
  if (typeof v.seconds === 'number') return v.seconds * 1000;
  return 0;
};

export const monthKey = (ms) => {
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
};

export const monthLabel = (key) => {
  const [y, m] = key.split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
};

export const formatDateTime = (ms) => {
  if (!ms) return '—';
  return new Date(ms).toLocaleString(undefined, {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
};

export const timeAgo = (ms, now = Date.now()) => {
  if (!ms) return 'Never';
  const s = Math.max(0, Math.round((now - ms) / 1000));
  if (s < 60) return `${s}s ago`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.round(h / 24)}d ago`;
};

export const initialsOf = (name, fallback = 'U') => {
  const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
  const s = parts.slice(0, 2).map((p) => p[0]).join('').toUpperCase();
  return s || fallback;
};

export const plural = (n, one, many) => (n === 1 ? one : many);

// -------------------------------------------------------------------- mappers

export const toWorker = (snap) => {
  const d = snap.data();
  const firstName = d.firstName || '';
  const lastName = d.lastName || '';
  return {
    id: snap.id,
    firstName,
    lastName,
    fullName: `${firstName} ${lastName}`.trim() || snap.id,
    phone: d.phone || '',
    photoUrl: d.photoUrl || null,
    siteId: d.siteId || null,
    assignedVestId: d.assignedVestId || null,
    active: d.active !== false,
  };
};

export const toIncident = (snap) => {
  const d = snap.data();
  return {
    id: snap.id,
    workerId: d.workerId || '',
    vestId: d.vestId || '',
    alertId: d.alertId || null,
    siteId: d.siteId || null,
    type: upper(d.type, 'VEST_OFFLINE'),
    severity: upper(d.severity, 'WARNING'),
    heartRate: num(d.heartRate),
    temperature: num(d.temperature),
    latitude: num(d.latitude),
    longitude: num(d.longitude),
    createdAt: toMillis(d.createdAt),
    outcome: d.outcome || null,
    resolutionNotes: d.resolutionNotes || null,
    resolvedBy: d.resolvedBy || null,
  };
};

export const toUser = (snap) => {
  const d = snap.data();
  const firstName = d.firstName || '';
  const lastName = d.lastName || '';
  return {
    uid: snap.id,
    firstName,
    lastName,
    fullName: `${firstName} ${lastName}`.trim(),
    email: d.email || '',
    phone: d.phone || '',
    siteId: d.siteId || null,
    active: d.active !== false,
  };
};

const toReading = (val) => {
  if (!val) return null;
  return {
    vestId: val.vestId || '',
    heartRate: num(val.heartRate),
    temperature: num(val.temperature),
    motionState: upper(val.motionState, 'UNKNOWN'),
    fallDetected: val.fallDetected === true,
    latitude: num(val.latitude),
    longitude: num(val.longitude),
    battery: num(val.battery),
    safetyResponse: upper(val.safetyResponse, 'NONE'),
    timestamp: num(val.timestamp) || 0,
  };
};

// ---------------------------------------------------------- safety evaluation
// Port of domain/safety/SafetyStatusEngine.kt

const EMERGENCY_RESPONSES = ['EMERGENCY_REQUESTED', 'NO_RESPONSE', 'ESCALATED'];

export function isStale(reading, t = DEFAULT_THRESHOLDS, now = Date.now()) {
  if (!reading || reading.timestamp <= 0) return true;
  return (now - reading.timestamp) / 1000 > t.offlineTimeoutSeconds;
}

export function evaluateStatus(reading, t = DEFAULT_THRESHOLDS, now = Date.now()) {
  if (!reading) return 'OFFLINE';
  if (isStale(reading, t, now)) return 'OFFLINE';
  if (EMERGENCY_RESPONSES.includes(reading.safetyResponse)) return 'EMERGENCY';
  if (reading.fallDetected || reading.motionState === 'FALL_DETECTED') return 'DANGER';
  const hrHigh = reading.heartRate != null && reading.heartRate > t.heartRateHigh;
  const tempHigh = reading.temperature != null && reading.temperature > t.temperatureHigh;
  if (hrHigh || tempHigh || reading.motionState === 'INACTIVE') return 'WARNING';
  return 'NORMAL';
}

// ------------------------------------------------------------ monthly reports
// Port of ReportRepositoryImpl.monthlyReports — newest month first.

export function buildMonthlyReports(incidents, activeWorkers, months = 6) {
  const byMonth = {};
  incidents.forEach((i) => {
    const k = monthKey(i.createdAt);
    (byMonth[k] = byMonth[k] || []).push(i);
  });
  const now = new Date();
  const reports = [];
  for (let offset = 0; offset < months; offset++) {
    const key = monthKey(new Date(now.getFullYear(), now.getMonth() - offset, 1).getTime());
    const list = byMonth[key] || [];
    const ratio = activeWorkers === 0 ? 0 : Math.floor((list.length * 100) / (activeWorkers * 30));
    const safety = activeWorkers === 0 ? 100 : 100 - Math.min(100, Math.max(0, ratio));
    reports.push({
      monthKey: key,
      totalIncidents: list.length,
      safetyPercentage: safety,
      activeWorkers,
      warningCount: list.filter((i) => i.severity === 'WARNING').length,
      dangerCount: list.filter((i) => i.severity === 'DANGER').length,
      emergencyCount: list.filter((i) => i.severity === 'EMERGENCY').length,
    });
  }
  return reports;
}

// ---------------------------------------------------------------------- hooks

/**
 * Live contractor data: workers, their vest readings, incidents and thresholds.
 * Equivalent to ContractorDashboardViewModel + WorkerDirectoryViewModel combined.
 */
export function useContractorData(contractorId) {
  const [workers, setWorkers] = useState([]);
  const [workersLoading, setWorkersLoading] = useState(true);
  const [workersError, setWorkersError] = useState(null);

  const [incidents, setIncidents] = useState([]);
  const [incidentsLoading, setIncidentsLoading] = useState(true);
  const [incidentsError, setIncidentsError] = useState(null);

  const [thresholds, setThresholds] = useState(DEFAULT_THRESHOLDS);
  const [readings, setReadings] = useState({});
  const [now, setNow] = useState(Date.now());

  // Re-evaluate staleness periodically, so a silent vest flips to Offline.
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 10_000);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    if (!contractorId) return undefined;
    const q = query(collection(db, 'workers'), where('contractorId', '==', contractorId));
    return onSnapshot(
      q,
      (qs) => {
        setWorkers(qs.docs.map(toWorker));
        setWorkersLoading(false);
        setWorkersError(null);
      },
      (err) => {
        console.error('Workers listener failed:', err);
        setWorkersError(err.message);
        setWorkersLoading(false);
      },
    );
  }, [contractorId]);

  useEffect(() => {
    if (!contractorId) return undefined;
    const q = query(
      collection(db, 'incidents'),
      where('contractorId', '==', contractorId),
      orderBy('createdAt', 'desc'),
      limit(500),
    );
    return onSnapshot(
      q,
      (qs) => {
        setIncidents(qs.docs.map(toIncident).sort((a, b) => b.createdAt - a.createdAt));
        setIncidentsLoading(false);
        setIncidentsError(null);
      },
      (err) => {
        console.error('Incidents listener failed:', err);
        setIncidentsError(err.message);
        setIncidentsLoading(false);
      },
    );
  }, [contractorId]);

  useEffect(() => onSnapshot(
    doc(db, 'settings', 'thresholds'),
    (snap) => {
      if (!snap.exists()) return setThresholds(DEFAULT_THRESHOLDS);
      const d = snap.data();
      setThresholds({
        heartRateHigh: num(d.heartRateHigh) ?? DEFAULT_THRESHOLDS.heartRateHigh,
        temperatureHigh: num(d.temperatureHigh) ?? DEFAULT_THRESHOLDS.temperatureHigh,
        offlineTimeoutSeconds: num(d.offlineTimeoutSeconds) ?? DEFAULT_THRESHOLDS.offlineTimeoutSeconds,
      });
      return undefined;
    },
    () => setThresholds(DEFAULT_THRESHOLDS),
  ), []);

  // One realtime listener per worker, mirroring MonitoringRepository.observeReadings.
  const workerIdsKey = workers.map((w) => w.id).sort().join('|');
  useEffect(() => {
    const ids = workerIdsKey ? workerIdsKey.split('|') : [];
    const unsubs = ids.map((id) => onValue(
      ref(rtdb, `liveReadings/${id}`),
      (snap) => setReadings((prev) => ({ ...prev, [id]: toReading(snap.val()) })),
      () => setReadings((prev) => ({ ...prev, [id]: null })),
    ));
    return () => unsubs.forEach((u) => u());
  }, [workerIdsKey]);

  const liveWorkers = useMemo(() => workers
    .map((w) => {
      const reading = readings[w.id] || null;
      return {
        worker: w,
        reading,
        status: evaluateStatus(reading, thresholds, now),
        stale: isStale(reading, thresholds, now),
      };
    })
    .sort((a, b) => a.worker.fullName.localeCompare(b.worker.fullName)), [workers, readings, thresholds, now]);

  return {
    loading: workersLoading || incidentsLoading,
    error: workersError || incidentsError,
    workers,
    liveWorkers,
    incidents,
    thresholds,
    now,
  };
}

/** Site Safety Officers belonging to this contractor (SsoListViewModel). */
export function useContractorSsos(contractorId) {
  const [state, setState] = useState({ loading: true, error: null, ssos: [] });
  useEffect(() => {
    if (!contractorId) return undefined;
    // `limit` is required by firestore.rules for contractor list queries.
    const q = query(
      collection(db, 'users'),
      where('role', '==', 'SSO'),
      where('contractorId', '==', contractorId),
      limit(200),
    );
    return onSnapshot(
      q,
      (qs) => setState({
        loading: false,
        error: null,
        ssos: qs.docs.map(toUser).sort((a, b) => (a.fullName || a.email).localeCompare(b.fullName || b.email)),
      }),
      (err) => setState({ loading: false, error: err.message, ssos: [] }),
    );
  }, [contractorId]);
  return state;
}
