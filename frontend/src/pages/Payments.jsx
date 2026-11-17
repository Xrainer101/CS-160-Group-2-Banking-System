import { useMemo, useState } from "react";
import {
  CalendarDays,
  CheckCircle2,
  Clock3,
  CreditCard,
  DollarSign,
  Plus,
  Repeat,
  Search,
  Trash2,
  Wallet,
  X,
} from "lucide-react";

import { money } from "../data/mockData";

const PAYMENT_CATEGORIES = [
  "Utilities",
  "Rent / Mortgage",
  "Internet / Phone",
  "Insurance",
  "Credit Card",
  "Subscriptions",
  "Other",
];

const today = () => {
  const date = new Date();
  const offset = date.getTimezoneOffset() * 60000;

  return new Date(date.getTime() - offset)
    .toISOString()
    .slice(0, 10);
};

function formatDate(value) {
  if (!value) return "Not specified";

  // New scheduled payments have YYYY-MM-DD dates.
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const date = new Date(`${value}T12:00:00`);

    return date.toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  }

  // Preserve dates from the existing mockData.js.
  return value;
}

function isUpcoming(payment) {
  if (payment.status === "cancelled") return false;

  // The existing mock payments don't contain status.
  return (
    !payment.status ||
    payment.status === "scheduled" ||
    payment.status === "pending"
  );
}

function PaymentCard({ payment, accounts, onCancel }) {
  const [confirmCancel, setConfirmCancel] = useState(false);

  const account = accounts.find(
    (item) =>
      String(item.id) === String(payment.accountId)
  );

  const recurring =
    payment.frequency &&
    payment.frequency !== "once";

  const canCancel =
    typeof onCancel === "function" &&
    isUpcoming(payment);

  return (
    <article
      style={{
        border: "1px solid #e1e8f2",
        borderRadius: 14,
        padding: 18,
        background: "#fff",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "flex-start",
          justifyContent: "space-between",
          gap: 14,
          flexWrap: "wrap",
        }}
      >
        <div
          style={{
            display: "flex",
            gap: 12,
            alignItems: "center",
            flex: 1,
          }}
        >
          <span
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              display: "grid",
              placeItems: "center",
              background: "#eaf0ff",
              color: "#2855c8",
              flexShrink: 0,
            }}
          >
            {recurring ? (
              <Repeat size={21} />
            ) : (
              <CreditCard size={21} />
            )}
          </span>

          <div>
            <strong
              style={{
                display: "block",
                fontSize: 16,
              }}
            >
              {payment.name}
            </strong>

            <small
              style={{
                color: "#718096",
                display: "block",
                marginTop: 4,
              }}
            >
              {payment.category || "Bill Payment"}
            </small>
          </div>
        </div>

        <strong
          style={{
            fontSize: 19,
            color: "#172b4d",
          }}
        >
          {money(payment.amount)}
        </strong>
      </div>

      <div
        style={{
          display: "flex",
          gap: 10,
          flexWrap: "wrap",
          marginTop: 16,
          alignItems: "center",
        }}
      >
        <span
          style={{
            padding: "6px 10px",
            borderRadius: 20,
            background: "#fff4dc",
            color: "#946319",
            fontSize: 12,
            fontWeight: 700,
          }}
        >
          <Clock3
            size={13}
            style={{
              display: "inline",
              verticalAlign: "middle",
              marginRight: 5,
            }}
          />
          Scheduled
        </span>

        {recurring && (
          <span
            style={{
              padding: "6px 10px",
              borderRadius: 20,
              background: "#e9f7f0",
              color: "#167650",
              fontSize: 12,
              fontWeight: 700,
            }}
          >
            <Repeat
              size={13}
              style={{
                display: "inline",
                verticalAlign: "middle",
                marginRight: 5,
              }}
            />
            {payment.frequency === "weekly"
              ? "Weekly"
              : payment.frequency === "monthly"
                ? "Monthly"
                : "Recurring"}
          </span>
        )}
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns:
            "repeat(auto-fit, minmax(160px, 1fr))",
          gap: 15,
          marginTop: 18,
        }}
      >
        <div>
          <small
            style={{
              color: "#8491a3",
              display: "block",
              marginBottom: 5,
            }}
          >
            Payment date
          </small>

          <strong>
            {formatDate(
              payment.paymentDate || payment.date
            )}
          </strong>
        </div>

        <div>
          <small
            style={{
              color: "#8491a3",
              display: "block",
              marginBottom: 5,
            }}
          >
            From account
          </small>

          <strong>
            {account
              ? `${account.type} ${account.number}`
              : "Demo account"}
          </strong>
        </div>
      </div>

      {payment.memo && (
        <p
          style={{
            marginTop: 14,
            marginBottom: 0,
            fontSize: 13,
            color: "#718096",
          }}
        >
          Memo: {payment.memo}
        </p>
      )}

      {canCancel && (
        <div
          style={{
            borderTop: "1px solid #edf0f5",
            marginTop: 18,
            paddingTop: 14,
          }}
        >
          {!confirmCancel ? (
            <button
              type="button"
              className="button button-outline"
              onClick={() => setConfirmCancel(true)}
            >
              <Trash2 size={15} />
              Cancel payment
            </button>
          ) : (
            <div
              style={{
                display: "flex",
                gap: 10,
                alignItems: "center",
                flexWrap: "wrap",
              }}
            >
              <span style={{ fontSize: 13 }}>
                Cancel this demo payment?
              </span>

              <button
                type="button"
                className="button button-danger"
                onClick={() => onCancel(payment.id)}
              >
                Confirm cancellation
              </button>

              <button
                type="button"
                className="button button-outline"
                onClick={() => setConfirmCancel(false)}
              >
                Keep payment
              </button>
            </div>
          )}
        </div>
      )}
    </article>
  );
}

export default function Payments({
  payments = [],
  accounts = [],
  schedulePayment,
  cancelPayment,
}) {
  const [showForm, setShowForm] = useState(false);

  const [payee, setPayee] = useState("");
  const [category, setCategory] = useState("Utilities");
  const [amount, setAmount] = useState("");
  const [accountId, setAccountId] = useState("");
  const [paymentDate, setPaymentDate] = useState(today());
  const [frequency, setFrequency] = useState("once");
  const [memo, setMemo] = useState("");

  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("all");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const activeAccounts = accounts.filter(
    (account) =>
      !account.status ||
      account.status === "active"
  );

  const selectedAccount =
    activeAccounts.find(
      (account) =>
        String(account.id) === accountId
    ) || activeAccounts[0];

  const upcoming = useMemo(
    () => payments.filter(isUpcoming),
    [payments]
  );

  const scheduledTotal = upcoming.reduce(
    (sum, payment) =>
      sum + Number(payment.amount || 0),
    0
  );

  const recurringCount = upcoming.filter(
    (payment) =>
      payment.frequency &&
      payment.frequency !== "once"
  ).length;

  const largestPayment = upcoming.reduce(
    (largest, payment) =>
      Math.max(
        largest,
        Number(payment.amount || 0)
      ),
    0
  );

  const filteredPayments = useMemo(() => {
    const query = search.trim().toLowerCase();

    return upcoming.filter((payment) => {
      const matchesSearch =
        !query ||
        payment.name.toLowerCase().includes(query) ||
        (payment.category || "")
          .toLowerCase()
          .includes(query);

      let matchesFilter = true;

      if (filter === "recurring") {
        matchesFilter =
          Boolean(payment.frequency) &&
          payment.frequency !== "once";
      }

      if (filter === "one-time") {
        matchesFilter =
          !payment.frequency ||
          payment.frequency === "once";
      }

      return matchesSearch && matchesFilter;
    });
  }, [upcoming, search, filter]);

  function resetForm() {
    setPayee("");
    setCategory("Utilities");
    setAmount("");
    setAccountId("");
    setPaymentDate(today());
    setFrequency("once");
    setMemo("");
    setError("");
  }

  function closeForm() {
    setShowForm(false);
    resetForm();
  }

  function submitPayment(event) {
    event.preventDefault();

    setError("");
    setSuccess("");

    const name = payee.trim();
    const value = amount.trim();

    if (!name || name.length > 100) {
      setError(
        "Enter a payee name up to 100 characters."
      );
      return;
    }

    if (!/^\d+(\.\d{1,2})?$/.test(value)) {
      setError(
        "Enter a valid amount such as 25 or 25.50."
      );
      return;
    }

    const numericAmount = Number(value);

    if (
      !Number.isFinite(numericAmount) ||
      numericAmount <= 0 ||
      numericAmount > 100000
    ) {
      setError(
        "Amount must be between $0.01 and $100,000."
      );
      return;
    }

    if (!selectedAccount) {
      setError("Select an active bank account.");
      return;
    }

    if (
      !paymentDate ||
      paymentDate < today()
    ) {
      setError(
        "Choose today or a future payment date."
      );
      return;
    }

    if (typeof schedulePayment !== "function") {
      setError(
        "Payment scheduling is not available."
      );
      return;
    }

    const details = {
      category,
      accountId: selectedAccount.id,
      paymentDate,
      frequency,
      memo: memo.trim(),
      status: "scheduled",
    };

    schedulePayment(
      name,
      numericAmount,
      details
    );

    setSuccess(
      `${name} was added to your demo ` +
        "scheduled payments."
    );

    setShowForm(false);
    resetForm();
    setFilter("all");
    setSearch("");
  }

  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">
          BANKING SERVICES
        </span>

        <h1>Pay Bills</h1>

        <p>
          Manage upcoming bills, schedule payments,
          and set up automatic payments.
        </p>
      </div>

      {/* Summary cards */}

      <div className="stats-grid">
        <section className="panel">
          <CalendarDays
            size={25}
            color="#2855c8"
          />

          <small
            style={{
              display: "block",
              marginTop: 12,
            }}
          >
            Scheduled payments
          </small>

          <h2>{upcoming.length}</h2>

          <small>
            Upcoming demo payments
          </small>
        </section>

        <section className="panel">
          <DollarSign
            size={25}
            color="#2855c8"
          />

          <small
            style={{
              display: "block",
              marginTop: 12,
            }}
          >
            Total scheduled
          </small>

          <h2>
            {money(scheduledTotal)}
          </h2>

          <small>
            Total of listed payments
          </small>
        </section>

        <section className="panel">
          <Repeat
            size={25}
            color="#2855c8"
          />

          <small
            style={{
              display: "block",
              marginTop: 12,
            }}
          >
            Automatic payments
          </small>

          <h2>{recurringCount}</h2>

          <small>
            Recurring payment instructions
          </small>
        </section>
      </div>

      {/* Main actions */}

      <section className="panel">
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 16,
            flexWrap: "wrap",
          }}
        >
          <div>
            <span className="eyebrow">
              BILL MANAGEMENT
            </span>

            <h2>Manage your payments</h2>

            <p
              style={{
                color: "#718096",
                marginBottom: 0,
              }}
            >
              Schedule a one-time payment or
              create an automatic payment.
            </p>
          </div>

          <button
            className="button button-primary"
            type="button"
            onClick={() => {
              setShowForm((previous) => !previous);
              setError("");
              setSuccess("");
            }}
          >
            {showForm ? (
              <>
                <X size={18} />
                Close
              </>
            ) : (
              <>
                <Plus size={18} />
                Schedule a Payment
              </>
            )}
          </button>
        </div>

        {success && (
          <p
            role="status"
            style={{
              color: "#157a55",
              marginTop: 18,
              display: "flex",
              gap: 8,
              alignItems: "center",
            }}
          >
            <CheckCircle2 size={18} />
            {success}
          </p>
        )}

        {showForm && (
          <div
            style={{
              borderTop: "1px solid #e7edf5",
              marginTop: 24,
              paddingTop: 24,
            }}
          >
            <h2>Schedule a bill payment</h2>

            <p className="panel-subtitle">
              This creates a simulated payment.
              No funds will be transferred.
            </p>

            <form
              className="stacked-form"
              onSubmit={submitPayment}
            >
              <label>
                Payee / Company name
                <input
                  type="text"
                  value={payee}
                  onChange={(event) =>
                    setPayee(event.target.value)
                  }
                  required
                  maxLength={100}
                  placeholder="e.g. PG&E, AT&T, Netflix"
                />
              </label>

              <label>
                Bill category
                <select
                  value={category}
                  onChange={(event) =>
                    setCategory(event.target.value)
                  }
                >
                  {PAYMENT_CATEGORIES.map(
                    (item) => (
                      <option
                        key={item}
                        value={item}
                      >
                        {item}
                      </option>
                    )
                  )}
                </select>
              </label>

              <label>
                Pay from account
                <select
                  value={
                    selectedAccount?.id ?? ""
                  }
                  onChange={(event) =>
                    setAccountId(event.target.value)
                  }
                  disabled={
                    !activeAccounts.length
                  }
                  required
                >
                  {!activeAccounts.length && (
                    <option value="">
                      No active accounts available
                    </option>
                  )}

                  {activeAccounts.map(
                    (account) => (
                      <option
                        key={account.id}
                        value={account.id}
                      >
                        {account.type}{" "}
                        {account.number} —{" "}
                        {money(account.available)}
                      </option>
                    )
                  )}
                </select>
              </label>

              <label>
                Payment amount (USD)
                <input
                  type="text"
                  inputMode="decimal"
                  placeholder="0.00"
                  value={amount}
                  onChange={(event) =>
                    setAmount(event.target.value)
                  }
                  required
                />
              </label>

              <label>
                First payment date
                <input
                  type="date"
                  value={paymentDate}
                  min={today()}
                  onChange={(event) =>
                    setPaymentDate(event.target.value)
                  }
                  required
                />
              </label>

              <label>
                Payment frequency
                <select
                  value={frequency}
                  onChange={(event) =>
                    setFrequency(event.target.value)
                  }
                >
                  <option value="once">
                    One-time payment
                  </option>
                  <option value="weekly">
                    Automatic — Weekly
                  </option>
                  <option value="monthly">
                    Automatic — Monthly
                  </option>
                </select>
              </label>

              {frequency !== "once" && (
                <div
                  style={{
                    background: "#edf4ff",
                    borderRadius: 12,
                    padding: 16,
                    color: "#31588f",
                    fontSize: 13,
                  }}
                >
                  <Repeat
                    size={17}
                    style={{
                      display: "inline",
                      verticalAlign: "middle",
                      marginRight: 8,
                    }}
                  />

                  This demo will label the
                  payment as recurring.
                  Automatic execution is not
                  connected to the banking backend.
                </div>
              )}

              <label>
                Memo (optional)
                <input
                  value={memo}
                  onChange={(event) =>
                    setMemo(event.target.value)
                  }
                  maxLength={120}
                  placeholder="e.g. November internet bill"
                />
              </label>

              {/* Payment preview */}

              <div
                style={{
                  background: "#f5f8fd",
                  border: "1px solid #e1e8f2",
                  borderRadius: 14,
                  padding: 20,
                }}
              >
                <h3>Payment summary</h3>

                <div className="summary-list">
                  <div className="summary-row">
                    <span>Payee</span>
                    <strong>
                      {payee || "Not entered"}
                    </strong>
                  </div>

                  <div className="summary-row">
                    <span>Amount</span>
                    <strong>
                      {money(
                        Number(amount) || 0
                      )}
                    </strong>
                  </div>

                  <div className="summary-row">
                    <span>Payment date</span>
                    <strong>
                      {formatDate(paymentDate)}
                    </strong>
                  </div>

                  <div className="summary-row">
                    <span>Frequency</span>
                    <strong>
                      {frequency === "once"
                        ? "One-time"
                        : frequency === "weekly"
                          ? "Weekly"
                          : "Monthly"}
                    </strong>
                  </div>
                </div>
              </div>

              {error && (
                <p
                  className="form-error"
                  role="alert"
                >
                  {error}
                </p>
              )}

              <div
                style={{
                  display: "flex",
                  gap: 12,
                  flexWrap: "wrap",
                }}
              >
                <button
                  type="submit"
                  className="button button-primary"
                  disabled={!activeAccounts.length}
                >
                  <CalendarDays size={17} />
                  Schedule (demo)
                </button>

                <button
                  type="button"
                  className="button button-outline"
                  onClick={closeForm}
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}
      </section>

      {/* Upcoming payments */}

      <section className="panel">
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: 12,
            flexWrap: "wrap",
            marginBottom: 22,
          }}
        >
          <div>
            <span className="eyebrow">
              UPCOMING PAYMENTS
            </span>

            <h2>
              Your Scheduled Bills
            </h2>

            <p
              style={{
                color: "#718096",
                marginBottom: 0,
              }}
            >
              Review and manage your
              upcoming demo payments.
            </p>
          </div>

          <span
            style={{
              fontSize: 13,
              color: "#667a98",
            }}
          >
            {upcoming.length} scheduled
          </span>
        </div>

        <div
          style={{
            display: "flex",
            gap: 12,
            flexWrap: "wrap",
            marginBottom: 24,
          }}
        >
          <label
            style={{
              flex: "1 1 220px",
              position: "relative",
            }}
          >
            <Search
              size={17}
              style={{
                position: "absolute",
                left: 12,
                top: 13,
                color: "#8190a4",
              }}
            />

            <input
              type="search"
              value={search}
              onChange={(event) =>
                setSearch(event.target.value)
              }
              placeholder="Search bills..."
              aria-label="Search bills"
              style={{
                width: "100%",
                padding: "11px 12px 11px 38px",
              }}
            />
          </label>

          <select
            value={filter}
            onChange={(event) =>
              setFilter(event.target.value)
            }
            aria-label="Filter payments"
            style={{
              padding: "11px 12px",
              minWidth: 170,
            }}
          >
            <option value="all">
              All Payments
            </option>

            <option value="one-time">
              One-time
            </option>

            <option value="recurring">
              Automatic
            </option>
          </select>
        </div>

        {filteredPayments.length === 0 ? (
          <div
            className="empty"
            style={{
              textAlign: "center",
              padding: "40px 20px",
            }}
          >
            <CalendarDays
              size={38}
              color="#91a4c1"
              style={{ marginBottom: 12 }}
            />

            <h3>No scheduled payments found</h3>

            <p>
              Bills you schedule will
              appear here.
            </p>

            <button
              type="button"
              className="button button-primary"
              onClick={() => setShowForm(true)}
            >
              <Plus size={17} />
              Schedule a Payment
            </button>
          </div>
        ) : (
          <div
            style={{
              display: "grid",
              gap: 14,
            }}
          >
            {filteredPayments.map(
              (payment) => (
                <PaymentCard
                  key={payment.id}
                  payment={payment}
                  accounts={accounts}
                  onCancel={cancelPayment}
                />
              )
            )}
          </div>
        )}
      </section>

      {/* Helpful information */}

      <section className="panel">
        <div
          style={{
            display: "flex",
            gap: 14,
            alignItems: "flex-start",
          }}
        >
          <Wallet
            size={25}
            color="#2855c8"
          />

          <div>
            <h2>About Bill Pay</h2>

            <p
              style={{
                color: "#718096",
                marginBottom: 8,
              }}
            >
              Organize your scheduled
              expenses and automatic
              payment instructions in one place.
            </p>

            <small>
              This is a CS160 academic prototype.
              Payments are simulated and do not
              withdraw funds, contact payees, or
              process automatically.
            </small>
          </div>
        </div>
      </section>
    </>
  );
}
