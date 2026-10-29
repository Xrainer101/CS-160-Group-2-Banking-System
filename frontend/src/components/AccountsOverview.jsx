import { Link } from 'react-router-dom';
import AccountCard from './AccountCard';
export default function AccountsOverview({ accounts }) {
  return (
    <section className="section">
      <div className="section-heading">
        <div>
          <span className="eyebrow">YOUR MONEY</span>
          <h2>Your accounts</h2>
        </div>
        <Link className="text-link" to="/accounts">
          View details →
        </Link>
      </div>
      <div className="accounts-grid">
        {accounts.map((a) => (
          <AccountCard key={a.id} account={a} />
        ))}
      </div>
    </section>
  );
}
