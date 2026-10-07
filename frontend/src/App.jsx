import Navbar from "./components/Navbar.jsx";
import WelcomeSection from "./components/WelcomeSection.jsx";
import AccountsOverview from "./components/AccountsOverview.jsx";
import QuickActions from "./components/QuickActions.jsx";
import RecentTransactions from "./components/RecentTransactions.jsx";
import UpcomingPayments from "./components/UpcomingPayments.jsx";
import Footer from "./components/Footer.jsx";
import { useEffect, useState } from 'react'

function App() {
  const [message, setMessage] = useState('Loading...')

  useEffect(() => {
    fetch('http://localhost:8000/api/data')
      .then((res) => res.json())
      .then((data) => setMessage(data.message))
      .catch((err) => setMessage('Failed to connect to backend'))
  }, [])



  return (
    <div className="app">
      <p> {message}</p>
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