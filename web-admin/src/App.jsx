import React, { useState } from 'react';
import { updatePassword } from 'firebase/auth';
import { doc, updateDoc } from 'firebase/firestore';
import { FiLock, FiCheckCircle, FiX } from 'react-icons/fi';
import { auth, db } from './firebase';
import Login from './Login';
import Dashboard from './Dashboard';
import ContractorDashboard from './contractor/ContractorDashboard';
import DeveloperDashboard from './DeveloperDashboard';
import ConfirmModal from './ConfirmModal';

function App() {
  const [user, setUser] = useState(null);
  const [newPass, setNewPass] = useState('');
  const [confirmPass, setConfirmPass] = useState('');
  const [passError, setPassError] = useState('');
  const [passSaving, setPassSaving] = useState(false);
  const [dismissedPrompt, setDismissedPrompt] = useState(false);
  const [passUpdatedModal, setPassUpdatedModal] = useState(false);

  const showPasswordPrompt = Boolean(user && user.profile?.mustChangePassword && !dismissedPrompt);

  const handleFirstLoginPasswordChange = async (e) => {
    e.preventDefault();
    setPassError('');
    if (!newPass || newPass.length < 6) {
      setPassError('New password must be at least 6 characters.');
      return;
    }
    if (newPass !== confirmPass) {
      setPassError('Passwords do not match.');
      return;
    }
    setPassSaving(true);
    try {
      if (auth.currentUser) {
        await updatePassword(auth.currentUser, newPass);
      }
      await updateDoc(doc(db, 'users', user.uid), {
        mustChangePassword: false,
        initialTempPassword: null,
        passwordChangedAt: new Date(),
      });
      setUser((prev) => prev ? { ...prev, profile: { ...prev.profile, mustChangePassword: false } } : prev);
      setDismissedPrompt(true);
      setNewPass('');
      setConfirmPass('');
      setPassUpdatedModal(true);
    } catch (err) {
      setPassError(err.message || 'Failed to update password.');
    } finally {
      setPassSaving(false);
    }
  };

  if (user) {
    let dashboardView;
    if (user.role === 'DEVELOPER') {
      dashboardView = <DeveloperDashboard user={user} onLogout={() => { setDismissedPrompt(false); setUser(null); }} />;
    } else if (user.role === 'CONTRACTOR') {
      dashboardView = <ContractorDashboard user={user} onLogout={() => { setDismissedPrompt(false); setUser(null); }} />;
    } else {
      dashboardView = <Dashboard user={user} onLogout={() => { setDismissedPrompt(false); setUser(null); }} />;
    }

    return (
      <>
        {dashboardView}
        {showPasswordPrompt && (
          <div className="modal-backdrop" style={{ zIndex: 9999 }}>
            <div className="modal-content sub-modal" style={{ maxWidth: '440px' }} onClick={(e) => e.stopPropagation()}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                  <div style={{ padding: '0.5rem', backgroundColor: '#fff7ed', color: '#ea580c', borderRadius: '8px' }}>
                    <FiLock style={{ fontSize: '1.2rem' }} />
                  </div>
                  <h3 style={{ fontSize: '1.2rem', fontWeight: 700, margin: 0 }}>Change Temporary Password</h3>
                </div>
                <button
                  type="button"
                  onClick={() => setDismissedPrompt(true)}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', fontSize: '1.2rem' }}
                  title="Remind me later"
                >
                  <FiX />
                </button>
              </div>
              <p style={{ color: '#475569', fontSize: '0.875rem', marginBottom: '1.25rem', lineHeight: 1.5 }}>
                Welcome! Since you signed in with a temporary password, you can set your new personal password now.
              </p>
              <form onSubmit={handleFirstLoginPasswordChange}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.9rem', marginBottom: '1.25rem' }}>
                  <div>
                    <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>NEW PASSWORD *</label>
                    <input
                      type="password"
                      className="form-input"
                      placeholder="At least 6 characters"
                      value={newPass}
                      onChange={(e) => setNewPass(e.target.value)}
                      style={{ width: '100%' }}
                      required
                    />
                  </div>
                  <div>
                    <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>CONFIRM NEW PASSWORD *</label>
                    <input
                      type="password"
                      className="form-input"
                      placeholder="Re-enter new password"
                      value={confirmPass}
                      onChange={(e) => setConfirmPass(e.target.value)}
                      style={{ width: '100%' }}
                      required
                    />
                  </div>
                </div>
                {passError && (
                  <div className="error-message" style={{ marginBottom: '1rem', textAlign: 'left' }}>{passError}</div>
                )}
                <div className="modal-actions">
                  <button type="button" className="btn-secondary" onClick={() => setDismissedPrompt(true)} disabled={passSaving}>
                    Later
                  </button>
                  <button type="submit" className="btn-primary" disabled={passSaving}>
                    <FiCheckCircle style={{ marginRight: '0.35rem' }} />
                    {passSaving ? 'Updating...' : 'Update Password'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
        <ConfirmModal
          open={passUpdatedModal}
          title="Password Updated Successfully!"
          message="Your password has been updated successfully! You can now continue using your BeVest account."
          variant="success"
          noticeOnly
          onCancel={() => setPassUpdatedModal(false)}
        />
      </>
    );
  }

  return (
    <Login onLogin={(userData) => { setDismissedPrompt(false); setUser(userData); }} />
  );
}

export default App;
