import React from 'react';
import { FiPlus, FiFilter, FiCalendar, FiEdit2, FiSlash, FiChevronLeft, FiChevronRight, FiCheckCircle } from 'react-icons/fi';
import { TbHistory } from 'react-icons/tb';

const UserManagement = () => {
  const users = [
    { initials: 'JD', name: 'Johnathan Doe', uid: 'U9-9902', role: 'CIVIL ENGINEER', status: 'Active', lastLogin: 'Oct 24, 2026', time: '09:45 AM', color: '#fed7aa' },
    { initials: 'AM', name: 'Angela Martinez', uid: 'U9-4421', role: 'CIVIL ENGINEER', status: 'Active', lastLogin: 'Oct 23, 2026', time: '14:12 PM', color: '#fed7aa' },
    { initials: 'SK', name: 'Samuel Kim', uid: 'U8-1002', role: 'CIVIL ENGINEER', status: 'Active', lastLogin: 'Oct 22, 2026', time: '11:05 AM', color: '#fed7aa' },
    { initials: 'RW', name: 'Robert White', uid: 'U8-3139', role: 'CIVIL ENGINEER', status: 'Active', lastLogin: 'Oct 22, 2026', time: 'Connected', color: '#fed7aa' },
  ];

  return (
    <div className="dashboard-content">
      <div className="page-header-flex">
        <div>
          <h1>User Management</h1>
          <p>Manage system access, roles, and safety credentials for all personnel.</p>
        </div>
        <button className="btn-primary">
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
