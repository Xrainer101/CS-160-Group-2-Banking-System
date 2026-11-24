import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient
import main

from test_account_detail import account, transaction


class TransferTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)
        self.addCleanup(self.client.close)
        main.app.dependency_overrides[main.get_current_user] = lambda: {
            "banking_user": {"user_id": 4, "username": "owner"}
        }
        self.addCleanup(main.app.dependency_overrides.clear)

        self.bank = Mock()
        self.ledger = Mock()
        self.external = Mock()
        self.source = account(7, "0012345678")
        self.savings = account(8, "0098765432")
        self.customer = account(9, "0000000009")
        self.bank.getBankAccountsByUsername.return_value = [self.source, self.savings]
        self.bank.getBankAccountByNumber.return_value = self.customer
        self.ledger.transfer.return_value = True
        self.ledger.transferToExternal.return_value = True
        self.saved = Mock()
        self.saved.getExternalAccountId.return_value = 11
        self.saved.getNickname.return_value = None
        self.saved.getBankName.return_value = "Other Bank"
        self.saved.getAccountNumber.return_value = "000044445555"
        self.external.getExternalAccountsByUserId.return_value = [self.saved]
        self.java = self.enterContext(patch.object(main.jpype, "JClass"))
        self.java.side_effect = {
            "database.BankAccountDAO": self.bank,
            "database.TransactionDAO": self.ledger,
            "database.ExternalAccountDAO": self.external,
        }.__getitem__
        tomorrow = datetime.now(main.TRANSFER_TIME_ZONE).date() + timedelta(days=1)
        self.tomorrow = tomorrow.isoformat()
        self.scheduled_utc = datetime.fromisoformat(self.tomorrow).replace(
            tzinfo=main.TRANSFER_TIME_ZONE
        ).astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
        self.own_target = {"kind": "own_account", "account_id": 8}
        self.customer_target = {"kind": "bank_customer", "account_number": "0000000009"}
        self.external_target = {"kind": "external_account", "external_account_id": 11}

    def post(self, **changes):
        return self.client.post("/api/transfers", json={
            "source_account_id": 7, "destination": self.own_target, "amount": "25.50", **changes
        })

    def test_due_processing_uses_session_owner_and_utc_cutoff(self):
        self.ledger.processDueTransfers.return_value.getCompleted.return_value = 2
        self.ledger.processDueTransfers.return_value.getFailed.return_value = 1
        response = self.client.post("/api/transfers/process-due")
        self.assertEqual(response.json(), {"completed": 2, "failed": 1})
        user_id, cutoff = self.ledger.processDueTransfers.call_args.args
        self.assertEqual(user_id, 4)
        cutoff_time = datetime.fromisoformat(cutoff).replace(tzinfo=timezone.utc)
        self.assertLess(abs((datetime.now(timezone.utc) - cutoff_time).total_seconds()), 5)

    def test_due_processing_requires_authentication(self):
        main.app.dependency_overrides.clear()
        response = self.client.post("/api/transfers/process-due")
        self.assertIn(response.status_code, (401, 403))
        self.ledger.processDueTransfers.assert_not_called()

    def test_immediate_transfers_dispatch_by_destination(self):
        cases = [
            (self.own_target, self.ledger.transfer, "0098765432"),
            (self.customer_target, self.ledger.transfer, "0000000009"),
            (self.external_target, self.ledger.transferToExternal, 11),
        ]
        for destination, method, target in cases:
            with self.subTest(destination=destination):
                method.reset_mock()
                result = self.post(destination=destination)
                self.assertEqual(result.status_code, 200)
                self.assertEqual(result.json(), {"status": "completed"})
                method.assert_called_once_with("0012345678", target, 25.5)
        self.bank.getBankAccountByNumber.assert_called_once_with("0000000009")

    def test_dated_transfers_use_pending_overload_without_checking_funds(self):
        cases = [
            (self.own_target, self.ledger.transfer, "0098765432"),
            (self.customer_target, self.ledger.transfer, "0000000009"),
            (self.external_target, self.ledger.transferToExternal, 11),
        ]
        for destination, method, target in cases:
            with self.subTest(destination=destination):
                method.reset_mock()
                result = self.post(destination=destination, scheduled_for=self.tomorrow, amount="50")
                self.assertEqual(result.status_code, 200)
                self.assertEqual(result.json(), {"status": "pending", "scheduled_for": self.tomorrow})
                method.assert_called_once_with("0012345678", target, 50.0, self.scheduled_utc)
        self.source.getBalance.assert_not_called()

    def test_unusable_accounts_never_write(self):
        self.assertEqual(self.post(source_account_id=99).status_code, 404)
        self.assertEqual(self.post(destination={"kind": "own_account", "account_id": 9}).status_code, 404)
        self.assertEqual(self.post(destination={"kind": "own_account", "account_id": 7}).status_code, 422)
        self.assertEqual(self.post(destination={"kind": "external_account", "external_account_id": 13}).status_code, 404)
        self.customer.getStatus.return_value = "closed"
        self.assertEqual(self.post(destination=self.customer_target, scheduled_for=self.tomorrow).status_code, 404)
        self.bank.getBankAccountByNumber.return_value = None
        self.assertEqual(self.post(destination=self.customer_target).status_code, 404)
        self.source.getStatus.return_value = "frozen"
        self.assertEqual(self.post().status_code, 409)
        self.ledger.transfer.assert_not_called()
        self.ledger.transferToExternal.assert_not_called()

    def test_validation_and_insufficient_funds(self):
        today = datetime.now(main.TRANSFER_TIME_ZONE).date()
        for value in (today.isoformat(), (today - timedelta(days=1)).isoformat(), "2027-02-30", 0):
            with self.subTest(scheduled_for=value):
                self.assertEqual(self.post(scheduled_for=value).status_code, 422)
        for amount in ("0", "-1", "1.234", "NaN"):
            with self.subTest(amount=amount):
                self.assertEqual(self.post(amount=amount).status_code, 422)
        self.assertEqual(self.post(amount="25.51").status_code, 409)
        self.assertEqual(self.post(destination={**self.own_target, "external_account_id": 11}).status_code, 422)
        self.assertEqual(self.post(destination={**self.customer_target, "account_number": "0009"}).status_code, 422)
        self.ledger.transfer.assert_not_called()

    def test_external_listing_is_owned_and_masked(self):
        response = self.client.get("/api/external-accounts")
        self.assertEqual(response.json(), [{"id": 11, "name": "Other Bank", "number": "•••• 5555"}])
        self.external.getExternalAccountsByUserId.assert_called_once_with(4)

    def test_authentication_required(self):
        main.app.dependency_overrides.clear()
        self.assertIn(self.post().status_code, (401, 403))
        self.assertIn(self.client.get("/api/external-accounts").status_code, (401, 403))
        self.java.assert_not_called()

    def test_activity_exposes_pending_status_and_date(self):
        pending = transaction(1, "2026-11-01 12:00:00")
        pending.getTransactionType.return_value = "transfer_out"
        pending.getStatus.return_value = "pending"
        pending.getScheduledFor.return_value = self.tomorrow + " 00:00:00"
        self.ledger.getTransactionsByAccountNumber.return_value = [pending]
        response = self.client.get("/api/accounts/7")
        self.assertEqual(response.status_code, 200)
        item = response.json()["transactions"][0]
        self.assertEqual(item["status"], "pending")
        self.assertEqual(item["scheduled_for"], self.tomorrow + " 00:00:00")
