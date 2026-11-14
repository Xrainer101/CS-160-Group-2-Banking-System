
import { apiRequest } from "./client";

// Proposed API contract.
// Requires backend implementation or confirmation.

export function getTransactions(token, accountId) {
  return apiRequest(
    `/api/accounts/${encodeURIComponent(accountId)}/transactions`,
    { token }
  );
}

export function transferFunds(
  token,
  fromAccountId,
  toAccountId,
  amount
) {
  return apiRequest("/api/transfers", {
    method: "POST",
    token,
    body: JSON.stringify({
      from_account_id: fromAccountId,
      to_account_id: toAccountId,
      amount,
    }),
  });
}
