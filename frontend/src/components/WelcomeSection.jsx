function WelcomeSection() {
  return (
    <section className="welcome-section">
      <div className="dashboard-container">
        <div>
          <p className="welcome-label">OVERVIEW</p>
          <h1>Welcome back, Sachi.</h1>
          <p className="welcome-text">
            Here's a quick look at your accounts and recent banking activity.
          </p>
        </div>

        <div className="last-login">
          <span>Last login</span>
          <strong>Today, 4:18 PM</strong>
        </div>
      </div>
    </section>
  );
}

export default WelcomeSection;