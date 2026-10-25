import React, { useState } from 'react';
import { 
  FiSearch, FiBell, FiHelpCircle, FiSettings, 
  FiGrid, FiUsers, FiShield, FiUserPlus, FiActivity
} from 'react-icons/fi';
import logo from './assets/bevest_logo.png';
import { auth } from './firebase';
import UserManagement from './UserManagement';

const Dashboard = ({ onLogout }) => {
  const [activeTab, setActiveTab] = useState('Overview');

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
                <h2>Recent Admin Activity</h2>
                <a href="#" className="view-all">View All Logs</a>
              </div>
              
              <table className="activity-table">
                <thead>
                  <tr>
                    <th>ACTIVITY TYPE</th>
                    <th>DESCRIPTION</th>
                    <th>TIME</th>
                    <th>STATUS</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>
                      <div className="cell-flex"><FiUserPlus style={{color: '#3b82f6'}}/> User Action</div>
                    </td>
                    <td>New Contractor Added: Elias Vance</td>
                    <td>2 mins ago</td>
                    <td><span className="badge success">SUCCESS</span></td>
                  </tr>
                  <tr>
                    <td>
                      <div className="cell-flex"><FiSettings style={{color: '#64748b'}}/> System Config</div>
                    </td>
                    <td>Maintenance mode turned on</td>
                    <td>3 hrs ago</td>
                    <td><span className="badge config">CONFIG</span></td>
                  </tr>
                  <tr>
                    <td>
                      <div className="cell-flex"><FiActivity style={{color: '#f59e0b'}}/> Vest Assigned</div>
                    </td>
                    <td>Vest V-1024 assigned to Worker W-89</td>
                    <td>5 hrs ago</td>
                    <td><span className="badge success">SUCCESS</span></td>
                  </tr>
                </tbody>
              </table>
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
    </div>
  );
};

export default Dashboard;
