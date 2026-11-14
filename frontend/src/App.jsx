import { useState, useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';
import Footer from './components/Footer';
import SignIn from './pages/SignIn';
import SignUp from './pages/SignUp';
import Dashboard from './pages/Dashboard';
import Accounts from './pages/Accounts';
import Transactions from './pages/Transactions';
import Transfers from './pages/Transfers';
import Cards from './pages/Cards';
import Settings from './pages/Settings';
import { initialAccounts, initialTransactions, initialPayments } from './data/mockData';
import './index.css';
import './styles/components.css';
import './styles/dashboard.css';
import './styles/auth.css';

function AppContent() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [accounts, setAccounts] = useState(initialAccounts);
  const [transactions, setTransactions] = useState(initialTransactions);
  const [payments, setPayments] = useState(initialPayments);
  const [frozen, setFrozen] = useState(false);
  const [notice, setNotice] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);

  function notify(message) {
    setNotice(message);
    window.setTimeout(() => setNotice(''), 4500);
  }
  function signIn(email, password) {

    const handleSubmit = async (e) => {
      try {
        const response = await fetch("http://localhost:8000/api/auth/login", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            username: email,
            password: password,
            
          }),
        });

        const data = await response.json();
        if (!response.ok) {
          // FastAPI returns 422 Unprocessable Entity if structural validation fails
          throw new Error(data.detail ? JSON.stringify(data.detail) : "Validation failed");
        }

        setUser({ name: 'Sachi', email });
        navigate('/dashboard');

        // setResponseMessage(data.message);
      } catch (err) {
        // for now, log in even when fail
        setUser({ name: 'Sachi', email });
        navigate('/dashboard');
      }
    };

    handleSubmit();

    
    
  }
  function signOut() {
    setUser(null);
    navigate('/signin');
  }
  function transfer(fromId, toId, amount) {
    const from = accounts.find((a) => a.id === fromId);
    const to = accounts.find((a) => a.id === toId);
    if (
      !from ||
      !to ||
      fromId === toId ||
      !Number.isFinite(amount) ||
      amount <= 0
    )
      return 'Enter a valid transfer.';
    if (amount > from.available) return 'Insufficient available funds.';
    setAccounts((prev) =>
      prev.map((a) => {
        if (a.id === fromId)
          return {
            ...a,
            balance: a.balance - amount,
            available: a.available - amount,
          };
        if (a.id === toId)
          return {
            ...a,
            balance: a.balance + amount,
            available: a.available + amount,
          };
        return a;
      }),
    );
    const date = new Date().toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
    setTransactions((prev) => [
      {
        id: Date.now(),
        name: `Transfer to ${to.type}`,
        category: 'Transfer',
        date,
        amount: -amount,
        account: fromId,
        icon: 'ArrowLeftRight',
      },
      {
        id: Date.now() + 1,
        name: `Transfer from ${from.type}`,
        category: 'Transfer',
        date,
        amount,
        account: toId,
        icon: 'ArrowLeftRight',
      },
      ...prev,
    ]);
    notify('Demo transfer completed. No real money was moved.');
    return '';
  }
  function schedulePayment(name, amount) {
    setPayments((prev) => [
      ...prev,
      { id: Date.now(), name, amount, date: 'Scheduled' },
    ]);
    notify('Demo payment added to your upcoming payments.');
  }
  const shared = {
    user,
    accounts,
    transactions,
    payments,
    frozen,
    setFrozen,
    notify,
    transfer,
    schedulePayment,
  };
  return (
    <>
      {user && (
        <Navbar
          user={user}
          onMenu={() => setMenuOpen((v) => !v)}
          onSignOut={signOut}
        />
      )}
      {notice && (
        <div className="toast" role="status">
          {notice}
          <button onClick={() => setNotice('')} aria-label="Dismiss">
            ×
          </button>
        </div>
      )}
      <Routes>
        <Route
          path="/signin"
          element={
            user ? (
              <Navigate to="/dashboard" replace />
            ) : (
              <SignIn onSignIn={signIn} />
            )
          }
        />
        <Route
          path="/signup"
          element={
            user ? (
              <Navigate to="/dashboard" replace />
            ) : (
              <SignUp onSignIn={signIn} />
            )
          }
        />
        <Route
          path="/*"
          element={
            user ? (
              <div className="app-shell">
                <Sidebar open={menuOpen} close={() => setMenuOpen(false)} />
                <div className="app-content">
                  <main className="main-content">
                    <Routes>
                      <Route
                        path="/dashboard"
                        element={<Dashboard {...shared} />}
                      />
                      <Route
                        path="/accounts"
                        element={<Accounts {...shared} />}
                      />
                      <Route
                        path="/transactions"
                        element={<Transactions {...shared} />}
                      />
                      <Route
                        path="/transfers"
                        element={<Transfers {...shared} />}
                      />
                      <Route path="/cards" element={<Cards {...shared} />} />
                      <Route
                        path="/settings"
                        element={<Settings {...shared} />}
                      />
                      <Route
                        path="*"
                        element={<Navigate to="/dashboard" replace />}
                      />
                    </Routes>
                  </main>
                  <Footer />
                </div>
              </div>
            ) : (
              <Navigate to="/signin" replace />
            )
          }
        />
      </Routes>
    </>
  );
}
export default function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  );
}
