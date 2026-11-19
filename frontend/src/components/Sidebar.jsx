import { NavLink } from "react-router-dom";

import {
  LayoutDashboard,
  Wallet,
  ArrowLeftRight,
  DollarSign,
  ArrowDown,
  ArrowUp,
  ListFilter,
  CreditCard,
  ShieldCheck,
  ShieldUser,
  MapPinned,
  Camera,
  SlidersHorizontal,
} from "lucide-react";

const links = [
  ["/dashboard", "Overview", LayoutDashboard],
  ["/accounts", "My Accounts", Wallet],
  ["/transactions", "Transactions", ListFilter],
  ["/transfers", "Transfers", ArrowLeftRight],
  ["/payments", "Pay Bills", DollarSign],
  ["/deposit", "Deposit", ArrowDown],
  ["/check-deposit", "Mobile Check Deposit", Camera],
  ["/withdraw", "Withdraw", ArrowUp],
  ["/atm-finder", "Find Chase ATMs", MapPinned],
  ["/cards", "My Cards", CreditCard],
  ["/settings", "Settings", SlidersHorizontal],
];

export default function Sidebar({
  open,
  close,
  canViewManagerReports = false,
}) {
  return (
    <>
      <div
        className={
          "sidebar-overlay " +
          (open ? "show" : "")
        }
        onClick={close}
      />

      <aside
        className={
          "sidebar " +
          (open ? "open" : "")
        }
      >
        <p className="sidebar-label">
          MAIN MENU
        </p>

        <nav>
          {links.map(([path, label, Icon]) => (
            <NavLink
              key={path}
              to={path}
              onClick={close}
              className={({ isActive }) =>
                "side-link " +
                (isActive ? "active" : "")
              }
            >
              <Icon size={19} />
              {label}
            </NavLink>
          ))}

          {canViewManagerReports && (
              <>
                <p
                  className="sidebar-label"
                  style={{ marginTop: 25 }}
                >
                  ADMINISTRATION
                </p>

                <NavLink
                  to="/manager-dashboard"
                  onClick={close}
                  className={({ isActive }) =>
                    "side-link " +
                    (isActive ? "active" : "")
                  }
                >
                  <ShieldUser size={19} />
                  Admin Portal
                </NavLink>
              </>
            )}
        </nav>

        <div className="sidebar-bottom">
          <ShieldCheck size={20} />
          <div>
            <strong>Secure demo</strong>
            <small>
              Academic banking application
            </small>
          </div>
        </div>
      </aside>
    </>
  );
}
