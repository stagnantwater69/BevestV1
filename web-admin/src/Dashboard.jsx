import React, { useState, useEffect } from 'react';
import { 
  FiGrid, FiUsers, FiShield, FiUserPlus, FiActivity, FiClock, FiX, FiMenu
} from 'react-icons/fi';
import logo from './assets/bevest_logo.png';
import { auth, db } from './firebase';
import { collection, getDocs, query, orderBy, limit, onSnapshot } from 'firebase/firestore';
import UserManagement from './UserManagement';
import SystemManagement from './SystemManagement';
import AdminProfile from './AdminProfile';
import { formatTimeAgo } from './activityLogger';

const Dashboard = ({ user, onLogout }) => {
  const [activeTab, setActiveTab] = useState('Overview');
  const [showLogsModal, setShowLogsModal] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [contractorCount, setContractorCount] = useState(0);
  const [officerCount, setOfficerCount] = useState(0);
  const [workerCount, setWorkerCount] = useState(0);
  const [vestCount, setVestCount] = useState(0);
  const [vestStats, setVestStats] = useState({ active: 0, offline: 0, other: 0 });

  // Real-time Activity Logs from Firestore database
  const [activityLogs, setActivityLogs] = useState([]);
  const [logsLoading, setLogsLoading] = useState(true);

  // Subscribe to activityLog in real-time from Firestore
  useEffect(() => {
    let unsubscribe = () => {};
    try {
      const q = query(
        collection(db, 'activityLog'),
        orderBy('createdAt', 'desc'),
        limit(50)
      );

      unsubscribe = onSnapshot(
        q,
        (snapshot) => {
          const logs = [];
          snapshot.forEach((doc) => {
            const d = doc.data();
            const actionText = d.message || d.action || 'Activity recorded';
            if (
              actionText.toLowerCase().includes('developer') ||
              (d.actorId && d.actorId.toLowerCase().includes('developer'))
            ) {
              return;
            }
            logs.push({
              id: doc.id,
              action: actionText,
              actorId: d.actorId || 'System',
              createdAt: d.createdAt || d.timestamp,
              time: formatTimeAgo(d.createdAt || d.timestamp)
            });
          });
          setActivityLogs(logs);
          setLogsLoading(false);
        },
        (err) => {
          console.warn('Falling back to unordered activityLog fetch:', err);
          const fallbackUnsub = onSnapshot(collection(db, 'activityLog'), (snapshot) => {
            const logs = [];
            snapshot.forEach((doc) => {
            const d = doc.data();
            const actionText = d.message || d.action || 'Activity recorded';
            if (
              actionText.toLowerCase().includes('developer') ||
              (d.actorId && d.actorId.toLowerCase().includes('developer'))
            ) {
              return;
            }
            logs.push({
              id: doc.id,
              action: actionText,
              actorId: d.actorId || 'System',
              createdAt: d.createdAt || d.timestamp,
              time: formatTimeAgo(d.createdAt || d.timestamp)
            });
          });
            logs.sort((a, b) => {
              const timeA = typeof a.createdAt === 'number' ? a.createdAt : (a.createdAt?.toDate ? a.createdAt.toDate().getTime() : 0);
              const timeB = typeof b.createdAt === 'number' ? b.createdAt : (b.createdAt?.toDate ? b.createdAt.toDate().getTime() : 0);
              return timeB - timeA;
            });
            setActivityLogs(logs.slice(0, 50));
            setLogsLoading(false);
          });
          unsubscribe = fallbackUnsub;
        }
      );
    } catch (err) {
      console.error('Error setting up activity log listener:', err);
      setLogsLoading(false);
    }

    return () => unsubscribe();
  }, []);

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
        
        <div 
          className="sidebar-user" 
          onClick={() => { setActiveTab('Profile'); setIsMobileMenuOpen(false); }}
          style={{ cursor: 'pointer' }}
        >
          <div className="avatar" style={{ backgroundColor: '#fce7db', color: '#ea580c', fontWeight: 700, fontSize: '0.875rem' }}>
            WA
          </div>
          <span className="username">Web Admin</span>
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
                    <button onClick={() => setShowLogsModal(true)} className="view-all" style={{background: 'none', border: 'none', cursor: 'pointer'}}>
                      Full Activity Log ({activityLogs.length})
                    </button>
                  </div>
                  
                  <div className="activity-list">
                    {logsLoading ? (
                      <div style={{ padding: '1.5rem', textAlign: 'center', color: '#94a3b8', fontSize: '0.875rem' }}>
                        Loading recent activity...
                      </div>
                    ) : activityLogs.length === 0 ? (
                      <div style={{ padding: '1.5rem', textAlign: 'center', color: '#94a3b8', fontSize: '0.875rem' }}>
                        No recent activity recorded yet.
                      </div>
                    ) : (
                      activityLogs.slice(0, 5).map(log => (
                        <div key={log.id} className="activity-row">
                          <FiClock className="activity-icon" />
                          <div className="activity-details">
                            <p>{log.action}</p>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginTop: '0.15rem' }}>
                              <span className="activity-time">{formatTimeAgo(log.createdAt)}</span>
                              {log.actorId && log.actorId !== 'System' && (
                                <span style={{ fontSize: '0.72rem', color: '#94a3b8' }}>• {log.actorId}</span>
                              )}
                            </div>
                          </div>
                        </div>
                      ))
                    )}
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
          <SystemManagement user={user} />
        )}

        {activeTab === 'Profile' && (
          <AdminProfile user={user} />
        )}
      </main>

      {/* Logs Modal */}
      {showLogsModal && (
        <div className="modal-overlay">
          <div className="modal-content activity-modal" style={{ maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
            <div className="modal-header">
              <h2>Full Activity Log ({activityLogs.length})</h2>
              <button className="close-btn" onClick={() => setShowLogsModal(false)}><FiX /></button>
            </div>
            <div className="modal-body" style={{ overflowY: 'auto' }}>
              <div className="activity-list">
                {activityLogs.length === 0 ? (
                  <p style={{ textAlign: 'center', color: '#94a3b8', padding: '2rem' }}>
                    No activity records found in database.
                  </p>
                ) : (
                  activityLogs.map(log => (
                    <div key={log.id} className="activity-row">
                      <FiClock className="activity-icon" />
                      <div className="activity-details">
                        <p style={{ fontWeight: 600, color: '#0f172a' }}>{log.action}</p>
                        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginTop: '0.2rem' }}>
                          <span className="activity-time">{formatTimeAgo(log.createdAt)}</span>
                          {log.actorId && (
                            <span style={{ fontSize: '0.75rem', color: '#64748b' }}>By: {log.actorId}</span>
                          )}
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Dashboard;
