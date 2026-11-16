import { useState } from 'react';
import OpenAccountModal from '../components/OpenAccountModal';
import AccountsOverview from '../components/AccountsOverview';
import { TransactionRows } from '../components/RecentTransactions';
import { money } from '../data/mockData';
export default function Accounts({
  accounts = [],
  transactions = [],
  bankingLoading = false,
  bankingError = '',
  onOpenAccount,
  openingAccount = false,
  accountRefreshError = '',
  refreshingAccounts = false,
  onRefreshAccounts,
}) {
  const [showAccountModal, setShowAccountModal] = useState(false);
  if (bankingLoading) return <div role="status">Loading your accounts...</div>;
  if (bankingError) return <div role="alert">Unable to load accounts: {bankingError}</div>;
  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">YOUR FINANCES</span>
        <h1>My accounts</h1>
        <p>Explore your balances and account activity.</p>
      </div>
      <button className="button button-primary" type="button"
        disabled={openingAccount || refreshingAccounts || Boolean(accountRefreshError)}
        onClick={() => setShowAccountModal(true)}>
        Open New Account
      </button>
      {showAccountModal && (
        <OpenAccountModal onClose={() => setShowAccountModal(false)}
          onCreate={onOpenAccount} submitting={openingAccount} />
      )}
      {accountRefreshError && (
        <div role="alert">
          <p>{accountRefreshError}</p>
          <button type="button" disabled={refreshingAccounts}
            onClick={onRefreshAccounts}>
            {refreshingAccounts ? 'Refreshing...' : 'Refresh accounts'}
          </button>
        </div>
      )}
      <AccountsOverview accounts={accounts} />
      <section className="panel">
        <h2>Account summary</h2>
        <div className="summary-list">
          {!accounts.length ? (
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
