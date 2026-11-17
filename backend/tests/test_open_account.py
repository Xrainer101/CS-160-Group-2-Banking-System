import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
import main


class OpenAccountTests(unittest.TestCase):
    def setUp(self):
        # No lifespan context: do not start Java or initialize real databases.
        self.client = TestClient(main.app)
        main.app.dependency_overrides[main.get_current_user] = lambda: {
            "banking_user": {"user_id": 42}
        }
        self.java_patch = patch.object(main.jpype, "JClass")
        self.dao = self.java_patch.start().return_value
        self.dao.createBankAccount.return_value = "1234567890"

    def tearDown(self):
        main.app.dependency_overrides.clear()
        self.java_patch.stop()
        self.client.close()

    def test_creates_each_type_for_authenticated_owner_at_zero(self):
        for account_type in ("checking", "savings"):
            with self.subTest(account_type=account_type):
                response = self.client.post("/api/accounts", json={"account_type": account_type})
                self.assertEqual(response.status_code, 201)
                self.dao.createBankAccount.assert_called_with(42, account_type, 0.0)

    def test_rejects_invalid_type_and_client_supplied_owner_or_balance(self):
        for body in (
            {"account_type": "credit"},
            {},
            {"account_type": "checking", "user_id": 99},
            {"account_type": "savings", "balance": 100},
        ):
            with self.subTest(body=body):
                self.assertEqual(self.client.post("/api/accounts", json=body).status_code, 422)
        self.dao.createBankAccount.assert_not_called()

    def test_java_failure_returns_server_error(self):
        self.dao.createBankAccount.return_value = None
        response = self.client.post("/api/accounts", json={"account_type": "checking"})
        self.assertEqual(response.status_code, 500)

    def test_requires_valid_authentication(self):
        main.app.dependency_overrides.clear()
        response = self.client.post("/api/accounts", json={"account_type": "checking"})
        self.assertIn(response.status_code, (401, 403))
        with patch.object(main, "validate_auth0_token", side_effect=ValueError("invalid")):
            response = self.client.post(
                "/api/accounts",
                json={"account_type": "checking"},
                headers={"Authorization": "Bearer invalid"},
            )
        self.assertEqual(response.status_code, 401)
        self.dao.createBankAccount.assert_not_called()
