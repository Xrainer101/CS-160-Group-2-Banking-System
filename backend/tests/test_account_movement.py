import unittest
from unittest.mock import Mock, patch
from fastapi.testclient import TestClient
import main


class MovementTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)
        self.addCleanup(self.client.close)
        main.app.dependency_overrides[main.get_current_user] = lambda: {"banking_user": {"username": "owner"}}
        self.addCleanup(main.app.dependency_overrides.clear)
        self.bank = Mock()
        self.transactions = Mock()
        self.account = Mock()
        self.account.getAccountId.return_value = 7
        self.account.getAccountNumber.return_value = "0012345678"
        self.account.getStatus.return_value = "active"
        self.account.getBalance.return_value = 23.0
        self.bank.getBankAccountsByUsername.return_value = [self.account]
        self.transactions.deposit.return_value = True
        self.transactions.withdraw.return_value = True
        java = self.enterContext(patch.object(main.jpype, "JClass"))
        java.side_effect = {"database.BankAccountDAO": self.bank, "database.TransactionDAO": self.transactions}.__getitem__

    def post(self, operation, amount, account_id=7):
        return self.client.post(f"/api/accounts/{account_id}/{operation}", json={"amount": amount})

    def test_valid_deposit_and_exact_balance_withdrawal(self):
        self.assertEqual(self.post("deposit", "25.50").status_code, 200)
        self.assertEqual(self.post("withdraw", "23").status_code, 200)
        self.transactions.deposit.assert_called_once_with("0012345678", 25.5)
        self.transactions.withdraw.assert_called_once_with("0012345678", 23.0)
        self.bank.getBankAccountsByUsername.assert_called_with("owner")

    def test_amount_validation_is_shared(self):
        for operation in ("deposit", "withdraw"):
            for value in ("", "abc", "NaN", "Infinity", "1.001", True, "1e2"):
                with self.subTest(operation=operation, value=value):
                    response = self.post(operation, value)
                    self.assertEqual(response.status_code, 422)
                    self.assertEqual(response.json()["detail"]["message"], "Enter an amount like 25 or 25.50")
            for value in ("0", "-5", 0):
                response = self.post(operation, value)
                self.assertEqual(response.status_code, 422)
                self.assertEqual(response.json()["detail"]["message"], "Enter an amount greater than $0.00")
        self.transactions.deposit.assert_not_called()
        self.transactions.withdraw.assert_not_called()

    def test_withdrawal_checks_current_balance(self):
        response = self.post("withdraw", "25")
        self.assertEqual(response.status_code, 409)
        self.assertEqual(response.json()["detail"]["message"], "That's more than the $23.00 available")
        self.transactions.withdraw.assert_not_called()

    def test_account_ownership_and_status(self):
        for operation in ("deposit", "withdraw"):
            self.assertEqual(self.post(operation, "1", 99).status_code, 404)
            self.account.getStatus.return_value = "closed"
            self.assertEqual(self.post(operation, "1").status_code, 404)
            self.account.getStatus.return_value = "frozen"
            self.assertEqual(self.post(operation, "1").status_code, 409)
            self.account.getStatus.return_value = "active"
        self.transactions.deposit.assert_not_called()
        self.transactions.withdraw.assert_not_called()

    def test_java_failure_and_extra_fields(self):
        self.transactions.deposit.return_value = False
        self.assertEqual(self.post("deposit", "1").status_code, 409)
        self.assertEqual(self.client.post("/api/accounts/7/deposit", json={"amount": "1", "user_id": 99}).status_code, 422)

    def test_authentication(self):
        main.app.dependency_overrides.clear()
        self.assertIn(self.post("deposit", "1").status_code, (401, 403))
        with patch.object(main, "validate_auth0_token", side_effect=ValueError("invalid")):
            response = self.client.post("/api/accounts/7/withdraw", json={"amount": "1"}, headers={"Authorization": "Bearer invalid"})
        self.assertEqual(response.status_code, 401)
        self.transactions.withdraw.assert_not_called()
