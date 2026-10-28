import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Wallet, ArrowLeftRight, ReceiptText, CreditCard, Settings, ShieldCheck } from 'lucide-react';
const links = [
  ['/dashboard','Overview',LayoutDashboard], ['/accounts','My Accounts',Wallet], ['/transactions','Transactions',ReceiptText],
  ['/transfers','Transfers & Payments',ArrowLeftRight], ['/cards','My Cards',CreditCard], ['/settings','Settings',Settings]
];
export default function Sidebar({ open, close }) {
  return <><div className={'sidebar-overlay '+(open?'show':'')} onClick={close}></div><aside className={'sidebar '+(open?'open':'')}>
    <p className="sidebar-label">MAIN MENU</p><nav>{links.map(([path,label,Icon]) => <NavLink key={path} to={path} onClick={close} className={({isActive})=>'side-link '+(isActive?'active':'')}><Icon size={19}/>{label}</NavLink>)}</nav>
    <div className="sidebar-bottom"><ShieldCheck size={20}/><div><strong>Secure demo</strong><small>Simulated banking experience</small></div></div>
  </aside></>;
}
