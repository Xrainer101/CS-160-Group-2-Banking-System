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
import AccountDetail from "./pages/AccountDetail";
import Transactions from "./pages/Transactions";
import Transfers from "./pages/Transfers";
import Payments from "./pages/Payments";
import AccountMovement from "./pages/AccountMovement";
import Cards from "./pages/Cards";
import Settings from "./pages/Settings";
import AdminPanel from "./pages/AdminPanel";

// New pages
import ATMFinder from "./pages/ATMFinder";
import CheckDeposit from "./pages/CheckDeposit";

import { initialPayments } from "./data/mockData";

import {
  clearSession,
  getSession,
  saveSession,
} from "./auth/authService";

import {
  getAccounts,
  getCurrentUser,
  createAccount,
  closeAccount,
  moveAccountMoney,
} from "./api/bankingApi";

import "./index.css";
import "./styles/components.css";
import "./styles/dashboard.css";
import "./styles/auth.css";

function userFromSession(session) {
  const token = session?.id_token;

  if (!token) return null;

  try {
    const encoded = token.split(".")[1];

    const payload = JSON.parse(
      atob(
        encoded
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
  const p = profile.banking_user || profile;

  return {
    userId: p.user_id,
    name:
      p.full_name ||
      p.name ||
      p.username ||
      "Banking user",
    username: p.username || "",
    email: p.email || "",
    role: p.role || "customer",
    isNewUser: p.is_new_user === true,
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
  const [transactions, setTransactions] = useState([]);
  const [payments, setPayments] = useState(initialPayments);

  const [frozen, setFrozen] = useState(false);
  const [notice, setNotice] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);

  const [bankingLoading, setBankingLoading] =
    useState(() => Boolean(session));

  const [bankingError, setBankingError] =
    useState("");

  const [openingAccount, setOpeningAccount] =
    useState(false);

  const [refreshingAccounts, setRefreshingAccounts] =
    useState(false);

  const [accountRefreshError, setAccountRefreshError] =
    useState("");

  const openingAccountRef = useRef(false);
  const skipNextSessionLoadRef = useRef(false);
  const sessionVersionRef = useRef(0);

  const isAdmin = user?.role === "admin";

  function notify(message) {
    setNotice(message);

    window.setTimeout(() => {
      setNotice("");
    }, 4500);
  }

  async function refreshAccounts(
    version = sessionVersionRef.current
  ) {
    if (version !== sessionVersionRef.current) return;

    setRefreshingAccounts(true);

    try {
      const data = await getAccounts();

      if (version !== sessionVersionRef.current) return;

      setAccounts(data.accounts || []);
      setTransactions(data.transactions || []);
      setAccountRefreshError("");
    } catch {
      if (version === sessionVersionRef.current) {
        setAccountRefreshError(
          "Account data could not refresh. Please retry."
        );
      }
    } finally {
      if (version === sessionVersionRef.current) {
        setRefreshingAccounts(false);
      }
    }
  }

  async function openAccount(accountType) {
    if (openingAccountRef.current) return false;

    const version = sessionVersionRef.current;

    openingAccountRef.current = true;
    setOpeningAccount(true);

    try {
      await createAccount(accountType);

      if (version !== sessionVersionRef.current) {
        return false;
      }

      notify(
        "Your new account was created with a $0.00 balance."
      );

      await refreshAccounts(version);

      return version === sessionVersionRef.current;
    } catch (error) {
      if (version === sessionVersionRef.current) {
        throw error;
      }

      return false;
    } finally {
      openingAccountRef.current = false;
      setOpeningAccount(false);
    }
  }

  async function handleCloseAccount(accountId) {
    const version = sessionVersionRef.current;

    try {
      await closeAccount(accountId);

      if (version !== sessionVersionRef.current) {
        return false;
      }

      setAccounts((previous) =>
        previous.filter(
          (account) =>
            String(account.id) !== String(accountId)
        )
      );

      setTransactions((previous) =>
        previous.filter(
          (transaction) =>
            String(transaction.account) !== String(accountId)
        )
      );

      navigate("/accounts", { replace: true });

      notify("Account closed successfully.");

      return true;
    } catch (error) {
      if (version === sessionVersionRef.current) {
        throw error;
      }

      return false;
    }
  }

  async function refreshMovement(version) {
    const data = await getAccounts();

    if (version !== sessionVersionRef.current) {
      return null;
    }

    setAccounts(data.accounts || []);
    setTransactions(data.transactions || []);

    return data.accounts || [];
  }

  async function handleMovement(accountId, kind, amount) {
    const version = sessionVersionRef.current;

    try {
      await moveAccountMoney(accountId, kind, amount);
    } catch (error) {
      if (version === sessionVersionRef.current) {
        throw error;
      }

      return null;
    }

    if (version !== sessionVersionRef.current) {
      return null;
    }

    const refresh = async () => {
      const updated = await refreshMovement(version);

      return (
        updated?.find(
          (account) =>
            String(account.id) === String(accountId)
        ) || null
      );
    };

    try {
      return {
        account: await refresh(),
        refresh,
      };
    } catch {
      return {
        account: null,
        refresh,
      };
    }
  }

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
      setAccounts([]);
      setTransactions([]);
      setBankingError("");
      setBankingLoading(true);

      try {
        const profile = await getCurrentUser();
        const accountData = await getAccounts();

        if (cancelled) return;

        setUser(userFromProfile(profile));
        setAccounts(accountData.accounts || []);
        setTransactions(accountData.transactions || []);
      } catch (error) {
        if (!cancelled) {
          setAccounts([]);
          setTransactions([]);

          setBankingError(
            error.message || "Unable to load banking data."
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

  async function handleAuthenticated(result) {
    if (!result?.session) return;

    sessionVersionRef.current += 1;
    setAccountRefreshError("");
    setRefreshingAccounts(false);

    saveSession(result.session);

    setAccounts([]);
    setTransactions([]);
    setBankingError("");
    setBankingLoading(true);

    try {
      const profile = await getCurrentUser();
      const accountData = await getAccounts();

      setUser(userFromProfile(profile));
      setAccounts(accountData.accounts || []);
      setTransactions(accountData.transactions || []);

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
    sessionVersionRef.current += 1;

    setAccountRefreshError("");
    setRefreshingAccounts(false);

    clearSession();

    setSession(null);
    setUser(null);
    setAccounts([]);
    setTransactions([]);
    setMenuOpen(false);

    navigate("/signin", { replace: true });
  }

  // Existing local-only demo transfer implementation.
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

    setAccounts((previous) =>
      previous.map((account) => {
        if (String(account.id) === String(fromId)) {
          return {
            ...account,
            balance: account.balance - amount,
            available: account.available - amount,
          };
        }

        if (String(account.id) === String(toId)) {
          return {
            ...account,
            balance: account.balance + amount,
            available: account.available + amount,
          };
        }

        return account;
      })
    );

    const date = new Date().toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });

    setTransactions((previous) => [
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
      ...previous,
    ]);

    notify(
      "Demo transfer completed. No real money was moved."
    );

    return "";
  }

  function schedulePayment(name, amount) {
    setPayments((previous) => [
      ...previous,
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
          onMenu={() => setMenuOpen((previous) => !previous)}
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
            onClick={() => setBankingError("")}
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
                <Sidebar
                  open={menuOpen}
                  close={() => setMenuOpen(false)}
                  isAdmin={isAdmin}
                />

                <div className="app-content">
                  <main className="main-content">
                    <Routes>
                      <Route
                        path="/dashboard"
                        element={<Dashboard {...shared} />}
                      />

                      <Route
                        path="/accounts"
                        element={
                          <Accounts
                            {...shared}
                            bankingLoading={bankingLoading}
                            bankingError={bankingError}
                            onOpenAccount={openAccount}
                            openingAccount={openingAccount}
                            accountRefreshError={accountRefreshError}
                            refreshingAccounts={refreshingAccounts}
                            onRefreshAccounts={() => refreshAccounts()}
                          />
                        }
                      />

                      <Route
                        path="/accounts/:accountId"
                        element={
                          <AccountDetail
                            key={session?.access_token}
                            onCloseAccount={handleCloseAccount}
                          />
                        }
                      />

                      <Route
                        path="/transactions"
                        element={<Transactions {...shared} />}
                      />

                      <Route
                        path="/transfers"
                        element={<Transfers {...shared} />}
                      />

                      <Route
                        path="/payments"
                        element={<Payments {...shared} />}
                      />

                      <Route
                        path="/deposit"
                        element={
                          <AccountMovement
                            key={`${session?.access_token}:deposit`}
                            kind="deposit"
                            accounts={accounts}
                            onMove={handleMovement}
                          />
                        }
                      />

                      <Route
                        path="/withdraw"
                        element={
                          <AccountMovement
                            key={`${session?.access_token}:withdraw`}
                            kind="withdraw"
                            accounts={accounts}
                            onMove={handleMovement}
                          />
                        }
                      />

                      <Route
                        path="/atm-finder"
                        element={<ATMFinder />}
                      />

                      {/* New camera-based check submission */}
                      <Route
                        path="/check-deposit"
                        element={
                          <CheckDeposit
                            key={session?.access_token}
                            accounts={accounts}
                          />
                        }
                      />

                      {/* New Chase ATM map */}
                      <Route
                        path="/atm-finder"
                        element={<ATMFinder />}
                      />

                      <Route
                        path="/cards"
                        element={<Cards {...shared} />}
                      />

                      <Route
                        path="/settings"
                        element={<Settings {...shared} />}
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
