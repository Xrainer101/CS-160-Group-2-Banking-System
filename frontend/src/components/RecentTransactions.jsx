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
export function TransactionRows({ transactions, renderAction }) {
  return (
    <div className="transaction-list">
      {!transactions?.length ? (
        <p className="empty">No transactions found.</p>
      ) : (
        transactions.map((t) => {
          const Icon = icons[t.icon] || CircleDollarSign;
          return (
            <div className={renderAction ? "transaction-row transaction-row-action" : "transaction-row"} key={t.id}>
              <span className="transaction-icon">
                <Icon size={19} />
              </span>
              <div className="transaction-details">
                <strong>{t.name}</strong>
                <small>
                  {t.category} · {t.date}
                  {t.status === 'pending' && ` · Pending${t.scheduled_for ? ` for ${t.scheduled_for.slice(0, 10)}` : ''}`}
                  {t.status === 'failed' && (t.failure_reason ? ` · Failed: ${t.failure_reason}` : ' · Failed')}
                  {t.status === 'cancelled' && ' · Cancelled'}
                </small>
              </div>
              <strong className={t.status && t.status !== 'completed' ? undefined : t.amount > 0 ? 'positive' : 'negative'}>
                {t.amount > 0 ? '+' : ''}
                {money(t.amount)}
              </strong>
              {renderAction?.(t)}
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
