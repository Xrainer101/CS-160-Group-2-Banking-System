
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
  searchClients,
  getClientAccounts,
  deleteClient,
  deleteClientAccount,
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
  const [selectedAccounts, setSelectedAccounts] =
    useState([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] =
    useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const [deleteTarget, setDeleteTarget] = useState(null);
  const [confirmation, setConfirmation] = useState("");
  const [deleting, setDeleting] = useState(false);

  async function loadClients(search = "") {
    if (preview) {
      setClients(SAMPLE_CLIENTS);
      return;
    }

    setLoading(true);
    setError("");

    try {
      const result = await searchClients(search);

      // Expected backend response: { clients: [...] }
      if (!Array.isArray(result.clients)) {
        throw new Error(
          "The admin API returned an unexpected response."
        );
      }

      setClients(result.clients.map(normalizeClient));
    } catch (err) {
      setClients([]);
      setError(
        err.status === 404
          ? "The admin client-search endpoint has not been implemented yet."
          : err.message
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
    }
  }, [preview]);

  const filteredClients = preview
    ? clients.filter((client) => {
        const text = [
          client.name,
          client.email,
          client.username,
          client.user_id,
        ]
          .join(" ")
          .toLowerCase();

        return text.includes(query.toLowerCase());
      })
    : clients;

  const selectedClient = clients.find(
    (client) =>
      String(client.user_id) ===
      String(selectedClientId)
  );

  async function selectClient(client) {
    setSelectedClientId(client.user_id);
    setSelectedAccounts([]);
    setError("");

    if (preview) {
      setSelectedAccounts(
        client.accounts.map(normalizeAccount)
      );
      return;
    }

    setDetailLoading(true);

    try {
      const result = await getClientAccounts(
        client.user_id
      );

      if (!Array.isArray(result.accounts)) {
        throw new Error(
          "The accounts endpoint returned an unexpected response."
        );
      }

      setSelectedAccounts(
        result.accounts.map(normalizeAccount)
      );
    } catch (err) {
      setError(
        err.status === 404
          ? "Client account lookup is not available in the backend yet."
          : err.message
      );
    } finally {
      setDetailLoading(false);
    }
  }

  async function handleDelete() {
    if (
      preview ||
      confirmation !== "DELETE" ||
      !deleteTarget
    ) {
      return;
    }

    setDeleting(true);
    setError("");

    try {
      if (deleteTarget.kind === "client") {
        await deleteClient(deleteTarget.id);
        setSelectedClientId(null);
        setSelectedAccounts([]);
      } else {
        await deleteClientAccount(deleteTarget.id);
        setSelectedAccounts((prev) =>
          prev.filter(
            (account) =>
              String(account.id) !==
              String(deleteTarget.id)
          )
        );
      }

      setMessage("The deletion request was successful.");
      setDeleteTarget(null);
      setConfirmation("");
      await loadClients(query);
    } catch (err) {
      setError(
        err.status === 404 || err.status === 405
          ? "Deletion has not been enabled in the backend."
          : err.message
      );
    } finally {
      setDeleting(false);
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
      preview
        ? "sample-clients-report.csv"
        : "admin-clients-report.csv",
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
      preview
        ? "sample-account-report.csv"
        : "client-account-report.csv",
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
              Data is requested using your Auth0 token.
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
                onClick={() => loadClients(query)}
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
                if (!preview) loadClients(query);
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
                                selectClient(client)
                              }
                            >
                              View accounts
                            </button>
                            <button
                              type="button"
                              className="admin-danger-button"
                              disabled={preview}
                              onClick={() => {
                                setDeleteTarget({
                                  kind: "client",
                                  id: client.user_id,
                                  name: client.name,
                                });
                                setConfirmation("");
                              }}
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
                    <button
                      type="button"
                      className="admin-danger-button"
                      disabled={preview}
                      onClick={() => {
                        setDeleteTarget({
                          kind: "account",
                          id: account.id,
                          name: account.type,
                        });
                        setConfirmation("");
                      }}
                    >
                      <Trash2 size={15} />
                      Delete
                    </button>
                  </div>
                ))
              )}

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
              <small>Report format</small>
              <h2>CSV</h2>
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

      {deleteTarget && !preview && (
        <div className="admin-modal-backdrop">
          <div
            className="admin-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-delete-title"
          >
            <AlertTriangle size={28} />
            <h2 id="admin-delete-title">
              Confirm deletion
            </h2>
            <p>
              You are requesting deletion of{" "}
              <strong>{deleteTarget.name}</strong>.
              This may be irreversible.
            </p>
            <p>
              Type <strong>DELETE</strong> to confirm.
            </p>

            <input
              autoFocus
              value={confirmation}
              onChange={(e) =>
                setConfirmation(e.target.value)
              }
              placeholder="DELETE"
              aria-label="Type DELETE to confirm"
            />

            <div className="admin-actions">
              <button
                type="button"
                className="button"
                onClick={() => {
                  setDeleteTarget(null);
                  setConfirmation("");
                }}
              >
                Cancel
              </button>
              <button
                type="button"
                className="admin-danger-button"
                disabled={
                  confirmation !== "DELETE" ||
                  deleting
                }
                onClick={handleDelete}
              >
                {deleting
                  ? "Deleting..."
                  : "Confirm deletion"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
