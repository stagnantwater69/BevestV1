import React, { useState } from 'react';
import { FiArrowRight } from 'react-icons/fi';
import { signInWithEmailAndPassword } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from './firebase';
import logo from './assets/bevest_logo.png';
import { logActivity } from './activityLogger';

const Login = ({ onLogin }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

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

  return (
    <div className="login-container">
      <div className="logo-section">
        <img src={logo} alt="Bevest Logo" />
        <h1>Bevest</h1>
        <p>Real time site monitoring</p>
      </div>

      <div className="login-card">
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
              <a href="#" className="forgot-link">Forgot?</a>
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
    </div>
  );
};

export default Login;
