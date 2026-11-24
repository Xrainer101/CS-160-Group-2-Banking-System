
import { Link } from "react-router-dom";

import {
  ArrowLeftRight,
  DollarSign,
  ArrowDown,
  ArrowUp,
  ListFilter,
  MapPinned,
} from "lucide-react";

const actions = [
  [
    "Deposit",
    "/deposit",
    ArrowDown,
    "Add money to an account",
  ],
  [
    "Withdraw",
    "/withdraw",
    ArrowUp,
    "Take money out",
  ],
  [
    "Find Chase ATMs",
    "/atm-finder",
    MapPinned,
    "Explore nearby locations",
  ],
  [
    "Transfer money",
    "/transfers",
    ArrowLeftRight,
    "Between accounts or people",
  ],
  [
    "Pay a bill",
    "/payments",
    DollarSign,
    "Schedule a payment",
  ],
  [
    "View activity",
    "/transactions",
    ListFilter,
    "Search your history",
  ],
];

export default function QuickActions() {
  return (
    <section className="section">
      <div className="section-heading">
        <div>
          <span className="eyebrow">
            SHORTCUTS
          </span>
          <h2>Quick actions</h2>
        </div>
      </div>

      <div className="quick-actions-grid">
        {actions.map(
          ([label, path, Icon, description]) => (
            <Link
              className="quick-action-card"
              key={label}
              to={path}
            >
              <span className="quick-icon">
                <Icon size={22} />
              </span>

              <div className="quick-action-text">
                <strong>{label}</strong>
                <small>{description}</small>
              </div>

              <span className="quick-arrow">
                ↗
              </span>
            </Link>
          )
        )}
      </div>
    </section>
  );
}
