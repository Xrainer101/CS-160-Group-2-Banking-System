import unittest
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient
import main


class CloseAccountTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)  # Do not run startup or touch databases.
        self.addCleanup(self.client.close)
        main.app.dependency_overrides[main.get_current_user] = lambda: {
            "banking_user": {"username": "owner"}
        }
        self.addCleanup(main.app.dependency_overrides.clear)
        self.dao = Mock()
        self.account = Mock()
        self.account.getAccountId.return_value = 7
        self.account.getAccountNumber.return_value = "0012345678"
        self.account.getStatus.return_value = "active"
        self.account.getBalance.return_value = 0.0
        self.dao.getBankAccountsByUsername.return_value = [self.account]
        self.dao.closeBankAccount.return_value = True
        self.java = self.enterContext(patch.object(main.jpype, "JClass", return_value=self.dao))

    def test_zero_balance_closes_using_java_without_deleting(self):
        response = self.client.post("/api/accounts/7/close")
        self.assertEqual(response.status_code, 200)
        self.dao.getBankAccountsByUsername.assert_called_once_with("owner")
        self.dao.closeBankAccount.assert_called_once_with("0012345678")
        self.dao.deleteBankAccount.assert_not_called()

    def test_current_nonzero_balance_rejected(self):
        for balance in (23.0, -1.0, 0.001):
            with self.subTest(balance=balance):
                self.account.getBalance.return_value = balance
                response = self.client.post("/api/accounts/7/close")
                self.assertEqual(response.status_code, 409)
                self.assertEqual(response.json()["detail"]["balance"], balance)
        self.dao.closeBankAccount.assert_not_called()

    def test_unknown_or_unowned_account_is_not_closed(self):
        self.assertEqual(self.client.post("/api/accounts/99/close").status_code, 404)
        self.dao.closeBankAccount.assert_not_called()

    def test_closed_account_hidden_and_cannot_close_again(self):
        self.account.getStatus.return_value = "closed"
        self.assertEqual(self.client.post("/api/accounts/7/close").status_code, 404)
        self.assertEqual(self.client.get("/api/accounts/7").status_code, 404)
        self.assertEqual(self.client.get("/api/accounts").json(), {"accounts": [], "transactions": []})
        self.dao.closeBankAccount.assert_not_called()
        self.dao.getTransactionsByAccountNumber.assert_not_called()

    def test_java_failure(self):
        self.dao.closeBankAccount.return_value = False
        self.assertEqual(self.client.post("/api/accounts/7/close").status_code, 500)

    def test_requires_authentication(self):
        main.app.dependency_overrides.clear()
        self.assertIn(self.client.post("/api/accounts/7/close").status_code, (401, 403))
        with patch.object(main, "validate_auth0_token", side_effect=ValueError("invalid")):
            response = self.client.post("/api/accounts/7/close", headers={"Authorization": "Bearer invalid"})
        self.assertEqual(response.status_code, 401)
        self.dao.closeBankAccount.assert_not_called()
