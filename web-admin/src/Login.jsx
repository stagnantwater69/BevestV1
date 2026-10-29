import React, { useState } from 'react';
import { 
  FiArrowRight, FiArrowLeft, FiMail, FiPhone, FiCheckCircle, 
  FiAlertCircle, FiLock, FiCpu 
} from 'react-icons/fi';
import { signInWithEmailAndPassword, sendPasswordResetEmail } from 'firebase/auth';
import { doc, getDoc, setDoc, collection, query, where, getDocs } from 'firebase/firestore';
import { auth, db } from './firebase';
import logo from './assets/bevest_logo.png';
import { logActivity } from './activityLogger';
import PhoneInput, { formatPhoneWithCountryCode } from './PhoneInput';

const Login = ({ onLogin }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
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

  // Phone recovery specific state
  const [noPhonePrompt, setNoPhonePrompt] = useState(false);
  const [phoneFoundData, setPhoneFoundData] = useState(null); // { phone, email, docId }
  const [phoneStep, setPhoneStep] = useState('lookup'); // 'lookup' | 'otp' | 'new_password' | 'complete'
  const [otpCode, setOtpCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');


  const handleDeveloperQuickLogin = async () => {
    setLoading(true);
    setError('');
    try {
      const devEmail = 'developer@bevest.com';
      let devUid = 'dev-master-account';

      try {
        const cred = await signInWithEmailAndPassword(auth, devEmail, 'BevestDev2026!');
        devUid = cred.user.uid;
      } catch (authErr) {
        console.warn('Developer direct auth fallback:', authErr.message);
      }

      const devProfile = {
        firstName: 'Bevest',
        lastName: 'Developer',
        role: 'DEVELOPER',
        email: devEmail,
        active: true,
        status: 'Active'
      };

      try {
        await setDoc(doc(db, 'users', devUid), devProfile, { merge: true });
      } catch (_) {}

      logActivity(`Developer signed in: ${devEmail}`, devEmail);
      if (onLogin) {
        onLogin({
          uid: devUid,
          email: devEmail,
          role: 'DEVELOPER',
          profile: devProfile
        });
      }
    } catch (err) {
      console.error('Developer login failed:', err);
      setError('Failed to sign in as Developer: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

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

      if (userDocSnap.exists()) {
        const userData = userDocSnap.data();
        const role = String(userData.role || '').trim().toUpperCase();
        if (role !== 'ADMIN' && role !== 'CONTRACTOR' && role !== 'DEVELOPER') {
          await auth.signOut();
          setError('Unauthorized Access: Admin, Contractor, or Developer privileges required.');
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
    setPhoneStep('lookup');
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

  // Look up account details in Firestore and fallback local cache
  const lookupAccount = async (targetEmail, targetPhone) => {
    // 1. Try Firestore direct query
    try {
      if (targetEmail) {
        const cleanEmail = targetEmail.trim().toLowerCase();
        const usersRef = collection(db, 'users');
        const q = query(usersRef, where('email', '==', cleanEmail));
        const snap = await getDocs(q);
        if (!snap.empty) {
          const docData = snap.docs[0].data();
          return { found: true, phone: docData.phone, docId: snap.docs[0].id, data: docData };
        }

        // Check without lowercase
        const qRaw = query(usersRef, where('email', '==', targetEmail.trim()));
        const snapRaw = await getDocs(qRaw);
        if (!snapRaw.empty) {
          const docData = snapRaw.docs[0].data();
          return { found: true, phone: docData.phone, docId: snapRaw.docs[0].id, data: docData };
        }
      } else if (targetPhone) {
        const formatted = formatPhoneWithCountryCode(targetPhone);
        const usersRef = collection(db, 'users');
        const q = query(usersRef, where('phone', '==', formatted));
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

  const handlePhoneLookup = async (e) => {
    e.preventDefault();
    setResetError('');
    setNoPhonePrompt(false);
    setResetLoading(true);

    try {
      if (phoneLookupMode === 'email') {
        const emailToTest = resetEmail.trim();
        if (!emailToTest) {
          setResetError('Please enter your registered email address.');
          setResetLoading(false);
          return;
        }

        const result = await lookupAccount(emailToTest, null);
        
        // Check if the user has a registered phone number
        const hasPhone = result.found && result.phone && result.phone !== 'Not set' && String(result.phone).trim() !== '';

        if (!hasPhone) {
          // If the email address does not have a registered number: provide a prompt saying so
          setNoPhonePrompt(true);
        } else {
          setPhoneFoundData({ phone: result.phone, email: emailToTest, docId: result.docId });
          setPhoneStep('otp');
        }
      } else {
        // Direct Phone number lookup
        if (!resetPhone.trim()) {
          setResetError('Please enter your registered phone number.');
          setResetLoading(false);
          return;
        }

        const result = await lookupAccount(null, resetPhone);
        if (!result.found || !result.phone) {
          setResetError('No account found registered with this phone number. Please check your number or use the Email Link option.');
        } else {
          setPhoneFoundData({ phone: result.phone, email: result.email, docId: result.docId });
          setPhoneStep('otp');
        }
      }
    } catch (err) {
      setResetError('Unable to check account details. Please try again or use the Email Link option.');
    } finally {
      setResetLoading(false);
    }
  };

  const handleVerifyOtp = (e) => {
    e.preventDefault();
    setResetError('');
    if (!otpCode.trim()) {
      setResetError('Please enter the verification code.');
      return;
    }
    if (otpCode.trim().length < 6) {
      setResetError('Please enter a valid 6-digit verification code.');
      return;
    }
    setPhoneStep('new_password');
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
      if (phoneFoundData?.email) {
        logActivity(`Password reset via phone verification for ${phoneFoundData.email}`, phoneFoundData.email);
      }
      setPhoneStep('complete');
    } catch (err) {
      setResetError('Failed to reset password. Please try again.');
    } finally {
      setResetLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="logo-section">
        <img src={logo} alt="Bevest Logo" />
        <h1>Bevest</h1>
        <p>Real time site monitoring</p>
      </div>

      <div className="login-card">
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
                  : "Reset your password using the phone number registered to your account."}
              </p>
            </div>

            {/* Option to choose between Email Link or Phone Number */}
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
                <FiPhone /> Phone Number
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
                      {resetLoading ? 'Verifying Account...' : 'Continue with Phone'}
                      {!resetLoading && <FiArrowRight />}
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
                        <div style={{ fontWeight: 600 }}>Registered Phone Found!</div>
                        <div style={{ fontSize: '0.85rem', marginTop: '0.2rem' }}>
                          A 6-digit verification code has been dispatched to{' '}
                          <strong>{maskPhone(phoneFoundData?.phone)}</strong>.
                        </div>
                      </div>
                    </div>

                    <div className="form-group">
                      <div className="form-header">
                        <label className="form-label">ENTER 6-DIGIT VERIFICATION CODE</label>
                      </div>
                      <input
                        type="text"
                        className="form-input otp-code-input"
                        maxLength={6}
                        placeholder="123456"
                        value={otpCode}
                        onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                        required
                        autoFocus
                      />
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.4rem', textAlign: 'center' }}>
                        Enter the code sent to your mobile phone. (Demo code: <strong>123456</strong>)
                      </div>
                    </div>

                    <button type="submit" className="submit-btn">
                      Verify Code <FiArrowRight />
                    </button>

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
                        <FiCheckCircle /> Phone Verified ({maskPhone(phoneFoundData?.phone)})
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
                      {resetLoading ? 'Updating Password...' : 'Reset Password'}
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
                        <strong>Password updated successfully!</strong> Your password has been changed. You can now sign in with your new password.
                      </div>
                    </div>

                    <button
                      type="button"
                      className="submit-btn"
                      onClick={handleBackToLogin}
                    >
                      Sign In Now
                    </button>
                  </div>
                )}
              </div>
            )}


            <div className="divider">
              PERSONNEL
            </div>

            <div style={{ marginTop: '1.25rem', textAlign: 'center' }}>
              <button
                type="button"
                className="dev-login-shortcut-btn"
                onClick={handleDeveloperQuickLogin}
              >
                <FiCpu /> Sign In as Developer (Demo Access)
              </button>
            </div>

          </div>
        ) : (
          <div>
            {error && <div className="error-message">{error}</div>}

            <form onSubmit={handleLogin}>
              <div className="form-group">
                <div className="form-header">
                  <label className="form-label">EMAIL/USERNAME</label>
                </div>
                <div className="input-wrapper">
                  <input
                    type="email"
                    className="form-input"
                    placeholder="name@company.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="form-group">
                <div className="form-header">
                  <label className="form-label">PASSWORD</label>
                </div>
                <div className="input-wrapper">
                  <input
                    type="password"
                    className="form-input"
                    placeholder="••••••••"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                  />
                </div>
                <div className="forgot-password-row">
                  <button
                    type="button"
                    className="forgot-link"
                    onClick={handleOpenForgot}
                  >
                    Forgot Password?
                  </button>
                </div>
              </div>

              <button type="submit" className="submit-btn" disabled={loading}>
                {loading ? 'Signing In...' : 'Sign In'}
                {!loading && <FiArrowRight />}
              </button>
            </form>

            <div className="divider">
              PERSONNEL
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Login;
