import React, { useEffect, useMemo, useState } from 'react';
import {
  FiMapPin, FiPlus, FiSearch, FiCalendar, FiClock, FiUsers,
  FiUserCheck, FiMail, FiPhone, FiChevronRight, FiX, FiEdit2, FiTrash2,
} from 'react-icons/fi';
import {
  useContractorSites,
  useContractorSsos,
  createContractorSite,
  updateContractorSite,
  deleteContractorSite,
  STATUS_COLOR,
  initialsOf,
} from './contractorData';
import { PageHeader, SearchBox, Badge, EmptyState, Skeleton } from './ui';
import AddressAutocomplete from '../AddressAutocomplete';
import ConfirmModal from '../ConfirmModal';
import { formatPhoneWithCountryCode } from '../PhoneInput';

const formatSiteDate = (raw) => {
  if (!raw) return 'Not set';
  const d = new Date(`${raw}T00:00:00`);
  if (Number.isNaN(d.getTime())) return raw;
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
};

const computeEstimatedEndDate = (startDate, durationMonths) => {
  if (!startDate || !durationMonths || durationMonths <= 0) return null;
  const d = new Date(`${startDate}T00:00:00`);
  if (Number.isNaN(d.getTime())) return null;
  d.setMonth(d.getMonth() + Number(durationMonths));
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
};

/**
 * Contractor Project Sites tab:
 * - Lists all project sites for this contractor
 * - Create Project Site modal (Project Name, Address, Start Date, Duration in Months)
 * - Clicking any Project Site opens its detail view with site information + assigned SSO(s)
 */
const ContractorSites = ({
  contractorId,
  organizationId = null,
  organizationName = null,
  workers = [],
  onOpenSso,
  resetSignal = 0,
}) => {
  const { loading, sites } = useContractorSites(contractorId);
  const { ssos } = useContractorSsos(contractorId);

  const [selectedSiteId, setSelectedSiteId] = useState(null);

  useEffect(() => {
    setSelectedSiteId(null);
  }, [resetSignal]);
  const [searchQuery, setSearchQuery] = useState('');

  // Create / Edit Project Site Modal state
  const [showSiteModal, setShowSiteModal] = useState(false);
  const [editingSite, setEditingSite] = useState(null);
  const [projectName, setProjectName] = useState('');
  const [address, setAddress] = useState('');
  const [startDate, setStartDate] = useState('');
  const [durationMonths, setDurationMonths] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [confirmDialog, setConfirmDialog] = useState(null);

  const selectedSite = useMemo(
    () => sites.find((s) => s.id === selectedSiteId) || null,
    [sites, selectedSiteId],
  );

  const ssosBySiteId = useMemo(() => {
    const map = new Map();
    ssos.forEach((sso) => {
      if (!sso.siteId) return;
      if (!map.has(sso.siteId)) map.set(sso.siteId, []);
      map.get(sso.siteId).push(sso);
    });
    return map;
  }, [ssos]);

  const visibleSites = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return sites;
    return sites.filter((s) =>
      (s.name || '').toLowerCase().includes(q)
      || (s.address || s.location || '').toLowerCase().includes(q)
      || (s.id || '').toLowerCase().includes(q)
    );
  }, [sites, searchQuery]);

  const openCreateModal = () => {
    setEditingSite(null);
    setProjectName('');
    setAddress('');
    setStartDate('');
    setDurationMonths('');
    setSubmitted(false);
    setErrorMsg('');
    setShowSiteModal(true);
  };

  const openEditModal = (site) => {
    if (!site) return;
    setEditingSite(site);
    setProjectName(site.name || '');
    setAddress(site.address || site.location || '');
    setStartDate(site.startDate || '');
    setDurationMonths(site.durationMonths != null ? String(site.durationMonths) : '');
    setSubmitted(false);
    setErrorMsg('');
    setShowSiteModal(true);
  };

  const handleSubmitSite = (e) => {
    e.preventDefault();
    setSubmitted(true);
    setErrorMsg('');

    const parsedDuration = Number(durationMonths);
    if (!projectName.trim() || !address.trim() || !startDate.trim() || !durationMonths.trim()) {
      setErrorMsg('Please fill in all required project site fields.');
      return;
    }
    if (!Number.isInteger(parsedDuration) || parsedDuration < 1) {
      setErrorMsg('Duration in months must be at least 1.');
      return;
    }

    const cleanName = projectName.trim();
    const isEditing = Boolean(editingSite);

    setConfirmDialog({
      title: isEditing ? 'Confirm Save Project Site' : 'Confirm Create Project Site',
      message: isEditing
        ? `Save changes to "${cleanName}"?`
        : `Do you want to create ${cleanName}?`,
      confirmLabel: isEditing ? 'Save Changes' : 'Create Site',
      variant: 'primary',
      onConfirm: async () => {
        setSaving(true);
        try {
          if (isEditing) {
            await updateContractorSite({
              siteId: editingSite.id,
              organizationId,
              name: projectName,
              address,
              startDate,
              durationMonths: parsedDuration,
            });
          } else {
            await createContractorSite({
              contractorId,
              organizationId,
              organizationName,
              name: projectName,
              address,
              startDate,
              durationMonths: parsedDuration,
            });
          }
          setShowSiteModal(false);
          setEditingSite(null);
          setConfirmDialog({
            title: isEditing ? 'Project Site Updated' : 'Project Site Created',
            message: isEditing
              ? `Project site "${cleanName}" has been updated successfully.`
              : `Project site "${cleanName}" has been created successfully.`,
            variant: 'success',
            noticeOnly: true,
          });
        } catch (err) {
          console.error(`Error ${isEditing ? 'updating' : 'creating'} project site:`, err);
          setConfirmDialog(null);
          setErrorMsg(err.message || `Failed to ${isEditing ? 'update' : 'create'} project site.`);
        } finally {
          setSaving(false);
        }
      },
    });
  };

  const requestDeleteSite = (site) => {
    if (!site) return;
    const assignedSsoCount = (ssosBySiteId.get(site.id) || []).length;
    const assignedWorkerCount = workers.filter((w) => w.siteId === site.id).length;
    const extraNotes = [];
    if (assignedSsoCount > 0) {
      extraNotes.push(`${assignedSsoCount} assigned Site Safety Officer${assignedSsoCount === 1 ? '' : 's'} will be unassigned from this site.`);
    }
    if (assignedWorkerCount > 0) {
      extraNotes.push(`${assignedWorkerCount} assigned worker${assignedWorkerCount === 1 ? '' : 's'} will be unassigned from this site.`);
    }

    setConfirmDialog({
      title: 'Delete Project Site?',
      message:
        `Are you sure you want to permanently delete "${site.name}"?`
        + (extraNotes.length > 0 ? `\n\n${extraNotes.join(' ')}` : '')
        + '\n\nThis action cannot be undone.',
      confirmLabel: 'Delete Site',
      variant: 'danger',
      onConfirm: async () => {
        setSaving(true);
        try {
          await deleteContractorSite({
            siteId: site.id,
            siteName: site.name,
            contractorId,
            organizationId,
          });
          if (selectedSiteId === site.id) {
            setSelectedSiteId(null);
          }
          setConfirmDialog({
            title: 'Project Site Deleted',
            message: `Project site "${site.name}" has been deleted.`,
            variant: 'success',
            noticeOnly: true,
          });
        } catch (err) {
          console.error('Error deleting project site:', err);
          setConfirmDialog({
            title: 'Delete Failed',
            message: err.message || 'Failed to delete project site.',
            variant: 'danger',
            noticeOnly: true,
          });
        } finally {
          setSaving(false);
        }
      },
    });
  };

  const renderDetailView = () => {
    const assignedSsos = ssosBySiteId.get(selectedSite.id) || [];
    const siteWorkers = workers.filter((w) => w.siteId === selectedSite.id);
    const estEnd = computeEstimatedEndDate(selectedSite.startDate, selectedSite.durationMonths);

    return (
      <>
        <PageHeader
          breadcrumb={[
            { label: 'Project Sites', onClick: () => setSelectedSiteId(null) },
            { label: selectedSite.name },
          ]}
          title={selectedSite.name}
          subtitle={`Site ID: ${selectedSite.id}`}
          actions={
            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn-primary"
                onClick={() => openEditModal(selectedSite)}
              >
                <FiEdit2 /> Edit Site
              </button>
              <button
                type="button"
                className="btn-secondary"
                style={{ color: '#dc2626', borderColor: '#fecaca' }}
                onClick={() => requestDeleteSite(selectedSite)}
              >
                <FiTrash2 /> Delete Site
              </button>
            </div>
          }
        />

        {/* Project Site Information Card */}
        <div className="ct-card" style={{ marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '1rem' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700, color: '#0f172a' }}>
                Project Site Information
              </h3>
              <p className="ct-muted" style={{ margin: '0.2rem 0 0', fontSize: '0.82rem' }}>
                Overview of the project site schedule and deployment details
              </p>
            </div>
            <Badge text="Active Site" color={STATUS_COLOR.NORMAL} />
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
              gap: '1rem',
              paddingTop: '0.75rem',
              borderTop: '1px solid #f1f5f9',
            }}
          >
            <div>
              <div style={{ fontSize: '0.72rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                Project Name
              </div>
              <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#0f172a' }}>
                {selectedSite.name}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '0.72rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                Address
              </div>
              <div style={{ fontSize: '0.92rem', color: '#0f172a', display: 'flex', alignItems: 'flex-start', gap: '0.35rem' }}>
                <FiMapPin style={{ color: '#ea580c', flexShrink: 0, marginTop: '0.2rem' }} />
                <span>{selectedSite.address || selectedSite.location || 'No address provided'}</span>
              </div>
            </div>

            <div>
              <div style={{ fontSize: '0.72rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                Start Date
              </div>
              <div style={{ fontSize: '0.92rem', color: '#0f172a', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                <FiCalendar style={{ color: '#ea580c', flexShrink: 0 }} />
                <span>{formatSiteDate(selectedSite.startDate)}</span>
              </div>
            </div>

            <div>
              <div style={{ fontSize: '0.72rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                Duration in Months
              </div>
              <div style={{ fontSize: '0.92rem', color: '#0f172a', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                <FiClock style={{ color: '#ea580c', flexShrink: 0 }} />
                <span>
                  {selectedSite.durationMonths
                    ? `${selectedSite.durationMonths} ${selectedSite.durationMonths === 1 ? 'month' : 'months'}${estEnd ? ` (ends ~${estEnd})` : ''}`
                    : 'Not set'}
                </span>
              </div>
            </div>

            <div>
              <div style={{ fontSize: '0.72rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                Assigned Workers
              </div>
              <div style={{ fontSize: '0.92rem', color: '#0f172a', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                <FiUsers style={{ color: '#ea580c', flexShrink: 0 }} />
                <span>{siteWorkers.length} {siteWorkers.length === 1 ? 'worker' : 'workers'}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Assigned Site Safety Officer(s) Section */}
        <div className="ct-section-header">
          <div>
            <h2>Assigned Site Safety Officer{assignedSsos.length === 1 ? '' : 's'}</h2>
            <p>
              {assignedSsos.length === 0
                ? 'No Site Safety Officer is assigned to this project site yet'
                : `${assignedSsos.length} officer${assignedSsos.length === 1 ? '' : 's'} managing worker safety at ${selectedSite.name}`}
            </p>
          </div>
        </div>

        {assignedSsos.length === 0 ? (
          <EmptyState
            icon={FiUserCheck}
            title="No Site Safety Officer assigned"
            message="Assign a Site Safety Officer to this project site from the Site Safety Officers tab to display them here."
          />
        ) : (
          <div className="ct-list">
            {assignedSsos.map((sso) => (
              <div
                key={sso.uid}
                className="ct-row ct-row-clickable"
                role="button"
                tabIndex={0}
                onClick={() => onOpenSso && onOpenSso(sso.uid)}
                onKeyDown={(e) => { if (e.key === 'Enter' && onOpenSso) onOpenSso(sso.uid); }}
              >
                <div
                  className="ct-avatar"
                  style={{ '--accent': sso.active ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE }}
                >
                  {initialsOf(sso.fullName || sso.email, 'O')}
                  <span className="ct-avatar-status" />
                </div>

                <div className="ct-row-main">
                  <span className="ct-row-title">{sso.fullName || sso.email}</span>
                  <span className="ct-row-sub" style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.25rem' }}>
                      <FiMail /> {sso.email || '—'}
                    </span>
                    {sso.phone && (
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.25rem' }}>
                        <FiPhone /> {formatPhoneWithCountryCode(sso.phone)}
                      </span>
                    )}
                  </span>
                </div>

                <Badge
                  text={sso.active ? 'Active' : 'Disabled'}
                  color={sso.active ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE}
                />

                {onOpenSso && <FiChevronRight className="ct-row-chevron" />}
              </div>
            ))}
          </div>
        )}
      </>
    );
  };

  const renderListView = () => (
    <>
      <PageHeader
        title="Project Sites"
        subtitle="Manage your construction project sites and view assigned Site Safety Officers"
        actions={sites.length > 0 && (
          <button type="button" className="btn-primary" onClick={openCreateModal}>
            <FiPlus /> Create Project Site
          </button>
        )}
      />

      {loading ? (
        <Skeleton rows={4} />
      ) : (
        <>
          {sites.length > 0 && (
            <div className="ct-toolbar">
              <SearchBox
                id="ct-site-search"
                value={searchQuery}
                onChange={setSearchQuery}
                placeholder="Search project name or address..."
              />
            </div>
          )}

          {visibleSites.length === 0 ? (
            <EmptyState
              icon={searchQuery ? FiSearch : FiMapPin}
              title={searchQuery ? 'No matching project sites' : 'No project sites yet'}
              message={
                searchQuery
                  ? `Nothing matches "${searchQuery}".`
                  : 'Create your first project site with its address, start date, and duration.'
              }
              action={
                !searchQuery && (
                  <button type="button" className="btn-primary" onClick={openCreateModal} style={{ marginTop: '1rem' }}>
                    <FiPlus /> Create Project Site
                  </button>
                )
              }
            />
          ) : (
            <div className="ct-list">
              {visibleSites.map((site) => {
                const siteSsos = ssosBySiteId.get(site.id) || [];
                const ssoSummary = siteSsos.length === 0
                  ? 'Unassigned'
                  : siteSsos.map((s) => s.fullName || s.email).join(', ');
                return (
                  <div
                    key={site.id}
                    className="ct-row ct-row-clickable"
                    role="button"
                    tabIndex={0}
                    onClick={() => setSelectedSiteId(site.id)}
                    onKeyDown={(e) => { if (e.key === 'Enter') setSelectedSiteId(site.id); }}
                  >
                    <div className="ct-row-icon" style={{ '--accent': 'var(--primary-color, #ea580c)' }}>
                      <FiMapPin />
                    </div>

                    <div className="ct-row-main">
                      <span className="ct-row-title">{site.name}</span>
                      <span className="ct-row-sub">
                        {site.address || site.location || 'No address provided'}
                      </span>
                      <span className="ct-row-meta">
                        Start: {formatSiteDate(site.startDate)}
                        {site.durationMonths ? ` · ${site.durationMonths} ${site.durationMonths === 1 ? 'month' : 'months'}` : ''}
                        {` · SSO: ${ssoSummary}`}
                      </span>
                    </div>

                    <Badge
                      text={`${siteSsos.length} ${siteSsos.length === 1 ? 'SSO' : 'SSOs'}`}
                      color={siteSsos.length > 0 ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE}
                    />

                    <div style={{ display: 'flex', gap: '0.4rem' }} onClick={(e) => e.stopPropagation()}>
                      <button
                        type="button"
                        className="btn-secondary"
                        title="Edit Project Site"
                        onClick={() => openEditModal(site)}
                      >
                        <FiEdit2 /> Edit
                      </button>
                      <button
                        type="button"
                        className="btn-secondary ct-disable-btn"
                        title="Delete Project Site"
                        style={{ color: '#dc2626', borderColor: '#fecaca' }}
                        onClick={() => requestDeleteSite(site)}
                      >
                        <FiTrash2 /> Delete
                      </button>
                    </div>

                    <FiChevronRight className="ct-row-chevron" />
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}
    </>
  );

  return (
    <>
      {selectedSite ? renderDetailView() : renderListView()}

      {/* Create / Edit Project Site Modal */}
      {showSiteModal && (
        <div className="modal-overlay" onClick={() => !saving && setShowSiteModal(false)}>
          <div className="modal-content ct-modal-sm" style={{ maxWidth: '480px', width: '95%' }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editingSite ? 'Edit Project Site' : 'Create Project Site'}</h2>
              <button
                type="button"
                className="close-btn"
                onClick={() => !saving && setShowSiteModal(false)}
                aria-label="Close"
              >
                <FiX />
              </button>
            </div>
            <div className="modal-body ct-modal-body">
              <form onSubmit={handleSubmitSite} noValidate>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem', marginBottom: '1rem' }}>
                  <div className="form-group-custom">
                    <label className="form-label">Project Name *</label>
                    <input
                      type="text"
                      className={`form-input${submitted && !projectName.trim() ? ' input-error' : ''}`}
                      placeholder="e.g. North Tower Phase 1"
                      value={projectName}
                      onChange={(e) => setProjectName(e.target.value)}
                      required
                    />
                  </div>

                  <div className="form-group-custom">
                    <label className="form-label">Address *</label>
                    <AddressAutocomplete
                      className="form-input"
                      placeholder="e.g. Ayala Avenue, Makati City"
                      value={address}
                      onChange={(val) => setAddress(val)}
                      hasError={submitted && !address.trim()}
                      required
                    />
                  </div>

                  <div className="form-group-custom">
                    <label className="form-label">Start Date *</label>
                    <input
                      type="date"
                      className={`form-input${submitted && !startDate.trim() ? ' input-error' : ''}`}
                      value={startDate}
                      onChange={(e) => setStartDate(e.target.value)}
                      required
                    />
                  </div>

                  <div className="form-group-custom">
                    <label className="form-label">Duration in Months *</label>
                    <input
                      type="text"
                      inputMode="numeric"
                      className={`form-input${submitted && (!durationMonths.trim() || !Number.isInteger(Number(durationMonths)) || Number(durationMonths) < 1) ? ' input-error' : ''}`}
                      placeholder="e.g. 12"
                      value={durationMonths}
                      onChange={(e) => setDurationMonths(e.target.value.replace(/\D/g, ''))}
                      required
                    />
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
                    onClick={() => { setShowSiteModal(false); setEditingSite(null); }}
                    disabled={saving}
                  >
                    Cancel
                  </button>
                  <button type="submit" className="btn-primary" disabled={saving}>
                    {saving
                      ? (editingSite ? 'Saving...' : 'Creating...')
                      : (editingSite ? 'Save Changes' : 'Create Project Site')}
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
        loading={saving}
        noticeOnly={confirmDialog?.noticeOnly}
        onConfirm={confirmDialog?.onConfirm}
        onCancel={() => !saving && setConfirmDialog(null)}
      />
    </>
  );
};

export default ContractorSites;
