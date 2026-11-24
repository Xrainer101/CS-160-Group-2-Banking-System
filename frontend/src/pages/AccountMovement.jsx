import { useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { money } from '../data/mockData';
import MovementModal from '../components/MovementModal';

export default function AccountMovement(props) {
  const [params] = useSearchParams();
  const initialAccountId = params.get('accountId') || '';
  const returnTo = initialAccountId
    ? `/accounts/${encodeURIComponent(initialAccountId)}${params.get('from') === 'overview' ? '?from=overview' : ''}` : null;
  return <AccountMovementForm key={initialAccountId} {...props} initialAccountId={initialAccountId} returnTo={returnTo} />;
}

function AccountMovementForm({ accounts = [], kind, onMove, initialAccountId, returnTo }) {
  const [selectedAccount, setSelectedAccount] = useState(initialAccountId);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(null);
  const amountRef = useRef(null);
  const activeAccounts = accounts.filter(account => account.status === 'active');
  const account = activeAccounts.find(account => String(account.id) === selectedAccount) || activeAccounts[0];
  const title = kind === 'deposit' ? 'Deposit' : 'Withdraw';

  function submit(event) {
    event.preventDefault();
    const text = amount.trim();
    let warning = '';
    if (!/^-?\d+(?:\.\d{1,2})?$/.test(text) || !Number.isFinite(Number(text))) {
      warning = 'Enter an amount like 25 or 25.50';
    } else if (Number(text) <= 0) {
      warning = 'Enter an amount greater than $0.00';
    } else if (kind === 'withdraw' && Number(text) > account?.available) {
      warning = `That's more than the ${money(account.available)} available`;
    }
    setError(warning);
    if (warning) { amountRef.current?.focus(); return; }
    if (account) setPending({ account, amount: text });
  }

  return (
    <>
      <div className="page-heading">
        {returnTo && <Link className="text-link" to={returnTo}>← Back to account details</Link>}
        <span className="eyebrow">MOVE MONEY</span><h1>{title}</h1>
      </div>
      <section className="panel">
        <h2>{title} money</h2>
        {!activeAccounts.length && <p className="empty">No active accounts available.</p>}
        <form className="stacked-form" noValidate onSubmit={submit}>
          <label>
            {kind === 'deposit' ? 'Deposit to' : 'Withdraw from'}
            <select value={account?.id ?? ''} disabled={!activeAccounts.length}
              onChange={event => { setSelectedAccount(event.target.value); setError(''); }}>
              {!activeAccounts.length && <option value="">No active accounts available</option>}
              {activeAccounts.map(account => (
                <option key={account.id} value={account.id}>
                  {account.type} {account.number} — {money(account.available)} available
                </option>
              ))}
            </select>
          </label>
          <label>
            Amount (USD)
            <input ref={amountRef} type="text" inputMode="decimal" placeholder="0.00"
              aria-invalid={Boolean(error)} aria-describedby={error ? 'amount-error' : undefined}
              value={amount} onChange={event => { setAmount(event.target.value); setError(''); }} />
          </label>
          {error && <p id="amount-error" className="form-error" role="alert">{error}</p>}
          <button type="submit" className="button button-primary" disabled={!account || Boolean(pending)}>{title}</button>
        </form>
      </section>
      {pending && <MovementModal kind={kind} pending={pending} onMove={onMove}
        onFieldError={message => { setPending(null); setError(message); amountRef.current?.focus(); }}
        onDismiss={completed => { setPending(null); if (completed) setAmount(''); }} />}
    </>
  );
}
