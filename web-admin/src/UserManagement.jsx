import React, { useState } from 'react';
import { FiPlus, FiFilter, FiCalendar, FiEdit2, FiSlash, FiChevronLeft, FiChevronRight, FiCheckCircle, FiLock, FiRefreshCw } from 'react-icons/fi';
import { TbHistory } from 'react-icons/tb';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { doc, setDoc } from 'firebase/firestore';
import { secondaryAuth, db } from './firebase';

const UserManagement = () => {
  const [view, setView] = useState('list'); // 'list' | 'add'
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    phone: '',
    email: '',
    password: '',
    role: 'SSO'
  });
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const users = [
    { initials: 'JD', name: 'Johnathan Doe', uid: 'U9-9902', role: 'CIVIL ENGINEER', status: 'Active', lastLogin: 'Oct 24, 2026', time: '09:45 AM', color: '#fed7aa' },
    { initials: 'AM', name: 'Angela Martinez', uid: 'U9-4421', role: 'CIVIL ENGINEER', status: 'Active', lastLogin: 'Oct 23, 2026', time: '14:12 PM', color: '#fed7aa' },
    { initials: 'SK', name: 'Samuel Kim', uid: 'U8-1002', role: 'CIVIL ENGINEER', status: 'Active', lastLogin: 'Oct 22, 2026', time: '11:05 AM', color: '#fed7aa' },
    { initials: 'RW', name: 'Robert White', uid: 'U8-3139', role: 'CIVIL ENGINEER', status: 'Active', lastLogin: 'Oct 22, 2026', time: 'Connected', color: '#fed7aa' },
  ];

  const handleGeneratePassword = () => {
    const randomNum = Math.floor(1000 + Math.random() * 9000);
    setFormData({ ...formData, password: `GID-2026-${randomNum}` });
  };

  const handleCreateUser = async () => {
    if (!formData.firstName || !formData.lastName || !formData.email || !formData.password) {
      setErrorMsg("Please fill in all required fields and generate a password.");
      return;
    }
    
    setLoading(true);
    setErrorMsg('');
    try {
      // Create user in Firebase Auth using the secondary instance (doesn't log admin out)
      const userCredential = await createUserWithEmailAndPassword(secondaryAuth, formData.email, formData.password);
      const user = userCredential.user;
      
      // Add user details to Firestore
      await setDoc(doc(db, "users", user.uid), {
        firstName: formData.firstName,
        lastName: formData.lastName,
        phone: formData.phone,
        email: formData.email,
        role: formData.role,
        status: 'Active',
        createdAt: new Date()
      });
      
      // Sign out the secondary instance immediately
      await secondaryAuth.signOut();
      
      // Reset form and go back to list
      setFormData({
        firstName: '',
        lastName: '',
        phone: '',
        email: '',
        password: '',
        role: 'SSO'
      });
      setView('list');
      alert("User created successfully!");
    } catch (error) {
      console.error("Error creating user:", error);
      setErrorMsg(error.message);
    } finally {
      setLoading(false);
    }
  };

  if (view === 'add') {
    return (
      <div className="dashboard-content">
        <div className="breadcrumb">
          <span className="bc-link" onClick={() => setView('list')}>User Management</span> 
          <span className="bc-separator">&gt;</span> 
          <span className="bc-current">Add New User</span>
        </div>
        
        <div className="page-header" style={{ marginBottom: '2rem' }}>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: '0.25rem' }}>Onboard New Personnel</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>Fill out the mandatory information below to grant system access and assign safety monitoring protocols.</p>
        </div>

        <div className="add-user-card">
          <div className="form-section-container">
            <div className="form-section-info">
              <h3>Personal Data</h3>
              <p>Core identity information for the employee or contractor.</p>
            </div>
            
            <div className="form-grid">
              <div className="form-group-custom">
                <label className="form-label">First Name</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="e.g. Marcus" 
                  value={formData.firstName}
                  onChange={(e) => setFormData({...formData, firstName: e.target.value})}
                />
              </div>
              <div className="form-group-custom">
                <label className="form-label">Phone Number</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="+1 (555) 000-0000"
                  value={formData.phone}
                  onChange={(e) => setFormData({...formData, phone: e.target.value})}
                />
              </div>
              <div className="form-group-custom">
                <label className="form-label">Last Name</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="e.g. Thorne"
                  value={formData.lastName}
                  onChange={(e) => setFormData({...formData, lastName: e.target.value})}
                />
              </div>
              <div className="form-group-custom">
                <label className="form-label">Email Address</label>
                <input 
                  type="email" 
                  className="form-input" 
                  placeholder="m.thorne@industrialcorp.com"
                  value={formData.email}
                  onChange={(e) => setFormData({...formData, email: e.target.value})}
                />
              </div>
              <div className="form-group-custom">
                <label className="form-label">Assign Role</label>
                <select 
                  className="form-input form-select"
                  value={formData.role}
                  onChange={(e) => setFormData({...formData, role: e.target.value})}
                >
                  <option value="SSO">Site Safety Officer</option>
                  <option value="Contractor">Contractor</option>
                  <option value="Admin">Admin</option>
                </select>
              </div>
              <div className="form-group-custom">
                <label className="form-label">Password</label>
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
            <button className="btn-secondary" onClick={() => setView('list')} disabled={loading}>Cancel</button>
            <button className="btn-primary" onClick={handleCreateUser} disabled={loading}>
              {loading ? 'CREATING...' : 'CREATE USER'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-content">
      <div className="page-header-flex">
        <div>
          <h1>User Management</h1>
          <p>Manage system access, roles, and safety credentials for all personnel.</p>
        </div>
        <button className="btn-primary" onClick={() => setView('add')}>
          <FiPlus style={{ fontSize: '1.2rem' }} /> Register New User
        </button>
      </div>

      <div className="um-summary-card">
        <div className="stat-title">ACTIVE USERS</div>
        <div className="stat-value">10</div>
      </div>

      <div className="um-table-container">
        <div className="filter-bar">
          <button className="filter-btn"><FiFilter /> Filter by Role</button>
          <button className="filter-btn"><FiCalendar /> Last Login</button>
        </div>

        <table className="um-table">
          <thead>
            <tr>
              <th>NAME & ID</th>
              <th>ROLE</th>
              <th>STATUS</th>
              <th>LAST LOGIN</th>
              <th className="align-right">ACTIONS</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user, idx) => (
              <tr key={idx}>
                <td>
                  <div className="user-cell">
                    <div className="user-avatar" style={{ backgroundColor: user.color }}>{user.initials}</div>
                    <div className="user-info">
                      <span className="user-name">{user.name}</span>
                      <span className="user-uid">UID: {user.uid}</span>
                    </div>
                  </div>
                </td>
                <td>
                  <span className="badge role-badge">{user.role}</span>
                </td>
                <td>
                  <div className="status-indicator">
                    <span className="status-dot active"></span>
                    {user.status}
                  </div>
                </td>
                <td>
                  <div className="login-info">
                    <span className="login-date">{user.lastLogin}</span>
                    <span className="login-time">{user.time}</span>
                  </div>
                </td>
                <td className="align-right">
                  <div className="action-buttons">
                    <button className="action-btn" title="Edit"><FiEdit2 /></button>
                    <button className="action-btn" title="History"><TbHistory /></button>
                    <button className="action-btn" title="Disable"><FiSlash /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="pagination">
          <button className="page-btn"><FiChevronLeft /></button>
          <button className="page-btn active">1</button>
          <button className="page-btn"><FiChevronRight /></button>
        </div>
      </div>
    </div>
  );
};

export default UserManagement;
