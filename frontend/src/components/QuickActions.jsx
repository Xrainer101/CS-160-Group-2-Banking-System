const actions = [
  {
    icon: "⇄",
    title: "Transfer Money",
    description: "Move money between accounts",
  },
  {
    icon: "$",
    title: "Pay Bills",
    description: "Manage automatic payments",
  },
  {
    icon: "▣",
    title: "Deposit Check",
    description: "Submit a mobile check deposit",
  },
  {
    icon: "⌖",
    title: "Find ATM",
    description: "Locate nearby Chase ATMs",
  },
];

function QuickActions() {
  return (
    <section className="quick-actions-section">
      <div className="section-header">
        <div>
          <p className="section-label">QUICK ACCESS</p>
          <h2>What would you like to do?</h2>
        </div>
      </div>

      <div className="quick-actions-grid">
        {actions.map((action) => (
          <button className="quick-action-card" key={action.title}>
            <div className="quick-action-icon">{action.icon}</div>

            <div>
              <h3>{action.title}</h3>
              <p>{action.description}</p>
            </div>

            <span className="quick-arrow">→</span>
          </button>
        ))}
      </div>
    </section>
  );
}

export default QuickActions;