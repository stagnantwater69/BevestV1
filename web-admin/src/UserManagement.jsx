import React, { useState, useEffect } from 'react';
import { 
  FiPlus, FiFilter, FiCalendar, FiEdit2, FiSlash, FiChevronLeft, FiChevronRight, 
  FiCheckCircle, FiLock, FiRefreshCw, FiSearch, FiCheck, FiPackage, FiX, FiLayers, FiAlertCircle, FiTrendingUp
} from 'react-icons/fi';
import { TbHistory } from 'react-icons/tb';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { doc, setDoc, updateDoc, collection, getDocs } from 'firebase/firestore';
import { secondaryAuth, db } from './firebase';
import { 
  SUBSCRIPTION_PLANS, 
  calculateCustomPricing,
  formatPhp, 
  calculateRenewalDate, 
  getContractorSubscription 
} from './subscriptionPlans';

const UserManagement = () => {
  const [view, setView] = useState('list'); // 'list' | 'add'
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    phone: '',
    email: '',
    password: '',
    role: 'Contractor',
    subscriptionPlan: 'BUSINESS', // 3 tiers: BUSINESS, ENTERPRISE, CUSTOM
    billingCycle: 'monthly',
    customVests: 130, // Default > 125
    customSsos: 25,
    customDiscount: 10,
  });
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const [users, setUsers] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [sortOrder, setSortOrder] = useState('asc');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  // Plan editing modal for existing contractors
  const [editingContractor, setEditingContractor] = useState(null);
  const [editPlanId, setEditPlanId] = useState('BUSINESS');
  const [editBillingCycle, setEditBillingCycle] = useState('monthly');
  const [editCustomVests, setEditCustomVests] = useState(130);
  const [editCustomSsos, setEditCustomSsos] = useState(25);
  const [editCustomDiscount, setEditCustomDiscount] = useState(10);
  const [savingEdit, setSavingEdit] = useState(false);

  const fetchUsers = async () => {
    try {
      const querySnapshot = await getDocs(collection(db, "users"));
      const usersData = [];
      querySnapshot.forEach((doc) => {
        usersData.push({ id: doc.id, ...doc.data() });
      });
      setUsers(usersData);
    } catch (err) {
      console.error("Error fetching users:", err);
    }
  };

  useEffect(() => {
    if (view === 'list') {
      fetchUsers();
    }
  }, [view]);

  const filteredUsers = users
    .filter(u => {
      const matchesSearch = `${u.firstName || ''} ${u.lastName || ''}`.toLowerCase().includes(searchQuery.toLowerCase()) || 
                            (u.email || '').toLowerCase().includes(searchQuery.toLowerCase());
      const matchesRole = roleFilter ? (u.role || '').toLowerCase() === roleFilter.toLowerCase() : true;
      return matchesSearch && matchesRole;
    })
    .sort((a, b) => {
      const nameA = `${a.firstName || ''} ${a.lastName || ''}`.toLowerCase();
      const nameB = `${b.firstName || ''} ${b.lastName || ''}`.toLowerCase();
      if (sortOrder === 'asc') {
        return nameA.localeCompare(nameB);
      } else {
        return nameB.localeCompare(nameA);
      }
    });

  const totalPages = Math.ceil(filteredUsers.length / itemsPerPage) || 1;
  const paginatedUsers = filteredUsers.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  const handleGeneratePassword = () => {
    const randomNum = Math.floor(1000 + Math.random() * 9000);
    setFormData({ ...formData, password: `GID-2026-${randomNum}` });
  };

  // Live calculation for Custom Plan in the Add Form
  const customPricing = calculateCustomPricing(formData.customVests, formData.customDiscount);

  // Live calculation for Custom Plan in the Edit Modal
  const editCustomPricing = calculateCustomPricing(editCustomVests, editCustomDiscount);

  const handleCreateUser = async () => {
    if (!formData.firstName || !formData.lastName || !formData.email || !formData.password) {
      setErrorMsg("Please fill in all required fields and generate a password.");
      return;
    }
    
    setLoading(true);
    setErrorMsg('');
    try {
      // Create user in Firebase Auth using the secondary instance (doesn't log admin out)
      const userCredential = await createUserWithEmailAndPassword(secondaryAuth, formData.email, formData.password);
      const user = userCredential.user;
      
      const now = new Date();
      const renew = calculateRenewalDate(now, formData.billingCycle);

      let vestLimit, ssoLimit, monthlyPrice, annualPrice, discountPercent, storageGb, planName;

      if (formData.subscriptionPlan === 'CUSTOM') {
        vestLimit = Math.max(0, parseInt(formData.customVests, 10) || 125);
        ssoLimit = Math.max(1, parseInt(formData.customSsos, 10) || 25);
        monthlyPrice = customPricing.monthlyPrice;
        annualPrice = customPricing.annualPrice;
        discountPercent = customPricing.discountPercent;
        storageGb = 100;
        planName = 'Custom';
      } else {
        const selectedPlan = SUBSCRIPTION_PLANS[formData.subscriptionPlan] || SUBSCRIPTION_PLANS.BUSINESS;
        vestLimit = selectedPlan.vests;
        ssoLimit = selectedPlan.ssos;
        monthlyPrice = selectedPlan.monthlyPrice;
        annualPrice = selectedPlan.annualPrice;
        discountPercent = selectedPlan.discountPercent;
        storageGb = selectedPlan.storageGb;
        planName = selectedPlan.name;
      }

      // Add user details to Firestore
      await setDoc(doc(db, "users", user.uid), {
        firstName: formData.firstName.trim(),
        lastName: formData.lastName.trim(),
        phone: formData.phone.trim(),
        email: formData.email.trim(),
        role: 'CONTRACTOR',
        active: true,
        status: 'Active',
        createdAt: now,
        subscription: {
          planId: formData.subscriptionPlan,
          planName,
          billingCycle: formData.billingCycle,
          vestLimit,
          ssoLimit,
          storageGb,
          monthlyPrice,
          annualPrice,
          discountPercent,
          assignedAt: now,
          renewDate: renew,
          status: 'Active'
        }
      });
      
      // Sign out the secondary instance immediately
      await secondaryAuth.signOut();
      
      // Reset form and go back to list
      setFormData({
        firstName: '',
        lastName: '',
        phone: '',
        email: '',
        password: '',
        role: 'Contractor',
        subscriptionPlan: 'BUSINESS',
        billingCycle: 'monthly',
        customVests: 130,
        customSsos: 25,
        customDiscount: 10,
      });
      setView('list');
      alert("Contractor created successfully with assigned subscription plan!");
    } catch (error) {
      console.error("Error creating contractor:", error);
      setErrorMsg(error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenEditPlan = (contractor) => {
    setEditingContractor(contractor);
    const sub = getContractorSubscription(contractor);
    const planId = sub.planId === 'STANDARD' ? 'BUSINESS' : (sub.planId || 'BUSINESS');
    setEditPlanId(planId);
    setEditBillingCycle(sub.billingCycle || 'monthly');
    setEditCustomVests(typeof sub.vestLimit === 'number' && sub.vestLimit > 100 ? sub.vestLimit : 130);
    setEditCustomSsos(typeof sub.ssoLimit === 'number' ? sub.ssoLimit : 25);
    setEditCustomDiscount(sub.discountPercent || 10);
  };

  const handleSavePlanEdit = async () => {
    if (!editingContractor) return;
    setSavingEdit(true);
    try {
      const now = new Date();
      const renew = calculateRenewalDate(now, editBillingCycle);

      let vestLimit, ssoLimit, monthlyPrice, annualPrice, discountPercent, storageGb, planName;

      if (editPlanId === 'CUSTOM') {
        vestLimit = Math.max(0, parseInt(editCustomVests, 10) || 125);
        ssoLimit = Math.max(1, parseInt(editCustomSsos, 10) || 25);
        monthlyPrice = editCustomPricing.monthlyPrice;
        annualPrice = editCustomPricing.annualPrice;
        discountPercent = editCustomPricing.discountPercent;
        storageGb = 100;
        planName = 'Custom';
      } else {
        const selectedPlan = SUBSCRIPTION_PLANS[editPlanId] || SUBSCRIPTION_PLANS.BUSINESS;
        vestLimit = selectedPlan.vests;
        ssoLimit = selectedPlan.ssos;
        monthlyPrice = selectedPlan.monthlyPrice;
        annualPrice = selectedPlan.annualPrice;
        discountPercent = selectedPlan.discountPercent;
        storageGb = selectedPlan.storageGb;
        planName = selectedPlan.name;
      }

      await updateDoc(doc(db, "users", editingContractor.id), {
        subscription: {
          planId: editPlanId,
          planName,
          billingCycle: editBillingCycle,
          vestLimit,
          ssoLimit,
          storageGb,
          monthlyPrice,
          annualPrice,
          discountPercent,
          assignedAt: now,
          renewDate: renew,
          status: 'Active'
        }
      });

      setEditingContractor(null);
      await fetchUsers();
      alert("Subscription plan updated successfully!");
    } catch (err) {
      console.error("Failed to update subscription:", err);
      alert("Error updating subscription: " + err.message);
    } finally {
      setSavingEdit(false);
    }
  };

  if (view === 'add') {
    return (
      <div className="dashboard-content">
        {/* Breadcrumb matching Wireframe 1 */}
        <div className="breadcrumb">
          <span className="bc-link" onClick={() => setView('list')}>User Management</span> 
          <span className="bc-separator">&gt;</span> 
          <span className="bc-current">Add Contractor</span>
        </div>
        
        {/* Page Header matching Wireframe 1 */}
        <div className="page-header" style={{ marginBottom: '2rem' }}>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: '0.25rem' }}>Onboard New Personnel</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>
            Fill out the mandatory information below to grant system access and assign safety monitoring protocols.
          </p>
        </div>

        <div className="add-user-card">
          {/* Personal Data Section from Wireframe 1 */}
          <div className="form-section-container" style={{ display: 'flex', flexWrap: 'wrap', gap: '2rem' }}>
            <div className="form-section-info" style={{ flex: '1 1 250px' }}>
              <h3>Personal Data</h3>
              <p>Core identity information for the employee or contractor.</p>
            </div>
            
            <div className="form-grid um-form-grid" style={{ flex: '2 1 400px' }}>
              <div className="form-group-custom">
                <label className="form-label">FIRST NAME</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="e.g. Marcus" 
                  value={formData.firstName}
                  onChange={(e) => setFormData({...formData, firstName: e.target.value})}
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">PHONE NUMBER</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="+1 (555) 000-0000"
                  value={formData.phone}
                  onChange={(e) => setFormData({...formData, phone: e.target.value})}
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">LAST NAME</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="e.g. Thorne"
                  value={formData.lastName}
                  onChange={(e) => setFormData({...formData, lastName: e.target.value})}
                />
              </div>

              <div className="form-group-custom">
                <label className="form-label">EMAIL ADDRESS</label>
                <input 
                  type="email" 
                  className="form-input" 
                  placeholder="m.thorne@industrialcorp.com"
                  value={formData.email}
                  onChange={(e) => setFormData({...formData, email: e.target.value})}
                />
              </div>

              {/* ASSIGN ROLE: Grayed out / disabled textfield defaulted to Contractor */}
              <div className="form-group-custom">
                <label className="form-label">ASSIGN ROLE</label>
                <input 
                  type="text" 
                  className="form-input" 
                  value="Contractor" 
                  disabled 
                  readOnly
                  style={{
                    backgroundColor: '#f1f5f9',
                    color: '#475569',
                    cursor: 'not-allowed',
                    fontWeight: 600,
                    borderColor: '#cbd5e1'
                  }}
                />
              </div>

              {/* PASSWORD input with generate button */}
              <div className="form-group-custom">
                <label className="form-label">PASSWORD</label>
                <div className="password-input-group">
                  <input 
                    type="text" 
                    className="form-input password-field" 
                    placeholder="Click generate to create..." 
                    value={formData.password}
                    readOnly
                  />
                  <button className="icon-btn lock-btn" type="button" onClick={handleGeneratePassword} title="Generate Password">
                    {formData.password ? <FiLock /> : <FiRefreshCw />}
                  </button>
                </div>
              </div>
            </div>
          </div>
          
          <hr className="form-divider" />

          {/* Admin Subscription Plan Assignment Section (3 Tiers: Business, Enterprise, Custom) */}
          <div className="form-section-container" style={{ display: 'flex', flexWrap: 'wrap', gap: '2rem' }}>
            <div className="form-section-info" style={{ flex: '1 1 250px' }}>
              <h3>Subscription Plan</h3>
              <p>Assign safety vest quota, SSO accounts allowance, and billing rate for this contractor.</p>
              
              <div style={{ marginTop: '1.5rem' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Billing Cycle
                </span>
                <div className="admin-cycle-pill-group" style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
                  <button
                    type="button"
                    className={`cycle-btn ${formData.billingCycle === 'monthly' ? 'active' : ''}`}
                    onClick={() => setFormData({ ...formData, billingCycle: 'monthly' })}
                  >
                    Monthly
                  </button>
                  <button
                    type="button"
                    className={`cycle-btn ${formData.billingCycle === 'annual' ? 'active' : ''}`}
                    onClick={() => setFormData({ ...formData, billingCycle: 'annual' })}
                  >
                    Annual
                  </button>
                </div>
              </div>
            </div>

            <div style={{ flex: '2 1 400px' }}>
              <div className="admin-plan-select-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))' }}>
                
                {/* 1. BUSINESS (No discount) */}
                <div 
                  className={`admin-plan-card ${formData.subscriptionPlan === 'BUSINESS' ? 'selected' : ''}`}
                  onClick={() => setFormData({ ...formData, subscriptionPlan: 'BUSINESS' })}
                >
                  <div className="admin-plan-card-header">
                    <span className="admin-plan-name">Business</span>
                    {formData.subscriptionPlan === 'BUSINESS' && <span className="admin-plan-check"><FiCheck /></span>}
                  </div>
                  <div className="admin-plan-price">
                    {formData.billingCycle === 'annual'
                      ? `${formatPhp(SUBSCRIPTION_PLANS.BUSINESS.annualPrice)} /yr`
                      : `${formatPhp(SUBSCRIPTION_PLANS.BUSINESS.monthlyPrice)} /mo`}
                  </div>
                  <div className="admin-plan-limits">
                    • <strong>50 Vests</strong> for workers<br />
                    • <strong>10 SSOs</strong> can be created<br />
                    • 20 GB Data Storage
                  </div>
                </div>

                {/* 2. ENTERPRISE (10% discount) */}
                <div 
                  className={`admin-plan-card ${formData.subscriptionPlan === 'ENTERPRISE' ? 'selected' : ''}`}
                  onClick={() => setFormData({ ...formData, subscriptionPlan: 'ENTERPRISE' })}
                >
                  <div className="admin-plan-card-header">
                    <span className="admin-plan-name">Enterprise</span>
                    <span className="admin-plan-discount-badge">10% OFF</span>
                    {formData.subscriptionPlan === 'ENTERPRISE' && <span className="admin-plan-check"><FiCheck /></span>}
                  </div>
                  <div className="admin-plan-price">
                    <span className="admin-plan-orig-price">
                      {formData.billingCycle === 'annual'
                        ? formatPhp(SUBSCRIPTION_PLANS.ENTERPRISE.baseAnnualPrice)
                        : formatPhp(SUBSCRIPTION_PLANS.ENTERPRISE.baseMonthlyPrice)}
                    </span>
                    {formData.billingCycle === 'annual'
                      ? `${formatPhp(SUBSCRIPTION_PLANS.ENTERPRISE.annualPrice)} /yr`
                      : `${formatPhp(SUBSCRIPTION_PLANS.ENTERPRISE.monthlyPrice)} /mo`}
                  </div>
                  <div className="admin-plan-limits">
                    • <strong>100 Vests</strong> for workers<br />
                    • <strong>20 SSOs</strong> can be created<br />
                    • 50 GB Data Storage
                  </div>
                </div>

                {/* 3. CUSTOM (Calculation for > 125 vests) */}
                <div 
                  className={`admin-plan-card ${formData.subscriptionPlan === 'CUSTOM' ? 'selected' : ''}`}
                  onClick={() => setFormData({ ...formData, subscriptionPlan: 'CUSTOM' })}
                >
                  <div className="admin-plan-card-header">
                    <span className="admin-plan-name">Custom (Scale)</span>
                    <span className="admin-plan-placeholder-badge">&gt; 125 Vests</span>
                    {formData.subscriptionPlan === 'CUSTOM' && <span className="admin-plan-check"><FiCheck /></span>}
                  </div>
                  <div className="admin-plan-price">
                    {formData.billingCycle === 'annual'
                      ? `${formatPhp(customPricing.annualPrice)} /yr`
                      : `${formatPhp(customPricing.monthlyPrice)} /mo`}
                  </div>
                  <div className="admin-plan-limits">
                    • <strong>{formData.customVests} Vests</strong> (custom)<br />
                    • <strong>{formData.customSsos} SSOs</strong> allowed<br />
                    • Unlimited Data Storage
                  </div>
                </div>

              </div>

              {/* Custom Plan Calculator for Admin (> 125 vests) */}
              {formData.subscriptionPlan === 'CUSTOM' && (
                <div className="admin-custom-calculator-card" style={{ marginTop: '1.25rem', padding: '1.25rem', backgroundColor: '#f8fafc', border: '1px solid #cbd5e1', borderRadius: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                    <FiTrendingUp style={{ color: 'var(--primary-color)', fontSize: '1.2rem' }} />
                    <strong style={{ fontSize: '0.95rem', color: '#0f172a' }}>
                      Custom Tier Pricing Calculator (&gt; 125 Vests)
                    </strong>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem', marginBottom: '1.25rem' }}>
                    <div>
                      <label className="form-label" style={{ fontSize: '0.78rem' }}>NUMBER OF VESTS</label>
                      <input
                        type="number"
                        className="form-input"
                        min="1"
                        value={formData.customVests}
                        onChange={(e) => setFormData({ ...formData, customVests: e.target.value })}
                        placeholder="e.g. 150"
                      />
                      {Number(formData.customVests) <= 125 ? (
                        <span style={{ fontSize: '0.72rem', color: '#d97706', display: 'block', marginTop: '0.25rem' }}>
                          ⚠️ Note: Custom tier is recommended for &gt; 125 vests.
                        </span>
                      ) : (
                        <span style={{ fontSize: '0.72rem', color: '#16a34a', display: 'block', marginTop: '0.25rem' }}>
                          ✓ Large deployment (&gt; 125 vests)
                        </span>
                      )}
                    </div>

                    <div>
                      <label className="form-label" style={{ fontSize: '0.78rem' }}>SSO CREATION LIMIT</label>
                      <input
                        type="number"
                        className="form-input"
                        min="1"
                        value={formData.customSsos}
                        onChange={(e) => setFormData({ ...formData, customSsos: e.target.value })}
                        placeholder="e.g. 30"
                      />
                    </div>

                    <div>
                      <label className="form-label" style={{ fontSize: '0.78rem' }}>VOLUME DISCOUNT (%)</label>
                      <input
                        type="number"
                        className="form-input"
                        min="0"
                        max="50"
                        value={formData.customDiscount}
                        onChange={(e) => setFormData({ ...formData, customDiscount: e.target.value })}
                        placeholder="10"
                      />
                    </div>
                  </div>

                  {/* Pricing Breakdown */}
                  <div style={{ backgroundColor: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '1rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.825rem', color: '#64748b' }}>
                      <span>Base Hardware Rate:</span>
                      <span>₱4,000 / vest / month</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.825rem', color: '#64748b' }}>
                      <span>Base Monthly ({formData.customVests} vests × ₱4,000):</span>
                      <span>{formatPhp(customPricing.baseMonthlyPrice)} /mo</span>
                    </div>
                    {customPricing.discountPercent > 0 && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.825rem', color: '#15803d' }}>
                        <span>Volume Discount ({customPricing.discountPercent}% off):</span>
                        <span>-{formatPhp(customPricing.baseMonthlyPrice - customPricing.monthlyPrice)} /mo</span>
                      </div>
                    )}
                    <hr style={{ border: 'none', borderTop: '1px dashed #e2e8f0', margin: '0.25rem 0' }} />
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.95rem', fontWeight: 700, color: '#0f172a' }}>
                      <span>Calculated Monthly:</span>
                      <span style={{ color: 'var(--primary-color)' }}>{formatPhp(customPricing.monthlyPrice)} /month</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', fontWeight: 600, color: '#475569' }}>
                      <span>Calculated Annual:</span>
                      <span>{formatPhp(customPricing.annualPrice)} /year</span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
          
          <hr className="form-divider" />
          
          {errorMsg && (
            <div className="error-message" style={{ marginBottom: '1rem', textAlign: 'left' }}>
              {errorMsg}
            </div>
          )}

          <div className="form-actions">
            <button className="btn-secondary" onClick={() => setView('list')} disabled={loading}>
              Cancel
            </button>
            <button className="btn-primary" onClick={handleCreateUser} disabled={loading}>
              {loading ? 'CREATING...' : 'ADD CONTRACTOR'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-content">
      <div className="page-header-flex" style={{ flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1>User Management</h1>
          <p>Manage system access, roles, and safety credentials for all personnel.</p>
        </div>
        {/* Changed button text from Register New User to Add Contractor */}
        <button className="btn-primary" onClick={() => setView('add')}>
          <FiPlus style={{ fontSize: '1.2rem' }} /> Add Contractor
        </button>
      </div>

      <div className="um-summary-card">
        <div className="stat-title">ACTIVE USERS</div>
        <div className="stat-value">{users.length}</div>
      </div>

      <div className="um-table-container">
        <div className="filter-bar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div className="search-bar um-search-bar" style={{ flex: '1 1 300px', backgroundColor: '#fff', border: '1px solid var(--border-color)', margin: 0 }}>
            <FiSearch className="search-icon" />
            <input 
              type="text" 
              placeholder="Search users by name or email..." 
              value={searchQuery}
              onChange={(e) => {setSearchQuery(e.target.value); setCurrentPage(1);}}
            />
          </div>
          <div style={{ display: 'flex', gap: '1rem' }}>
            <select className="filter-btn" style={{ appearance: 'auto' }} value={roleFilter} onChange={(e) => {setRoleFilter(e.target.value); setCurrentPage(1);}}>
              <option value="">All Roles</option>
              <option value="Contractor">Contractor</option>
              <option value="SSO">Site Safety Officer</option>
              <option value="Admin">Admin</option>
            </select>
          </div>
        </div>

        <div className="table-responsive-wrapper" style={{ overflowX: 'auto', width: '100%' }}>
          <table className="um-table" style={{ minWidth: '850px' }}>
            <thead>
              <tr>
                <th onClick={() => setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')} style={{ cursor: 'pointer', userSelect: 'none', width: '25%' }}>
                  NAME {sortOrder === 'asc' ? '↑' : '↓'}
                </th>
                <th style={{ width: '15%' }}>ROLE</th>
                <th style={{ width: '22%' }}>PLAN & HARDWARE</th>
                <th style={{ width: '13%' }}>STATUS</th>
                <th style={{ width: '15%' }}>LAST LOGIN</th>
                <th className="align-right" style={{ width: '10%' }}>ACTIONS</th>
              </tr>
            </thead>
            <tbody>
              {paginatedUsers.map((user) => {
                const initials = ((user.firstName?.[0] || '') + (user.lastName?.[0] || '')).toUpperCase() || 'U';
                const isContractor = (user.role || '').toLowerCase() === 'contractor';
                const sub = isContractor ? getContractorSubscription(user) : null;

                return (
                  <tr key={user.id}>
                    <td>
                      <div className="user-cell">
                        <div className="user-avatar" style={{ backgroundColor: '#fed7aa' }}>{initials}</div>
                        <div className="user-info">
                          <span className="user-name">{user.firstName} {user.lastName}</span>
                          <span style={{ fontSize: '0.75rem', color: '#64748b' }}>{user.email}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className="badge role-badge">{user.role}</span>
                    </td>
                    <td>
                      {isContractor && sub ? (
                        <div 
                          className="table-plan-badge-wrapper"
                          onClick={() => handleOpenEditPlan(user)}
                          title="Click to edit subscription"
                          style={{ cursor: 'pointer' }}
                        >
                          <span className={`table-plan-badge plan-${sub.planId.toLowerCase()}`}>
                            {sub.planName}
                          </span>
                          <span className="table-plan-limits-text">
                            {sub.vestLimit} vests • {sub.ssoLimit} SSOs
                          </span>
                        </div>
                      ) : (
                        <span style={{ color: '#94a3b8' }}>--</span>
                      )}
                    </td>
                    <td>
                      <div className="status-indicator">
                        <span className="status-dot active"></span>
                        {user.status || 'Active'}
                      </div>
                    </td>
                    <td>
                      <div className="login-info">
                        <span className="login-date">--</span>
                        <span className="login-time"></span>
                      </div>
                    </td>
                    <td className="align-right">
                      <div className="action-buttons">
                        {isContractor && (
                          <button 
                            className="action-btn" 
                            title="Edit Subscription Plan" 
                            onClick={() => handleOpenEditPlan(user)}
                          >
                            <FiPackage />
                          </button>
                        )}
                        <button className="action-btn" title="Edit"><FiEdit2 /></button>
                        <button className="action-btn" title="Disable"><FiSlash /></button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div className="pagination" style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
            Showing {filteredUsers.length === 0 ? 0 : ((currentPage - 1) * itemsPerPage) + 1} to {Math.min(currentPage * itemsPerPage, filteredUsers.length)} of {filteredUsers.length} users
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <button className="page-btn" disabled={currentPage === 1} onClick={() => setCurrentPage(p => Math.max(1, p - 1))}><FiChevronLeft /></button>
            <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>Page {currentPage} of {totalPages}</span>
            <button className="page-btn" disabled={currentPage === totalPages} onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}><FiChevronRight /></button>
          </div>
        </div>
      </div>

      {/* Admin Edit Subscription Plan Modal */}
      {editingContractor && (
        <div className="modal-backdrop" onClick={() => !savingEdit && setEditingContractor(null)}>
          <div className="modal-content sub-modal" onClick={e => e.stopPropagation()} style={{ maxWidth: '560px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 700 }}>
                Manage Subscription Plan
              </h3>
              <button 
                type="button" 
                onClick={() => setEditingContractor(null)} 
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.25rem', color: '#64748b' }}
              >
                <FiX />
              </button>
            </div>

            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginBottom: '1.25rem' }}>
              Contractor: <strong>{editingContractor.firstName} {editingContractor.lastName}</strong> ({editingContractor.email})
            </p>

            <div style={{ marginBottom: '1rem' }}>
              <label className="form-label">ASSIGN PLAN</label>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.75rem' }}>
                {Object.values(SUBSCRIPTION_PLANS).map((p) => (
                  <div
                    key={p.id}
                    onClick={() => setEditPlanId(p.id)}
                    style={{
                      border: editPlanId === p.id ? '2px solid var(--primary-color)' : '1px solid #cbd5e1',
                      backgroundColor: editPlanId === p.id ? '#fff7ed' : '#ffffff',
                      borderRadius: '8px',
                      padding: '0.75rem',
                      cursor: 'pointer',
                      transition: 'all 0.2s',
                      textAlign: 'center'
                    }}
                  >
                    <div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#0f172a' }}>{p.name}</div>
                    <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.2rem' }}>
                      {p.id === 'CUSTOM' ? '> 125 Vests' : `${p.vests} Vests`}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* If Custom is selected in Edit Modal */}
            {editPlanId === 'CUSTOM' && (
              <div style={{ backgroundColor: '#f8fafc', border: '1px solid #cbd5e1', borderRadius: '8px', padding: '1rem', marginBottom: '1.25rem' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', marginBottom: '0.75rem' }}>
                  <div>
                    <label className="form-label" style={{ fontSize: '0.75rem' }}>CUSTOM VESTS (&gt; 125)</label>
                    <input
                      type="number"
                      className="form-input"
                      value={editCustomVests}
                      onChange={(e) => setEditCustomVests(e.target.value)}
                    />
                  </div>
                  <div>
                    <label className="form-label" style={{ fontSize: '0.75rem' }}>CUSTOM SSOS</label>
                    <input
                      type="number"
                      className="form-input"
                      value={editCustomSsos}
                      onChange={(e) => setEditCustomSsos(e.target.value)}
                    />
                  </div>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.825rem', color: '#0f172a', fontWeight: 600 }}>
                  <span>Calculated Rate:</span>
                  <span style={{ color: 'var(--primary-color)' }}>
                    {editBillingCycle === 'annual'
                      ? `${formatPhp(editCustomPricing.annualPrice)} /yr`
                      : `${formatPhp(editCustomPricing.monthlyPrice)} /mo`}
                  </span>
                </div>
              </div>
            )}

            <div style={{ marginBottom: '1.5rem' }}>
              <label className="form-label">BILLING CYCLE</label>
              <select 
                className="form-input form-select"
                value={editBillingCycle}
                onChange={(e) => setEditBillingCycle(e.target.value)}
              >
                <option value="monthly">Monthly Renewal</option>
                <option value="annual">Annual Renewal (Yearly)</option>
              </select>
            </div>

            <div className="modal-actions">
              <button 
                className="btn-secondary" 
                onClick={() => setEditingContractor(null)} 
                disabled={savingEdit}
              >
                Cancel
              </button>
              <button 
                className="btn-primary" 
                onClick={handleSavePlanEdit} 
                disabled={savingEdit}
              >
                {savingEdit ? 'Saving...' : 'Update Plan'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserManagement;
