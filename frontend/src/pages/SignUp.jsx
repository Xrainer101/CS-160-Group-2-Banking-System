import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Landmark, ShieldCheck } from 'lucide-react';
import { QRCodeCanvas } from 'qrcode.react';
import {login, signup as createAuth0User,startMfaEnrollment, verifyMfaEnrollment} from '../auth/authService';
export default function SignUp({ onAuthenticated }) {
  const [name, setName] = useState(''),
    [dateOfBirth, setDateOfBirth] = useState(''),
    [email, setEmail] = useState(''),
    [phone, setPhone] = useState(''),
    [username, setUsername] = useState(''),
    [password, setPassword] = useState(''),
    [confirm, setConfirm] = useState(''),
    [mfaToken, setMfaToken] = useState(''),
    [mfaCode, setMfaCode] = useState(''),
    [qrCode, setQrCode] = useState(''),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(false);
  const passwordRequirements = [
    { label: 'At least 8 characters', met: password.length >= 8 },
    { label: 'One uppercase letter', met: /[A-Z]/.test(password) },
    { label: 'One lowercase letter', met: /[a-z]/.test(password) },
    { label: 'One number', met: /\d/.test(password) },
    { label: 'One special character', met: /[^A-Za-z0-9]/.test(password) },
  ];
  const passwordRequirementsMet = passwordRequirements.every(
    (requirement) => requirement.met,
  );
  async function submit(e) {
    e.preventDefault();
    setError('');
    if (!passwordRequirementsMet) {
      setError('Meet all password requirements before continuing.');
      return;
    }
    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    const nameParts = name.trim().split(/\s+/).filter(Boolean);
    const validName =
      nameParts.length >= 2 &&
      nameParts.every((part) => /^[A-Za-zÀ-ÖØ-öø-ÿ'’-]+$/.test(part));
    if (!validName) {
      setError('Enter a valid first and last name.');
      return;
    }
    setLoading(true);
    try {
      if (mfaToken) {
        const result = await verifyMfaEnrollment(mfaToken, mfaCode);
        onAuthenticated(result);
        return;
      }

      await createAuth0User({
        fullName: name,
        dateOfBirth,
        email,
        phone,
        username,
        password,
      });

      const result = await login(username, password);
      if (result.requiresMfa) {
        const enrollment = await startMfaEnrollment(result.mfaToken);
        setMfaToken(result.mfaToken);
        setQrCode(enrollment.barcode_uri);
      } else {
        onAuthenticated(result);
      }
    } catch (err) {
      if (
        err.code === 'user_exists' ||
        err.code === 'username_exists' ||
        err.code === 'invalid_signup'
      ) {
        setError('An account with this email or username already exists.');
      } else {
        setError(err.message || 'Unable to create the account.');
      }
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
                placeholder="First name, Last name"></input>
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
            <div className="password-requirements" aria-live="polite">
              {passwordRequirements.map((requirement) => (
                <label
                  className={`password-requirement${requirement.met ? ' met' : ''}`}
                  key={requirement.label}
                >
                  <input type="checkbox" checked={requirement.met} readOnly />
                  {requirement.label}
                </label>
              ))}
            </div>
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
            {mfaToken && (
              <>
                {qrCode && (
                  <div className="mfa-enrollment">
                    <p>1. Download and open the Duo Mobile app.</p>
                    <p>2. Tap Add + , and select "Use QR Code"</p>
                    <p>3. Scan the QR code below. Name account.</p>
                    <p>4. Enter the six-digit code from Duo mobile below.</p>
                    <QRCodeCanvas value={qrCode} size={220} includeMargin />
                  </div>
                )}
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
              </>
            )}
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            <button
              className="button button-primary full"
              disabled={loading || !passwordRequirementsMet}
            >
              {loading
                ? 'Please wait...'
                : mfaToken
                  ? 'Complete enrollment'
                  : 'Create account'}
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
