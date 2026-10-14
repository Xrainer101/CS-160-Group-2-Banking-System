const accounts = [
  {
    type: "Checking",
    number: "•••• 2048",
    balance: "$8,240.10",
    available: "$8,120.10",
  },
  {
    type: "Savings",
    number: "•••• 7391",
    balance: "$4,240.15",
    available: "$4,240.15",
  },
];

function AccountsOverview() {
  return (
    <section className="accounts-section">
      <div className="section-header">
        <div>
          <p className="section-label">YOUR ACCOUNTS</p>
          <h2>Accounts</h2>
        </div>

        <button className="outline-btn">Open New Account</button>
      </div>

      <div className="accounts-grid">
        {accounts.map((account) => (
          <div className="account-card" key={account.number}>
            <div className="account-card-top">
              <div>
                <span className="account-type">{account.type}</span>
                <span className="account-number">{account.number}</span>
              </div>

              <button className="menu-btn">•••</button>
            </div>

            <div className="account-balance">
              <span>Current balance</span>
              <h3>{account.balance}</h3>
            </div>

            <div className="account-footer">
              <div>
                <span>Available balance</span>
                <strong>{account.available}</strong>
              </div>

              <button className="view-account-btn">
                View account →
              </button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

export default AccountsOverview;