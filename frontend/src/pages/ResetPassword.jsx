
import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Landmark, ShieldCheck } from "lucide-react";
import { resetPassword } from "../api/authApi";

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");

    if (!token) {
      setError("This password reset link is invalid.");
      return;
    }

    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }

    if (password !== confirm) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      await resetPassword(token, password);
      setSuccess(true);
      setPassword("");
      setConfirm("");
    } catch (err) {
      if (err.status === 404 || err.status === 405) {
        setError(
          "Password reset is not available on the server yet."
        );
      } else {
        setError(
          err.message ||
          "This reset link may be invalid or expired."
        );
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
          <span className="auth-overline">
            ACCOUNT SECURITY
          </span>

          <h1>Secure your account.</h1>

          <p>
            Set a new password and get back to
            managing your finances.
          </p>

          <div className="auth-feature">
            <ShieldCheck />
            Password protection
          </div>
        </div>

        <small>
          Academic banking prototype.
          Not a real financial institution.
        </small>
      </div>

      <div className="auth-main">
        <div className="auth-card">
          <span className="eyebrow">
            RESET PASSWORD
          </span>

          {success ? (
            <>
              <h2>Password updated</h2>

              <p>
                Your password has been changed.
                You can now sign in.
              </p>

              <Link
                to="/signin"
                className="button button-primary full"
              >
                Go to sign in
              </Link>
            </>
          ) : !token ? (
            <>
              <h2>Invalid reset link</h2>

              <p>
                This link is missing its reset token.
                Request a new password reset link.
              </p>

              <Link to="/forgot-password">
                Request new link
              </Link>
            </>
          ) : (
            <>
              <h2>Create a new password</h2>

              <p>
                Enter and confirm your new password.
              </p>

              <form
                className="stacked-form"
                onSubmit={handleSubmit}
              >
                <label>
                  New password

                  <input
                    type="password"
                    required
                    minLength={8}
                    autoComplete="new-password"
                    placeholder="At least 8 characters"
                    value={password}
                    onChange={(e) => {
                      setPassword(e.target.value);
                      setError("");
                    }}
                    disabled={loading}
                  />
                </label>

                <label>
                  Confirm new password

                  <input
                    type="password"
                    required
                    autoComplete="new-password"
                    placeholder="Confirm password"
                    value={confirm}
                    onChange={(e) => {
                      setConfirm(e.target.value);
                      setError("");
                    }}
                    disabled={loading}
                  />
                </label>

                {error && (
                  <p className="form-error" role="alert">
                    {error}
                  </p>
                )}

                <button
                  type="submit"
                  className="button button-primary full"
                  disabled={loading}
                >
                  {loading
                    ? "Updating password..."
                    : "Reset password"}
                </button>
              </form>
            </>
          )}

          <p className="auth-switch">
            <Link to="/signin">
              Back to sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
