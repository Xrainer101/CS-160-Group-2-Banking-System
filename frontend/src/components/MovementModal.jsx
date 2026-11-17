import { useEffect, useRef, useState } from 'react';
import { money } from '../data/mockData';

export default function MovementModal({ kind, pending, onMove, onFieldError, onDismiss }) {
  const dialogRef = useRef(null);
  const busyRef = useRef(false);
  const mounted = useRef(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const title = kind === 'deposit' ? 'Deposit' : 'Withdraw';
  const label = `${pending.account.account_type === 'savings' ? 'Savings' : 'Checking'} ${pending.account.number}`;

  useEffect(() => {
    mounted.current = true;
    const previous = document.activeElement;
    dialogRef.current.showModal();
    return () => { mounted.current = false; dialogRef.current?.close(); previous?.focus(); };
  }, []);

  async function confirm() {
    if (busyRef.current || result) return;
    busyRef.current = true;
    setBusy(true);
    setError('');
    try {
      const completed = await onMove(pending.account.id, kind, pending.amount);
      if (mounted.current && completed) setResult(completed);
    } catch (failure) {
      if (!mounted.current) return;
      if (failure.detail?.field === 'amount') {
        onFieldError(failure.detail.message);
      } else {
        setError(failure.message || 'Unable to complete transaction.');
      }
    } finally {
      busyRef.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  async function retryRefresh() {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError('');
    try {
      const account = await result.refresh();
      if (mounted.current && account) setResult(previous => ({ ...previous, account }));
    } catch {
      if (mounted.current) setError('Unable to refresh. Your transaction has already completed.');
    } finally {
      busyRef.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  return (
    <dialog ref={dialogRef} className="open-account-dialog"
      aria-labelledby="movement-title" aria-describedby="movement-description"
      onCancel={event => { event.preventDefault(); if (!busyRef.current) onDismiss(Boolean(result)); }}>
      <h2 id="movement-title">{result ? 'Done' : 'Please Confirm'}</h2>
      <div id="movement-description">
        {result ? <>
          <p>{kind === 'deposit' ? 'Deposited' : 'Withdrew'} {money(Number(pending.amount))} {kind === 'deposit' ? 'into' : 'from'} your {label}.</p>
          {result.account ? <p>New {label} balance: {money(result.account.balance)}</p>
            : <p>Your transaction completed, but the balance could not refresh.</p>}
        </> : <p>{title} {money(Number(pending.amount))} {kind === 'deposit' ? 'into' : 'from'} your {label}?</p>}
      </div>
      {error && <p role="alert">{error}</p>}
      {busy && <p role="status">{result ? 'Refreshing...' : 'Processing...'}</p>}
      <div className="open-account-actions">
        {result ? <>
          {!result.account && <button type="button" className="button button-outline" disabled={busy} onClick={retryRefresh}>Refresh balance</button>}
          <button type="button" className="button button-primary" disabled={busy} onClick={() => onDismiss(true)}>OK</button>
        </> : <>
          <button type="button" className="button button-outline" autoFocus disabled={busy} onClick={() => onDismiss(false)}>Cancel</button>
          <button type="button" className="button button-primary" disabled={busy} onClick={confirm}>{title}</button>
        </>}
      </div>
    </dialog>
  );
}
