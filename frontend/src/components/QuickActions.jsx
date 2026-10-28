import { Link } from 'react-router-dom';
import { ArrowLeftRight, ReceiptText, CreditCard, ListFilter } from 'lucide-react';
const actions = [['Transfer money','/transfers',ArrowLeftRight],['Pay a bill','/transfers',ReceiptText],['Manage cards','/cards',CreditCard],['View activity','/transactions',ListFilter]];
export default function QuickActions() { return <section className="section"><div className="section-heading"><div><span className="eyebrow">SHORTCUTS</span><h2>Quick actions</h2></div></div><div className="quick-actions-grid">{actions.map(([label,path,Icon])=><Link className="quick-action-card" key={label} to={path}><span className="quick-icon"><Icon size={22}/></span><strong>{label}</strong><span className="quick-arrow">↗</span></Link>)}</div></section>; }
