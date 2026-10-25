import React, { useState } from 'react';
import { FiGrid, FiUsers, FiFileText, FiX, FiMenu } from 'react-icons/fi';
import { TbCpu } from 'react-icons/tb';
import logo from '../assets/bevest_logo.png';
import { auth } from '../firebase';
import { useContractorData, initialsOf } from './contractorData';
import ContractorOverview from './ContractorOverview';
import ContractorReports, { MonthlyReportDetail, IncidentHistory } from './ContractorReports';
import ContractorSsos, { AddSso } from './ContractorSsos';
import ContractorProfile from './ContractorProfile';
import SimulationScreen from '../simulation/SimulationScreen';
import './contractor.css';

/**
 * Contractor web area. Same shell as the Admin dashboard (sidebar, user chip,
 * nav items, sign-out footer, mobile burger menu) with the contractor's mobile
 * tabs — Dashboard, Workers, Reports, IoT Simulation — as its navigation.
 */
const NAV = [
  { key: 'Dashboard', label: 'Dashboard', icon: FiGrid },
  { key: 'SSOs', label: 'Site Safety Officers', icon: FiUsers },
  { key: 'Reports', label: 'Reports', icon: FiFileText },
  { key: 'Simulation', label: 'IoT Simulation', icon: TbCpu },
];

// Which nav tab each sub-page belongs to (for the active highlight).
const PARENT_TAB = {
  Dashboard: 'Dashboard',
  SSOs: 'SSOs',
  AddSso: 'SSOs',
  Reports: 'Reports',
  ReportDetail: 'Reports',
  Incidents: 'Reports',
  Simulation: 'Simulation',
  Profile: null,
};

const ContractorDashboard = ({ user, onLogout }) => {
  const [profile, setProfile] = useState(user.profile || {});
  const [route, setRoute] = useState({ page: 'Dashboard', param: null, from: null });
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  // A contractor's own uid is their contractorId (see firestore.rules / AccountRepositoryImpl).
  const contractorId = profile.contractorId || user.uid;
  const data = useContractorData(contractorId);

  const fullName = `${profile.firstName || ''} ${profile.lastName || ''}`.trim() || user.email;
  const activeTab = PARENT_TAB[route.page];

  const go = (page, param = null) => {
    setRoute((r) => ({ page, param, from: r.page }));
    setIsMobileMenuOpen(false);
  };

  const handleSignOut = () => {
    auth.signOut();
    if (onLogout) onLogout();
  };

  const renderPage = () => {
    switch (route.page) {
      case 'Reports':
        return (
          <ContractorReports
            data={data}
            onOpenReport={(key) => go('ReportDetail', key)}
            onOpenIncidents={() => go('Incidents')}
            onOpenSsos={() => go('SSOs')}
          />
        );
      case 'ReportDetail':
        return (
          <MonthlyReportDetail
            data={data}
            monthKey={route.param}
            contractorName={fullName}
            onBack={() => go('Reports')}
          />
        );
      case 'Incidents':
        return <IncidentHistory data={data} onBack={() => go('Reports')} />;
      case 'SSOs':
        return (
          <ContractorSsos
            contractorId={contractorId}
            onAdd={() => go('AddSso')}
            onBack={route.from === 'Dashboard' || route.from === 'Reports' ? () => go(route.from) : null}
            backLabel={route.from === 'Reports' ? 'Reports' : 'Dashboard'}
          />
        );
      case 'AddSso':
        return <AddSso contractorId={contractorId} onDone={() => go('SSOs')} />;
      case 'Simulation':
        return <SimulationScreen user={user} />;
      case 'Profile':
        return <ContractorProfile user={user} profile={profile} onProfileChange={setProfile} />;
      case 'Dashboard':
      default:
        return (
          <ContractorOverview
            contractorId={contractorId}
            data={data}
            onNavigate={go}
          />
        );
    }
  };

  return (
    <div className="dashboard-container contractor-shell" style={{ position: 'relative' }}>

      {/* Mobile Header (Only visible on mobile) */}
      <div className="mobile-header">
        <div className="mobile-logo-group">
          <img src={logo} alt="Bevest" />
          <h2>BeVest Contractor</h2>
        </div>
        <button
          id="ct-burger-btn"
          className="burger-btn"
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          aria-label="Toggle navigation"
        >
          {isMobileMenuOpen ? <FiX /> : <FiMenu />}
        </button>
      </div>

      {/* Sidebar */}
      <aside className={`sidebar ${isMobileMenuOpen ? 'open' : ''}`}>
        <div className="sidebar-header desktop-only">
          <img src={logo} alt="Bevest" className="sidebar-logo" />
          <div className="sidebar-title-group">
            <h2>BeVest Contractor</h2>
            <span>SAFETY OVERVIEW</span>
          </div>
        </div>

        <div
          id="ct-sidebar-user"
          className={`sidebar-user ${route.page === 'Profile' ? 'ct-user-active' : ''}`}
          onClick={() => go('Profile')}
          style={{ cursor: 'pointer' }}
        >
          <div className="avatar" style={{ backgroundColor: '#fce7db', color: '#ea580c', fontWeight: 700, fontSize: '0.875rem' }}>
            {initialsOf(fullName, 'C')}
          </div>
          <div className="ct-user-text">
            <span className="username">{fullName}</span>
            <span className="ct-user-role">Contractor</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          {NAV.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              id={`ct-nav-${key.toLowerCase()}`}
              className={`nav-item ${activeTab === key ? 'active' : ''}`}
              onClick={() => go(key)}
            >
              <Icon /> {label || key}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <button id="ct-sign-out" className="nav-item" onClick={handleSignOut} style={{ color: '#ef4444' }}>
            Sign Out
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="main-content">
        <div className="dashboard-content ct-content" key={route.page + (route.param || '')}>
          {renderPage()}
        </div>
      </main>
    </div>
  );
};

export default ContractorDashboard;
