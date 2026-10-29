import { Link } from 'react-router-dom';
import {
  ArrowDownLeft,
  ArrowLeftRight,
  ShoppingCart,
  Zap,
  Coffee,
  TrendingUp,
  Play,
  CircleDollarSign,
} from 'lucide-react';
import { money } from '../data/mockData';
const icons = {
  ArrowDownLeft,
  ArrowLeftRight,
  ShoppingCart,
  Zap,
  Coffee,
  TrendingUp,
  Play,
};
export function TransactionRows({ transactions }) {
  return (
    <div className="transaction-list">
      {transactions.length === 0 ? (
        <p className="empty">No transactions found.</p>
      ) : (
        transactions.map((t) => {
          const Icon = icons[t.icon] || CircleDollarSign;
          return (
            <div className="transaction-row" key={t.id}>
              <span className="transaction-icon">
                <Icon size={19} />
              </span>
              <div className="transaction-details">
                <strong>{t.name}</strong>
                <small>
                  {t.category} · {t.date}
                </small>
              </div>
              <strong className={t.amount > 0 ? 'positive' : 'negative'}>
                {t.amount > 0 ? '+' : ''}
                {money(t.amount)}
              </strong>
            </div>
          );
        })
      )}
    </div>
  );
}
export default function RecentTransactions({ transactions }) {
  return (
    <section className="panel">
      <div className="section-heading">
        <div>
          <span className="eyebrow">ACTIVITY</span>
          <h2>Recent transactions</h2>
        </div>
        <Link className="text-link" to="/transactions">
          View all →
        </Link>
      </div>
      <TransactionRows transactions={transactions.slice(0, 5)} />
    </section>
  );
}
