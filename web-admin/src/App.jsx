import React, { useState } from 'react';
import Login from './Login';
import Dashboard from './Dashboard';

function App() {
  const [user, setUser] = useState(null);

  if (user) {
    return <Dashboard onLogout={() => setUser(null)} />;
  }

  return (
    <Login onLogin={(userData) => setUser(userData)} />
  );
}

export default App;
