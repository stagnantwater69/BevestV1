import React, { useEffect, useState } from 'react';
import {
  FiPlay, FiSquare, FiRefreshCw, FiAlertTriangle, FiX, FiCheckCircle
} from 'react-icons/fi';
import {
  TbHeartbeat, TbTemperature, TbBattery2, TbHistory, TbBolt, TbCpu
} from 'react-icons/tb';
import { simulationEngine, SCENARIOS, PHASES } from './simulationEngine';
import './simulation.css';

const PHASE_COLORS = {
  NORMAL: '#16a34a',
  WARNING: '#d97706',
  DANGER: '#dc2626',
  EMERGENCY: '#9f1239',
};

const SimWorkerCard = ({ worker, onScenario, onEscalate }) => {
  const phaseColor = PHASE_COLORS[worker.phase] || PHASE_COLORS.NORMAL;

  return (
    <div className="sim-worker-card">
      <div className="sim-worker-header">
        <div>
          <h3 className="sim-worker-name">{worker.name}</h3>
          <span className="sim-worker-meta">
            ID {worker.workerId}{worker.vestId ? ` · Vest ${worker.vestId}` : ''}
          </span>
        </div>
        <span
          className="sim-phase-badge"
          style={{
            backgroundColor: `color-mix(in srgb, ${phaseColor} 14%, transparent)`,
            color: phaseColor,
          }}
        >
          {worker.phase}
        </span>
      </div>

      <div className="sim-vitals-row">
        <div className="sim-vital-item">
          <TbHeartbeat className="sim-vital-icon" />
          <span className="sim-vital-val">{worker.heartRate}</span>
          <span className="sim-vital-unit">BPM</span>
        </div>
        <div className="sim-vital-item">
          <TbTemperature className="sim-vital-icon" />
          <span className="sim-vital-val">{worker.temperature.toFixed(1)}</span>
          <span className="sim-vital-unit">°C</span>
        </div>
        <div className="sim-vital-item">
          <TbBattery2 className="sim-vital-icon" />
          <span className="sim-vital-val">{worker.battery}</span>
          <span className="sim-vital-unit">%</span>
        </div>
      </div>

      <div className="sim-scenarios-row">
        {SCENARIOS.map((sc) => (
          <button
            key={sc.key}
            type="button"
            className={`sim-scenario-chip ${worker.scenario === sc.key ? 'selected' : ''}`}
            onClick={() => onScenario(sc.key)}
          >
            {sc.label}
          </button>
        ))}
      </div>

      {worker.scenario !== 'HEALTHY' && worker.phase !== PHASES.EMERGENCY && (
        <button
          type="button"
          className="sim-escalate-btn"
          onClick={onEscalate}
        >
          <TbBolt /> Escalate to emergency now
        </button>
      )}
    </div>
  );
};

const SimulationScreen = ({ user }) => {
  const [state, setState] = useState(simulationEngine.state);
  const [confirmReset, setConfirmReset] = useState(false);

  useEffect(() => {
    // Bind to user scope
    const isContractor = user?.role === 'CONTRACTOR';
    simulationEngine.bind({
      contractorId: isContractor ? (user.profile?.contractorId || user.uid) : null,
      siteId: user?.profile?.siteId || null,
      canStream: true,
    });

    const unsubscribe = simulationEngine.subscribe(setState);
    return () => unsubscribe();
  }, [user]);

  const handleToggle = () => {
    if (state.streaming) {
      simulationEngine.stop();
    } else {
      simulationEngine.start();
    }
  };

  const handleScenario = (workerId, sc) => {
    simulationEngine.setScenario(workerId, sc);
  };

  const handleEscalate = (workerId) => {
    simulationEngine.escalateNow(workerId);
  };

  const handleSeedHistory = () => {
    simulationEngine.seedHistory(6);
  };

  const handleResetConfirm = () => {
    setConfirmReset(false);
    simulationEngine.reset();
  };

  const hasElevation = state.liveWarnings > 0 || state.liveEmergencies > 0;

  return (
    <div className="sim-container">
      {/* Page Header */}
      <div className="ct-page-header">
        <div className="ct-page-header-row">
          <div>
            <h1>IoT simulation</h1>
            <p className="sim-eyebrow-sub">DEMO MODE</p>
          </div>
        </div>
      </div>

      <p className="sim-desc">
        The vests aren't built yet. This streams realistic sensor data and raises the alerts and incidents
        the cloud engine would create — so you can see the application fully connected.
      </p>

      {/* Message Notification */}
      {state.message && (
        <div className="sim-message-bar">
          <span>{state.message}</span>
          <button
            type="button"
            className="sim-msg-dismiss"
            onClick={() => simulationEngine.consumeMessage()}
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Stream Toggle Card */}
      <div className={`sim-stream-card ${state.streaming ? 'active' : ''}`}>
        <div className="sim-stream-info">
          <span className={`sim-stream-eyebrow ${state.streaming ? 'live' : 'offline'}`}>
            {state.streaming ? '● LIVE' : '○ OFFLINE'}
          </span>
          <h2 className="sim-stream-title">
            {state.streaming
              ? `${state.workers.length} vest${state.workers.length === 1 ? '' : 's'} transmitting`
              : 'Stream is stopped'}
          </h2>
          {state.streaming && hasElevation && (
            <p className="sim-stream-alert-note">
              {state.liveEmergencies > 0 && `${state.liveEmergencies} emergency `}
              {state.liveWarnings > 0 && `${state.liveWarnings} elevated`}
            </p>
          )}
        </div>

        <button
          type="button"
          className={`sim-stream-btn ${state.streaming ? 'stop' : 'start'}`}
          onClick={handleToggle}
          disabled={state.busy}
        >
          {state.streaming ? <FiSquare /> : <FiPlay />}
          {state.streaming ? 'Stop' : 'Go live'}
        </button>
      </div>

      {/* Workers Section */}
      <div className="sim-section-header">
        <h2>Workers</h2>
        <span className="sim-worker-count">{state.workers.length} registered</span>
      </div>

      {state.workers.length === 0 ? (
        <div className="sim-empty-card">
          <TbCpu className="sim-empty-icon" />
          <h3>No active workers found</h3>
          <p>Add a worker in the system and pair a vest, then return here to simulate them.</p>
        </div>
      ) : (
        <div className="sim-workers-grid">
          {state.workers.map((w) => (
            <SimWorkerCard
              key={w.workerId}
              worker={w}
              onScenario={(sc) => handleScenario(w.workerId, sc)}
              onEscalate={() => handleEscalate(w.workerId)}
            />
          ))}
        </div>
      )}

      {/* History Section */}
      <div className="sim-section-header" style={{ marginTop: '2.5rem' }}>
        <h2>History</h2>
        <p className="sim-section-sub">
          Adds ~4–8 past incidents per worker across the last 6 months. Safe to run more than once.
        </p>
      </div>

      <div className="sim-history-actions">
        <button
          type="button"
          className="btn-primary sim-action-btn"
          onClick={handleSeedHistory}
          disabled={state.busy || !state.bound}
        >
          {state.busy ? (
            <FiRefreshCw className="sim-spin" />
          ) : (
            <TbHistory style={{ fontSize: '1.25rem' }} />
          )}
          {state.busy ? 'Seeding records...' : 'Seed 6 months of history'}
        </button>

        <button
          type="button"
          className="sim-reset-btn"
          onClick={() => setConfirmReset(true)}
          disabled={state.busy}
        >
          <FiRefreshCw /> Reset — remove all simulated data
        </button>
      </div>

      {/* Activity Log Section */}
      {state.log.length > 0 && (
        <div className="sim-log-section">
          <h2>Activity</h2>
          <div className="sim-log-box">
            {state.log.map((entry, idx) => (
              <div key={idx} className="sim-log-row">
                <code>{entry}</code>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Reset Confirmation Modal */}
      {confirmReset && (
        <div className="modal-overlay" onClick={() => setConfirmReset(false)}>
          <div className="modal-content ct-modal-sm" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Reset simulation?</h2>
              <button
                type="button"
                className="close-btn"
                onClick={() => setConfirmReset(false)}
                aria-label="Close"
              >
                <FiX />
              </button>
            </div>
            <div className="modal-body ct-modal-body">
              <p className="ct-muted" style={{ lineHeight: 1.5 }}>
                Stops the live stream and permanently deletes every simulated alert and incident.
                Real operational data is untouched.
              </p>
              <div className="form-actions" style={{ marginTop: '1.5rem' }}>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setConfirmReset(false)}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="submit-btn"
                  style={{
                    backgroundColor: '#dc2626',
                    marginTop: 0,
                    width: 'auto',
                    padding: '0.65rem 1.25rem',
                  }}
                  onClick={handleResetConfirm}
                >
                  Reset
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SimulationScreen;
