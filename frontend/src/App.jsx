
import { useState } from "react";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useNavigate,
} from "react-router-dom";

// Components
import Navbar from "./components/Navbar";
import Sidebar from "./components/Sidebar";
import Footer from "./components/Footer";

// Authentication pages
import SignIn from "./pages/SignIn";
import SignUp from "./pages/SignUp";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";

// Banking pages
import Dashboard from "./pages/Dashboard";
import Accounts from "./pages/Accounts";
import Transactions from "./pages/Transactions";
import Transfers from "./pages/Transfers";
import Cards from "./pages/Cards";
import Settings from "./pages/Settings";

// Backend authentication
import {
  loginUser,
  fetchCurrentUser,
} from "./api/authApi";

// Existing demonstration data
import {
  initialAccounts,
  initialTransactions,
  initialPayments,
} from "./data/mockData";

// Styles
import "./index.css";
import "./styles/components.css";
import "./styles/dashboard.css";
import "./styles/auth.css";

function AppContent() {
  const navigate = useNavigate();

  // Authenticated user
  const [user, setUser] = useState(null);
  const [accessToken, setAccessToken] = useState(null);

  // Existing banking state
  const [accounts, setAccounts] = useState(initialAccounts);
  const [transactions, setTransactions] =
    useState(initialTransactions);
  const [payments, setPayments] = useState(initialPayments);

  // Existing UI state
  const [frozen, setFrozen] = useState(false);
  const [notice, setNotice] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);

  // Display temporary notifications
  function notify(message) {
    setNotice(message);

    window.setTimeout(() => {
      setNotice("");
    }, 4500);
  }

  // Authenticate against FastAPI
  async function signIn(username, password) {
    const response = await loginUser(
      username.trim(),
      password
    );

    if (!response?.access_token) {
      throw new Error(
        "Login failed: the server did not return an access token."
      );
    }

    // Validate the access token and retrieve user details
    const profile = await fetchCurrentUser(
      response.access_token
    );

    if (!profile?.user_id) {
      throw new Error(
        "Unable to retrieve your account profile."
      );
    }

    // User is authenticated only after both API calls succeed
    setAccessToken(response.access_token);

    setUser({
      userId: profile.user_id,
      name: profile.name || profile.username,
      username: profile.username,
      email: profile.email || "",
    });

    setMenuOpen(false);

    navigate("/dashboard");
  }

  // End the current frontend session
  function signOut() {
    setUser(null);
    setAccessToken(null);
    setMenuOpen(false);

    // Clear any previous simulated banking changes
    setAccounts(initialAccounts);
    setTransactions(initialTransactions);
    setPayments(initialPayments);
    setFrozen(false);

    navigate("/signin", { replace: true });
  }

  // Existing DEMO transfer functionality.
  // This does not move real money or update the backend.
  function transfer(fromId, toId, amount) {
    const from = accounts.find(
      (account) => account.id === fromId
    );

    const to = accounts.find(
      (account) => account.id === toId
    );

    if (
      !from ||
      !to ||
      fromId === toId ||
      !Number.isFinite(amount) ||
      amount <= 0
    ) {
      return "Enter a valid transfer.";
    }

    if (amount > from.available) {
      return "Insufficient available funds.";
    }

    // Update demo account balances
    setAccounts((previousAccounts) =>
      previousAccounts.map((account) => {
        if (account.id === fromId) {
          return {
            ...account,
            balance: account.balance - amount,
            available: account.available - amount,
          };
        }

        if (account.id === toId) {
          return {
            ...account,
            balance: account.balance + amount,
            available: account.available + amount,
          };
        }

        return account;
      })
    );

    // Create demo transaction records
    const date = new Date().toLocaleDateString(
      "en-US",
      {
        month: "short",
        day: "numeric",
        year: "numeric",
      }
    );

    setTransactions((previousTransactions) => [
      {
        id: Date.now(),
        name: `Transfer to ${to.type}`,
        category: "Transfer",
        date,
        amount: -amount,
        account: fromId,
        icon: "ArrowLeftRight",
      },
      {
        id: Date.now() + 1,
        name: `Transfer from ${from.type}`,
        category: "Transfer",
        date,
        amount,
        account: toId,
        icon: "ArrowLeftRight",
      },
      ...previousTransactions,
    ]);

    notify(
      "Demo transfer completed. No real money was moved."
    );

    return "";
  }

  // Existing DEMO bill scheduling functionality
  function schedulePayment(name, amount) {
    setPayments((previousPayments) => [
      ...previousPayments,
      {
        id: Date.now(),
        name,
        amount,
        date: "Scheduled",
      },
    ]);

    notify(
      "Demo payment added to your upcoming payments."
    );
  }

  // Shared props for the existing banking pages
  const shared = {
    user,
    accessToken,

    accounts,
    setAccounts,

    transactions,
    setTransactions,

    payments,
    frozen,
    setFrozen,

    notify,
    transfer,
    schedulePayment,
  };

  return (
    <>
      {/* Navigation bar for authenticated users */}
      {user && (
        <Navbar
          user={user}
          onMenu={() =>
            setMenuOpen((previous) => !previous)
          }
          onSignOut={signOut}
        />
      )}

      {/* Global notifications */}
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

      <Routes>
        {/* SIGN IN */}
        <Route
          path="/signin"
          element={
            user ? (
              <Navigate
                to="/dashboard"
                replace
              />
            ) : (
              <SignIn onSignIn={signIn} />
            )
          }
        />

        {/* SIGN UP */}
        <Route
          path="/signup"
          element={
            user ? (
              <Navigate
                to="/dashboard"
                replace
              />
            ) : (
              <SignUp />
            )
          }
        />

        {/* FORGOT PASSWORD */}
        <Route
          path="/forgot-password"
          element={
            <ForgotPassword />
          }
        />

        {/* RESET PASSWORD */}
        <Route
          path="/reset-password"
          element={
            <ResetPassword />
          }
        />

        {/* PROTECTED BANKING PAGES */}
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
                />

                <div className="app-content">
                  <main className="main-content">
                    <Routes>
                      {/* Dashboard */}
                      <Route
                        path="/dashboard"
                        element={
                          <Dashboard {...shared} />
                        }
                      />

                      {/* Accounts */}
                      <Route
                        path="/accounts"
                        element={
                          <Accounts {...shared} />
                        }
                      />

                      {/* Transactions */}
                      <Route
                        path="/transactions"
                        element={
                          <Transactions {...shared} />
                        }
                      />

                      {/* Transfers */}
                      <Route
                        path="/transfers"
                        element={
                          <Transfers {...shared} />
                        }
                      />

                      {/* Cards */}
                      <Route
                        path="/cards"
                        element={
                          <Cards {...shared} />
                        }
                      />

                      {/* Settings */}
                      <Route
                        path="/settings"
                        element={
                          <Settings {...shared} />
                        }
                      />

                      {/* Default dashboard */}
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
