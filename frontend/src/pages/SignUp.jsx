import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Landmark, ShieldCheck } from 'lucide-react';
export default function SignUp({ onSignIn }) {
  const [name, setName] = useState(''),
    [username, setUsername] = useState(''),
    [email, setEmail] = useState(''),
    [dateOfBirth, setDateOfBirth] = useState(''),
    [phone, setPhone] = useState(''),
    [password, setPassword] = useState(''),
    [confirm, setConfirm] = useState(''),
    [error, setError] = useState('');
  function submit(e) {
    e.preventDefault();
    if (password.length < 8) {
      setError('Use at least 8 characters for this demo form.');
      return;
    }
    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    onSignIn(email);
  }
  return (
    <div className="auth-page">
      <div className="auth-promo">
        <div className="auth-brand">
          <Landmark /> Team 2 Bank
        </div>
        <div>
          <span className="auth-overline">GET STARTED</span>
          <h1>A clearer way to see your financial picture.</h1>
          <p>
            Explore our banking prototype with sample accounts and simulated
            transactions.
          </p>
          <div className="auth-feature">
            <ShieldCheck /> No real accounts are created
          </div>
        </div>
        <small>Illustrative data. Not a real financial institution.</small>
      </div>
      <div className="auth-main">
        <div className="auth-card">
          <span className="eyebrow">GET STARTED</span>
          <h2>Open an account</h2>
          <p>Fill in your details to set up online banking.</p>
          <form className="stacked-form" onSubmit={submit}>
            <label>
              Full name
              <input
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Your name"
              />
            </label>
            <label>
              Date of birth
              <input
                required
                type="date"
                value={dateOfBirth}
                onChange={(e) => setDateOfBirth(e.target.value)}
              />
            </label>
            <label>
              Email address
              <input
                required
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@email.com"
              />
            </label>
            <label>
              Phone number
              <input
                required
                type="tel"
                autoComplete="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="(555) 000-0000"
              />
            </label>
            <label>
              Username
              <input
                required
                type="text"
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Choose a username"
              />
            </label>
            <label>
              Password
              <input
                required
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="At least 8 characters"
              />
            </label>
            <label>
              Confirm password
              <input
                required
                type="password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                placeholder="Re-enter your password"
              />
            </label>
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            <button className="button button-primary full">
              Create account
            </button>
          </form>
          <p className="auth-switch">
            Already have an account? <Link to="/signin">Sign in</Link>
          </p>
          <div className="auth-disclaimer">
            Never enter a real banking password in this prototype.
          </div>
        </div>
      </div>
    </div>
  );
}
