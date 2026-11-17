import { getAccessToken } from "../auth/authService";

const BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  "http://localhost:8000";

async function managerRequest(path, options = {}) {
  const token = getAccessToken();

  if (!token) {
    throw new Error(
      "Your session is missing an Auth0 access token."
    );
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.headers || {}),
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const error = new Error(
      typeof data.detail === "string"
        ? data.detail
        : `Manager request failed (${response.status}).`
    );
    error.status = response.status;
    throw error;
  }

  return data;
}

export async function getManagerPermission() {
  try {
    return await managerRequest(
      "/api/manager/permission-check"
    );
  } catch (error) {
    if (error.status === 403) {
      return { authorized: false };
    }
    throw error;
  }
}

export function getManagerSummary() {
  return managerRequest("/api/manager/reports/summary");
}

export function getManagerCustomers() {
  return managerRequest("/api/manager/reports/customers");
}

export function getManagerCustomerAccounts(userId) {
  return managerRequest(
    `/api/manager/reports/customers/${encodeURIComponent(
      userId
    )}/accounts`
  );
}

export function getManagerCustomerTransactions(userId) {
  return managerRequest(
    `/api/manager/reports/customers/${encodeURIComponent(
      userId
    )}/transactions`
  );
}

export function closeManagerCustomerAccount(accountId) {
  return managerRequest(
    `/api/manager/reports/accounts/${encodeURIComponent(accountId)}`,
    { method: "DELETE" }
  );
}

export function deactivateManagerCustomer(userId) {
  return managerRequest(
    `/api/manager/reports/customers/${encodeURIComponent(userId)}`,
    { method: "DELETE" }
  );
}
