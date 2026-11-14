import { ArrowUpRight, Wallet } from 'lucide-react';
import { Link } from 'react-router-dom';
import { money } from '../data/mockData';
export default function AccountCard({ account }) {
  return (
    <article className={'account-card ' + account.color}>
      <div className="account-card-top">
        <span className="account-icon">
          <Wallet size={19} />
        </span>
        <span className="account-number">{account.number}</span>
      </div>
      <div>
        <span className="muted-light">{account.type}</span>
        <h3>{money(account.balance)}</h3>
        <span className="muted-light">
          Available: {money(account.available)}
        </span>
      </div>
      <Link to="/accounts" className="account-card-link">
        View account <ArrowUpRight size={17} />
      </Link>
    </article>
  );
}
