import { useState } from 'react';
import { Repeat } from 'lucide-react';
import { money } from '../data/mockData';

export default function Payments({ payments = [], schedulePayment }) {
  const totalScheduled = payments.reduce((sum, payment) => sum + payment.amount, 0);
  const largestBill = payments.reduce((largest, payment) => Math.max(largest, payment.amount), 0);
  const [payee, setPayee] = useState(''),
    [billAmount, setBillAmount] = useState(''),
    [billError, setBillError] = useState('');
  function bill(e) {
    e.preventDefault();
    if (
      !payee.trim() ||
      !Number.isFinite(Number(billAmount)) ||
      Number(billAmount) <= 0
    ) {
      setBillError('Enter a payee and a valid amount.');
      return;
    }
    schedulePayment(payee.trim(), Number(billAmount));
    setBillError('');
    setPayee('');
    setBillAmount('');
  }
  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">MOVE MONEY</span>
        <h1>Pay Bills</h1>
        <p>Practice scheduling demo bill payments.</p>
      </div>
      <div className="stats-grid">
        <section className="panel">
          <Repeat aria-hidden="true" />
          <small>Scheduled Bills</small>
          <h2>{payments.length}</h2>
        </section>
        <section className="panel">
          <small>Total Scheduled</small>
          <h2>{money(totalScheduled)}</h2>
        </section>
        <section className="panel">
          <small>Largest Bill</small>
          <h2>{money(largestBill)}</h2>
        </section>
      </div>
        <section className="panel">
          <h2>Schedule a bill</h2>
          <p className="panel-subtitle">Adds a simulated upcoming payment.</p>
          <form className="stacked-form" onSubmit={bill}>
            <label>
              Payee name
              <input
                value={payee}
                onChange={(e) => setPayee(e.target.value)}
                required
                placeholder="e.g. Internet provider"
              />
            </label>
            <label>
              Amount (USD)
              <input
                type="number"
                min="0.01"
                step="0.01"
                required
                value={billAmount}
                onChange={(e) => setBillAmount(e.target.value)}
                placeholder="0.00"
              />
            </label>
            {billError && (
              <p className="form-error" role="alert">
                {billError}
              </p>
            )}
            <button className="button button-primary">Schedule (demo)</button>
          </form>
        </section>
      <section className="panel">
        <h2>Your Bills</h2>
        {payments.length === 0 ? (
          <p className="empty">No bills scheduled yet.</p>
        ) : (
          payments.map(payment => (
            <div className="summary-row" key={payment.id}>
              <span>
                <strong>{payment.name}</strong>
                <small>{payment.date}</small>
              </span>
              <strong>{money(payment.amount)}</strong>
            </div>
          ))
        )}
      </section>
    </>
  );
}
