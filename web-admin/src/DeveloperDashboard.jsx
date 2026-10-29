import React, { useState, useEffect, useRef } from 'react';
import { 
  FiCpu, FiCreditCard, FiTool, FiActivity, FiLogOut, FiPlus, 
  FiSearch, FiFilter, FiDownload, FiPrinter, FiCheckCircle, 
  FiAlertCircle, FiClock, FiCalendar, FiDollarSign, FiUsers, 
  FiRefreshCw, FiEye, FiTrash2, FiShield, FiTag, FiFileText, 
  FiAlertTriangle, FiCheck, FiX, FiLayers
} from 'react-icons/fi';
import { 
  collection, doc, getDocs, setDoc, updateDoc, deleteDoc, 
  query, where, onSnapshot, getDoc 
} from 'firebase/firestore';
import { db } from './firebase';
import QRCode from 'qrcode';
import { logActivity } from './activityLogger';
import { SUBSCRIPTION_PLANS, formatPhp, formatDisplayDate } from './subscriptionPlans';
import logo from './assets/bevest_logo.png';
import './developer.css';

const DeveloperDashboard = ({ user, onLogout }) => {
  const [activeTab, setActiveTab] = useState('hardware'); // 'hardware' | 'subscriptions' | 'troubleshoot' | 'activity'
  
  // Vest Catalog state
  const [catalog, setCatalog] = useState([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogSearch, setCatalogSearch] = useState('');
  const [catalogFilter, setCatalogFilter] = useState('ALL'); // 'ALL' | 'UNREGISTERED' | 'REGISTERED' | 'MAINTENANCE'
  
  // QR Generator Form state
  const [genVestId, setGenVestId] = useState(`VEST-2026-${Math.floor(1000 + Math.random() * 9000)}`);
  const [genModel, setGenModel] = useState('Bevest Smart IoT Vest v2.1');
  const [genBatch, setGenBatch] = useState('BATCH-2026-Q4');
  const [genFirmware, setGenFirmware] = useState('v1.4.2');
  const [genToken, setGenToken] = useState(() => Math.random().toString(16).substring(2, 10).toUpperCase());
  const [liveQrDataUrl, setLiveQrDataUrl] = useState('');
  const [uploading, setUploading] = useState(false);
  const [toastMsg, setToastMsg] = useState({ type: '', text: '' });

  // Batch Generator Modal
  const [showBatchModal, setShowBatchModal] = useState(false);
  const [batchCount, setBatchCount] = useState(10);
  const [batchPrefix, setBatchPrefix] = useState('VEST-2026-');
  const [batchGenerating, setBatchGenerating] = useState(false);

  // QR Viewer Modal
  const [selectedVestQr, setSelectedVestQr] = useState(null);
  const [viewQrDataUrl, setViewQrDataUrl] = useState('');

  // Subscriptions & Contractors state
  const [contractors, setContractors] = useState([]);
  const [contractorsLoading, setContractorsLoading] = useState(true);
  const [contractorSearch, setContractorSearch] = useState('');
  
  // Extend Subscription Modal
  const [extendingContractor, setExtendingContractor] = useState(null);
  const [extensionDays, setExtensionDays] = useState(30);
  const [extensionNotes, setExtensionNotes] = useState('');
  const [extendingLoading, setExtendingLoading] = useState(false);

  // Payment Recording Modal
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [paymentContractor, setPaymentContractor] = useState(null);
  const [paymentAmount, setPaymentAmount] = useState(200000);
  const [paymentMethod, setPaymentMethod] = useState('Bank Wire (BDO)');
  const [paymentRef, setPaymentRef] = useState(`TXN-${Date.now().toString().slice(-6)}`);
  const [paymentNotes, setPaymentNotes] = useState('');
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [paymentHistory, setPaymentHistory] = useState([]);

  // Troubleshoot State
  const [troubleshootTarget, setTroubleshootTarget] = useState(null);
  const [troubleshootStatus, setTroubleshootStatus] = useState('Active');
  const [troubleshootVestBoost, setTroubleshootVestBoost] = useState(0);
  const [troubleshootSsoBoost, setTroubleshootSsoBoost] = useState(0);
  const [troubleshootNotes, setTroubleshootNotes] = useState('');
  const [troubleshootSaving, setTroubleshootSaving] = useState(false);

  // Activity logs
  const [activityLogs, setActivityLogs] = useState([]);

  const devEmail = user?.email || 'developer@bevest.com';
  const devName = user?.profile?.firstName 
    ? `${user.profile.firstName} ${user.profile.lastName || ''}`.trim()
    : 'Bevest Developer';

  const showToast = (type, text) => {
    setToastMsg({ type, text });
    setTimeout(() => setToastMsg({ type: '', text: '' }), 5000);
  };

  // 1. Generate Live QR Code Preview
  useEffect(() => {
    const payload = JSON.stringify({
      vestId: genVestId.trim(),
      verified: true,
      batch: genBatch.trim(),
      mfg: new Date().toISOString().split('T')[0],
      token: genToken
    });

    QRCode.toDataURL(payload, {
      width: 280,
      margin: 2,
      color: {
        dark: '#0f172a',
        light: '#ffffff'
      }
    })
    .then(url => setLiveQrDataUrl(url))
    .catch(err => console.error('Error generating preview QR:', err));
  }, [genVestId, genBatch, genToken]);

  // 2. Subscribe to Central Hardware Catalog (vestCatalog)
  useEffect(() => {
    setCatalogLoading(true);
    const catalogRef = collection(db, 'vestCatalog');
    
    const unsubscribe = onSnapshot(catalogRef, (snap) => {
      const items = [];
      snap.forEach(d => items.push({ id: d.id, ...d.data() }));
      
      // If empty, seed initial hardware catalog records
      if (items.length === 0) {
        seedInitialCatalog();
      } else {
        items.sort((a, b) => (b.createdAt?.toDate ? b.createdAt.toDate().getTime() : 0) - (a.createdAt?.toDate ? a.createdAt.toDate().getTime() : 0));
        setCatalog(items);
        // Sync to localStorage for offline fallback
        try {
          const map = {};
          items.forEach(v => { map[v.vestId || v.id] = v; });
          localStorage.setItem('bevest_vest_catalog', JSON.stringify(map));
        } catch (_) {}
      }
      setCatalogLoading(false);
    }, (err) => {
      console.warn('Fallback loading catalog from local storage:', err.message);
      try {
        const raw = localStorage.getItem('bevest_vest_catalog');
        if (raw) {
          const map = JSON.parse(raw);
          setCatalog(Object.values(map));
        }
      } catch (_) {}
      setCatalogLoading(false);
    });

    return () => unsubscribe();
  }, []);

  // 3. Seed Initial Hardware Catalog if empty
  const seedInitialCatalog = async () => {
    const seeds = [
      {
        vestId: 'VEST-001',
        model: 'Bevest Smart IoT Vest v2.0',
        batchNumber: 'BATCH-2026-Q1',
        firmwareVersion: 'v1.4.0',
        hardwareToken: 'A1B2-C3D4',
        catalogStatus: 'REGISTERED',
        verified: true,
        registeredToAdmin: 'testwebadmin@bevest.com',
        registeredAt: new Date()
      },
      {
        vestId: 'VEST-002',
        model: 'Bevest Smart IoT Vest v2.0',
        batchNumber: 'BATCH-2026-Q1',
        firmwareVersion: 'v1.4.0',
        hardwareToken: 'E5F6-G7H8',
        catalogStatus: 'REGISTERED',
        registeredToAdmin: 'testwebadmin@bevest.com',
        registeredAt: new Date()
      },
      {
        vestId: 'VEST-2026-0001',
        model: 'Bevest Smart IoT Vest v2.1',
        batchNumber: 'BATCH-2026-Q4',
        firmwareVersion: 'v1.4.2',
        hardwareToken: '8A9F-22C1',
        catalogStatus: 'UNREGISTERED',
        verified: true,
        registeredToAdmin: null,
        registeredAt: null
      },
      {
        vestId: 'VEST-2026-0002',
        model: 'Bevest Smart IoT Vest v2.1',
        batchNumber: 'BATCH-2026-Q4',
        firmwareVersion: 'v1.4.2',
        hardwareToken: '4E7B-90A3',
        catalogStatus: 'UNREGISTERED',
        verified: true,
        registeredToAdmin: null,
        registeredAt: null
      },
      {
        vestId: 'VEST-2026-0003',
        model: 'Bevest Smart IoT Vest v2.1',
        batchNumber: 'BATCH-2026-Q4',
        firmwareVersion: 'v1.4.2',
        hardwareToken: '3C1D-89F0',
        catalogStatus: 'UNREGISTERED',
        verified: true,
        registeredToAdmin: null,
        registeredAt: null
      }
    ];

    const localMap = {};
    for (const item of seeds) {
      item.createdAt = new Date();
      item.createdByEmail = devEmail;
      localMap[item.vestId] = item;
      try {
        await setDoc(doc(db, 'vestCatalog', item.vestId), item, { merge: true });
      } catch (_) {}
    }
    localStorage.setItem('bevest_vest_catalog', JSON.stringify(localMap));
    setCatalog(seeds);
  };

  // 4. Subscribe to Contractors (for Subscriptions & Payments)
  useEffect(() => {
    setContractorsLoading(true);
    const usersRef = collection(db, 'users');
    const q = query(usersRef, where('role', '==', 'CONTRACTOR'));

    const unsubscribe = onSnapshot(q, (snap) => {
      const list = [];
      snap.forEach(d => list.push({ id: d.id, ...d.data() }));
      setContractors(list);
      setContractorsLoading(false);
    }, (err) => {
      console.warn('Contractors fetch error:', err.message);
      // Fallback from local user directory
      try {
        const raw = localStorage.getItem('bevest_user_directory');
        if (raw) {
          const dict = JSON.parse(raw);
          const fallbackList = Object.entries(dict)
            .filter(([_, u]) => (u.role || '').toUpperCase() === 'CONTRACTOR')
            .map(([em, u]) => ({ id: em, email: em, ...u }));
          setContractors(fallbackList);
        }
      } catch (_) {}
      setContractorsLoading(false);
    });

    return () => unsubscribe();
  }, []);

  // 5. Subscribe to Payment History
  useEffect(() => {
    const paymentsRef = collection(db, 'subscriptionPayments');
    const unsubscribe = onSnapshot(paymentsRef, (snap) => {
      const pList = [];
      snap.forEach(d => pList.push({ id: d.id, ...d.data() }));
      pList.sort((a, b) => (b.paymentDate?.toDate ? b.paymentDate.toDate().getTime() : 0) - (a.paymentDate?.toDate ? a.paymentDate.toDate().getTime() : 0));
      setPaymentHistory(pList);
    }, (err) => {
      console.warn('Payments load error:', err.message);
    });
    return () => unsubscribe();
  }, []);

  // Generate a new random Vest ID
  const handleRegenerateId = () => {
    const num = Math.floor(1000 + Math.random() * 9000);
    setGenVestId(`VEST-2026-${num}`);
    setGenToken(Math.random().toString(16).substring(2, 10).toUpperCase());
  };

  // Upload single vest to database
  const handleUploadSingleVest = async (e) => {
    e.preventDefault();
    const cleanId = genVestId.trim().toUpperCase();
    if (!cleanId) {
      showToast('error', 'Please provide a valid Vest ID.');
      return;
    }

    setUploading(true);
    try {
      const now = new Date();
      const vestRecord = {
        vestId: cleanId,
        model: genModel.trim(),
        batchNumber: genBatch.trim(),
        firmwareVersion: genFirmware.trim(),
        hardwareToken: genToken,
        catalogStatus: 'UNREGISTERED',
        verified: true,
        mfgDate: now,
        createdAt: now,
        createdByEmail: devEmail,
        registeredToAdmin: null,
        registeredToContractorId: null,
        registeredAt: null
      };

      await setDoc(doc(db, 'vestCatalog', cleanId), vestRecord);
      
      // Update local storage cache
      try {
        const local = JSON.parse(localStorage.getItem('bevest_vest_catalog') || '{}');
        local[cleanId] = vestRecord;
        localStorage.setItem('bevest_vest_catalog', JSON.stringify(local));
      } catch (_) {}

      await logActivity(`Developer generated & uploaded vest: ${cleanId} (Batch: ${genBatch})`, devEmail);
      showToast('success', `✓ Vest ${cleanId} uploaded to Developer Hardware Database! Ready for Admin/SSO scan.`);
      
      handleRegenerateId();
    } catch (err) {
      console.error('Error uploading vest to catalog:', err);
      showToast('error', 'Failed to upload vest: ' + err.message);
    } finally {
      setUploading(false);
    }
  };

  // Batch generate vests
  const handleBatchGenerate = async () => {
    const count = parseInt(batchCount, 10) || 10;
    setBatchGenerating(true);
    try {
      const startNum = Math.floor(1000 + Math.random() * 8000);
      const batchItems = [];
      const now = new Date();

      for (let i = 0; i < count; i++) {
        const currentId = `${batchPrefix}${startNum + i}`;
        const item = {
          vestId: currentId,
          model: genModel.trim(),
          batchNumber: genBatch.trim(),
          firmwareVersion: genFirmware.trim(),
          hardwareToken: Math.random().toString(16).substring(2, 10).toUpperCase(),
          catalogStatus: 'UNREGISTERED',
          verified: true,
          mfgDate: now,
          createdAt: now,
          createdByEmail: devEmail,
          registeredToAdmin: null,
          registeredToContractorId: null,
          registeredAt: null
        };
        batchItems.push(item);
        await setDoc(doc(db, 'vestCatalog', currentId), item);
      }

      // Sync local storage
      try {
        const local = JSON.parse(localStorage.getItem('bevest_vest_catalog') || '{}');
        batchItems.forEach(b => { local[b.vestId] = b; });
        localStorage.setItem('bevest_vest_catalog', JSON.stringify(local));
      } catch (_) {}

      await logActivity(`Developer batch generated ${count} vests (${batchPrefix}${startNum}..${batchPrefix}${startNum + count - 1})`, devEmail);
      showToast('success', `✓ Successfully generated and uploaded batch of ${count} vests!`);
      setShowBatchModal(false);
    } catch (err) {
      console.error('Batch generation error:', err);
      showToast('error', 'Batch generation failed: ' + err.message);
    } finally {
      setBatchGenerating(false);
    }
  };

  // Open QR Viewer Modal for a catalog item
  const handleOpenQrViewer = (vest) => {
    setSelectedVestQr(vest);
    const payload = JSON.stringify({
      vestId: vest.vestId || vest.id,
      verified: true,
      batch: vest.batchNumber || 'N/A',
      token: vest.hardwareToken || 'N/A'
    });
    QRCode.toDataURL(payload, {
      width: 320,
      margin: 2,
      color: { dark: '#0f172a', light: '#ffffff' }
    })
    .then(url => setViewQrDataUrl(url))
    .catch(console.error);
  };

  // Print single QR Label
  const handlePrintLabel = (vestId, batch, qrUrl) => {
    const printWin = window.open('', '_blank', 'width=600,height=600');
    if (!printWin) return;
    printWin.document.write(`
      <html>
        <head>
          <title>Print Vest Label - ${vestId}</title>
          <style>
            body { font-family: sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; background: #f8fafc; }
            .label-box { border: 2px dashed #0f172a; padding: 24px; text-align: center; border-radius: 12px; background: #fff; width: 300px; }
            .title { font-size: 18px; font-weight: 800; color: #0f172a; margin-bottom: 6px; }
            .sub { font-size: 11px; color: #64748b; font-weight: 600; text-transform: uppercase; margin-bottom: 16px; }
            img { width: 220px; height: 220px; }
            .id-code { font-size: 22px; font-weight: 800; color: #0f172a; margin-top: 12px; letter-spacing: 0.05em; }
            .batch { font-size: 11px; color: #64748b; font-weight: 700; margin-top: 4px; }
            .security { font-size: 9px; color: #94a3b8; margin-top: 12px; border-top: 1px solid #e2e8f0; padding-top: 8px; }
          </style>
        </head>
        <body>
          <div class="label-box">
            <div class="title">BEVEST SMART IOT</div>
            <div class="sub">Authentic Hardware QR</div>
            <img src="${qrUrl}" />
            <div class="id-code">${vestId}</div>
            <div class="batch">${batch} • VERIFIED</div>
            <div class="security">Scan with Bevest Admin or SSO App to Register</div>
          </div>
          <script>
            window.onload = function() { window.print(); window.close(); }
          </script>
        </body>
      </html>
    `);
    printWin.document.close();
  };

  // Extend Subscription for Contractor
  const handleConfirmExtension = async () => {
    if (!extendingContractor) return;
    setExtendingLoading(true);
    try {
      const days = parseInt(extensionDays, 10) || 30;
      const sub = extendingContractor.subscription || {};
      
      // Calculate new renewal date
      const currentRenew = sub.renewDate?.toDate ? sub.renewDate.toDate() : (sub.renewDate ? new Date(sub.renewDate) : new Date());
      const baseDate = currentRenew > new Date() ? currentRenew : new Date();
      const newRenew = new Date(baseDate.getTime() + days * 24 * 60 * 60 * 1000);

      const updatedSubscription = {
        ...sub,
        status: 'Active',
        renewDate: newRenew,
        lastExtendedAt: new Date(),
        lastExtendedBy: devEmail,
        lastExtensionDays: days,
        extensionNotes: extensionNotes.trim()
      };

      await updateDoc(doc(db, 'users', extendingContractor.id), {
        subscription: updatedSubscription
      });

      await logActivity(`Developer extended subscription for ${extendingContractor.email} by +${days} days (New expiry: ${formatDisplayDate(newRenew)})`, devEmail);
      showToast('success', `✓ Extended subscription for ${extendingContractor.email} by +${days} days!`);
      setExtendingContractor(null);
      setExtensionNotes('');
    } catch (err) {
      console.error('Error extending subscription:', err);
      showToast('error', 'Failed to extend subscription: ' + err.message);
    } finally {
      setExtendingLoading(false);
    }
  };

  // Record & Approve Subscription Payment
  const handleRecordPayment = async (e) => {
    e.preventDefault();
    if (!paymentContractor) {
      showToast('error', 'Please select a contractor.');
      return;
    }

    setPaymentLoading(true);
    try {
      const now = new Date();
      const sub = paymentContractor.subscription || {};
      const plan = SUBSCRIPTION_PLANS[sub.planId] || SUBSCRIPTION_PLANS.BUSINESS;
      
      // Add 1 month or 1 year extension upon payment approval
      const cycle = sub.billingCycle || 'monthly';
      const monthsToAdd = cycle === 'annual' ? 12 : 1;
      const currentRenew = sub.renewDate?.toDate ? sub.renewDate.toDate() : (sub.renewDate ? new Date(sub.renewDate) : new Date());
      const baseDate = currentRenew > now ? currentRenew : now;
      const newRenew = new Date(baseDate);
      newRenew.setMonth(newRenew.getMonth() + monthsToAdd);

      const paymentDoc = {
        contractorId: paymentContractor.id,
        contractorEmail: paymentContractor.email,
        contractorName: `${paymentContractor.firstName || ''} ${paymentContractor.lastName || ''}`.trim(),
        planId: sub.planId || 'BUSINESS',
        planName: sub.planName || plan.name,
        amount: Number(paymentAmount) || plan.monthlyPrice,
        paymentMethod: paymentMethod,
        referenceNumber: paymentRef.trim(),
        paymentDate: now,
        verifiedBy: devEmail,
        status: 'Verified',
        notes: paymentNotes.trim()
      };

      await setDoc(doc(collection(db, 'subscriptionPayments')), paymentDoc);

      // Update contractor subscription status and renewal date
      await updateDoc(doc(db, 'users', paymentContractor.id), {
        'subscription.status': 'Active',
        'subscription.renewDate': newRenew,
        'subscription.lastPaymentDate': now,
        'subscription.lastPaymentRef': paymentRef.trim(),
        'subscription.lastPaymentAmount': Number(paymentAmount)
      });

      await logActivity(`Developer verified ₱${Number(paymentAmount).toLocaleString()} payment from ${paymentContractor.email} (Ref: ${paymentRef})`, devEmail);
      showToast('success', `✓ Payment verified and subscription activated for ${paymentContractor.email}!`);
      setShowPaymentModal(false);
      setPaymentNotes('');
    } catch (err) {
      console.error('Error recording payment:', err);
      showToast('error', 'Failed to record payment: ' + err.message);
    } finally {
      setPaymentLoading(false);
    }
  };

  // Troubleshoot Resolution Fixes
  const handleApplyTroubleshoot = async () => {
    if (!troubleshootTarget) return;
    setTroubleshootSaving(true);
    try {
      const sub = troubleshootTarget.subscription || {};
      const currentVests = Number(sub.vestLimit) || 50;
      const currentSsos = Number(sub.ssoLimit) || 10;
      
      const newVestLimit = Math.max(currentVests, currentVests + Number(troubleshootVestBoost));
      const newSsoLimit = Math.max(currentSsos, currentSsos + Number(troubleshootSsoBoost));

      const updates = {
        'subscription.status': troubleshootStatus,
        'subscription.vestLimit': newVestLimit,
        'subscription.ssoLimit': newSsoLimit,
        'subscription.troubleshootNotes': troubleshootNotes.trim(),
        'subscription.troubleshootAt': new Date(),
        'subscription.troubleshootBy': devEmail
      };

      if (troubleshootStatus === 'Active' && sub.status === 'Expired') {
        const extDate = new Date();
        extDate.setDate(extDate.getDate() + 14); // 14 days emergency grace
        updates['subscription.renewDate'] = extDate;
      }

      await updateDoc(doc(db, 'users', troubleshootTarget.id), updates);

      await logActivity(`Developer adjusted subscription parameters for ${troubleshootTarget.email} (Status: ${troubleshootStatus})`, devEmail);
      showToast('success', `✓ Successfully updated subscription parameters for ${troubleshootTarget.email}`);
      setTroubleshootTarget(null);
      setTroubleshootVestBoost(0);
      setTroubleshootSsoBoost(0);
      setTroubleshootNotes('');
    } catch (err) {
      console.error('Error troubleshooting contractor:', err);
      showToast('error', 'Failed to update contractor: ' + err.message);
    } finally {
      setTroubleshootSaving(false);
    }
  };

  // Metrics Calculations
  const activeSubsCount = contractors.filter(c => c.subscription?.status === 'Active').length;
  const totalMrr = contractors.reduce((sum, c) => {
    if (c.subscription?.status === 'Active') {
      return sum + (Number(c.subscription.monthlyPrice) || 0);
    }
    return sum;
  }, 0);
  const registeredVestsCount = catalog.filter(v => v.catalogStatus === 'REGISTERED').length;
  const unregisteredVestsCount = catalog.filter(v => v.catalogStatus === 'UNREGISTERED').length;

  // Filtered Catalog
  const filteredCatalog = catalog.filter(v => {
    const matchesSearch = (v.vestId || '').toLowerCase().includes(catalogSearch.toLowerCase()) ||
                          (v.batchNumber || '').toLowerCase().includes(catalogSearch.toLowerCase());
    const matchesFilter = catalogFilter === 'ALL' || v.catalogStatus === catalogFilter;
    return matchesSearch && matchesFilter;
  });

  return (
    <div className="dev-container">
      {/* Toast Notification */}
      {toastMsg.text && (
        <div style={{
          position: 'fixed',
          top: '1.5rem',
          right: '1.5rem',
          zIndex: 9999,
          background: toastMsg.type === 'error' ? '#ef4444' : '#10b981',
          color: '#ffffff',
          padding: '0.85rem 1.25rem',
          borderRadius: '8px',
          boxShadow: '0 10px 25px rgba(0,0,0,0.3)',
          display: 'flex',
          alignItems: 'center',
          gap: '0.6rem',
          fontWeight: 600,
          fontSize: '0.875rem'
        }}>
          {toastMsg.type === 'error' ? <FiAlertCircle /> : <FiCheckCircle />}
          {toastMsg.text}
        </div>
      )}

      {/* Sidebar */}
      <div className="dev-sidebar">
        <div className="dev-brand">
          <img src={logo} alt="Bevest" />
          <div className="dev-brand-text">
            <h1>Bevest</h1>
            <span className="dev-badge">Developer Portal</span>
          </div>
        </div>

        <nav className="dev-nav">
          <button
            type="button"
            className={`dev-nav-btn ${activeTab === 'hardware' ? 'active' : ''}`}
            onClick={() => setActiveTab('hardware')}
          >
            <FiCpu /> Vest QR & Hardware
          </button>
          <button
            type="button"
            className={`dev-nav-btn ${activeTab === 'subscriptions' ? 'active' : ''}`}
            onClick={() => setActiveTab('subscriptions')}
          >
            <FiCreditCard /> Subscriptions & Payments
          </button>
          <button
            type="button"
            className={`dev-nav-btn ${activeTab === 'troubleshoot' ? 'active' : ''}`}
            onClick={() => setActiveTab('troubleshoot')}
          >
            <FiTool /> Troubleshoot Center
          </button>
        </nav>

        <div className="dev-user-footer">
          <div className="dev-user-info">
            <div className="dev-user-avatar">
              DEV
            </div>
            <div className="dev-user-meta">
              <div className="dev-user-name">{devName}</div>
              <div className="dev-user-role">System Developer</div>
            </div>
          </div>
          <button
            type="button"
            className="dev-logout-btn"
            title="Sign Out"
            onClick={onLogout}
          >
            <FiLogOut />
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div className="dev-main">
        {/* Top Header */}
        <div className="dev-header">
          <div>
            <h2>
              {activeTab === 'hardware' && 'Smart Vest Hardware & QR Generator'}
              {activeTab === 'subscriptions' && 'Subscription & Payment Management'}
              {activeTab === 'troubleshoot' && 'Subscription Diagnostics & Troubleshooting'}
            </h2>
            <p>
              {activeTab === 'hardware' && 'Generate official scannable QR codes and upload authentic hardware vests to the Bevest database.'}
              {activeTab === 'subscriptions' && 'Manage contractor subscription plans, record/verify payments, and extend subscriptions.'}
              {activeTab === 'troubleshoot' && 'Diagnose subscription issues, grant quota overrides, and resolve billing conflicts.'}
            </p>
          </div>
          <div>
            {activeTab === 'hardware' && (
              <button
                type="button"
                className="dev-btn-emerald"
                onClick={() => setShowBatchModal(true)}
              >
                <FiLayers /> Batch Generate QR Codes
              </button>
            )}
            {activeTab === 'subscriptions' && (
              <button
                type="button"
                className="dev-btn-emerald"
                onClick={() => setShowPaymentModal(true)}
              >
                <FiDollarSign /> Record & Approve Payment
              </button>
            )}
          </div>
        </div>

        <div className="dev-body">
          {/* Quick Stats Grid */}
          <div className="dev-stats-grid">
            <div className="dev-stat-card">
              <div>
                <div className="dev-stat-val">{catalog.length}</div>
                <div className="dev-stat-lbl">Total Hardware Vests</div>
              </div>
              <div className="dev-stat-icon purple"><FiCpu /></div>
            </div>

            <div className="dev-stat-card">
              <div>
                <div className="dev-stat-val" style={{ color: '#34d399' }}>{unregisteredVestsCount}</div>
                <div className="dev-stat-lbl">Ready for Admin/SSO Scan</div>
              </div>
              <div className="dev-stat-icon emerald"><FiCheckCircle /></div>
            </div>

            <div className="dev-stat-card">
              <div>
                <div className="dev-stat-val">{activeSubsCount}</div>
                <div className="dev-stat-lbl">Active Subscriptions</div>
              </div>
              <div className="dev-stat-icon cyan"><FiUsers /></div>
            </div>

            <div className="dev-stat-card">
              <div>
                <div className="dev-stat-val">₱{totalMrr.toLocaleString()}</div>
                <div className="dev-stat-lbl">Monthly Recurring Rev</div>
              </div>
              <div className="dev-stat-icon amber"><FiDollarSign /></div>
            </div>
          </div>

          {/* TAB 1: HARDWARE & QR GENERATOR */}
          {activeTab === 'hardware' && (
            <div>
              {/* Single Vest Generator Card */}
              <div className="dev-card">
                <div className="dev-card-header">
                  <h3 className="dev-card-title">
                    <FiCpu /> Generate New Vest QR Code
                  </h3>
                  <button
                    type="button"
                    className="dev-btn-secondary"
                    onClick={handleRegenerateId}
                  >
                    <FiRefreshCw /> Generate New ID
                  </button>
                </div>

                <div className="dev-generator-grid">
                  <form onSubmit={handleUploadSingleVest}>
                    <div className="dev-form-row">
                      <div className="dev-form-group">
                        <label className="dev-form-label">VEST HARDWARE ID *</label>
                        <input
                          type="text"
                          className="dev-input"
                          value={genVestId}
                          onChange={(e) => setGenVestId(e.target.value.toUpperCase())}
                          required
                        />
                      </div>

                      <div className="dev-form-group">
                        <label className="dev-form-label">HARDWARE BATCH *</label>
                        <input
                          type="text"
                          className="dev-input"
                          value={genBatch}
                          onChange={(e) => setGenBatch(e.target.value.toUpperCase())}
                          required
                        />
                      </div>
                    </div>

                    <div className="dev-form-row">
                      <div className="dev-form-group">
                        <label className="dev-form-label">MODEL SPECIFICATION</label>
                        <input
                          type="text"
                          className="dev-input"
                          value={genModel}
                          onChange={(e) => setGenModel(e.target.value)}
                          required
                        />
                      </div>

                      <div className="dev-form-group">
                        <label className="dev-form-label">FIRMWARE VERSION</label>
                        <input
                          type="text"
                          className="dev-input"
                          value={genFirmware}
                          onChange={(e) => setGenFirmware(e.target.value)}
                          required
                        />
                      </div>
                    </div>

                    <div className="dev-form-group">
                      <label className="dev-form-label">SECURITY CHIP TOKEN (AUTO-GENERATED)</label>
                      <input
                        type="text"
                        className="dev-input"
                        value={genToken}
                        readOnly
                        style={{ color: '#818cf8', fontWeight: 700 }}
                      />
                    </div>

                    <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem' }}>
                      <button
                        type="submit"
                        className="dev-btn-primary"
                        disabled={uploading}
                      >
                        {uploading ? 'Uploading to Database...' : 'Upload & Register to Database'}
                        {!uploading && <FiCheck />}
                      </button>

                      <button
                        type="button"
                        className="dev-btn-secondary"
                        onClick={() => handlePrintLabel(genVestId, genBatch, liveQrDataUrl)}
                      >
                        <FiPrinter /> Print Label
                      </button>

                      {liveQrDataUrl && (
                        <a
                          href={liveQrDataUrl}
                          download={`${genVestId}-QR.png`}
                          className="dev-btn-secondary"
                        >
                          <FiDownload /> Download PNG
                        </a>
                      )}
                    </div>
                  </form>

                  {/* Live QR Preview Box */}
                  <div className="dev-qr-preview-box">
                    <div className="dev-qr-sticker">
                      <div style={{ fontSize: '0.75rem', fontWeight: 800, color: '#0f172a', letterSpacing: '0.05em', marginBottom: '4px' }}>
                        BEVEST SMART IOT
                      </div>
                      {liveQrDataUrl ? (
                        <img src={liveQrDataUrl} alt="QR Code" />
                      ) : (
                        <div style={{ width: 180, height: 180, background: '#f1f5f9' }} />
                      )}
                      <div className="dev-sticker-id">{genVestId}</div>
                      <div className="dev-sticker-batch">{genBatch} • VERIFIED</div>
                    </div>
                    <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>
                      Ready to be scanned and registered by Admin or SSO
                    </div>
                  </div>
                </div>
              </div>

              {/* Hardware Catalog Table */}
              <div className="dev-card">
                <div className="dev-card-header">
                  <h3 className="dev-card-title">
                    <FiLayers /> Central Hardware Database ({filteredCatalog.length} Vests)
                  </h3>

                  <div style={{ display: 'flex', gap: '0.75rem' }}>
                    <div style={{ position: 'relative', width: '240px' }}>
                      <input
                        type="text"
                        className="dev-input"
                        placeholder="Search Vest ID or Batch..."
                        value={catalogSearch}
                        onChange={(e) => setCatalogSearch(e.target.value)}
                      />
                    </div>

                    <select
                      className="dev-select"
                      value={catalogFilter}
                      onChange={(e) => setCatalogFilter(e.target.value)}
                      style={{ width: '180px' }}
                    >
                      <option value="ALL">All Statuses</option>
                      <option value="UNREGISTERED">Ready for Scan</option>
                      <option value="REGISTERED">Registered to Company</option>
                      <option value="MAINTENANCE">Maintenance</option>
                    </select>
                  </div>
                </div>

                <div className="dev-table-container">
                  <table className="dev-table">
                    <thead>
                      <tr>
                        <th>Vest ID</th>
                        <th>Model & Firmware</th>
                        <th>Batch Number</th>
                        <th>Hardware Status</th>
                        <th>Registered To</th>
                        <th>Added Date</th>
                        <th style={{ textAlign: 'right' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {catalogLoading ? (
                        <tr>
                          <td colSpan={7} style={{ textAlign: 'center', padding: '2rem' }}>
                            Loading hardware catalog...
                          </td>
                        </tr>
                      ) : filteredCatalog.length === 0 ? (
                        <tr>
                          <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: '#94a3b8' }}>
                            No hardware vests found matching your search.
                          </td>
                        </tr>
                      ) : (
                        filteredCatalog.map(v => (
                          <tr key={v.id || v.vestId}>
                            <td>
                              <strong style={{ color: '#ffffff', letterSpacing: '0.05em' }}>
                                {v.vestId || v.id}
                              </strong>
                            </td>
                            <td>
                              <div>{v.model || 'Bevest Smart Vest'}</div>
                              <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>{v.firmwareVersion || 'v1.4.2'}</span>
                            </td>
                            <td>
                              <span style={{ color: '#cbd5e1', fontWeight: 500 }}>{v.batchNumber || 'N/A'}</span>
                            </td>
                            <td>
                              {v.catalogStatus === 'UNREGISTERED' && (
                                <span className="dev-badge-green">
                                  <FiCheckCircle /> Available for Scan
                                </span>
                              )}
                              {v.catalogStatus === 'REGISTERED' && (
                                <span className="dev-badge-blue">
                                  <FiShield /> Claimed & Registered
                                </span>
                              )}
                              {v.catalogStatus === 'MAINTENANCE' && (
                                <span className="dev-badge-amber">
                                  <FiTool /> Maintenance
                                </span>
                              )}
                            </td>
                            <td>
                              {v.registeredToAdmin ? (
                                <div>
                                  <div style={{ fontWeight: 600 }}>{v.registeredToAdminName || 'Admin'}</div>
                                  <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>{v.registeredToAdmin}</span>
                                </div>
                              ) : (
                                <span style={{ color: '#64748b', fontStyle: 'italic' }}>Pending Admin/SSO Claim</span>
                              )}
                            </td>
                            <td>
                              {v.createdAt?.toDate ? formatDisplayDate(v.createdAt.toDate()) : 'Recent'}
                            </td>
                            <td style={{ textAlign: 'right' }}>
                              <div style={{ display: 'inline-flex', gap: '0.35rem' }}>
                                <button
                                  type="button"
                                  className="dev-btn-secondary"
                                  onClick={() => handleOpenQrViewer(v)}
                                  title="View QR Code"
                                >
                                  <FiEye />
                                </button>
                                <button
                                  type="button"
                                  className="dev-btn-secondary"
                                  onClick={() => {
                                    QRCode.toDataURL(JSON.stringify({ vestId: v.vestId, verified: true }), { width: 300 })
                                      .then(url => handlePrintLabel(v.vestId, v.batchNumber, url));
                                  }}
                                  title="Print Label"
                                >
                                  <FiPrinter />
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: SUBSCRIPTIONS & PAYMENTS */}
          {activeTab === 'subscriptions' && (
            <div>
              {/* Contractors Subscriptions List */}
              <div className="dev-card">
                <div className="dev-card-header">
                  <h3 className="dev-card-title">
                    <FiCreditCard /> Contractor Subscriptions ({contractors.length} Accounts)
                  </h3>

                  <div style={{ position: 'relative', width: '260px' }}>
                    <input
                      type="text"
                      className="dev-input"
                      placeholder="Search Contractor or Email..."
                      value={contractorSearch}
                      onChange={(e) => setContractorSearch(e.target.value)}
                    />
                  </div>
                </div>

                <div className="dev-table-container">
                  <table className="dev-table">
                    <thead>
                      <tr>
                        <th>Contractor Company</th>
                        <th>Email</th>
                        <th>Subscribed Plan</th>
                        <th>Billing Cycle</th>
                        <th>Vests Quota</th>
                        <th>Status</th>
                        <th>Renewal / Expiry</th>
                        <th style={{ textAlign: 'right' }}>Subscription Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {contractorsLoading ? (
                        <tr>
                          <td colSpan={8} style={{ textAlign: 'center', padding: '2rem' }}>
                            Loading contractor subscriptions...
                          </td>
                        </tr>
                      ) : contractors.length === 0 ? (
                        <tr>
                          <td colSpan={8} style={{ textAlign: 'center', padding: '2rem', color: '#94a3b8' }}>
                            No contractors registered in the system yet.
                          </td>
                        </tr>
                      ) : (
                        contractors
                          .filter(c => 
                            (c.firstName || '').toLowerCase().includes(contractorSearch.toLowerCase()) ||
                            (c.lastName || '').toLowerCase().includes(contractorSearch.toLowerCase()) ||
                            (c.email || '').toLowerCase().includes(contractorSearch.toLowerCase())
                          )
                          .map(c => {
                            const sub = c.subscription || {};
                            const hasPlan = !!sub.planId;
                            const isExpired = sub.renewDate && new Date(sub.renewDate.toDate ? sub.renewDate.toDate() : sub.renewDate) < new Date();
                            const renewStr = sub.renewDate ? formatDisplayDate(sub.renewDate.toDate ? sub.renewDate.toDate() : sub.renewDate) : 'Not Scheduled';

                            return (
                              <tr key={c.id}>
                                <td>
                                  <strong>{c.firstName} {c.lastName}</strong>
                                </td>
                                <td>{c.email}</td>
                                <td>
                                  {hasPlan ? (
                                    <div>
                                      <span style={{ fontWeight: 600, color: '#f8fafc' }}>{sub.planName || sub.planId}</span>
                                      <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>
                                        ₱{(sub.monthlyPrice || 0).toLocaleString()}/mo
                                      </div>
                                    </div>
                                  ) : (
                                    <span style={{ color: '#94a3b8', fontStyle: 'italic' }}>No Plan Active</span>
                                  )}
                                </td>
                                <td>
                                  <span style={{ textTransform: 'capitalize' }}>{sub.billingCycle || 'Monthly'}</span>
                                </td>
                                <td>
                                  <strong>{sub.vestLimit || 'N/A'}</strong> vests
                                </td>
                                <td>
                                  {sub.status === 'Active' && !isExpired && (
                                    <span className="dev-badge-green"><FiCheckCircle /> Active</span>
                                  )}
                                  {sub.status === 'Grace Period' && (
                                    <span className="dev-badge-amber"><FiAlertTriangle /> Grace Period</span>
                                  )}
                                  {(sub.status === 'Expired' || isExpired) && (
                                    <span className="dev-badge-red"><FiAlertCircle /> Expired</span>
                                  )}
                                  {(!sub.status || sub.status === 'Inactive') && (
                                    <span className="dev-badge-amber">Inactive</span>
                                  )}
                                </td>
                                <td>
                                  <div style={{ color: isExpired ? '#f87171' : '#f8fafc' }}>
                                    {renewStr}
                                  </div>
                                </td>
                                <td style={{ textAlign: 'right' }}>
                                  <div style={{ display: 'inline-flex', gap: '0.4rem' }}>
                                    <button
                                      type="button"
                                      className="dev-btn-secondary"
                                      onClick={() => setExtendingContractor(c)}
                                      title="Extend Subscription"
                                    >
                                      <FiCalendar /> Extend
                                    </button>
                                    <button
                                      type="button"
                                      className="dev-btn-secondary"
                                      onClick={() => {
                                        setPaymentContractor(c);
                                        setPaymentAmount(c.subscription?.monthlyPrice || 200000);
                                        setShowPaymentModal(true);
                                      }}
                                      title="Record Payment"
                                    >
                                      <FiDollarSign /> Payment
                                    </button>
                                    <button
                                      type="button"
                                      className="dev-btn-secondary"
                                      onClick={() => {
                                        setTroubleshootTarget(c);
                                        setTroubleshootStatus(c.subscription?.status || 'Active');
                                        setActiveTab('troubleshoot');
                                      }}
                                      title="Troubleshoot"
                                    >
                                      <FiTool />
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
              </div>

              {/* Verified Payments Table */}
              <div className="dev-card">
                <div className="dev-card-header">
                  <h3 className="dev-card-title">
                    <FiDollarSign /> Verified Payment History ({paymentHistory.length} Transactions)
                  </h3>
                </div>

                <div className="dev-table-container">
                  <table className="dev-table">
                    <thead>
                      <tr>
                        <th>Reference #</th>
                        <th>Contractor</th>
                        <th>Plan</th>
                        <th>Amount Paid</th>
                        <th>Payment Method</th>
                        <th>Date Verified</th>
                        <th>Verified By</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {paymentHistory.length === 0 ? (
                        <tr>
                          <td colSpan={8} style={{ textAlign: 'center', padding: '1.5rem', color: '#94a3b8' }}>
                            No payment transactions recorded yet. Click "Record & Approve Payment" to add one.
                          </td>
                        </tr>
                      ) : (
                        paymentHistory.map(p => (
                          <tr key={p.id}>
                            <td><strong>{p.referenceNumber || p.id}</strong></td>
                            <td>{p.contractorEmail}</td>
                            <td>{p.planName}</td>
                            <td><strong style={{ color: '#34d399' }}>₱{Number(p.amount || 0).toLocaleString()}</strong></td>
                            <td>{p.paymentMethod}</td>
                            <td>{p.paymentDate?.toDate ? formatDisplayDate(p.paymentDate.toDate()) : 'Recent'}</td>
                            <td>{p.verifiedBy}</td>
                            <td>
                              <span className="dev-badge-green"><FiCheckCircle /> Verified</span>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: TROUBLESHOOT CENTER */}
          {activeTab === 'troubleshoot' && (
            <div>
              {/* Alert Diagnostics Banner */}
              <div className="dev-card" style={{ borderColor: 'rgba(245, 158, 11, 0.3)', background: 'rgba(30, 41, 59, 0.95)' }}>
                <div className="dev-card-header" style={{ borderBottomColor: 'rgba(245, 158, 11, 0.2)' }}>
                  <h3 className="dev-card-title" style={{ color: '#fbbf24' }}>
                    <FiAlertTriangle /> Subscription Health Diagnostics
                  </h3>
                </div>
                <p style={{ color: '#cbd5e1', fontSize: '0.875rem', lineHeight: '1.5', margin: '0 0 1rem 0' }}>
                  Use this console to diagnose contractor subscription issues, override license statuses, grant emergency quota boosts, or fix billing sync conflicts.
                </p>

                <div className="dev-form-group">
                  <label className="dev-form-label">SELECT CONTRACTOR TO INSPECT & TROUBLESHOOT</label>
                  <select
                    className="dev-select"
                    value={troubleshootTarget?.id || ''}
                    onChange={(e) => {
                      const found = contractors.find(c => c.id === e.target.value);
                      setTroubleshootTarget(found || null);
                      if (found) {
                        setTroubleshootStatus(found.subscription?.status || 'Active');
                      }
                    }}
                  >
                    <option value="">-- Choose a contractor account --</option>
                    {contractors.map(c => (
                      <option key={c.id} value={c.id}>
                        {c.firstName} {c.lastName} ({c.email}) - Current Status: {c.subscription?.status || 'Inactive'}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {troubleshootTarget && (
                <div className="dev-card">
                  <div className="dev-card-header">
                    <h3 className="dev-card-title">
                      <FiTool /> Troubleshooting Console: {troubleshootTarget.firstName} {troubleshootTarget.lastName}
                    </h3>
                    <span className="dev-badge">Account ID: {troubleshootTarget.id}</span>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
                    <div style={{ background: '#0f172a', padding: '1rem', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.06)' }}>
                      <div className="dev-stat-lbl">Assigned Plan</div>
                      <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f8fafc', marginTop: '4px' }}>
                        {troubleshootTarget.subscription?.planName || 'No Active Plan'}
                      </div>
                    </div>

                    <div style={{ background: '#0f172a', padding: '1rem', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.06)' }}>
                      <div className="dev-stat-lbl">Current Vest Quota</div>
                      <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f8fafc', marginTop: '4px' }}>
                        {troubleshootTarget.subscription?.vestLimit || 50} Vests
                      </div>
                    </div>

                    <div style={{ background: '#0f172a', padding: '1rem', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.06)' }}>
                      <div className="dev-stat-lbl">SSO Quota</div>
                      <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f8fafc', marginTop: '4px' }}>
                        {troubleshootTarget.subscription?.ssoLimit || 10} SSOs
                      </div>
                    </div>

                    <div style={{ background: '#0f172a', padding: '1rem', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.06)' }}>
                      <div className="dev-stat-lbl">Subscription Expiry</div>
                      <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f8fafc', marginTop: '4px' }}>
                        {troubleshootTarget.subscription?.renewDate 
                          ? formatDisplayDate(troubleshootTarget.subscription.renewDate.toDate ? troubleshootTarget.subscription.renewDate.toDate() : troubleshootTarget.subscription.renewDate)
                          : 'Not Scheduled'}
                      </div>
                    </div>
                  </div>

                  <div className="dev-form-row">
                    <div className="dev-form-group">
                      <label className="dev-form-label">FORCE OVERRIDE SUBSCRIPTION STATUS</label>
                      <select
                        className="dev-select"
                        value={troubleshootStatus}
                        onChange={(e) => setTroubleshootStatus(e.target.value)}
                      >
                        <option value="Active">Active (Full Access)</option>
                        <option value="Grace Period">Grace Period (+14 Days Allowance)</option>
                        <option value="Suspended">Suspended (Temporary Freeze)</option>
                        <option value="Expired">Expired (Prompt for Payment)</option>
                      </select>
                    </div>

                    <div className="dev-form-group">
                      <label className="dev-form-label">TEMPORARY VEST QUOTA BOOST</label>
                      <select
                        className="dev-select"
                        value={troubleshootVestBoost}
                        onChange={(e) => setTroubleshootVestBoost(Number(e.target.value))}
                      >
                        <option value={0}>No Extra Vests (+0)</option>
                        <option value={5}>+5 Vests Emergency Quota</option>
                        <option value={10}>+10 Vests Emergency Quota</option>
                        <option value={25}>+25 Vests Emergency Quota</option>
                      </select>
                    </div>
                  </div>

                  <div className="dev-form-group">
                    <label className="dev-form-label">DEVELOPER RESOLUTION NOTES</label>
                    <textarea
                      className="dev-input"
                      rows={3}
                      placeholder="e.g. Authorized emergency 10-vest boost pending contract renewal wire transfer."
                      value={troubleshootNotes}
                      onChange={(e) => setTroubleshootNotes(e.target.value)}
                    />
                  </div>

                  <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1rem' }}>
                    <button
                      type="button"
                      className="dev-btn-primary"
                      onClick={handleApplyTroubleshoot}
                      disabled={troubleshootSaving}
                    >
                      {troubleshootSaving ? 'Applying Changes...' : 'Save & Apply Troubleshooting Fix'}
                      {!troubleshootSaving && <FiCheck />}
                    </button>
                    <button
                      type="button"
                      className="dev-btn-secondary"
                      onClick={() => setTroubleshootTarget(null)}
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* EXTEND SUBSCRIPTION MODAL */}
      {extendingContractor && (
        <div className="dev-modal-overlay">
          <div className="dev-modal-card">
            <div className="dev-modal-header">
              <h3>Extend Contractor Subscription</h3>
              <button
                type="button"
                className="dev-modal-close"
                onClick={() => setExtendingContractor(null)}
              >
                <FiX />
              </button>
            </div>

            <div style={{ marginBottom: '1.25rem', color: '#cbd5e1', fontSize: '0.875rem' }}>
              Extending subscription for <strong>{extendingContractor.firstName} {extendingContractor.lastName}</strong> ({extendingContractor.email}).
            </div>

            <div className="dev-form-group">
              <label className="dev-form-label">EXTENSION DURATION</label>
              <select
                className="dev-select"
                value={extensionDays}
                onChange={(e) => setExtensionDays(Number(e.target.value))}
              >
                <option value={30}>+30 Days (1 Month Extension)</option>
                <option value={60}>+60 Days (2 Months Extension)</option>
                <option value={90}>+90 Days (Quarterly Extension)</option>
                <option value={180}>+180 Days (Half-Year Extension)</option>
                <option value={365}>+365 Days (1 Full Year Extension)</option>
              </select>
            </div>

            <div className="dev-form-group">
              <label className="dev-form-label">EXTENSION REASON / DEVELOPER NOTES</label>
              <input
                type="text"
                className="dev-input"
                placeholder="e.g. Wire transfer in progress / Sales contract renewal"
                value={extensionNotes}
                onChange={(e) => setExtensionNotes(e.target.value)}
              />
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="dev-btn-secondary"
                onClick={() => setExtendingContractor(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="dev-btn-emerald"
                onClick={handleConfirmExtension}
                disabled={extendingLoading}
              >
                {extendingLoading ? 'Extending...' : 'Confirm & Extend Subscription'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* RECORD PAYMENT MODAL */}
      {showPaymentModal && (
        <div className="dev-modal-overlay">
          <div className="dev-modal-card">
            <div className="dev-modal-header">
              <h3>Record Subscription Payment</h3>
              <button
                type="button"
                className="dev-modal-close"
                onClick={() => setShowPaymentModal(false)}
              >
                <FiX />
              </button>
            </div>

            <form onSubmit={handleRecordPayment}>
              <div className="dev-form-group">
                <label className="dev-form-label">SELECT CONTRACTOR *</label>
                <select
                  className="dev-select"
                  value={paymentContractor?.id || ''}
                  onChange={(e) => {
                    const found = contractors.find(c => c.id === e.target.value);
                    setPaymentContractor(found || null);
                    if (found?.subscription?.monthlyPrice) {
                      setPaymentAmount(found.subscription.monthlyPrice);
                    }
                  }}
                  required
                >
                  <option value="">-- Choose Contractor --</option>
                  {contractors.map(c => (
                    <option key={c.id} value={c.id}>
                      {c.firstName} {c.lastName} ({c.email}) - {c.subscription?.planName || 'Business'}
                    </option>
                  ))}
                </select>
              </div>

              <div className="dev-form-row">
                <div className="dev-form-group">
                  <label className="dev-form-label">AMOUNT (PHP ₱) *</label>
                  <input
                    type="number"
                    className="dev-input"
                    value={paymentAmount}
                    onChange={(e) => setPaymentAmount(e.target.value)}
                    required
                  />
                </div>

                <div className="dev-form-group">
                  <label className="dev-form-label">PAYMENT METHOD</label>
                  <select
                    className="dev-select"
                    value={paymentMethod}
                    onChange={(e) => setPaymentMethod(e.target.value)}
                  >
                    <option value="Bank Wire (BDO)">Bank Wire (BDO)</option>
                    <option value="Bank Wire (BPI)">Bank Wire (BPI)</option>
                    <option value="GCash">GCash</option>
                    <option value="Maya">Maya</option>
                    <option value="Corporate Cheque">Corporate Cheque</option>
                    <option value="Direct Invoice">Direct Invoice</option>
                  </select>
                </div>
              </div>

              <div className="dev-form-group">
                <label className="dev-form-label">TRANSACTION REFERENCE # *</label>
                <input
                  type="text"
                  className="dev-input"
                  value={paymentRef}
                  onChange={(e) => setPaymentRef(e.target.value)}
                  required
                />
              </div>

              <div className="dev-form-group">
                <label className="dev-form-label">RECEIPT REMARKS / NOTES</label>
                <input
                  type="text"
                  className="dev-input"
                  placeholder="e.g. Official Receipt #1042 Issued"
                  value={paymentNotes}
                  onChange={(e) => setPaymentNotes(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', justifyContent: 'flex-end' }}>
                <button
                  type="button"
                  className="dev-btn-secondary"
                  onClick={() => setShowPaymentModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="dev-btn-emerald"
                  disabled={paymentLoading}
                >
                  {paymentLoading ? 'Verifying...' : 'Approve Payment & Activate'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* BATCH GENERATOR MODAL */}
      {showBatchModal && (
        <div className="dev-modal-overlay">
          <div className="dev-modal-card">
            <div className="dev-modal-header">
              <h3>Batch Generate Vest QR Codes</h3>
              <button
                type="button"
                className="dev-modal-close"
                onClick={() => setShowBatchModal(false)}
              >
                <FiX />
              </button>
            </div>

            <p style={{ color: '#cbd5e1', fontSize: '0.875rem', marginBottom: '1.25rem' }}>
              Generate multiple sequential smart vest records, register them to the central hardware database, and prepare scannable QR labels.
            </p>

            <div className="dev-form-row">
              <div className="dev-form-group">
                <label className="dev-form-label">NUMBER OF VESTS</label>
                <select
                  className="dev-select"
                  value={batchCount}
                  onChange={(e) => setBatchCount(Number(e.target.value))}
                >
                  <option value={5}>5 Vests</option>
                  <option value={10}>10 Vests</option>
                  <option value={20}>20 Vests</option>
                  <option value={50}>50 Vests</option>
                </select>
              </div>

              <div className="dev-form-group">
                <label className="dev-form-label">ID PREFIX</label>
                <input
                  type="text"
                  className="dev-input"
                  value={batchPrefix}
                  onChange={(e) => setBatchPrefix(e.target.value.toUpperCase())}
                />
              </div>
            </div>

            <div className="dev-form-group">
              <label className="dev-form-label">BATCH LOT NUMBER</label>
              <input
                type="text"
                className="dev-input"
                value={genBatch}
                onChange={(e) => setGenBatch(e.target.value.toUpperCase())}
              />
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="dev-btn-secondary"
                onClick={() => setShowBatchModal(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="dev-btn-emerald"
                onClick={handleBatchGenerate}
                disabled={batchGenerating}
              >
                {batchGenerating ? 'Generating...' : `Generate ${batchCount} Vests`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* QR VIEWER MODAL */}
      {selectedVestQr && (
        <div className="dev-modal-overlay">
          <div className="dev-modal-card" style={{ maxWidth: '420px', textAlign: 'center' }}>
            <div className="dev-modal-header">
              <h3>{selectedVestQr.vestId}</h3>
              <button
                type="button"
                className="dev-modal-close"
                onClick={() => setSelectedVestQr(null)}
              >
                <FiX />
              </button>
            </div>

            <div style={{ background: '#ffffff', padding: '1.25rem', borderRadius: '12px', display: 'inline-block', margin: '0 auto 1.25rem auto' }}>
              {viewQrDataUrl && <img src={viewQrDataUrl} alt="QR" style={{ width: '220px', height: '220px', display: 'block' }} />}
              <div style={{ fontWeight: 800, color: '#0f172a', fontSize: '1.1rem', marginTop: '8px' }}>
                {selectedVestQr.vestId}
              </div>
              <div style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 700 }}>
                {selectedVestQr.batchNumber} • BEVEST SMART IOT
              </div>
            </div>

            <div style={{ textAlign: 'left', background: '#0f172a', padding: '0.85rem 1rem', borderRadius: '8px', marginBottom: '1.25rem', fontSize: '0.8125rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span style={{ color: '#94a3b8' }}>Status:</span>
                <span style={{ fontWeight: 600 }}>{selectedVestQr.catalogStatus}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span style={{ color: '#94a3b8' }}>Model:</span>
                <span>{selectedVestQr.model || 'Bevest Smart Vest v2'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#94a3b8' }}>Security Token:</span>
                <span style={{ fontFamily: 'monospace', color: '#818cf8' }}>{selectedVestQr.hardwareToken || 'N/A'}</span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'center' }}>
              <button
                type="button"
                className="dev-btn-secondary"
                onClick={() => handlePrintLabel(selectedVestQr.vestId, selectedVestQr.batchNumber, viewQrDataUrl)}
              >
                <FiPrinter /> Print Label
              </button>
              {viewQrDataUrl && (
                <a
                  href={viewQrDataUrl}
                  download={`${selectedVestQr.vestId}-QR.png`}
                  className="dev-btn-primary"
                >
                  <FiDownload /> Download
                </a>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DeveloperDashboard;

