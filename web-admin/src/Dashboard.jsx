import React, { useState, useEffect } from 'react';
import { 
  FiSearch, FiBell, FiHelpCircle, FiSettings, 
  FiGrid, FiUsers, FiShield, FiUserPlus, FiActivity, FiClock, FiX
} from 'react-icons/fi';
import logo from './assets/bevest_logo.png';
import { auth, db } from './firebase';
import { collection, getDocs } from 'firebase/firestore';
import UserManagement from './UserManagement';

const Dashboard = ({ onLogout }) => {
  const [activeTab, setActiveTab] = useState('Overview');
  const [showLogsModal, setShowLogsModal] = useState(false);
  const [contractorCount, setContractorCount] = useState(0);
  const [officerCount, setOfficerCount] = useState(0);
  const [workerCount, setWorkerCount] = useState(0);
  const [vestCount, setVestCount] = useState(0);

  useEffect(() => {
    const fetchCounts = async () => {
      try {
        const usersSnapshot = await getDocs(collection(db, 'users'));
        let contractors = 0;
        let officers = 0;
        usersSnapshot.forEach((doc) => {
          const role = doc.data().role;
          if (role && role.toLowerCase() === 'contractor') contractors++;
          if (role && role.toUpperCase() === 'SSO') officers++;
        });
        setContractorCount(contractors);
        setOfficerCount(officers);

        const workersSnapshot = await getDocs(collection(db, 'workers'));
        setWorkerCount(workersSnapshot.size);

        const vestsSnapshot = await getDocs(collection(db, 'vests'));
        setVestCount(vestsSnapshot.size);
      } catch (err) {
        console.error("Error fetching counts:", err);
      }
    };
    if (activeTab === 'Overview') {
      fetchCounts();
    }
  }, [activeTab]);

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

            {/* Stats Section Header */}
            <div className="section-header">
              <h2>People</h2>
              <p className="section-subtitle">Accounts and monitored workers</p>
              <hr className="section-divider" />
            </div>

            {/* Stats Cards */}
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-title">CONTRACTORS</div>
                <div className="stat-value">{contractorCount}</div>
              </div>
              <div className="stat-card">
                <div className="stat-title">OFFICERS</div>
                <div className="stat-value">{officerCount}</div>
              </div>
              <div className="stat-card">
                <div className="stat-title">WORKERS</div>
                <div className="stat-value">{workerCount}</div>
              </div>
              <div className="stat-card">
                <div className="stat-title">VESTS</div>
                <div className="stat-value">{vestCount}</div>
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
