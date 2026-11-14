
import { useState } from "react";
import { Link } from "react-router-dom";
import { Landmark, ShieldCheck, Mail } from "lucide-react";
import { requestPasswordReset } from "../api/authApi";

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      await requestPasswordReset(email.trim());
      setSubmitted(true);
    } catch (err) {
      if (err.status === 404 || err.status === 405) {
        setError(
          "Password recovery is not available on the server yet."
        );
      } else {
        setError(
          err.message || "Unable to process your request."
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
            ACCOUNT RECOVERY
          </span>
          <h1>
            Get back to managing your money.
          </h1>
          <p>
            Securely recover access to your
            Team 2 Bank account.
          </p>
          <div className="auth-feature">
            <ShieldCheck />
            Secure password recovery
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
            PASSWORD RECOVERY
          </span>

          {submitted ? (
            <>
              <Mail size={40} />

              <h2>Check your email</h2>

              <p>
                If an account exists with that email
                address, password reset instructions
                will be sent.
              </p>

              <p className="auth-switch">
                <Link to="/signin">
                  Back to sign in
                </Link>
              </p>
            </>
          ) : (
            <>
              <h2>Forgot your password?</h2>

              <p>
                Enter the email associated with your
                account to request a password reset.
              </p>

              <form
                className="stacked-form"
                onSubmit={handleSubmit}
              >
                <label>
                  Email address

                  <input
                    type="email"
                    required
                    autoComplete="email"
                    placeholder="name@email.com"
                    value={email}
                    onChange={(e) => {
                      setEmail(e.target.value);
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
                    ? "Sending request..."
                    : "Send reset link"}
                </button>
              </form>

              <p className="auth-switch">
                Remembered your password?{" "}
                <Link to="/signin">
                  Back to sign in
                </Link>
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
