import React, { useState, useEffect } from 'react';
import { 
  FiCpu, FiCreditCard, FiActivity, FiTool, FiLogOut, FiPlus, 
  FiSearch, FiDownload, FiPrinter, FiCheckCircle, 
  FiAlertCircle, FiDollarSign, FiUsers, 
  FiRefreshCw, FiEye, FiTrash2, FiShield, FiTag, 
  FiAlertTriangle, FiCheck, FiX, FiLayers, FiMenu,
  FiBriefcase, FiUserPlus, FiLock, FiMail, FiPhone
} from 'react-icons/fi';
import { 
  collection, doc, getDocs, setDoc, updateDoc, 
  query, where, onSnapshot 
} from 'firebase/firestore';
import { createUserWithEmailAndPassword, signInWithEmailAndPassword } from 'firebase/auth';
import { auth, secondaryAuth, db } from './firebase';
import QRCode from 'qrcode';
import { logActivity } from './activityLogger';
import { SUBSCRIPTION_PLANS, formatDisplayDate } from './subscriptionPlans';
import PhoneInput, { formatPhoneWithCountryCode } from './PhoneInput';
import logo from './assets/bevest_logo.png';
import './developer.css';

const DeveloperDashboard = ({ user, onLogout }) => {
  const [activeTab, setActiveTab] = useState('hardware'); // 'hardware' | 'admins' | 'subscriptions' | 'troubleshoot'
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  
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
  const [genStatus, setGenStatus] = useState('AVAILABLE');
  const [batchStatus, setBatchStatus] = useState('AVAILABLE');
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

  // Admin Management state
  const [admins, setAdmins] = useState([]);
  const [adminsLoading, setAdminsLoading] = useState(true);
  const [adminSearch, setAdminSearch] = useState('');
  const [showAddAdminModal, setShowAddAdminModal] = useState(false);
  const [adminCreating, setAdminCreating] = useState(false);

  // Admin Form state
  const [adminCompany, setAdminCompany] = useState('');
  const [adminFirstName, setAdminFirstName] = useState('');
  const [adminLastName, setAdminLastName] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminPhone, setAdminPhone] = useState('');
  const [adminPassword, setAdminPassword] = useState(`ADM-2026-${Math.floor(1000 + Math.random() * 9000)}`);

  // Subscriptions & Contractors state
  const [contractors, setContractors] = useState([]);
  const [contractorsLoading, setContractorsLoading] = useState(true);
  const [contractorSearch, setContractorSearch] = useState('');

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

  const devEmail = user?.email || 'developer@bevest.com';
  const devName = user?.profile?.firstName 
    ? `${user.profile.firstName} ${user.profile.lastName || ''}`.trim()
    : 'Bevest Developer';

  const showToast = (type, text) => {
    setToastMsg({ type, text });
    setTimeout(() => setToastMsg({ type: '', text: '' }), 5000);
  };

  const handleSignOut = () => {
    auth.signOut();
    if (onLogout) onLogout();
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
      width: 260,
      margin: 2,
      color: {
        dark: '#0f172a',
        light: '#ffffff'
      }
    })
    .then(url => setLiveQrDataUrl(url))
    .catch(err => console.error('Error generating preview QR:', err));
  }, [genVestId, genBatch, genToken]);

  // 2. Subscribe to Vests collection (direct hardware repository)
  useEffect(() => {
    setCatalogLoading(true);
    const vestsRef = collection(db, 'vests');
    
    const unsubscribe = onSnapshot(vestsRef, (snap) => {
      const items = [];
      snap.forEach(d => items.push({ id: d.id, ...d.data() }));
      items.sort((a, b) => (b.createdAt?.toDate ? b.createdAt.toDate().getTime() : 0) - (a.createdAt?.toDate ? a.createdAt.toDate().getTime() : 0));
      setCatalog(items);
      setCatalogLoading(false);
    }, (err) => {
      console.warn('Error fetching vests:', err.message);
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
        await setDoc(doc(db, 'vests', item.vestId), { ...item, status: 'AVAILABLE' }, { merge: true });
      } catch (_) {}
    }
    localStorage.setItem('bevest_vest_catalog', JSON.stringify(localMap));
    setCatalog(seeds);
  };

  // 4. Subscribe to Admins (where role == 'ADMIN')
  useEffect(() => {
    setAdminsLoading(true);
    const usersRef = collection(db, 'users');
    const q = query(usersRef, where('role', '==', 'ADMIN'));

    const unsubscribe = onSnapshot(q, (snap) => {
      const list = [];
      snap.forEach(d => list.push({ id: d.id, ...d.data() }));
      list.sort((a, b) => (b.createdAt?.toDate ? b.createdAt.toDate().getTime() : 0) - (a.createdAt?.toDate ? a.createdAt.toDate().getTime() : 0));
      setAdmins(list);
      setAdminsLoading(false);
    }, (err) => {
      console.warn('Admins fetch error:', err.message);
      try {
        const raw = localStorage.getItem('bevest_user_directory');
        if (raw) {
          const dict = JSON.parse(raw);
          const fallbackList = Object.entries(dict)
            .filter(([_, u]) => (u.role || '').toUpperCase() === 'ADMIN')
            .map(([em, u]) => ({ id: em, email: em, ...u }));
          setAdmins(fallbackList);
        }
      } catch (_) {}
      setAdminsLoading(false);
    });

    return () => unsubscribe();
  }, []);

  // 5. Subscribe to Contractors (for Subscriptions & Payments)
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

  // 6. Subscribe to Payment History
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

  const handleRegenerateId = () => {
    const num = Math.floor(1000 + Math.random() * 9000);
    setGenVestId(`VEST-2026-${num}`);
    setGenToken(Math.random().toString(16).substring(2, 10).toUpperCase());
  };

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
        id: cleanId,
        status: genStatus || 'AVAILABLE',
        model: genModel.trim() || 'Bevest Smart Vest v2',
        batchNumber: genBatch.trim() || 'BATCH-2026-A1',
        firmwareVersion: genFirmware.trim() || 'v2.4.0',
        hardwareToken: genToken,
        verified: true,
        battery: 100,
        online: false,
        assignedWorkerId: null,
        inCompanyDirectory: false,
        createdAt: now,
        createdByEmail: devEmail
      };

      await setDoc(doc(db, 'vests', cleanId), vestRecord, { merge: true });

      await logActivity(`Developer created smart vest ${cleanId} (Status: ${genStatus || 'AVAILABLE'})`, devEmail);
      showToast('success', `✓ Vest ${cleanId} saved to vests collection with status "${genStatus || 'AVAILABLE'}"!`);
      
      handleRegenerateId();
    } catch (err) {
      console.error('Error uploading vest to vests collection:', err);
      showToast('error', 'Failed to upload vest: ' + err.message);
    } finally {
      setUploading(false);
    }
  };

  const handleBatchGenerate = async () => {
    const count = parseInt(batchCount, 10) || 10;
    setBatchGenerating(true);
    try {
      const startNum = Math.floor(1000 + Math.random() * 8000);
      const now = new Date();

      for (let i = 0; i < count; i++) {
        const currentId = `${batchPrefix}${startNum + i}`;
        const item = {
          vestId: currentId,
          id: currentId,
          status: batchStatus || 'AVAILABLE',
          model: genModel.trim() || 'Bevest Smart Vest v2',
          batchNumber: genBatch.trim() || 'BATCH-2026-A1',
          firmwareVersion: genFirmware.trim() || 'v2.4.0',
          hardwareToken: Math.random().toString(16).substring(2, 10).toUpperCase(),
          verified: true,
          battery: 100,
          online: false,
          assignedWorkerId: null,
          inCompanyDirectory: false,
          createdAt: now,
          createdByEmail: devEmail
        };
        await setDoc(doc(db, 'vests', currentId), item, { merge: true });
      }

      await logActivity(`Developer batch generated ${count} vests to vests collection (Status: ${batchStatus || 'AVAILABLE'})`, devEmail);
      showToast('success', `✓ Successfully generated and saved ${count} vests to vests collection!`);
      setShowBatchModal(false);
    } catch (err) {
      console.error('Batch generation error:', err);
      showToast('error', 'Batch generation failed: ' + err.message);
    } finally {
      setBatchGenerating(false);
    }
  };

  const handleOpenQrViewer = (vest) => {
    setSelectedVestQr(vest);
    const payload = JSON.stringify({
      vestId: vest.vestId || vest.id,
      verified: true,
      batch: vest.batchNumber || 'N/A',
      status: vest.status || 'AVAILABLE',
      token: vest.hardwareToken || 'N/A'
    });
    QRCode.toDataURL(payload, {
      width: 300,
      margin: 2,
      color: { dark: '#0f172a', light: '#ffffff' }
    })
    .then(url => setViewQrDataUrl(url))
    .catch(console.error);
  };

  const handlePrintLabel = (vestId, batch, qrUrl) => {
    const printWin = window.open('', '_blank', 'width=600,height=600');
    if (!printWin) return;
    printWin.document.write(`
      <html>
        <head>
          <title>Print Vest Label - ${vestId}</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; background: #f8fafc; }
            .label-box { border: 2px dashed #ea580c; padding: 24px; text-align: center; border-radius: 12px; background: #fff; width: 300px; box-shadow: 0 4px 15px rgba(0,0,0,0.08); }
            .title { font-size: 18px; font-weight: 800; color: #0f172a; margin-bottom: 4px; }
            .sub { font-size: 11px; color: #ea580c; font-weight: 700; text-transform: uppercase; margin-bottom: 14px; letter-spacing: 0.05em; }
            img { width: 200px; height: 200px; }
            .id-code { font-size: 20px; font-weight: 800; color: #0f172a; margin-top: 10px; letter-spacing: 0.05em; }
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
            <div class="security">Scan with BeVest Admin or SSO App to Register</div>
          </div>
          <script>
            window.onload = function() { window.print(); window.close(); }
          </script>
        </body>
      </html>
    `);
    printWin.document.close();
  };

  // DEVELOPER FUNCTION: CREATE ACCOUNT FOR ADMIN
  const handleCreateAdmin = async (e) => {
    e.preventDefault();
    if (!adminCompany.trim() || !adminFirstName.trim() || !adminLastName.trim() || !adminEmail.trim() || !adminPhone.trim() || !adminPassword) {
      showToast('error', 'Please fill in all required fields including Company Name and Phone.');
      return;
    }

    setAdminCreating(true);
    try {
      let newAdminUid = null;
      try {
        const userCredential = await createUserWithEmailAndPassword(secondaryAuth, adminEmail.trim(), adminPassword);
        newAdminUid = userCredential.user.uid;
      } catch (authErr) {
        if (authErr.code === 'auth/email-already-in-use') {
          // If already in Firebase Auth, sign in to secondaryAuth to obtain the UID
          const signedIn = await signInWithEmailAndPassword(secondaryAuth, adminEmail.trim(), adminPassword);
          newAdminUid = signedIn.user.uid;
        } else {
          throw authErr;
        }
      }

      const now = new Date();
      const adminDoc = {
        firstName: adminFirstName.trim(),
        lastName: adminLastName.trim(),
        company: adminCompany.trim(),
        companyName: adminCompany.trim(),
        phone: formatPhoneWithCountryCode(adminPhone),
        email: adminEmail.trim(),
        role: 'ADMIN',
        active: true,
        status: 'Active',
        createdAt: now,
        createdByDeveloper: devEmail
      };

      // Save user document in Firestore users collection
      await setDoc(doc(db, 'users', newAdminUid), adminDoc, { merge: true });

      // Update local user directory cache
      try {
        const directory = JSON.parse(localStorage.getItem('bevest_user_directory') || '{}');
        directory[adminEmail.toLowerCase().trim()] = {
          phone: formatPhoneWithCountryCode(adminPhone),
          role: 'ADMIN',
          firstName: adminFirstName.trim(),
          lastName: adminLastName.trim(),
          company: adminCompany.trim(),
          companyName: adminCompany.trim()
        };
        localStorage.setItem('bevest_user_directory', JSON.stringify(directory));
      } catch (_) {}

      // Log activity
      await logActivity(`Developer registered Admin account for ${adminEmail.trim()} (${adminCompany.trim()})`, devEmail);

      showToast('success', `✓ Admin account for ${adminCompany.trim()} (${adminEmail.trim()}) registered in users collection!`);
      setShowAddAdminModal(false);
      
      // Reset form fields
      setAdminCompany('');
      setAdminFirstName('');
      setAdminLastName('');
      setAdminEmail('');
      setAdminPhone('');
      setAdminPassword(`ADM-2026-${Math.floor(1000 + Math.random() * 9000)}`);
    } catch (err) {
      console.error('Error creating admin account:', err);
      showToast('error', 'Failed to create Admin account: ' + err.message);
    } finally {
      try {
        await secondaryAuth.signOut();
      } catch (_) {}
      setAdminCreating(false);
    }
  };

  // Toggle Admin Active / Disabled status
  const handleToggleAdminStatus = async (adminAccount) => {
    const currentActive = adminAccount.active !== false && adminAccount.status !== 'Disabled';
    const newActive = !currentActive;
    const newStatus = newActive ? 'Active' : 'Disabled';

    try {
      await updateDoc(doc(db, 'users', adminAccount.id), {
        active: newActive,
        status: newStatus
      });
      await logActivity(`Developer ${newActive ? 'activated' : 'disabled'} Admin account: ${adminAccount.email}`, devEmail);
      showToast('success', `✓ Admin account for ${adminAccount.email} is now ${newStatus}.`);
    } catch (err) {
      console.error('Error toggling admin status:', err);
      showToast('error', 'Failed to update admin: ' + err.message);
    }
  };


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
        extDate.setDate(extDate.getDate() + 14);
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

  // Metrics
  const activeSubsCount = contractors.filter(c => c.subscription?.status === 'Active').length;
  const totalMrr = contractors.reduce((sum, c) => {
    if (c.subscription?.status === 'Active') {
      return sum + (Number(c.subscription.monthlyPrice) || 0);
    }
    return sum;
  }, 0);
  const registeredVestsCount = catalog.filter(v => v.status === 'ASSIGNED' || v.status === 'ACTIVE' || v.inCompanyDirectory === true).length;
  const unregisteredVestsCount = catalog.filter(v => (v.status || 'AVAILABLE') === 'AVAILABLE' || v.catalogStatus === 'UNREGISTERED').length;

  const filteredCatalog = catalog.filter(v => {
    const matchesSearch = (v.vestId || v.id || '').toLowerCase().includes(catalogSearch.toLowerCase()) ||
                          (v.batchNumber || '').toLowerCase().includes(catalogSearch.toLowerCase());
    const matchesFilter = catalogFilter === 'ALL' || (v.status || 'AVAILABLE') === catalogFilter;
    return matchesSearch && matchesFilter;
  });

  const filteredAdmins = admins.filter(a => {
    const q = adminSearch.toLowerCase();
    const comp = (a.company || a.companyName || '').toLowerCase();
    const name = `${a.firstName || ''} ${a.lastName || ''}`.toLowerCase();
    const em = (a.email || '').toLowerCase();
    return comp.includes(q) || name.includes(q) || em.includes(q);
  });

  return (
    <div className="dashboard-container" style={{ position: 'relative' }}>
      {/* Toast Notification */}
      {toastMsg.text && (
        <div style={{
          position: 'fixed',
          top: '1.25rem',
          right: '1.25rem',
          zIndex: 9999,
          background: toastMsg.type === 'error' ? '#ef4444' : '#16a34a',
          color: '#ffffff',
          padding: '0.85rem 1.25rem',
          borderRadius: '8px',
          boxShadow: '0 10px 25px rgba(0,0,0,0.2)',
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

      {/* Mobile Header (Visible on Mobile & Tablet) */}
      <div className="mobile-header">
        <div className="mobile-logo-group">
          <img src={logo} alt="Bevest Logo" />
          <h2>BeVest Developer</h2>
        </div>
        <button 
          className="burger-btn" 
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          aria-label="Toggle Navigation Menu"
        >
          {isMobileMenuOpen ? <FiX /> : <FiMenu />}
        </button>
      </div>

      {/* Mobile Backdrop Overlay */}
      {isMobileMenuOpen && (
        <div 
          className="sidebar-backdrop" 
          onClick={() => setIsMobileMenuOpen(false)} 
        />
      )}

      {/* Sidebar (Matching Admin UI Design) */}
      <aside className={`sidebar ${isMobileMenuOpen ? 'open' : ''}`}>
        <div className="sidebar-header desktop-only">
          <img src={logo} alt="Bevest Logo" className="sidebar-logo" />
          <div className="sidebar-title-group">
            <h2>BeVest Developer</h2>
            <span>SYSTEM & HARDWARE</span>
          </div>
        </div>
        
        <div className="sidebar-user">
          <div className="avatar" style={{ backgroundColor: '#ffedd5', color: '#ea580c', fontWeight: 700, fontSize: '0.875rem' }}>
            DV
          </div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <span className="username" style={{ lineHeight: 1.2 }}>{devName}</span>
            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>System Developer</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          <button 
            className={`nav-item ${activeTab === 'hardware' ? 'active' : ''}`}
            onClick={() => { setActiveTab('hardware'); setIsMobileMenuOpen(false); }}
          >
            <FiCpu /> Vest Hardware & QR
          </button>
          <button 
            className={`nav-item ${activeTab === 'admins' ? 'active' : ''}`}
            onClick={() => { setActiveTab('admins'); setIsMobileMenuOpen(false); }}
          >
            <FiBriefcase /> Admin Accounts
          </button>
          <button 
            className={`nav-item ${activeTab === 'subscriptions' ? 'active' : ''}`}
            onClick={() => { setActiveTab('subscriptions'); setIsMobileMenuOpen(false); }}
          >
            <FiCreditCard /> Subscriptions & Billing
          </button>
          <button 
            className={`nav-item ${activeTab === 'troubleshoot' ? 'active' : ''}`}
            onClick={() => { setActiveTab('troubleshoot'); setIsMobileMenuOpen(false); }}
          >
            <FiTool /> Troubleshoot Center
          </button>
        </nav>
        
        <div className="sidebar-footer">
          <button className="nav-item" onClick={handleSignOut} style={{ color: '#ef4444' }}>
            <FiLogOut /> Sign Out
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="main-content">
        <div className="dashboard-content">
          {/* Top Page Header */}
          <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
            <div>
              <h1>
                {activeTab === 'hardware' && 'Smart Vest Hardware & QR Generator'}
                {activeTab === 'admins' && 'Admin Account Provisioning & Companies'}
                {activeTab === 'subscriptions' && 'Subscription & Payment Management'}
                {activeTab === 'troubleshoot' && 'Subscription Diagnostics & Troubleshooting'}
              </h1>
              <p>
                {activeTab === 'hardware' && 'Generate official scannable QR codes and upload authentic smart vests to the central Bevest database.'}
                {activeTab === 'admins' && 'Onboard and manage Admin accounts for client companies and project sites.'}
                {activeTab === 'subscriptions' && 'Manage contractor subscription tiers, verify manual payments, and monitor subscription expiration dates.'}
                {activeTab === 'troubleshoot' && 'Diagnose subscription errors, grant temporary quota boosts, and override account statuses.'}
              </p>
            </div>

            <div className="dev-header-actions" style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
              {activeTab === 'hardware' && (
                <button
                  type="button"
                  className="dev-btn-orange"
                  onClick={() => setShowBatchModal(true)}
                >
                  <FiLayers /> Batch Generate QR Codes
                </button>
              )}
              {activeTab === 'subscriptions' && (
                <button
                  type="button"
                  className="dev-btn-orange"
                  onClick={() => setShowPaymentModal(true)}
                >
                  Record & Approve Payment
                </button>
              )}
            </div>
          </div>

          {/* Stats Overview Grid (Matching Admin Design) */}
          <div className="stats-grid">
            <div className="dev-stat-card">
              <div>
                <div className="dev-stat-title">TOTAL HARDWARE VESTS</div>
                <div className="dev-stat-value">{catalog.length}</div>
              </div>
              <div className="dev-stat-icon-wrapper dev-stat-icon-orange">
                <FiCpu />
              </div>
            </div>

            <div className="dev-stat-card highlight">
              <div>
                <div className="dev-stat-title">READY FOR ADMIN/SSO SCAN</div>
                <div className="dev-stat-value" style={{ color: '#16a34a' }}>{unregisteredVestsCount}</div>
              </div>
              <div className="dev-stat-icon-wrapper dev-stat-icon-emerald">
                <FiCheckCircle />
              </div>
            </div>

            <div className="dev-stat-card">
              <div>
                <div className="dev-stat-title">ADMIN COMPANIES</div>
                <div className="dev-stat-value">{admins.length}</div>
              </div>
              <div className="dev-stat-icon-wrapper dev-stat-icon-purple">
                <FiBriefcase />
              </div>
            </div>

            <div className="dev-stat-card">
              <div>
                <div className="dev-stat-title">ACTIVE SUBSCRIPTIONS</div>
                <div className="dev-stat-value">{activeSubsCount}</div>
              </div>
              <div className="dev-stat-icon-wrapper dev-stat-icon-blue">
                <FiUsers />
              </div>
            </div>
          </div>

          {/* TAB 1: VEST HARDWARE & QR GENERATOR */}
          {activeTab === 'hardware' && (
            <div>
              {/* QR Code Generator Card */}
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

                <div className="dev-form-grid">
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
                      <label className="dev-form-label">SECURITY CHIP TOKEN (HARDWARE IDENTIFIER)</label>
                      <input
                        type="text"
                        className="dev-input"
                        value={genToken}
                        readOnly
                        style={{ color: '#ea580c', fontWeight: 700, backgroundColor: '#f8fafc' }}
                      />
                    </div>

                    <div className="dev-form-group">
                      <label className="dev-form-label">STATUS *</label>
                      <select
                        className="dev-select"
                        value={genStatus}
                        onChange={(e) => setGenStatus(e.target.value)}
                        required
                      >
                        <option value="AVAILABLE">AVAILABLE (Ready for Assignment)</option>
                        <option value="ACTIVE">ACTIVE (In Use)</option>
                        <option value="OFFLINE">OFFLINE (Storage / Disconnected)</option>
                        <option value="MAINTENANCE">MAINTENANCE (Inspection Required)</option>
                      </select>
                    </div>

                    <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', flexWrap: 'wrap' }}>
                      <button
                        type="submit"
                        className="dev-btn-orange"
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
                  <div className="dev-qr-sticker-box">
                    <div className="dev-sticker-brand">
                      BEVEST SMART IOT
                    </div>
                    {liveQrDataUrl ? (
                      <img src={liveQrDataUrl} alt="Vest QR Code" />
                    ) : (
                      <div style={{ width: 180, height: 180, backgroundColor: '#f1f5f9', borderRadius: '8px' }} />
                    )}
                    <div className="dev-sticker-id">{genVestId}</div>
                    <div className="dev-sticker-batch">{genBatch} • VERIFIED</div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.5rem' }}>
                      Ready for Admin / SSO verification scan
                    </div>
                  </div>
                </div>
              </div>

              {/* Central Hardware Catalog Table */}
              <div className="dev-card">
                <div className="dev-card-header">
                  <h3 className="dev-card-title">
                    <FiLayers /> Central Hardware Database ({filteredCatalog.length} Vests)
                  </h3>

                  <div className="dev-search-filter-row" style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                    <div style={{ position: 'relative', minWidth: '220px' }}>
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
                      style={{ minWidth: '180px' }}
                    >
                      <option value="ALL">All Statuses</option>
                      <option value="AVAILABLE">AVAILABLE</option>
                      <option value="ACTIVE">ACTIVE</option>
                      <option value="ASSIGNED">ASSIGNED</option>
                      <option value="OFFLINE">OFFLINE</option>
                      <option value="MAINTENANCE">MAINTENANCE</option>
                    </select>
                  </div>
                </div>

                <div className="dev-table-wrapper">
                  <table className="dev-table">
                    <thead>
                      <tr>
                        <th>Vest ID</th>
                        <th>Model & Firmware</th>
                        <th>Batch Lot</th>
                        <th>Status</th>
                        <th>Registered Company</th>
                        <th>Added Date</th>
                        <th style={{ textAlign: 'right' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {catalogLoading ? (
                        <tr>
                          <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                            Loading hardware catalog...
                          </td>
                        </tr>
                      ) : filteredCatalog.length === 0 ? (
                        <tr>
                          <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                            No hardware vests found matching your criteria.
                          </td>
                        </tr>
                      ) : (
                        filteredCatalog.map(v => (
                          <tr key={v.id || v.vestId}>
                            <td>
                              <strong style={{ color: 'var(--text-main)', letterSpacing: '0.05em' }}>
                                {v.vestId || v.id}
                              </strong>
                            </td>
                            <td>
                              <div style={{ fontWeight: 500 }}>{v.model || 'Bevest Smart Vest'}</div>
                              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{v.firmwareVersion || 'v1.4.2'}</span>
                            </td>
                            <td>
                              <span style={{ fontWeight: 500 }}>{v.batchNumber || 'N/A'}</span>
                            </td>
                            <td>
                              {(v.status === 'AVAILABLE' || (!v.status && v.catalogStatus === 'UNREGISTERED')) && (
                                <span className="dev-badge-green">
                                  <FiCheckCircle /> AVAILABLE
                                </span>
                              )}
                              {v.status === 'ACTIVE' && (
                                <span className="dev-badge-blue">
                                  <FiActivity /> ACTIVE
                                </span>
                              )}
                              {v.status === 'ASSIGNED' && (
                                <span className="dev-badge-blue">
                                  <FiUsers /> ASSIGNED
                                </span>
                              )}
                              {v.status === 'OFFLINE' && (
                                <span className="dev-badge-gray">
                                  OFFLINE
                                </span>
                              )}
                              {v.status === 'MAINTENANCE' && (
                                <span className="dev-badge-amber">
                                  <FiTool /> MAINTENANCE
                                </span>
                              )}
                            </td>
                            <td>
                              {v.registeredToAdmin ? (
                                <div>
                                  <div style={{ fontWeight: 600 }}>{v.registeredToAdminName || 'Admin'}</div>
                                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{v.registeredToAdmin}</span>
                                </div>
                              ) : (
                                <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>Pending Admin/SSO Claim</span>
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
                                  style={{ padding: '0.4rem 0.65rem' }}
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
                                  style={{ padding: '0.4rem 0.65rem' }}
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

          {/* TAB 2: ADMIN MANAGEMENT (NEW FUNCTION FOR DEVELOPER) */}
          {activeTab === 'admins' && (
            <div>
              <div className="dev-card">
                <div className="dev-card-header">
                  <div>
                    <h3 className="dev-card-title">
                      <FiBriefcase /> Admin Accounts Directory ({admins.length} Companies)
                    </h3>
                    <p style={{ margin: '0.25rem 0 0 0', fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
                      Admin accounts oversee companies and project sites. Developers can onboard new Admin accounts below.
                    </p>
                  </div>

                  <div className="dev-search-filter-row" style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                    <div style={{ position: 'relative', minWidth: '260px' }}>
                      <input
                        type="text"
                        className="dev-input"
                        placeholder="Search Company, Admin, or Email..."
                        value={adminSearch}
                        onChange={(e) => setAdminSearch(e.target.value)}
                      />
                    </div>
                    <button
                      type="button"
                      className="dev-btn-orange"
                      onClick={() => setShowAddAdminModal(true)}
                    >
                      <FiUserPlus /> Onboard New Admin
                    </button>
                  </div>
                </div>

                <div className="dev-table-wrapper">
                  <table className="dev-table">
                    <thead>
                      <tr>
                        <th>Company / Organization</th>
                        <th>Admin Name</th>
                        <th>Email Address</th>
                        <th>Phone Number</th>
                        <th>Account Status</th>
                        <th>Created Date</th>
                        <th style={{ textAlign: 'right' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {adminsLoading ? (
                        <tr>
                          <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                            Loading admin accounts...
                          </td>
                        </tr>
                      ) : filteredAdmins.length === 0 ? (
                        <tr>
                          <td colSpan={7} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                            No Admin accounts found matching your search.
                          </td>
                        </tr>
                      ) : (
                        filteredAdmins.map(a => {
                          const isActive = a.active !== false && a.status !== 'Disabled';
                          const company = a.company || a.companyName || 'Bevest Enterprise';
                          return (
                            <tr key={a.id}>
                              <td>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
                                  <div style={{ 
                                    backgroundColor: '#ffedd5', 
                                    color: '#ea580c', 
                                    width: '34px', 
                                    height: '34px', 
                                    borderRadius: '8px', 
                                    display: 'flex', 
                                    alignItems: 'center', 
                                    justifyContent: 'center',
                                    fontSize: '1.1rem',
                                    flexShrink: 0
                                  }}>
                                    <FiBriefcase />
                                  </div>
                                  <div>
                                    <strong style={{ color: 'var(--text-main)', fontSize: '0.95rem' }}>
                                      {company}
                                    </strong>
                                    <div style={{ fontSize: '0.725rem', color: 'var(--text-muted)' }}>
                                      System Administrator
                                    </div>
                                  </div>
                                </div>
                              </td>
                              <td>
                                <div style={{ fontWeight: 600 }}>{a.firstName} {a.lastName}</div>
                              </td>
                              <td>
                                <span>{a.email}</span>
                              </td>
                              <td>
                                <span>{a.phone || 'Not set'}</span>
                              </td>
                              <td>
                                {isActive ? (
                                  <span className="dev-badge-green">
                                    <FiCheckCircle /> Active
                                  </span>
                                ) : (
                                  <span className="dev-badge-red">
                                    <FiAlertCircle /> Disabled
                                  </span>
                                )}
                              </td>
                              <td>
                                {a.createdAt?.toDate ? formatDisplayDate(a.createdAt.toDate()) : 'Recent'}
                              </td>
                              <td style={{ textAlign: 'right' }}>
                                <button
                                  type="button"
                                  className="dev-btn-secondary"
                                  onClick={() => handleToggleAdminStatus(a)}
                                  style={{ padding: '0.45rem 0.75rem', fontSize: '0.8rem' }}
                                >
                                  {isActive ? 'Disable Account' : 'Activate Account'}
                                </button>
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: SUBSCRIPTIONS & PAYMENTS */}
          {activeTab === 'subscriptions' && (
            <div>
              {/* Contractors Subscriptions List */}
              <div className="dev-card">
                <div className="dev-card-header">
                  <h3 className="dev-card-title">
                    <FiCreditCard /> Contractor Subscriptions ({contractors.length} Accounts)
                  </h3>

                  <div style={{ position: 'relative', minWidth: '240px' }}>
                    <input
                      type="text"
                      className="dev-input"
                      placeholder="Search Contractor or Email..."
                      value={contractorSearch}
                      onChange={(e) => setContractorSearch(e.target.value)}
                    />
                  </div>
                </div>

                <div className="dev-table-wrapper">
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
                          <td colSpan={8} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                            Loading contractor subscriptions...
                          </td>
                        </tr>
                      ) : contractors.length === 0 ? (
                        <tr>
                          <td colSpan={8} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                            No contractors found in the database.
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
                                      <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{sub.planName || sub.planId}</span>
                                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                        ₱{(sub.monthlyPrice || 0).toLocaleString()}/mo
                                      </div>
                                    </div>
                                  ) : (
                                    <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>No Plan Configured</span>
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
                                  <div style={{ color: isExpired ? '#b91c1c' : 'var(--text-main)', fontWeight: isExpired ? 600 : 400 }}>
                                    {renewStr}
                                  </div>
                                </td>
                                <td style={{ textAlign: 'right' }}>
                                  <div style={{ display: 'inline-flex', gap: '0.4rem' }}>
                                    <button
                                      type="button"
                                      className="dev-btn-secondary"
                                      onClick={() => {
                                        setPaymentContractor(c);
                                        setPaymentAmount(c.subscription?.monthlyPrice || 200000);
                                        setShowPaymentModal(true);
                                      }}
                                      title="Record Payment"
                                      style={{ padding: '0.45rem 0.75rem' }}
                                    >
                                      <FiCreditCard /> Payment
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
                                      style={{ padding: '0.45rem 0.75rem' }}
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

                <div className="dev-table-wrapper">
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
                          <td colSpan={8} style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--text-muted)' }}>
                            No payment transactions recorded yet. Click "Record & Approve Payment" to add one.
                          </td>
                        </tr>
                      ) : (
                        paymentHistory.map(p => (
                          <tr key={p.id}>
                            <td><strong>{p.referenceNumber || p.id}</strong></td>
                            <td>{p.contractorEmail}</td>
                            <td>{p.planName}</td>
                            <td><strong style={{ color: '#15803d' }}>₱{Number(p.amount || 0).toLocaleString()}</strong></td>
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

          {/* TAB 4: TROUBLESHOOT CENTER */}
          {activeTab === 'troubleshoot' && (
            <div>
              {/* Alert Diagnostics Banner */}
              <div className="dev-card" style={{ borderLeft: '4px solid #f59e0b', backgroundColor: '#fffbeb' }}>
                <div className="dev-card-header" style={{ borderBottomColor: '#fde68a' }}>
                  <h3 className="dev-card-title" style={{ color: '#b45309' }}>
                    <FiAlertTriangle /> Subscription Health Diagnostics
                  </h3>
                </div>
                <p style={{ color: '#92400e', fontSize: '0.875rem', lineHeight: '1.5', margin: '0 0 1.25rem 0' }}>
                  Use this console to diagnose contractor subscription issues, override license statuses, grant emergency quota boosts, or fix billing sync conflicts.
                </p>

                <div className="dev-form-group">
                  <label className="dev-form-label" style={{ color: '#78350f' }}>SELECT CONTRACTOR TO INSPECT & TROUBLESHOOT</label>
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
                    style={{ backgroundColor: '#ffffff' }}
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
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>UID: {troubleshootTarget.id}</span>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
                    <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <div className="dev-stat-title">Assigned Plan</div>
                      <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-main)', marginTop: '4px' }}>
                        {troubleshootTarget.subscription?.planName || 'No Active Plan'}
                      </div>
                    </div>

                    <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <div className="dev-stat-title">Current Vest Quota</div>
                      <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-main)', marginTop: '4px' }}>
                        {troubleshootTarget.subscription?.vestLimit || 50} Vests
                      </div>
                    </div>

                    <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <div className="dev-stat-title">SSO Quota</div>
                      <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-main)', marginTop: '4px' }}>
                        {troubleshootTarget.subscription?.ssoLimit || 10} SSOs
                      </div>
                    </div>

                    <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                      <div className="dev-stat-title">Subscription Expiry</div>
                      <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-main)', marginTop: '4px' }}>
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

                  <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      className="dev-btn-orange"
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
      </main>

      {/* MODAL: ONBOARD NEW ADMIN (NEW FUNCTION FOR DEVELOPER) */}
      {showAddAdminModal && (
        <div className="dev-modal-overlay">
          <div className="dev-modal-card">
            <div className="dev-modal-header">
              <h3>Onboard New Admin Account</h3>
              <button
                type="button"
                className="dev-modal-close"
                onClick={() => setShowAddAdminModal(false)}
              >
                <FiX />
              </button>
            </div>

            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginBottom: '1.25rem', lineHeight: '1.4' }}>
              Create an administrative account for a company or client organization. Admins have oversight to manage contractor accounts, site safety officers, and monitor active workers.
            </p>

            <form onSubmit={handleCreateAdmin}>
              {/* WHICH COMPANY IS THE ADMIN (EXPLICIT USER REQUIREMENT) */}
              <div className="dev-form-group">
                <label className="dev-form-label">WHICH COMPANY IS THE ADMIN? (COMPANY / CLIENT NAME) *</label>
                <input
                  type="text"
                  className="dev-input"
                  placeholder="e.g. BuildCorp Infrastructure, J&T Express Philippines, Apex Builders"
                  value={adminCompany}
                  onChange={(e) => setAdminCompany(e.target.value)}
                  required
                  autoFocus
                />
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.35rem' }}>
                  Specify the organization or client enterprise this Admin oversees.
                </div>
              </div>

              <div className="dev-form-row">
                <div className="dev-form-group">
                  <label className="dev-form-label">FIRST NAME *</label>
                  <input
                    type="text"
                    className="dev-input"
                    placeholder="First name"
                    value={adminFirstName}
                    onChange={(e) => setAdminFirstName(e.target.value)}
                    required
                  />
                </div>

                <div className="dev-form-group">
                  <label className="dev-form-label">LAST NAME *</label>
                  <input
                    type="text"
                    className="dev-input"
                    placeholder="Last name"
                    value={adminLastName}
                    onChange={(e) => setAdminLastName(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="dev-form-group">
                <label className="dev-form-label">ADMIN EMAIL ADDRESS *</label>
                <input
                  type="email"
                  className="dev-input"
                  placeholder="admin@company.com"
                  value={adminEmail}
                  onChange={(e) => setAdminEmail(e.target.value)}
                  required
                />
              </div>

              <div className="dev-form-group">
                <label className="dev-form-label">PHONE NUMBER *</label>
                <PhoneInput
                  value={adminPhone}
                  onChange={(p) => setAdminPhone(p)}
                  required
                />
              </div>

              <div className="dev-form-group">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.45rem' }}>
                  <label className="dev-form-label" style={{ margin: 0 }}>TEMPORARY PASSWORD *</label>
                  <button
                    type="button"
                    className="dev-btn-secondary"
                    style={{ padding: '0.2rem 0.6rem', fontSize: '0.75rem' }}
                    onClick={() => setAdminPassword(`ADM-2026-${Math.floor(1000 + Math.random() * 9000)}`)}
                  >
                    <FiRefreshCw /> Auto-Generate
                  </button>
                </div>
                <input
                  type="text"
                  className="dev-input"
                  value={adminPassword}
                  onChange={(e) => setAdminPassword(e.target.value)}
                  required
                />
              </div>

              <div className="dev-form-group">
                <label className="dev-form-label">ASSIGNED ROLE</label>
                <input
                  type="text"
                  className="dev-input"
                  value="ADMIN (System Safety Oversight)"
                  readOnly
                  disabled
                  style={{ backgroundColor: '#f8fafc', color: '#ea580c', fontWeight: 600 }}
                />
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className="dev-btn-secondary"
                  onClick={() => setShowAddAdminModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="dev-btn-orange"
                  disabled={adminCreating}
                >
                  {adminCreating ? 'Creating Admin Account...' : 'Create Admin Account'}
                </button>
              </div>
            </form>
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

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className="dev-btn-secondary"
                  onClick={() => setShowPaymentModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="dev-btn-orange"
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

            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginBottom: '1.25rem' }}>
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

            <div className="dev-form-group">
              <label className="dev-form-label">INITIAL VEST STATUS *</label>
              <select
                className="dev-select"
                value={batchStatus}
                onChange={(e) => setBatchStatus(e.target.value)}
                required
              >
                <option value="AVAILABLE">AVAILABLE (Ready for Assignment)</option>
                <option value="ACTIVE">ACTIVE (In Use)</option>
                <option value="OFFLINE">OFFLINE (Storage / Disconnected)</option>
                <option value="MAINTENANCE">MAINTENANCE (Inspection Required)</option>
              </select>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="dev-btn-secondary"
                onClick={() => setShowBatchModal(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="dev-btn-orange"
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

            <div style={{ background: '#ffffff', padding: '1.25rem', borderRadius: '12px', border: '2px dashed var(--border-color)', display: 'inline-block', margin: '0 auto 1.25rem auto' }}>
              {viewQrDataUrl && <img src={viewQrDataUrl} alt="QR" style={{ width: '220px', height: '220px', display: 'block' }} />}
              <div style={{ fontWeight: 800, color: '#0f172a', fontSize: '1.1rem', marginTop: '8px' }}>
                {selectedVestQr.vestId}
              </div>
              <div style={{ fontSize: '0.75rem', color: '#ea580c', fontWeight: 700 }}>
                {selectedVestQr.batchNumber} • BEVEST SMART IOT
              </div>
            </div>

            <div style={{ textAlign: 'left', background: '#f8fafc', border: '1px solid var(--border-color)', padding: '0.85rem 1rem', borderRadius: '8px', marginBottom: '1.25rem', fontSize: '0.8125rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Status:</span>
                <span style={{ fontWeight: 600 }}>{selectedVestQr.status || 'AVAILABLE'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span style={{ color: 'var(--text-muted)' }}>Model:</span>
                <span>{selectedVestQr.model || 'Bevest Smart Vest v2'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)' }}>Security Token:</span>
                <span style={{ fontFamily: 'monospace', color: '#ea580c', fontWeight: 700 }}>{selectedVestQr.hardwareToken || 'N/A'}</span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'center', flexWrap: 'wrap' }}>
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
                  className="dev-btn-orange"
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
