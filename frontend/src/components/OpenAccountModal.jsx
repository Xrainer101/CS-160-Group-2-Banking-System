import { useEffect, useRef, useState } from 'react';

export default function OpenAccountModal({ onClose, onCreate, submitting }) {
  const dialogRef = useRef(null);
  const pendingRef = useRef(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const previousFocus = document.activeElement;
    const dialog = dialogRef.current;
    dialog.showModal();
    return () => {
      dialog.close();
      previousFocus?.focus();
    };
  }, []);

  async function chooseAccount(type) {
    if (pendingRef.current || submitting) return;
    pendingRef.current = true;
    setError('');
    try {
      if (await onCreate(type)) onClose();
    } catch (failure) {
      setError(failure.message || 'Unable to create the account.');
    } finally {
      pendingRef.current = false;
    }
  }

  return (
    <dialog ref={dialogRef} className="open-account-dialog"
      aria-labelledby="open-account-title" aria-describedby="open-account-description"
      onCancel={(event) => {
        event.preventDefault();
        if (!submitting && !pendingRef.current) onClose();
      }}>
      <h2 id="open-account-title">Open New Account</h2>
      <p id="open-account-description">
        Choose an account type to open it with a $0.00 balance.
      </p>
      {error && <p role="alert">{error}</p>}
      {submitting && <p role="status">Creating your account...</p>}
      <div className="open-account-actions">
        <button type="button" className="button button-primary" disabled={submitting}
          onClick={() => chooseAccount('checking')}>Checking</button>
        <button type="button" className="button button-primary" disabled={submitting}
          onClick={() => chooseAccount('savings')}>Savings</button>
        <button type="button" disabled={submitting} onClick={onClose}>Cancel</button>
      </div>
    </dialog>
  );
}
