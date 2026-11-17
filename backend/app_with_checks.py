import hashlib
import io
import os
import re
import sqlite3
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

import jpype
from fastapi import (
    Depends,
    File,
    Form,
    HTTPException,
    UploadFile,
)
from PIL import Image, UnidentifiedImageError

from main import app, get_current_user


MAX_FILE_SIZE = 5 * 1024 * 1024
MAX_PIXELS = 20_000_000

CHECK_DB_PATH = Path(
    os.getenv(
        "CHECK_DEPOSIT_DB_PATH",
        str(
            Path(__file__).resolve().parent /
            "check_deposit_requests.db"
        ),
    )
)


def connect_check_db():
    connection = sqlite3.connect(CHECK_DB_PATH)
    connection.row_factory = sqlite3.Row

    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS check_deposits (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            account_id INTEGER NOT NULL,
            amount_cents INTEGER NOT NULL,
            fingerprint TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending',
            created_at TEXT NOT NULL,
            UNIQUE(user_id, fingerprint)
        )
        """
    )

    connection.commit()
    return connection


def account_belongs_to_user(user, account_id):
    username = user["banking_user"]["username"]

    dao = jpype.JClass("database.BankAccountDAO")

    accounts = dao.getBankAccountsByUsername(username)

    for account in accounts:
        if int(account.getAccountId()) == account_id:
            if str(account.getStatus()) != "active":
                raise HTTPException(
                    status_code=409,
                    detail="Selected account is not active.",
                )

            return True

    return False


async def read_valid_image(upload):
    if upload.content_type not in (
        "image/jpeg",
        "image/png",
    ):
        raise HTTPException(
            status_code=422,
            detail="Images must be JPEG or PNG.",
        )

    data = await upload.read(MAX_FILE_SIZE + 1)

    if not data or len(data) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=422,
            detail="Each image must be under 5 MB.",
        )

    try:
        with Image.open(io.BytesIO(data)) as image:
            if image.format not in ("JPEG", "PNG"):
                raise ValueError("Unsupported image format")

            width, height = image.size

            if width < 400 or height < 250:
                raise ValueError("Image resolution is too low")

            if width * height > MAX_PIXELS:
                raise ValueError("Image resolution is too large")

            image.verify()

    except (
        UnidentifiedImageError,
        OSError,
        ValueError,
        Image.DecompressionBombError,
    ):
        raise HTTPException(
            status_code=422,
            detail="Invalid or unsupported image.",
        )

    # The raw image is deliberately not stored.
    return data


def parse_amount(amount):
    text = amount.strip()

    if not re.fullmatch(r"\d+(?:\.\d{1,2})?", text):
        raise HTTPException(
            status_code=422,
            detail="Enter an amount like 25 or 25.50.",
        )

    value = Decimal(text)

    if value <= 0 or value > Decimal("10000.00"):
        raise HTTPException(
            status_code=422,
            detail="Amount must be between $0.01 and $10,000.",
        )

    return int(value * 100)


def serialize_deposit(row):
    return {
        "id": row["id"],
        "account_id": row["account_id"],
        "amount": row["amount_cents"] / 100,
        "status": row["status"],
        "created_at": row["created_at"],
    }


@app.post("/api/check-deposits", status_code=201)
async def submit_check_deposit(
    account_id: int = Form(...),
    amount: str = Form(...),
    front_image: UploadFile = File(...),
    back_image: UploadFile = File(...),
    user=Depends(get_current_user),
):
    if not account_belongs_to_user(user, account_id):
        raise HTTPException(
            status_code=404,
            detail="Bank account not found.",
        )

    cents = parse_amount(amount)

    front_bytes = await read_valid_image(front_image)
    back_bytes = await read_valid_image(back_image)

    try:
        if front_bytes == back_bytes:
            raise HTTPException(
                status_code=422,
                detail="Front and back images must differ.",
            )

        fingerprint = hashlib.sha256(
            hashlib.sha256(front_bytes).digest() +
            hashlib.sha256(back_bytes).digest()
        ).hexdigest()

        user_id = int(user["banking_user"]["user_id"])

        timestamp = datetime.now(timezone.utc).isoformat()

        connection = connect_check_db()

        try:
            cursor = connection.execute(
                """
                INSERT INTO check_deposits
                    (
                        user_id,
                        account_id,
                        amount_cents,
                        fingerprint,
                        status,
                        created_at
                    )
                VALUES (?, ?, ?, ?, 'pending', ?)
                """,
                (
                    user_id,
                    account_id,
                    cents,
                    fingerprint,
                    timestamp,
                ),
            )

            connection.commit()

            new_id = cursor.lastrowid

        except sqlite3.IntegrityError:
            raise HTTPException(
                status_code=409,
                detail=(
                    "These same check images were already "
                    "submitted. Use a different fictional check."
                ),
            )
        finally:
            connection.close()

        return {
            "message": (
                "Demo check submitted for review. "
                "No funds were credited."
            ),
            "deposit": {
                "id": new_id,
                "account_id": account_id,
                "amount": cents / 100,
                "status": "pending",
                "created_at": timestamp,
            },
        }

    finally:
        # No uploaded photos are saved to disk.
        del front_bytes
        del back_bytes


@app.get("/api/check-deposits")
def list_check_deposits(
    user=Depends(get_current_user),
):
    user_id = int(user["banking_user"]["user_id"])

    connection = connect_check_db()

    try:
        rows = connection.execute(
            """
            SELECT
                id,
                account_id,
                amount_cents,
                status,
                created_at
            FROM check_deposits
            WHERE user_id = ?
            ORDER BY id DESC
            LIMIT 100
            """,
            (user_id,),
        ).fetchall()

        return {
            "deposits": [
                serialize_deposit(row)
                for row in rows
            ]
        }

    finally:
        connection.close()