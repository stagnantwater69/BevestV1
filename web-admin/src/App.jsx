import React, { useState } from 'react';
import Login from './Login';
import Dashboard from './Dashboard';
import ContractorDashboard from './contractor/ContractorDashboard';

function App() {
  const [user, setUser] = useState(null);

  if (user) {
    if (user.role === 'CONTRACTOR') {
      return <ContractorDashboard user={user} onLogout={() => setUser(null)} />;
    }
    return <Dashboard onLogout={() => setUser(null)} />;
  }

  return (
    <Login onLogin={(userData) => setUser(userData)} />
  );
}

export default App;
