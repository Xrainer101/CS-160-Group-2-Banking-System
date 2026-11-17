import unittest
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient
import main


def account(account_id, number):
    result = Mock()
    for method, value in {
        "getAccountId": account_id, "getAccountNumber": number,
        "getRoutingNumber": "012345678", "getAccountType": "checking",
        "getBalance": 25.5, "getStatus": "active",
    }.items():
        getattr(result, method).return_value = value
    return result


def transaction(transaction_id, timestamp):
    result = Mock()
    for method, value in {
        "getTransactionId": transaction_id, "getTimestamp": timestamp,
        "getTransactionType": "withdrawal", "getAmount": 5.0,
    }.items():
        getattr(result, method).return_value = value
    return result


class AccountDetailTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)  # No startup or real database writes.
        self.addCleanup(self.client.close)
        main.app.dependency_overrides[main.get_current_user] = lambda: {
            "banking_user": {"username": "owner"}
        }
        self.addCleanup(main.app.dependency_overrides.clear)
        self.accounts = Mock()
        self.transactions = Mock()
        self.accounts.getBankAccountsByUsername.return_value = [
            account(7, "0012345678"), account(8, "0098765432")
        ]
        self.transactions.getTransactionsByAccountNumber.return_value = []
        self.java = self.enterContext(patch.object(main.jpype, "JClass"))
        self.java.side_effect = {
            "database.BankAccountDAO": self.accounts,
            "database.TransactionDAO": self.transactions,
        }.__getitem__

    def test_owned_account_returns_numbers_and_only_its_transactions(self):
        self.transactions.getTransactionsByAccountNumber.return_value = [
            transaction(1, "2026-11-01 12:00:00"),
            transaction(2, "2026-11-02 12:00:00"),
        ]
        response = self.client.get("/api/accounts/7")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["account"]["id"], 7)
        self.assertEqual(data["account"]["account_number"], "0012345678")
        self.assertEqual(data["account"]["routing_number"], "012345678")
        self.assertEqual(data["account"]["number"], "•••• 5678")
        self.assertEqual([t["id"] for t in data["transactions"]], [2, 1])
        self.assertTrue(all(t["account"] == 7 and t["amount"] == -5 for t in data["transactions"]))
        self.accounts.getBankAccountsByUsername.assert_called_once_with("owner")
        self.transactions.getTransactionsByAccountNumber.assert_called_once_with("0012345678")

    def test_unknown_or_unowned_account_returns_404_without_transaction_lookup(self):
        response = self.client.get("/api/accounts/99")
        self.assertEqual(response.status_code, 404)
        self.assertEqual(response.json(), {"detail": "Account not found"})
        self.transactions.getTransactionsByAccountNumber.assert_not_called()

    def test_empty_activity(self):
        self.assertEqual(self.client.get("/api/accounts/8").json()["transactions"], [])

    def test_list_keeps_full_numbers_private(self):
        response = self.client.get("/api/accounts")
        self.assertEqual(response.status_code, 200)
        for item in response.json()["accounts"]:
            self.assertNotIn("account_number", item)
            self.assertNotIn("routing_number", item)

    def test_missing_and_invalid_authentication(self):
        main.app.dependency_overrides.clear()
        self.assertIn(self.client.get("/api/accounts/7").status_code, (401, 403))
        with patch.object(main, "validate_auth0_token", side_effect=ValueError("invalid")):
            response = self.client.get("/api/accounts/7", headers={"Authorization": "Bearer invalid"})
        self.assertEqual(response.status_code, 401)
        self.accounts.getBankAccountsByUsername.assert_not_called()
