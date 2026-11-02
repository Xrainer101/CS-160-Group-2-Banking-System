import { useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { TransactionRows } from '../components/RecentTransactions';
export default function Transactions({ transactions, accounts }) {
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all');
  const [account, setAccount] = useState('all');
  const results = useMemo(
    () =>
      transactions.filter(
        (t) =>
          (t.name + ' ' + t.category)
            .toLowerCase()
            .includes(search.toLowerCase()) &&
          (filter === 'all' ||
            (filter === 'income' ? t.amount > 0 : t.amount < 0)) &&
          (account === 'all' || t.account === account),
      ),
    [transactions, search, filter, account],
  );
  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">ACTIVITY</span>
        <h1>Transactions</h1>
        <p>Search and filter your demo transaction history.</p>
      </div>
      <section className="panel">
        <div className="filter-bar">
          <label className="search-box">
            <Search size={18} />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search transactions"
              aria-label="Search transactions"
            />
          </label>
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            aria-label="Transaction type"
          >
            <option value="all">All activity</option>
            <option value="income">Money in</option>
            <option value="expense">Money out</option>
          </select>
          <select
            value={account}
            onChange={(e) => setAccount(e.target.value)}
            aria-label="Account"
          >
            <option value="all">All accounts</option>
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.type}
              </option>
            ))}
          </select>
        </div>
        <TransactionRows transactions={results} />
      </section>
    </>
  );
}
