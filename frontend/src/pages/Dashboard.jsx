import { ArrowUpRight, PiggyBank, Wallet } from 'lucide-react';
import WelcomeSection from '../components/WelcomeSection';
import AccountsOverview from '../components/AccountsOverview';
import QuickActions from '../components/QuickActions';
import RecentTransactions from '../components/RecentTransactions';
import UpcomingPayments from '../components/UpcomingPayments';
import StatCard from '../components/StatCard';
import { money } from '../data/mockData';
export default function Dashboard({ user, accounts, transactions, payments }) {
 const total=accounts.reduce((sum,a)=>sum+a.balance,0);
 const spent=transactions.filter(t=>t.amount<0&&t.category!=='Transfer').reduce((sum,t)=>sum-Math.abs(t.amount),0);
 return <><WelcomeSection user={user}/><div className="stats-grid"><StatCard label="Total balance" value={money(total)} icon={Wallet} note="Across all accounts"/><StatCard label="Available savings" value={money(accounts.find(a=>a.id==='savings')?.available||0)} icon={PiggyBank} note="Your savings account"/><StatCard label="Recent spending" value={money(Math.abs(spent))} icon={ArrowUpRight} note="Based on demo transactions"/></div><AccountsOverview accounts={accounts}/><QuickActions/><div className="dashboard-grid"><RecentTransactions transactions={transactions}/><UpcomingPayments payments={payments}/></div></>;
}
