
import { apiRequest } from "./client";

// Proposed API contract.
// Requires backend implementation or confirmation.

export function getAccounts(token) {
  return apiRequest("/api/accounts", { token });
}

export function createAccount(token, accountType) {
  return apiRequest("/api/accounts", {
    method: "POST",
    token,
    body: JSON.stringify({
      account_type: accountType,
    }),
  });
}
