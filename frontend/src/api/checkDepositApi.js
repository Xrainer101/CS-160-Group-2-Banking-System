import { getAccessToken } from "../auth/authService";

const BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ||
  "http://localhost:8000"
).replace(/\/$/, "");

async function authorizedFetch(path, options = {}) {
  const token = getAccessToken();

  if (!token) {
    throw new Error(
      "Please sign in with Auth0 before submitting a check."
    );
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/json",
      ...(options.headers || {}),
    },
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const message =
      typeof data.detail === "string"
        ? data.detail
        : "The check-deposit request failed.";

    throw new Error(message);
  }

  return data;
}

export async function submitCheckDeposit({
  accountId,
  amount,
  front,
  back,
}) {
  const form = new FormData();

  form.append("account_id", String(accountId));
  form.append("amount", String(amount));
  form.append("front_image", front);
  form.append("back_image", back);

  // Do not set Content-Type manually.
  // The browser adds the multipart boundary.
  return authorizedFetch("/api/check-deposits", {
    method: "POST",
    body: form,
  });
}

export function getCheckDeposits() {
  return authorizedFetch("/api/check-deposits");
}