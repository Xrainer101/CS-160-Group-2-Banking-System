
import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Landmark,
  ShieldCheck,
  Eye,
  EyeOff,
} from "lucide-react";

export default function SignIn({ onSignIn }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);

  // New states for backend authentication
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function submit(e) {
    e.preventDefault();

    // Clear previous errors
    setError("");

    // Validate input
    if (!username.trim() || !password) {
      setError("Please enter your username and password.");
      return;
    }

    setLoading(true);

    try {
      // Calls the signIn function in App.jsx,
      // which communicates with the backend.
      await onSignIn(username.trim(), password);

      // App.jsx handles navigation after successful login.
    } catch (err) {
      console.error("Login failed:", err);

      // Display the backend error to the user
      setError(
        err.message ||
          "Unable to sign in. Please try again."
      );

      // Clear password after unsuccessful login
      setPassword("");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      {/* Left side: Branding and promotional content */}
      <div className="auth-promo">
        <div className="auth-brand">
          <Landmark />
          Team 2 Bank
        </div>

        <div>
          <span className="auth-overline">
            BANKING, SIMPLIFIED
          </span>

          <h1>
            Everything you need to manage your money.
          </h1>

          <p>
            One simple place for your accounts,
            transfers, cards and spending activity.
          </p>

          <div className="auth-feature">
            <ShieldCheck />
            Built for an academic frontend demonstration
          </div>
        </div>

        <small>
          Illustrative data. Not a real financial
          institution.
        </small>
      </div>

      {/* Right side: Sign-in form */}
      <div className="auth-main">
        <div className="auth-card">
          <span className="eyebrow">
            WELCOME BACK
          </span>

          <h2>Log in to your account</h2>

          <p>
            Check your balances, move money and pay bills.
          </p>

          <form
            className="stacked-form"
            onSubmit={submit}
          >
            {/* Username */}
            <label>
              Username

              <input
                type="text"
                required
                autoComplete="username"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setError("");
                }}
                placeholder="Enter your username"
                disabled={loading}
              />
            </label>

            {/* Password */}
            <label>
              Password

              <div className="password-field">
                <input
                  type={show ? "text" : "password"}
                  required
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    setError("");
                  }}
                  placeholder="Enter your password"
                  disabled={loading}
                />

                <button
                  type="button"
                  onClick={() => setShow((v) => !v)}
                  aria-label={
                    show
                      ? "Hide password"
                      : "Show password"
                  }
                  disabled={loading}
                >
                  {show ? (
                    <EyeOff size={18} />
                  ) : (
                    <Eye size={18} />
                  )}
                </button>
              </div>
            </label>

            {/* Password recovery */}
            <div className="auth-options">
              <Link
                to="/forgot-password"
                className="forgot-password"
              >
                Forgot password?
              </Link>
            </div>

            {/* Authentication error */}
            {error && (
              <p
                className="form-error"
                role="alert"
              >
                {error}
              </p>
            )}

            {/* Submit */}
            <button
              type="submit"
              className="button button-primary full"
              disabled={loading}
            >
              {loading
                ? "Signing in..."
                : "Sign in"}
            </button>
          </form>

          {/* Registration link */}
          <p className="auth-switch">
            Don't have an account?{" "}
            <Link to="/signup">
              Register
            </Link>
          </p>

          <div className="auth-disclaimer">
            Never enter a real banking password in this
            prototype.
          </div>
        </div>
      </div>
    </div>
  );
}
