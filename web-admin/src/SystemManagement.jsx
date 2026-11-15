import React, { useState, useEffect } from 'react';
import { 
  FiSearch, FiCheckCircle, FiAlertCircle, 
  FiRefreshCw, FiTrash2, FiPrinter, FiDownload, FiUser, FiShield, FiCpu, 
  FiWifi, FiWifiOff, FiX, FiEye, FiCheck, FiChevronLeft, FiChevronRight,
  FiClock, FiTag, FiMinimize2
} from 'react-icons/fi';
import { 
  collection, doc, getDocs, updateDoc, onSnapshot 
} from 'firebase/firestore';
import { db } from './firebase';
import QRCode from 'qrcode';
import { logActivity } from './activityLogger';

const getWifiSignalInfo = (rssi, isOnline) => {
  if (!isOnline || rssi == null || rssi === 0) {
    return { label: 'No Signal', color: '#94a3b8', bg: '#f1f5f9', isOnline: false };
  }
  if (rssi >= -60) {
    return { label: 'Excellent', color: '#10b981', bg: '#ecfdf5', isOnline: true, text: `${rssi} dBm (Excellent)` };
  }
  if (rssi >= -72) {
    return { label: 'Good', color: '#16a34a', bg: '#f0fdf4', isOnline: true, text: `${rssi} dBm (Good)` };
  }
  if (rssi >= -84) {
    return { label: 'Fair', color: '#d97706', bg: '#fffbeb', isOnline: true, text: `${rssi} dBm (Fair)` };
  }
  return { label: 'Weak', color: '#dc2626', bg: '#fef2f2', isOnline: true, text: `${rssi} dBm (Weak)` };
};

const SystemManagement = ({ user }) => {
  const [vests, setVests] = useState([]);
  const [workersMap, setWorkersMap] = useState({});
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 6;

  // Modals state
  const [showQrViewerModal, setShowQrViewerModal] = useState(null); // vest object
  const [qrCodeDataUrl, setQrCodeDataUrl] = useState('');

  // Current admin display info
  const currentAdminName = user?.profile?.firstName 
    ? `${user.profile.firstName} ${user.profile.lastName || ''}`.trim()
    : (user?.email || 'Web Admin');
  const currentAdminEmail = user?.email || 'admin@bevest.com';

  // 1. Fetch Workers for mapping assignedWorkerId -> Worker Info
  useEffect(() => {
    const fetchWorkers = async () => {
      try {
        const workersSnap = await getDocs(collection(db, 'workers'));
        const map = {};
        workersSnap.forEach((d) => {
          map[d.id] = { id: d.id, ...d.data() };
        });
        setWorkersMap(map);
      } catch (err) {
        console.error('Error fetching workers:', err);
      }
    };
    fetchWorkers();
  }, []);

  // 2. Subscribe to Vests in real-time (Only Company Directory claimed vests)
  useEffect(() => {
    setLoading(true);
    const q = collection(db, 'vests');
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const vestList = [];
      const currentAdminComp = (user?.companyId || user?.profile?.company || user?.profile?.companyName || '').trim().toLowerCase();

      snapshot.forEach((docSnap) => {
        const data = docSnap.data();
        // RULE 1: Unclaimed developer inventory must NEVER appear in System Management.
        // Only vests that have been paired/scanned and added to the company directory are shown.
        if (!data.inCompanyDirectory) return;

        // RULE 2: If the current admin belongs to a specific company, scope to this company
        if (currentAdminComp && data.companyId) {
          if (data.companyId.trim().toLowerCase() !== currentAdminComp) return;
        }

        vestList.push({ id: docSnap.id, ...data });
      });
      // Sort newest first by addedAt or vestId
      vestList.sort((a, b) => {
        const timeA = a.addedAt?.toDate ? a.addedAt.toDate().getTime() : (a.addedAt ? new Date(a.addedAt).getTime() : 0);
        const timeB = b.addedAt?.toDate ? b.addedAt.toDate().getTime() : (b.addedAt ? new Date(b.addedAt).getTime() : 0);
        return timeB - timeA || a.id.localeCompare(b.id);
      });
      setVests(vestList);
      setLoading(false);
    }, (err) => {
      console.error('Error listening to vests:', err);
      setLoading(false);
    });

    return () => unsubscribe();
  }, [user]);

  // Generate QR code Data URL whenever QR Viewer Modal opens
  useEffect(() => {
    if (showQrViewerModal) {
      QRCode.toDataURL(showQrViewerModal.id || showQrViewerModal.vestId, {
        width: 320,
        margin: 2,
        color: {
          dark: '#0f172a',
          light: '#ffffff'
        }
      })
      .then(url => setQrCodeDataUrl(url))
      .catch(err => console.error('Error generating QR code:', err));
    } else {
      setQrCodeDataUrl('');
    }
  }, [showQrViewerModal]);

  // Force Unassign Vest from Worker (Admin Override)
  const handleUnassignVest = async (vest) => {
    if (!vest.assignedWorkerId) return;
    const confirm = window.confirm(`Are you sure you want to unassign Vest ${vest.id} from Worker ${vest.assignedWorkerId}?`);
    if (!confirm) return;

    try {
      await updateDoc(doc(db, 'vests', vest.id), {
        assignedWorkerId: null,
        status: 'AVAILABLE',
        openAssignmentId: null
      });

      // Clear assignedVestId on worker
      if (vest.assignedWorkerId) {
        await updateDoc(doc(db, 'workers', vest.assignedWorkerId), {
          assignedVestId: null
        });
      }
      logActivity(`Unassigned vest: ${vest.id}`, currentAdminEmail);
      alert(`Vest ${vest.id} is now Unassigned and Available for SSO pairing.`);
    } catch (err) {
      console.error('Error unassigning vest:', err);
      alert('Could not unassign vest: ' + err.message);
    }
  };

  // Remove Vest from Company Directory (safely detaches without destroying the hardware record)
  const handleDeleteVest = async (vest) => {
    const confirm = window.confirm(`Remove Vest ${vest.id} from your company directory?\n\nThe vest will be detached from your company and returned to unassigned inventory. It can be re-paired anytime.`);
    if (!confirm) return;

    try {
      await updateDoc(doc(db, 'vests', vest.id), {
        inCompanyDirectory: false,
        companyId: null,
        siteId: null,
        addedByAdminId: null,
        addedByAdminEmail: null,
        addedByAdminName: null,
        addedBySsoUid: null,
        status: 'AVAILABLE',
        assignedWorkerId: null,
        removedFromDirectoryAt: new Date()
      });
      if (vest.assignedWorkerId) {
        await updateDoc(doc(db, 'workers', vest.assignedWorkerId), {
          assignedVestId: null
        });
      }
      logActivity(`Removed vest: ${vest.id} from directory`, currentAdminEmail);
      alert(`Vest ${vest.id} has been removed from your company directory.`);
    } catch (err) {
      console.error('Error removing vest:', err);
      alert('Could not remove vest: ' + err.message);
    }
  };

  // Filtered vests
  const filteredVests = vests.filter(vest => {
    const vId = (vest.id || vest.vestId || '').toLowerCase();
    const worker = vest.assignedWorkerId ? workersMap[vest.assignedWorkerId] : null;
    const workerName = worker ? `${worker.firstName || ''} ${worker.lastName || ''}`.toLowerCase() : '';
    const admin = (vest.addedByAdminName || vest.addedByAdminEmail || '').toLowerCase();
    
    const matchesSearch = vId.includes(searchQuery.toLowerCase()) || 
                          workerName.includes(searchQuery.toLowerCase()) || 
                          admin.includes(searchQuery.toLowerCase());

    const matchesStatus = statusFilter === 'ALL' ? true : 
                          statusFilter === 'ASSIGNED' ? Boolean(vest.assignedWorkerId) :
                          statusFilter === 'AVAILABLE' ? (!vest.assignedWorkerId && vest.status !== 'OFFLINE') :
                          statusFilter === 'OFFLINE' ? (vest.status === 'OFFLINE' || vest.online === false) : true;

    return matchesSearch && matchesStatus;
  });

  const totalPages = Math.ceil(filteredVests.length / itemsPerPage) || 1;
  const paginatedVests = filteredVests.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  // Stats calculation
  const totalVestsCount = vests.length;
  const assignedVestsCount = vests.filter(v => v.assignedWorkerId).length;
  const availableVestsCount = vests.filter(v => !v.assignedWorkerId && v.status !== 'OFFLINE').length;
  const offlineVestsCount = vests.filter(v => v.status === 'OFFLINE' || v.online === false).length;

  return (
    <div className="dashboard-content system-mgmt-container">
      {/* Page Header */}
      <div className="page-header-flex" style={{ flexWrap: 'wrap', gap: '1rem', marginBottom: '1.75rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: 'var(--text-main)', marginBottom: '0.25rem' }}>
            System Management
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>
            Hardware Provisioning Directory: Real-time oversight of smart safety vests, worker assignments, and hardware connectivity.
          </p>
        </div>
      </div>

      {/* Directory Summary Metrics Cards */}
      <div className="sys-metrics-grid">
        <div className="sys-stat-card">
          <div className="sys-stat-icon" style={{ backgroundColor: '#ffedd5', color: '#ea580c' }}>
            <FiCpu />
          </div>
          <div>
            <div className="stat-title">REGISTERED VESTS</div>
            <div className="stat-value">{totalVestsCount}</div>
            <span className="sys-stat-subtitle">In company directory</span>
          </div>
        </div>

        <div className="sys-stat-card">
          <div className="sys-stat-icon" style={{ backgroundColor: '#dbeafe', color: '#2563eb' }}>
            <FiUser />
          </div>
          <div>
            <div className="stat-title">ASSIGNED TO WORKERS</div>
            <div className="stat-value" style={{ color: '#2563eb' }}>{assignedVestsCount}</div>
            <span className="sys-stat-subtitle">Actively deployed</span>
          </div>
        </div>

        <div className="sys-stat-card">
          <div className="sys-stat-icon" style={{ backgroundColor: '#dcfce7', color: '#16a34a' }}>
            <FiCheckCircle />
          </div>
          <div>
            <div className="stat-title">AVAILABLE FOR SSOS</div>
            <div className="stat-value" style={{ color: '#16a34a' }}>{availableVestsCount}</div>
            <span className="sys-stat-subtitle">Ready to pair on site</span>
          </div>
        </div>

        <div className="sys-stat-card">
          <div className="sys-stat-icon" style={{ backgroundColor: '#f1f5f9', color: '#64748b' }}>
            <FiWifiOff />
          </div>
          <div>
            <div className="stat-title">OFFLINE / UNCONNECTED</div>
            <div className="stat-value" style={{ color: '#64748b' }}>{offlineVestsCount}</div>
            <span className="sys-stat-subtitle">Awaiting power / signal</span>
          </div>
        </div>
      </div>

      {/* Main Table Card */}
      <div className="um-table-container" style={{ marginTop: '1.5rem' }}>
        <div className="filter-bar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div className="search-bar um-search-bar" style={{ flex: '1 1 300px', backgroundColor: '#fff', border: '1px solid var(--border-color)', margin: 0 }}>
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder="Search by Vest ID, assigned worker, or admin name..." 
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
            />
          </div>

          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <select 
              className="filter-btn" 
              style={{ appearance: 'auto' }} 
              value={statusFilter} 
              onChange={(e) => { setStatusFilter(e.target.value); setCurrentPage(1); }}
            >
              <option value="ALL">All Hardware Status</option>
              <option value="AVAILABLE">Available for SSO</option>
              <option value="ASSIGNED">Assigned to Worker</option>
              <option value="OFFLINE">Offline / Unconnected</option>
            </select>
          </div>
        </div>

        {/* Vests List Table */}
        <div className="table-responsive-wrapper" style={{ overflowX: 'auto', width: '100%' }}>
          <table className="um-table" style={{ minWidth: '950px' }}>
            <thead>
              <tr>
                <th style={{ width: '22%' }}>SMART VEST ID & QR</th>
                <th style={{ width: '15%' }}>HARDWARE STATUS</th>
                <th style={{ width: '25%' }}>ASSIGNED WORKER</th>
                <th style={{ width: '22%' }}>REGISTERED BY (ADMIN)</th>
                <th className="align-right" style={{ width: '16%' }}>ACTIONS</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
                    <FiRefreshCw className="spin" style={{ marginRight: '0.5rem' }} /> Loading company vest directory...
                  </td>
                </tr>
              ) : paginatedVests.length === 0 ? (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', padding: '3.5rem 1rem' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.75rem' }}>
                      <div style={{ width: '56px', height: '56px', borderRadius: '50%', backgroundColor: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem', color: '#94a3b8' }}>
                        <FiCpu />
                      </div>
                      <h4 style={{ fontSize: '1.1rem', color: '#0f172a' }}>No safety vests found</h4>
                      <p style={{ color: '#64748b', fontSize: '0.875rem', maxWidth: '400px' }}>
                        No safety vests found in the company directory. Vests are provisioned in the hardware catalog and deployed by Site Safety Officers.
                      </p>
                    </div>
                  </td>
                </tr>
              ) : (
                paginatedVests.map((vest) => {
                  const worker = vest.assignedWorkerId ? workersMap[vest.assignedWorkerId] : null;
                  const isAssigned = Boolean(vest.assignedWorkerId);
                  const lastSeenMs = vest.lastSeen?.toDate ? vest.lastSeen.toDate().getTime() : (typeof vest.lastSeen === 'number' ? vest.lastSeen : 0);
                  const isRecent = lastSeenMs > 0 && (Date.now() - lastSeenMs < 45000);
                  const isOnline = (vest.online === true || vest.online === 'true') && (lastSeenMs === 0 || isRecent);

                  return (
                    <tr key={vest.id}>
                      {/* VEST ID & QR CLICKABLE */}
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                          <button 
                            type="button"
                            onClick={() => setShowQrViewerModal(vest)}
                            title="Click to view & print QR code"
                            style={{ 
                              width: '42px', 
                              height: '42px', 
                              backgroundColor: '#fff7ed', 
                              border: '1px solid #fed7aa',
                              borderRadius: '8px', 
                              display: 'flex', 
                              alignItems: 'center', 
                              justifyContent: 'center',
                              cursor: 'pointer',
                              color: '#ea580c',
                              fontSize: '1.2rem',
                              flexShrink: 0
                            }}
                          >
                            <FiCpu />
                          </button>
                          <div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                              <strong style={{ fontSize: '0.95rem', color: '#0f172a' }}>
                                {vest.id || vest.vestId}
                              </strong>
                              <button
                                type="button"
                                onClick={() => setShowQrViewerModal(vest)}
                                style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', padding: '2px' }}
                                title="View QR Label"
                              >
                                <FiEye style={{ fontSize: '0.9rem' }} />
                              </button>
                            </div>
                            {(vest.serialNumber || vest.esp32Serial) && (
                              <div style={{ fontSize: '0.75rem', color: '#ea580c', fontWeight: 700, fontFamily: 'monospace' }}>
                                ESP32: {vest.serialNumber || vest.esp32Serial}
                              </div>
                            )}
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '2px', flexWrap: 'wrap' }}>
                              <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                Battery: {vest.battery != null ? `${vest.battery}%` : '100%'}
                              </span>
                              {(() => {
                                const isOnline = vest.online === true || vest.status === 'AVAILABLE' || vest.status === 'ACTIVE';
                                const wifi = getWifiSignalInfo(vest.wifiRssi ?? vest.rssi, isOnline);
                                return (
                                  <span
                                    title={isOnline && (vest.wifiRssi != null || vest.rssi != null) ? `Wi-Fi Signal: ${wifi.text}` : 'Wi-Fi: Disconnected'}
                                    style={{
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      gap: '3px',
                                      fontSize: '0.7rem',
                                      fontWeight: 600,
                                      padding: '1px 6px',
                                      borderRadius: '4px',
                                      backgroundColor: wifi.bg,
                                      color: wifi.color,
                                    }}
                                  >
                                    {wifi.isOnline ? <FiWifi style={{ fontSize: '0.75rem' }} /> : <FiWifiOff style={{ fontSize: '0.75rem' }} />}
                                    {wifi.isOnline && (vest.wifiRssi != null || vest.rssi != null) ? `${vest.wifiRssi ?? vest.rssi} dBm` : wifi.label}
                                  </span>
                                );
                              })()}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* HARDWARE STATUS */}
                      <td>
                        {isAssigned ? (
                          <span className="sub-status-pill" style={{ backgroundColor: '#dbeafe', color: '#1e40af' }}>
                            <span className="sub-status-dot" style={{ backgroundColor: '#2563eb' }}></span>
                            Assigned
                          </span>
                        ) : vest.status === 'OFFLINE' ? (
                          <span className="sub-status-pill" style={{ backgroundColor: '#f1f5f9', color: '#475569' }}>
                            <span className="sub-status-dot" style={{ backgroundColor: '#94a3b8' }}></span>
                            Offline
                          </span>
                        ) : (
                          <span className="sub-status-pill active">
                            <span className="sub-status-dot"></span>
                            Available
                          </span>
                        )}
                      </td>

                      {/* ASSIGNED WORKER */}
                      <td>
                        {isAssigned ? (
                          <div style={{ display: 'flex', flexDirection: 'column' }}>
                            <strong style={{ color: '#0f172a', fontSize: '0.9rem' }}>
                              {worker ? `${worker.firstName} ${worker.lastName}` : vest.assignedWorkerId}
                            </strong>
                            <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                              {worker?.siteId ? `Site: ${worker.siteId}` : 'On site'} • ID: {vest.assignedWorkerId}
                            </span>
                          </div>
                        ) : (
                          <span style={{ 
                            fontSize: '0.8rem', 
                            color: '#16a34a', 
                            backgroundColor: '#f0fdf4', 
                            padding: '0.2rem 0.5rem', 
                            borderRadius: '4px',
                            border: '1px dashed #bbf7d0'
                          }}>
                            Ready for SSO Pairing
                          </span>
                        )}
                      </td>

                      {/* REGISTERED BY (ADMIN) */}
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                          <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#0f172a' }}>
                            {vest.addedByAdminName || vest.addedByAdminEmail || 'Web Admin'}
                          </span>
                          <span style={{ fontSize: '0.72rem', color: '#64748b' }}>
                            {vest.addedAt?.toDate ? vest.addedAt.toDate().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : 'Registered'}
                          </span>
                        </div>
                      </td>

                      {/* ACTIONS */}
                      <td className="align-right">
                        <div className="action-buttons">
                          <button 
                            className="action-btn" 
                            title="View & Print QR Code" 
                            onClick={() => setShowQrViewerModal(vest)}
                          >
                            <FiEye />
                          </button>

                          {isAssigned && (
                            <button 
                              className="action-btn" 
                              title="Force Unassign Vest"
                              onClick={() => handleUnassignVest(vest)}
                              style={{ color: '#d97706' }}
                            >
                              <FiMinimize2 />
                            </button>
                          )}

                          <button 
                            className="action-btn" 
                            title="Delete Vest"
                            onClick={() => handleDeleteVest(vest)}
                            style={{ color: '#ef4444' }}
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
        <div className="pagination" style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
            Showing {filteredVests.length === 0 ? 0 : ((currentPage - 1) * itemsPerPage) + 1} to {Math.min(currentPage * itemsPerPage, filteredVests.length)} of {filteredVests.length} vests
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <button className="page-btn" disabled={currentPage === 1} onClick={() => setCurrentPage(p => Math.max(1, p - 1))}><FiChevronLeft /></button>
            <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>Page {currentPage} of {totalPages}</span>
            <button className="page-btn" disabled={currentPage === totalPages} onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}><FiChevronRight /></button>
          </div>
        </div>
      </div>

      {/* ----------------- QR VIEWER & PRINT LABEL MODAL ----------------- */}
      {showQrViewerModal && (
        <div className="modal-overlay modal-backdrop" onClick={() => setShowQrViewerModal(null)}>
          <div className="modal-content modal-popup" onClick={e => e.stopPropagation()} style={{ maxWidth: '420px', padding: '2rem', textAlign: 'center' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>Vest Hardware Tag</h3>
              <button 
                type="button" 
                onClick={() => setShowQrViewerModal(null)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.35rem', color: '#94a3b8', padding: '0.2rem', display: 'flex', alignItems: 'center' }}
                title="Close"
              >
                <FiX />
              </button>
            </div>

            {/* Printable Vest Label Card */}
            <div 
              id="printable-vest-label" 
              style={{ 
                backgroundColor: '#ffffff', 
                border: '2px solid #0f172a', 
                borderRadius: '16px', 
                padding: '1.5rem', 
                margin: '0 auto 1.5rem auto',
                boxShadow: '0 4px 12px rgba(0,0,0,0.06)',
                maxWidth: '280px'
              }}
            >
              <div style={{ fontSize: '0.75rem', fontWeight: 800, letterSpacing: '0.1em', color: '#ea580c', textTransform: 'uppercase', marginBottom: '0.5rem' }}>
                BEVEST SAFETY HARDWARE
              </div>
              
              {qrCodeDataUrl ? (
                <img 
                  src={qrCodeDataUrl} 
                  alt={`QR for ${showQrViewerModal.id}`} 
                  style={{ width: '100%', height: 'auto', display: 'block', borderRadius: '8px' }} 
                />
              ) : (
                <div style={{ padding: '2rem', color: '#94a3b8' }}>Generating QR...</div>
              )}

              <div style={{ marginTop: '0.75rem', fontSize: '1.25rem', fontWeight: 800, color: '#0f172a', letterSpacing: '0.05em' }}>
                {showQrViewerModal.id || showQrViewerModal.vestId}
              </div>
              {(showQrViewerModal.serialNumber || showQrViewerModal.esp32Serial) && (
                <div style={{ fontSize: '0.85rem', color: '#ea580c', fontWeight: 700, fontFamily: 'monospace', marginTop: '0.2rem' }}>
                  ESP32 S/N: {showQrViewerModal.serialNumber || showQrViewerModal.esp32Serial}
                </div>
              )}
              <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.15rem' }}>
                Scan with BeVest Mobile App to Pair
              </div>
            </div>

            <div style={{ fontSize: '0.825rem', color: '#64748b', marginBottom: '1.5rem' }}>
              Status: <strong>{showQrViewerModal.assignedWorkerId ? `Assigned to Worker ${showQrViewerModal.assignedWorkerId}` : 'Available for SSO pairing'}</strong>
            </div>

            <div className="modal-actions" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.75rem', width: '100%' }}>
              <button 
                type="button" 
                className="btn-secondary"
                style={{ padding: '0.7rem 1.25rem', fontSize: '0.875rem', fontWeight: 600, borderRadius: '8px' }}
                onClick={() => {
                  const printWin = window.open('', '_blank');
                  if (printWin) {
                    printWin.document.write(`
                      <html>
                        <head>
                          <title>Print Label - ${showQrViewerModal.id}</title>
                          <style>
                            body { font-family: sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }
                            .card { border: 2px solid #000; border-radius: 12px; padding: 24px; text-align: center; width: 280px; }
                            .logo { font-size: 13px; font-weight: 800; color: #ea580c; margin-bottom: 8px; }
                            .id { font-size: 24px; font-weight: 800; margin-top: 12px; }
                            .sub { font-size: 11px; color: #555; }
                            img { width: 100%; height: auto; }
                          </style>
                        </head>
                        <body>
                          <div class="card">
                            <div class="logo">BEVEST SAFETY HARDWARE</div>
                            <img src="${qrCodeDataUrl}" />
                            <div class="id">${showQrViewerModal.id}</div>
                            ${(showQrViewerModal.serialNumber || showQrViewerModal.esp32Serial) ? `<div style="font-size:12px; font-weight:700; color:#ea580c; font-family:monospace; margin-top:2px; margin-bottom:4px;">ESP32 S/N: ${showQrViewerModal.serialNumber || showQrViewerModal.esp32Serial}</div>` : ''}
                            <div class="sub">Scan with BeVest Mobile to Pair</div>
                          </div>
                          <script>window.onload = function() { window.print(); window.close(); }</script>
                        </body>
                      </html>
                    `);
                    printWin.document.close();
                  }
                }}
              >
                <FiPrinter style={{ marginRight: '0.4rem' }} /> Print Label
              </button>

              {qrCodeDataUrl && (
                <a 
                  href={qrCodeDataUrl} 
                  download={`Bevest-${showQrViewerModal.id}-QR.png`}
                  className="btn-primary"
                  style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center', padding: '0.7rem 1.25rem', fontSize: '0.875rem', fontWeight: 600, borderRadius: '8px' }}
                >
                  <FiDownload style={{ marginRight: '0.4rem' }} /> Download QR
                </a>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SystemManagement;
