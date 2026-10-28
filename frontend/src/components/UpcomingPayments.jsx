import { Link } from 'react-router-dom';
import { CalendarDays } from 'lucide-react';
import { money } from '../data/mockData';
export default function UpcomingPayments({ payments }) { return <section className="panel"><div className="section-heading"><div><span className="eyebrow">COMING UP</span><h2>Upcoming payments</h2></div><Link className="text-link" to="/transfers">Manage →</Link></div><div className="payments-list">{payments.slice(0,4).map(p=><div className="payment-row" key={p.id}><span className="payment-icon"><CalendarDays size={20}/></span><div className="payment-info"><strong>{p.name}</strong><small>{p.date}</small></div><strong>{money(p.amount)}</strong></div>)}</div><Link to="/transfers" className="button button-outline full">Schedule a payment</Link></section>; }
