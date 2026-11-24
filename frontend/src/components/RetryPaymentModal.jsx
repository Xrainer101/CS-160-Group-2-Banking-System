import { useEffect, useRef, useState } from 'react';
import { money } from '../data/mockData';

export default function RetryPaymentModal({ payment, onConfirm, onDismiss }) {
  const dialogRef = useRef(null);
  const busyRef = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement;
    dialog.showModal();
    return () => {
      dialog.close();
      previousFocus?.focus();
    };
  }, []);

  async function confirm() {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError('');
    let failure;
    try {
      failure = await onConfirm();
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
    if (failure) setError(failure);
    else onDismiss();
  }

  return (
    <dialog ref={dialogRef} className="open-account-dialog"
      aria-labelledby="retry-payment-title" aria-describedby="retry-payment-description"
      onCancel={event => { event.preventDefault(); if (!busyRef.current) onDismiss(); }}>
      <h2 id="retry-payment-title">Please Confirm</h2>
      <p id="retry-payment-description">
        Pay {money(Math.abs(payment.amount))} to {payment.payee} from {payment.sourceAccountLabel}?
      </p>
      {error && <p className="form-error" role="alert">{error}</p>}
      {busy && <p role="status">Retrying payment…</p>}
      <div className="open-account-actions">
        <button type="button" className="button button-outline" autoFocus disabled={busy} onClick={onDismiss}>
          Cancel
        </button>
        <button type="button" className="button button-primary" disabled={busy} onClick={confirm}>
          Try Payment Again
        </button>
      </div>
    </dialog>
  );
}
