import React, { useState } from 'react';
import {
  FiUserCheck, FiPlus, FiSlash, FiSearch, FiLock, FiRefreshCw, FiArrowLeft, FiX, FiCheckCircle
} from 'react-icons/fi';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { doc, setDoc, updateDoc } from 'firebase/firestore';
import { secondaryAuth, db } from '../firebase';
import { useContractorSsos, useContractorSites, siteLabel, STATUS_COLOR, initialsOf } from './contractorData';
import { PageHeader, SearchBox, Badge, EmptyState, ErrorState, Skeleton } from './ui';
import { logActivity } from '../activityLogger';
import PhoneInput, { formatPhoneWithCountryCode } from '../PhoneInput';

/**
 * Site Safety Officers directory for the contractor.
 * Port of ContractorSsosScreen (ContractorScreens.kt) and SsoListViewModel.
 */
const ContractorSsos = ({ contractorId, onAdd, onOpen, onBack, backLabel = 'Dashboard' }) => {
  const { loading, error, ssos } = useContractorSsos(contractorId);
  const { sites } = useContractorSites(contractorId);
  const [queryText, setQueryText] = useState('');
  const [pendingDisable, setPendingDisable] = useState(null);
  const [disabling, setDisabling] = useState(false);

  const visible = ssos.filter((sso) => {
    const q = queryText.trim().toLowerCase();
    if (!q) return true;
    return (
      (sso.fullName || '').toLowerCase().includes(q) ||
      (sso.email || '').toLowerCase().includes(q)
    );
  });

  const handleDisable = async () => {
    if (!pendingDisable) return;
    setDisabling(true);
    try {
      await updateDoc(doc(db, 'users', pendingDisable.uid), {
        active: false,
        status: 'Inactive'
      });
      logActivity(`Disabled Site Safety Officer: ${pendingDisable.fullName || pendingDisable.email}`);
      setPendingDisable(null);
    } catch (err) {
      console.error('Error disabling SSO:', err);
      alert('Failed to disable officer: ' + err.message);
    } finally {
      setDisabling(false);
    }
  };

  const header = (
    <PageHeader
      breadcrumb={onBack ? [{ label: backLabel, onClick: onBack }, { label: 'Site Safety Officers' }] : null}
      title="Site Safety Officers"
      subtitle="Officers monitoring workers on your sites"
      actions={
        <button id="ct-add-sso-btn" className="btn-primary" onClick={onAdd}>
          <FiPlus /> Add officer
        </button>
      }
    />
  );

  if (loading) return (<>{header}<Skeleton rows={4} /></>);
  if (error) return (<>{header}<ErrorState message={error} /></>);

  return (
    <>
      {header}

      <div className="ct-toolbar">
        <SearchBox
          id="ct-sso-search"
          value={queryText}
          onChange={setQueryText}
          placeholder="Search officers by name or email"
        />
      </div>

      {visible.length === 0 ? (
        <EmptyState
          icon={queryText ? FiSearch : FiUserCheck}
          title={queryText ? 'No matches' : 'No officers yet'}
          message={
            queryText
              ? `Nothing matches "${queryText}".`
              : 'Add a Site Safety Officer and assign them to a site so they can monitor the workers there.'
          }
          action={
            !queryText && (
              <button className="btn-primary" onClick={onAdd} style={{ marginTop: '1rem' }}>
                <FiPlus /> Add officer
              </button>
            )
          }
        />
      ) : (
        <div className="ct-list">
          {visible.map((sso) => (
            <div
              key={sso.uid}
              className="ct-row ct-row-clickable"
              id={`ct-sso-${sso.uid}`}
              role="button"
              tabIndex={0}
              onClick={() => onOpen(sso.uid)}
              onKeyDown={(e) => { if (e.key === 'Enter') onOpen(sso.uid); }}
            >
              <div
                className="ct-avatar"
                style={{
                  '--accent': sso.active ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE
                }}
              >
                {initialsOf(sso.fullName || sso.email, 'O')}
                <span className="ct-avatar-status" />
              </div>

              <div className="ct-row-main">
                <span className="ct-row-title">{sso.fullName || sso.email}</span>
                <span className="ct-row-sub">{sso.email}</span>
                <span className="ct-row-meta">
                  {siteLabel(sites, sso.siteId)}
                </span>
              </div>

              <Badge
                text={sso.active ? 'Active' : 'Disabled'}
                color={sso.active ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE}
              />

              {sso.active && (
                <button
                  className="btn-secondary ct-disable-btn"
                  title="Disable access"
                  onClick={(e) => { e.stopPropagation(); setPendingDisable(sso); }}
                >
                  <FiSlash /> Disable
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Confirmation Dialog matching mobile ConfirmationDialog */}
      {pendingDisable && (
        <div className="modal-overlay" onClick={() => setPendingDisable(null)}>
          <div className="modal-content ct-modal-sm" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Disable this officer?</h2>
              <button
                className="close-btn"
                onClick={() => setPendingDisable(null)}
                aria-label="Close"
              >
                <FiX />
              </button>
            </div>
            <div className="modal-body ct-modal-body">
              <p className="ct-muted" style={{ lineHeight: 1.5 }}>
                <strong>{pendingDisable.fullName || pendingDisable.email}</strong> will lose access
                on their next app open and will stop receiving safety alerts for their site.
                Their records will be kept.
              </p>
              <div className="form-actions" style={{ marginTop: '1.5rem' }}>
                <button
                  className="btn-secondary"
                  onClick={() => setPendingDisable(null)}
                  disabled={disabling}
                >
                  Cancel
                </button>
                <button
                  className="submit-btn"
                  style={{
                    backgroundColor: '#dc2626',
                    marginTop: 0,
                    width: 'auto',
                    padding: '0.65rem 1.25rem'
                  }}
                  onClick={handleDisable}
                  disabled={disabling}
                >
                  {disabling ? 'Disabling...' : 'Disable access'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

/**
 * Add SSO account form.
 * Port of AddSsoScreen (ContractorScreens.kt) and AddSsoViewModel.
 */
export const AddSso = ({ contractorId, onDone }) => {
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    siteId: '',
    tempPassword: ''
  });
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const handleGeneratePassword = () => {
    const randomNum = Math.floor(1000 + Math.random() * 9000);
    setFormData((prev) => ({ ...prev, tempPassword: `SSO-2026-${randomNum}` }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');

    if (!formData.firstName.trim() || !formData.lastName.trim() || !formData.email.trim() || !formData.tempPassword) {
      setErrorMsg('Please fill in all required fields and provide a temporary password.');
      return;
    }

    setSubmitting(true);
    try {
      // Create user using secondary Firebase Auth instance so contractor stays signed in
      const userCredential = await createUserWithEmailAndPassword(
        secondaryAuth,
        formData.email.trim(),
        formData.tempPassword
      );
      const newUser = userCredential.user;

      // Create document in Firestore users/{uid}
      await setDoc(doc(db, 'users', newUser.uid), {
        firstName: formData.firstName.trim(),
        lastName: formData.lastName.trim(),
        email: formData.email.trim(),
        phone: formatPhoneWithCountryCode(formData.phone),
        role: 'SSO',
        contractorId: contractorId,
        siteId: formData.siteId.trim() || null,
        active: true,
        status: 'Active',
        createdAt: new Date()
      });

      // Sign out secondary auth
      await secondaryAuth.signOut();

      logActivity(`Created Site Safety Officer: ${formData.firstName.trim()} ${formData.lastName.trim()} (${formData.email.trim()})`);

      onDone();
    } catch (err) {
      console.error('Error creating SSO:', err);
      setErrorMsg(err.message || 'Failed to create officer account.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader
        breadcrumb={[{ label: 'Site Safety Officers', onClick: onDone }, { label: 'Add officer' }]}
        title="Add officer"
        subtitle="New Site Safety Officer account for your project sites"
      />

      <div className="add-user-card" style={{ maxWidth: '800px', margin: '0 auto' }}>
        <form onSubmit={handleSubmit}>
          {/* Personal Name */}
          <div className="form-section-container">
            <div className="form-section-info">
              <h3>Name</h3>
              <p>Officer identity details for identification on site.</p>
            </div>
            <div className="form-grid">
              <div className="form-group-custom">
                <label className="form-label">First name *</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Marcus"
                  value={formData.firstName}
                  onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                  required
                />
              </div>
              <div className="form-group-custom">
                <label className="form-label">Last name *</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Vance"
                  value={formData.lastName}
                  onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                  required
                />
              </div>
            </div>
          </div>

          <hr className="form-divider" />

          {/* Contact Details */}
          <div className="form-section-container">
            <div className="form-section-info">
              <h3>Contact</h3>
              <p>Used by the officer to sign in and receive critical alerts.</p>
            </div>
            <div className="form-grid">
              <div className="form-group-custom">
                <label className="form-label">Email address *</label>
                <input
                  type="email"
                  className="form-input"
                  placeholder="sso@contractor.com"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  required
                />
              </div>
              <div className="form-group-custom">
                <label className="form-label">Phone number</label>
                <PhoneInput
                  value={formData.phone}
                  onChange={(phone) => setFormData({ ...formData, phone })}
                />
              </div>
            </div>
          </div>

          <hr className="form-divider" />

          {/* Access & Assignment */}
          <div className="form-section-container">
            <div className="form-section-info">
              <h3>Access & Assignment</h3>
              <p>Site assignment and initial login credentials.</p>
            </div>
            <div className="form-grid">
              <div className="form-group-custom">
                <label className="form-label">Assigned site ID</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="Optional — e.g. Site-A"
                  value={formData.siteId}
                  onChange={(e) => setFormData({ ...formData, siteId: e.target.value })}
                />
                <span className="input-hint" style={{ margin: '0.25rem 0 0' }}>
                  Can also be assigned or updated later
                </span>
              </div>
              <div className="form-group-custom">
                <label className="form-label">Temporary password *</label>
                <div className="password-input-group">
                  <input
                    type="text"
                    className="form-input password-field"
                    placeholder="Click generate to create..."
                    value={formData.tempPassword}
                    onChange={(e) => setFormData({ ...formData, tempPassword: e.target.value })}
                    required
                  />
                  <button
                    className="icon-btn lock-btn"
                    type="button"
                    onClick={handleGeneratePassword}
                    title="Generate Password"
                  >
                    {formData.tempPassword ? <FiLock /> : <FiRefreshCw />}
                  </button>
                </div>
                <span className="input-hint" style={{ margin: '0.25rem 0 0' }}>
                  They change this from their profile after signing in
                </span>
              </div>
            </div>
          </div>

          <hr className="form-divider" />

          {errorMsg && (
            <div className="error-message" style={{ textAlign: 'left', marginBottom: '1.5rem' }}>
              {errorMsg}
            </div>
          )}

          <div className="form-actions">
            <button
              type="button"
              className="btn-secondary"
              onClick={onDone}
              disabled={submitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn-primary"
              disabled={submitting}
            >
              {submitting ? 'Creating officer...' : 'Create officer account'}
            </button>
          </div>
        </form>
      </div>
    </>
  );
};

export default ContractorSsos;
