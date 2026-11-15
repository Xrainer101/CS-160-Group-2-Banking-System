import { useState } from 'react';
import { money } from '../data/mockData';
export default function Transfers({ accounts = [], transfer }) {
  const [from, setFrom] = useState(''),
    [to, setTo] = useState(''),
    [amount, setAmount] = useState(''),
    [error, setError] = useState('');
  const fromId = accounts.some(account => String(account.id) === from)
    ? from : String(accounts[0]?.id ?? '');
  const toId = accounts.some(account => String(account.id) === to)
    ? to : String((accounts.find(account => String(account.id) !== fromId) || accounts[0])?.id ?? '');
  function submit(e) {
    e.preventDefault();
    const result = transfer(fromId, toId, Number(amount));
    setError(result);
    if (!result) setAmount('');
  }
  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">MOVE MONEY</span>
        <h1>Transfers</h1>
        <p>Practice moving money between demo accounts.</p>
      </div>
        <section className="panel">
          <h2>Transfer between accounts</h2>
          <p className="panel-subtitle">
            Changes appear instantly in this demo only.
          </p>
          {accounts.length < 2 && <p className="empty">You need two accounts to transfer money.</p>}
          <form className="stacked-form" onSubmit={submit}>
            <label>
              From account
              <select value={fromId} disabled={!accounts.length} onChange={(e) => setFrom(e.target.value)}>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.type} {a.number} — {money(a.available)} available
                  </option>
                ))}
              </select>
            </label>
            <label>
              To account
              <select value={toId} disabled={!accounts.length} onChange={(e) => setTo(e.target.value)}>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.type} {a.number}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Amount (USD)
              <input
                type="number"
                min="0.01"
                step="0.01"
                required
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </label>
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            <button className="button button-primary" disabled={accounts.length < 2}>Transfer (demo)</button>
          </form>
        </section>
    </>
  );
}
