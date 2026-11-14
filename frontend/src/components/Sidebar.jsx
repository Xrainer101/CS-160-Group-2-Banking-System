
import { NavLink } from "react-router-dom";
import {
  LayoutDashboard,
  Wallet,
  ArrowLeftRight,
  ReceiptText,
  CreditCard,
  Settings,
  ShieldCheck,
  ShieldUser,
  ChartNoAxesCombined,
  PiggyBank,
  CalendarRange,
  Repeat,
} from "lucide-react";

const links = [
  ["/dashboard", "Overview", LayoutDashboard],
  ["/accounts", "My Accounts", Wallet],
  ["/transactions", "Transactions", ReceiptText],
  ["/transfers", "Transfers & Payments", ArrowLeftRight],
  ["/cards", "My Cards", CreditCard],
];

export default function Sidebar({
  open,
  close,
  isAdmin = false,
}) {
  return (
    <>
      <div
        className={
          "sidebar-overlay " + (open ? "show" : "")
        }
        onClick={close}
      />

      <aside
        className={"sidebar " + (open ? "open" : "")}
      >
        <p className="sidebar-label">MAIN MENU</p>

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

          <p
            className="sidebar-label"
            style={{ marginTop: 25 }}
          >
            ADMINISTRATION
          </p>

          <NavLink
            to={isAdmin ? "/admin" : "/admin-preview"}
            onClick={close}
            className={({ isActive }) =>
              "side-link " +
              (isActive ? "active" : "")
            }
          >
            <ShieldUser size={19} />
            {isAdmin
              ? "Admin Portal"
              : "Admin Preview"}
          </NavLink>
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
