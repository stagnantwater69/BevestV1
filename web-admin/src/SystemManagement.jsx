import React, { useState, useEffect, useRef } from 'react';
import { 
  FiCamera, FiUploadCloud, FiSearch, FiCheckCircle, FiAlertCircle, 
  FiRefreshCw, FiTrash2, FiPrinter, FiDownload, FiUser, FiShield, FiCpu, 
  FiWifi, FiWifiOff, FiX, FiEye, FiCheck, FiChevronLeft, FiChevronRight,
  FiClock, FiTag, FiMinimize2
} from 'react-icons/fi';
import { 
  collection, doc, getDoc, getDocs, setDoc, deleteDoc, updateDoc, onSnapshot 
} from 'firebase/firestore';
import { db } from './firebase';
import jsQR from 'jsqr';
import QRCode from 'qrcode';
import { logActivity } from './activityLogger';

const SystemManagement = ({ user }) => {
  const [vests, setVests] = useState([]);
  const [workersMap, setWorkersMap] = useState({});
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 6;

  // Modals state
  const [showScannerModal, setShowScannerModal] = useState(false);
  const [showQrViewerModal, setShowQrViewerModal] = useState(null); // vest object

  // Form states
  const [scanFeedback, setScanFeedback] = useState({ type: '', message: '' });
  const [isProcessing, setIsProcessing] = useState(false);
  const [qrCodeDataUrl, setQrCodeDataUrl] = useState('');

  // Camera scanner references
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const animationFrameId = useRef(null);
  const streamRef = useRef(null);
  const [cameraError, setCameraError] = useState('');
  const [isCameraActive, setIsCameraActive] = useState(false);

  // File upload input ref
  const fileInputRef = useRef(null);

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

  // 2. Subscribe to Vests in real-time
  useEffect(() => {
    setLoading(true);
    const q = collection(db, 'vests');
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const vestList = [];
      snapshot.forEach((docSnap) => {
        vestList.push({ id: docSnap.id, ...docSnap.data() });
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
  }, []);

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

  // Clean parsed vest ID
  const parseVestId = (raw) => {
    if (!raw) return '';
    let text = String(raw).trim();
    // If JSON payload like {"vestId":"VEST-001"}
    try {
      if (text.startsWith('{') && text.endsWith('}')) {
        const parsed = JSON.parse(text);
        if (parsed.vestId) return String(parsed.vestId).trim().toUpperCase();
        if (parsed.id) return String(parsed.id).trim().toUpperCase();
      }
    } catch (_) {}
    // If URL payload like https://bevest.com/vest/VEST-001
    if (text.includes('/vest/')) {
      const parts = text.split('/vest/');
      text = parts[parts.length - 1];
    }
    // Remove query params if any
    text = text.split('?')[0].replace(/[^a-zA-Z0-9_-]/g, '');
    return text.toUpperCase();
  };

  // Add Vest to Database & Company Directory with Developer Verification
  const registerScannedVest = async (rawId) => {
    const vestId = parseVestId(rawId);
    if (!vestId) {
      setScanFeedback({ type: 'error', message: 'Invalid or empty Vest ID from QR Code.' });
      return false;
    }

    setIsProcessing(true);
    setScanFeedback({ type: '', message: '' });

    try {
      // 1. VERIFY VEST AGAINST DEVELOPER HARDWARE DATABASE (vestCatalog)
      let catalogVest = null;
      try {
        const catSnap = await getDoc(doc(db, 'vestCatalog', vestId));
        if (catSnap.exists()) {
          catalogVest = catSnap.data();
        }
      } catch (err) {
        console.warn('Developer catalog direct check:', err.message);
      }

      // Check local storage hardware catalog fallback
      if (!catalogVest) {
        try {
          const localCat = JSON.parse(localStorage.getItem('bevest_vest_catalog') || '{}');
          if (localCat[vestId]) {
            catalogVest = localCat[vestId];
          }
        } catch (_) {}
      }

      // If vest is NOT found in the Developer Database: Refuse registration
      if (!catalogVest) {
        setScanFeedback({
          type: 'error',
          message: `❌ Vest Verification Failed: Vest "${vestId}" was NOT found in the Bevest Developer hardware database. Only authentic vests generated by a Bevest Developer can be registered.`
        });
        setIsProcessing(false);
        return false;
      }

      // If already registered to another company/contractor:
      if (catalogVest.catalogStatus === 'REGISTERED' && catalogVest.registeredToAdmin && catalogVest.registeredToAdmin !== currentAdminEmail) {
        setScanFeedback({
          type: 'warning',
          message: `⚠️ Vest "${vestId}" is already registered to ${catalogVest.registeredToAdminName || catalogVest.registeredToAdmin}. It cannot be registered to another organization.`
        });
        setIsProcessing(false);
        return false;
      }

      // 2. Check if vest already exists in this company's local database
      const existing = vests.find(v => (v.id || v.vestId || '').toUpperCase() === vestId);
      if (existing) {
        const assignedWorker = existing.assignedWorkerId ? workersMap[existing.assignedWorkerId] : null;
        const workerInfo = assignedWorker 
          ? ` Currently assigned to ${assignedWorker.firstName} ${assignedWorker.lastName} (${assignedWorker.id}).`
          : ' Currently available for SSO pairing.';
        setScanFeedback({
          type: 'warning',
          message: `Vest "${vestId}" is already registered in your company database! Status: ${existing.status || 'AVAILABLE'}.${workerInfo}`
        });
        setIsProcessing(false);
        return false;
      }

      // 3. Register to company's Firestore collection 'vests'
      const now = new Date();
      await setDoc(doc(db, 'vests', vestId), {
        vestId: vestId,
        status: 'AVAILABLE',
        model: catalogVest.model || 'Bevest Smart IoT Vest v2.1',
        batchNumber: catalogVest.batchNumber || 'N/A',
        firmwareVersion: catalogVest.firmwareVersion || 'v1.4.2',
        verifiedByDeveloper: true,
        assignedWorkerId: null,
        battery: 100,
        online: false,
        lastSeen: null,
        openAssignmentId: null,
        inCompanyDirectory: true,
        addedByAdminId: user?.uid || 'admin',
        addedByAdminEmail: currentAdminEmail,
        addedByAdminName: currentAdminName,
        addedAt: now
      });

      // 4. Update Developer Central Hardware Database status to REGISTERED
      try {
        await updateDoc(doc(db, 'vestCatalog', vestId), {
          catalogStatus: 'REGISTERED',
          registeredToAdmin: currentAdminEmail,
          registeredToAdminName: currentAdminName,
          registeredToId: user?.uid || 'admin',
          registeredAt: now
        });
      } catch (_) {}

      // Update local storage catalog cache
      try {
        const localCat = JSON.parse(localStorage.getItem('bevest_vest_catalog') || '{}');
        if (localCat[vestId]) {
          localCat[vestId].catalogStatus = 'REGISTERED';
          localCat[vestId].registeredToAdmin = currentAdminEmail;
          localCat[vestId].registeredAt = now;
          localStorage.setItem('bevest_vest_catalog', JSON.stringify(localCat));
        }
      } catch (_) {}

      setScanFeedback({
        type: 'success',
        message: `✓ Verified & Registered! Vest "${vestId}" (Model: ${catalogVest.model || 'Bevest Smart Vest'}, Batch: ${catalogVest.batchNumber || 'N/A'}) was verified in the Developer Database and registered to your company directory!`
      });
      logActivity(`Verified & registered smart vest from Developer Catalog: ${vestId}`, currentAdminEmail);
      setIsProcessing(false);
      return true;
    } catch (err) {
      console.error('Error registering vest:', err);
      setScanFeedback({
        type: 'error',
        message: `Failed to register vest: ${err.message}`
      });
      setIsProcessing(false);
      return false;
    }
  };

  // Start Live Camera QR Scanner
  const startCamera = async () => {
    setCameraError('');
    setIsCameraActive(true);
    setScanFeedback({ type: '', message: '' });

    try {
      const constraints = {
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1280 },
          height: { ideal: 720 }
        }
      };

      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      streamRef.current = stream;

      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        videoRef.current.setAttribute('playsinline', 'true');
        await videoRef.current.play();
        scanFrame();
      }
    } catch (err) {
      console.error('Camera access error:', err);
      setCameraError('Unable to access camera. Please allow camera permissions or upload a QR image.');
      setIsCameraActive(false);
    }
  };

  // Stop Camera
  const stopCamera = () => {
    if (animationFrameId.current) {
      cancelAnimationFrame(animationFrameId.current);
      animationFrameId.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    setIsCameraActive(false);
  };

  // Scan Video Frame in a Loop
  const scanFrame = () => {
    if (!videoRef.current || videoRef.current.readyState !== videoRef.current.HAVE_ENOUGH_DATA) {
      animationFrameId.current = requestAnimationFrame(scanFrame);
      return;
    }

    const video = videoRef.current;
    const canvas = canvasRef.current || document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const code = jsQR(imageData.data, imageData.width, imageData.height, {
      inversionAttempts: 'dontInvert',
    });

    if (code && code.data) {
      stopCamera();
      registerScannedVest(code.data);
      return;
    }

    animationFrameId.current = requestAnimationFrame(scanFrame);
  };

  // Handle Upload QR Code Image
  const handleImageUpload = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setScanFeedback({ type: '', message: '' });
    setIsProcessing(true);

    const reader = new FileReader();
    reader.onload = (event) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = img.width;
        canvas.height = img.height;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, img.width, img.height);

        const imageData = ctx.getImageData(0, 0, img.width, img.height);
        const code = jsQR(imageData.data, imageData.width, imageData.height);

        if (code && code.data) {
          registerScannedVest(code.data);
        } else {
          setScanFeedback({
            type: 'error',
            message: 'No readable QR code found in the uploaded image. Please make sure the code is sharp and not glare-obscured.'
          });
          setIsProcessing(false);
        }
      };
      img.onerror = () => {
        setScanFeedback({ type: 'error', message: 'Failed to process image file.' });
        setIsProcessing(false);
      };
      img.src = event.target.result;
    };
    reader.readAsDataURL(file);
    // Reset file input so user can pick the same file again if desired
    e.target.value = '';
  };

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

  // Delete Vest from Company Directory
  const handleDeleteVest = async (vest) => {
    const confirm = window.confirm(`Permanently remove Vest ${vest.id} from the company directory? This action cannot be undone.`);
    if (!confirm) return;

    try {
      await deleteDoc(doc(db, 'vests', vest.id));
      if (vest.assignedWorkerId) {
        await updateDoc(doc(db, 'workers', vest.assignedWorkerId), {
          assignedVestId: null
        });
      }
      logActivity(`Removed vest: ${vest.id} from directory`, currentAdminEmail);
      alert(`Vest ${vest.id} removed from company directory.`);
    } catch (err) {
      console.error('Error deleting vest:', err);
      alert('Could not remove vest: ' + err.message);
    }
  };

  // Close scanner modal and cleanup camera
  const closeScannerModal = () => {
    stopCamera();
    setShowScannerModal(false);
    setScanFeedback({ type: '', message: '' });
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
            Hardware Provisioning Directory: Scan or upload Vest QR codes to register safety vests for SSO worker pairing.
          </p>
        </div>

        {/* Action Buttons for QR Scanning and Adding */}
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button 
            className="btn-primary" 
            onClick={() => { setShowScannerModal(true); startCamera(); }}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}
          >
            <FiCamera style={{ fontSize: '1.15rem' }} /> Scan QR Code
          </button>

          <button 
            className="btn-secondary" 
            onClick={() => fileInputRef.current?.click()}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}
          >
            <FiUploadCloud style={{ fontSize: '1.15rem' }} /> Upload QR Image
          </button>
          <input 
            type="file" 
            ref={fileInputRef} 
            onChange={handleImageUpload} 
            accept="image/*" 
            style={{ display: 'none' }} 
          />
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
                        Scan or upload a vest QR code above to register your first hardware vest to the company directory.
                      </p>
                      <button 
                        className="btn-primary" 
                        onClick={() => { setShowScannerModal(true); startCamera(); }}
                        style={{ marginTop: '0.5rem' }}
                      >
                        <FiCamera style={{ marginRight: '0.4rem' }} /> Scan Vest QR Code
                      </button>
                    </div>
                  </td>
                </tr>
              ) : (
                paginatedVests.map((vest) => {
                  const worker = vest.assignedWorkerId ? workersMap[vest.assignedWorkerId] : null;
                  const isAssigned = Boolean(vest.assignedWorkerId);
                  const isOnline = vest.online === true || vest.online === 'true';

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
                            <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                              Battery: {vest.battery != null ? `${vest.battery}%` : '100%'}
                            </span>
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

      {/* ----------------- CAMERA SCANNER MODAL ----------------- */}
      {showScannerModal && (
        <div className="modal-overlay modal-backdrop" onClick={closeScannerModal}>
          <div className="modal-content modal-popup" onClick={e => e.stopPropagation()} style={{ maxWidth: '480px', padding: '2rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <FiCamera style={{ color: 'var(--primary-color)', fontSize: '1.25rem' }} />
                <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>Scan Vest QR Code</h3>
              </div>
              <button 
                type="button" 
                onClick={closeScannerModal}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.35rem', color: '#94a3b8', padding: '0.2rem', display: 'flex', alignItems: 'center' }}
                title="Close"
              >
                <FiX />
              </button>
            </div>

            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: '0 0 1.25rem 0', lineHeight: 1.5 }}>
              Point the camera at the QR code printed on the BeVest safety vest to automatically register it into the company directory.
            </p>

            {/* Video Viewfinder */}
            <div className="qr-scanner-viewfinder-box" style={{ position: 'relative', width: '100%', height: '260px', backgroundColor: '#000', borderRadius: '12px', overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {cameraError ? (
                <div style={{ padding: '1rem', color: '#f87171', textAlign: 'center', fontSize: '0.85rem' }}>
                  <FiAlertCircle style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }} /><br />
                  {cameraError}
                </div>
              ) : (
                <>
                  <video 
                    ref={videoRef} 
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
                  />
                  {/* Scanner overlay target frame */}
                  <div className="scanner-target-frame" style={{ position: 'absolute', width: '180px', height: '180px', border: '2px solid rgba(249, 115, 22, 0.8)', borderRadius: '16px', boxShadow: '0 0 0 9999px rgba(0, 0, 0, 0.45)', pointerEvents: 'none' }}>
                    <div className="scanner-laser-line" style={{ position: 'absolute', width: '100%', height: '2px', backgroundColor: '#f97316', boxShadow: '0 0 8px #f97316', animation: 'scanAnimation 2s infinite ease-in-out' }} />
                  </div>
                </>
              )}
            </div>

            {/* Feedback message */}
            {scanFeedback.message && (
              <div 
                style={{ 
                  marginTop: '1rem', 
                  padding: '0.75rem 1rem', 
                  borderRadius: '8px', 
                  fontSize: '0.85rem',
                  lineHeight: 1.45,
                  backgroundColor: scanFeedback.type === 'success' ? '#ecfdf5' : scanFeedback.type === 'warning' ? '#fffbeb' : '#fef2f2',
                  color: scanFeedback.type === 'success' ? '#065f46' : scanFeedback.type === 'warning' ? '#92400e' : '#991b1b',
                  border: `1px solid ${scanFeedback.type === 'success' ? '#a7f3d0' : scanFeedback.type === 'warning' ? '#fde68a' : '#fecaca'}`
                }}
              >
                {scanFeedback.message}
              </div>
            )}

            {/* Action Buttons in Scanner Modal */}
            <div className="modal-actions" style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '0.75rem', marginTop: '1.5rem', width: '100%' }}>
              <button 
                type="button" 
                className="btn-secondary" 
                onClick={() => fileInputRef.current?.click()}
                style={{ padding: '0.7rem 1.25rem', fontSize: '0.875rem', fontWeight: 600, borderRadius: '8px' }}
              >
                <FiUploadCloud style={{ marginRight: '0.4rem' }} /> Or Upload Image
              </button>
              <button 
                type="button" 
                className="btn-primary" 
                onClick={closeScannerModal}
                style={{ padding: '0.7rem 1.25rem', fontSize: '0.875rem', fontWeight: 600, borderRadius: '8px' }}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

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
