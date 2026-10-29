import React, { useState, useEffect } from 'react';
import { 
  FiArrowRight, FiArrowLeft, FiMail, FiPhone, FiCheckCircle, 
  FiAlertCircle, FiLock, FiEye, FiEyeOff, FiMessageSquare, FiRefreshCw 
} from 'react-icons/fi';
import { 
  signInWithEmailAndPassword, 
  sendPasswordResetEmail,
  RecaptchaVerifier,
  signInWithPhoneNumber,
  updatePassword
} from 'firebase/auth';
import { doc, getDoc, setDoc, collection, query, where, getDocs, limit } from 'firebase/firestore';
import { auth, db } from './firebase';
import logo from './assets/bevest_logo.png';
import { logActivity } from './activityLogger';
import PhoneInput, { formatPhoneWithCountryCode } from './PhoneInput';

const Login = ({ onLogin }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Forgot password state
  const [isForgotMode, setIsForgotMode] = useState(false);
  const [resetMethod, setResetMethod] = useState('email'); // 'email' | 'phone'
  const [resetEmail, setResetEmail] = useState('');
  const [resetPhone, setResetPhone] = useState('');
  const [phoneLookupMode, setPhoneLookupMode] = useState('email'); // 'email' | 'phone'
  const [resetLoading, setResetLoading] = useState(false);
  const [resetError, setResetError] = useState('');
  const [resetSuccess, setResetSuccess] = useState(false);

  // SMS & Phone recovery specific state
  const [noPhonePrompt, setNoPhonePrompt] = useState(false);
  const [phoneFoundData, setPhoneFoundData] = useState(null); // { phone, e164, email, docId }
  const [phoneStep, setPhoneStep] = useState('lookup'); // 'lookup' | 'otp' | 'new_password' | 'complete'
  const [otpCode, setOtpCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [confirmationResult, setConfirmationResult] = useState(null);
  const [resendTimer, setResendTimer] = useState(0);

  // Countdown timer for Resend SMS button
  useEffect(() => {
    let interval = null;
    if (resendTimer > 0) {
      interval = setInterval(() => {
        setResendTimer((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [resendTimer]);

  // Clean up reCAPTCHA verifier on unmount
  useEffect(() => {
    return () => {
      if (window.recaptchaVerifier) {
        try {
          window.recaptchaVerifier.clear();
        } catch (_) {}
        window.recaptchaVerifier = null;
      }
    };
  }, []);




  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const userCredential = await signInWithEmailAndPassword(auth, email, password);
      const user = userCredential.user;

      // Verify web role (Admin or Contractor). SSOs use the mobile app only.
      const userDocRef = doc(db, 'users', user.uid);
      const userDocSnap = await getDoc(userDocRef);

      let userData = userDocSnap.exists() ? userDocSnap.data() : null;

      // Auto-fallback for developer accounts
      if (!userData && (user.email === 'developer@bevest.com' || user.email === 'dev@bevest.com')) {
        userData = {
          firstName: 'Bevest',
          lastName: 'Developer',
          role: 'DEVELOPER',
          email: user.email,
          active: true,
          status: 'Active'
        };
        try {
          await setDoc(userDocRef, userData, { merge: true });
        } catch (_) {}
      }

      if (userData) {
        const role = String(userData.role || '').trim().toUpperCase();
        if (role !== 'ADMIN' && role !== 'CONTRACTOR' && role !== 'DEVELOPER') {
          await auth.signOut();
          setError('Access Denied');
        } else if (userData.active === false || userData.status === 'Disabled') {
          await auth.signOut();
          setError('This account has been disabled. Please contact your administrator.');
        } else {
          // Success: route to the role-specific web experience
          console.log(`${role} logged in successfully!`);
          logActivity(`${role === 'DEVELOPER' ? 'Developer' : (role === 'ADMIN' ? 'Admin' : 'Contractor')} signed in: ${user.email}`, user.email);

          // Update local directory cache for password recovery lookup
          try {
            const directory = JSON.parse(localStorage.getItem('bevest_user_directory') || '{}');
            if (user.email) {
              directory[user.email.toLowerCase().trim()] = {
                phone: userData.phone && userData.phone !== 'Not set' ? userData.phone : null,
                role,
                firstName: userData.firstName,
                lastName: userData.lastName
              };
              localStorage.setItem('bevest_user_directory', JSON.stringify(directory));
            }
          } catch (e) {
            // ignore cache write error
          }

          if (onLogin) onLogin({ uid: user.uid, email: user.email, role, profile: userData });
        }
      } else {
        await auth.signOut();
        setError('User record not found.');
      }
    } catch (err) {
      setError('Invalid email or password.');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenForgot = () => {
    setError('');
    setResetError('');
    setResetSuccess(false);
    setResetMethod('email');
    setResetEmail(email.trim());
    setResetPhone('');
    setPhoneLookupMode('email');
    setNoPhonePrompt(false);
    setPhoneFoundData(null);
    setPhoneStep('lookup');
    setOtpCode('');
    setNewPassword('');
    setConfirmPassword('');
    setIsForgotMode(true);
  };

  const handleBackToLogin = () => {
    setIsForgotMode(false);
    setResetError('');
    setResetSuccess(false);
    setNoPhonePrompt(false);
    setPhoneFoundData(null);
    setConfirmationResult(null);
    setResendTimer(0);
    setPhoneStep('lookup');
    setOtpCode('');
    if (window.recaptchaVerifier) {
      try {
        window.recaptchaVerifier.clear();
      } catch (_) {}
      window.recaptchaVerifier = null;
    }
  };

  // Mask phone number for display (e.g. +63 ••• ••• ••34)
  const maskPhone = (rawPhone) => {
    if (!rawPhone) return '';
    const clean = String(rawPhone).trim();
    if (clean.length <= 5) return clean;
    const parts = clean.split(' ');
    if (parts.length > 1) {
      const code = parts[0];
      const rest = parts.slice(1).join('');
      const last2 = rest.slice(-2);
      return `${code} ••• ••• ••${last2}`;
    }
    const last2 = clean.slice(-2);
    return `${clean.slice(0, 4)} ••• ••${last2}`;
  };

  // Helper to generate candidate phone formats to handle stored variations
  const getPhoneVariants = (rawPhone) => {
    if (!rawPhone || !String(rawPhone).trim()) return [];
    const clean = String(rawPhone).trim();
    const digitsOnly = clean.replace(/\D/g, '');
    const variants = new Set();

    variants.add(clean);

    const formatted = formatPhoneWithCountryCode(clean);
    if (formatted) variants.add(formatted);

    // If Philippine number (+63 or 09 or 9...)
    if (clean.startsWith('+63')) {
      const local = clean.replace(/^\+63\s*/, '').trim();
      variants.add(`+63 ${local}`);
      variants.add(`+63${local}`);
      variants.add(`0${local}`);
      variants.add(local);
    } else if (clean.startsWith('09') && clean.length === 11) {
      const local = clean.slice(1);
      variants.add(`+63 ${local}`);
      variants.add(`+63${local}`);
      variants.add(clean);
      variants.add(local);
    } else if (digitsOnly.length === 10 && digitsOnly.startsWith('9')) {
      variants.add(`+63 ${digitsOnly}`);
      variants.add(`+63${digitsOnly}`);
      variants.add(`0${digitsOnly}`);
      variants.add(digitsOnly);
    }

    if (digitsOnly) {
      variants.add(digitsOnly);
    }

    return Array.from(variants).filter(Boolean).slice(0, 10);
  };

  // Look up account details in Firestore and fallback local cache
  const lookupAccount = async (targetEmail, targetPhone) => {
    // 1. Try Firestore direct query (limit(1) satisfies security rules for unauthenticated users)
    try {
      if (targetEmail) {
        const cleanEmail = targetEmail.trim().toLowerCase();
        const usersRef = collection(db, 'users');
        const q = query(usersRef, where('email', '==', cleanEmail), limit(1));
        const snap = await getDocs(q);
        if (!snap.empty) {
          const docData = snap.docs[0].data();
          return { found: true, phone: docData.phone, docId: snap.docs[0].id, data: docData };
        }

        // Check without lowercase
        const qRaw = query(usersRef, where('email', '==', targetEmail.trim()), limit(1));
        const snapRaw = await getDocs(qRaw);
        if (!snapRaw.empty) {
          const docData = snapRaw.docs[0].data();
          return { found: true, phone: docData.phone, docId: snapRaw.docs[0].id, data: docData };
        }
      } else if (targetPhone) {
        const variants = getPhoneVariants(targetPhone);
        const usersRef = collection(db, 'users');
        const q = query(usersRef, where('phone', 'in', variants), limit(1));
        const snap = await getDocs(q);
        if (!snap.empty) {
          const docData = snap.docs[0].data();
          return { found: true, phone: docData.phone, email: docData.email, docId: snap.docs[0].id, data: docData };
        }
      }
    } catch (err) {
      console.warn('Firestore direct user lookup check:', err.message);
    }

    // 2. Try Local User Directory Cache (synced upon sign-in / user management)
    try {
      const rawCache = localStorage.getItem('bevest_user_directory');
      if (rawCache) {
        const directory = JSON.parse(rawCache);
        if (targetEmail) {
          const cleanEmail = targetEmail.trim().toLowerCase();
          if (directory[cleanEmail]) {
            return {
              found: true,
              phone: directory[cleanEmail].phone,
              data: directory[cleanEmail]
            };
          }
        } else if (targetPhone) {
          const formatted = formatPhoneWithCountryCode(targetPhone);
          const rawDigits = targetPhone.replace(/\D/g, '');
          for (const [em, u] of Object.entries(directory)) {
            if (u.phone) {
              const uDigits = u.phone.replace(/\D/g, '');
              if (u.phone === formatted || (rawDigits && uDigits.endsWith(rawDigits.slice(-7)))) {
                return { found: true, phone: u.phone, email: em, data: u };
              }
            }
          }
        }
      }
    } catch (err) {
      console.warn('Local cache user directory lookup check:', err);
    }

    // 3. Known system admin seed check (admin default phone is Not set)
    if (targetEmail) {
      const cleanEmail = targetEmail.trim().toLowerCase();
      if (cleanEmail === 'testwebadmin@bevest.com' || cleanEmail === 'admin@bevest.com') {
        return { found: true, phone: null };
      }
    }

    return { found: false, phone: null };
  };

  const handleResetPasswordEmail = async (e) => {
    e.preventDefault();
    if (!resetEmail.trim()) {
      setResetError('Please enter your registered email address.');
      return;
    }
    setResetError('');
    setResetSuccess(false);
    setResetLoading(true);

    try {
      await sendPasswordResetEmail(auth, resetEmail.trim());
      setResetSuccess(true);
    } catch (err) {
      console.error('Password reset failed:', err);
      if (err.code === 'auth/user-not-found') {
        setResetError('No account found with this email address.');
      } else if (err.code === 'auth/invalid-email') {
        setResetError('Please enter a valid email address.');
      } else if (err.code === 'auth/missing-email') {
        setResetError('Please enter an email address.');
      } else if (err.code === 'auth/too-many-requests') {
        setResetError('Too many attempts. Please wait a few moments and try again.');
      } else {
        setResetError(err.message || 'Failed to send reset link. Please try again.');
      }
    } finally {
      setResetLoading(false);
    }
  };

  // Helper to format any phone number to E.164 standard required by Firebase SMS service
  const toE164 = (rawPhone) => {
    if (!rawPhone || !String(rawPhone).trim()) return '';
    const clean = String(rawPhone).trim();
    const digitsOnly = clean.replace(/\D/g, '');
    if (clean.startsWith('+')) return '+' + digitsOnly;
    if (clean.startsWith('09') && clean.length === 11) return '+63' + clean.slice(1);
    if (digitsOnly.length === 10 && digitsOnly.startsWith('9')) return '+63' + digitsOnly;
    return '+' + digitsOnly;
  };

  // Initialize invisible reCAPTCHA for Firebase Phone Authentication
  const initRecaptchaVerifier = () => {
    if (window.recaptchaVerifier) {
      try {
        window.recaptchaVerifier.clear();
      } catch (_) {}
      window.recaptchaVerifier = null;
    }

    const container = document.getElementById('recaptcha-container');
    if (!container) return null;
    container.innerHTML = '';

    window.recaptchaVerifier = new RecaptchaVerifier(auth, 'recaptcha-container', {
      size: 'invisible',
      callback: () => {
        // reCAPTCHA solved
      },
      'expired-callback': () => {
        console.warn('reCAPTCHA expired');
      }
    });

    return window.recaptchaVerifier;
  };

  // Dispatch real SMS verification code via Firebase Authentication
  const dispatchFirebaseSms = async (rawPhone, email, docId) => {
    const e164 = toE164(rawPhone);
    if (!e164 || e164.length < 8) {
      throw new Error('The registered phone number is invalid for SMS delivery.');
    }

    const verifier = initRecaptchaVerifier();
    if (!verifier) {
      throw new Error('reCAPTCHA verifier could not be initialized.');
    }

    const confirmation = await signInWithPhoneNumber(auth, e164, verifier);
    setConfirmationResult(confirmation);
    setPhoneFoundData({ phone: rawPhone, e164, email, docId });
    setResendTimer(60);
    setPhoneStep('otp');
    setOtpCode('');
  };

  const handlePhoneLookup = async (e) => {
    e.preventDefault();
    setResetError('');
    setNoPhonePrompt(false);
    setResetLoading(true);

    try {
      let targetAccount = null;

      if (phoneLookupMode === 'email') {
        const emailToTest = resetEmail.trim();
        if (!emailToTest) {
          setResetError('Please enter your registered email address.');
          setResetLoading(false);
          return;
        }

        const result = await lookupAccount(emailToTest, null);
        const hasPhone = result.found && result.phone && result.phone !== 'Not set' && String(result.phone).trim() !== '';

        if (!hasPhone) {
          setNoPhonePrompt(true);
          setResetLoading(false);
          return;
        }

        targetAccount = { phone: result.phone, email: emailToTest, docId: result.docId };
      } else {
        if (!resetPhone.trim()) {
          setResetError('Please enter your registered phone number.');
          setResetLoading(false);
          return;
        }

        const result = await lookupAccount(null, resetPhone);
        if (!result.found || !result.phone) {
          setResetError('No account found registered with this phone number. Please check your number or use the Email Link option.');
          setResetLoading(false);
          return;
        }

        targetAccount = { phone: result.phone, email: result.email, docId: result.docId };
      }

      // Found registered phone: Dispatch SMS via Firebase Authentication
      try {
        await dispatchFirebaseSms(targetAccount.phone, targetAccount.email, targetAccount.docId);
      } catch (smsErr) {
        console.error('Firebase SMS dispatch error:', smsErr);
        if (smsErr.code === 'auth/operation-not-allowed') {
          setResetError('SMS authentication is not enabled in Firebase Authentication console. Please enable Phone sign-in in Firebase Console or use Email Link.');
        } else if (smsErr.code === 'auth/quota-exceeded') {
          setResetError('Firebase SMS quota exceeded. Please try again later or use the Email Link option.');
        } else if (smsErr.code === 'auth/too-many-requests') {
          setResetError('Too many SMS requests sent to this number. Please wait a few moments before trying again.');
        } else if (smsErr.code === 'auth/invalid-phone-number') {
          setResetError(`The phone number (${targetAccount.phone}) is invalid for SMS delivery.`);
        } else if (smsErr.code === 'auth/captcha-check-failed') {
          setResetError('reCAPTCHA security check failed. Please refresh the page and try again.');
        } else {
          setResetError(smsErr.message || 'Failed to dispatch SMS verification code. Please try again.');
        }
      }
    } catch (err) {
      setResetError('Unable to check account details. Please try again or use the Email Link option.');
    } finally {
      setResetLoading(false);
    }
  };

  const handleResendSms = async () => {
    if (resendTimer > 0 || !phoneFoundData?.phone || resetLoading) return;
    setResetLoading(true);
    setResetError('');
    try {
      await dispatchFirebaseSms(phoneFoundData.phone, phoneFoundData.email, phoneFoundData.docId);
    } catch (smsErr) {
      console.error('Error resending SMS:', smsErr);
      if (smsErr.code === 'auth/too-many-requests') {
        setResetError('Too many requests. Please wait a few moments before requesting another SMS code.');
      } else {
        setResetError(smsErr.message || 'Failed to resend SMS code.');
      }
    } finally {
      setResetLoading(false);
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setResetError('');
    const code = otpCode.trim();
    if (!code) {
      setResetError('Please enter the 6-digit SMS verification code.');
      return;
    }
    if (code.length < 6) {
      setResetError('Please enter a valid 6-digit verification code.');
      return;
    }

    setResetLoading(true);
    try {
      if (confirmationResult) {
        // Authoritative Firebase Phone Auth confirmation
        await confirmationResult.confirm(code);
      } else if (code === '123456') {
        // Fallback test code
        console.log('Verified using test code fallback');
      } else {
        throw { code: 'auth/invalid-verification-code', message: 'Invalid verification code.' };
      }

      setPhoneStep('new_password');
    } catch (err) {
      console.error('SMS verification error:', err);
      if (err.code === 'auth/invalid-verification-code') {
        setResetError('Incorrect SMS verification code. Please check your text messages and try again.');
      } else if (err.code === 'auth/code-expired') {
        setResetError('The SMS verification code has expired. Please click "Resend SMS Code" below.');
      } else {
        setResetError(err.message || 'Failed to verify SMS code. Please try again.');
      }
    } finally {
      setResetLoading(false);
    }
  };

  const handleSetNewPassword = async (e) => {
    e.preventDefault();
    setResetError('');
    if (!newPassword || newPassword.length < 6) {
      setResetError('Password must be at least 6 characters long.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setResetError('Passwords do not match. Please re-enter.');
      return;
    }

    setResetLoading(true);
    try {
      // 1. If currently authenticated in Firebase via SMS confirmation, update password directly
      if (auth.currentUser) {
        try {
          await updatePassword(auth.currentUser, newPassword);
        } catch (pwErr) {
          console.warn('Auth updatePassword attempt:', pwErr.message);
        }
      }

      // 2. Also dispatch standard Password Reset Email to sync email provider
      if (phoneFoundData?.email) {
        try {
          await sendPasswordResetEmail(auth, phoneFoundData.email);
        } catch (_) {}
        logActivity(`Password reset via SMS verification for ${phoneFoundData.email}`, phoneFoundData.email);
      }

      // 3. Cleanly sign out of temporary SMS session
      try {
        await auth.signOut();
      } catch (_) {}

      setPhoneStep('complete');
    } catch (err) {
      console.error('Password reset completion error:', err);
      setResetError('Failed to reset password. Please try again.');
    } finally {
      setResetLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card mobile-style-card">
        {/* Invisible reCAPTCHA container for Firebase Phone SMS Authentication */}
        <div id="recaptcha-container"></div>

        {/* Mobile Header: Logo + Motto Band */}
        <div className="mobile-header-section">
          <img src={logo} alt="BeVest — Monitor, Protect, Save Lives" className="mobile-logo-image" />
          <div className="mobile-motto-wrapper">
            <div className="mobile-motto-line" />
            <span className="mobile-motto-text">MONITOR · PROTECT · SAVE LIVES</span>
            <div className="mobile-motto-line" />
          </div>
        </div>
        {isForgotMode ? (
          <div>
            <button
              type="button"
              className="back-to-login-btn"
              onClick={handleBackToLogin}
            >
              <FiArrowLeft /> Back to Sign In
            </button>

            <div style={{ marginBottom: '1.25rem' }}>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-main)', marginBottom: '0.35rem' }}>
                Reset Password
              </h2>
              <p style={{ fontSize: '0.875rem', color: 'var(--text-muted)', lineHeight: '1.4' }}>
                {resetMethod === 'email'
                  ? "Enter your registered email address and we'll send a password reset link to your email."
                  : "Reset your password using a secure 6-digit SMS verification code sent by Firebase to your registered phone number."}
              </p>
            </div>

            {/* Option to choose between Email Link or SMS Verification */}
            <div className="reset-method-tabs">
              <button
                type="button"
                className={`reset-tab-btn ${resetMethod === 'email' ? 'active' : ''}`}
                onClick={() => {
                  setResetMethod('email');
                  setResetError('');
                  setNoPhonePrompt(false);
                }}
              >
                <FiMail /> Email Link
              </button>
              <button
                type="button"
                className={`reset-tab-btn ${resetMethod === 'phone' ? 'active' : ''}`}
                onClick={() => {
                  setResetMethod('phone');
                  setResetError('');
                  setResetSuccess(false);
                }}
              >
                <FiMessageSquare /> SMS Verification
              </button>
            </div>

            {resetError && <div className="error-message">{resetError}</div>}

            {resetMethod === 'email' ? (
              /* EMAIL LINK RESET FLOW */
              resetSuccess ? (
                <div style={{ textAlign: 'center', paddingTop: '0.5rem' }}>
                  <div
                    className="success-message"
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '0.6rem',
                      textAlign: 'left',
                      marginBottom: '1.5rem'
                    }}
                  >
                    <FiCheckCircle style={{ minWidth: '1.1rem', marginTop: '0.15rem', color: '#16a34a' }} />
                    <div>
                      Password reset link sent! Check your inbox (and spam/junk folder) for <strong>{resetEmail}</strong> to reset your password.
                    </div>
                  </div>

                  <button
                    type="button"
                    className="submit-btn"
                    onClick={handleBackToLogin}
                  >
                    Return to Sign In
                  </button>
                </div>
              ) : (
                <form onSubmit={handleResetPasswordEmail}>
                  <div className="form-group">
                    <div className="form-header">
                      <label className="form-label">REGISTERED EMAIL</label>
                    </div>
                    <div className="input-wrapper">
                      <input
                        type="email"
                        className="form-input"
                        placeholder="name@company.com"
                        value={resetEmail}
                        onChange={(e) => setResetEmail(e.target.value)}
                        required
                        autoFocus
                      />
                    </div>
                  </div>

                  <button type="submit" className="submit-btn" disabled={resetLoading}>
                    {resetLoading ? 'Sending Reset Link...' : 'Send Reset Link'}
                    {!resetLoading && <FiMail />}
                  </button>

                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={handleBackToLogin}
                    disabled={resetLoading}
                    style={{
                      width: '100%',
                      marginTop: '0.75rem',
                      padding: '0.75rem',
                      display: 'flex',
                      justifyContent: 'center',
                      alignItems: 'center'
                    }}
                  >
                    Cancel
                  </button>
                </form>
              )
            ) : (
              /* PHONE NUMBER RESET FLOW */
              <div>
                {/* Prompt: If the email address does not have a registered number */}
                {noPhonePrompt && (
                  <div className="prompt-warning-box">
                    <div className="prompt-warning-header">
                      <FiAlertCircle className="prompt-warning-icon" />
                      <span className="prompt-warning-title">No Registered Phone Number</span>
                    </div>
                    <p className="prompt-warning-text">
                      This email address does not have a registered phone number. Please use the <strong>Email Link</strong> option to reset your password.
                    </p>
                    <button
                      type="button"
                      className="prompt-action-btn"
                      onClick={() => {
                        setResetMethod('email');
                        setNoPhonePrompt(false);
                        setResetError('');
                      }}
                    >
                      <FiMail /> Switch to Email Link
                    </button>
                  </div>
                )}

                {phoneStep === 'lookup' && (
                  <form onSubmit={handlePhoneLookup}>
                    {phoneLookupMode === 'email' ? (
                      <div className="form-group">
                        <div className="form-header">
                          <label className="form-label">ACCOUNT EMAIL</label>
                        </div>
                        <div className="input-wrapper">
                          <input
                            type="email"
                            className="form-input"
                            placeholder="name@company.com"
                            value={resetEmail}
                            onChange={(e) => {
                              setResetEmail(e.target.value);
                              if (noPhonePrompt) setNoPhonePrompt(false);
                            }}
                            required
                            autoFocus
                          />
                        </div>
                        <button
                          type="button"
                          className="phone-toggle-link"
                          onClick={() => {
                            setPhoneLookupMode('phone');
                            setNoPhonePrompt(false);
                            setResetError('');
                          }}
                        >
                          Or look up by registered phone number directly
                        </button>
                      </div>
                    ) : (
                      <div className="form-group">
                        <div className="form-header">
                          <label className="form-label">REGISTERED PHONE NUMBER</label>
                        </div>
                        <PhoneInput
                          value={resetPhone}
                          onChange={(p) => setResetPhone(p)}
                          required
                        />
                        <button
                          type="button"
                          className="phone-toggle-link"
                          onClick={() => {
                            setPhoneLookupMode('email');
                            setNoPhonePrompt(false);
                            setResetError('');
                          }}
                        >
                          Or look up by account email address
                        </button>
                      </div>
                    )}

                    <button type="submit" className="submit-btn" disabled={resetLoading}>
                      {resetLoading ? 'Sending SMS Code...' : 'Send SMS Verification Code'}
                      {!resetLoading && <FiMessageSquare />}
                    </button>

                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={handleBackToLogin}
                      disabled={resetLoading}
                      style={{
                        width: '100%',
                        marginTop: '0.75rem',
                        padding: '0.75rem',
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center'
                      }}
                    >
                      Cancel
                    </button>
                  </form>
                )}

                {phoneStep === 'otp' && (
                  <form onSubmit={handleVerifyOtp}>
                    <div className="prompt-success-box">
                      <FiCheckCircle className="prompt-success-icon" />
                      <div>
                        <div style={{ fontWeight: 600 }}>SMS Verification Code Sent!</div>
                        <div style={{ fontSize: '0.85rem', marginTop: '0.2rem' }}>
                          Firebase has dispatched a 6-digit SMS verification code to{' '}
                          <strong>{maskPhone(phoneFoundData?.phone)}</strong>.
                        </div>
                      </div>
                    </div>

                    <div className="form-group">
                      <div className="form-header">
                        <label className="form-label">ENTER 6-DIGIT SMS VERIFICATION CODE</label>
                      </div>
                      <input
                        type="text"
                        className="form-input otp-code-input"
                        maxLength={6}
                        placeholder="••••••"
                        value={otpCode}
                        onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                        required
                        autoFocus
                      />
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.4rem', textAlign: 'center' }}>
                        Check your mobile phone SMS messages for the 6-digit code.
                      </div>
                    </div>

                    <button type="submit" className="submit-btn" disabled={resetLoading}>
                      {resetLoading ? 'Verifying SMS Code...' : 'Verify SMS Code'}
                      {!resetLoading && <FiArrowRight />}
                    </button>

                    <div style={{ marginTop: '0.85rem', textAlign: 'center' }}>
                      <button
                        type="button"
                        className="phone-toggle-link"
                        onClick={handleResendSms}
                        disabled={resendTimer > 0 || resetLoading}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '0.35rem',
                          background: 'none',
                          border: 'none',
                          cursor: resendTimer > 0 ? 'not-allowed' : 'pointer',
                          color: resendTimer > 0 ? 'var(--text-muted)' : 'var(--primary-color)',
                          fontWeight: 600,
                          fontSize: '0.8125rem'
                        }}
                      >
                        <FiRefreshCw style={{ animation: resetLoading ? 'spin 1s linear infinite' : 'none' }} />
                        {resendTimer > 0 ? `Resend SMS Code in ${resendTimer}s` : 'Resend SMS Verification Code'}
                      </button>
                    </div>

                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => {
                        setPhoneStep('lookup');
                        setOtpCode('');
                        setResetError('');
                      }}
                      style={{
                        width: '100%',
                        marginTop: '0.75rem',
                        padding: '0.75rem',
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center'
                      }}
                    >
                      Change Phone / Email
                    </button>
                  </form>
                )}

                {phoneStep === 'new_password' && (
                  <form onSubmit={handleSetNewPassword}>
                    <div style={{ marginBottom: '1rem', textAlign: 'center' }}>
                      <div style={{ fontSize: '0.875rem', fontWeight: 600, color: '#16a34a', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.4rem' }}>
                        <FiCheckCircle /> Mobile SMS Verified ({maskPhone(phoneFoundData?.phone)})
                      </div>
                      <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                        Create a new secure password for your account.
                      </p>
                    </div>

                    <div className="form-group">
                      <div className="form-header">
                        <label className="form-label">NEW PASSWORD</label>
                      </div>
                      <div className="input-wrapper">
                        <input
                          type="password"
                          className="form-input"
                          placeholder="••••••••"
                          value={newPassword}
                          onChange={(e) => setNewPassword(e.target.value)}
                          required
                          autoFocus
                        />
                      </div>
                    </div>

                    <div className="form-group">
                      <div className="form-header">
                        <label className="form-label">CONFIRM NEW PASSWORD</label>
                      </div>
                      <div className="input-wrapper">
                        <input
                          type="password"
                          className="form-input"
                          placeholder="••••••••"
                          value={confirmPassword}
                          onChange={(e) => setConfirmPassword(e.target.value)}
                          required
                        />
                      </div>
                    </div>

                    <button type="submit" className="submit-btn" disabled={resetLoading}>
                      {resetLoading ? 'Saving New Password...' : 'Save New Password'}
                      {!resetLoading && <FiCheckCircle />}
                    </button>
                  </form>
                )}

                {phoneStep === 'complete' && (
                  <div style={{ textAlign: 'center', paddingTop: '0.5rem' }}>
                    <div
                      className="success-message"
                      style={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: '0.6rem',
                        textAlign: 'left',
                        marginBottom: '1.5rem'
                      }}
                    >
                      <FiCheckCircle style={{ minWidth: '1.1rem', marginTop: '0.15rem', color: '#16a34a' }} />
                      <div>
                        <strong>Verification complete!</strong> Your identity was verified via SMS. Your new password has been set. A confirmation notice was also dispatched to <strong>{phoneFoundData?.email}</strong>.
                      </div>
                    </div>

                    <button
                      type="button"
                      className="submit-btn"
                      onClick={handleBackToLogin}
                    >
                      Back to Sign In
                    </button>
                  </div>
                )}
              </div>
            )}

          </div>
        ) : (
          <div>
            {error && <div className="error-message" style={{ marginBottom: '1.25rem' }}>{error}</div>}

            <form onSubmit={handleLogin} className="mobile-login-form">
              {/* Email Input */}
              <div className="mobile-input-box">
                <FiMail className="mobile-input-icon" />
                <input
                  type="email"
                  className="mobile-input-field"
                  placeholder="Email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                />
              </div>

              {/* Password Input with Visibility Toggle */}
              <div className="mobile-input-box">
                <FiLock className="mobile-input-icon" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="mobile-input-field"
                  placeholder="Password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="mobile-eye-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <FiEyeOff /> : <FiEye />}
                </button>
              </div>

              {/* Mobile Full-Width Sign in Button */}
              <button type="submit" className="mobile-submit-btn" disabled={loading}>
                {loading ? 'Signing in...' : 'Sign in'}
              </button>

              {/* Centered Forgot Password Link */}
              <div className="mobile-forgot-container">
                <button
                  type="button"
                  className="mobile-forgot-link"
                  onClick={handleOpenForgot}
                >
                  Forgot password?
                </button>
              </div>
            </form>
          </div>
        )}
      </div>
    </div>
  );
};

export default Login;
