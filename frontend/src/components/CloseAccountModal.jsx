import { useEffect, useRef, useState } from 'react';
import { money } from '../data/mockData';

export default function CloseAccountModal({ account, onDismiss, onConfirm }) {
  const dialogRef = useRef(null);
  const pendingRef = useRef(false);
  const mountedRef = useRef(false);
  const [balance, setBalance] = useState(account.balance);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const blocked = balance !== 0;
  const type = account.account_type === 'savings' ? 'Savings' : 'Checking';

  useEffect(() => {
    mountedRef.current = true;
    const previousFocus = document.activeElement;
    const dialog = dialogRef.current;
    dialog.showModal();
    return () => {
      mountedRef.current = false;
      dialog.close();
      previousFocus?.focus();
    };
  }, []);

  async function confirm() {
    if (pendingRef.current || blocked) return;
    pendingRef.current = true;
    setSubmitting(true);
    setError('');
    try {
      await onConfirm();
    } catch (failure) {
      if (!mountedRef.current) return;
      if (failure.status === 409 && typeof failure.detail?.balance === 'number') {
        setBalance(failure.detail.balance);
      } else {
        setError(failure.message || 'Unable to close the account.');
      }
    } finally {
      pendingRef.current = false;
      if (mountedRef.current) setSubmitting(false);
    }
  }

  return (
    <dialog ref={dialogRef} className="open-account-dialog"
      aria-labelledby="close-account-title" aria-describedby="close-account-description"
      onCancel={event => {
        event.preventDefault();
        if (!pendingRef.current) onDismiss();
      }}>
      <h2 id="close-account-title">
        {blocked ? 'Move your money first' : `Close ${type} ••••${account.number.slice(-4)}?`}
      </h2>
      <p id="close-account-description">
        {blocked
          ? `This account still has ${money(balance)}. Transfer or withdraw it, then you can close the account.`
          : "You won't be able to use this account after it's closed. Any bills scheduled from it will be cancelled."}
      </p>
      {error && <p role="alert">{error}</p>}
      {submitting && <p role="status">Closing your account...</p>}
      <div className="open-account-actions">
        <button type="button" className="button button-outline" autoFocus
          disabled={submitting} onClick={onDismiss}>{blocked ? 'Cancel' : 'Keep it'}</button>
        {!blocked && <button type="button" className="button button-danger"
          disabled={submitting} onClick={confirm}>Close account</button>}
      </div>
    </dialog>
  );
}
