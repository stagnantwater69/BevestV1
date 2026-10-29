import React, { useState, useEffect } from 'react';
import { 
  FiPlus, FiEdit2, FiSlash, FiChevronLeft, FiChevronRight, 
  FiCheckCircle, FiLock, FiRefreshCw, FiSearch, FiX, FiUserCheck, FiUserX,
  FiUser, FiMail, FiPhone, FiAlertCircle
} from 'react-icons/fi';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { doc, setDoc, updateDoc, collection, getDocs } from 'firebase/firestore';
import { secondaryAuth, db } from './firebase';
import { getContractorSubscription } from './subscriptionPlans';
import { logActivity } from './activityLogger';
import PhoneInput, { formatPhoneWithCountryCode } from './PhoneInput';

const UserManagement = () => {
  const [view, setView] = useState('list'); // 'list' | 'add'
  const [accountStatusTab, setAccountStatusTab] = useState('active'); // 'active' | 'disabled'

  // Add Contractor form state - NO subscription fields (admin only provisions account credentials)
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    phone: '',
    email: '',
    password: '',
    role: 'Contractor'
  });
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const [users, setUsers] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [sortOrder, setSortOrder] = useState('asc');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  // Edit user profile details modal state
  const [editingUser, setEditingUser] = useState(null);
  const [editFirstName, setEditFirstName] = useState('');
  const [editLastName, setEditLastName] = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [editEmail, setEditEmail] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);

  // Disable account confirmation modal state
  const [disablingUser, setDisablingUser] = useState(null);
  const [disablingLoading, setDisablingLoading] = useState(false);

  const fetchUsers = async () => {
    try {
      const querySnapshot = await getDocs(collection(db, "users"));
      const usersData = [];
      querySnapshot.forEach((docSnap) => {
        usersData.push({ id: docSnap.id, ...docSnap.data() });
      });
      setUsers(usersData);
      try {
        const directory = JSON.parse(localStorage.getItem('bevest_user_directory') || '{}');
        usersData.forEach((u) => {
          if (u.email) {
            directory[u.email.toLowerCase().trim()] = {
              phone: u.phone && u.phone !== 'Not set' ? u.phone : null,
              role: u.role,
              firstName: u.firstName,
              lastName: u.lastName
            };
          }
        });
        localStorage.setItem('bevest_user_directory', JSON.stringify(directory));
      } catch (e) {
        // ignore cache write errors
      }
    } catch (err) {
      console.error("Error fetching users:", err);
    }
  };

  useEffect(() => {
    if (view === 'list') {
      fetchUsers();
    }
  }, [view]);

  // Compute Active and Disabled counts
  const activeUsersCount = users.filter(u => u.active !== false && u.status !== 'Disabled').length;
  const disabledUsersCount = users.filter(u => u.active === false || u.status === 'Disabled').length;

  // Filter users based on Active/Disabled tab, Search query, and Role
  const filteredUsers = users
    .filter(u => {
      const isAccountDisabled = u.active === false || u.status === 'Disabled';
      if (accountStatusTab === 'active' && isAccountDisabled) return false;
      if (accountStatusTab === 'disabled' && !isAccountDisabled) return false;

      const matchesSearch = `${u.firstName || ''} ${u.lastName || ''}`.toLowerCase().includes(searchQuery.toLowerCase()) || 
                            (u.email || '').toLowerCase().includes(searchQuery.toLowerCase());
      const matchesRole = roleFilter ? (u.role || '').toLowerCase() === roleFilter.toLowerCase() : true;
      return matchesSearch && matchesRole;
    })
    .sort((a, b) => {
      const nameA = `${a.firstName || ''} ${a.lastName || ''}`.toLowerCase();
      const nameB = `${b.firstName || ''} ${b.lastName || ''}`.toLowerCase();
      if (sortOrder === 'asc') {
        return nameA.localeCompare(nameB);
      } else {
        return nameB.localeCompare(nameA);
      }
    });

  const totalPages = Math.ceil(filteredUsers.length / itemsPerPage) || 1;
  const paginatedUsers = filteredUsers.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  const handleGeneratePassword = () => {
    const randomNum = Math.floor(1000 + Math.random() * 9000);
    setFormData({ ...formData, password: `GID-2026-${randomNum}` });
  };

  // 1. Create Contractor Account without Subscription Plan (Contractor chooses plan on their own portal)
  const handleCreateUser = async () => {
    if (!formData.firstName.trim() || !formData.lastName.trim() || !formData.email.trim() || !formData.phone.trim() || !formData.password) {
      setErrorMsg("Please fill in all required fields (including phone number) and generate a password.");
      return;
    }
    
    setLoading(true);
    setErrorMsg('');
    try {
      // Create user in Firebase Auth using the secondary instance (keeps admin logged in)
      const userCredential = await createUserWithEmailAndPassword(secondaryAuth, formData.email.trim(), formData.password);
      const newUser = userCredential.user;
      
      const now = new Date();

      // Contractors start with no subscription plan
      await setDoc(doc(db, "users", newUser.uid), {
        firstName: formData.firstName.trim(),
        lastName: formData.lastName.trim(),
        phone: formatPhoneWithCountryCode(formData.phone),
        email: formData.email.trim(),
        role: (formData.role || 'Contractor').toUpperCase() === 'DEVELOPER' ? 'DEVELOPER' : 'CONTRACTOR',
        active: true,
        status: 'Active',
        createdAt: now,
        subscription: null // Starts with NO subscription plan
      });
      
      // Sign out the secondary auth instance immediately
      await secondaryAuth.signOut();

      // Save activity log in database
      await logActivity(`Contractor account created for ${formData.email.trim()}`);
      
      // Reset form and go back to list
      setFormData({
        firstName: '',
        lastName: '',
        phone: '',
        email: '',
        password: '',
        role: 'Contractor'
      });
      setView('list');
      alert("Contractor account created successfully! The contractor will start with no subscription plan and can choose their package upon sign-in.");
    } catch (error) {
      console.error("Error creating contractor:", error);
      setErrorMsg(error.message);
    } finally {
      setLoading(false);
    }
  };

  // 2. Open Edit Profile Modal
  const handleOpenEditUser = (userToEdit) => {
    setEditingUser(userToEdit);
    setEditFirstName(userToEdit.firstName || '');
    setEditLastName(userToEdit.lastName || '');
    setEditPhone(userToEdit.phone || '');
    setEditEmail(userToEdit.email || '');
  };

  // Save updated user profile details in Firestore
  const handleSaveUserEdit = async () => {
    if (!editingUser) return;
    if (!editFirstName.trim() || !editLastName.trim()) {
      alert("First name and last name are required.");
      return;
    }

    setSavingEdit(true);
    try {
      const userDocRef = doc(db, "users", editingUser.id);
      await updateDoc(userDocRef, {
        firstName: editFirstName.trim(),
        lastName: editLastName.trim(),
        phone: formatPhoneWithCountryCode(editPhone),
        email: editEmail.trim()
      });

      await logActivity(`Updated profile details for ${editFirstName.trim()} ${editLastName.trim()} (${editEmail.trim()})`);
      setEditingUser(null);
      await fetchUsers();
      alert("User profile updated successfully!");
    } catch (err) {
      console.error("Error updating user profile:", err);
      alert("Failed to update profile: " + err.message);
    } finally {
      setSavingEdit(false);
    }
  };

  // 3. Disable User Account (Account remains safely in the database)
  const handleConfirmDisable = async () => {
    if (!disablingUser) return;
    setDisablingLoading(true);
    try {
      const userDocRef = doc(db, "users", disablingUser.id);
      await updateDoc(userDocRef, {
        active: false,
        status: 'Disabled',
        disabledAt: new Date()
      });

      await logActivity(`Disabled account for ${disablingUser.firstName || ''} ${disablingUser.lastName || ''} (${disablingUser.email})`);
      setDisablingUser(null);
      await fetchUsers();
      alert(`Account for ${disablingUser.email} has been disabled. It is preserved in the database and moved to the Disabled Accounts list.`);
    } catch (err) {
      console.error("Error disabling user:", err);
      alert("Failed to disable user: " + err.message);
    } finally {
      setDisablingLoading(false);
    }
  };

  // 4. Enable / Reactivate User Account
  const handleEnableUser = async (userToEnable) => {
    const confirm = window.confirm(`Re-activate access for ${userToEnable.firstName || ''} ${userToEnable.lastName || ''} (${userToEnable.email})?`);
    if (!confirm) return;

    try {
      const userDocRef = doc(db, "users", userToEnable.id);
      await updateDoc(userDocRef, {
        active: true,
        status: 'Active'
      });

      await logActivity(`Re-enabled account for ${userToEnable.firstName || ''} ${userToEnable.lastName || ''} (${userToEnable.email})`);
      await fetchUsers();
      alert(`Account for ${userToEnable.email} has been re-enabled.`);
    } catch (err) {
      console.error("Error enabling user:", err);
      alert("Failed to enable user: " + err.message);
    }
  };

  // View: Add Contractor (Creation Only, No Subscription Provisioning)
  if (view === 'add') {
    return (
      <div className="dashboard-content">
        <div className="breadcrumb">
          <span className="bc-link" onClick={() => setView('list')}>User Management</span> 
          <span className="bc-separator">&gt;</span> 
          <span className="bc-current">Add Contractor</span>
        </div>
        
        <div className="page-header" style={{ marginBottom: '2rem' }}>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: '0.25rem' }}>Onboard New Contractor</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>
            Create the contractor user account. The contractor will start with no subscription plan and can select their tier upon signing in.
          </p>
        </div>

        <div className="add-user-card">
          <div className="form-section-container" style={{ display: 'flex', flexWrap: 'wrap', gap: '2rem' }}>
            <div className="form-section-info" style={{ flex: '1 1 250px' }}>
              <h3>Contractor Identity</h3>
              <p>Core contact details and initial authentication credentials for the contractor.</p>
            </div>
            
            <div className="form-grid um-form-grid" style={{ flex: '2 1 400px' }}>
              <div className="form-group-custom">
                <label className="form-label">FIRST NAME *</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="e.g. Marcus" 
                  value={formData.firstName}
                  onChange={(e) => setFormData({...formData, firstName: e.target.value})}
                  required
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">PHONE NUMBER *</label>
                <PhoneInput 
                  value={formData.phone}
                  onChange={(phone) => setFormData({ ...formData, phone })}
                  required
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">LAST NAME *</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="e.g. Thorne" 
                  value={formData.lastName}
                  onChange={(e) => setFormData({...formData, lastName: e.target.value})}
                  required
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">EMAIL ADDRESS *</label>
                <input 
                  type="email" 
                  className="form-input" 
                  placeholder="contractor@buildcorp.com" 
                  value={formData.email}
                  onChange={(e) => setFormData({...formData, email: e.target.value})}
                  required
                />
              </div>

              {/* Read-only assigned role */}
              <div className="form-group-custom">
                <label className="form-label">ASSIGN ROLE</label>
                <select 
                  className="form-input" 
                  value={formData.role || 'Contractor'}
                  onChange={(e) => setFormData({...formData, role: e.target.value})}
                  style={{ cursor: 'pointer', backgroundColor: '#ffffff' }}
                >
                  <option value="Contractor">Contractor (Site Contractor)</option>
                  <option value="Developer">Developer (Hardware & Subscriptions)</option>
                </select>
              </div>

              {/* Password Generator */}
              <div className="form-group-custom">
                <label className="form-label">TEMPORARY PASSWORD *</label>
                <div className="password-input-group">
                  <input 
                    type="text" 
                    className="form-input password-field" 
                    placeholder="Click generate to create..." 
                    value={formData.password}
                    readOnly
                  />
                  <button className="icon-btn lock-btn" type="button" onClick={handleGeneratePassword} title="Generate Password">
                    {formData.password ? <FiLock /> : <FiRefreshCw />}
                  </button>
                </div>
              </div>
            </div>
          </div>
          
          <hr className="form-divider" />
          
          {errorMsg && (
            <div className="error-message" style={{ marginBottom: '1rem', textAlign: 'left' }}>
              {errorMsg}
            </div>
          )}

          <div className="form-actions">
            <button className="btn-secondary" onClick={() => setView('list')} disabled={loading}>
              Cancel
            </button>
            <button className="btn-primary" onClick={handleCreateUser} disabled={loading}>
              {loading ? 'CREATING ACCOUNT...' : 'CREATE CONTRACTOR ACCOUNT'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  // View: User Management List
  return (
    <div className="dashboard-content">
      <div className="page-header-flex" style={{ flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1>User Management</h1>
          <p>Manage system credentials, edit personnel details, and oversee active or disabled accounts.</p>
        </div>
        <button className="btn-primary" onClick={() => setView('add')}>
          <FiPlus style={{ fontSize: '1.2rem' }} /> Add Contractor
        </button>
      </div>

      {/* Summary Stat Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem', marginBottom: '1.5rem' }}>
        <div className="um-summary-card" style={{ margin: 0 }}>
          <div className="stat-title">ACTIVE USERS</div>
          <div className="stat-value" style={{ color: '#16a34a' }}>{activeUsersCount}</div>
        </div>

        <div className="um-summary-card" style={{ margin: 0, backgroundColor: '#ffffff', border: '1px solid #cbd5e1' }}>
          <div className="stat-title" style={{ color: '#64748b' }}>DISABLED ACCOUNTS</div>
          <div className="stat-value" style={{ color: '#dc2626' }}>{disabledUsersCount}</div>
        </div>
      </div>

      {/* Main Table Container with Active / Disabled Tabs */}
      <div className="um-table-container">
        
        {/* Active vs Disabled Segmented View Switcher */}
        <div style={{ display: 'flex', borderBottom: '1px solid #e2e8f0', padding: '0.75rem 1.25rem 0 1.25rem', gap: '1rem', backgroundColor: '#f8fafc' }}>
          <button
            type="button"
            onClick={() => { setAccountStatusTab('active'); setCurrentPage(1); }}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.75rem 1.25rem',
              border: 'none',
              borderBottom: accountStatusTab === 'active' ? '3px solid var(--primary-color, #ea580c)' : '3px solid transparent',
              backgroundColor: 'transparent',
              color: accountStatusTab === 'active' ? 'var(--primary-color, #ea580c)' : '#64748b',
              fontWeight: 700,
              fontSize: '0.95rem',
              cursor: 'pointer',
              transition: 'all 0.2s'
            }}
          >
            <FiUserCheck style={{ fontSize: '1.1rem' }} />
            Active Accounts ({activeUsersCount})
          </button>

          <button
            type="button"
            onClick={() => { setAccountStatusTab('disabled'); setCurrentPage(1); }}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.75rem 1.25rem',
              border: 'none',
              borderBottom: accountStatusTab === 'disabled' ? '3px solid #dc2626' : '3px solid transparent',
              backgroundColor: 'transparent',
              color: accountStatusTab === 'disabled' ? '#dc2626' : '#64748b',
              fontWeight: 700,
              fontSize: '0.95rem',
              cursor: 'pointer',
              transition: 'all 0.2s'
            }}
          >
            <FiUserX style={{ fontSize: '1.1rem' }} />
            Disabled Accounts ({disabledUsersCount})
          </button>
        </div>

        {/* Filter Toolbar */}
        <div className="filter-bar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', padding: '1.25rem' }}>
          <div className="search-bar um-search-bar" style={{ flex: '1 1 300px', backgroundColor: '#fff', border: '1px solid var(--border-color)', margin: 0 }}>
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder={`Search ${accountStatusTab} users by name or email...`}
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
            />
          </div>
          <div style={{ display: 'flex', gap: '1rem' }}>
            <select className="filter-btn" style={{ appearance: 'auto' }} value={roleFilter} onChange={(e) => { setRoleFilter(e.target.value); setCurrentPage(1); }}>
              <option value="">All Roles</option>
              <option value="Contractor">Contractor</option>
              <option value="SSO">Site Safety Officer</option>
              <option value="Admin">Admin</option>
            </select>
          </div>
        </div>

        {/* Users Table */}
        <div className="table-responsive-wrapper" style={{ overflowX: 'auto', width: '100%' }}>
          <table className="um-table" style={{ minWidth: '850px' }}>
            <thead>
              <tr>
                <th onClick={() => setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')} style={{ cursor: 'pointer', userSelect: 'none', width: '28%' }}>
                  NAME {sortOrder === 'asc' ? '↑' : '↓'}
                </th>
                <th style={{ width: '14%' }}>ROLE</th>
                <th style={{ width: '20%' }}>SUBSCRIPTION</th>
                <th style={{ width: '14%' }}>STATUS</th>
                <th style={{ width: '14%' }}>PHONE</th>
                <th className="align-right" style={{ width: '10%' }}>ACTIONS</th>
              </tr>
            </thead>
            <tbody>
              {paginatedUsers.length === 0 ? (
                <tr>
                  <td colSpan="6" style={{ textAlign: 'center', padding: '3rem 1rem', color: '#94a3b8' }}>
                    {accountStatusTab === 'disabled' 
                      ? 'No disabled accounts found. Disabled user accounts will appear here and remain in the database.' 
                      : 'No active users found matching your search.'}
                  </td>
                </tr>
              ) : (
                paginatedUsers.map((user) => {
                  const initials = ((user.firstName?.[0] || '') + (user.lastName?.[0] || '')).toUpperCase() || 'U';
                  const isContractor = (user.role || '').toLowerCase() === 'contractor';
                  const sub = isContractor ? getContractorSubscription(user) : null;
                  const isAccountDisabled = user.active === false || user.status === 'Disabled';

                  return (
                    <tr key={user.id}>
                      <td>
                        <div className="user-cell">
                          <div 
                            className="user-avatar" 
                            style={{ 
                              backgroundColor: isAccountDisabled ? '#f1f5f9' : '#fed7aa',
                              color: isAccountDisabled ? '#94a3b8' : '#ea580c'
                            }}
                          >
                            {initials}
                          </div>
                          <div className="user-info">
                            <span className="user-name" style={{ color: isAccountDisabled ? '#64748b' : '#0f172a' }}>
                              {user.firstName} {user.lastName}
                            </span>
                            <span style={{ fontSize: '0.75rem', color: '#64748b' }}>{user.email}</span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className="badge role-badge">{user.role}</span>
                      </td>
                      <td>
                        {isContractor ? (
                          sub && sub.hasPlan ? (
                            <div className="table-plan-badge-wrapper">
                              <span className={`table-plan-badge plan-${sub.planId.toLowerCase()}`}>
                                {sub.planName}
                              </span>
                              <span className="table-plan-limits-text">
                                {sub.vestLimit} vests • {sub.ssoLimit} SSOs
                              </span>
                            </div>
                          ) : (
                            <span 
                              className="table-plan-badge" 
                              style={{ backgroundColor: '#f1f5f9', color: '#64748b', border: '1px solid #cbd5e1' }}
                            >
                              No Plan
                            </span>
                          )
                        ) : (
                          <span style={{ color: '#94a3b8' }}>--</span>
                        )}
                      </td>
                      <td>
                        <div className="status-indicator">
                          <span 
                            className="status-dot" 
                            style={{ 
                              backgroundColor: isAccountDisabled ? '#dc2626' : '#16a34a' 
                            }}
                          />
                          <span style={{ color: isAccountDisabled ? '#dc2626' : '#16a34a', fontWeight: 600 }}>
                            {isAccountDisabled ? 'Disabled' : (user.status || 'Active')}
                          </span>
                        </div>
                      </td>
                      <td>
                        <span style={{ fontSize: '0.85rem', color: '#475569' }}>
                          {user.phone || '--'}
                        </span>
                      </td>
                      <td className="align-right">
                        <div className="action-buttons">
                          {/* Edit user details button */}
                          <button 
                            className="action-btn" 
                            title="Edit User Profile" 
                            onClick={() => handleOpenEditUser(user)}
                          >
                            <FiEdit2 />
                          </button>

                          {/* Disable / Enable Button */}
                          {isAccountDisabled ? (
                            <button 
                              className="action-btn" 
                              title="Re-enable Account" 
                              onClick={() => handleEnableUser(user)}
                              style={{ color: '#16a34a' }}
                            >
                              <FiCheckCircle />
                            </button>
                          ) : (
                            <button 
                              className="action-btn" 
                              title="Disable Account" 
                              onClick={() => setDisablingUser(user)}
                              style={{ color: '#dc2626' }}
                            >
                              <FiSlash />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="pagination" style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem', padding: '1rem 1.25rem' }}>
          <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
            Showing {filteredUsers.length === 0 ? 0 : ((currentPage - 1) * itemsPerPage) + 1} to {Math.min(currentPage * itemsPerPage, filteredUsers.length)} of {filteredUsers.length} {accountStatusTab} users
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <button className="page-btn" disabled={currentPage === 1} onClick={() => setCurrentPage(p => Math.max(1, p - 1))}><FiChevronLeft /></button>
            <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>Page {currentPage} of {totalPages}</span>
            <button className="page-btn" disabled={currentPage === totalPages} onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}><FiChevronRight /></button>
          </div>
        </div>
      </div>

      {/* Edit User Profile Details Modal */}
      {editingUser && (
        <div className="modal-backdrop" onClick={() => !savingEdit && setEditingUser(null)}>
          <div className="modal-content sub-modal" onClick={e => e.stopPropagation()} style={{ maxWidth: '480px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <div style={{ padding: '0.45rem', backgroundColor: '#fff7ed', color: '#ea580c', borderRadius: '8px' }}>
                  <FiEdit2 />
                </div>
                <h3 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0 }}>
                  Edit User Profile
                </h3>
              </div>
              <button 
                type="button" 
                onClick={() => setEditingUser(null)} 
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.25rem', color: '#64748b' }}
              >
                <FiX />
              </button>
            </div>

            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.25rem' }}>
              Update contact information and personal details for <strong>{editingUser.email}</strong>.
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.5rem' }}>
              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>FIRST NAME *</label>
                <input 
                  type="text" 
                  className="form-input" 
                  value={editFirstName} 
                  onChange={(e) => setEditFirstName(e.target.value)}
                  style={{ width: '100%' }}
                />
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>LAST NAME *</label>
                <input 
                  type="text" 
                  className="form-input" 
                  value={editLastName} 
                  onChange={(e) => setEditLastName(e.target.value)}
                  style={{ width: '100%' }}
                />
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>PHONE NUMBER</label>
                <PhoneInput 
                  value={editPhone} 
                  onChange={(phone) => setEditPhone(phone)}
                />
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>EMAIL ADDRESS</label>
                <input 
                  type="email" 
                  className="form-input" 
                  value={editEmail} 
                  onChange={(e) => setEditEmail(e.target.value)}
                  style={{ width: '100%' }}
                />
              </div>
            </div>

            <div className="modal-actions">
              <button 
                type="button" 
                className="btn-secondary" 
                onClick={() => setEditingUser(null)} 
                disabled={savingEdit}
              >
                Cancel
              </button>
              <button 
                type="button" 
                className="btn-primary" 
                onClick={handleSaveUserEdit} 
                disabled={savingEdit}
              >
                {savingEdit ? 'Saving...' : 'Save Profile Changes'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Disable Account Confirmation Modal (Preserves Account in Database) */}
      {disablingUser && (
        <div className="modal-backdrop" onClick={() => !disablingLoading && setDisablingUser(null)}>
          <div className="modal-content sub-modal" onClick={e => e.stopPropagation()} style={{ maxWidth: '440px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', marginBottom: '0.75rem' }}>
              <div style={{ padding: '0.5rem', backgroundColor: '#fef2f2', color: '#dc2626', borderRadius: '8px' }}>
                <FiSlash style={{ fontSize: '1.25rem' }} />
              </div>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0, color: '#0f172a' }}>
                Disable User Account
              </h3>
            </div>

            <p style={{ color: '#475569', fontSize: '0.875rem', lineHeight: 1.5, marginBottom: '1rem' }}>
              Are you sure you want to disable the account for{' '}
              <strong>{disablingUser.firstName} {disablingUser.lastName}</strong> ({disablingUser.email})?
            </p>

            <div style={{ backgroundColor: '#fffbeb', border: '1px solid #fde68a', borderRadius: '8px', padding: '0.75rem 1rem', marginBottom: '1.5rem', fontSize: '0.8rem', color: '#92400e' }}>
              ✓ <strong>Database Integrity:</strong> This account will NOT be deleted. All records, historical incident assignments, and site records will remain preserved in the database.
              <br /><br />
              The user will be immediately blocked from signing in until re-enabled by an administrator.
            </div>

            <div className="modal-actions">
              <button 
                type="button" 
                className="btn-secondary" 
                onClick={() => setDisablingUser(null)} 
                disabled={disablingLoading}
              >
                Cancel
              </button>
              <button 
                type="button" 
                className="btn-primary" 
                onClick={handleConfirmDisable} 
                disabled={disablingLoading}
                style={{ backgroundColor: '#dc2626', borderColor: '#dc2626' }}
              >
                {disablingLoading ? 'Disabling...' : 'Disable Account'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserManagement;
