import React, { useMemo, useState } from 'react';
import { FiEdit2, FiMapPin, FiUsers, FiMail, FiPhone } from 'react-icons/fi';
import { doc, updateDoc } from 'firebase/firestore';
import { db } from '../firebase';
import {
  useContractorSsos, useContractorSites, siteLabel, STATUS_COLOR, initialsOf,
} from './contractorData';
import { PageHeader, Badge, EmptyState, ErrorState, Skeleton } from './ui';
import PhoneInput, { formatPhoneWithCountryCode } from '../PhoneInput';

/**
 * Site Safety Officer profile for the contractor.
 * Shows the officer's details, the site they are assigned to, and the workers
 * assigned to that officer at that site, and lets the contractor edit the
 * officer or assign them to a different site.
 */
const SsoProfile = ({ contractorId, uid, workers, onBack }) => {
  const { loading, error, ssos } = useContractorSsos(contractorId);
  const { sites } = useContractorSites(contractorId);
  const sso = ssos.find((s) => s.uid === uid);

  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState(null);
  const [isCustomSite, setIsCustomSite] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  // Merged sites list: from projects collection, worker siteIds, and current SSO siteId
  const allSites = useMemo(() => {
    const map = new Map();
    sites.forEach((s) => map.set(s.id, { id: s.id, name: s.name, location: s.location || '' }));
    workers.forEach((w) => {
      if (w.siteId && !map.has(w.siteId)) {
        map.set(w.siteId, { id: w.siteId, name: `Site ${w.siteId}`, location: '' });
      }
    });
    if (sso?.siteId && !map.has(sso.siteId)) {
      map.set(sso.siteId, { id: sso.siteId, name: `Site ${sso.siteId}`, location: '' });
    }
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
  }, [sites, workers, sso]);

  // Workers belong to a site; the officer looks after all workers at their assigned site.
  const siteWorkers = useMemo(() => {
    if (!sso?.siteId) return [];
    return workers
      .filter((w) => w.siteId === sso.siteId)
      .sort((a, b) => a.fullName.localeCompare(b.fullName));
  }, [workers, sso]);

  const breadcrumb = [{ label: 'Site Safety Officers', onClick: onBack }, { label: sso?.fullName || 'Profile' }];

  if (loading) return (<><PageHeader breadcrumb={breadcrumb} title="Officer profile" /><Skeleton rows={3} /></>);
  if (error) return (<><PageHeader breadcrumb={breadcrumb} title="Officer profile" /><ErrorState message={error} /></>);
  if (!sso) {
    return (
      <>
        <PageHeader breadcrumb={breadcrumb} title="Officer profile" />
        <EmptyState title="Officer not found" message="This officer may have been removed." />
      </>
    );
  }

  const site = allSites.find((s) => s.id === sso.siteId);

  const startEdit = () => {
    const currentSiteId = sso.siteId || '';
    const existsInList = !currentSiteId || allSites.some((s) => s.id === currentSiteId);
    setForm({
      firstName: sso.firstName || '',
      lastName: sso.lastName || '',
      phone: sso.phone || '',
      siteId: currentSiteId,
    });
    setIsCustomSite(!existsInList && !!currentSiteId);
    setErrorMsg('');
    setEditing(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!form.firstName.trim() || !form.lastName.trim()) {
      setErrorMsg('First and last name are required.');
      return;
    }
    setSaving(true);
    setErrorMsg('');
    try {
      await updateDoc(doc(db, 'users', sso.uid), {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        phone: formatPhoneWithCountryCode(form.phone),
        siteId: form.siteId ? form.siteId.trim() : null,
      });
      setEditing(false);
    } catch (err) {
      console.error('Error updating SSO:', err);
      setErrorMsg(err.message || 'Failed to save changes.');
    } finally {
      setSaving(false);
    }
  };

  const header = (
    <PageHeader
      breadcrumb={breadcrumb}
      title={sso.fullName || sso.email}
      subtitle="Site Safety Officer"
      actions={!editing && (
        <button id="ct-sso-edit-btn" className="btn-primary" onClick={startEdit}>
          <FiEdit2 /> Edit profile
        </button>
      )}
    />
  );

  if (editing) {
    return (
      <>
        {header}
        <div className="add-user-card" style={{ maxWidth: '800px', margin: '0 auto' }}>
          <form onSubmit={handleSave}>
            <div className="form-grid">
              <div className="form-group-custom">
                <label className="form-label" htmlFor="ct-sso-first">First name *</label>
                <input
                  id="ct-sso-first"
                  className="form-input"
                  value={form.firstName}
                  onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                  required
                />
              </div>
              <div className="form-group-custom">
                <label className="form-label" htmlFor="ct-sso-last">Last name *</label>
                <input
                  id="ct-sso-last"
                  className="form-input"
                  value={form.lastName}
                  onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                  required
                />
              </div>
              <div className="form-group-custom">
                <label className="form-label" htmlFor="ct-sso-email">Email address</label>
                <input id="ct-sso-email" className="form-input" value={sso.email} disabled />
                <span className="input-hint" style={{ margin: '0.25rem 0 0' }}>
                  The sign-in email cannot be changed
                </span>
              </div>
              <div className="form-group-custom">
                <label className="form-label" htmlFor="ct-sso-phone">Phone number</label>
                <PhoneInput
                  id="ct-sso-phone"
                  value={form.phone}
                  onChange={(phone) => setForm({ ...form, phone })}
                />
              </div>
              <div className="form-group-custom" style={{ gridColumn: '1 / -1' }}>
                <label className="form-label" htmlFor="ct-sso-site">Assigned Site</label>
                <select
                  id="ct-sso-site"
                  className="form-input"
                  value={isCustomSite ? '__CUSTOM__' : form.siteId}
                  onChange={(e) => {
                    if (e.target.value === '__CUSTOM__') {
                      setIsCustomSite(true);
                      setForm({ ...form, siteId: '' });
                    } else {
                      setIsCustomSite(false);
                      setForm({ ...form, siteId: e.target.value });
                    }
                  }}
                >
                  <option value="">No site assigned</option>
                  {allSites.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}{s.location ? ` — ${s.location}` : ''}
                    </option>
                  ))}
                  <option value="__CUSTOM__">+ Enter custom site ID...</option>
                </select>
                {isCustomSite && (
                  <input
                    type="text"
                    id="ct-sso-custom-site"
                    className="form-input"
                    style={{ marginTop: '0.5rem' }}
                    placeholder="Enter custom Site ID (e.g. Site-C)"
                    value={form.siteId}
                    onChange={(e) => setForm({ ...form, siteId: e.target.value })}
                    autoFocus
                  />
                )}
                <span className="input-hint" style={{ margin: '0.25rem 0 0' }}>
                  Reassigning the officer to another site changes which workers they monitor.
                </span>
              </div>
            </div>

            {errorMsg && (
              <div className="error-message" style={{ textAlign: 'left', margin: '1rem 0' }}>{errorMsg}</div>
            )}

            <div className="form-actions" style={{ marginTop: '1.5rem' }}>
              <button type="button" className="btn-secondary" onClick={() => setEditing(false)} disabled={saving}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Saving...' : 'Save changes'}
              </button>
            </div>
          </form>
        </div>
      </>
    );
  }

  return (
    <>
      {header}

      {/* Officer Summary Card */}
      <div className="ct-card ct-sso-summary">
        <div
          className="ct-avatar lg"
          style={{ '--accent': sso.active ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE }}
        >
          {initialsOf(sso.fullName || sso.email, 'O')}
          <span className="ct-avatar-status" />
        </div>
        <div className="ct-sso-details">
          <div className="ct-sso-detail"><FiMail /> {sso.email || '—'}</div>
          <div className="ct-sso-detail"><FiPhone /> {sso.phone || 'No phone number'}</div>
          <div className="ct-sso-detail">
            <FiMapPin />
            <span id="ct-sso-site-name">
              <strong>Assigned Site:</strong> {sso.siteId ? `${siteLabel(allSites, sso.siteId)}${site?.location ? ` (${site.location})` : ''}` : 'No site assigned'}
            </span>
          </div>
        </div>
        <Badge
          text={sso.active ? 'Active' : 'Disabled'}
          color={sso.active ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE}
        />
      </div>

      {/* Assigned Workers Section */}
      <div className="ct-section-header">
        <div>
          <h2>Assigned workers</h2>
          <p id="ct-sso-worker-count">
            {sso.siteId
              ? `${siteWorkers.length} ${siteWorkers.length === 1 ? 'worker' : 'workers'} assigned to this officer at ${siteLabel(allSites, sso.siteId)}`
              : 'Assign a site to this officer to see monitored workers'}
          </p>
        </div>
      </div>

      {siteWorkers.length === 0 ? (
        <EmptyState
          icon={FiUsers}
          title={sso.siteId ? 'No workers on this site yet' : 'No site assigned'}
          message={sso.siteId
            ? 'Workers added to this site will appear here.'
            : 'Click "Edit profile" above to assign this officer to a site.'}
        />
      ) : (
        <div className="ct-list">
          {siteWorkers.map((w) => (
            <div key={w.id} className="ct-row" id={`ct-sso-worker-${w.id}`}>
              <div
                className="ct-avatar"
                style={{ '--accent': w.active ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE }}
              >
                {initialsOf(w.fullName, 'W')}
              </div>
              <div className="ct-row-main">
                <span className="ct-row-title">{w.fullName}</span>
                <span className="ct-row-sub">Worker ID: {w.id}{w.phone ? ` · ${w.phone}` : ''}</span>
              </div>
              <Badge
                text={w.active ? 'Active' : 'Inactive'}
                color={w.active ? STATUS_COLOR.NORMAL : STATUS_COLOR.OFFLINE}
              />
            </div>
          ))}
        </div>
      )}
    </>
  );
};

export default SsoProfile;

