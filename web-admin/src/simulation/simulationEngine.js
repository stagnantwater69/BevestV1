/**
 * Web port of SimulationEngine.kt (app/src/main/java/com/jtexpress/bevest/simulation/SimulationEngine.kt).
 *
 * Stands in for the ESP32 firmware and the cloud safety engine while the IoT hardware layer
 * isn't transmitting live yet, so the app can be demoed as if it were fully connected.
 *
 * - Streams realistic evolving sensor readings to Realtime Database (liveReadings/{workerId}).
 * - Writes simulated alerts and incidents to Firestore when a situation escalates.
 * - Seeds 6 months of realistic historical incident data.
 * - Cleans up and resets all simulated data with one click.
 *
 * Singleton design: The live stream keeps running while navigating between pages in the web app.
 */
import {
  collection, doc, getDocs, query, where, writeBatch,
} from 'firebase/firestore';
import { ref, set, remove } from 'firebase/database';
import { db, rtdb, auth } from '../firebase';

const TICK_MS = 3000;

export const SCENARIOS = [
  { key: 'HEALTHY', label: 'Healthy' },
  { key: 'HIGH_HEART_RATE', label: 'High heart rate' },
  { key: 'HIGH_TEMPERATURE', label: 'High temperature' },
  { key: 'FALL', label: 'Fall detected' },
  { key: 'INACTIVITY', label: 'Prolonged inactivity' },
  { key: 'EMERGENCY_REQUEST', label: 'Manual emergency' },
];

export const PHASES = {
  NORMAL: 'NORMAL',
  WARNING: 'WARNING',
  DANGER: 'DANGER',
  EMERGENCY: 'EMERGENCY',
};

// Math helpers directly ported from Kotlin SimulationEngine
const randInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const randFloat = (min, max) => Math.random() * (max - min) + min;
const clamp = (val, min, max) => Math.min(max, Math.max(min, val));
const round1 = (v) => Math.round(v * 10) / 10;
const round5 = (v) => Math.round(v * 100000) / 100000;

const driftInt = (current, mean, min, max, jitter) => {
  const pull = Math.floor((mean - current) / 3);
  return clamp(current + pull + randInt(-jitter, jitter), min, max);
};

const driftFloat = (current, mean, min, max, jitter) => {
  const pull = (mean - current) / 3;
  return clamp(current + pull + randFloat(-jitter, jitter), min, max);
};

const rampInt = (current, target, step) => {
  if (Math.abs(target - current) <= step) return target;
  return current + (target > current ? step : -step);
};

const rampFloat = (current, target, step) => {
  if (Math.abs(target - current) <= step) return target;
  return current + (target > current ? step : -step);
};

const formatTime = () => {
  const d = new Date();
  return d.toTimeString().split(' ')[0];
};

const randomEvent = () => {
  const roll = randInt(0, 99);
  if (roll < 35) return { type: 'HEART_RATE_WARNING', severity: 'WARNING' };
  if (roll < 60) return { type: 'TEMPERATURE_WARNING', severity: 'WARNING' };
  if (roll < 78) return { type: 'INACTIVITY', severity: 'WARNING' };
  if (roll < 92) return { type: 'FALL_DETECTED', severity: 'DANGER' };
  return { type: 'NO_SAFETY_RESPONSE', severity: 'EMERGENCY' };
};

const randomOutcome = () => {
  const outcomes = [
    'Worker safe — false alarm',
    'Worker safe — rested and resumed',
    'Attended by first aider, cleared',
    'Escalated to site medic, resolved',
  ];
  return outcomes[randInt(0, outcomes.length - 1)];
};

class SimulationEngine {
  constructor() {
    this.intervalId = null;
    this.boundKey = null;
    this.boundContractorId = null;
    this.boundSiteId = null;
    this.canStream = true;

    this.sim = new Map(); // workerId -> { worker, lat, lng }
    this.elapsedTicks = new Map();
    this.openAlertId = new Map();
    this.incidentRaised = new Set();
    this.gpsCenter = { lat: 10.3157, lng: 123.8854 };

    this.state = {
      bound: false,
      streaming: false,
      busy: false,
      canStream: true,
      workers: [],
      log: [],
      message: null,
    };

    this.listeners = new Set();
  }

  subscribe(listener) {
    this.listeners.add(listener);
    listener(this.state);
    return () => this.listeners.delete(listener);
  }

  notify() {
    const liveEmergencies = this.state.workers.filter((w) => w.phase === PHASES.EMERGENCY).length;
    const liveWarnings = this.state.workers.filter(
      (w) => w.phase === PHASES.WARNING || w.phase === PHASES.DANGER,
    ).length;

    const snapshot = {
      ...this.state,
      liveEmergencies,
      liveWarnings,
    };
    this.listeners.forEach((l) => l(snapshot));
  }

  updateState(partial) {
    this.state = { ...this.state, ...partial };
    this.notify();
  }

  log(line) {
    const stamped = `${formatTime()}  ${line}`;
    console.log('[BeVestSim]', stamped);
    const newLog = [stamped, ...this.state.log].slice(0, 20);
    this.updateState({ log: newLog });
  }

  async bind({ contractorId = null, siteId = null, canStream = true } = {}) {
    const key = `${contractorId || ''}|${siteId || ''}`;
    if (key === this.boundKey && this.sim.size > 0) {
      this.updateState({ canStream });
      return;
    }

    this.boundKey = key;
    this.boundContractorId = contractorId;
    this.boundSiteId = siteId;
    this.canStream = canStream;
    this.updateState({ busy: true, canStream });

    try {
      let q = collection(db, 'workers');
      if (siteId) {
        q = query(q, where('siteId', '==', siteId));
      } else if (contractorId) {
        q = query(q, where('contractorId', '==', contractorId));
      }

      const snap = await getDocs(q);
      const activeWorkers = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .filter((w) => w.active !== false);

      if (activeWorkers.length === 0) {
        this.updateState({
          busy: false,
          bound: true,
          workers: [],
          message: 'No active workers found to simulate.',
        });
        return;
      }

      this.sim.clear();
      this.elapsedTicks.clear();
      this.openAlertId.clear();
      this.incidentRaised.clear();

      const workerList = [];
      activeWorkers.forEach((worker) => {
        const sw = {
          workerId: worker.id,
          name: `${worker.firstName || ''} ${worker.lastName || ''}`.trim() || worker.id,
          vestId: worker.assignedVestId || '',
          siteId: worker.siteId || siteId || '',
          contractorId: worker.contractorId || contractorId || '',
          scenario: 'HEALTHY',
          phase: PHASES.NORMAL,
          heartRate: 78,
          temperature: 36.8,
          battery: 96,
          motion: 'MOVING',
        };
        const lat = this.gpsCenter.lat + randFloat(-0.0008, 0.0008);
        const lng = this.gpsCenter.lng + randFloat(-0.0008, 0.0008);

        this.sim.set(worker.id, { w: sw, lat, lng });
        workerList.push(sw);
      });

      this.updateState({
        busy: false,
        bound: true,
        workers: workerList,
      });
    } catch (err) {
      console.error('Simulation bind error:', err);
      this.updateState({
        busy: false,
        bound: true,
        message: `Failed to load workers: ${err.message}`,
      });
    }
  }

  async start() {
    if (!this.state.canStream || this.intervalId) return;
    if (this.sim.size === 0) {
      this.updateState({ message: 'No workers to stream.' });
      return;
    }

    this.updateState({ busy: true });
    try {
      // Test writing first sensor reading to RTDB
      const firstId = this.sim.keys().next().value;
      await set(ref(rtdb, `liveReadings/${firstId}`), {
        workerId: firstId,
        timestamp: Date.now(),
      });

      this.log(`Live stream started — ${this.sim.size} vest(s) transmitting`);
      this.updateState({ streaming: true, busy: false });

      this.intervalId = setInterval(() => {
        this.tickAll();
      }, TICK_MS);
    } catch (err) {
      console.error('Simulation start error:', err);
      this.updateState({
        busy: false,
        message: `Realtime Database stream failed: ${err.message}`,
      });
    }
  }

  stop() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    this.updateState({ streaming: false });
    this.log('Live stream stopped');

    // Clear live readings from RTDB
    for (const id of this.sim.keys()) {
      remove(ref(rtdb, `liveReadings/${id}`)).catch(() => {});
    }

    for (const item of this.sim.values()) {
      item.w = {
        ...item.w,
        phase: PHASES.NORMAL,
        motion: 'MOVING',
      };
    }
    this.elapsedTicks.clear();
    this.publish();
  }

  setScenario(workerId, scenario) {
    const s = this.sim.get(workerId);
    if (!s) return;

    s.w = { ...s.w, scenario };
    this.elapsedTicks.set(workerId, 0);
    this.incidentRaised.delete(`${workerId}:${scenario}`);

    if (scenario === 'HEALTHY') {
      this.resolveOpenAlert(workerId, 'Condition cleared — worker recovered');
      s.w = { ...s.w, phase: PHASES.NORMAL, motion: 'MOVING' };
      set(ref(rtdb, `liveReadings/${workerId}/safetyResponse`), 'NONE').catch(() => {});
    }

    const scLabel = SCENARIOS.find((x) => x.key === scenario)?.label || scenario;
    this.log(`${s.w.name}: scenario → ${scLabel}`);
    this.publish();

    if (!this.state.streaming && scenario !== 'HEALTHY') {
      this.start();
    }
  }

  escalateNow(workerId) {
    const s = this.sim.get(workerId);
    if (!s) return;

    if (s.w.scenario === 'HEALTHY') {
      this.setScenario(workerId, 'FALL');
    }
    this.elapsedTicks.set(workerId, 999);
    this.tick(workerId);
    this.publish();
  }

  async tickAll() {
    const ids = Array.from(this.sim.keys());
    for (const id of ids) {
      await this.tick(id);
    }
    this.publish();
  }

  async tick(id) {
    const s = this.sim.get(id);
    if (!s) return;

    const ticks = (this.elapsedTicks.get(id) || 0) + 1;
    this.elapsedTicks.set(id, ticks);
    const sc = s.w.scenario;

    let hr = s.w.heartRate;
    let temp = s.w.temperature;
    let motion = 'MOVING';
    let fall = false;
    let response = 'NONE';

    switch (sc) {
      case 'HEALTHY':
        hr = driftInt(hr, 78, 68, 96, 4);
        temp = driftFloat(temp, 36.8, 36.3, 37.2, 0.1);
        break;
      case 'HIGH_HEART_RATE':
        hr = rampInt(hr, 132, 9);
        hr = driftInt(hr, hr, 118, 145, 4);
        temp = driftFloat(temp, 37.1, 36.6, 37.6, 0.1);
        break;
      case 'HIGH_TEMPERATURE':
        temp = rampFloat(temp, 39.0, 0.35);
        temp = driftFloat(temp, temp, 38.2, 39.8, 0.15);
        hr = driftInt(hr, 96, 88, 108, 4);
        break;
      case 'FALL':
        fall = true;
        motion = 'FALL_DETECTED';
        hr = driftInt(hr, 104, 92, 120, 5);
        break;
      case 'INACTIVITY':
        motion = 'INACTIVE';
        hr = driftInt(hr, 62, 55, 72, 3);
        break;
      case 'EMERGENCY_REQUEST':
        response = 'EMERGENCY_REQUESTED';
        hr = driftInt(hr, 110, 95, 130, 6);
        break;
      default:
        break;
    }

    const battery = clamp(s.w.battery - (randInt(0, 2) === 0 ? 1 : 0), 5, 100);

    s.lat += randFloat(-0.00025, 0.00025);
    s.lng += randFloat(-0.00025, 0.00025);

    let phase = s.w.phase;
    if (sc === 'HEALTHY') {
      phase = PHASES.NORMAL;
    } else if (sc === 'EMERGENCY_REQUEST') {
      phase = PHASES.EMERGENCY;
    } else if (sc === 'FALL') {
      phase = ticks >= 8 ? PHASES.EMERGENCY : PHASES.DANGER;
    } else if (ticks in [1, 2]) {
      phase = PHASES.WARNING;
    } else if (ticks >= 3 && ticks <= 9) {
      phase = PHASES.DANGER;
    } else if (ticks >= 10) {
      phase = PHASES.EMERGENCY;
    }

    s.w = {
      ...s.w,
      heartRate: hr,
      temperature: round1(temp),
      battery,
      motion,
      phase,
    };

    // Push reading to Realtime Database
    try {
      await set(ref(rtdb, `liveReadings/${id}`), {
        vestId: s.w.vestId,
        heartRate: hr,
        temperature: round1(temp),
        motionState: motion,
        fallDetected: fall,
        latitude: round5(s.lat),
        longitude: round5(s.lng),
        battery,
        safetyResponse:
          phase === PHASES.EMERGENCY && response === 'NONE' ? 'NO_RESPONSE' : response,
        timestamp: Date.now(),
      });
    } catch (err) {
      console.warn('Could not push live reading for worker', id, err);
    }

    // Raise Firestore Alert when entering DANGER
    if (phase === PHASES.DANGER && !this.openAlertId.has(id)) {
      this.raiseAlert(s.w, 'DANGER');
    }

    // Raise Firestore Incident + Alert when entering EMERGENCY
    if (phase === PHASES.EMERGENCY) {
      const incidentKey = `${id}:${sc}`;
      if (!this.incidentRaised.has(incidentKey)) {
        this.incidentRaised.add(incidentKey);
        this.raiseAlert(s.w, 'EMERGENCY');
        this.raiseIncident(s.w);
        set(ref(rtdb, `liveReadings/${id}/safetyResponse`), 'NO_RESPONSE').catch(() => {});
      }
    }
  }

  typeFor(scenario) {
    switch (scenario) {
      case 'HIGH_HEART_RATE': return 'HEART_RATE_WARNING';
      case 'HIGH_TEMPERATURE': return 'TEMPERATURE_WARNING';
      case 'FALL': return 'FALL_DETECTED';
      case 'INACTIVITY': return 'INACTIVITY';
      case 'EMERGENCY_REQUEST': return 'EMERGENCY_REQUEST';
      default: return 'NO_SAFETY_RESPONSE';
    }
  }

  messageFor(w) {
    switch (w.scenario) {
      case 'HIGH_HEART_RATE': return `Elevated heart rate (${w.heartRate} BPM)`;
      case 'HIGH_TEMPERATURE': return `High body temperature (${round1(w.temperature)} °C)`;
      case 'FALL': return 'Fall detected — no movement since';
      case 'INACTIVITY': return 'Prolonged inactivity detected';
      case 'EMERGENCY_REQUEST': return 'Worker pressed the emergency button';
      default: return 'No safety response';
    }
  }

  async raiseAlert(w, severity) {
    try {
      const alertRef = doc(collection(db, 'alerts'));
      await set(alertRef, {
        workerId: w.workerId,
        vestId: w.vestId,
        siteId: w.siteId,
        contractorId: w.contractorId,
        createdAt: Date.now(),
        simulated: true,
        type: this.typeFor(w.scenario),
        severity,
        status: 'ACTIVE',
        message: this.messageFor(w),
      });
      this.openAlertId.set(w.workerId, alertRef.id);
      this.log(`${w.name}: ${severity} alert raised`);
    } catch (err) {
      console.error('raiseAlert failed:', err);
    }
  }

  async raiseIncident(w) {
    try {
      const incidentRef = doc(collection(db, 'incidents'));
      await set(incidentRef, {
        workerId: w.workerId,
        vestId: w.vestId,
        siteId: w.siteId,
        contractorId: w.contractorId,
        createdAt: Date.now(),
        simulated: true,
        alertId: this.openAlertId.get(w.workerId) || null,
        type: this.typeFor(w.scenario),
        severity: 'EMERGENCY',
        heartRate: w.heartRate,
        temperature: round1(w.temperature),
        latitude: round5(this.sim.get(w.workerId)?.lat || this.gpsCenter.lat),
        longitude: round5(this.sim.get(w.workerId)?.lng || this.gpsCenter.lng),
        outcome: null,
        resolutionNotes: null,
      });
      this.log(`${w.name}: EMERGENCY incident created`);
    } catch (err) {
      console.error('raiseIncident failed:', err);
    }
  }

  async resolveOpenAlert(workerId, notes) {
    const alertId = this.openAlertId.get(workerId);
    if (!alertId) return;
    this.openAlertId.delete(workerId);

    try {
      const alertRef = doc(db, 'alerts', alertId);
      await set(alertRef, {
        status: 'RESOLVED',
        resolvedAt: Date.now(),
        resolutionNotes: notes,
        resolvedBy: auth.currentUser?.uid || 'simulator',
      }, { merge: true });
    } catch (err) {
      console.error('resolveOpenAlert failed:', err);
    }
  }

  publish() {
    this.updateState({
      workers: Array.from(this.sim.values()).map((item) => item.w),
    });
  }

  async seedHistory(months = 6) {
    if (this.sim.size === 0) {
      this.updateState({ message: 'No workers to seed history for.' });
      return;
    }

    this.updateState({ busy: true });
    try {
      const now = Date.now();
      const monthMs = 30 * 24 * 3600 * 1000;
      let written = 0;

      const workers = Array.from(this.sim.values()).map((s) => s.w);

      // Batches in Firestore support up to 500 operations
      let currentBatch = writeBatch(db);
      let opCount = 0;

      for (const w of workers) {
        const count = randInt(4, 8);
        for (let i = 0; i < count; i++) {
          const ageMs = randInt(0, months * monthMs);
          const createdAt = now - ageMs;
          const { type, severity } = randomEvent();
          const resolved = randInt(0, 99) < 85;
          const hr = type === 'HEART_RATE_WARNING' ? randInt(118, 150) : randInt(70, 105);
          const temp = type === 'TEMPERATURE_WARNING'
            ? round1(randFloat(38.2, 39.9))
            : round1(randFloat(36.4, 37.4));

          const incidentRef = doc(collection(db, 'incidents'));
          currentBatch.set(incidentRef, {
            workerId: w.workerId,
            vestId: w.vestId,
            siteId: w.siteId,
            contractorId: w.contractorId,
            type,
            severity,
            heartRate: hr,
            temperature: temp,
            latitude: round5(this.gpsCenter.lat + randFloat(-0.001, 0.001)),
            longitude: round5(this.gpsCenter.lng + randFloat(-0.001, 0.001)),
            createdAt,
            outcome: resolved ? randomOutcome() : null,
            resolutionNotes: resolved ? 'Reviewed by site safety officer.' : null,
            resolvedBy: resolved ? (auth.currentUser?.uid || 'simulator') : null,
            simulated: true,
          });

          const alertRef = doc(collection(db, 'alerts'));
          currentBatch.set(alertRef, {
            workerId: w.workerId,
            vestId: w.vestId,
            siteId: w.siteId,
            contractorId: w.contractorId,
            type,
            severity,
            status: resolved ? 'RESOLVED' : 'ACTIVE',
            message: `${type.replace(/_/g, ' ').toLowerCase()} · ${w.name}`,
            createdAt,
            incidentId: incidentRef.id,
            simulated: true,
          });

          written += 2;
          opCount += 2;

          if (opCount >= 400) {
            await currentBatch.commit();
            currentBatch = writeBatch(db);
            opCount = 0;
          }
        }
      }

      if (opCount > 0) {
        await currentBatch.commit();
      }

      this.log(`Seeded ${written} history records across ${months} months`);
      this.updateState({
        busy: false,
        message: `Added ${written / 2} incidents to history.`,
      });
    } catch (err) {
      console.error('seedHistory failed:', err);
      this.updateState({
        busy: false,
        message: `Seed failed: ${err.message}`,
      });
    }
  }

  async reset() {
    this.stop();
    this.updateState({ busy: true });

    try {
      let deleted = 0;

      // Delete simulated alerts and incidents
      for (const collName of ['alerts', 'incidents']) {
        let q = query(collection(db, collName), where('simulated', '==', true));
        if (this.boundSiteId) {
          q = query(q, where('siteId', '==', this.boundSiteId));
        } else if (this.boundContractorId) {
          q = query(q, where('contractorId', '==', this.boundContractorId));
        }

        const snap = await getDocs(q);
        let batch = writeBatch(db);
        let count = 0;

        for (const docSnap of snap.docs) {
          batch.delete(docSnap.ref);
          count++;
          deleted++;
          if (count >= 400) {
            await batch.commit();
            batch = writeBatch(db);
            count = 0;
          }
        }
        if (count > 0) {
          await batch.commit();
        }
      }

      // Clear realtime readings for all loaded workers
      for (const id of this.sim.keys()) {
        await remove(ref(rtdb, `liveReadings/${id}`)).catch(() => {});
        const s = this.sim.get(id);
        if (s) {
          s.w = {
            ...s.w,
            scenario: 'HEALTHY',
            phase: PHASES.NORMAL,
          };
        }
      }

      this.openAlertId.clear();
      this.incidentRaised.clear();
      this.elapsedTicks.clear();
      this.publish();

      this.log(`Reset — removed ${deleted} simulated records`);
      this.updateState({
        busy: false,
        message: 'Cleared all simulated data.',
      });
    } catch (err) {
      console.error('Simulation reset failed:', err);
      this.updateState({
        busy: false,
        message: `Reset failed: ${err.message}`,
      });
    }
  }

  consumeMessage() {
    this.updateState({ message: null });
  }
}

// Global singleton instance so simulation keeps streaming while browsing tabs
export const simulationEngine = new SimulationEngine();
