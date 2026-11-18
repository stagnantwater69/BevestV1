import React, { useState, useEffect } from 'react';
import { FiMail, FiPhone, FiEdit2, FiLock, FiChevronRight, FiArrowLeft, FiShield, FiBriefcase } from 'react-icons/fi';
import PhoneInput, { formatPhoneWithCountryCode } from './PhoneInput';
import { validatePhoneNumber, formatProfileName } from './validation';
import { doc, updateDoc, getDoc } from 'firebase/firestore';
import { db } from './firebase';

const AdminProfile = ({ user }) => {
  const [showEdit, setShowEdit] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [editError, setEditError] = useState('');
  const [editSubmitted, setEditSubmitted] = useState(false);
  const [profile, setProfile] = useState({
    firstName: user?.profile?.firstName || 'Web',
    middleName: user?.profile?.middleName || '',
    lastName: user?.profile?.lastName || 'Admin',
    suffix: user?.profile?.suffix || '',
    email: user?.email || user?.profile?.email || 'testwebadmin@bevest.com',
    phone: (user?.profile?.phone && user.profile.phone !== 'Not set' ? formatPhoneWithCountryCode(user.profile.phone) : '') || 'Not set',
    company: user?.profile?.company || user?.profile?.companyName || 'Bevest Enterprise'
  });

  useEffect(() => {
    if (user?.uid) {
      getDoc(doc(db, 'users', user.uid)).then(snap => {
        if (snap.exists()) {
          const d = snap.data();
          setProfile(prev => ({
            ...prev,
            firstName: d.firstName || prev.firstName,
            middleName: d.middleName != null ? d.middleName : prev.middleName,
            lastName: d.lastName || prev.lastName,
            suffix: d.suffix != null ? d.suffix : prev.suffix,
            email: d.email || prev.email,
            phone: (d.phone && d.phone !== 'Not set' ? formatPhoneWithCountryCode(d.phone) : '') || prev.phone,
            company: d.company || d.companyName || prev.company
          }));
        }
      }).catch(err => console.error('Error fetching admin profile:', err));
    }
  }, [user]);

  const [editFirstName, setEditFirstName] = useState(profile.firstName);
  const [editMiddleName, setEditMiddleName] = useState(profile.middleName || '');
  const [editLastName, setEditLastName] = useState(profile.lastName);
  const [editSuffix, setEditSuffix] = useState(profile.suffix || '');
  const [editPhone, setEditPhone] = useState(profile.phone !== 'Not set' ? profile.phone : '');

  const initials = `${(profile.firstName || 'W')[0]}${(profile.lastName || 'A')[0]}`.toUpperCase();

  const handleOpenEdit = () => {
    setEditFirstName(profile.firstName);
    setEditMiddleName(profile.middleName || '');
    setEditLastName(profile.lastName);
    setEditSuffix(profile.suffix || '');
    setEditPhone(profile.phone !== 'Not set' ? profile.phone : '');
    setEditError('');
    setEditSubmitted(false);
    setShowEdit(true);
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setEditSubmitted(true);
    if (!editFirstName.trim() || !editLastName.trim()) {
      setEditError('First name and last name are required.');
      return;
    }
    const phoneErr = validatePhoneNumber(editPhone, false);
    if (phoneErr) {
      setEditError(phoneErr);
      return;
    }
    setEditError('');
    const formattedPhone = formatPhoneWithCountryCode(editPhone);
    setProfile(prev => ({
      ...prev,
      firstName: editFirstName.trim(),
      middleName: editMiddleName.trim(),
      lastName: editLastName.trim(),
      suffix: editSuffix.trim(),
      phone: formattedPhone || 'Not set'
    }));
    if (user?.uid) {
      try {
        await updateDoc(doc(db, 'users', user.uid), {
          firstName: editFirstName.trim(),
          middleName: editMiddleName.trim(),
          lastName: editLastName.trim(),
          suffix: editSuffix.trim(),
          phone: formattedPhone
        });
      } catch (err) {
        console.error('Error saving admin profile to database:', err);
      }
      try {
        const directory = JSON.parse(localStorage.getItem('bevest_user_directory') || '{}');
        if (profile.email) {
          directory[profile.email.toLowerCase().trim()] = {
            ...(directory[profile.email.toLowerCase().trim()] || {}),
            phone: formattedPhone || null,
            firstName: editFirstName.trim(),
            middleName: editMiddleName.trim(),
            lastName: editLastName.trim(),
            suffix: editSuffix.trim(),
            role: profile.role || 'ADMIN',
            company: profile.company || ''
          };
          localStorage.setItem('bevest_user_directory', JSON.stringify(directory));
        }
      } catch (e) {}
    }
    setShowEdit(false);
  };

  const handleUpdatePassword = (e) => {
    e.preventDefault();
    setShowPassword(false);
  };

  return (
    <div className="dashboard-content profile-page">
      <div className="profile-container">
        
        {/* Profile Header Card */}
        <div className="profile-header-card">
          <div className="profile-avatar">{initials}</div>
          <h2>{formatProfileName(profile)}</h2>
          <div className="admin-badge">
            <FiShield /> ADMINISTRATOR
          </div>
        </div>

        {/* Account Section */}
        <div className="profile-section">
          <div className="section-title">ACCOUNT</div>
          <div className="profile-list-card">
            <div className="profile-list-item">
              <FiBriefcase className="item-icon" />
              <div className="item-details">
                <span className="item-label">Construction Company</span>
                <span className="item-value" style={{ fontWeight: 600, color: 'var(--text-color, #0f172a)' }}>
                  {profile.company || 'Bevest Enterprise'}
                </span>
              </div>
            </div>
            <div className="profile-list-item">
              <FiMail className="item-icon" />
              <div className="item-details">
                <span className="item-label">Email</span>
                <span className="item-value">{profile.email}</span>
              </div>
            </div>
            <div className="profile-list-item">
              <FiPhone className="item-icon" />
              <div className="item-details">
                <span className="item-label">Phone</span>
                <span className="item-value">{profile.phone}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Settings Section */}
        <div className="profile-section">
          <div className="section-title">SETTINGS</div>
          <div className="profile-list-card">
            <div className="profile-list-item clickable" onClick={handleOpenEdit}>
              <div className="item-icon-bg"><FiEdit2 /></div>
              <div className="item-details">
                <span className="item-value">Edit profile</span>
                <span className="item-label">Name and phone number</span>
              </div>
              <FiChevronRight className="chevron" />
            </div>
            <div className="profile-list-item clickable" onClick={() => setShowPassword(true)}>
              <div className="item-icon-bg"><FiLock /></div>
              <div className="item-details">
                <span className="item-value">Change password</span>
                <span className="item-label">Update your sign-in password</span>
              </div>
              <FiChevronRight className="chevron" />
            </div>
          </div>
        </div>
      </div>

      {/* Modals */}
      {showEdit && (
        <div className="profile-modal-overlay">
          <div className="profile-modal-card">
            <div className="profile-modal-header">
              <button onClick={() => setShowEdit(false)} className="back-btn"><FiArrowLeft /></button>
              <h2>Edit profile</h2>
            </div>
            <div className="profile-modal-body">
              <div className="section-title">YOUR DETAILS</div>
              <form onSubmit={handleSaveProfile} className="profile-form" noValidate>
                
                <div className={`floating-input-group${editSubmitted && !editFirstName.trim() ? ' input-error' : ''}`}>
                  <input
                    type="text"
                    value={editFirstName}
                    onChange={(e) => setEditFirstName(e.target.value)}
                    required
                    placeholder=" "
                  />
                  <label>First name *</label>
                </div>

                <div className="floating-input-group">
                  <input
                    type="text"
                    value={editMiddleName}
                    onChange={(e) => setEditMiddleName(e.target.value)}
                    placeholder=" "
                  />
                  <label>Middle name (optional)</label>
                </div>
                
                <div className={`floating-input-group${editSubmitted && !editLastName.trim() ? ' input-error' : ''}`}>
                  <input
                    type="text"
                    value={editLastName}
                    onChange={(e) => setEditLastName(e.target.value)}
                    required
                    placeholder=" "
                  />
                  <label>Last name *</label>
                </div>

                <div className="floating-input-group">
                  <input
                    type="text"
                    value={editSuffix}
                    onChange={(e) => setEditSuffix(e.target.value)}
                    placeholder=" "
                  />
                  <label>Suffix (optional)</label>
                </div>
                
                <PhoneInput
                  variant="floating"
                  label="Phone number"
                  value={editPhone}
                  onChange={(phone) => { setEditPhone(phone); setEditError(''); }}
                  placeholder="900 000 0000"
                />

                {editError && (
                  <div className="error-message" style={{ textAlign: 'left', margin: '0.25rem 0' }}>{editError}</div>
                )}

                <button type="submit" className="btn-orange">Save changes</button>
              </form>
            </div>
          </div>
        </div>
      )}

      {showPassword && (
        <div className="profile-modal-overlay">
          <div className="profile-modal-card">
            <div className="profile-modal-header">
              <button onClick={() => setShowPassword(false)} className="back-btn"><FiArrowLeft /></button>
              <h2>Change password</h2>
            </div>
            <div className="profile-modal-body">
              <form onSubmit={handleUpdatePassword} className="profile-form">
                
                <div className="section-title">CONFIRM IT'S YOU</div>
                <div className="floating-input-group">
                  <input type="password" required placeholder=" " />
                  <label>Current password *</label>
                </div>
                
                <div className="section-title mt-2">NEW PASSWORD</div>
                <div className="floating-input-group">
                  <input type="password" required minLength={6} placeholder=" " />
                  <label>New password *</label>
                  <span className="input-hint">At least 6 characters</span>
                </div>
                
                <div className="floating-input-group">
                  <input type="password" required placeholder=" " />
                  <label>Confirm new password *</label>
                </div>
                
                <button type="submit" className="btn-orange">Update password</button>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminProfile;
