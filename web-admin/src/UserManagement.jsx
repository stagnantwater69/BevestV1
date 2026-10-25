import React, { useState, useEffect } from 'react';
import { FiPlus, FiFilter, FiCalendar, FiEdit2, FiSlash, FiChevronLeft, FiChevronRight, FiCheckCircle, FiLock, FiRefreshCw, FiSearch } from 'react-icons/fi';
import { TbHistory } from 'react-icons/tb';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { doc, setDoc, collection, getDocs } from 'firebase/firestore';
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

  const [users, setUsers] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [sortOrder, setSortOrder] = useState('asc');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const querySnapshot = await getDocs(collection(db, "users"));
        const usersData = [];
        querySnapshot.forEach((doc) => {
          usersData.push({ id: doc.id, ...doc.data() });
        });
        setUsers(usersData);
      } catch (err) {
        console.error("Error fetching users:", err);
      }
    };
    if (view === 'list') {
      fetchUsers();
    }
  }, [view]);

  const filteredUsers = users
    .filter(u => {
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
        <div className="stat-value">{users.length}</div>
      </div>

      <div className="um-table-container">
        <div className="filter-bar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="search-bar" style={{ width: '300px', backgroundColor: '#fff', border: '1px solid var(--border-color)', margin: 0 }}>
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder="Search users by name or email..." 
              value={searchQuery}
              onChange={(e) => {setSearchQuery(e.target.value); setCurrentPage(1);}}
            />
          </div>
          <div style={{ display: 'flex', gap: '1rem' }}>
            <select className="filter-btn" style={{ appearance: 'auto' }} value={roleFilter} onChange={(e) => {setRoleFilter(e.target.value); setCurrentPage(1);}}>
              <option value="">All Roles</option>
              <option value="SSO">Site Safety Officer</option>
              <option value="Contractor">Contractor</option>
              <option value="Admin">Admin</option>
            </select>
          </div>
        </div>

        <table className="um-table">
          <thead>
            <tr>
              <th onClick={() => setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')} style={{ cursor: 'pointer', userSelect: 'none', width: '30%' }}>
                NAME {sortOrder === 'asc' ? '↑' : '↓'}
              </th>
              <th style={{ width: '20%' }}>ROLE</th>
              <th style={{ width: '15%' }}>STATUS</th>
              <th style={{ width: '20%' }}>LAST LOGIN</th>
              <th className="align-right" style={{ width: '15%' }}>ACTIONS</th>
            </tr>
          </thead>
          <tbody>
            {paginatedUsers.map((user) => {
              const initials = ((user.firstName?.[0] || '') + (user.lastName?.[0] || '')).toUpperCase() || 'U';
              return (
                <tr key={user.id}>
                  <td>
                    <div className="user-cell">
                      <div className="user-avatar" style={{ backgroundColor: '#fed7aa' }}>{initials}</div>
                      <div className="user-info">
                        <span className="user-name">{user.firstName} {user.lastName}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span className="badge role-badge">{user.role}</span>
                  </td>
                  <td>
                    <div className="status-indicator">
                      <span className="status-dot active"></span>
                      {user.status || 'Active'}
                    </div>
                  </td>
                  <td>
                    <div className="login-info">
                      <span className="login-date">--</span>
                      <span className="login-time"></span>
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
              )
            })}
          </tbody>
        </table>

        <div className="pagination" style={{ justifyContent: 'space-between' }}>
          <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
            Showing {filteredUsers.length === 0 ? 0 : ((currentPage - 1) * itemsPerPage) + 1} to {Math.min(currentPage * itemsPerPage, filteredUsers.length)} of {filteredUsers.length} users
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <button className="page-btn" disabled={currentPage === 1} onClick={() => setCurrentPage(p => Math.max(1, p - 1))}><FiChevronLeft /></button>
            <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>Page {currentPage} of {totalPages}</span>
            <button className="page-btn" disabled={currentPage === totalPages} onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}><FiChevronRight /></button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default UserManagement;
