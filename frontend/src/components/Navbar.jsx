function Navbar() {
  return (
    <header className="navbar">
      <div className="navbar-container">
        <div className="brand">
          <div className="brand-icon">T2</div>

          <div>
            <h2>Team 2 Bank</h2>
            <span>Online Banking</span>
          </div>
        </div>

        <nav className="nav-links">
          <a href="#">Home</a>
          <a href="#">Accounts</a>
          <a href="#">Transfers</a>
          <a href="#">Bill Pay</a>
          <a href="#">ATM Locator</a>
        </nav>

        <div className="nav-user">
          <button className="notification-btn">🔔</button>

          <div className="user-info">
            <div className="user-avatar">SK</div>

            <div>
              <strong>Sachi</strong>
              <span>Customer</span>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}

export default Navbar;