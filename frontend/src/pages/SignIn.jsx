import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Landmark, ShieldCheck, Eye, EyeOff } from 'lucide-react';
import { login, verifyMfa } from '../auth/authService';
export default function SignIn({ onAuthenticated }) {
  const [username, setUsername] = useState(''),
    [password, setPassword] = useState(''),
    [show, setShow] = useState(false),
    [mfaToken, setMfaToken] = useState(''),
    [mfaCode, setMfaCode] = useState(''),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(false);
  async function submit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (mfaToken) {
        const result = await verifyMfa(mfaToken, mfaCode);
        onAuthenticated(result);
        return;
      }
      const result = await login(username, password);
      if (result.requiresMfa) {
        setMfaToken(result.mfaToken);
      } else {
        onAuthenticated(result);
      }
    } catch (err) {
      setError(err.message || 'Unable to sign in.');
    } finally {
      setLoading(false);
    }
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
          <h2>Log in to your account</h2>
          <p>Check your balances, move money and pay bills.</p>
          <form className="stacked-form" onSubmit={submit}>
            <label>
              Username
              <input
                type="text"
                required
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Enter your username"
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
                  placeholder="Enter your password"
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
              {/*
              <label className="inline-check">
                <input type="checkbox" /> Remember me
              </label>
              */}
              <button type="button" className="forgot-password">
                Forgot password?
              </button>
            </div>
            {mfaToken && (
              <label>
                Duo verification code
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  maxLength="6"
                  required
                  value={mfaCode}
                  onChange={(e) => setMfaCode(e.target.value)}
                  placeholder="Enter your six-digit code"
                />
              </label>
            )}
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            <button className="button button-primary full" disabled={loading}>
              {loading
                ? 'Please wait...'
                : mfaToken
                  ? 'Verify code'
                  : 'Sign in'}
            </button>
          </form>
          <p className="auth-switch">
            Don't have an account? <Link to="/signup">Register</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
