# backend/main.py
import os
import sqlite3
import json
import logging
import time
import re
from decimal import Decimal
from typing import Annotated, Literal
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from urllib.parse import quote
from datetime import date, datetime, timedelta, timezone
import bcrypt   # For securely hashing passwords
import jwt      # For creating and verifying JSON Web Tokens (JWTs)
from jwt import PyJWKClient  # Gets Auth0's public keys for token validation
from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware  # Backend and frontend communication
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer   # Bearer Token authentication
from pydantic import BaseModel, EmailStr, Field, field_validator # Validate request data
import jpype

logger = logging.getLogger("uvicorn.error")

# Basic settings 
DATABASE_PATH = os.getenv(
    "DATABASE_PATH",
    os.path.join(os.path.dirname(__file__), "bank.db")
)
# Security settings
JWT_SECRET = "local-development-secret-key-for-testing-only"
AUTH0_DOMAIN = os.getenv("AUTH0_DOMAIN")
AUTH0_AUDIENCE = os.getenv("AUTH0_AUDIENCE")
AUTH0_MANAGEMENT_CLIENT_ID = os.getenv("AUTH0_MANAGEMENT_CLIENT_ID")
AUTH0_MANAGEMENT_CLIENT_SECRET = os.getenv("AUTH0_MANAGEMENT_CLIENT_SECRET")
AUTH0_MANAGEMENT_AUDIENCE = os.getenv("AUTH0_MANAGEMENT_AUDIENCE", f"https://{AUTH0_DOMAIN}/api/v2/" if AUTH0_DOMAIN else "")
AUTH0_ISSUER = f"https://{AUTH0_DOMAIN}/" if AUTH0_DOMAIN else ""
AUTH0_JWKS_URL = f"https://{AUTH0_DOMAIN}/.well-known/jwks.json" if AUTH0_DOMAIN else ""
auth0_jwks_client = PyJWKClient(AUTH0_JWKS_URL) if AUTH0_JWKS_URL else None
LOCKOUT_LIMIT = 10
LOCKOUT_MINUTES = 15
AUTH0_USERINFO_CACHE_TTL_SECONDS = 300
_auth0_userinfo_cache = {}

# def normalize_phone_number(value):
#     return re.sub(r"\D", "", str(value))

app = FastAPI(title="Online Banking API")   # Create FastAPI app

# CORS settings
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register Request 
# JSON registration 
class RegisterRequest(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    email: EmailStr
    username: str = Field(min_length=3, max_length=30)
    password: str = Field(min_length=8)

class LoginRequest(BaseModel):
    username: str
    password: str
# Information needed to check whether an email, username, or phone number is already in use
class ProfileAvailabilityRequest(BaseModel):
    email: EmailStr
    username: str = Field(min_length=3, max_length=30)
    phone_number: str = Field(min_length=1, max_length=30)

class DeletedUsernameRequest(BaseModel):
    username: str = Field(min_length=1, max_length=30)

# Database functions
def get_database():
    database = sqlite3.connect(DATABASE_PATH)
    database.row_factory = sqlite3.Row
    try:
        yield database
    finally:
        database.close()

# Database initialization creating the users table if it doesn't exist
def initialize_database():
    database = sqlite3.connect(DATABASE_PATH)
    database.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            user_id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT NOT NULL UNIQUE,
            username TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            failed_attempts INTEGER NOT NULL DEFAULT 0,
            lock_until TEXT
        )
        """
    )
    database.commit()
    database.close()

# Startup event to initialize the database when the application starts
@app.on_event("startup")
def startup():
    from java_database import initialize_java_database; 
    initialize_java_database()

    initialize_database()

# Password Hashing
def hash_password(password: str) -> str:    # Hashes plain text passwords
    hashed_password = bcrypt.hashpw(    # Generates secure hash
        password.encode("utf-8"),       # Converts string into bytes
        bcrypt.gensalt()                # Generates a random salt
    )
    return hashed_password.decode("utf-8")

# Password Verification
def check_password(password: str, password_hash: str) -> bool:
    return bcrypt.checkpw(
        password.encode("utf-8"),
        password_hash.encode("utf-8")
    )

# Token functions JWT Creation
def create_token(user_id: int) -> str:  # Creates authentication token for a user
    expiration = datetime.now(timezone.utc) + timedelta(minutes=30) # Expires in 30 minutes
    token_data = {
        "sub": str(user_id),
        "exp": expiration
    }
    return jwt.encode(token_data, JWT_SECRET, algorithm="HS256")  # Token signed with secret key

# Bearer Authentication
security = HTTPBearer()

# Auth0 token validation
def validate_auth0_token(token: str):
    if auth0_jwks_client is None:
        raise Exception("Auth0 is not configured")
    signing_key = auth0_jwks_client.get_signing_key_from_jwt(token).key

    return jwt.decode( # Decodes and validates the Auth0 token
        token,
        signing_key,
        algorithms=["RS256"],
        audience=AUTH0_AUDIENCE,
        issuer=AUTH0_ISSUER
    )

# Get Auth0 user profile
def get_auth0_userinfo(token: str):
    cached = _auth0_userinfo_cache.get(token)
    if cached and time.monotonic() - cached["created_at"] < AUTH0_USERINFO_CACHE_TTL_SECONDS:
        return cached["profile"]

    userinfo_url = f"https://{AUTH0_DOMAIN}/userinfo"
    request = Request(
        userinfo_url,
        headers={"Authorization": f"Bearer {token}"}
    )
    try:
        with urlopen(request, timeout=10) as response:
            profile = json.loads(response.read().decode("utf-8"))
            _auth0_userinfo_cache[token] = {
                "created_at": time.monotonic(),
                "profile": profile,
            }
            return profile
    except Exception as e:
        raise HTTPException(
            status_code=502,
            detail=f"Unable to retrieve Auth0 user profile: {e}"
        )

# Delete Auth0 user account
def delete_auth0_user(auth0_sub: str) -> None:
    if not AUTH0_MANAGEMENT_CLIENT_ID or not AUTH0_MANAGEMENT_CLIENT_SECRET:
        raise HTTPException(
            status_code=500,
            detail="Auth0 account deletion is not configured on the backend",
        )

    token_request = Request(
        f"https://{AUTH0_DOMAIN}/oauth/token",
        data=json.dumps({
            "grant_type": "client_credentials",
            "client_id": AUTH0_MANAGEMENT_CLIENT_ID,
            "client_secret": AUTH0_MANAGEMENT_CLIENT_SECRET,
            "audience": AUTH0_MANAGEMENT_AUDIENCE,
        }).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(token_request, timeout=10) as response:
            management_token = json.loads(response.read().decode("utf-8"))["access_token"]
    except Exception as exc:
        logger.exception("Unable to obtain Auth0 Management API token")
        raise HTTPException(status_code=502, detail="Unable to contact Auth0 for account deletion") from exc

    delete_request = Request(
        f"https://{AUTH0_DOMAIN}/api/v2/users/{quote(auth0_sub, safe='')}",
        headers={"Authorization": f"Bearer {management_token}"},
        method="DELETE",
    )
    try:
        with urlopen(delete_request, timeout=10):
            return
    except HTTPError as exc:
        if exc.code == 404:
            return
        logger.exception("Auth0 rejected deletion of user %s", auth0_sub)
        raise HTTPException(status_code=502, detail="Auth0 could not delete the customer account") from exc
    except Exception as exc:
        logger.exception("Unable to delete Auth0 user %s", auth0_sub)
        raise HTTPException(status_code=502, detail="Unable to contact Auth0 for account deletion") from exc

# Link Auth0 user to banking user
def link_auth0_user(token_data):
    """Resolve Auth0 subject to a Java banking user and creating one if needed."""
    user_dao = jpype.JClass("database.UserDAO")
    auth0_sub = token_data["sub"]
    is_new_user = False       # Stays true only when this login creates the user's local banking profile
    banking_user = user_dao.getUserByAuth0Sub(auth0_sub)
    if banking_user is not None:
        profile_status = user_dao.getProfileStatusByAuth0Sub(auth0_sub)
        if str(profile_status or "").lower() == "inactive":
            raise HTTPException(
                status_code=403,
                detail="Account has been deleted. Please contact support.",
            )
    if banking_user is None and token_data.get("email"):
        banking_user = user_dao.linkAuth0SubByEmail(
            auth0_sub,
            token_data["email"]
        )
    # If no banking user exists then create one
    if banking_user is None:
        email = token_data.get("email")
        if not email:
            raise HTTPException(
                status_code=400,
                detail="Auth0 token is missing email needed to create a banking profile"
            )
        # Get the user's registration information from the Auth0 token.
        metadata = token_data.get("user_metadata") or {}
        # Prefer the full name collected during registration over Auth0's display name
        full_name = str(
            token_data.get("full_name")
            or metadata.get("full_name")
            or token_data.get("name")
            or ""
        ).strip()
        name_parts = full_name.split(maxsplit=1)
        # Use Auth0's first/last name fields, or split the full name if needed.
        first_name = str(token_data.get("given_name") or metadata.get("first_name") or (name_parts[0] if name_parts else "Auth0"))
        last_name = str(token_data.get("family_name") or metadata.get("last_name") or (name_parts[1] if len(name_parts) > 1 else "User"))
        email_name = str(email).split("@", 1)[0]
        subject_name = auth0_sub.split("|", 1)[-1][:8]
        username = str(token_data.get("username") or metadata.get("username") or f"{email_name}_{subject_name}")[:30]
        # Check both the token and Auth0 metadata for the phone number.
        phone_value = token_data.get("phone_number") or metadata.get("phone_number")
        # A phone number is required before we can create the banking profile.
        if not phone_value:
            raise HTTPException(
                status_code=400,
                detail="Auth0 profile is missing a phone number needed to create a banking profile"
            )
        phone_number = str(phone_value).strip()  # Convert it to text and remove extra spaces.
#         phone_number = normalize_phone_number(phone_value)
#                 if len(phone_number) != 10:
#                     raise HTTPException(
#                         status_code=400,
#                         detail="Phone number must contain 10 digits"
#                     )
        date_of_birth = token_data.get("date_of_birth") or metadata.get("date_of_birth")

        # Check whether another banking profile already uses these values.
        conflict = user_dao.findAuth0ProfileConflict(username,email,phone_number)
        if conflict == "database":
            raise HTTPException(
                status_code=500,
                detail="Unable to check for duplicate banking profile data"
            )
        if conflict:
            labels = {
                "username": "Username",
                "email": "Email",
                "phone_number": "Phone number",
            }
            raise HTTPException(
                status_code=409,
                detail=f"{labels[conflict]} is already in use"
            )

        banking_user = user_dao.registerAuth0User(
            auth0_sub,
            username,
            first_name,
            last_name,
            email,
            phone_number,
            date_of_birth,
        )
        if banking_user is None:
            raise HTTPException(
                status_code=500,
                detail="Unable to create a banking profile for this Auth0 account"
            )
        is_new_user = True
        from java_database import create_default_accounts
        create_default_accounts(int(banking_user.getUserId()))

    metadata = token_data.get("user_metadata") or {}
    auth0_full_name = str(token_data.get("full_name") or metadata.get("full_name") or "").strip()
    banking_name = f"{banking_user.getFirstName()} {banking_user.getLastName()}".strip()
    display_name = auth0_full_name or banking_name

    return {
        "user_id": int(banking_user.getUserId()),
        "username": str(banking_user.getUsername()),
        "name": display_name,
        "full_name": display_name,
        "email": str(banking_user.getEmail()),
        "phone_number": str(banking_user.getPhoneNumber()),
        "date_of_birth": (
            str(banking_user.getDateOfBirth())
            if banking_user.getDateOfBirth() is not None
            else None
        ),
        "is_new_user": is_new_user,
    }

# Check Auth0 token
@app.get("/api/auth/token-check")
def check_auth0_token(credentials: HTTPAuthorizationCredentials = Depends(security)): # Checks if the Auth0 token is valid
    try:
        token_data = validate_auth0_token(credentials.credentials)
    except Exception:
        raise HTTPException(
            status_code=401,
            detail="Invalid or expired Auth0 token"
        )

    return {
        "message": "Auth0 token is valid",
        "auth0_user_id": token_data["sub"]
    }

# Current Auth0 user
def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)):
    try:
        token_data = validate_auth0_token(credentials.credentials)
    except Exception as exc:
        logger.exception(
            "Auth0 token validation failed (%s)",
            type(exc).__name__,
        )
        raise HTTPException(
            status_code=401,
            detail="Invalid or expired Auth0 token"
        ) from exc

    if not token_data.get("sub"):
        raise HTTPException(
            status_code=401,
            detail="Auth0 token is missing a user identity"
        )

    # Auth0 adds a prefix to custom claims
    # Copy those values to the simple field names that the rest of this backend already expects
    claim_namespace = "https://team2bank-api"
    for field in ("full_name", "date_of_birth", "phone_number", "username"):
        namespaced_field = f"{claim_namespace}/{field}"
        if not token_data.get(field) and token_data.get(namespaced_field):
            token_data[field] = token_data[namespaced_field]

    user_dao = jpype.JClass("database.UserDAO")
    linked_user = user_dao.getUserByAuth0Sub(token_data["sub"])
    profile_fields = ("email", "name", "given_name", "family_name", "username", "phone_number", "date_of_birth", "user_metadata")
    if linked_user is None and any(not token_data.get(field) for field in profile_fields):
        userinfo = get_auth0_userinfo(credentials.credentials)
        if userinfo.get("sub") != token_data["sub"]:
            raise HTTPException(
                status_code=401,
                detail="Auth0 user profile does not match the access token"
            )
        token_data.update(userinfo)

    return {
        "auth0": token_data,
        "banking_user": link_auth0_user(token_data)
    }

# Permission needed to access manager reports
# Managers can view customer's profiles, accounts, transactions, and delete customer accounts and profiles
MANAGER_REPORTS_PERMISSION = "read:manager-reports"
DELETE_ACCOUNTS_PERMISSION = "delete:accounts"
DELETE_CUSTOMERS_PERMISSION = "delete:customers"
def require_manager_reports_permission(user=Depends(get_current_user)):
    # Check if the user has permission from auth0
    permissions = user["auth0"].get("permissions") or []
    if MANAGER_REPORTS_PERMISSION not in permissions:
        raise HTTPException(
            status_code=403,
            detail="You do not have permission to view manager reports",
        )
    return user

def require_delete_accounts_permission(user=Depends(get_current_user)):
    permissions = user["auth0"].get("permissions") or []
    if DELETE_ACCOUNTS_PERMISSION not in permissions:
        raise HTTPException(
            status_code=403,
            detail="You do not have permission to close customer accounts",
        )
    return user

def require_delete_customers_permission(user=Depends(get_current_user)):
    permissions = user["auth0"].get("permissions") or []
    if DELETE_CUSTOMERS_PERMISSION not in permissions:
        raise HTTPException(
            status_code=403,
            detail="You do not have permission to deactivate customers",
        )
    return user

@app.get("/api/manager/permission-check")
def check_manager_reports_permission(user=Depends(require_manager_reports_permission)):
    # Test endpoint for checking manager permissions
    return {
        "authorized": True,
        "permission": MANAGER_REPORTS_PERMISSION,
        "user_id": user["banking_user"]["user_id"],
    }

def _format_datetime(value):
    return str(value) if value is not None else None

# Hide most of the account number for security
def _mask_account_number(value):
    if value is None:
        return None
    account_number = str(value)
    return f"•••• {account_number[-4:]}"

def _manager_user_response(user):
    return {
        "user_id": int(user.getUserId()),
        "username": str(user.getUsername()),
        "full_name": f"{user.getFirstName()} {user.getLastName()}".strip(),
        "email": str(user.getEmail()),
        "phone_number": str(user.getPhoneNumber()),
        "date_of_birth": (
            str(user.getDateOfBirth())
            if user.getDateOfBirth() is not None
            else None
        ),
        "role": str(user.getRole()),
        "created_at": _format_datetime(user.getCreatedAt()),
    }

def _manager_account_response(account):
    return {
        "account_id": int(account.getAccountId()),
        "user_id": int(account.getUserId()),
        "account_type": str(account.getAccountType()),
        "account_number": _mask_account_number(account.getAccountNumber()),
        "routing_number": "•••••••••",
        "balance": float(account.getBalance()),
        "status": str(account.getStatus()),
        "opened_at": _format_datetime(account.getOpenedAt()),
        "closed_at": _format_datetime(account.getClosedAt()),
    }

def _manager_transaction_response(transaction):
    return {
        "transaction_id": int(transaction.getTransactionId()),
        "account_number": _mask_account_number(transaction.getAccountNumber()),
        "transaction_type": str(transaction.getTransactionType()),
        "amount": float(transaction.getAmount()),
        "related_account": _mask_account_number(transaction.getRelatedAccount()),
        "status": str(transaction.getStatus()),
        "scheduled_for": _format_datetime(transaction.getScheduledFor()),
        "timestamp": _format_datetime(transaction.getTimestamp()),
    }

# Summary report for managers
@app.get("/api/manager/reports/summary")
def get_manager_report_summary(user=Depends(require_manager_reports_permission)):
    user_dao = jpype.JClass("database.UserDAO")
    account_dao = jpype.JClass("database.BankAccountDAO")
    users = list(user_dao.getAllUsers())
    accounts = []
    for banking_user in users:
        accounts.extend(
            account_dao.getBankAccountsByUserId(
                int(banking_user.getUserId())
            )
        )
    return {
        "customer_count": len(users),
        "account_count": len(accounts),
        "active_account_count": sum(1 for account in accounts if str(account.getStatus()).lower() == "active"),
        "total_balance": sum(float(account.getBalance()) for account in accounts),
    }
@app.get("/api/manager/reports/customers")
def get_manager_customers(user=Depends(require_manager_reports_permission)):
    user_dao = jpype.JClass("database.UserDAO")
    return {
        "customers": [
            _manager_user_response(banking_user)
            for banking_user in list(user_dao.getAllUsers())
        ]
    }
# Get all accounts for a customer
@app.get("/api/manager/reports/customers/{user_id}/accounts")
def get_manager_customer_accounts(user_id: int,user=Depends(require_manager_reports_permission)):
    user_dao = jpype.JClass("database.UserDAO")
    account_dao = jpype.JClass("database.BankAccountDAO")
    if user_dao.getUserById(user_id) is None:
        raise HTTPException(status_code=404, detail="Customer not found")

    return {
        "accounts": [
            _manager_account_response(account)
            for account in account_dao.getBankAccountsByUserId(user_id)
        ]
    }

@app.get("/api/manager/reports/customers/{user_id}/transactions")
def get_manager_customer_transactions(user_id: int, user=Depends(require_manager_reports_permission)):
    user_dao = jpype.JClass("database.UserDAO")
    account_dao = jpype.JClass("database.BankAccountDAO")
    transaction_dao = jpype.JClass("database.TransactionDAO")
    if user_dao.getUserById(user_id) is None:
        raise HTTPException(status_code=404, detail="Customer not found")

    transactions = []
    for account in account_dao.getBankAccountsByUserId(user_id):
        account_number = str(account.getAccountNumber())
        transactions.extend(
            _manager_transaction_response(transaction)
            for transaction in transaction_dao.getTransactionsByAccountNumber(account_number)
        )
    return {"transactions": transactions}
# Close a customer's bank account
@app.delete("/api/manager/reports/accounts/{account_id}")
def close_manager_customer_account(
    account_id: int,
    user=Depends(require_delete_accounts_permission),
):
    account_dao = jpype.JClass("database.BankAccountDAO")
    status = account_dao.getAccountStatusById(account_id)

    if status is None:
        raise HTTPException(status_code=404, detail="Bank account not found")
    if str(status).lower() == "closed":
        raise HTTPException(status_code=409, detail="Bank account is already closed")
    if not account_dao.closeBankAccountById(account_id):
        raise HTTPException(status_code=500, detail="Unable to close bank account")

    return {
        "account_id": account_id,
        "status": "closed",
        "message": "Bank account closed successfully",
    }

@app.delete("/api/manager/reports/customers/{user_id}")
def deactivate_manager_customer(
    user_id: int,
    user=Depends(require_delete_customers_permission),
):
    if user_id == user["banking_user"]["user_id"]:
        raise HTTPException(
            status_code=403,
            detail="Managers cannot deactivate their own profile",
        )

    user_dao = jpype.JClass("database.UserDAO")
    auth0_sub = user_dao.getAuth0SubByUserId(user_id)
    result = str(user_dao.deactivateCustomerById(user_id))

    if result == "not_found":
        raise HTTPException(status_code=404, detail="Customer not found")
    if result == "not_customer":
        raise HTTPException(status_code=403, detail="Only customer profiles can be deactivated")
    if result == "already_inactive":
        if auth0_sub:
            delete_auth0_user(str(auth0_sub))
            return {
                "user_id": user_id,
                "status": "deleted",
                "message": "Customer account deleted successfully",
            }
        raise HTTPException(status_code=409, detail="Customer profile is already inactive")
    if result == "active_accounts":
        raise HTTPException(
            status_code=409,
            detail="Close all customer accounts before deactivating the profile",
        )
    if result != "deactivated":
        raise HTTPException(status_code=500, detail="Unable to deactivate customer profile")

    if auth0_sub:
        delete_auth0_user(str(auth0_sub))

    return {
        "user_id": user_id,
        "status": "deleted",
        "message": "Customer account deleted successfully",
    }
# Users delete own profile
@app.delete("/api/auth/profile")
def delete_own_profile(user=Depends(get_current_user)):
    user_id = int(user["banking_user"]["user_id"])
    user_dao = jpype.JClass("database.UserDAO")
    auth0_sub = user_dao.getAuth0SubByUserId(user_id)

    result = str(user_dao.deactivateCustomerById(user_id))
    if result == "active_accounts":
        raise HTTPException(
            status_code=409,
            detail="Please close all bank accounts before deleting your profile.",
        )
    if result == "already_inactive":
        if auth0_sub:
            delete_auth0_user(str(auth0_sub))
            return {"status": "deleted", "message": "Customer account deleted successfully"}
        raise HTTPException(status_code=409, detail="This profile has already been deleted.")
    if result == "not_customer":
        raise HTTPException(status_code=403, detail="Only customer profiles can be deleted this way")
    if result != "deactivated":
        raise HTTPException(status_code=500, detail="Unable to delete your profile")

    if auth0_sub:
        delete_auth0_user(str(auth0_sub))
    return {"status": "deleted", "message": "Customer account deleted successfully"}

# Basic test endpoint
@app.get("/api/data")
def read_data():
    return {"message": "Hello from backend"}

# Checks submitted email, username, and phone number before auth0 registration so duplicate errors appear on signup page
@app.post("/api/auth/check-deleted-username")
def check_deleted_username(request: DeletedUsernameRequest):
    user_dao = jpype.JClass("database.UserDAO")
    return {
        "deleted": bool(user_dao.isInactiveUsername(request.username.strip()))
    }

@app.post("/api/auth/check-profile-availability")
def check_profile_availability(request: ProfileAvailabilityRequest):
    user_dao = jpype.JClass("database.UserDAO") # Connectes to the Java UserDAO class to check for conflicts in the database
    conflict = user_dao.findAuth0ProfileConflict(
        request.username.strip().lower(),
        str(request.email).strip().lower(),
        request.phone_number.strip(), #normalize_phone_number(request.phone_number)
    )
    if conflict == "database":
        raise HTTPException(
            status_code=500,
            detail="Unable to check for duplicate banking profile data",
        )
    if conflict:
        labels = {
            "username": "Username",
            "email": "Email",
            "phone_number": "Phone number",
        }
        raise HTTPException(
            status_code=409,
            detail=f"{labels[conflict]} is already in use",
        )
    # Registration can continue if no duplicate errors
    return {"available": True}

# Register
@app.post("/api/auth/register")
def register(
    request: RegisterRequest,
    database: sqlite3.Connection = Depends(get_database)
):
    # Converts username and email to lowercase
    username = request.username.lower()
    email = str(request.email).lower()

    # Check if user exists in the database
    existing_user = database.execute(
        """
        SELECT user_id
        FROM users
        WHERE username = ? OR email = ?
        """,
        (username, email)
    ).fetchone()
    # If user exists then 409 Conflict
    if existing_user:
        raise HTTPException(
            status_code=409,
            detail="Username or email is already in use"
        )
    # Otherwise store the new user in the database with hashed password
    database.execute(
        """
        INSERT INTO users
        (name, email, username, password_hash)
        VALUES (?, ?, ?, ?)
        """,
        (
            request.name,
            email,
            username,
            hash_password(request.password)
        )
    )
    database.commit()
    return {
        "message": "Registration successful",
        "username": username
    }


# Login
@app.post("/api/auth/login")
def login(
    request: LoginRequest,
    database: sqlite3.Connection = Depends(get_database)
):
    username = request.username.strip().lower()

    # Use the SAME SQLite database as registration.
    user = database.execute(
        "SELECT * FROM users WHERE username = ?",
        (username,)
    ).fetchone()

    # Reject unknown users.
    if user is None:
        raise HTTPException(
            status_code=401,
            detail="Invalid username or password"
        )

    now = datetime.now(timezone.utc)

    # Check whether account is temporarily locked.
    if user["lock_until"]:
        lock_until = datetime.fromisoformat(
            user["lock_until"]
        )

        if lock_until > now:
            raise HTTPException(
                status_code=423,
                detail="Account is temporarily locked"
            )

    # Verify the bcrypt password hash.
    password_is_correct = check_password(
        request.password,
        user["password_hash"]
    )

    if not password_is_correct:
        failed_attempts = user["failed_attempts"] + 1

        if failed_attempts >= LOCKOUT_LIMIT:
            lock_until = now + timedelta(
                minutes=LOCKOUT_MINUTES
            )

            database.execute(
                """
                UPDATE users
                SET failed_attempts = 0,
                    lock_until = ?
                WHERE user_id = ?
                """,
                (
                    lock_until.isoformat(),
                    user["user_id"]
                )
            )

            database.commit()

            raise HTTPException(
                status_code=423,
                detail="Account is temporarily locked"
            )

        database.execute(
            """
            UPDATE users
            SET failed_attempts = ?,
                lock_until = NULL
            WHERE user_id = ?
            """,
            (
                failed_attempts,
                user["user_id"]
            )
        )

        database.commit()

        raise HTTPException(
            status_code=401,
            detail="Invalid username or password"
        )

    # Successful login: clear failures and lockout.
    database.execute(
        """
        UPDATE users
        SET failed_attempts = 0,
            lock_until = NULL
        WHERE user_id = ?
        """,
        (user["user_id"],)
    )

    database.commit()

    # Return token for subsequent authenticated requests.
    return {
        "access_token": create_token(user["user_id"]),
        "token_type": "bearer",

        # Login returns name and username
        "user" : {
            "user_id" : user["user_id"],
            "name" : user["name"],
            "email" : user["email"],
            "username" : user["username"]
        }
    }

# Get the currently logged-in user
@app.get("/api/auth/me")
def get_me(user=Depends(get_current_user)):
    banking_user = user["banking_user"]
    return {
        "auth0_user_id": user["auth0"]["sub"],
        "user_id": banking_user["user_id"],
        "name": banking_user["name"],
        "email": banking_user["email"],
        "username": banking_user["username"],
        "is_new_user": banking_user.get("is_new_user", False)
    }

class OpenAccountRequest(BaseModel):
    model_config = {"extra": "forbid"}
    account_type: Literal["checking", "savings"]


@app.post("/api/accounts", status_code=201)
def open_account(request: OpenAccountRequest, user=Depends(get_current_user)):
    bank_account_dao = jpype.JClass("database.BankAccountDAO")
    account_number = bank_account_dao.createBankAccount(
        int(user["banking_user"]["user_id"]), request.account_type, 0.0
    )
    if account_number is None:
        raise HTTPException(status_code=500, detail="Unable to create the bank account")
    return {"message": "Account created successfully"}


@app.get("/api/accounts")
def get_accounts_data(user=Depends(get_current_user)):
    bank_account_dao = jpype.JClass("database.BankAccountDAO")
    transaction_dao = jpype.JClass("database.TransactionDAO")
    banking_username = user["banking_user"]["username"]
    java_accounts = bank_account_dao.getBankAccountsByUsername(banking_username)

    return serialize_accounts([account for account in java_accounts if str(account.getStatus()) != "closed"])


@app.get("/api/accounts/{account_id}")
def get_account_detail(account_id: int, user=Depends(get_current_user)):
    bank_account_dao = jpype.JClass("database.BankAccountDAO")
    java_accounts = bank_account_dao.getBankAccountsByUsername(user["banking_user"]["username"])
    account = next((account for account in java_accounts if int(account.getAccountId()) == account_id), None)
    if account is None or str(account.getStatus()) == "closed":
        raise HTTPException(status_code=404, detail="Account not found")
    data = serialize_accounts([account], include_details=True)
    return {"account": data["accounts"][0], "transactions": data["transactions"]}


@app.post("/api/accounts/{account_id}/close")
def close_account(account_id: int, user=Depends(get_current_user)):
    bank_account_dao = jpype.JClass("database.BankAccountDAO")
    java_accounts = bank_account_dao.getBankAccountsByUsername(user["banking_user"]["username"])
    account = next((account for account in java_accounts if int(account.getAccountId()) == account_id), None)
    if account is None or str(account.getStatus()) == "closed":
        raise HTTPException(status_code=404, detail="Account not found")
    balance = float(account.getBalance())
    if balance != 0:
        raise HTTPException(status_code=409, detail={"message": "Move your money first", "balance": balance})
    if not bank_account_dao.closeBankAccount(str(account.getAccountNumber())):
        raise HTTPException(status_code=500, detail="Unable to close the account")
    return {"message": "Account closed successfully"}


class MovementRequest(BaseModel):
    model_config = {"extra": "forbid"}
    amount: Decimal

    @field_validator("amount", mode="before")
    @classmethod
    def validate_amount(cls, value):
        text = str(value).strip()
        if not re.fullmatch(r"-?\d+(?:\.\d{1,2})?", text):
            raise HTTPException(status_code=422, detail={"field": "amount", "message": "Enter an amount like 25 or 25.50"})
        amount = Decimal(text)
        if amount <= 0:
            raise HTTPException(status_code=422, detail={"field": "amount", "message": "Enter an amount greater than $0.00"})
        if not __import__("math").isfinite(float(amount)):
            raise HTTPException(status_code=422, detail={"field": "amount", "message": "Enter an amount like 25 or 25.50"})
        return amount


class OwnAccountDestination(BaseModel):
    model_config = {"extra": "forbid"}
    kind: Literal["own_account"]
    account_id: int = Field(gt=0, strict=True)


class BankCustomerDestination(BaseModel):
    model_config = {"extra": "forbid"}
    kind: Literal["bank_customer"]
    account_number: str = Field(pattern=r"^[0-9]{10}$")


class ExternalAccountDestination(BaseModel):
    model_config = {"extra": "forbid"}
    kind: Literal["external_account"]
    external_account_id: int = Field(gt=0, strict=True)


class TransferRequest(MovementRequest):
    source_account_id: int = Field(gt=0, strict=True)
    destination: Annotated[
        OwnAccountDestination | BankCustomerDestination | ExternalAccountDestination,
        Field(discriminator="kind"),
    ]
    scheduled_for: date | None = None

    @field_validator("scheduled_for", mode="before")
    @classmethod
    def validate_scheduled_date(cls, value):
        if value is None:
            return None
        try:
            if isinstance(value, str) and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value):
                parsed = date.fromisoformat(value)
                if parsed > datetime.now(timezone.utc).date():
                    return parsed
        except ValueError:
            pass
        raise HTTPException(status_code=422, detail={
            "field": "scheduled_for", "message": "Choose a future date (UTC).",
        })


def owned_external_accounts(user):
    external_dao = jpype.JClass("database.ExternalAccountDAO")
    return external_dao.getExternalAccountsByUserId(user["banking_user"]["user_id"])


@app.get("/api/external-accounts")
def get_external_accounts(user=Depends(get_current_user)):
    return [
        {
            "id": int(account.getExternalAccountId()),
            "name": str(account.getNickname() or account.getBankName()),
            "number": _mask_account_number(account.getAccountNumber()),
        }
        for account in owned_external_accounts(user)
    ]


@app.post("/api/transfers")
def create_transfer(request: TransferRequest, user=Depends(get_current_user)):
    bank_dao = jpype.JClass("database.BankAccountDAO")
    accounts = {
        int(account.getAccountId()): account
        for account in bank_dao.getBankAccountsByUsername(user["banking_user"]["username"])
    }
    source = accounts.get(request.source_account_id)
    if source is None or str(source.getStatus()) == "closed":
        raise HTTPException(status_code=404, detail="Source account not found.")
    if str(source.getStatus()) != "active":
        raise HTTPException(status_code=409, detail="The source account is inactive.")

    source_number = str(source.getAccountNumber())
    destination = request.destination
    transaction_dao = jpype.JClass("database.TransactionDAO")
    if isinstance(destination, ExternalAccountDestination):
        external_id = destination.external_account_id
        if not any(int(a.getExternalAccountId()) == external_id for a in owned_external_accounts(user)):
            raise HTTPException(status_code=404, detail="Recipient is unavailable.")
        send = transaction_dao.transferToExternal
        target = external_id
    else:
        recipient = (
            accounts.get(destination.account_id)
            if isinstance(destination, OwnAccountDestination)
            else bank_dao.getBankAccountByNumber(destination.account_number)
        )
        if recipient is None or str(recipient.getStatus()) != "active":
            raise HTTPException(status_code=404, detail="Recipient is unavailable.")
        target = str(recipient.getAccountNumber())
        if target == source_number:
            raise HTTPException(status_code=422, detail="Choose a different destination account.")
        send = transaction_dao.transfer

    amount = float(request.amount)
    if request.scheduled_for is None:
        balance = float(source.getBalance())
        if request.amount > Decimal(str(balance)):
            raise HTTPException(status_code=409, detail={
                "field": "amount", "message": f"That's more than the ${balance:,.2f} available",
            })
        saved = send(source_number, target, amount)
    else:
        saved = send(source_number, target, amount, f"{request.scheduled_for} 00:00:00")

    if not saved:
        raise HTTPException(status_code=409, detail="Unable to submit the transfer. Refresh your accounts and try again.")
    if request.scheduled_for is not None:
        return {"status": "pending", "scheduled_for": request.scheduled_for.isoformat()}
    return {"status": "completed"}


@app.post("/api/accounts/{account_id}/deposit")
def deposit_to_account(account_id: int, request: MovementRequest, user=Depends(get_current_user)):
    return move_account_money(account_id, request, user, "deposit")


@app.post("/api/accounts/{account_id}/withdraw")
def withdraw_from_account(account_id: int, request: MovementRequest, user=Depends(get_current_user)):
    return move_account_money(account_id, request, user, "withdraw")


def move_account_money(account_id, request, user, operation):
    bank_dao = jpype.JClass("database.BankAccountDAO")
    accounts = bank_dao.getBankAccountsByUsername(user["banking_user"]["username"])
    account = next((a for a in accounts if int(a.getAccountId()) == account_id), None)
    if account is None or str(account.getStatus()) == "closed":
        raise HTTPException(status_code=404, detail="Account not found")
    if str(account.getStatus()) != "active":
        raise HTTPException(status_code=409, detail="This account is not active")
    balance = float(account.getBalance())
    if operation == "withdraw" and request.amount > Decimal(str(balance)):
        raise HTTPException(status_code=409, detail={
            "field": "amount", "message": f"That's more than the ${balance:,.2f} available",
            "available": balance,
        })
    transaction_dao = jpype.JClass("database.TransactionDAO")
    if not getattr(transaction_dao, operation)(str(account.getAccountNumber()), float(request.amount)):
        raise HTTPException(status_code=409, detail="Unable to complete the transaction. Refresh your accounts and try again.")
    return {"message": "Transaction completed"}


def serialize_accounts(java_accounts, include_details=False):
    transaction_dao = jpype.JClass("database.TransactionDAO")
    accounts = []
    transactions = []
    account_type_labels = {
        "checking": "Everyday Checking",
        "savings": "High-Yield Savings",
    }
    transaction_labels = {
        "deposit": ("Deposit", "Income", "ArrowDownLeft", 1),
        "withdrawal": ("Withdrawal", "Expense", "CircleDollarSign", -1),
        "transfer_in": ("Transfer received", "Transfer", "ArrowLeftRight", 1),
        "transfer_out": ("Transfer sent", "Transfer", "ArrowLeftRight", -1),
    }

    for account in java_accounts:
        account_id = int(account.getAccountId())
        account_number = str(account.getAccountNumber())
        account_type = str(account.getAccountType()).lower()
        balance = float(account.getBalance())
        accounts.append(
            {
                "id": account_id,
                "type": account_type_labels.get(account_type, account_type.title()),
                "number": f"•••• {account_number[-4:]}",
                "balance": balance,
                "available": balance,
                "color": "purple" if account_type == "savings" else "blue",
                "account_type": account_type,
                "status": str(account.getStatus()),
            }
        )

        if include_details:
            accounts[-1]["account_number"] = account_number
            accounts[-1]["routing_number"] = str(account.getRoutingNumber())

        java_transactions = transaction_dao.getTransactionsByAccountNumber(account_number)
        for transaction in java_transactions:
            transaction_type = str(transaction.getTransactionType()).lower()
            name, category, icon, direction = transaction_labels.get(
                transaction_type,
                (transaction_type.replace("_", " ").title(), "Other", "CircleDollarSign", 1),
            )
            status = str(transaction.getStatus())
            if status != "completed" and transaction_type in ("transfer_in", "transfer_out"):
                name = "Incoming transfer" if transaction_type == "transfer_in" else "Outgoing transfer"
            timestamp = str(transaction.getTimestamp())
            transaction_date = datetime.fromisoformat(timestamp)
            transactions.append(
                {
                    "id": int(transaction.getTransactionId()),
                    "name": name,
                    "category": category,
                    "date": (
                        f"{transaction_date:%b} "
                        f"{transaction_date.day}, {transaction_date.year}"
                    ),
                    "timestamp": timestamp,
                    "amount": direction * abs(float(transaction.getAmount())),
                    "account": account_id,
                    "transaction_type": transaction_type,
                    "status": status,
                    "scheduled_for": _format_datetime(transaction.getScheduledFor()),
                    "icon": icon,
                }
            )

    accounts.sort(key=lambda account: account["id"])
    transactions.sort(key=lambda transaction: transaction["timestamp"], reverse=True)

    return {
        "accounts": accounts,
        "transactions": transactions,
    }
