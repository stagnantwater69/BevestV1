import React, { useState } from 'react';
import { FiMail, FiPhone, FiEdit2, FiLock, FiChevronRight, FiArrowLeft, FiShield } from 'react-icons/fi';

const AdminProfile = () => {
  const [showEdit, setShowEdit] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [profile, setProfile] = useState({
    firstName: 'Web',
    lastName: 'Admin',
    email: 'testwebadmin@bevest.com',
    phone: 'Not set'
  });

  const initials = `${profile.firstName[0]}${profile.lastName[0]}`.toUpperCase();

  const handleSaveProfile = (e) => {
    e.preventDefault();
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
          <h2>{profile.firstName} {profile.lastName}</h2>
          <div className="admin-badge">
            <FiShield /> ADMINISTRATOR
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
            <div className="profile-list-item clickable" onClick={() => setShowEdit(true)}>
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
              <form onSubmit={handleSaveProfile} className="profile-form">
                
                <div className="floating-input-group">
                  <input type="text" defaultValue={profile.firstName} required placeholder=" " />
                  <label>First name *</label>
                </div>
                
                <div className="floating-input-group">
                  <input type="text" defaultValue={profile.lastName} required placeholder=" " />
                  <label>Last name *</label>
                </div>
                
                <div className="floating-input-group">
                  <input type="text" defaultValue={profile.phone !== 'Not set' ? profile.phone : ''} placeholder=" " />
                  <label>Phone number</label>
                </div>
                
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
