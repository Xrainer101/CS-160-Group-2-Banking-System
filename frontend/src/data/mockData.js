export const initialAccounts = [
  {
    id: 'checking',
    type: 'Everyday Checking',
    number: '•••• 2048',
    balance: 8240.1,
    available: 8120.1,
    color: 'blue',
  },
  {
    id: 'savings',
    type: 'High-Yield Savings',
    number: '•••• 7391',
    balance: 4240.15,
    available: 4240.15,
    color: 'purple',
  },
];
export const initialTransactions = [
  {
    id: 1,
    name: "Trader Joe's",
    category: 'Groceries',
    date: 'Oct 25, 2026',
    amount: -42.63,
    account: 'checking',
    icon: 'ShoppingCart',
  },
  {
    id: 2,
    name: 'Direct Deposit',
    category: 'Income',
    date: 'Oct 24, 2026',
    amount: 1850,
    account: 'checking',
    icon: 'ArrowDownLeft',
  },
  {
    id: 3,
    name: 'PG&E',
    category: 'Utilities',
    date: 'Oct 23, 2026',
    amount: -94.21,
    account: 'checking',
    icon: 'Zap',
  },
  {
    id: 4,
    name: 'Coffee Shop',
    category: 'Dining',
    date: 'Oct 22, 2026',
    amount: -8.5,
    account: 'checking',
    icon: 'Coffee',
  },
  {
    id: 5,
    name: 'Savings Interest',
    category: 'Interest',
    date: 'Oct 20, 2026',
    amount: 12.45,
    account: 'savings',
    icon: 'TrendingUp',
  },
  {
    id: 6,
    name: 'Streaming Subscription',
    category: 'Entertainment',
    date: 'Oct 18, 2026',
    amount: -15.99,
    account: 'checking',
    icon: 'Play',
  },
];
export const initialPayments = [
  { id: 1, name: 'Apartment Rent', date: 'Nov 1', amount: 1450 },
  { id: 2, name: 'Internet', date: 'Nov 5', amount: 65 },
  { id: 3, name: 'Phone', date: 'Nov 10', amount: 48.99 },
];
export const money = (amount) =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(
    amount,
  );
