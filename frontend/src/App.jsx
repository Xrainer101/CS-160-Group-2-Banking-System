import { useEffect, useRef, useState } from "react";

import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
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

import {
  initialAccounts,
  initialTransactions,
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
  createAccount,
  closeAccount,
  moveAccountMoney,
} from "./api/bankingApi";

import { getManagerPermission } from "./api/adminApi";

import "./index.css";
import "./styles/components.css";
import "./styles/dashboard.css";
import "./styles/auth.css";

// ======================================================
// OPTIONAL LOCAL PAGES
// ======================================================
// Vite includes these modules only when the files exist.
//
// devDemo.local.jsx is only used in development.
// The ATM Finder and Check Deposit pages can also be
// developed independently without breaking App.jsx.

const localModules = import.meta.glob(
  [
    "./devDemo.local.jsx",
    "./pages/ATMFinder.jsx",
    "./pages/CheckDeposit.jsx",
  ],
  { eager: true }
);

const DevDemoPage = import.meta.env.DEV
  ? localModules["./devDemo.local.jsx"]?.default
  : null;

const ATMFinder =
  localModules["./pages/ATMFinder.jsx"]?.default;

const CheckDeposit =
  localModules["./pages/CheckDeposit.jsx"]?.default;

// ======================================================
// LOCAL DEVELOPMENT DEMO
// ======================================================

const DEMO_STORAGE_KEY = "team2bank.local.demo";

const DEMO_USER = {
  userId: "demo",
  name: "Demo Customer",
  username: "demo",
  email: "demo@example.com",
  role: "customer",
  isNewUser: false,
};

function isDemoSession() {
  return (
    import.meta.env.DEV &&
    Boolean(DevDemoPage) &&
    sessionStorage.getItem(DEMO_STORAGE_KEY) === "true"
  );
}

function createDemoAccounts() {
  return initialAccounts.map((account) => ({
    ...account,
    status: "active",
    account_type:
      String(account.id) === "savings"
        ? "savings"
        : "checking",
  }));
}

// ======================================================
// AUTH0 / USER PROFILE HELPERS
// ======================================================

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

// ======================================================
// MAIN APPLICATION
// ======================================================

function AppContent() {
  const location = useLocation();
  const navigate = useNavigate();

  // ----------------------------------------------------
  // AUTHENTICATION STATE
  // ----------------------------------------------------

  const [demoMode, setDemoMode] = useState(
    () => isDemoSession()
  );

  const [session, setSession] = useState(
    () => getSession()
  );

  const [user, setUser] = useState(() =>
    isDemoSession()
      ? DEMO_USER
      : userFromSession(getSession())
  );

  // ----------------------------------------------------
  // BANKING DATA
  // ----------------------------------------------------

  const [accounts, setAccounts] = useState(() =>
    isDemoSession()
      ? createDemoAccounts()
      : []
  );

  const [transactions, setTransactions] =
    useState(() =>
      isDemoSession()
        ? [...initialTransactions]
        : []
    );

  const [payments, setPayments] =
    useState(() => [...initialPayments]);

  const [frozen, setFrozen] = useState(false);

  // ----------------------------------------------------
  // UI STATE
  // ----------------------------------------------------

  const [notice, setNotice] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);

  const [bankingLoading, setBankingLoading] =
    useState(() =>
      !isDemoSession() && Boolean(getSession())
    );

  const [bankingError, setBankingError] =
    useState("");
  const [canViewManagerReports, setCanViewManagerReports] =
    useState(false);

  const isManagerArea =
    location.pathname === "/manager-dashboard" ||
    location.pathname === "/admin";

  const [openingAccount, setOpeningAccount] =
    useState(false);

  const [
    accountRefreshError,
    setAccountRefreshError,
  ] = useState("");

  const [
    refreshingAccounts,
    setRefreshingAccounts,
  ] = useState(false);

  const skipNextSessionLoadRef = useRef(false);
  const sessionVersionRef = useRef(0);
  const openingAccountRef = useRef(false);

  const isAdmin =
    !demoMode && user?.role === "admin";

  // ====================================================
  // NOTIFICATIONS
  // ====================================================

  function notify(message) {
    setNotice(message);

    window.setTimeout(() => {
      setNotice("");
    }, 4500);
  }

  // ====================================================
  // LOCAL DEMO LOGIN
  // ====================================================

  function enterDemo() {
    if (!import.meta.env.DEV || !DevDemoPage) {
      return;
    }

    sessionVersionRef.current += 1;

    // Demo mode never creates an Auth0 session.
    clearSession();
    setSession(null);

    sessionStorage.setItem(
      DEMO_STORAGE_KEY,
      "true"
    );

    setDemoMode(true);
    setUser(DEMO_USER);

    setAccounts(createDemoAccounts());
    setTransactions([...initialTransactions]);
    setPayments([...initialPayments]);

    setFrozen(false);
    setBankingLoading(false);
    setBankingError("");

    setAccountRefreshError("");
    setRefreshingAccounts(false);

    navigate("/dashboard", {
      replace: true,
    });
  }

  // ====================================================
  // REFRESH ACCOUNT DATA
  // ====================================================

  async function refreshAccounts(
    version = sessionVersionRef.current
  ) {
    if (version !== sessionVersionRef.current) {
      return;
    }

    if (demoMode) {
      setAccountRefreshError("");
      return true;
    }

    setRefreshingAccounts(true);

    try {
      const data = await getAccounts();

      if (version !== sessionVersionRef.current) {
        return;
      }

      setAccounts(data.accounts || []);
      setTransactions(data.transactions || []);

      setAccountRefreshError("");
      return true;
    } catch {
      if (version === sessionVersionRef.current) {
        setAccountRefreshError(
          "Account data could not refresh. Please retry."
        );
      }
      return false;
    } finally {
      if (version === sessionVersionRef.current) {
        setRefreshingAccounts(false);
      }
    }
  }

  // ====================================================
  // OPEN ACCOUNT
  // ====================================================

  async function openAccount(accountType) {
    // Local demo account creation.
    if (demoMode) {
      const id = `demo-${Date.now()}`;

      const isSavings =
        accountType === "savings";

      setAccounts((previous) => [
        ...previous,
        {
          id,
          type: isSavings
            ? "High-Yield Savings"
            : "Everyday Checking",
          account_type: accountType,
          number: "•••• DEMO",
          balance: 0,
          available: 0,
          color: isSavings ? "purple" : "blue",
          status: "active",
        },
      ]);

      notify(
        "Demo account created. No real account was opened."
      );

      return true;
    }

    // Existing authenticated backend flow.
    if (openingAccountRef.current) {
      return false;
    }

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

  // ====================================================
  // CLOSE ACCOUNT
  // ====================================================

  async function handleCloseAccount(accountId) {
    if (demoMode) {
      const account = accounts.find(
        (item) =>
          String(item.id) === String(accountId)
      );

      if (!account) {
        throw new Error("Account not found.");
      }

      if (account.balance !== 0) {
        throw new Error(
          "Move your demo funds before closing this account."
        );
      }

      setAccounts((previous) =>
        previous.filter(
          (item) =>
            String(item.id) !== String(accountId)
        )
      );

      setTransactions((previous) =>
        previous.filter(
          (item) =>
            String(item.account) !== String(accountId)
        )
      );

      navigate("/accounts", {
        replace: true,
      });

      notify("Demo account closed.");

      return true;
    }

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
            String(transaction.account) !==
            String(accountId)
        )
      );

      navigate("/accounts", {
        replace: true,
      });

      notify("Account closed successfully.");

      return true;
    } catch (error) {
      if (version === sessionVersionRef.current) {
        throw error;
      }

      return false;
    }
  }

  // ====================================================
  // REFRESH AFTER DEPOSIT OR WITHDRAWAL
  // ====================================================

  async function refreshMovement(version) {
    if (demoMode) {
      return accounts;
    }

    const data = await getAccounts();

    if (version !== sessionVersionRef.current) {
      return null;
    }

    setAccounts(data.accounts || []);
    setTransactions(data.transactions || []);

    return data.accounts || [];
  }

  // ====================================================
  // DEPOSIT / WITHDRAW
  // ====================================================

  async function handleMovement(
    accountId,
    kind,
    amount
  ) {
    if (demoMode) {
      const value = Number(amount);

      if (
        !Number.isFinite(value) ||
        value <= 0 ||
        !/^\d+(?:\.\d{1,2})?$/.test(String(amount))
      ) {
        throw new Error("Invalid amount.");
      }

      const account = accounts.find(
        (item) =>
          String(item.id) === String(accountId)
      );

      if (!account) {
        throw new Error("Account not found.");
      }

      if (
        kind === "withdraw" &&
        value > account.available
      ) {
        throw new Error(
          "Insufficient available funds."
        );
      }

      const adjustment =
        kind === "deposit"
          ? value
          : -value;

      const updatedAccount = {
        ...account,
        balance:
          Math.round(
            (account.balance + adjustment) * 100
          ) / 100,
        available:
          Math.round(
            (account.available + adjustment) * 100
          ) / 100,
      };

      setAccounts((previous) =>
        previous.map((item) =>
          String(item.id) === String(accountId)
            ? updatedAccount
            : item
        )
      );

      const date = new Date().toLocaleDateString(
        "en-US",
        {
          month: "short",
          day: "numeric",
          year: "numeric",
        }
      );

      setTransactions((previous) => [
        {
          id: Date.now(),
          name:
            kind === "deposit"
              ? "Demo Deposit"
              : "Demo Withdrawal",
          category:
            kind === "deposit"
              ? "Income"
              : "Expense",
          date,
          amount: adjustment,
          account: accountId,
          icon:
            kind === "deposit"
              ? "ArrowDownLeft"
              : "CircleDollarSign",
        },
        ...previous,
      ]);

      notify(
        "Demo transaction completed. No real funds were moved."
      );

      return {
        account: updatedAccount,
        refresh: async () => updatedAccount,
      };
    }

    // Existing authenticated backend flow.
    const version = sessionVersionRef.current;

    try {
      await moveAccountMoney(
        accountId,
        kind,
        amount
      );
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
            String(account.id) ===
            String(accountId)
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

  // ====================================================
  // LOAD AUTH0 BANKING DATA
  // ====================================================

  useEffect(() => {
    let cancelled = false;

    // Never request protected banking data
    // while using the local demo account.
    if (demoMode) {
      setBankingLoading(false);
      setBankingError("");
      return;
    }

    if (!session) {
      setAccounts([]);
      setTransactions([]);
      setBankingError("");
      setBankingLoading(false);
      setCanViewManagerReports(false);
      return;
    }

    if (skipNextSessionLoadRef.current) {
      skipNextSessionLoadRef.current = false;
      return;
    }

    async function loadBankingData() {
      setAccounts([]);
      setTransactions([]);
      setBankingLoading(true);
      setBankingError("");

      try {
        const profile = await getCurrentUser();
        const accountData = await getAccounts();
        const permission = await getManagerPermission();

        if (cancelled) return;

        setUser(userFromProfile(profile));

        setAccounts(accountData.accounts || []);

        setTransactions(
          accountData.transactions || []
        );
        setCanViewManagerReports(
          permission.authorized === true
        );
      } catch (error) {
        if (!cancelled) {
          setAccounts([]);
          setTransactions([]);

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
  }, [session, demoMode]);

  // ====================================================
  // AUTH0 LOGIN CALLBACK
  // ====================================================

  async function handleAuthenticated(result) {
    if (!result?.session) return;

    sessionVersionRef.current += 1;

    sessionStorage.removeItem(DEMO_STORAGE_KEY);
    setDemoMode(false);

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
      const permission = await getManagerPermission();

      setUser(userFromProfile(profile));

      setAccounts(accountData.accounts || []);

      setTransactions(
        accountData.transactions || []
      );
      setCanViewManagerReports(
        permission.authorized === true
      );

      skipNextSessionLoadRef.current = true;
      setSession(result.session);

      navigate(
        permission.authorized === true
          ? "/manager-dashboard"
          : "/dashboard"
      );
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

  // ====================================================
  // SIGN OUT
  // ====================================================

  function signOut() {
    sessionVersionRef.current += 1;

    sessionStorage.removeItem(DEMO_STORAGE_KEY);

    setDemoMode(false);
    setAccountRefreshError("");
    setRefreshingAccounts(false);

    clearSession();

    setSession(null);
    setUser(null);
    setCanViewManagerReports(false);
    setMenuOpen(false);

    setAccounts([]);
    setTransactions([]);
    setPayments([...initialPayments]);

    setBankingError("");
    setBankingLoading(false);

    if (import.meta.env.DEV && DevDemoPage) {
      navigate("/dev-demo", {
        replace: true,
      });
    } else {
      navigate("/signin", {
        replace: true,
      });
    }
  }

  // ====================================================
  // TRANSFERS
  // ====================================================

  function transfer(fromId, toId, amount) {
    const from = accounts.find(
      (account) =>
        String(account.id) === String(fromId)
    );

    const to = accounts.find(
      (account) =>
        String(account.id) === String(toId)
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

    const date = new Date().toLocaleDateString(
      "en-US",
      {
        month: "short",
        day: "numeric",
        year: "numeric",
      }
    );

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

  // ====================================================
  // BILL PAY - UPDATED
  // ====================================================
  // Supports:
  // - One-time payments
  // - Recurring payment labels
  // - Funding account
  // - Payment date
  // - Category
  // - Memo
  // - Cancellation
  //
  // This is frontend-only demo functionality.
  // It does not debit an account or call a bill-pay API.

  function schedulePayment(
    name,
    amount,
    details = {}
  ) {
    const newPayment = {
      id:
        typeof crypto !== "undefined" &&
        typeof crypto.randomUUID === "function"
          ? crypto.randomUUID()
          : `payment-${Date.now()}-${Math.random()}`,

      name,
      amount: Number(amount),

      date:
        details.paymentDate || "Scheduled",

      paymentDate:
        details.paymentDate || null,

      category:
        details.category || "Other",

      accountId:
        details.accountId || null,

      frequency:
        details.frequency || "once",

      memo:
        details.memo || "",

      status: "scheduled",
    };

    setPayments((previous) => [
      ...previous,
      newPayment,
    ]);

    notify(
      "Demo bill payment scheduled successfully."
    );

    return newPayment;
  }

  function cancelPayment(paymentId) {
    setPayments((previous) =>
      previous.filter(
        (payment) =>
          String(payment.id) !==
          String(paymentId)
      )
    );

    notify(
      "Demo scheduled payment cancelled."
    );
  }

  // ====================================================
  // SHARED PROPS
  // ====================================================

  const shared = {
    user,
    accounts,
    transactions,
    payments,

    frozen,
    setFrozen,

    notify,
    signOut,

    schedulePayment,
    cancelPayment,
  };

  // ====================================================
  // APPLICATION ROUTES
  // ====================================================

  return (
    <>
      {user && (
        <Navbar
          user={user}
          showMenu={!isManagerArea}
          onMenu={() =>
            setMenuOpen((previous) => !previous)
          }
          onSignOut={signOut}
        />
      )}

      {notice && (
        <div
          className="toast"
          role="status"
        >
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
        <div
          className="toast"
          role="status"
        >
          Loading your banking data...
        </div>
      )}

      {bankingError && (
        <div
          className="toast"
          role="alert"
        >
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
        {/* ===================================== */}
        {/* LOCAL DEMO LOGIN                      */}
        {/* ===================================== */}

        {import.meta.env.DEV && DevDemoPage && (
          <Route
            path="/dev-demo"
            element={
              user ? (
                <Navigate
                  to="/dashboard"
                  replace
                />
              ) : (
                <DevDemoPage
                  onEnter={enterDemo}
                />
              )
            }
          />
        )}

        {/* ===================================== */}
        {/* AUTH0 SIGN IN                         */}
        {/* ===================================== */}

        <Route
          path="/signin"
          element={
            user ? (
              <Navigate
                to={
                  canViewManagerReports
                    ? "/manager-dashboard"
                    : "/dashboard"
                }
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

        {/* ===================================== */}
        {/* AUTH0 SIGN UP                         */}
        {/* ===================================== */}

        <Route
          path="/signup"
          element={
            user ? (
              <Navigate
                to={
                  canViewManagerReports
                    ? "/manager-dashboard"
                    : "/dashboard"
                }
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

        {/* ===================================== */}
        {/* BANKING APPLICATION                   */}
        {/* ===================================== */}

        <Route
          path="/*"
          element={
            user ? (
              <div className="app-shell">
                {!isManagerArea && (
                  <Sidebar
                    open={menuOpen}
                    close={() =>
                      setMenuOpen(false)
                    }
                    canViewManagerReports={
                      canViewManagerReports
                    }
                  />
                )}

                <div className="app-content">
                  <main className="main-content">
                    <Routes>
                      {/* DASHBOARD */}

                      <Route
                        path="/dashboard"
                        element={
                          <Dashboard
                            {...shared}
                          />
                        }
                      />

                      {/* ACCOUNTS */}

                      <Route
                        path="/accounts"
                        element={
                          <Accounts
                            {...shared}
                            bankingLoading={
                              bankingLoading
                            }
                            bankingError={
                              bankingError
                            }
                            onOpenAccount={
                              openAccount
                            }
                            openingAccount={
                              openingAccount
                            }
                            accountRefreshError={
                              accountRefreshError
                            }
                            refreshingAccounts={
                              refreshingAccounts
                            }
                            onRefreshAccounts={() =>
                              refreshAccounts()
                            }
                          />
                        }
                      />

                      {/* ACCOUNT DETAILS */}

                      <Route
                        path="/accounts/:accountId"
                        element={
                          demoMode ? (
                            <Navigate
                              to="/accounts"
                              replace
                            />
                          ) : (
                            <AccountDetail
                              key={
                                session?.access_token
                              }
                              onCloseAccount={
                                handleCloseAccount
                              }
                            />
                          )
                        }
                      />

                      {/* TRANSACTIONS */}

                      <Route
                        path="/transactions"
                        element={
                          <Transactions
                            {...shared}
                          />
                        }
                      />

                      {/* TRANSFERS */}

                      <Route
                        path="/transfers"
                        element={
                          <Transfers
                            key={demoMode ? "demo" : session?.access_token}
                            accounts={accounts}
                            demoTransfer={demoMode ? transfer : undefined}
                            refreshAccounts={refreshAccounts}
                          />
                        }
                      />

                      {/* PAY BILLS - UPDATED */}

                      <Route
                        path="/payments"
                        element={
                          <Payments
                            {...shared}
                          />
                        }
                      />

                      {/* DEPOSIT */}

                      <Route
                        path="/deposit"
                        element={
                          <AccountMovement
                            kind="deposit"
                            accounts={accounts}
                            onMove={
                              handleMovement
                            }
                          />
                        }
                      />

                      {/* WITHDRAW */}

                      <Route
                        path="/withdraw"
                        element={
                          <AccountMovement
                            kind="withdraw"
                            accounts={accounts}
                            onMove={
                              handleMovement
                            }
                          />
                        }
                      />

                      {/* ATM FINDER */}

                      {ATMFinder && (
                        <Route
                          path="/atm-finder"
                          element={
                            <ATMFinder />
                          }
                        />
                      )}

                      {/* MOBILE CHECK DEPOSIT */}

                      {CheckDeposit && (
                        <Route
                          path="/check-deposit"
                          element={
                            demoMode ? (
                              <section className="panel">
                                <h2>
                                  Mobile Check Deposit
                                </h2>

                                <p>
                                  This feature
                                  requires Auth0
                                  authentication
                                  to submit
                                  simulated checks.
                                </p>

                                <p>
                                  Demo mode
                                  does not upload
                                  check photos
                                  or credit funds.
                                </p>
                              </section>
                            ) : (
                              <CheckDeposit
                                accounts={accounts}
                              />
                            )
                          }
                        />
                      )}

                      {/* CARDS */}

                      <Route
                        path="/cards"
                        element={
                          <Cards
                            {...shared}
                          />
                        }
                      />

                      {/* SETTINGS */}

                      <Route
                        path="/settings"
                        element={
                          <Settings
                            {...shared}
                          />
                        }
                      />

                      {/* ADMIN PREVIEW */}

                      <Route
                        path="/admin"
                        element={
                          canViewManagerReports ? (
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

                      {/* ADMIN PORTAL */}

                      <Route
                        path="/manager-dashboard"
                        element={
                          canViewManagerReports ? (
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

                      {/* DEFAULT ROUTE */}

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

// ======================================================
// ROOT APP
// ======================================================

export default function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  );
}
