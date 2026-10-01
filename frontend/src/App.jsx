import Navbar from "./components/Navbar.jsx";
import WelcomeSection from "./components/WelcomeSection.jsx";
import AccountsOverview from "./components/AccountsOverview.jsx";
import QuickActions from "./components/QuickActions.jsx";
import RecentTransactions from "./components/RecentTransactions.jsx";
import UpcomingPayments from "./components/UpcomingPayments.jsx";
import Footer from "./components/Footer.jsx";

function App() {
  return (
    <div className="app">
      <Navbar />

      <main className="dashboard">
        <WelcomeSection />

        <div className="dashboard-container">
          <AccountsOverview />

          <QuickActions />

          <div className="dashboard-grid">
            <RecentTransactions />
            <UpcomingPayments />
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}

export default App;