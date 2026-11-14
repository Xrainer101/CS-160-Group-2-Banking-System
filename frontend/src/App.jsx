
import { useEffect, useRef, useState } from "react";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useNavigate,
} from "react-router-dom";

import Navbar from "./components/Navbar";
import Sidebar from "./components/Sidebar";
import Footer from "./components/Footer";

import SignIn from "./pages/SignIn";
import SignUp from "./pages/SignUp";
import Dashboard from "./pages/Dashboard";
import Accounts from "./pages/Accounts";
import Transactions from "./pages/Transactions";
import Transfers from "./pages/Transfers";
import Cards from "./pages/Cards";
import Settings from "./pages/Settings";
import AdminPanel from "./pages/AdminPanel";

import {
  initialPayments,
} from "./data/mockData";

import {
  clearSession,
  getSession,
  saveSession,
} from "./auth/authService";

import {
  getAccounts,
  getCurrentUser,
} from "./api/bankingApi";

import "./index.css";
import "./styles/components.css";
import "./styles/dashboard.css";
import "./styles/auth.css";

// Basic session display information.
// This does NOT determine administrator privileges.
function userFromSession(session) {
  const token = session?.id_token;
  if (!token) return null;

  try {
    const encodedPayload = token.split(".")[1];
    const payload = JSON.parse(
      atob(
        encodedPayload
          .replace(/-/g, "+")
          .replace(/_/g, "/")
      )
    );

    return {
      name:
        payload.name ||
        payload.nickname ||
        payload.email ||
        "Banking user",
      email: payload.email || "",
      role: "customer",
    };
  } catch {
    return null;
  }
}

function userFromProfile(profile) {
  const bankingProfile =
    profile.banking_user || profile;

  return {
    userId: bankingProfile.user_id,
    name:
      bankingProfile.full_name ||
      bankingProfile.name ||
      bankingProfile.username ||
      "Banking user",
    username: bankingProfile.username || "",
    email: bankingProfile.email || "",
    role: bankingProfile.role || "customer",
    isNewUser:
      bankingProfile.is_new_user === true,
  };
}

function AppContent() {
  const navigate = useNavigate();

  const [session, setSession] = useState(
    () => getSession()
  );

  const [user, setUser] = useState(
    () => userFromSession(session)
  );

  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] =
    useState([]);
  const [payments, setPayments] =
    useState(initialPayments);

  const [frozen, setFrozen] = useState(false);
  const [notice, setNotice] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
  const [bankingLoading, setBankingLoading] =
    useState(false);
  const [bankingError, setBankingError] =
    useState("");

  const skipNextSessionLoadRef = useRef(false);

  const isAdmin = user?.role === "admin";

  useEffect(() => {
    let cancelled = false;

    if (!session) {
      setAccounts([]);
      setTransactions([]);
      setBankingError("");
      setBankingLoading(false);
      return;
    }

    if (skipNextSessionLoadRef.current) {
      skipNextSessionLoadRef.current = false;
      return;
    }

    async function loadBankingData() {
      setBankingLoading(true);
      setBankingError("");

      try {
        const profile = await getCurrentUser();
        const accountData = await getAccounts();

        if (cancelled) return;

        setUser(userFromProfile(profile));
        setAccounts(accountData.accounts || []);
        setTransactions(
          accountData.transactions || []
        );
      } catch (error) {
        if (!cancelled) {
          setBankingError(
            error.message ||
              "Unable to load banking data."
          );
        }
      } finally {
        if (!cancelled) {
          setBankingLoading(false);
        }
      }
    }

    loadBankingData();

    return () => {
      cancelled = true;
    };
  }, [session]);

  function notify(message) {
    setNotice(message);
    window.setTimeout(
      () => setNotice(""),
      4500
    );
  }

  async function handleAuthenticated(result) {
    if (!result?.session) return;

    saveSession(result.session);

    setBankingError("");
    setBankingLoading(true);

    try {
      const profile = await getCurrentUser();
      const accountData = await getAccounts();

      setUser(userFromProfile(profile));
      setAccounts(accountData.accounts || []);
      setTransactions(
        accountData.transactions || []
      );

      skipNextSessionLoadRef.current = true;
      setSession(result.session);

      navigate("/dashboard");
    } catch (error) {
      clearSession();
      setSession(null);
      setUser(null);
      setAccounts([]);
      setTransactions([]);
      throw error;
    } finally {
      setBankingLoading(false);
    }
  }

  function signOut() {
    clearSession();
    setSession(null);
    setUser(null);
    setMenuOpen(false);
    setAccounts([]);
    setTransactions([]);
    navigate("/signin", { replace: true });
  }

  // Existing demo transfer functionality.
  // This does not modify actual bank database balances.
  function transfer(fromId, toId, amount) {
    const from = accounts.find(
      (a) => String(a.id) === String(fromId)
    );

    const to = accounts.find(
      (a) => String(a.id) === String(toId)
    );

    if (
      !from ||
      !to ||
      String(fromId) === String(toId) ||
      !Number.isFinite(amount) ||
      amount <= 0
    ) {
      return "Enter a valid transfer.";
    }

    if (amount > from.available) {
      return "Insufficient available funds.";
    }

    setAccounts((prev) =>
      prev.map((a) => {
        if (String(a.id) === String(fromId)) {
          return {
            ...a,
            balance: a.balance - amount,
            available: a.available - amount,
          };
        }

        if (String(a.id) === String(toId)) {
          return {
            ...a,
            balance: a.balance + amount,
            available: a.available + amount,
          };
        }

        return a;
      })
    );

    const date = new Date().toLocaleDateString(
      "en-US",
      {
        month: "short",
        day: "numeric",
        year: "numeric",
      }
    );

    setTransactions((prev) => [
      {
        id: Date.now(),
        name: `Transfer to ${to.type}`,
        category: "Transfer",
        date,
        amount: -amount,
        account: from.id,
        icon: "ArrowLeftRight",
      },
      {
        id: Date.now() + 1,
        name: `Transfer from ${from.type}`,
        category: "Transfer",
        date,
        amount,
        account: to.id,
        icon: "ArrowLeftRight",
      },
      ...prev,
    ]);

    notify(
      "Demo transfer completed. No real money was moved."
    );

    return "";
  }

  function schedulePayment(name, amount) {
    setPayments((prev) => [
      ...prev,
      {
        id: Date.now(),
        name,
        amount,
        date: "Scheduled",
      },
    ]);

    notify(
      "Demo payment added to upcoming payments."
    );
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
          onMenu={() =>
            setMenuOpen((prev) => !prev)
          }
          onSignOut={signOut}
        />
      )}

      {notice && (
        <div className="toast" role="status">
          {notice}
          <button
            type="button"
            onClick={() => setNotice("")}
            aria-label="Dismiss"
          >
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
          <button
            type="button"
            onClick={() =>
              setBankingError("")
            }
            aria-label="Dismiss"
          >
            ×
          </button>
        </div>
      )}

      <Routes>
        <Route
          path="/signin"
          element={
            user ? (
              <Navigate
                to="/dashboard"
                replace
              />
            ) : (
              <SignIn
                onAuthenticated={
                  handleAuthenticated
                }
              />
            )
          }
        />

        <Route
          path="/signup"
          element={
            user ? (
              <Navigate
                to="/dashboard"
                replace
              />
            ) : (
              <SignUp
                onAuthenticated={
                  handleAuthenticated
                }
              />
            )
          }
        />

        <Route
          path="/*"
          element={
            user ? (
              <div className="app-shell">
                <Sidebar
                  open={menuOpen}
                  close={() =>
                    setMenuOpen(false)
                  }
                  isAdmin={isAdmin}
                />

                <div className="app-content">
                  <main className="main-content">
                    <Routes>
                      <Route
                        path="/dashboard"
                        element={
                          <Dashboard {...shared} />
                        }
                      />

                      <Route
                        path="/accounts"
                        element={
                          <Accounts {...shared} />
                        }
                      />

                      <Route
                        path="/transactions"
                        element={
                          <Transactions {...shared} />
                        }
                      />

                      <Route
                        path="/transfers"
                        element={
                          <Transfers {...shared} />
                        }
                      />

                      <Route
                        path="/cards"
                        element={
                          <Cards {...shared} />
                        }
                      />

                      <Route
                        path="/settings"
                        element={
                          <Settings {...shared} />
                        }
                      />

                      <Route
                        path="/admin-preview"
                        element={
                          <AdminPanel
                            preview={true}
                            user={user}
                          />
                        }
                      />

                      <Route
                        path="/admin"
                        element={
                          isAdmin ? (
                            <AdminPanel
                              preview={false}
                              user={user}
                            />
                          ) : (
                            <Navigate
                              to="/dashboard"
                              replace
                            />
                          )
                        }
                      />

                      <Route
                        path="*"
                        element={
                          <Navigate
                            to="/dashboard"
                            replace
                          />
                        }
                      />
                    </Routes>
                  </main>

                  <Footer />
                </div>
              </div>
            ) : (
              <Navigate
                to="/signin"
                replace
              />
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
