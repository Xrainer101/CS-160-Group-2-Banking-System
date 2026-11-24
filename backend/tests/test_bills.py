from datetime import datetime, timedelta
from types import SimpleNamespace
from unittest.mock import Mock

from fastapi.testclient import TestClient

import main


def test_bill_routes_use_the_signed_in_owner(monkeypatch):
    dao = Mock()
    monkeypatch.setattr(main.jpype, "JClass", lambda name: dao)
    monkeypatch.setitem(main.app.dependency_overrides, main.get_current_user, lambda: {
        "banking_user": {"user_id": 4},
    })
    day = datetime.now(main.TRANSFER_TIME_ZONE).date()
    payment_date = day.isoformat()
    bill = SimpleNamespace(
        id=lambda: 8, occurrenceId=lambda: 12, accountId=lambda: 7, name=lambda: "Internet", amount=lambda: 25.50,
        paymentDate=lambda: payment_date, category=lambda: "Internet / Phone",
        frequency=lambda: "monthly", memo=lambda: "service", status=lambda: "pending",
        failureReason=lambda: None, sourceAccountLabel=lambda: "Checking •••• 1234",
    )
    dao.scheduleBill.return_value = bill
    dao.getBillSchedules.return_value = [bill]
    dao.cancelBill.return_value = True
    request = {
        "source_account_id": 7, "name": " Internet ", "amount": "25.50",
        "payment_date": payment_date, "category": "Internet / Phone",
        "frequency": "monthly", "memo": " service ",
    }
    client = TestClient(main.app)
    try:
        created = client.post("/api/bills", json=request)
        assert created.status_code == 201
        assert created.json()["status"] == "pending"
        assert created.json()["occurrenceId"] == 12
        assert created.json()["sourceAccountLabel"] == "Checking •••• 1234"
        dao.scheduleBill.assert_called_once_with(4, 7, "Internet", 25.50, payment_date,
                                                 "monthly", "Internet / Phone", "service")
        assert client.get("/api/bills").json() == [created.json()]
        dao.getBillSchedules.assert_called_once_with(4)
        bill.failureReason = lambda: "Insufficient funds on the day the bill was processed."
        assert client.get("/api/bills").json()[0]["failureReason"] == "Not enough money in Checking •••• 1234."
        assert client.delete("/api/bills/8").status_code == 204
        dao.cancelBill.assert_called_once_with(4, 8)
        dao.retryFailedBill.return_value.failureReason.return_value = "Insufficient funds."
        retried = client.post("/api/bills/occurrences/12/retry")
        assert retried.json() == {"status": "failed", "failureReason": "Insufficient funds."}
        dao.retryFailedBill.assert_called_once_with(4, 12)
        dao.retryFailedBill.return_value.failureReason.return_value = None
        assert client.post("/api/bills/occurrences/12/retry").json()["status"] == "completed"
        dao.retryFailedBill.return_value = None
        assert client.post("/api/bills/occurrences/12/retry").status_code == 404
        dao.cancelBill.return_value = False
        assert client.delete("/api/bills/17").status_code == 404
        assert client.post("/api/bills", json={**request, "payment_date": (day - timedelta(days=1)).isoformat()}).status_code == 422
        dao.scheduleBill.return_value = None
        assert client.post("/api/bills", json=request).status_code == 409
    finally:
        client.close()
