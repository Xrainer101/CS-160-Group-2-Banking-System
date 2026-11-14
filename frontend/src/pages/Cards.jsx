import { CreditCard, Lock, Unlock, Wifi } from 'lucide-react';
export default function Cards({ frozen, setFrozen, notify }) {
  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">CARD MANAGEMENT</span>
        <h1>My cards</h1>
        <p>Manage your sample debit card and preferences.</p>
      </div>
      <div className="two-column">
        <div className="virtual-card">
          <div className="virtual-card-top">
            <span>TEAM 2 BANK</span>
            <Wifi size={26} />
          </div>
          <CreditCard size={36} />
          <div className="virtual-number">
            •••• &nbsp; •••• &nbsp; •••• &nbsp; 2048
          </div>
          <div className="virtual-card-bottom">
            <span>
              SACHI K<br />
              <small>CARDHOLDER</small>
            </span>
            <strong>DEBIT</strong>
          </div>
        </div>
        <section className="panel">
          <h2>Card controls</h2>
          <p className="panel-subtitle">
            These controls are for demonstration only.
          </p>
          <div className="card-control">
            <div>
              <strong>{frozen ? 'Card is frozen' : 'Card is active'}</strong>
              <p>
                {frozen
                  ? 'Purchases would be blocked in a real banking app.'
                  : 'Freeze your card temporarily using the button.'}
              </p>
            </div>
            <button
              className="button button-outline"
              onClick={() => setFrozen((v) => !v)}
            >
              {frozen ? <Unlock size={17} /> : <Lock size={17} />}{' '}
              {frozen ? 'Unfreeze' : 'Freeze'}
            </button>
          </div>
          <button
            className="button button-outline full"
            onClick={() => notify('Card replacement request is a demo only.')}
          >
            Request replacement card
          </button>
        </section>
      </div>
    </>
  );
}
