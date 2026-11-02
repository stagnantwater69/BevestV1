import React, { useState } from 'react';
import {
  FiMail, FiPhone, FiEdit2, FiLock, FiChevronRight, FiArrowLeft, FiShield, FiBriefcase
} from 'react-icons/fi';
import { doc, updateDoc } from 'firebase/firestore';
import { updatePassword, EmailAuthProvider, reauthenticateWithCredential } from 'firebase/auth';
import { auth, db } from '../firebase';
import { initialsOf } from './contractorData';
import PhoneInput, { formatPhoneWithCountryCode } from '../PhoneInput';

const ContractorProfile = ({ user, profile, onProfileChange }) => {
  const [showEdit, setShowEdit] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const [editFirstName, setEditFirstName] = useState(profile.firstName || '');
  const [editLastName, setEditLastName] = useState(profile.lastName || '');
  const [editPhone, setEditPhone] = useState(profile.phone || '');

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [savingProfile, setSavingProfile] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);
  const [profileError, setProfileError] = useState('');
  const [passwordError, setPasswordError] = useState('');

  const fullName = `${profile.firstName || ''} ${profile.lastName || ''}`.trim() || user.email;
  const initials = initialsOf(fullName, 'C');

  const handleOpenEdit = () => {
    setEditFirstName(profile.firstName || '');
    setEditLastName(profile.lastName || '');
    setEditPhone(profile.phone || '');
    setProfileError('');
    setShowEdit(true);
  };

  const handleOpenPassword = () => {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setPasswordError('');
    setShowPassword(true);
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setProfileError('');
    setSavingProfile(true);

    try {
      const userRef = doc(db, 'users', user.uid);
      const updated = {
        firstName: editFirstName.trim(),
        lastName: editLastName.trim(),
        phone: formatPhoneWithCountryCode(editPhone)
      };
      await updateDoc(userRef, updated);

      const merged = { ...profile, ...updated };
      if (onProfileChange) onProfileChange(merged);
      setShowEdit(false);
    } catch (err) {
      console.error('Error saving profile:', err);
      setProfileError(err.message || 'Failed to update profile.');
    } finally {
      setSavingProfile(false);
    }
  };

  const handleUpdatePassword = async (e) => {
    e.preventDefault();
    setPasswordError('');

    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match.');
      return;
    }

    if (newPassword.length < 6) {
      setPasswordError('Password must be at least 6 characters.');
      return;
    }

    setSavingPassword(true);
    try {
      const currentUser = auth.currentUser;
      if (!currentUser || !currentUser.email) {
        throw new Error('Not signed in.');
      }

      // Re-authenticate user before updating password
      const credential = EmailAuthProvider.credential(currentUser.email, currentPassword);
      await reauthenticateWithCredential(currentUser, credential);
      await updatePassword(currentUser, newPassword);

      alert('Password updated successfully!');
      setShowPassword(false);
    } catch (err) {
      console.error('Error updating password:', err);
      if (err.code === 'auth/wrong-password' || err.code === 'auth/invalid-credential') {
        setPasswordError('Incorrect current password.');
      } else {
        setPasswordError(err.message || 'Failed to update password.');
      }
    } finally {
      setSavingPassword(false);
    }
  };

  return (
    <div className="profile-page">
      <div className="profile-container">
        {/* Profile Header Card */}
        <div className="profile-header-card">
          <div className="profile-avatar">{initials}</div>
          <h2>{fullName}</h2>
          <div className="admin-badge">
            <FiShield /> CONTRACTOR
          </div>
        </div>

        {/* Account Section */}
        <div className="profile-section">
          <div className="section-title">ACCOUNT</div>
          <div className="profile-list-card">
            <div className="profile-list-item">
              <FiMail className="item-icon" />
              <div className="item-details">
                <span className="item-label">Email</span>
                <span className="item-value">{user.email || profile.email}</span>
              </div>
            </div>
            <div className="profile-list-item">
              <FiPhone className="item-icon" />
              <div className="item-details">
                <span className="item-label">Phone</span>
                <span className="item-value">{profile.phone || 'Not set'}</span>
              </div>
            </div>
            <div className="profile-list-item">
              <FiBriefcase className="item-icon" />
              <div className="item-details">
                <span className="item-label">Contractor ID</span>
                <span className="item-value">{profile.contractorId || user.uid}</span>
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
            <div className="profile-list-item clickable" onClick={handleOpenPassword}>
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

      {/* Edit Profile Modal */}
      {showEdit && (
        <div className="profile-modal-overlay">
          <div className="profile-modal-card">
            <div className="profile-modal-header">
              <button onClick={() => setShowEdit(false)} className="back-btn" aria-label="Back">
                <FiArrowLeft />
              </button>
              <h2>Edit profile</h2>
            </div>
            <div className="profile-modal-body">
              <div className="section-title">YOUR DETAILS</div>
              {profileError && (
                <div className="error-message" style={{ textAlign: 'left', margin: '0 0 1rem' }}>
                  {profileError}
                </div>
              )}
              <form onSubmit={handleSaveProfile} className="profile-form">
                <div className="floating-input-group">
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
                    value={editLastName}
                    onChange={(e) => setEditLastName(e.target.value)}
                    required
                    placeholder=" "
                  />
                  <label>Last name *</label>
                </div>

                <PhoneInput
                  variant="floating"
                  label="Phone number"
                  value={editPhone}
                  onChange={(phone) => setEditPhone(phone)}
                  placeholder="900 000 0000"
                />

                <button type="submit" className="btn-orange" disabled={savingProfile}>
                  {savingProfile ? 'Saving...' : 'Save changes'}
                </button>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Change Password Modal */}
      {showPassword && (
        <div className="profile-modal-overlay">
          <div className="profile-modal-card">
            <div className="profile-modal-header">
              <button onClick={() => setShowPassword(false)} className="back-btn" aria-label="Back">
                <FiArrowLeft />
              </button>
              <h2>Change password</h2>
            </div>
            <div className="profile-modal-body">
              {passwordError && (
                <div className="error-message" style={{ textAlign: 'left', margin: '0 0 1rem' }}>
                  {passwordError}
                </div>
              )}
              <form onSubmit={handleUpdatePassword} className="profile-form">
                <div className="section-title">CONFIRM IT'S YOU</div>
                <div className="floating-input-group">
                  <input
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    required
                    placeholder=" "
                  />
                  <label>Current password *</label>
                </div>

                <div className="section-title mt-2">NEW PASSWORD</div>
                <div className="floating-input-group">
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                    minLength={6}
                    placeholder=" "
                  />
                  <label>New password *</label>
                  <span className="input-hint">At least 6 characters</span>
                </div>

                <div className="floating-input-group">
                  <input
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    placeholder=" "
                  />
                  <label>Confirm new password *</label>
                </div>

                <button type="submit" className="btn-orange" disabled={savingPassword}>
                  {savingPassword ? 'Updating...' : 'Update password'}
                </button>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ContractorProfile;
