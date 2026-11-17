import { useEffect, useState } from "react";
import {
  Camera,
  CheckCircle2,
  Clock3,
  UploadCloud,
} from "lucide-react";

import { money } from "../data/mockData";

import {
  getCheckDeposits,
  submitCheckDeposit,
} from "../api/checkDepositApi";

function PhotoInput({ title, file, onChange }) {
  const [preview, setPreview] = useState("");

  useEffect(() => {
    if (!file) {
      setPreview("");
      return;
    }

    const url = URL.createObjectURL(file);
    setPreview(url);

    return () => URL.revokeObjectURL(url);
  }, [file]);

  return (
    <div
      style={{
        border: "1px dashed #b7c7e2",
        borderRadius: 14,
        padding: 18,
        background: "#f9fbff",
      }}
    >
      <h3 style={{ display: "flex", gap: 8 }}>
        <Camera size={20} />
        {title}
      </h3>

      <p style={{ color: "#667a98" }}>
        Use a fictional check on a flat, well-lit
        surface. Capture all four corners.
      </p>

      <input
        type="file"
        accept="image/jpeg,image/png"
        capture="environment"
        onChange={(e) => {
          onChange(e.target.files?.[0] || null);
        }}
        required
      />

      {preview && (
        <img
          src={preview}
          alt={`${title} preview`}
          style={{
            display: "block",
            marginTop: 14,
            width: "100%",
            maxHeight: 260,
            objectFit: "contain",
            borderRadius: 10,
          }}
        />
      )}

      {file && (
        <small style={{ display: "block", marginTop: 8 }}>
          {file.name} — {(file.size / 1024).toFixed(0)} KB
        </small>
      )}
    </div>
  );
}

export default function CheckDeposit({ accounts = [] }) {
  const activeAccounts = accounts.filter(
    (account) => account.status === "active"
  );

  const [accountId, setAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [front, setFront] = useState(null);
  const [back, setBack] = useState(null);

  const [submitting, setSubmitting] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [history, setHistory] = useState([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const selectedAccount =
    activeAccounts.find(
      (account) => String(account.id) === accountId
    ) || activeAccounts[0];

  async function refreshHistory() {
    setLoadingHistory(true);

    try {
      const result = await getCheckDeposits();
      setHistory(result.deposits || []);
    } catch (err) {
      setError(err.message || "Unable to load deposits.");
    } finally {
      setLoadingHistory(false);
    }
  }

  useEffect(() => {
    let alive = true;

    getCheckDeposits()
      .then((result) => {
        if (alive) setHistory(result.deposits || []);
      })
      .catch(() => {});

    return () => {
      alive = false;
    };
  }, []);

  async function submit(event) {
    event.preventDefault();

    setError("");
    setSuccess("");

    const value = amount.trim();

    if (!selectedAccount) {
      setError("Select an active bank account.");
      return;
    }

    if (!/^\d+(\.\d{1,2})?$/.test(value)) {
      setError("Enter an amount such as 50 or 50.25.");
      return;
    }

    if (Number(value) <= 0 || Number(value) > 10000) {
      setError("Enter an amount between $0.01 and $10,000.");
      return;
    }

    if (!front || !back) {
      setError("Capture both sides of the check.");
      return;
    }

    for (const file of [front, back]) {
      if (
        !["image/jpeg", "image/png"].includes(file.type) ||
        file.size > 5 * 1024 * 1024
      ) {
        setError(
          "Each image must be a JPEG or PNG under 5 MB."
        );
        return;
      }
    }

    setSubmitting(true);

    try {
      const result = await submitCheckDeposit({
        accountId: selectedAccount.id,
        amount: value,
        front,
        back,
      });

      setSuccess(
        `Demo check submission #${result.deposit.id} ` +
          "recorded as pending. Your account balance " +
          "has not changed."
      );

      setAmount("");
      setFront(null);
      setBack(null);

      await refreshHistory();
    } catch (err) {
      setError(err.message || "Could not submit check.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">MOBILE BANKING</span>
        <h1>Mobile Check Deposit</h1>
        <p>
          Photograph both sides of a check and send
          a simulated deposit request for review.
        </p>
      </div>

      <section className="panel">
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            marginBottom: 20,
          }}
        >
          <UploadCloud size={26} color="#2855c8" />
          <div>
            <h2 style={{ margin: 0 }}>
              Submit a check
            </h2>
            <small>
              Academic demonstration — no real funds
              are deposited.
            </small>
          </div>
        </div>

        <form
          className="stacked-form"
          onSubmit={submit}
        >
          <label>
            Deposit into account

            <select
              required
              value={selectedAccount?.id ?? ""}
              onChange={(e) => setAccountId(e.target.value)}
              disabled={!activeAccounts.length}
            >
              {!activeAccounts.length && (
                <option value="">
                  No active accounts available
                </option>
              )}

              {activeAccounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.type} {account.number}
                </option>
              ))}
            </select>
          </label>

          {selectedAccount && (
            <p style={{ color: "#667a98" }}>
              Current available balance:{" "}
              <strong>
                {money(selectedAccount.available)}
              </strong>
            </p>
          )}

          <label>
            Check amount (USD)
            <input
              required
              type="text"
              inputMode="decimal"
              placeholder="0.00"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>

          <div
            style={{
              display: "grid",
              gridTemplateColumns:
                "repeat(auto-fit, minmax(250px, 1fr))",
              gap: 18,
              marginTop: 12,
            }}
          >
            <PhotoInput
              title="Front of check"
              file={front}
              onChange={setFront}
            />

            <PhotoInput
              title="Back of check"
              file={back}
              onChange={setBack}
            />
          </div>

          <div
            style={{
              padding: 16,
              borderRadius: 12,
              background: "#fff6e4",
              marginTop: 12,
              fontSize: 14,
            }}
          >
            <strong>For demonstration only</strong>
            <p style={{ margin: "6px 0 0" }}>
              Use a fictional/sample check, not a real
              financial document. Photos are inspected
              and discarded by the backend. This feature
              does not verify or settle checks and will
              not increase your account balance.
            </p>
          </div>

          {error && (
            <p role="alert" className="form-error">
              {error}
            </p>
          )}

          {success && (
            <p
              role="status"
              style={{
                color: "#16865c",
                display: "flex",
                alignItems: "center",
                gap: 8,
              }}
            >
              <CheckCircle2 size={20} />
              {success}
            </p>
          )}

          <button
            className="button button-primary"
            type="submit"
            disabled={
              submitting ||
              !selectedAccount ||
              !front ||
              !back
            }
          >
            <Camera size={18} />
            {submitting
              ? "Submitting..."
              : "Submit demo check"}
          </button>
        </form>
      </section>

      <section className="panel">
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <h2>Check submission history</h2>

          <button
            type="button"
            className="button button-outline"
            onClick={refreshHistory}
            disabled={loadingHistory}
          >
            Refresh
          </button>
        </div>

        {!history.length && (
          <p className="empty">
            No check submissions yet.
          </p>
        )}

        <div className="summary-list">
          {history.map((item) => (
            <div
              className="summary-row"
              key={item.id}
            >
              <span>
                <strong>
                  <Clock3
                    size={16}
                    style={{
                      display: "inline",
                      marginRight: 5,
                    }}
                  />
                  Pending review
                </strong>

                <small
                  style={{
                    display: "block",
                    marginTop: 5,
                  }}
                >
                  Account #{item.account_id} ·{" "}
                  {new Date(item.created_at).toLocaleString()}
                </small>
              </span>

              <strong>
                {money(item.amount)}
              </strong>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}