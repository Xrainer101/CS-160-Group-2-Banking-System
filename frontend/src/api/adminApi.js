
import { getAccessToken } from "../auth/authService";

const BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  "http://localhost:8000";

async function adminRequest(path, options = {}) {
  const token = getAccessToken();

  if (!token) {
    throw new Error(
      "Your session is missing an Auth0 access token."
    );
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...(options.body
        ? { "Content-Type": "application/json" }
        : {}),
      ...options.headers,
    },
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const error = new Error(
      typeof data.detail === "string"
        ? data.detail
        : `Admin request failed (${response.status}).`
    );
    error.status = response.status;
    throw error;
  }

  return data;
}

// Planned API:
// GET /api/admin/clients?search=...
export function searchClients(search = "") {
  const params = new URLSearchParams();
  if (search.trim()) {
    params.set("search", search.trim());
  }

  const query = params.toString();

  return adminRequest(
    `/api/admin/clients${query ? `?${query}` : ""}`
  );
}

// Planned API:
// GET /api/admin/clients/{id}/accounts
export function getClientAccounts(clientId) {
  return adminRequest(
    `/api/admin/clients/${encodeURIComponent(
      clientId
    )}/accounts`
  );
}

// Planned API:
// DELETE /api/admin/clients/{id}
export function deleteClient(clientId) {
  return adminRequest(
    `/api/admin/clients/${encodeURIComponent(clientId)}`,
    { method: "DELETE" }
  );
}

// Planned API:
// DELETE /api/admin/accounts/{id}
export function deleteClientAccount(accountId) {
  return adminRequest(
    `/api/admin/accounts/${encodeURIComponent(accountId)}`,
    { method: "DELETE" }
  );
}
