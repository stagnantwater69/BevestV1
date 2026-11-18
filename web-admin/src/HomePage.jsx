import React, { useState, useEffect } from 'react';
import {
  FiLogIn, FiMenu, FiX,
  FiShield, FiActivity, FiMapPin, FiBell, FiPlus, FiMinus,
  FiCheckCircle, FiCpu, FiSmartphone, FiMonitor, FiPhone,
  FiMail, FiTarget, FiLayers, FiSend
} from 'react-icons/fi';
import { collection, addDoc } from 'firebase/firestore';
import { db } from './firebase';
import { useWebpageSettings } from './webpageConfig';
import logo from './assets/bevest_logo.png';
import safetySlide1 from './assets/carousel/safety-1.jpg';
import safetySlide2 from './assets/carousel/safety-2.jpg';
import safetySlide3 from './assets/carousel/safety-3.jpg';
import './home.css';

const CURRENT_YEAR = new Date().getFullYear();

const CAROUSEL_SLIDES = [
  {
    src: safetySlide1,
    alt: 'Construction worker wearing safety harness and high-visibility gear on scaffolding'
  },
  {
    src: safetySlide2,
    alt: 'Construction safety crew inspecting elevated steel structure'
  },
  {
    src: safetySlide3,
    alt: 'Workers climbing steel roof trusses wearing safety vests and harnesses'
  }
];

const CAPABILITIES = [
  {
    id: 'vitals',
    title: 'Real-Time Heart Rate & Body Temperature Monitoring',
    body: 'Continuously measures worker heart rate (BPM) using an integrated MAX30102 pulse oximeter and body temperature (°C) via a DS18B20 thermal probe. Alerts trigger automatically when readings enter warning or danger zones based on worker age.'
  },
  {
    id: 'fall',
    title: 'Automated Fall & Motion Detection (MPU6050 IMU)',
    body: 'Detects free-fall events, severe impacts, and prolonged worker immobility using a 6-DoF accelerometer and gyroscope. Instant emergency flags and local buzzer sirens activate within milliseconds of a fall.'
  },
  {
    id: 'gnss',
    title: 'Multi-GNSS Outdoor Site Location Tracking (NEO-M8N)',
    body: 'Tracks real-time coordinates across GPS, GLONASS, Galileo, and SBAS constellations so Site Safety Officers can pinpoint workers immediately on live site maps during an incident.'
  },
  {
    id: 'safety-handshake',
    title: 'Two-Way Safety Response & Physical SOS Button',
    body: 'Safety Officers can ping a vest remotely to trigger a 15-second safety check alarm. Workers respond with a single click on the vest button or hold it for 3 seconds to send an urgent Worker SOS trigger.'
  }
];

const HomePage = ({ onNavigateLogin }) => {
  const { settings: webpageConfig } = useWebpageSettings();

  const [currentSlide, setCurrentSlide] = useState(0);
  const [activeNav, setActiveNav] = useState('home');
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [openAccordion, setOpenAccordion] = useState('vitals');

  // Order Now Modal state
  const [showOrderModal, setShowOrderModal] = useState(false);
  const [orderCompany, setOrderCompany] = useState('');
  const [orderName, setOrderName] = useState('');
  const [orderEmail, setOrderEmail] = useState('');
  const [orderPhone, setOrderPhone] = useState('');
  const [orderQty, setOrderQty] = useState(10);
  const [orderNotes, setOrderNotes] = useState('');
  const [orderSubmitting, setOrderSubmitting] = useState(false);
  const [orderSubmitted, setOrderSubmitted] = useState(false);
  const [orderError, setOrderError] = useState('');

  // Auto-advance construction safety carousel every 5 seconds
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % CAROUSEL_SLIDES.length);
    }, 5000);
    return () => clearInterval(timer);
  }, []);

  const scrollToSection = (sectionId) => {
    setActiveNav(sectionId);
    setMobileNavOpen(false);
    const el = document.getElementById(sectionId);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const handleOpenOrder = () => {
    setOrderSubmitted(false);
    setOrderError('');
    setShowOrderModal(true);
  };

  const handleSubmitOrder = async (e) => {
    e.preventDefault();
    if (!orderCompany.trim() || !orderName.trim() || !orderEmail.trim() || !orderPhone.trim()) {
      setOrderError('Please fill in your company, name, email, and contact number.');
      return;
    }

    setOrderSubmitting(true);
    setOrderError('');
    try {
      await addDoc(collection(db, 'vestOrders'), {
        company: orderCompany.trim(),
        contactName: orderName.trim(),
        email: orderEmail.trim(),
        phone: orderPhone.trim(),
        quantity: Number(orderQty) || 1,
        notes: orderNotes.trim(),
        status: 'Pending',
        createdAt: new Date()
      });
      setOrderSubmitted(true);
      setOrderCompany('');
      setOrderName('');
      setOrderEmail('');
      setOrderPhone('');
      setOrderQty(10);
      setOrderNotes('');
    } catch (err) {
      console.warn('Order save note:', err.message);
      setOrderError('Unable to submit order online right now. Please reach us directly via Call Us or Email Us.');
    } finally {
      setOrderSubmitting(false);
    }
  };

  return (
    <div className="bevest-home-page">
      {/* 1. TOP UTILITY HEADER BAR (White) */}
      <header className="home-topbar">
        <div className="home-container home-topbar-inner">
          <div className="home-brand" onClick={() => scrollToSection('home')}>
            <img src={logo} alt="BeVest Logo" className="home-brand-logo" />
            <div className="home-brand-text">
              <div className="home-brand-name">
                Be<span>Vest</span>
              </div>
              <span className="home-brand-tagline">Monitor · Protect · Save Lives</span>
            </div>
          </div>

          <div className="home-topbar-right">
            <div className="home-info-list">
              <div className="home-info-item">
                <span className="home-info-label">Call Us</span>
                <a
                  href={`tel:${(webpageConfig.callUs || '').replace(/\s+/g, '')}`}
                  className="home-info-value"
                >
                  {webpageConfig.callUs}
                </a>
              </div>

              <div className="home-info-item">
                <span className="home-info-label">Email Us</span>
                <a
                  href={`mailto:${webpageConfig.emailUs || ''}`}
                  className="home-info-value"
                >
                  {webpageConfig.emailUs}
                </a>
              </div>
            </div>

            <button
              type="button"
              className="home-login-btn"
              onClick={onNavigateLogin}
              aria-label="Login"
              title="Login"
            >
              <FiLogIn /> <span className="home-login-text">Login</span>
            </button>
          </div>
        </div>
      </header>

      {/* 2. DARK NAVIGATION BAR */}
      <nav className="home-navbar" aria-label="Primary Navigation">
        <div className="home-container home-navbar-inner">
          <button
            type="button"
            className="home-mobile-menu-btn"
            onClick={() => setMobileNavOpen(!mobileNavOpen)}
            aria-label="Toggle Menu"
          >
            {mobileNavOpen ? <FiX /> : <FiMenu />}
          </button>

          <ul className={`home-nav-links ${mobileNavOpen ? 'open' : ''}`}>
            <li>
              <button
                type="button"
                className={`home-nav-link ${activeNav === 'home' ? 'active' : ''}`}
                onClick={() => scrollToSection('home')}
              >
                Home
              </button>
            </li>
            <li>
              <button
                type="button"
                className={`home-nav-link ${activeNav === 'objectives' ? 'active' : ''}`}
                onClick={() => scrollToSection('objectives')}
              >
                Objectives
              </button>
            </li>
            <li>
              <button
                type="button"
                className={`home-nav-link ${activeNav === 'scope' ? 'active' : ''}`}
                onClick={() => scrollToSection('scope')}
              >
                Scope
              </button>
            </li>
            <li>
              <button
                type="button"
                className={`home-nav-link ${activeNav === 'contact' ? 'active' : ''}`}
                onClick={() => scrollToSection('contact')}
              >
                Contact
              </button>
            </li>
          </ul>
        </div>
      </nav>

      {/* 3. HERO CAROUSEL SECTION */}
      <section id="home" className="home-hero">
        <div className="home-hero-slides" aria-hidden="true">
          {CAROUSEL_SLIDES.map((slide, idx) => (
            <div
              key={idx}
              className={`home-hero-slide ${idx === currentSlide ? 'active' : ''}`}
              style={{ backgroundImage: `url(${slide.src})` }}
              role="img"
              aria-label={slide.alt}
            />
          ))}
        </div>
        <div className="home-hero-overlay" />

        <div className="home-container home-hero-content">
          <span className="home-hero-eyebrow">
            {webpageConfig.smallTitle}
          </span>
          <h1 className="home-hero-title">
            {webpageConfig.bigTitle}
          </h1>
          <div className="home-hero-actions">
            <button
              type="button"
              className="home-btn-primary"
              onClick={() => scrollToSection('services')}
            >
              Our Services
            </button>
            <button
              type="button"
              className="home-btn-outline"
              onClick={() => scrollToSection('contact')}
            >
              Contact Now
            </button>
          </div>
        </div>
      </section>

      {/* 4. OVERLAPPING BEVEST ORANGE CTA BANNER */}
      <div className="home-container home-cta-wrapper">
        <div className="home-cta-bar">
          <h2 className="home-cta-text">Get your vest now</h2>
          <button
            type="button"
            className="home-cta-btn"
            onClick={handleOpenOrder}
          >
            Order Now
          </button>
        </div>
      </div>

      {/* 5. WHAT IS BEVEST & WHAT IT CAN DO (Redirect Target for "OUR SERVICES") */}
      <section id="services" className="home-section" style={{ paddingTop: '1.75rem' }}>
        <div className="home-container home-two-col">
          <div>
            <span className="home-section-eyebrow">What Is BeVest</span>
            <h2 className="home-section-title">Smart Wearable Safety For High-Risk Sites</h2>
            <p className="home-section-desc">
              <strong>BeVest</strong> is an industrial IoT smart safety vest engineered for construction and logistics workers. Combining an embedded ESP32 sensor vest, a Site Safety Officer (SSO) mobile app, and a real-time cloud dispatch dashboard, BeVest detects falls, heat stress, abnormal heart rates, and emergency SOS signals the moment they happen.
            </p>

            <div className="home-feature-pills">
              <div className="home-feature-pill">
                <div className="home-feature-pill-icon"><FiActivity /></div>
                <div>
                  <h4>Live Biometrics</h4>
                  <p>Continuous heart rate and body temperature alerts.</p>
                </div>
              </div>
              <div className="home-feature-pill">
                <div className="home-feature-pill-icon"><FiShield /></div>
                <div>
                  <h4>Fall Detection</h4>
                  <p>Instant free-fall and impact recognition via IMU.</p>
                </div>
              </div>
              <div className="home-feature-pill">
                <div className="home-feature-pill-icon"><FiMapPin /></div>
                <div>
                  <h4>Multi-GNSS GPS</h4>
                  <p>High-accuracy outdoor location pinpointing.</p>
                </div>
              </div>
              <div className="home-feature-pill">
                <div className="home-feature-pill-icon"><FiBell /></div>
                <div>
                  <h4>SOS & Check-In</h4>
                  <p>Two-way safety ping and physical siren alerts.</p>
                </div>
              </div>
            </div>
          </div>

          <div>
            <span className="home-section-eyebrow">Our Capabilities</span>
            <h2 className="home-section-title">What BeVest Can Do</h2>
            <p className="home-section-desc" style={{ marginBottom: '1.25rem' }}>
              Every smart vest works autonomously from the field to keep safety teams informed without slowing down construction workflows.
            </p>

            <div className="home-accordion">
              {CAPABILITIES.map((cap) => {
                const isOpen = openAccordion === cap.id;
                return (
                  <div key={cap.id} className="home-accordion-item">
                    <button
                      type="button"
                      className={`home-accordion-header ${isOpen ? 'open' : ''}`}
                      onClick={() => setOpenAccordion(isOpen ? '' : cap.id)}
                    >
                      <span>{cap.title}</span>
                      <span className="home-accordion-toggle">
                        {isOpen ? <FiMinus /> : <FiPlus />}
                      </span>
                    </button>
                    {isOpen && (
                      <div className="home-accordion-body">
                        {cap.body}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </section>

      {/* 6. OBJECTIVES SECTION */}
      <section id="objectives" className="home-section home-section-alt">
        <div className="home-container">
          <div style={{ textAlign: 'center', maxWidth: '680px', margin: '0 auto' }}>
            <span className="home-section-eyebrow">Purpose & Mission</span>
            <h2 className="home-section-title">Project Objectives</h2>
            <p className="home-section-desc" style={{ marginBottom: 0 }}>
              Designed to bridge the gap between active jobsite hazards and immediate emergency response for every construction crew.
            </p>
          </div>

          <div className="home-cards-grid">
            <div className="home-card">
              <div className="home-card-icon"><FiTarget /></div>
              <h3>Immediate Hazard Detection</h3>
              <p>
                Identify falls, sudden impacts, heat exhaustion, and abnormal cardiac activity in real time before minor incidents become critical emergencies.
              </p>
            </div>

            <div className="home-card">
              <div className="home-card-icon"><FiBell /></div>
              <h3>Rapid Two-Way Response</h3>
              <p>
                Enable Site Safety Officers to verify worker status remotely with an audible vest check-in and receive instant 3-second physical SOS triggers.
              </p>
            </div>

            <div className="home-card">
              <div className="home-card-icon"><FiMapPin /></div>
              <h3>Accurate Site Localization</h3>
              <p>
                Transmit Multi-GNSS coordinates directly to mobile and web dashboards so rescue personnel can locate an injured worker without delay.
              </p>
            </div>

            <div className="home-card">
              <div className="home-card-icon"><FiCheckCircle /></div>
              <h3>Centralized Safety Records</h3>
              <p>
                Maintain automated 1-minute vital history logs, incident timelines, and downloadable monthly safety compliance reports for site leadership.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 7. SCOPE SECTION */}
      <section id="scope" className="home-section">
        <div className="home-container">
          <div style={{ textAlign: 'center', maxWidth: '700px', margin: '0 auto' }}>
            <span className="home-section-eyebrow">System Architecture</span>
            <h2 className="home-section-title">System Scope</h2>
            <p className="home-section-desc" style={{ marginBottom: 0 }}>
              An end-to-end connected safety platform uniting field wearables, mobile command, and organizational oversight.
            </p>
          </div>

          <div className="home-cards-grid">
            <div className="home-card">
              <div className="home-card-icon"><FiCpu /></div>
              <h3>Smart IoT Safety Vest</h3>
              <p>Wearable ESP32 hardware engineered for demanding construction environments.</p>
              <ul className="home-scope-list">
                <li><FiCheckCircle /> ESP32 BLE Wi-Fi provisioning & 2.4 GHz telemetry</li>
                <li><FiCheckCircle /> MPU6050 IMU, MAX30102 pulse & DS18B20 temperature</li>
                <li><FiCheckCircle /> u-blox NEO-M8N Multi-GNSS receiver</li>
                <li><FiCheckCircle /> Physical D15 SOS button & D25 emergency siren</li>
              </ul>
            </div>

            <div className="home-card">
              <div className="home-card-icon"><FiSmartphone /></div>
              <h3>SSO Android Mobile App</h3>
              <p>Field command tool for Site Safety Officers overseeing active crews.</p>
              <ul className="home-scope-list">
                <li><FiCheckCircle /> QR & Bluetooth vest pairing and worker assignment</li>
                <li><FiCheckCircle /> Real-time vitals, battery & Wi-Fi signal monitoring</li>
                <li><FiCheckCircle /> Live OpenStreetMap worker location tracking</li>
                <li><FiCheckCircle /> Full-screen fall/vital alarms & incident resolution</li>
              </ul>
            </div>

            <div className="home-card">
              <div className="home-card-icon"><FiMonitor /></div>
              <h3>Web Admin & Contractor Portal</h3>
              <p>Central management console for contractors and safety administrators.</p>
              <ul className="home-scope-list">
                <li><FiCheckCircle /> Organization isolation & contractor/SSO provisioning</li>
                <li><FiCheckCircle /> Vest inventory & site project monitoring</li>
                <li><FiCheckCircle /> Live safety telemetry & historical charts</li>
                <li><FiCheckCircle /> Monthly safety reports & subscription management</li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* 8. CONTACT SECTION */}
      <section id="contact" className="home-section home-section-alt">
        <div className="home-container">
          <div className="home-contact-grid">
            <div className="home-contact-box">
              <div>
                <span className="home-section-eyebrow">Get In Touch</span>
                <h2 className="home-section-title" style={{ marginBottom: '0.75rem' }}>Contact BeVest</h2>
                <p className="home-section-desc" style={{ marginBottom: '1.5rem' }}>
                  Ready to equip your construction workforce with smart safety vests? Reach out to our team for inquiries, site deployments, or vest orders.
                </p>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  <div className="home-contact-row">
                    <div className="home-contact-icon"><FiPhone /></div>
                    <div>
                      <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
                        Call Us
                      </div>
                      <a
                        href={`tel:${(webpageConfig.callUs || '').replace(/\s+/g, '')}`}
                        style={{ fontSize: '1.05rem', fontWeight: 700, color: '#0f172a', textDecoration: 'none' }}
                      >
                        {webpageConfig.callUs}
                      </a>
                    </div>
                  </div>

                  <div className="home-contact-row">
                    <div className="home-contact-icon"><FiMail /></div>
                    <div>
                      <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
                        Email Us
                      </div>
                      <a
                        href={`mailto:${webpageConfig.emailUs || ''}`}
                        style={{ fontSize: '1.05rem', fontWeight: 700, color: '#0f172a', textDecoration: 'none' }}
                      >
                        {webpageConfig.emailUs}
                      </a>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="home-contact-box" style={{ background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)', color: '#ffffff', border: 'none' }}>
              <div>
                <span className="home-section-eyebrow" style={{ color: '#fb923c' }}>Deploy BeVest On Site</span>
                <h2 className="home-section-title" style={{ color: '#ffffff', marginBottom: '0.75rem' }}>
                  Equip Your Crew Today
                </h2>
                <p style={{ color: '#cbd5e1', fontSize: '0.95rem', lineHeight: 1.7, marginBottom: '1.75rem' }}>
                  Order verified BeVest IoT Smart Safety Vests for your construction site, or sign in to manage your existing contractors, officers, and active worker telemetry.
                </p>
              </div>

              <div style={{ display: 'flex', gap: '0.9rem', flexWrap: 'wrap' }}>
                <button type="button" className="home-btn-primary" onClick={handleOpenOrder}>
                  <FiLayers /> Order Now
                </button>
                <button type="button" className="home-btn-outline" onClick={onNavigateLogin}>
                  <FiLogIn /> Portal Login
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 9. FOOTER */}
      <footer className="home-footer">
        <div className="home-container home-footer-inner">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <img src={logo} alt="BeVest" style={{ height: '32px', width: 'auto' }} />
            <span style={{ fontWeight: 700, color: '#ffffff' }}>BeVest Smart IoT Safety System</span>
          </div>
          <div style={{ fontSize: '0.82rem', color: '#94a3b8' }}>
            © {CURRENT_YEAR} BeVest. Monitor · Protect · Save Lives.
          </div>
        </div>
      </footer>

      {/* ORDER NOW MODAL */}
      {showOrderModal && (
        <div className="home-modal-overlay" onClick={() => setShowOrderModal(false)}>
          <div className="home-modal-card" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', paddingBottom: '0.75rem', borderBottom: '1px solid #e2e8f0' }}>
              <div>
                <span style={{ fontSize: '0.72rem', fontWeight: 700, color: '#ea580c', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                  Get Your Vest Now
                </span>
                <h3 style={{ margin: '2px 0 0 0', fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
                  Order BeVest Smart Safety Vests
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setShowOrderModal(false)}
                style={{ background: '#f1f5f9', border: 'none', borderRadius: '6px', padding: '0.45rem', cursor: 'pointer', color: '#475569', display: 'flex' }}
              >
                <FiX />
              </button>
            </div>

            {orderSubmitted ? (
              <div style={{ textAlign: 'center', padding: '1rem 0' }}>
                <div style={{ width: '54px', height: '54px', borderRadius: '50%', background: '#dcfce7', color: '#16a34a', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.65rem', marginBottom: '0.85rem' }}>
                  <FiCheckCircle />
                </div>
                <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '1.15rem', color: '#0f172a' }}>Order Inquiry Received!</h4>
                <p style={{ fontSize: '0.88rem', color: '#475569', lineHeight: 1.55, marginBottom: '1.25rem' }}>
                  Thank you for choosing BeVest. Our deployment team will contact you shortly via email or phone. You may also reach us at <strong>{webpageConfig.callUs}</strong> or <strong>{webpageConfig.emailUs}</strong>.
                </p>
                <button type="button" className="home-btn-primary" onClick={() => setShowOrderModal(false)}>
                  Done
                </button>
              </div>
            ) : (
              <form onSubmit={handleSubmitOrder}>
                {orderError && (
                  <div className="error-message" style={{ marginBottom: '1rem', textAlign: 'left' }}>
                    {orderError}
                  </div>
                )}

                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
                  <div>
                    <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, display: 'block', marginBottom: '0.3rem' }}>
                      COMPANY / CONTRACTOR NAME *
                    </label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. BuildCorp Construction"
                      value={orderCompany}
                      onChange={(e) => setOrderCompany(e.target.value)}
                      required
                    />
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                    <div>
                      <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, display: 'block', marginBottom: '0.3rem' }}>
                        CONTACT PERSON *
                      </label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="Full name"
                        value={orderName}
                        onChange={(e) => setOrderName(e.target.value)}
                        required
                      />
                    </div>
                    <div>
                      <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, display: 'block', marginBottom: '0.3rem' }}>
                        VEST QUANTITY *
                      </label>
                      <input
                        type="number"
                        min="1"
                        className="form-input"
                        value={orderQty}
                        onChange={(e) => setOrderQty(e.target.value)}
                        required
                      />
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                    <div>
                      <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, display: 'block', marginBottom: '0.3rem' }}>
                        EMAIL ADDRESS *
                      </label>
                      <input
                        type="email"
                        className="form-input"
                        placeholder="name@company.com"
                        value={orderEmail}
                        onChange={(e) => setOrderEmail(e.target.value)}
                        required
                      />
                    </div>
                    <div>
                      <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, display: 'block', marginBottom: '0.3rem' }}>
                        PHONE NUMBER *
                      </label>
                      <input
                        type="tel"
                        className="form-input"
                        placeholder="+63 917 000 0000"
                        value={orderPhone}
                        onChange={(e) => setOrderPhone(e.target.value)}
                        required
                      />
                    </div>
                  </div>

                  <div>
                    <label className="form-label" style={{ fontSize: '0.75rem', fontWeight: 700, display: 'block', marginBottom: '0.3rem' }}>
                      PROJECT SITE / NOTES
                    </label>
                    <textarea
                      className="form-input"
                      rows={2}
                      placeholder="Site location or preferred deployment schedule..."
                      value={orderNotes}
                      onChange={(e) => setOrderNotes(e.target.value)}
                      style={{ height: 'auto', paddingTop: '0.6rem' }}
                    />
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1.25rem', gap: '0.75rem', flexWrap: 'wrap' }}>
                  <span style={{ fontSize: '0.78rem', color: '#64748b' }}>
                    Or call <strong>{webpageConfig.callUs}</strong>
                  </span>
                  <div style={{ display: 'flex', gap: '0.6rem' }}>
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => setShowOrderModal(false)}
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      className="home-btn-primary"
                      disabled={orderSubmitting}
                      style={{ padding: '0.7rem 1.25rem' }}
                    >
                      <FiSend /> {orderSubmitting ? 'Submitting...' : 'Submit Order'}
                    </button>
                  </div>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default HomePage;
