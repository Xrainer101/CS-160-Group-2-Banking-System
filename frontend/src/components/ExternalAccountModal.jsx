import { useEffect, useRef, useState } from 'react';
import { createExternalAccount } from '../api/bankingApi';

export default function ExternalAccountModal({ onClose, onSaved }) {
  const dialogRef = useRef(null);
  const saving = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement;
    dialog.showModal();
    return () => {
      dialog.close();
      previousFocus?.focus();
    };
  }, []);

  async function save(event) {
    event.preventDefault();
    if (saving.current) return;

    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    if (values.account_number !== values.confirm_account_number) {
      setError({ field: 'confirm_account_number', message: 'The account numbers do not match.' });
      form.elements.namedItem('confirm_account_number').focus();
      return;
    }

    saving.current = true;
    setSubmitting(true);
    setError(null);
    let account;
    try {
      account = await createExternalAccount(values);
    } catch (failure) {
      const detail = failure.detail;
      setError(Array.isArray(detail)
        ? { field: detail[0]?.loc?.at(-1), message: 'Check the highlighted account details and try again.' }
        : { field: detail?.field, message: detail?.message || failure.message || 'Unable to save the external account.' });
    } finally {
      saving.current = false;
      setSubmitting(false);
    }
    if (account) onSaved(account);
  }

  function fieldError(name) {
    return error?.field === name
      ? { 'aria-invalid': true, 'aria-describedby': 'external-account-error' }
      : {};
  }

  return (
    <dialog ref={dialogRef} className="open-account-dialog" aria-labelledby="external-account-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!saving.current) onClose();
      }}>
      <h2 id="external-account-title">Add external account</h2>
      <form className="stacked-form" onSubmit={save}>
        <label>
          Bank name
          <input name="bank_name" maxLength={120} required autoFocus {...fieldError('bank_name')} />
        </label>
        <label>
          Account holder name
          <input name="account_holder_name" maxLength={120} required {...fieldError('account_holder_name')} />
        </label>
        <label>
          Routing number
          <input name="routing_number" inputMode="numeric" pattern="[0-9]{9}" maxLength={9}
            title="Enter nine digits" required {...fieldError('routing_number')} />
        </label>
        <label>
          Account number
          <input name="account_number" inputMode="numeric" pattern="[0-9]{4,17}" maxLength={17}
            title="Enter four to 17 digits" required {...fieldError('account_number')} />
        </label>
        <label>
          Confirm account number
          <input name="confirm_account_number" inputMode="numeric" pattern="[0-9]{4,17}" maxLength={17}
            title="Enter four to 17 digits" required {...fieldError('confirm_account_number')} />
        </label>
        <label>
          Account type
          <select name="account_type" defaultValue="" required {...fieldError('account_type')}>
            <option value="" disabled>Select account type</option>
            <option value="checking">Checking</option>
            <option value="savings">Savings</option>
          </select>
        </label>
        <label>
          Nickname (optional)
          <input name="nickname" maxLength={80} {...fieldError('nickname')} />
        </label>
        {error && <p id="external-account-error" className="form-error" role="alert">{error.message}</p>}
        <div className="open-account-actions">
          <button type="button" className="button button-outline" disabled={submitting} onClick={onClose}>Cancel</button>
          <button type="submit" className="button button-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save external account'}
          </button>
        </div>
      </form>
    </dialog>
  );
}
