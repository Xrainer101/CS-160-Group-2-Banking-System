import { useState, useEffect } from 'react';
import AccountsOverview from '../components/AccountsOverview';
import { TransactionRows } from '../components/RecentTransactions';
import { money } from '../data/mockData';
import { fetchData } from '../api';
export default function Accounts() {

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchData('/accounts')
      .then(res => {
        setData(res);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  if (loading) return <div>Loading data...</div>;
  if (!data) return <div>Failed to load data.</div>;
  const accounts = data.accounts
  const transactions = data.transactions
  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">YOUR FINANCES</span>
        <h1>My accounts</h1>
        <p>Explore your balances and account activity.</p>
      </div>
      <AccountsOverview accounts={accounts} />
      <section className="panel">
        <h2>Account summary</h2>
        <div className="summary-list">
          {!accounts ? (
            <p className="empty">No accounts found.</p>
          ) : (
            accounts.map((a) => (
              <div key={a.id} className="summary-row">
                <span>
                  {a.type} <small>{a.number}</small>
                </span>
                <strong>{money(a.balance)}</strong>
              </div>
            ))
          )}
        </div>
      </section>
      <section className="panel">
        <h2>Latest activity</h2>
        <TransactionRows transactions={transactions} />
      </section>
    </>
  );
}
