import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { createTransfer, getExternalAccounts } from '../api/bankingApi';
import ExternalAccountModal from '../components/ExternalAccountModal';
import { money } from '../data/mockData';

const label = (account) => `${account.type} ${account.number}`;
const pacificDate = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/Los_Angeles',
  year: 'numeric',
  month: 'numeric',
  day: 'numeric',
});

function tomorrowPacific() {
  const parts = pacificDate.formatToParts(new Date());
  const year = Number(parts.find((part) => part.type === 'year').value);
  const month = Number(parts.find((part) => part.type === 'month').value);
  const day = Number(parts.find((part) => part.type === 'day').value);
  return new Date(Date.UTC(year, month - 1, day + 1)).toISOString().slice(0, 10);
}

export default function Transfers(props) {
  const [params] = useSearchParams();
  const initialAccountId = params.get('accountId') || '';
  const returnTo = initialAccountId
    ? `/accounts/${encodeURIComponent(initialAccountId)}${params.get('from') === 'overview' ? '?from=overview' : ''}`
    : null;
  return <TransferForm key={initialAccountId} {...props} initialAccountId={initialAccountId} returnTo={returnTo} />;
}

function TransferForm({ accounts, demoTransfer, refreshAccounts, initialAccountId, returnTo }) {
  const [from, setFrom] = useState(initialAccountId);
  const [to, setTo] = useState('');
  const [kind, setKind] = useState('own_account');
  const [recipient, setRecipient] = useState('');
  const [externalId, setExternalId] = useState('');
  const [external, setExternal] = useState(null);
  const [externalError, setExternalError] = useState('');
  const [externalAttempt, setExternalAttempt] = useState(0);
  const [showExternalModal, setShowExternalModal] = useState(false);
  const [amount, setAmount] = useState('');
  const [timing, setTiming] = useState('now');
  const [date, setDate] = useState('');
  const [review, setReview] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [refreshError, setRefreshError] = useState(false);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);

  const active = accounts.filter((account) => demoTransfer || account.status === 'active');
  const source = active.find((account) => String(account.id) === from) || active[0];
  const ownTargets = active.filter((account) => account.id !== source?.id);
  const ownTarget = ownTargets.find((account) => String(account.id) === to) || ownTargets[0];
  const externalTarget = external?.find((account) => String(account.id) === externalId) || external?.[0];
  const minimumDate = tomorrowPacific();
  const hasTarget = kind === 'own_account' ? Boolean(ownTarget)
    : kind === 'external_account' ? Boolean(externalTarget) : true;

  useEffect(() => {
    if (kind !== 'external_account') return;
    const controller = new AbortController();
    getExternalAccounts(controller.signal)
      .then((items) => {
        if (!controller.signal.aborted) setExternal(items);
      })
      .catch(() => {
        if (!controller.signal.aborted) setExternalError('Could not load saved external accounts.');
      });
    return () => controller.abort();
  }, [kind, externalAttempt]);

  function chooseDestination(event) {
    setExternal(null);
    setExternalError('');
    setKind(event.target.value);
    setError('');
  }

  function prepareReview(event) {
    event.preventDefault();
    if (!source || !hasTarget) return;
    setError('');
    setResult(null);
    const value = amount.trim();
    if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(value) || !Number.isFinite(Number(value)) || Number(value) <= 0) {
      setError('Enter an amount greater than $0.00 with up to two decimal places.');
      return;
    }
    if (timing === 'now' && Number(value) > source.available) {
      setError(`That's more than the ${money(source.available)} available.`);
      return;
    }
    if (timing === 'date' && (!date || date < tomorrowPacific())) {
      setError('Choose a future date.');
      return;
    }

    let destination;
    let destinationLabel;
    if (kind === 'own_account') {
      destination = { kind, account_id: Number(ownTarget.id) };
      destinationLabel = label(ownTarget);
    } else if (kind === 'bank_customer') {
      const number = recipient.trim();
      if (!/^[0-9]{10}$/.test(number)) {
        setError('Enter their 10-digit account number.');
        return;
      }
      destination = { kind, account_number: number };
      destinationLabel = `Team 2 Bank account •••• ${number.slice(-4)}`;
    } else {
      destination = { kind, external_account_id: externalTarget.id };
      destinationLabel = `${externalTarget.name} ${externalTarget.number}`;
    }
    setReview({
      sourceLabel: label(source),
      destinationLabel,
      request: {
        source_account_id: demoTransfer ? source.id : Number(source.id),
        amount: value,
        destination: demoTransfer ? { ...destination, account_id: ownTarget.id } : destination,
        ...(timing === 'date' && { scheduled_for: date }),
      },
    });
  }

  async function confirmTransfer() {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError('');
    try {
      const { request } = review;
      if (demoTransfer) {
        const message = demoTransfer(request.source_account_id, request.destination.account_id, Number(request.amount));
        if (message) throw new Error(message);
        setResult({ status: 'completed' });
      } else {
        setResult(await createTransfer(request));
      }
      setReview(null);
      setAmount('');
      if (!demoTransfer) setRefreshError(!(await refreshAccounts()));
    } catch (failure) {
      setError(failure.detail?.message || (Array.isArray(failure.detail)
        ? 'Check your transfer details and try again.' : failure.message));
      setReview(null);
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  async function retryRefresh() {
    setBusy(true);
    setRefreshError(!(await refreshAccounts()));
    setBusy(false);
  }

  return (
    <>
      <div className="page-heading">
        {returnTo && <Link className="text-link" to={returnTo}>← Back to account details</Link>}
        <h1>Transfer money</h1>
      </div>
      <section className="panel">
        {demoTransfer && <p className="panel-subtitle">Demo transfers use sample balances.</p>}
        {result && (
          <p role="status">
            {result.status === 'pending'
              ? `Pending transfer saved for ${result.scheduled_for}. It will be processed the next time you open the signed-in site on or after that date.`
              : demoTransfer ? 'Demo transfer completed.' : 'Transfer completed.'}
          </p>
        )}
        {refreshError && (
          <p role="status">
            Account data could not refresh.{' '}
            <button type="button" className="button" disabled={busy} onClick={retryRefresh}>Refresh account data</button>
          </p>
        )}
        {error && <p className="form-error" role="alert">{error}</p>}
        {review ? (
          <div className="stacked-form">
            <h2>Review transfer</h2>
            <dl>
              <dt>From</dt><dd>{review.sourceLabel}</dd>
              <dt>To</dt><dd>{review.destinationLabel}</dd>
              <dt>Amount</dt><dd>{money(Number(review.request.amount))}</dd>
              <dt>When</dt><dd>{review.request.scheduled_for || 'Now'}</dd>
            </dl>
            {review.request.scheduled_for && <p>The transfer will be processed when you next open the signed-in site on or after the selected date.</p>}
            {review.request.destination.kind === 'external_account' && <p>Other-bank transfers are simulated.</p>}
            <button type="button" className="button button-primary" disabled={busy} onClick={confirmTransfer}>
              {busy ? 'Submitting…' : review.request.scheduled_for ? 'Save pending transfer' : 'Transfer now'}
            </button>
            <button type="button" className="button" disabled={busy} onClick={() => setReview(null)}>Edit transfer</button>
          </div>
        ) : (
          <form className="stacked-form" onSubmit={prepareReview}>
            {!source && <p className="empty">You need an active account to send money.</p>}
            <label>
              From account
              <select value={source?.id ?? ''} disabled={!source} onChange={(event) => setFrom(event.target.value)}>
                {active.map((account) => (
                  <option key={account.id} value={account.id}>{label(account)} — {money(account.available)} available</option>
                ))}
              </select>
            </label>
            {!demoTransfer && (
              <label>
                Send to
                <select value={kind} onChange={chooseDestination}>
                  <option value="own_account">My accounts</option>
                  <option value="bank_customer">Another Team 2 Bank customer</option>
                  <option value="external_account">An account at another bank</option>
                </select>
              </label>
            )}
            {kind === 'own_account' && (
              ownTarget ? (
                <label>
                  To account
                  <select value={ownTarget.id} onChange={(event) => setTo(event.target.value)}>
                    {ownTargets.map((account) => (
                      <option key={account.id} value={account.id}>{label(account)} — {money(account.available)} available</option>
                    ))}
                  </select>
                </label>
              ) : <p className="empty">You need another active account to transfer between your accounts.</p>
            )}
            {kind === 'bank_customer' && (
              <label>
                Recipient account number
                <input inputMode="numeric" maxLength={10} required value={recipient} onChange={(event) => setRecipient(event.target.value)} />
              </label>
            )}
            {kind === 'external_account' && (
              <>
                {externalError ? (
                  <div>
                    <p role="alert">{externalError}</p>
                    <button type="button" className="button" onClick={() => {
                      setExternalError('');
                      setExternalAttempt(attempt => attempt + 1);
                    }}>Retry</button>
                  </div>
                ) : externalTarget ? (
                  <label>
                    Saved external account
                    <select value={externalTarget.id} onChange={(event) => setExternalId(event.target.value)}>
                      {external.map((account) => <option key={account.id} value={account.id}>{account.name} {account.number}</option>)}
                    </select>
                  </label>
                ) : <p role="status">{external ? 'You have no saved external accounts.' : 'Loading saved external accounts…'}</p>}
                {external && (
                  <div>
                    <button type="button" className="button button-outline" onClick={() => setShowExternalModal(true)}>
                      Add external account
                    </button>
                  </div>
                )}
              </>
            )}
            <label>
              Amount (USD)
              <input inputMode="decimal" required placeholder="0.00" value={amount} onChange={(event) => setAmount(event.target.value)} />
            </label>
            {!demoTransfer && (
              <label>
                When
                <select value={timing} onChange={(event) => setTiming(event.target.value)}>
                  <option value="now">Now</option>
                  <option value="date">On a date</option>
                </select>
              </label>
            )}
            {timing === 'date' && (
              <>
                <label>
                  Date
                  <input type="date" min={minimumDate} required value={date} onChange={(event) => setDate(event.target.value)} />
                </label>
                <p>Funds are checked when the transfer runs, the next time you open the signed-in site on or after this date.</p>
              </>
            )}
            <button className="button button-primary" disabled={busy || !source || !hasTarget}>Review transfer</button>
          </form>
        )}
      </section>
      {showExternalModal && (
        <ExternalAccountModal
          onClose={() => setShowExternalModal(false)}
          onSaved={(account) => {
            setExternal((accounts) => [...accounts, account]);
            setExternalId(String(account.id));
            setShowExternalModal(false);
          }}
        />
      )}
    </>
  );
}
