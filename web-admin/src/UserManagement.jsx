import React, { useState, useEffect } from 'react';
import { 
  FiPlus, FiEdit2, FiSlash, FiChevronLeft, FiChevronRight, 
  FiCheckCircle, FiLock, FiRefreshCw, FiSearch, FiX, FiUserCheck, FiUserX,
  FiUser, FiMail, FiPhone, FiAlertCircle, FiTrash2
} from 'react-icons/fi';
import { createUserWithEmailAndPassword, signInWithEmailAndPassword, updatePassword, sendPasswordResetEmail } from 'firebase/auth';
import { doc, setDoc, updateDoc, deleteDoc, collection, getDocs, query, where } from 'firebase/firestore';
import { auth, secondaryAuth, db } from './firebase';
import { getContractorSubscription } from './subscriptionPlans';
import { logActivity } from './activityLogger';
import PhoneInput, { formatPhoneWithCountryCode } from './PhoneInput';
import ConfirmModal from './ConfirmModal';
import { validateEmail, validatePhoneNumber, liveEmailError, formatProfileName } from './validation';

const UserManagement = ({ user }) => {
  const adminOrgId = user?.profile?.organizationId || null;
  const adminOrgName = user?.profile?.organizationName || user?.profile?.company || user?.profile?.companyName || '';
  const [showAddModal, setShowAddModal] = useState(false);
  const [accountStatusTab, setAccountStatusTab] = useState('active'); // 'active' | 'disabled'

  // Add Contractor form state - NO subscription fields (admin only provisions account credentials)
  const [formData, setFormData] = useState({
    firstName: '',
    middleName: '',
    lastName: '',
    suffix: '',
    phone: '',
    email: '',
    password: '',
    role: 'Contractor'
  });
  const [addSubmitted, setAddSubmitted] = useState(false);
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
  const [editMiddleName, setEditMiddleName] = useState('');
  const [editLastName, setEditLastName] = useState('');
  const [editSuffix, setEditSuffix] = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [editEmail, setEditEmail] = useState('');
  const [editNewPassword, setEditNewPassword] = useState('');
  const [editSubmitted, setEditSubmitted] = useState(false);
  const [savingEdit, setSavingEdit] = useState(false);

  // Disable account confirmation modal state
  const [disablingUser, setDisablingUser] = useState(null);
  const [disablingLoading, setDisablingLoading] = useState(false);

  // Delete account confirmation modal state
  const [deletingUser, setDeletingUser] = useState(null);
  const [deletingLoading, setDeletingLoading] = useState(false);

  // Generic Confirmation / Notice Modal state
  const [confirmDialog, setConfirmDialog] = useState(null);

  const fetchUsers = async () => {
    try {
      const querySnapshot = await getDocs(collection(db, "users"));
      const currentUid = auth.currentUser?.uid;
      const usersData = [];
      querySnapshot.forEach((docSnap) => {
        if (currentUid && docSnap.id === currentUid) return;
        const uData = docSnap.data();
        const roleUpper = (uData.role || '').trim().toUpperCase();
        // Admin User Management only displays Contractor and Site Safety Officer (SSO) accounts
        if (roleUpper !== 'CONTRACTOR' && roleUpper !== 'SSO') return;
        // Organization isolation: if Admin belongs to an organization, only show users in the same organization
        if (adminOrgId && uData.organizationId !== adminOrgId) return;
        usersData.push({ id: docSnap.id, ...uData });
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
    fetchUsers();
  }, [adminOrgId]);

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

  // 1. Create Contractor Account without Subscription Plan (with Confirmation Modal)
  const requestCreateUser = () => {
    setAddSubmitted(true);
    if (!formData.firstName.trim() || !formData.lastName.trim() || !formData.email.trim() || !formData.phone.trim() || !formData.password) {
      setErrorMsg("Please fill in all required fields (including phone number) and generate a password.");
      return;
    }
    const emailErr = validateEmail(formData.email);
    if (emailErr) {
      setErrorMsg(emailErr);
      return;
    }
    const phoneErr = validatePhoneNumber(formData.phone, true);
    if (phoneErr) {
      setErrorMsg(phoneErr);
      return;
    }
    setErrorMsg('');
    setConfirmDialog({
      title: 'Confirm Create Contractor',
      message: `Create new contractor account for ${formData.firstName.trim()} ${formData.lastName.trim()} (${formData.email.trim()})?`,
      confirmLabel: 'Create Contractor',
      variant: 'primary',
      onConfirm: handleCreateUser,
    });
  };

  const handleCreateUser = async () => {
    setConfirmDialog(null);
    setLoading(true);
    setErrorMsg('');
    try {
      // Create user in Firebase Auth using the secondary instance (keeps admin logged in)
      const userCredential = await createUserWithEmailAndPassword(secondaryAuth, formData.email.trim(), formData.password);
      const newUser = userCredential.user;
      
      const now = new Date();

      // Contractors start with no subscription plan and inherit the Admin's Organization
      await setDoc(doc(db, "users", newUser.uid), {
        firstName: formData.firstName.trim(),
        middleName: (formData.middleName || '').trim(),
        lastName: formData.lastName.trim(),
        suffix: (formData.suffix || '').trim(),
        phone: formatPhoneWithCountryCode(formData.phone),
        email: formData.email.trim(),
        role: 'CONTRACTOR',
        organizationId: adminOrgId,
        organizationName: adminOrgName || null,
        company: adminOrgName || null,
        companyName: adminOrgName || null,
        createdByAdminId: user?.uid || auth.currentUser?.uid || null,
        active: true,
        status: 'Active',
        mustChangePassword: true,
        initialTempPassword: formData.password,
        createdAt: now,
        subscription: null // Starts with NO subscription plan
      });
      
      // Sign out the secondary auth instance immediately
      await secondaryAuth.signOut();

      // Save activity log in database
      await logActivity(`Contractor account created for ${formData.email.trim()}`);
      
      // Reset form, close modal, and refresh users list
      setFormData({
        firstName: '',
        middleName: '',
        lastName: '',
        suffix: '',
        phone: '',
        email: '',
        password: '',
        role: 'Contractor'
      });
      setAddSubmitted(false);
      setShowAddModal(false);
      await fetchUsers();
      setConfirmDialog({
        title: 'Contractor Account Created',
        message: 'Contractor account created successfully! The contractor will start with no subscription plan and can choose their package upon sign-in.',
        variant: 'success',
        noticeOnly: true,
      });
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
    setEditMiddleName(userToEdit.middleName || '');
    setEditLastName(userToEdit.lastName || '');
    setEditSuffix(userToEdit.suffix || '');
    setEditPhone(userToEdit.phone || '');
    setEditEmail(userToEdit.email || '');
    setEditNewPassword('');
    setEditSubmitted(false);
  };

  const handleGenerateEditPassword = () => {
    const randomNum = Math.floor(1000 + Math.random() * 9000);
    setEditNewPassword(`GID-2026-${randomNum}`);
  };

  const handleSendPasswordReset = async () => {
    if (!editingUser?.email) return;
    try {
      await sendPasswordResetEmail(auth, editingUser.email.trim());
      await logActivity(`Sent password reset email to ${editingUser.email.trim()}`);
      alert(`Password reset link sent to ${editingUser.email.trim()}.`);
    } catch (err) {
      alert('Failed to send password reset email: ' + err.message);
    }
  };

  // Save updated user profile details (and optional password change) in Firestore
  const handleSaveUserEdit = async () => {
    if (!editingUser) return;
    setEditSubmitted(true);
    if (!editFirstName.trim() || !editLastName.trim()) {
      alert("First name and last name are required.");
      return;
    }
    const emailErr = validateEmail(editEmail);
    if (emailErr) {
      alert(emailErr);
      return;
    }
    const phoneErr = validatePhoneNumber(editPhone, false);
    if (phoneErr) {
      alert(phoneErr);
      return;
    }
    if (editNewPassword && editNewPassword.trim().length < 6) {
      alert("New password must be at least 6 characters.");
      return;
    }

    setSavingEdit(true);
    try {
      const updates = {
        firstName: editFirstName.trim(),
        middleName: editMiddleName.trim(),
        lastName: editLastName.trim(),
        suffix: editSuffix.trim(),
        phone: formatPhoneWithCountryCode(editPhone),
        email: editEmail.trim()
      };

      let passwordNote = '';
      if (editNewPassword.trim()) {
        const cleanPass = editNewPassword.trim();
        updates.mustChangePassword = true;
        updates.initialTempPassword = cleanPass;
        // Attempt direct Firebase Auth password change via secondaryAuth if known password exists
        const prevPass = editingUser.initialTempPassword;
        if (prevPass && editingUser.email) {
          try {
            const cred = await signInWithEmailAndPassword(secondaryAuth, editingUser.email, prevPass);
            await updatePassword(cred.user, cleanPass);
            await secondaryAuth.signOut();
            passwordNote = ' and temporary password updated';
          } catch (_) {
            try { await secondaryAuth.signOut(); } catch (_) {}
            try {
              await sendPasswordResetEmail(auth, editEmail.trim());
              passwordNote = ' (password reset email sent to user)';
            } catch (_) {}
          }
        } else if (editEmail.trim()) {
          try {
            await sendPasswordResetEmail(auth, editEmail.trim());
            passwordNote = ' (password reset email sent to user)';
          } catch (_) {}
        }
      }

      const userDocRef = doc(db, "users", editingUser.id);
      await updateDoc(userDocRef, updates);

      await logActivity(`Updated profile details${passwordNote} for ${editFirstName.trim()} ${editLastName.trim()} (${editEmail.trim()})`);
      setEditingUser(null);
      await fetchUsers();
      setConfirmDialog({
        title: 'Profile Updated',
        message: `User profile updated successfully${passwordNote}!`,
        variant: 'success',
        noticeOnly: true,
      });
    } catch (err) {
      console.error("Error updating user profile:", err);
      setConfirmDialog({
        title: 'Update Failed',
        message: 'Failed to update profile: ' + err.message,
        variant: 'danger',
        noticeOnly: true,
      });
    } finally {
      setSavingEdit(false);
    }
  };

  // 2b. Permanently Delete Contractor Account
  const handleConfirmDelete = async () => {
    if (!deletingUser) return;
    setDeletingLoading(true);
    try {
      const targetEmail = deletingUser.email;
      await deleteDoc(doc(db, "users", deletingUser.id));
      await logActivity(`Deleted contractor account for ${deletingUser.firstName || ''} ${deletingUser.lastName || ''} (${targetEmail})`);
      setDeletingUser(null);
      await fetchUsers();
      setConfirmDialog({
        title: 'Account Deleted',
        message: `Account for ${targetEmail} has been permanently deleted.`,
        variant: 'success',
        noticeOnly: true,
      });
    } catch (err) {
      console.error("Error deleting user:", err);
      setConfirmDialog({
        title: 'Delete Failed',
        message: 'Failed to delete account: ' + err.message,
        variant: 'danger',
        noticeOnly: true,
      });
    } finally {
      setDeletingLoading(false);
    }
  };

  // 3. Disable User Account (Account remains safely in the database)
  const handleConfirmDisable = async () => {
    if (!disablingUser) return;
    setDisablingLoading(true);
    try {
      const targetEmail = disablingUser.email;
      const userDocRef = doc(db, "users", disablingUser.id);
      await updateDoc(userDocRef, {
        active: false,
        status: 'Disabled',
        disabledAt: new Date()
      });

      await logActivity(`Disabled account for ${disablingUser.firstName || ''} ${disablingUser.lastName || ''} (${targetEmail})`);
      setDisablingUser(null);
      await fetchUsers();
      setConfirmDialog({
        title: 'Account Disabled',
        message: `Account for ${targetEmail} has been disabled. It is preserved in the database and moved to the Disabled Accounts list.`,
        variant: 'success',
        noticeOnly: true,
      });
    } catch (err) {
      console.error("Error disabling user:", err);
      setConfirmDialog({
        title: 'Disable Failed',
        message: 'Failed to disable user: ' + err.message,
        variant: 'danger',
        noticeOnly: true,
      });
    } finally {
      setDisablingLoading(false);
    }
  };

  // 4. Enable / Reactivate User Account
  const handleEnableUser = (userToEnable) => {
    setConfirmDialog({
      title: 'Re-enable Account?',
      message: `Re-activate access for ${userToEnable.firstName || ''} ${userToEnable.lastName || ''} (${userToEnable.email})?`,
      confirmLabel: 'Re-enable Account',
      variant: 'primary',
      onConfirm: async () => {
        setConfirmDialog(null);
        try {
          const userDocRef = doc(db, "users", userToEnable.id);
          await updateDoc(userDocRef, {
            active: true,
            status: 'Active'
          });

          await logActivity(`Re-enabled account for ${userToEnable.firstName || ''} ${userToEnable.lastName || ''} (${userToEnable.email})`);
          await fetchUsers();
          setConfirmDialog({
            title: 'Account Re-enabled',
            message: `Account for ${userToEnable.email} has been re-enabled.`,
            variant: 'success',
            noticeOnly: true,
          });
        } catch (err) {
          console.error("Error enabling user:", err);
          setConfirmDialog({
            title: 'Error',
            message: 'Failed to enable user: ' + err.message,
            variant: 'danger',
            noticeOnly: true,
          });
        }
      }
    });
  };

  // View: User Management List
  return (
    <div className="dashboard-content">
      <div className="page-header-flex" style={{ flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1>User Management</h1>
          <p>Manage system credentials, edit personnel details, and oversee active or disabled accounts.</p>
        </div>
        <button
          className="btn-primary"
          onClick={() => {
            setErrorMsg('');
            setAddSubmitted(false);
            setShowAddModal(true);
          }}
        >
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
                              {formatProfileName(user)}
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
                          {formatPhoneWithCountryCode(user.phone) || '--'}
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
                              style={{ color: '#d97706' }}
                            >
                              <FiSlash />
                            </button>
                          )}

                          {/* Delete Account Button */}
                          <button 
                            className="action-btn" 
                            title="Delete Account" 
                            onClick={() => setDeletingUser(user)}
                            style={{ color: '#dc2626' }}
                          >
                            <FiTrash2 />
                          </button>
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
                  className={`form-input${editSubmitted && !editFirstName.trim() ? ' input-error' : ''}`} 
                  value={editFirstName} 
                  onChange={(e) => setEditFirstName(e.target.value)}
                  style={{ width: '100%' }}
                />
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>MIDDLE NAME (OPTIONAL)</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="Optional"
                  value={editMiddleName} 
                  onChange={(e) => setEditMiddleName(e.target.value)}
                  style={{ width: '100%' }}
                />
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>LAST NAME *</label>
                <input 
                  type="text" 
                  className={`form-input${editSubmitted && !editLastName.trim() ? ' input-error' : ''}`} 
                  value={editLastName} 
                  onChange={(e) => setEditLastName(e.target.value)}
                  style={{ width: '100%' }}
                />
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>SUFFIX (OPTIONAL)</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="e.g. Jr., III"
                  value={editSuffix} 
                  onChange={(e) => setEditSuffix(e.target.value)}
                  style={{ width: '100%' }}
                />
              </div>

              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>PHONE NUMBER</label>
                <PhoneInput 
                  value={editPhone} 
                  onChange={(phone) => setEditPhone(phone)}
                  hasError={editSubmitted && Boolean(editPhone.trim()) && Boolean(validatePhoneNumber(editPhone, false))}
                />
              </div>

              {(() => {
                const emailErr = liveEmailError(editEmail);
                const isInvalid = Boolean(emailErr) || (editSubmitted && !editEmail.trim());
                return (
                  <div>
                    <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700 }}>EMAIL ADDRESS</label>
                    <input 
                      type="email" 
                      className={`form-input${isInvalid ? ' input-error' : ''}`} 
                      value={editEmail} 
                      onChange={(e) => setEditEmail(e.target.value)}
                      style={{ width: '100%' }}
                    />
                    {emailErr && <div className="field-error-text">{emailErr}</div>}
                  </div>
                );
              })()}

              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                  <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, margin: 0 }}>CHANGE PASSWORD (OPTIONAL)</label>
                  <button
                    type="button"
                    onClick={handleSendPasswordReset}
                    style={{ background: 'none', border: 'none', color: '#ea580c', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                  >
                    Send Reset Email
                  </button>
                </div>
                <div className="password-input-group">
                  <input 
                    type="text" 
                    className="form-input password-field" 
                    placeholder="Enter new password or click generate..." 
                    value={editNewPassword} 
                    onChange={(e) => setEditNewPassword(e.target.value)}
                    style={{ width: '100%' }}
                  />
                  <button className="icon-btn lock-btn" type="button" onClick={handleGenerateEditPassword} title="Generate New Password">
                    <FiRefreshCw />
                  </button>
                </div>
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

      {/* Delete Account Confirmation Modal */}
      {deletingUser && (
        <div className="modal-backdrop" onClick={() => !deletingLoading && setDeletingUser(null)}>
          <div className="modal-content sub-modal" onClick={e => e.stopPropagation()} style={{ maxWidth: '440px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', marginBottom: '0.75rem' }}>
              <div style={{ padding: '0.5rem', backgroundColor: '#fef2f2', color: '#dc2626', borderRadius: '8px' }}>
                <FiTrash2 style={{ fontSize: '1.25rem' }} />
              </div>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0, color: '#0f172a' }}>
                Delete Contractor Account
              </h3>
            </div>

            <p style={{ color: '#475569', fontSize: '0.875rem', lineHeight: 1.5, marginBottom: '1.5rem' }}>
              Are you sure you want to permanently remove the account for{' '}
              <strong>{deletingUser.firstName} {deletingUser.lastName}</strong> ({deletingUser.email})? This action cannot be undone.
            </p>

            <div className="modal-actions">
              <button 
                type="button" 
                className="btn-secondary" 
                onClick={() => setDeletingUser(null)} 
                disabled={deletingLoading}
              >
                Cancel
              </button>
              <button 
                type="button" 
                className="btn-primary" 
                onClick={handleConfirmDelete} 
                disabled={deletingLoading}
                style={{ backgroundColor: '#dc2626', borderColor: '#dc2626' }}
              >
                {deletingLoading ? 'Deleting...' : 'Delete Account'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Contractor Modal Popup */}
      {showAddModal && (
        <div className="modal-backdrop" onClick={() => !loading && setShowAddModal(false)}>
          <div
            className="modal-content sub-modal"
            onClick={(e) => e.stopPropagation()}
            style={{ maxWidth: '480px', width: '95%', maxHeight: '90vh', overflowY: 'auto' }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.55rem' }}>
                <div style={{ padding: '0.45rem', backgroundColor: '#fff7ed', color: '#ea580c', borderRadius: '8px' }}>
                  <FiUser />
                </div>
                <h3 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0 }}>
                  Onboard New Contractor
                </h3>
              </div>
              <button
                type="button"
                onClick={() => !loading && setShowAddModal(false)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.25rem', color: '#64748b' }}
              >
                <FiX />
              </button>
            </div>

            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.25rem', lineHeight: 1.45 }}>
              Create the contractor user account. The contractor will start with no subscription plan and can select their tier upon signing in.
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem', marginBottom: '1rem' }}>
              <div className="form-group-custom">
                <label className="form-label">FIRST NAME *</label>
                <input
                  type="text"
                  className={`form-input${addSubmitted && !formData.firstName.trim() ? ' input-error' : ''}`}
                  placeholder="e.g. Marcus"
                  value={formData.firstName}
                  onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                  required
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">MIDDLE NAME (OPTIONAL)</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="Optional"
                  value={formData.middleName}
                  onChange={(e) => setFormData({ ...formData, middleName: e.target.value })}
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">LAST NAME *</label>
                <input
                  type="text"
                  className={`form-input${addSubmitted && !formData.lastName.trim() ? ' input-error' : ''}`}
                  placeholder="e.g. Thorne"
                  value={formData.lastName}
                  onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                  required
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">SUFFIX (OPTIONAL)</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Jr., III"
                  value={formData.suffix}
                  onChange={(e) => setFormData({ ...formData, suffix: e.target.value })}
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">PHONE NUMBER *</label>
                <PhoneInput
                  value={formData.phone}
                  onChange={(phone) => setFormData({ ...formData, phone })}
                  hasError={addSubmitted && (!formData.phone.trim() || Boolean(validatePhoneNumber(formData.phone, true)))}
                  required
                />
              </div>

              {(() => {
                const emailErr = liveEmailError(formData.email);
                const isInvalid = Boolean(emailErr) || (addSubmitted && !formData.email.trim());
                return (
                  <div className="form-group-custom">
                    <label className="form-label">EMAIL ADDRESS *</label>
                    <input
                      type="email"
                      className={`form-input${isInvalid ? ' input-error' : ''}`}
                      placeholder="contractor@buildcorp.com"
                      value={formData.email}
                      onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                      required
                    />
                    {emailErr && <div className="field-error-text">{emailErr}</div>}
                  </div>
                );
              })()}

              <div className="form-group-custom">
                <label className="form-label">TEMPORARY PASSWORD *</label>
                <div className="password-input-group">
                  <input
                    type="text"
                    className={`form-input password-field${addSubmitted && !formData.password ? ' input-error' : ''}`}
                    placeholder="Enter or click generate..."
                    value={formData.password}
                    onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  />
                  <button className="icon-btn lock-btn" type="button" onClick={handleGeneratePassword} title="Generate Password">
                    {formData.password ? <FiLock /> : <FiRefreshCw />}
                  </button>
                </div>
              </div>
            </div>

            {errorMsg && (
              <div className="error-message" style={{ marginBottom: '1rem', textAlign: 'left' }}>
                {errorMsg}
              </div>
            )}

            <div className="modal-actions">
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setShowAddModal(false)}
                disabled={loading}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary"
                onClick={requestCreateUser}
                disabled={loading}
              >
                {loading ? 'Creating Account...' : 'Create Contractor Account'}
              </button>
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
        loading={loading}
        onConfirm={confirmDialog?.onConfirm}
        onCancel={() => setConfirmDialog(null)}
      />
    </div>
  );
};

export default UserManagement;
