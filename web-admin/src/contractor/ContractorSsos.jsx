import React, { useState } from 'react';
import {
  FiUserCheck, FiPlus, FiSearch, FiLock, FiRefreshCw, FiX, FiTrash2, FiMapPin
} from 'react-icons/fi';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { collection, doc, setDoc } from 'firebase/firestore';
import { secondaryAuth, db } from '../firebase';
import { useContractorSsos, useContractorSites, siteLabel, STATUS_COLOR, initialsOf, deleteSsoWithWorkers } from './contractorData';
import { PageHeader, SearchBox, Badge, EmptyState, ErrorState, Skeleton } from './ui';
import { logActivity } from '../activityLogger';
import PhoneInput, { formatPhoneWithCountryCode } from '../PhoneInput';
import ConfirmModal from '../ConfirmModal';

/**
 * Site Safety Officers directory for the contractor.
 * Includes Add Site Safety Officer modal, Contractor Site creation (isolated by contractorId),
 * and SSO Delete / Disable actions.
 */
const ContractorSsos = ({ contractorId, organizationId = null, organizationName = null, onOpen, onBack, backLabel = 'Dashboard' }) => {
  const { loading, error, ssos } = useContractorSsos(contractorId);
  const { sites } = useContractorSites(contractorId);
  const [queryText, setQueryText] = useState('');

  // Delete SSO confirmation modal state
  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // Add SSO modal state
  const [showAddModal, setShowAddModal] = useState(false);
  const [formData, setFormData] = useState({
    firstName: '',
    middleName: '',
    lastName: '',
    email: '',
    phone: '',
    siteId: '',
    tempPassword: ''
  });
  const [creatingNewSiteInline, setCreatingNewSiteInline] = useState(false);
  const [inlineSiteName, setInlineSiteName] = useState('');
  const [inlineSiteLocation, setInlineSiteLocation] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  // Standalone Create Site modal state
  const [showCreateSiteModal, setShowCreateSiteModal] = useState(false);
  const [newSiteName, setNewSiteName] = useState('');
  const [newSiteLocation, setNewSiteLocation] = useState('');
  const [savingSite, setSavingSite] = useState(false);
  const [siteError, setSiteError] = useState('');
  const [confirmDialog, setConfirmDialog] = useState(null);

  const hasExistingSsos = ssos.length > 0;

  const visible = ssos.filter((sso) => {
    const q = queryText.trim().toLowerCase();
    if (!q) return true;
    return (
      (sso.fullName || '').toLowerCase().includes(q) ||
      (sso.email || '').toLowerCase().includes(q)
    );
  });

  const openAddSsoModal = () => {
    setFormData({
      firstName: '',
      middleName: '',
      lastName: '',
      email: '',
      phone: '',
      siteId: sites[0]?.id || '',
      tempPassword: ''
    });
    setCreatingNewSiteInline(sites.length === 0);
    setInlineSiteName('');
    setInlineSiteLocation('');
    setErrorMsg('');
    setShowAddModal(true);
  };

  const handleGeneratePassword = () => {
    const randomNum = Math.floor(1000 + Math.random() * 9000);
    setFormData((prev) => ({ ...prev, tempPassword: `SSO-2026-${randomNum}` }));
  };

  const createContractorSiteDoc = async (nameRaw, locationRaw) => {
    const cleanName = nameRaw.trim();
    const cleanLocation = locationRaw.trim();
    const slug = cleanName.toUpperCase().replace(/[^A-Z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 16) || 'SITE';
    const siteDocRef = doc(collection(db, 'projects'));
    const siteId = `${slug}-${siteDocRef.id.slice(0, 5).toUpperCase()}`;
    await setDoc(doc(db, 'projects', siteId), {
      projectId: siteId,
      name: cleanName,
      location: cleanLocation,
      contractorId: contractorId,
      organizationId: organizationId || null,
      organizationName: organizationName || null,
      active: true,
      createdAt: new Date()
    });
    await logActivity(`Contractor created project site: ${cleanName} (${siteId})`);
    return siteId;
  };

  const handleCreateStandaloneSite = (e) => {
    e.preventDefault();
    setSiteError('');
    if (!newSiteName.trim()) {
      setSiteError('Site name is required.');
      return;
    }
    setConfirmDialog({
      title: 'Confirm Create Project Site',
      message: `Create project site "${newSiteName.trim()}"${newSiteLocation.trim() ? ` (${newSiteLocation.trim()})` : ''}?`,
      confirmLabel: 'Create Site',
      variant: 'primary',
      onConfirm: async () => {
        setConfirmDialog(null);
        setSavingSite(true);
        try {
          await createContractorSiteDoc(newSiteName, newSiteLocation);
          setNewSiteName('');
          setNewSiteLocation('');
          setShowCreateSiteModal(false);
          setConfirmDialog({
            title: 'Project Site Created',
            message: 'Project site created successfully and is now available for your Site Safety Officers and workers.',
            variant: 'success',
            noticeOnly: true,
          });
        } catch (err) {
          setSiteError(err.message || 'Failed to create project site.');
        } finally {
          setSavingSite(false);
        }
      },
    });
  };

  const handleCreateSso = (e) => {
    e.preventDefault();
    setErrorMsg('');

    if (!formData.firstName.trim() || !formData.lastName.trim() || !formData.email.trim() || !formData.tempPassword) {
      setErrorMsg('Please fill in all required fields and provide a temporary password.');
      return;
    }
    if (creatingNewSiteInline && !inlineSiteName.trim() && !formData.siteId) {
      setErrorMsg('Please enter a new site name or choose an existing site.');
      return;
    }

    setConfirmDialog({
      title: 'Confirm Add Site Safety Officer',
      message: `Create Site Safety Officer account for ${formData.firstName.trim()} ${formData.lastName.trim()} (${formData.email.trim()})?`,
      confirmLabel: 'Add Site Safety Officer',
      variant: 'primary',
      onConfirm: async () => {
        setConfirmDialog(null);
        setSubmitting(true);
        try {
          let resolvedSiteId = formData.siteId.trim() || null;
          if (creatingNewSiteInline && inlineSiteName.trim()) {
            resolvedSiteId = await createContractorSiteDoc(inlineSiteName, inlineSiteLocation);
          }

          const userCredential = await createUserWithEmailAndPassword(
            secondaryAuth,
            formData.email.trim(),
            formData.tempPassword
          );
          const newUser = userCredential.user;

          await setDoc(doc(db, 'users', newUser.uid), {
            firstName: formData.firstName.trim(),
            middleName: (formData.middleName || '').trim(),
            lastName: formData.lastName.trim(),
            email: formData.email.trim(),
            phone: formatPhoneWithCountryCode(formData.phone),
            role: 'SSO',
            contractorId: contractorId,
            organizationId: organizationId || null,
            organizationName: organizationName || null,
            company: organizationName || null,
            companyName: organizationName || null,
            siteId: resolvedSiteId,
            active: true,
            status: 'Active',
            mustChangePassword: true,
            createdAt: new Date()
          });

          await secondaryAuth.signOut();
          logActivity(`Created Site Safety Officer: ${formData.firstName.trim()} ${formData.lastName.trim()} (${formData.email.trim()})`);
          setShowAddModal(false);
          setConfirmDialog({
            title: 'Site Safety Officer Added',
            message: `Site Safety Officer ${formData.firstName.trim()} ${formData.lastName.trim()} has been created successfully.`,
            variant: 'success',
            noticeOnly: true,
          });
        } catch (err) {
          console.error('Error creating SSO:', err);
          setErrorMsg(err.message || 'Failed to create officer account.');
        } finally {
          setSubmitting(false);
        }
      },
    });
  };

  const handleDeleteSso = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      const removedWorkers = await deleteSsoWithWorkers({
        contractorId,
        ssoUid: pendingDelete.uid,
        siteId: pendingDelete.siteId || null,
      });
      logActivity(
        `Deleted Site Safety Officer: ${pendingDelete.fullName || pendingDelete.email}` +
        (removedWorkers > 0 ? ` (and ${removedWorkers} assigned worker${removedWorkers === 1 ? '' : 's'})` : '')
      );
      setPendingDelete(null);
    } catch (err) {
      console.error('Error deleting SSO:', err);
      alert('Failed to delete officer: ' + err.message);
    } finally {
      setDeleting(false);
    }
  };

  const header = (
    <PageHeader
      breadcrumb={onBack ? [{ label: backLabel, onClick: onBack }, { label: 'Site Safety Officers' }] : null}
      title="Site Safety Officers"
      subtitle="Officers monitoring workers on your sites"
      actions={
        <div style={{ display: 'flex', gap: '0.6rem', flexWrap: 'wrap' }}>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => { setSiteError(''); setShowCreateSiteModal(true); }}
          >
            <FiMapPin /> Add Project Site
          </button>
          {hasExistingSsos && (
            <button id="ct-add-sso-btn" className="btn-primary" onClick={openAddSsoModal}>
              <FiPlus /> Add Site Safety Officer
            </button>
          )}
        </div>
      }
    />
  );

  if (loading) return (<>{header}<Skeleton rows={4} /></>);
  if (error) return (<>{header}<ErrorState message={error} /></>);

  return (
    <>
      {header}

      {/* Contractor's Isolated Project Sites Pill Strip */}
      {sites.length > 0 && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '1rem' }}>
          <span style={{ fontSize: '0.78rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
            Your Project Sites ({sites.length}):
          </span>
          {sites.map((s) => (
            <span
              key={s.id}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.35rem',
                padding: '0.25rem 0.65rem',
                borderRadius: '999px',
                fontSize: '0.78rem',
                fontWeight: 600,
                backgroundColor: '#fff7ed',
                color: '#ea580c',
                border: '1px solid #fed7aa'
              }}
            >
              <FiMapPin style={{ fontSize: '0.75rem' }} />
              {s.name}{s.location ? ` · ${s.location}` : ''}
            </span>
          ))}
        </div>
      )}

      {hasExistingSsos && (
        <div className="ct-toolbar">
          <SearchBox
            id="ct-sso-search"
            value={queryText}
            onChange={setQueryText}
            placeholder="Search officers by name or email"
          />
        </div>
      )}

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
              <button className="btn-primary" onClick={openAddSsoModal} style={{ marginTop: '1rem' }}>
                <FiPlus /> Add Site Safety Officer
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
                <span className="ct-row-sub">
                  {sso.email}{sso.phone ? ` · ${formatPhoneWithCountryCode(sso.phone)}` : ''}
                </span>
                <span className="ct-row-meta">
                  {siteLabel(sites, sso.siteId)}
                </span>
              </div>

              <Badge
                text={sso.active ? 'Active' : 'Disabled'}
                color={sso.active ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE}
              />

              <div style={{ display: 'flex', gap: '0.4rem' }} onClick={(e) => e.stopPropagation()}>
                <button
                  className="btn-secondary ct-disable-btn"
                  title="Delete Site Safety Officer"
                  style={{ color: '#dc2626', borderColor: '#fecaca' }}
                  onClick={() => setPendingDelete(sso)}
                >
                  <FiTrash2 /> Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Delete SSO Confirmation Dialog */}
      {pendingDelete && (
        <div className="modal-overlay" onClick={() => !deleting && setPendingDelete(null)}>
          <div className="modal-content ct-modal-sm" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Delete Site Safety Officer?</h2>
              <button
                className="close-btn"
                onClick={() => setPendingDelete(null)}
                aria-label="Close"
              >
                <FiX />
              </button>
            </div>
            <div className="modal-body ct-modal-body">
              <p className="ct-muted" style={{ lineHeight: 1.5 }}>
                Are you sure you want to permanently delete{' '}
                <strong>{pendingDelete.fullName || pendingDelete.email}</strong>? All workers assigned under this officer will also be deleted. This action cannot be undone.
              </p>
              <div className="form-actions" style={{ marginTop: '1.5rem' }}>
                <button
                  className="btn-secondary"
                  onClick={() => setPendingDelete(null)}
                  disabled={deleting}
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
                  onClick={handleDeleteSso}
                  disabled={deleting}
                >
                  {deleting ? 'Deleting...' : 'Delete Officer'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Create Project Site Modal */}
      {showCreateSiteModal && (
        <div className="modal-overlay" onClick={() => !savingSite && setShowCreateSiteModal(false)}>
          <div className="modal-content ct-modal-sm" style={{ maxWidth: '460px' }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Create Project Site</h2>
              <button
                className="close-btn"
                onClick={() => setShowCreateSiteModal(false)}
                aria-label="Close"
              >
                <FiX />
              </button>
            </div>
            <div className="modal-body ct-modal-body">
              <p className="ct-muted" style={{ marginBottom: '1rem' }}>
                Create a project site for your organization. Only your Site Safety Officers and workers can be assigned to it.
              </p>
              <form onSubmit={handleCreateStandaloneSite}>
                <div className="form-group-custom" style={{ marginBottom: '0.9rem' }}>
                  <label className="form-label">Site Name *</label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="e.g. North Tower Site A"
                    value={newSiteName}
                    onChange={(e) => setNewSiteName(e.target.value)}
                    required
                  />
                </div>
                <div className="form-group-custom" style={{ marginBottom: '1rem' }}>
                  <label className="form-label">Location / Address</label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="e.g. Makati CBD, Metro Manila"
                    value={newSiteLocation}
                    onChange={(e) => setNewSiteLocation(e.target.value)}
                  />
                </div>
                {siteError && (
                  <div className="error-message" style={{ textAlign: 'left', marginBottom: '1rem' }}>{siteError}</div>
                )}
                <div className="form-actions">
                  <button type="button" className="btn-secondary" onClick={() => setShowCreateSiteModal(false)} disabled={savingSite}>
                    Cancel
                  </button>
                  <button type="submit" className="btn-primary" disabled={savingSite}>
                    {savingSite ? 'Creating...' : 'Create Site'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Add Site Safety Officer Modal / Pop-up */}
      {showAddModal && (
        <div className="modal-overlay" onClick={() => !submitting && setShowAddModal(false)}>
          <div className="modal-content" style={{ maxWidth: '480px', width: '95%', maxHeight: '90vh', overflowY: 'auto' }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Add Site Safety Officer</h2>
              <button
                className="close-btn"
                onClick={() => setShowAddModal(false)}
                aria-label="Close"
              >
                <FiX />
              </button>
            </div>
            <div className="modal-body ct-modal-body">
              <form onSubmit={handleCreateSso}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem', marginBottom: '1rem' }}>
                  <div className="form-group-custom">
                    <label className="form-label">First Name *</label>
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
                    <label className="form-label">Middle Name (Optional)</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="Optional"
                      value={formData.middleName}
                      onChange={(e) => setFormData({ ...formData, middleName: e.target.value })}
                    />
                  </div>

                  <div className="form-group-custom">
                    <label className="form-label">Last Name *</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. Vance"
                      value={formData.lastName}
                      onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                      required
                    />
                  </div>

                  <div className="form-group-custom">
                    <label className="form-label">Phone Number</label>
                    <PhoneInput
                      value={formData.phone}
                      onChange={(phone) => setFormData({ ...formData, phone })}
                    />
                  </div>

                  <div className="form-group-custom">
                    <label className="form-label">Email Address *</label>
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
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                      <label className="form-label" style={{ margin: 0 }}>Project Site</label>
                      <button
                        type="button"
                        onClick={() => setCreatingNewSiteInline(!creatingNewSiteInline)}
                        style={{ background: 'none', border: 'none', color: '#ea580c', fontSize: '0.78rem', fontWeight: 700, cursor: 'pointer' }}
                      >
                        {creatingNewSiteInline ? 'Choose Existing Site' : '+ Create New Site'}
                      </button>
                    </div>
                    {!creatingNewSiteInline ? (
                      <select
                        className="form-input"
                        value={formData.siteId}
                        onChange={(e) => setFormData({ ...formData, siteId: e.target.value })}
                      >
                        <option value="">Select a project site...</option>
                        {sites.map((s) => (
                          <option key={s.id} value={s.id}>
                            {s.name}{s.location ? ` — ${s.location}` : ''}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.6rem', backgroundColor: '#fff7ed', padding: '0.75rem', borderRadius: '8px', border: '1px solid #fed7aa' }}>
                        <div>
                          <label className="form-label" style={{ fontSize: '0.72rem' }}>New Site Name *</label>
                          <input
                            type="text"
                            className="form-input"
                            placeholder="e.g. Site Alpha"
                            value={inlineSiteName}
                            onChange={(e) => setInlineSiteName(e.target.value)}
                          />
                        </div>
                        <div>
                          <label className="form-label" style={{ fontSize: '0.72rem' }}>Location</label>
                          <input
                            type="text"
                            className="form-input"
                            placeholder="e.g. Taguig City"
                            value={inlineSiteLocation}
                            onChange={(e) => setInlineSiteLocation(e.target.value)}
                          />
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="form-group-custom">
                    <label className="form-label">Temporary Password *</label>
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
                    <span className="input-hint" style={{ margin: '0.35rem 0 0' }}>
                      The officer will be prompted to change this temporary password on first sign-in.
                    </span>
                  </div>
                </div>

                {errorMsg && (
                  <div className="error-message" style={{ textAlign: 'left', marginBottom: '1rem' }}>
                    {errorMsg}
                  </div>
                )}

                <div className="form-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => setShowAddModal(false)}
                    disabled={submitting}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={submitting}
                  >
                    {submitting ? 'Creating officer...' : 'Add Site Safety Officer'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      <ConfirmModal
        open={Boolean(confirmDialog)}
        title={confirmDialog?.title}
        message={confirmDialog?.message}
        confirmLabel={confirmDialog?.confirmLabel}
        variant={confirmDialog?.variant}
        noticeOnly={confirmDialog?.noticeOnly}
        onConfirm={confirmDialog?.onConfirm}
        onCancel={() => setConfirmDialog(null)}
      />
    </>
  );
};

export const AddSso = ContractorSsos;
export default ContractorSsos;
