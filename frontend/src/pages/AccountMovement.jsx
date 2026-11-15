import { useState } from 'react';
import { money } from '../data/mockData';

export default function AccountMovement({ accounts = [], kind }) {
  const [selectedAccount, setSelectedAccount] = useState('');
  const [amount, setAmount] = useState('');
  const [memo, setMemo] = useState('');
  const title = kind === 'deposit' ? 'Deposit' : 'Withdraw';
  const accountId = accounts.some(account => String(account.id) === selectedAccount)
    ? selectedAccount : String(accounts[0]?.id ?? '');

  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">MOVE MONEY</span>
        <h1>{title}</h1>
      </div>
      <section className="panel">
        <h2>{title} money</h2>
        <p className="panel-subtitle">Coming soon.</p>
        {!accounts.length && <p className="empty">No accounts available.</p>}
        <form className="stacked-form" onSubmit={event => event.preventDefault()}>
          <label>
            {kind === 'deposit' ? 'Deposit to' : 'Withdraw from'}
            <select value={accountId} disabled={!accounts.length}
              onChange={event => setSelectedAccount(event.target.value)}>
              {!accounts.length && <option value="">No accounts available</option>}
              {accounts.map(account => (
                <option key={account.id} value={account.id}>
                  {account.type} {account.number} — {money(account.available)} available
                </option>
              ))}
            </select>
          </label>
          <label>
            Amount (USD)
            <input type="number" min="0.01" step="0.01" placeholder="0.00"
              value={amount} onChange={event => setAmount(event.target.value)} />
          </label>
          <label>
            Memo (optional)
            <input type="text" value={memo} onChange={event => setMemo(event.target.value)} />
          </label>
          <button type="submit" className="button button-primary" disabled>{title}</button>
        </form>
      </section>
    </>
  );
}
