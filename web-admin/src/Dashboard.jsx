import React, { useState, useEffect } from 'react';
import { 
  FiGrid, FiUsers, FiShield, FiUserPlus, FiActivity, FiClock, FiX, FiMenu
} from 'react-icons/fi';
import logo from './assets/bevest_logo.png';
import { auth, db } from './firebase';
import { collection, getDocs } from 'firebase/firestore';
import UserManagement from './UserManagement';

const Dashboard = ({ onLogout }) => {
  const [activeTab, setActiveTab] = useState('Overview');
  const [showLogsModal, setShowLogsModal] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [contractorCount, setContractorCount] = useState(0);
  const [officerCount, setOfficerCount] = useState(0);
  const [workerCount, setWorkerCount] = useState(0);
  const [vestCount, setVestCount] = useState(0);
  const [vestStats, setVestStats] = useState({ active: 0, offline: 0, other: 0 });

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
        let activeVests = 0;
        let offlineVests = 0;
        let otherVests = 0;
        vestsSnapshot.forEach((doc) => {
          const data = doc.data();
          if (data.online === 'true' || data.online === true) activeVests++;
          else if (data.online === 'false' || data.online === false) offlineVests++;
          else otherVests++;
        });
        setVestCount(vestsSnapshot.size);
        setVestStats({ active: activeVests, offline: offlineVests, other: otherVests });
      } catch (err) {
        console.error("Error fetching counts:", err);
      }
    };
    if (activeTab === 'Overview') {
      fetchCounts();
    }
  }, [activeTab]);

  const totalVests = vestStats.active + vestStats.offline + vestStats.other;
  const activePct = totalVests ? Math.round((vestStats.active / totalVests) * 100) : 0;
  const offlinePct = totalVests ? Math.round((vestStats.offline / totalVests) * 100) : 0;
  const otherPct = totalVests ? Math.round((vestStats.other / totalVests) * 100) : 0;

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
    <div className="dashboard-container" style={{ position: 'relative' }}>
      
      {/* Mobile Header (Only visible on mobile) */}
      <div className="mobile-header">
        <div className="mobile-logo-group">
          <img src={logo} alt="Bevest" />
          <h2>BeVest Admin</h2>
        </div>
        <button className="burger-btn" onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}>
          {isMobileMenuOpen ? <FiX /> : <FiMenu />}
        </button>
      </div>

      {/* Sidebar */}
      <aside className={`sidebar ${isMobileMenuOpen ? 'open' : ''}`}>
        <div className="sidebar-header desktop-only">
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
            onClick={() => { setActiveTab('Overview'); setIsMobileMenuOpen(false); }}
          >
            <FiGrid /> Overview
          </button>
          <button 
            className={`nav-item ${activeTab === 'User Management' ? 'active' : ''}`}
            onClick={() => { setActiveTab('User Management'); setIsMobileMenuOpen(false); }}
          >
            <FiUsers /> User Management
          </button>
          <button 
            className={`nav-item ${activeTab === 'System Management' ? 'active' : ''}`}
            onClick={() => { setActiveTab('System Management'); setIsMobileMenuOpen(false); }}
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

            {/* Dashboard Columns */}
            <div className="dashboard-columns">
              
              {/* Fleet Health Column */}
              <div className="fleet-health-column">
                <div className="section-header">
                  <h2>Fleet health</h2>
                  <p className="section-subtitle">How the vest hardware is doing</p>
                  <hr className="section-divider" />
                </div>
                
                <div className="fleet-card">
                  {/* Progress Bar */}
                  <div className="fleet-progress-bar">
                    <div className="progress-segment active" style={{ width: `${activePct}%` }}></div>
                    <div className="progress-segment offline" style={{ width: `${offlinePct}%` }}></div>
                    <div className="progress-segment other" style={{ width: `${otherPct}%` }}></div>
                  </div>
                  
                  {/* Legend/Stats */}
                  <div className="fleet-stats">
                    <div className="fleet-stat-row">
                      <div className="stat-label">
                        <span className="dot dot-active"></span> Active
                      </div>
                      <div className="stat-values">
                        <span className="count">{vestStats.active}</span>
                        <span className="pct">{activePct}%</span>
                      </div>
                    </div>
                    <div className="fleet-stat-row">
                      <div className="stat-label">
                        <span className="dot dot-offline"></span> Offline
                      </div>
                      <div className="stat-values">
                        <span className="count">{vestStats.offline}</span>
                        <span className="pct">{offlinePct}%</span>
                      </div>
                    </div>
                    <div className="fleet-stat-row">
                      <div className="stat-label">
                        <span className="dot dot-other"></span> Other
                      </div>
                      <div className="stat-values">
                        <span className="count">{vestStats.other}</span>
                        <span className="pct">{otherPct}%</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Recent Activity Column */}
              <div className="recent-activity-column">
                <div className="section-header">
                  <h2>Recent Activity</h2>
                  <p className="section-subtitle">Administrative actions across the system</p>
                  <hr className="section-divider" />
                </div>
                <div className="recent-activity-container">
                  <div className="activity-header">
                    <div>
                      {/* Subtitle moved to section header above */}
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
