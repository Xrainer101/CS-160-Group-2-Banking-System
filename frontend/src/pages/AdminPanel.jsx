
import { useEffect, useState } from "react";
import {
  Search,
  Users,
  Wallet,
  FileBarChart,
  Trash2,
  ShieldCheck,
  AlertTriangle,
  Download,
  RefreshCw,
} from "lucide-react";

import {
  getManagerSummary,
  getManagerCustomers,
  getManagerCustomerAccounts,
  getManagerCustomerTransactions,
  closeManagerCustomerAccount,
  deactivateManagerCustomer,
} from "../api/adminApi";

import { money } from "../data/mockData";
import "../styles/admin.css";

// Fictional records, ONLY for administrator UI preview.
// These are never sent to the backend.
const SAMPLE_CLIENTS = [
  {
    user_id: "demo-001",
    name: "Alex Morgan",
    username: "alex.demo",
    email: "alex@example.com",
    role: "customer",
    accounts: [
      {
        id: "demo-a1",
        type: "Everyday Checking",
        number: "•••• 1024",
        balance: 4500,
      },
      {
        id: "demo-a2",
        type: "High-Yield Savings",
        number: "•••• 2048",
        balance: 8500,
      },
    ],
  },
  {
    user_id: "demo-002",
    name: "Jordan Lee",
    username: "jordan.demo",
    email: "jordan@example.com",
    role: "customer",
    accounts: [
      {
        id: "demo-a3",
        type: "Everyday Checking",
        number: "•••• 3060",
        balance: 3200,
      },
    ],
  },
  {
    user_id: "demo-003",
    name: "Taylor Smith",
    username: "taylor.demo",
    email: "taylor@example.com",
    role: "customer",
    accounts: [],
  },
];

function normalizeClient(client) {
  return {
    ...client,
    user_id: client.user_id ?? client.id,
    name:
      client.name ||
      client.full_name ||
      [client.first_name, client.last_name]
        .filter(Boolean)
        .join(" ") ||
      client.username ||
      "Unnamed client",
    accounts: Array.isArray(client.accounts)
      ? client.accounts
      : [],
  };
}

function normalizeAccount(account) {
  return {
    ...account,
    id: account.id ?? account.account_id,
    type:
      account.type ||
      account.account_type ||
      "Bank account",
    number:
      account.number ||
      (account.account_number
        ? `•••• ${String(
            account.account_number
          ).slice(-4)}`
        : "Account number unavailable"),
    balance: Number(account.balance || 0),
  };
}

function normalizeTransaction(transaction) {
  return {
    ...transaction,
    id: transaction.transaction_id ?? transaction.id,
    type:
      transaction.transaction_type ||
      transaction.type ||
      "Transaction",
    amount: Number(transaction.amount || 0),
    status: transaction.status || "unknown",
    timestamp: transaction.timestamp || "",
  };
}

function formatCreatedDate(value) {
  if (!value) return "Unavailable";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);

  return date.toLocaleString(undefined, {
    month: "long",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function formatDateOfBirth(value) {
  if (!value) return "Unavailable";

  const dateText = String(value);
  const match = dateText.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (match) {
    const [, year, month, day] = match;
    return `${month}/${day}/${year}`;
  }

  return dateText;
}

function formatPhoneNumber(value) {
  if (!value) return "Unavailable";

  const digits = String(value).replace(/\D/g, "");
  if (digits.length !== 10) return String(value);

  return `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6)}`;
}

function downloadCSV(filename, rows) {
  const escapeCell = (value) => {
    let text = String(value ?? "");

    // Prevent spreadsheet formula injection.
    if (/^[=+\-@\t\r]/.test(text)) {
      text = `'${text}`;
    }

    return `"${text.replace(/"/g, '""')}"`;
  };

  const csv = rows
    .map((row) => row.map(escapeCell).join(","))
    .join("\r\n");

  const blob = new Blob([csv], {
    type: "text/csv;charset=utf-8",
  });

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export default function AdminPanel({
  preview = true,
  user,
}) {
  const [tab, setTab] = useState("clients");
  const [query, setQuery] = useState("");
  const [clients, setClients] = useState(
    preview ? SAMPLE_CLIENTS : []
  );
  const [selectedClientId, setSelectedClientId] =
    useState(null);
  const [selectedProfile, setSelectedProfile] =
    useState(null);
  const [selectedAccounts, setSelectedAccounts] =
    useState([]);
  const [selectedTransactions, setSelectedTransactions] =
    useState([]);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] =
    useState(false);
  const [actionLoading, setActionLoading] =
    useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function loadClients() {
    if (preview) {
      setClients(SAMPLE_CLIENTS);
      return;
    }

    setLoading(true);
    setError("");

    try {
      const result = await getManagerCustomers();

      // Expected backend response: { customers: [...] }
      if (!Array.isArray(result.customers)) {
        throw new Error(
          "The manager API returned an unexpected response."
        );
      }

      setClients(result.customers.map(normalizeClient));
    } catch (err) {
      setClients([]);
      setError(
        err.message
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (preview) {
      setClients(SAMPLE_CLIENTS);
    } else {
      loadClients();
      getManagerSummary()
        .then(setSummary)
        .catch((err) => setError(err.message));
    }
  }, [preview]);

  const filteredClients = clients.filter((client) => {
    const text = [
      client.name,
      client.email,
      client.username,
      client.user_id,
    ]
      .join(" ")
      .toLowerCase();

    return text.includes(query.toLowerCase());
  });

  const selectedClient = clients.find(
    (client) =>
      String(client.user_id) ===
      String(selectedClientId)
  );

  async function selectClient(client) {
    setSelectedClientId(client.user_id);
    setSelectedAccounts([]);
    setSelectedTransactions([]);
    setError("");

    if (preview) {
      setSelectedAccounts(
        client.accounts.map(normalizeAccount)
      );
      return;
    }

    setDetailLoading(true);

    try {
      const [accountResult, transactionResult] =
        await Promise.all([
          getManagerCustomerAccounts(client.user_id),
          getManagerCustomerTransactions(client.user_id),
        ]);

      if (!Array.isArray(accountResult.accounts)) {
        throw new Error(
          "The manager accounts endpoint returned an unexpected response."
        );
      }

      if (!Array.isArray(transactionResult.transactions)) {
        throw new Error(
          "The manager transactions endpoint returned an unexpected response."
        );
      }

      setSelectedAccounts(
        accountResult.accounts.map(normalizeAccount)
      );
      setSelectedTransactions(
        transactionResult.transactions.map(
          normalizeTransaction
        )
      );
    } catch (err) {
      setError(
        err.status === 404
          ? "Customer details could not be found."
          : err.message
      );
    } finally {
      setDetailLoading(false);
    }
  }

  async function closeAccount(account) {
    const confirmed = window.confirm(
      `Are you sure you want to close this ${account.type} account ending in ${account.number.slice(-4)}? ` +
      "The account and transaction history will be preserved."
    );
    if (!confirmed) return;

    setActionLoading(true);
    setError("");
    setMessage("");

    try {
      await closeManagerCustomerAccount(account.id);
      setSelectedAccounts((accounts) =>
        accounts.map((item) =>
          item.id === account.id
            ? { ...item, status: "closed" }
            : item
        )
      );
      setMessage("Bank account closed successfully.");
    } catch (err) {
      setError(err.message);
    } finally {
      setActionLoading(false);
    }
  }

  async function deactivateClient(client) {
    const confirmed = window.confirm(
      `Are you sure you want to deactivate ${client.name}'s customer profile? ` +
      "All of the customer's accounts must already be closed."
    );
    if (!confirmed) return;

    setActionLoading(true);
    setError("");
    setMessage("");

    try {
      await deactivateManagerCustomer(client.user_id);
      setClients((currentClients) =>
        currentClients.filter(
          (item) => item.user_id !== client.user_id
        )
      );
      setSelectedClientId(null);
      setSelectedProfile(null);
      setSelectedAccounts([]);
      setSelectedTransactions([]);
      setMessage("Customer profile deactivated successfully.");
    } catch (err) {
      setError(err.message);
    } finally {
      setActionLoading(false);
    }
  }

  // Export only records returned by the authenticated
  // admin API. Preview exports are labeled as samples.
  function exportClientReport() {
    const rows = [
      [
        "Client ID",
        "Name",
        "Username",
        "Email",
        "Role",
      ],
      ...filteredClients.map((client) => [
        client.user_id,
        client.name,
        client.username,
        client.email,
        client.role || "customer",
      ]),
    ];

    downloadCSV(
      "manager-clients-report.csv",
      rows
    );

    setMessage("CSV report generated.");
  }

  function exportAccountReport() {
    if (!selectedClient) return;

    const rows = [
      [
        "Client ID",
        "Account ID",
        "Account Type",
        "Masked Account",
        "Balance",
      ],
      ...selectedAccounts.map((account) => [
        selectedClient.user_id,
        account.id,
        account.type,
        account.number,
        account.balance,
      ]),
    ];

    downloadCSV(
      "manager-account-report.csv",
      rows
    );

    setMessage("Account report generated.");
  }

  return (
    <div className="admin-portal">
      <div className="page-heading">
        <span className="eyebrow">
          BANK MANAGEMENT
        </span>
        <h1>Administrator Portal</h1>
        <p>
          Manage clients, review bank accounts,
          and generate administrative reports.
        </p>
      </div>

      {preview ? (
        <div className="admin-banner">
          <AlertTriangle size={20} />
          <div>
            <strong>Preview mode</strong>
            <p>
              Showing fictional sample records.
              Live customer lookup and deletion
              require admin-only backend endpoints.
            </p>
          </div>
        </div>
      ) : (
        <div className="admin-banner">
          <ShieldCheck size={20} />
          <div>
            <strong>Administrator view</strong>
            <p>
              Signed in as {user?.name || "Administrator"}.
            </p>
          </div>
        </div>
      )}

      {error && (
        <p className="admin-error" role="alert">
          {error}
        </p>
      )}

      {message && (
        <p className="admin-success" role="status">
          {message}
        </p>
      )}

      <div className="admin-tabs">
        <button
          type="button"
          className={tab === "clients" ? "active" : ""}
          onClick={() => setTab("clients")}
        >
          <Users size={18} />
          Client Management
        </button>

        <button
          type="button"
          className={tab === "reports" ? "active" : ""}
          onClick={() => setTab("reports")}
        >
          <FileBarChart size={18} />
          Reports
        </button>
      </div>

      {tab === "clients" && (
        <>
          <section className="panel">
            <div className="admin-heading-row">
              <h2>Client Directory</h2>
              <button
                type="button"
                className="button"
                onClick={loadClients}
                disabled={loading}
              >
                <RefreshCw size={16} />
                Refresh
              </button>
            </div>

            <form
              className="admin-search"
              onSubmit={(e) => {
                e.preventDefault();
                loadClients();
              }}
            >
              <Search size={20} />
              <input
                value={query}
                onChange={(e) =>
                  setQuery(e.target.value)
                }
                placeholder="Search name, username, email or ID"
                aria-label="Search clients"
              />
              <button
                type="submit"
                className="button button-primary"
              >
                Search
              </button>
            </form>

            {loading ? (
              <p>Loading clients...</p>
            ) : filteredClients.length === 0 ? (
              <p className="empty">
                No clients found.
              </p>
            ) : (
              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Client</th>
                      <th>Username</th>
                      <th>Client ID</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredClients.map((client) => (
                      <tr key={client.user_id}>
                        <td>
                          <strong>{client.name}</strong>
                          <small>{client.email}</small>
                        </td>
                        <td>{client.username}</td>
                        <td>{client.user_id}</td>
                        <td>
                          <div className="admin-actions">
                            <button
                              type="button"
                              className="text-link"
                              onClick={() =>
                                setSelectedProfile(client)
                              }
                            >
                              View profile
                            </button>
                            <button
                              type="button"
                              className="text-link"
                              onClick={() =>
                                selectClient(client)
                              }
                            >
                              View accounts
                            </button>
                            <button
                              type="button"
                              className="admin-danger-button"
                              disabled={actionLoading}
                              onClick={() => deactivateClient(client)}
                              title="Deactivate customer profile"
                            >
                              <Trash2 size={15} />
                              Delete
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {selectedProfile && (
            <section className="panel">
              <div className="admin-heading-row">
                <div>
                  <h2>{selectedProfile.name}</h2>
                  <p className="panel-subtitle">
                    Customer profile
                  </p>
                </div>
                <button
                  type="button"
                  className="text-link"
                  onClick={() => setSelectedProfile(null)}
                >
                  Close
                </button>
              </div>

              <div className="admin-profile-grid">
                <div>
                  <small>Full name</small>
                  <strong>{selectedProfile.name || "Unavailable"}</strong>
                </div>
                <div>
                  <small>Username</small>
                  <strong>{selectedProfile.username || "Unavailable"}</strong>
                </div>
                <div>
                  <small>Email</small>
                  <strong>{selectedProfile.email || "Unavailable"}</strong>
                </div>
                <div>
                  <small>Phone number</small>
                  <strong>{formatPhoneNumber(selectedProfile.phone_number)}</strong>
                </div>
                <div>
                  <small>Date of birth</small>
                  <strong>{formatDateOfBirth(selectedProfile.date_of_birth)}</strong>
                </div>
                <div>
                  <small>Role</small>
                  <strong>{selectedProfile.role || "customer"}</strong>
                </div>
                <div>
                  <small>Created date</small>
                  <strong>{formatCreatedDate(selectedProfile.created_at)}</strong>
                </div>
                <div>
                  <small>User ID</small>
                  <strong>{selectedProfile.user_id || "Unavailable"}</strong>
                </div>
              </div>
            </section>
          )}

          {selectedClient && (
            <section className="panel">
              <div className="admin-heading-row">
                <div>
                  <h2>{selectedClient.name}</h2>
                  <p className="panel-subtitle">
                    Linked bank accounts
                  </p>
                </div>
                <button
                  type="button"
                  className="text-link"
                  onClick={() =>
                    setSelectedClientId(null)
                  }
                >
                  Close
                </button>
              </div>

              {detailLoading ? (
                <p>Loading accounts...</p>
              ) : selectedAccounts.length === 0 ? (
                <p className="empty">
                  No accounts available.
                </p>
              ) : (
                selectedAccounts.map((account) => (
                  <div
                    className="summary-row"
                    key={account.id}
                  >
                    <span>
                      <strong>{account.type}</strong>
                      <small>{account.number}</small>
                    </span>
                    <strong>
                      {money(account.balance)}
                    </strong>
                    {String(account.status).toLowerCase() === "closed" ? (
                      <span className="account-status-badge">
                        Closed
                      </span>
                    ) : (
                      <button
                        type="button"
                        className="admin-danger-button"
                        disabled={actionLoading}
                        onClick={() => closeAccount(account)}
                        title="Close bank account"
                      >
                        <Trash2 size={15} />
                        Delete
                      </button>
                    )}
                  </div>
                ))
              )}
              <div className="admin-transactions">
                <h3>Transaction history</h3>
                {detailLoading ? (
                  <p>Loading transactions...</p>
                ) : selectedTransactions.length === 0 ? (
                  <p className="empty">
                    No transactions available.
                  </p>
                ) : (
                  <div className="admin-table-wrap">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>Type</th>
                          <th>Amount</th>
                          <th>Status</th>
                          <th>Timestamp</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selectedTransactions.map(
                          (transaction) => (
                            <tr key={transaction.id}>
                              <td>{transaction.type}</td>
                              <td>
                                {money(transaction.amount)}
                              </td>
                              <td>{transaction.status}</td>
                              <td>
                                {transaction.timestamp ||
                                  "Unavailable"}
                              </td>
                            </tr>
                          )
                        )}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <button
                type="button"
                className="button"
                onClick={exportAccountReport}
              >
                <Download size={16} />
                Download account report
              </button>
            </section>
          )}
        </>
      )}

      {tab === "reports" && (
        <>
          <div className="admin-stats">
            <article className="panel">
              <Users size={23} />
              <small>Clients in current result</small>
              <h2>{filteredClients.length}</h2>
            </article>
            <article className="panel">
              <Wallet size={23} />
              <small>
                Accounts in selected client
              </small>
              <h2>{selectedAccounts.length}</h2>
            </article>
            <article className="panel">
              <FileBarChart size={23} />
              <small>Total bank balance</small>
              <h2>
                {money(summary?.total_balance || 0)}
              </h2>
            </article>
          </div>

          <section className="panel">
            <h2>Generate Reports</h2>
            <p className="panel-subtitle">
              Export the currently loaded client records.
              This is not a bank-wide financial summary.
            </p>

            <div className="admin-report-option">
              <div>
                <strong>Client Directory Report</strong>
                <p>
                  Client IDs, names, usernames,
                  emails and roles.
                </p>
              </div>
              <button
                type="button"
                className="button button-primary"
                onClick={exportClientReport}
                disabled={loading}
              >
                <Download size={17} />
                Export CSV
              </button>
            </div>

            <div className="admin-report-option">
              <div>
                <strong>Client Account Report</strong>
                <p>
                  Account details for the selected client.
                  Select a client first.
                </p>
              </div>
              <button
                type="button"
                className="button"
                onClick={exportAccountReport}
                disabled={!selectedClient || detailLoading}
              >
                <Download size={17} />
                Export CSV
              </button>
            </div>
          </section>
        </>
      )}

    </div>
  );
}
