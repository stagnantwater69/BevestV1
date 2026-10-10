import React, { useState } from 'react';
import { 
  FiSearch, FiBell, FiHelpCircle, FiSettings, 
  FiGrid, FiUsers, FiShield, FiUserPlus, FiActivity, FiClock, FiX
} from 'react-icons/fi';
import logo from './assets/bevest_logo.png';
import { auth } from './firebase';
import UserManagement from './UserManagement';

const Dashboard = ({ onLogout }) => {
  const [activeTab, setActiveTab] = useState('Overview');
  const [showLogsModal, setShowLogsModal] = useState(false);

  const activityLogs = [
    { id: 1, action: "Contractor account created for jimz@bevest.com", time: "42d ago" },
    { id: 2, action: "Contractor account created for roch@bevest.com", time: "42d ago" },
    { id: 3, action: "Contractor account created for test@gmail.com", time: "42d ago" },
    { id: 4, action: "Maintenance mode disabled", time: "42d ago" },
    { id: 5, action: "Maintenance mode enabled", time: "42d ago" }
  ];

  const handleSignOut = () => {
    auth.signOut();
    if (onLogout) onLogout();
  };

  return (
    <div className="dashboard-container">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-header">
          <img src={logo} alt="Bevest" className="sidebar-logo" />
          <div className="sidebar-title-group">
            <h2>BeVest Admin</h2>
            <span>SAFETY OVERSIGHT</span>
          </div>
        </div>
        
        <div className="sidebar-user">
          <div className="avatar">
            <FiUsers />
          </div>
          <span className="username">Admin User</span>
        </div>

        <nav className="sidebar-nav">
          <button 
            className={`nav-item ${activeTab === 'Overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('Overview')}
          >
            <FiGrid /> Overview
          </button>
          <button 
            className={`nav-item ${activeTab === 'User Management' ? 'active' : ''}`}
            onClick={() => setActiveTab('User Management')}
          >
            <FiUsers /> User Management
          </button>
          <button 
            className={`nav-item ${activeTab === 'System Management' ? 'active' : ''}`}
            onClick={() => setActiveTab('System Management')}
          >
            <FiShield /> System Management
          </button>
        </nav>
        
        <div className="sidebar-footer">
          <button className="nav-item" onClick={handleSignOut} style={{ color: '#ef4444' }}>
            Sign Out
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="main-content">
        {/* Topbar */}
        <header className="topbar">
          <div className="search-bar">
            <FiSearch className="search-icon" />
            <input type="text" placeholder="Search system logs, workers, or vests..." />
          </div>
          <div className="topbar-actions">
            <button className="icon-btn"><FiBell /></button>
            <button className="icon-btn"><FiHelpCircle /></button>
            <button className="icon-btn"><FiSettings /></button>
          </div>
        </header>

        {/* Dashboard Area */}
        {activeTab === 'Overview' && (
          <div className="dashboard-content">
            <div className="page-header">
              <h1>System Dashboard</h1>
              <p>Real-time oversight across all active project sites.</p>
            </div>

            {/* Stats Cards */}
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-title">TOTAL CONTRACTORS</div>
                <div className="stat-value">12</div>
              </div>
              <div className="stat-card">
                <div className="stat-title">SAFETY OFFICERS / WORKERS</div>
                <div className="stat-value">5 / 143</div>
              </div>
              <div className="stat-card">
                <div className="stat-title">TOTAL VESTS</div>
                <div className="stat-value">150</div>
              </div>
              <div className="stat-card highlight">
                <div className="stat-title">FLEET HEALTH (ACTIVE VESTS)</div>
                <div className="stat-value">142</div>
              </div>
            </div>

            {/* Recent Activity */}
            <div className="recent-activity-container">
              <div className="activity-header">
                <div>
                  <h2>Recent Activity</h2>
                  <p className="activity-subtitle">Administrative actions across the system</p>
                </div>
                <button onClick={() => setShowLogsModal(true)} className="view-all" style={{background: 'none', border: 'none', cursor: 'pointer'}}>Full Activity Log</button>
              </div>
              
              <div className="activity-list">
                {activityLogs.slice(0, 3).map(log => (
                  <div key={log.id} className="activity-row">
                    <FiClock className="activity-icon" />
                    <div className="activity-details">
                      <p>{log.action}</p>
                      <span className="activity-time">{log.time}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'User Management' && (
          <UserManagement />
        )}

        {activeTab === 'System Management' && (
          <div className="dashboard-content">
            <div className="page-header">
              <h1>System Management</h1>
              <p>Manage system configurations, backup and compliance settings.</p>
            </div>
          </div>
        )}
      </main>

      {/* Logs Modal */}
      {showLogsModal && (
        <div className="modal-overlay">
          <div className="modal-content activity-modal">
            <div className="modal-header">
              <h2>Full Activity Log</h2>
              <button className="close-btn" onClick={() => setShowLogsModal(false)}><FiX /></button>
            </div>
            <div className="modal-body">
              <div className="activity-list">
                {activityLogs.map(log => (
                  <div key={log.id} className="activity-row">
                    <FiClock className="activity-icon" />
                    <div className="activity-details">
                      <p>{log.action}</p>
                      <span className="activity-time">{log.time}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Dashboard;
