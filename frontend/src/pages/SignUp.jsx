import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  Landmark,
  ShieldCheck,
  Eye,
  EyeOff,
} from "lucide-react";

import { registerUser } from "../api/authApi";

export default function SignUp() {
  const navigate = useNavigate();

  // Registration form fields
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");

  // Password visibility
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  // API request status
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  function validateForm() {
    if (!name.trim()) {
      return "Please enter your full name.";
    }

    if (!username.trim()) {
      return "Please choose a username.";
    }

    if (
      username.trim().length < 3 ||
      username.trim().length > 30
    ) {
      return "Username must be between 3 and 30 characters.";
    }

    if (!/^[A-Za-z0-9_.]+$/.test(username.trim())) {
      return "Username may only contain letters, numbers, underscores, and periods.";
    }

    if (!email.trim()) {
      return "Please enter your email address.";
    }

    if (!dateOfBirth) {
      return "Please enter your date of birth.";
    }

    const today = new Date();
    const selectedDate = new Date(
      `${dateOfBirth}T12:00:00`
    );

    if (
      Number.isNaN(selectedDate.getTime()) ||
      selectedDate > today
    ) {
      return "Please enter a valid date of birth.";
    }

    if (!phone.trim()) {
      return "Please enter your phone number.";
    }

    const phoneDigits = phone.replace(/\D/g, "");
    const normalizedPhone =
      phoneDigits.length === 11 &&
      phoneDigits.startsWith("1")
        ? phoneDigits.slice(1)
        : phoneDigits;

    if (normalizedPhone.length !== 10) {
      return "Please enter a valid 10-digit phone number.";
    }

    if (password.length < 8) {
      return "Password must be at least 8 characters.";
    }

    if (password !== confirm) {
      return "Passwords do not match.";
    }

    return "";
  }

  async function submit(e) {
    e.preventDefault();
    setError("");

    const validationError = validateForm();

    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);

    try {
      // Current FastAPI registration endpoint accepts:
      // name, email, username, password.
      //
      // Date of birth and phone number are collected
      // by the UI but are not currently sent because
      // the backend schema does not yet support them.

      await registerUser({
        name: name.trim(),
        email: email.trim().toLowerCase(),
        username: username.trim().toLowerCase(),
        password,
      });

      // Registration succeeded.
      // Navigate to sign-in instead of bypassing login.
      navigate("/signin", {
        replace: true,
        state: {
          registrationSuccess: true,
        },
      });
    } catch (err) {
      console.error("Registration failed:", err);

      setError(
        err.message ||
          "Something went wrong while creating your account. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      {/* Left side: Branding */}
      <div className="auth-promo">
        <div className="auth-brand">
          <Landmark />
          Team 2 Bank
        </div>

        <div>
          <span className="auth-overline">
            GET STARTED
          </span>

          <h1>
            A clearer way to see your financial picture.
          </h1>

          <p>
            Explore our banking platform to manage your
            accounts, transfers, savings, and spending
            activity in one convenient place.
          </p>

          <div className="auth-feature">
            <ShieldCheck />
            Secure account registration
          </div>
        </div>

        <small>
          Academic banking application.
          Not a real financial institution.
        </small>
      </div>

      {/* Right side: Registration form */}
      <div className="auth-main">
        <div className="auth-card">
          <span className="eyebrow">
            GET STARTED
          </span>

          <h2>Open an account</h2>

          <p>
            Fill in your details to create your
            Team 2 Bank profile.
          </p>

          <form
            className="stacked-form"
            onSubmit={submit}
          >
            {/* Full name */}
            <label>
              Full name

              <input
                type="text"
                required
                autoComplete="name"
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  setError("");
                }}
                placeholder="Your full name"
                disabled={loading}
              />
            </label>

            {/* Date of birth */}
            <label>
              Date of birth

              <input
                type="date"
                required
                value={dateOfBirth}
                max={
                  new Date().toLocaleDateString("en-CA")
                }
                onChange={(e) => {
                  setDateOfBirth(e.target.value);
                  setError("");
                }}
                disabled={loading}
              />
            </label>

            {/* Email address */}
            <label>
              Email address

              <input
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  setError("");
                }}
                placeholder="name@email.com"
                disabled={loading}
              />
            </label>

            {/* Phone number */}
            <label>
              Phone number

              <input
                type="tel"
                required
                autoComplete="tel"
                value={phone}
                onChange={(e) => {
                  setPhone(e.target.value);
                  setError("");
                }}
                placeholder="(555) 123-4567"
                disabled={loading}
              />
            </label>

            {/* Username */}
            <label>
              Username

              <input
                type="text"
                required
                minLength={3}
                maxLength={30}
                autoComplete="username"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setError("");
                }}
                placeholder="Choose a username"
                disabled={loading}
              />
            </label>

            {/* Password */}
            <label>
              Password

              <div className="password-field">
                <input
                  type={
                    showPassword
                      ? "text"
                      : "password"
                  }
                  required
                  minLength={8}
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    setError("");
                  }}
                  placeholder="At least 8 characters"
                  disabled={loading}
                />

                <button
                  type="button"
                  onClick={() =>
                    setShowPassword((prev) => !prev)
                  }
                  aria-label={
                    showPassword
                      ? "Hide password"
                      : "Show password"
                  }
                  disabled={loading}
                >
                  {showPassword ? (
                    <EyeOff size={18} />
                  ) : (
                    <Eye size={18} />
                  )}
                </button>
              </div>
            </label>

            {/* Confirm password */}
            <label>
              Confirm password

              <div className="password-field">
                <input
                  type={
                    showConfirm
                      ? "text"
                      : "password"
                  }
                  required
                  minLength={8}
                  autoComplete="new-password"
                  value={confirm}
                  onChange={(e) => {
                    setConfirm(e.target.value);
                    setError("");
                  }}
                  placeholder="Re-enter your password"
                  disabled={loading}
                />

                <button
                  type="button"
                  onClick={() =>
                    setShowConfirm((prev) => !prev)
                  }
                  aria-label={
                    showConfirm
                      ? "Hide password"
                      : "Show password"
                  }
                  disabled={loading}
                >
                  {showConfirm ? (
                    <EyeOff size={18} />
                  ) : (
                    <Eye size={18} />
                  )}
                </button>
              </div>
            </label>

            {/* Error message */}
            {error && (
              <p
                className="form-error"
                role="alert"
              >
                {error}
              </p>
            )}

            {/* Submit button */}
            <button
              type="submit"
              className="button button-primary full"
              disabled={loading}
            >
              {loading
                ? "Creating account..."
                : "Create account"}
            </button>
          </form>

          {/* Sign in link */}
          <p className="auth-switch">
            Already have an account?{" "}
            <Link to="/signin">
              Sign in
            </Link>
          </p>

          <div className="auth-disclaimer">
            This is an academic banking prototype.
            Do not use real banking credentials.
            Date of birth and phone number are not
            currently saved by the backend.
          </div>
        </div>
      </div>
    </div>
  );
}
