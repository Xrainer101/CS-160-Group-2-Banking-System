import { Bell, Menu, LogOut, Landmark } from 'lucide-react';
export default function Navbar({ user, onMenu, onSignOut }) {
  return (
    <header className="topbar">
      <div className="topbar-brand">
        <button
          className="icon-button mobile-menu"
          onClick={onMenu}
          aria-label="Toggle navigation"
        >
          <Menu size={20} />
        </button>
        <span className="logo-mark">
          <Landmark size={21} />
        </span>
        <div>
          <strong>Team 2 Bank</strong>
          <small>Digital Banking</small>
        </div>
      </div>
      <div className="topbar-right">
        <span className="demo-pill">DEMO MODE</span>
        <button
          className="icon-button"
          aria-label="Notifications"
          title="Demo notifications"
        >
          <Bell size={19} />
        </button>
        <span className="avatar">{user.name.slice(0, 1)}</span>
        <span className="topbar-name">{user.name}</span>
        <button
          className="icon-button"
          title="Sign out"
          aria-label="Sign out"
          onClick={onSignOut}
        >
          <LogOut size={19} />
        </button>
      </div>
    </header>
  );
}
