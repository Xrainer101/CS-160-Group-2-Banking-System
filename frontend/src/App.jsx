import { useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useNavigate} from 'react-router-dom';
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
import { initialPayments } from './data/mockData';
import './index.css';
import './styles/components.css';
import './styles/dashboard.css';
import './styles/auth.css';
import { clearSession, getSession, saveSession } from './auth/authService';
import { getAccounts, getCurrentUser } from './api/bankingApi';

// Reads the auth0 session and returns the user's name and email
function userFromSession(session) {
  const token = session?.id_token;
  if (!token) return null;
  try {
    const encodedPayload = token.split('.')[1];
    const payload = JSON.parse(
      atob(encodedPayload.replace(/-/g, '+').replace(/_/g, '/')),
    );
    return {
      name: payload.name || payload.nickname || payload.email,
      email: payload.email,
    };
  } catch {
    return null;
  }
}

function AppContent() {
  const navigate = useNavigate();
  const [session, setSession] = useState(() => getSession());
  const [user, setUser] = useState(() => userFromSession(session));
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [payments, setPayments] = useState(initialPayments);
  const [frozen, setFrozen] = useState(false);
  const [notice, setNotice] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [bankingLoading, setBankingLoading] = useState(false);
  const [bankingError, setBankingError] = useState('');

  // Load the user's banking data when they log in
  useEffect(() => {
    let cancelled = false;
    if (!session) {
      setAccounts([]);
      setTransactions([]);
      setBankingError('');
      setBankingLoading(false);
      return () => {
        cancelled = true;
      };
    }
    // Get user's profile and account information from the API
    async function loadBankingData() {
      setBankingLoading(true);
      setBankingError('');
      try {
        const profile = await getCurrentUser();
        const accountData = await getAccounts();
        if (cancelled) return;
        setUser({
          name: profile.name || 'Banking user',
          email: profile.email || '',
        });
        setAccounts(accountData.accounts || []);
        setTransactions(accountData.transactions || []);
      } catch (error) {
        if (!cancelled) {
          setBankingError(error.message || 'Unable to load banking data.');
        }
      } finally {
        if (!cancelled) setBankingLoading(false);
      }
    }

    loadBankingData();
    return () => {
      cancelled = true;
    };
  }, [session]);

  function notify(message) {
    setNotice(message);
    window.setTimeout(() => setNotice(''), 4500);
  }
  function handleAuthenticated(result) {
    if (!result?.session) return;
    saveSession(result.session);
    setSession(result.session);
    setUser(userFromSession(result.session));
    navigate('/dashboard');
  }
  function signOut() {
    clearSession();
    setSession(null);
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
      {bankingLoading && (
        <div className="toast" role="status">
          Loading your banking data...
        </div>
      )}
      {bankingError && (
        <div className="toast" role="alert">
          {bankingError}
          <button onClick={() => setBankingError('')} aria-label="Dismiss">
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
              <SignIn onAuthenticated={handleAuthenticated} />
            )
          }
        />
        <Route
          path="/signup"
          element={
            user ? (
              <Navigate to="/dashboard" replace />
            ) : (
              <SignUp onAuthenticated={handleAuthenticated} />
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
                        element={<Accounts />}
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
