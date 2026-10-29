import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Landmark, ShieldCheck, Eye, EyeOff } from 'lucide-react';
export default function SignIn({ onSignIn }) {
  const [email, setEmail] = useState('demo@team2bank.com'),
    [password, setPassword] = useState(''),
    [show, setShow] = useState(false);
  function submit(e) {
    e.preventDefault();
    if (email && password) onSignIn(email, password);
  }
  return (
    <div className="auth-page">
      <div className="auth-promo">
        <div className="auth-brand">
          <Landmark /> Team 2 Bank
        </div>
        <div>
          <span className="auth-overline">BANKING, SIMPLIFIED</span>
          <h1>Everything you need to manage your money.</h1>
          <p>
            One simple place for your accounts, transfers, cards and spending
            activity.
          </p>
          <div className="auth-feature">
            <ShieldCheck /> Built for an academic frontend demonstration
          </div>
        </div>
        <small>Illustrative data. Not a real financial institution.</small>
      </div>
      <div className="auth-main">
        <div className="auth-card">
          <span className="eyebrow">WELCOME BACK</span>
          <h2>Sign in to your account</h2>
          <p>Enter any email and password to explore the demo.</p>
          <form className="stacked-form" onSubmit={submit}>
            <label>
              Email address
              <input
                // type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
              />
            </label>
            <label>
              Password
              <div className="password-field">
                <input
                  type={show ? 'text' : 'password'}
                  required
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter any demo password"
                />
                <button
                  type="button"
                  onClick={() => setShow((v) => !v)}
                  aria-label={show ? 'Hide password' : 'Show password'}
                >
                  {show ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </label>
            <div className="auth-options">
              <label className="inline-check">
                <input type="checkbox" /> Remember me
              </label>
              <span>Demo access</span>
            </div>
            <button className="button button-primary full">Sign in</button>
          </form>
          <p className="auth-switch">
            New here? <Link to="/signup">Create an account</Link>
          </p>
          <div className="auth-disclaimer">
            Demo only: no passwords are stored or verified. Do not enter a real
            password.
          </div>
        </div>
      </div>
    </div>
  );
}
