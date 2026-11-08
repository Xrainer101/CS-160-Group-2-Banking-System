# backend/main.py
import os
import sqlite3
import json
from urllib.request import Request, urlopen
from datetime import datetime, timedelta, timezone
import bcrypt   # For securely hashing passwords
import jwt      # For creating and verifying JSON Web Tokens (JWTs)
from jwt import PyJWKClient  # Gets Auth0's public keys for token validation
from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware  # Backend and frontend communication
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer   # Bearer Token authentication
from pydantic import BaseModel, EmailStr, Field # Validate request data
import jpype

# Basic settings 
DATABASE_PATH = os.getenv(
    "DATABASE_PATH",
    os.path.join(os.path.dirname(__file__), "bank.db")
)
# Security settings
JWT_SECRET = "local-development-secret-key-for-testing-only"
AUTH0_DOMAIN = os.getenv("AUTH0_DOMAIN")
AUTH0_AUDIENCE = os.getenv("AUTH0_AUDIENCE")
AUTH0_ISSUER = f"https://{AUTH0_DOMAIN}/" if AUTH0_DOMAIN else ""
AUTH0_JWKS_URL = f"https://{AUTH0_DOMAIN}/.well-known/jwks.json" if AUTH0_DOMAIN else ""
auth0_jwks_client = PyJWKClient(AUTH0_JWKS_URL) if AUTH0_JWKS_URL else None
LOCKOUT_LIMIT = 10
LOCKOUT_MINUTES = 15

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
    userinfo_url = f"https://{AUTH0_DOMAIN}/userinfo"
    request = Request(
        userinfo_url,
        headers={"Authorization": f"Bearer {token}"}
    )
    try:
        with urlopen(request, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        raise HTTPException(
            status_code=502,
            detail=f"Unable to retrieve Auth0 user profile: {e}"
        )

# Link Auth0 user to banking user
def link_auth0_user(token_data):
    """Resolve Auth0 subject to a Java banking user and creating one if needed."""
    user_dao = jpype.JClass("database.UserDAO")
    auth0_sub = token_data["sub"]
    banking_user = user_dao.getUserByAuth0Sub(auth0_sub)
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
        full_name = str(token_data.get("name") or "").strip()
        name_parts = full_name.split(maxsplit=1)
        first_name = str(token_data.get("given_name") or (name_parts[0] if name_parts else "Auth0"))
        last_name = str(token_data.get("family_name") or (name_parts[1] if len(name_parts) > 1 else "User"))
        email_name = str(email).split("@", 1)[0]
        subject_name = auth0_sub.split("|", 1)[-1][:8]
        username = f"{email_name}_{subject_name}"[:30]
        phone_number = f"auth0:{auth0_sub}"

        banking_user = user_dao.registerAuth0User(
            auth0_sub,
            username,
            first_name,
            last_name,
            email,
            phone_number,
        )
        if banking_user is None:
            raise HTTPException(
                status_code=500,
                detail="Unable to create a banking profile for this Auth0 account"
            )
        from java_database import create_default_accounts
        create_default_accounts(int(banking_user.getUserId()))

    return {
        "user_id": int(banking_user.getUserId()),
        "username": str(banking_user.getUsername()),
        "name": f"{banking_user.getFirstName()} {banking_user.getLastName()}",
        "email": str(banking_user.getEmail())
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
    except Exception:
        raise HTTPException(
            status_code=401,
            detail="Invalid or expired Auth0 token"
        )

    if not token_data.get("sub"):
        raise HTTPException(
            status_code=401,
            detail="Auth0 token is missing a user identity"
        )

    if not token_data.get("email"):
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

# Basic test endpoint
@app.get("/api/data")
def read_data():
    return {"message": "Hello from backend"}

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
    request: LoginRequest
):
    print("hi")
    username = request.username.lower()
    # Find user in the database by username
    database_manager = jpype.JClass("database.DatabaseManager")

    user = database_manager.getUserByUsername(username) # this doesn't work
    # If no user then 401 error
    if user is None:
        raise HTTPException(
            status_code=401,
            detail="Invalid username or password"
        )
    
    
    # Account lock check: If the user has failed too many login attempts, lock the account for a period of time
    if user["lockout_until"]:
        lock_until = datetime.fromisoformat(user["lockout_until"])

        if lock_until > datetime.now(timezone.utc):
            raise HTTPException(
                status_code=423,
                detail="Account is temporarily locked"
            )

    # Password verification by checking password hash
    password_is_correct = check_password(
        request.password,
        user["password_hash"]
    )

    # If password wrong increment the failed attempts
    if not password_is_correct:
        # failed_attempts = user["failed_attempts"] + 1
        # # Lock the account if the failed attempts exceed the limit
        # if failed_attempts >= LOCKOUT_LIMIT:
        #     lock_until = datetime.now(timezone.utc) + timedelta( # lockout time
        #         minutes=LOCKOUT_MINUTES
        #     )
        #     database.execute(
        #         """
        #         UPDATE users
        #         SET failed_attempts = 0, lock_until = ?
        #         WHERE user_id = ?
        #         """,
        #         (lock_until.isoformat(), user["user_id"])
        #     )
        #     database.commit()

        #     raise HTTPException(
        #         status_code=423,
        #         detail="Account is temporarily locked"
        #     )
        # # Failed password but not locked yet, update the failed attempts in the database
        # database.execute(
        #     """
        #     UPDATE users
        #     SET failed_attempts = ?
        #     WHERE user_id = ?
        #     """,
        #     (failed_attempts, user["user_id"])
        # )
        # database.commit()

        raise HTTPException(
            status_code=401,
            detail="Invalid username or password"
        )
    # Successful login, resets failure count and lockout status, and returns a JWT token
    # database.execute(
    #     """
    #     UPDATE users
    #     SET failed_attempts = 0, lock_until = NULL
    #     WHERE user_id = ?
    #     """,
    #     (user["user_id"],)
    # )
    # database.commit()

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
        "username": banking_user["username"]
    }

@app.get("/api/accounts")
def get_accounts_data(user=Depends(get_current_user)):
    bank_account_dao = jpype.JClass("database.BankAccountDAO")
    transaction_dao = jpype.JClass("database.TransactionDAO")
    banking_username = user["banking_user"]["username"]
    java_accounts = bank_account_dao.getBankAccountsByUsername(banking_username)

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

        java_transactions = transaction_dao.getTransactionsByAccountNumber(account_number)
        for transaction in java_transactions:
            transaction_type = str(transaction.getTransactionType()).lower()
            name, category, icon, direction = transaction_labels.get(
                transaction_type,
                (transaction_type.replace("_", " ").title(), "Other", "CircleDollarSign", 1),
            )
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
                    "icon": icon,
                }
            )

    accounts.sort(key=lambda account: account["id"])
    transactions.sort(key=lambda transaction: transaction["timestamp"], reverse=True)

    return {
        "accounts": accounts,
        "transactions": transactions,
    }