import React, { useState } from 'react';
import { FiArrowRight, FiArrowLeft, FiMail, FiCheckCircle } from 'react-icons/fi';
import { signInWithEmailAndPassword, sendPasswordResetEmail } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from './firebase';
import logo from './assets/bevest_logo.png';
import { logActivity } from './activityLogger';

const Login = ({ onLogin }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Forgot password state
  const [isForgotMode, setIsForgotMode] = useState(false);
  const [resetEmail, setResetEmail] = useState('');
  const [resetLoading, setResetLoading] = useState(false);
  const [resetError, setResetError] = useState('');
  const [resetSuccess, setResetSuccess] = useState(false);

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
        if (role !== 'ADMIN' && role !== 'CONTRACTOR') {
          await auth.signOut();
          setError('Unauthorized Access: Admin or Contractor privileges required.');
        } else if (userData.active === false || userData.status === 'Disabled') {
          await auth.signOut();
          setError('This account has been disabled. Please contact your administrator.');
        } else {
          // Success: route to the role-specific web experience
          console.log(`${role} logged in successfully!`);
          logActivity(`${role === 'ADMIN' ? 'Admin' : 'Contractor'} signed in: ${user.email}`, user.email);
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
    setResetEmail(email.trim());
    setIsForgotMode(true);
  };

  const handleBackToLogin = () => {
    setIsForgotMode(false);
    setResetError('');
    setResetSuccess(false);
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
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
                Enter your registered Admin or Contractor email address and we'll send a password reset link to your email.
              </p>
            </div>

            {resetError && <div className="error-message">{resetError}</div>}

            {resetSuccess ? (
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
              <form onSubmit={handleResetPassword}>
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
            )}

            <div className="divider">
              PERSONNEL
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
