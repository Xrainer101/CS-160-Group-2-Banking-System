const transactions = [
  {
    name: "Trader Joe's",
    category: "Groceries",
    date: "Sep 30",
    amount: "-$42.63",
    type: "expense",
    icon: "🛒",
  },
  {
    name: "Direct Deposit",
    category: "Income",
    date: "Sep 29",
    amount: "+$1,850.00",
    type: "income",
    icon: "↓",
  },
  {
    name: "PG&E",
    category: "Utilities",
    date: "Sep 28",
    amount: "-$94.21",
    type: "expense",
    icon: "⚡",
  },
  {
    name: "Transfer to Savings",
    category: "Transfer",
    date: "Sep 27",
    amount: "-$300.00",
    type: "expense",
    icon: "⇄",
  },
];

function RecentTransactions() {
  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <p className="section-label">ACTIVITY</p>
          <h2>Recent Transactions</h2>
        </div>

        <button className="text-btn">View all</button>
      </div>

      <div className="transaction-list">
        {transactions.map((transaction, index) => (
          <div className="transaction-row" key={index}>
            <div className="transaction-left">
              <div className="transaction-icon">
                {transaction.icon}
              </div>

              <div>
                <strong>{transaction.name}</strong>

                <div className="transaction-meta">
                  <span>{transaction.category}</span>
                  <span>•</span>
                  <span>{transaction.date}</span>
                </div>
              </div>
            </div>

            <strong
              className={
                transaction.type === "income"
                  ? "transaction-income"
                  : "transaction-expense"
              }
            >
              {transaction.amount}
            </strong>
          </div>
        ))}
      </div>
    </section>
  );
}

export default RecentTransactions;