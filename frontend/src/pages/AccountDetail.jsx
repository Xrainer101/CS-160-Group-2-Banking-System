import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { Wallet } from 'lucide-react';
import { getAccountDetail } from '../api/bankingApi';
import { TransactionRows } from '../components/RecentTransactions';
import { money } from '../data/mockData';
import CloseAccountModal from '../components/CloseAccountModal';

export default function AccountDetail({ onCloseAccount }) {
  const { accountId } = useParams();
  return <AccountDetailContent key={accountId} accountId={accountId} onCloseAccount={onCloseAccount} />;
}

function AccountDetailContent({ accountId, onCloseAccount }) {
  const [params] = useSearchParams();
  const fromOverview = params.get('from') === 'overview';
  const source = fromOverview ? '&from=overview' : '';
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [showNumber, setShowNumber] = useState(false);
  const [showCloseModal, setShowCloseModal] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;
    getAccountDetail(accountId, controller.signal)
      .then(result => { if (!cancelled) setData(result); })
      .catch(failure => {
        if (!cancelled) setError(failure.message || 'Unable to load account.');
      });
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [accountId]);

  return (
    <>
      <div className="page-heading">
        <Link className="text-link" to={fromOverview ? "/dashboard" : "/accounts"}>
          {fromOverview ? "← Back to overview" : "← Back to accounts"}
        </Link>
        <h1>Account details</h1>
      </div>
      {error ? <p role="alert">{error}</p> : !data ? (
        <p role="status">Loading your account...</p>
      ) : (
        <>
          <article className={'account-card account-detail-card ' + data.account.color}
            aria-label={data.account.type}>
            <div className="account-card-top">
              <span className="account-icon"><Wallet size={19} /></span>
              <span>{data.account.type}</span>
            </div>
            <div>
              <span className="muted-light">Current balance</span>
              <h2>{money(data.account.balance)}</h2>
              <span className="muted-light">Available: {money(data.account.available)}</span>
            </div>
            <div className="account-detail-numbers">
              <div className="account-detail-number-row">
                <div>
                  <span className="muted-light">Account number</span>
                  <strong id="account-number-value">
                    {showNumber ? data.account.account_number : data.account.number}
                  </strong>
                </div>
                <button type="button" className="account-number-toggle"
                  aria-controls="account-number-value" aria-pressed={showNumber}
                  aria-label={showNumber ? 'Hide account number' : 'Show account number'}
                  onClick={() => setShowNumber(value => !value)}>
                  {showNumber ? 'Hide' : 'Show'}
                </button>
              </div>
              <div className="account-detail-routing">
                <span className="muted-light">Routing number</span>
                <strong>{data.account.routing_number}</strong>
              </div>
            </div>
          </article>
          <nav className="account-detail-actions" aria-label="Account actions">
            <Link className="button button-outline" to={`/deposit?accountId=${data.account.id}${source}`}>Deposit</Link>
            <Link className="button button-outline" to={`/withdraw?accountId=${data.account.id}${source}`}>Withdraw</Link>
            <Link className="button button-outline" to={`/transfers?accountId=${data.account.id}${source}`}>Transfer</Link>
          </nav>
          <section className="panel">
            <h2>Latest activity</h2>
            <TransactionRows transactions={data.transactions} />
          </section>
          <div className="close-account-footer">
            <button type="button" className="close-account-link"
              onClick={() => setShowCloseModal(true)}>Close this account</button>
          </div>
          {showCloseModal && (
            <CloseAccountModal account={data.account}
              onDismiss={() => setShowCloseModal(false)}
              onConfirm={() => onCloseAccount(accountId)} />
          )}
        </>
      )}
    </>
  );
}
