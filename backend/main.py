# backend/main.py
import os
import sqlite3
from datetime import datetime, timedelta, timezone
import bcrypt   # For securely hashing passwords
import jwt      # For creating and verifying JSON Web Tokens (JWTs)
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

# Current User Function
def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    database: sqlite3.Connection = Depends(get_database)
):
    try:
        # Validates the token and extracts the user ID
        token_data = jwt.decode(
            credentials.credentials,
            JWT_SECRET,
            algorithms=["HS256"]
        )
        user_id = int(token_data["sub"])
    # If token bad retrieve the logged in user and check if the user exists in the database
    except Exception:
        raise HTTPException(
            status_code=401,
            detail="Invalid or expired token"
        )
    user = database.execute(
        "SELECT user_id, name, email, username FROM users WHERE user_id = ?",
        (user_id,)
    ).fetchone()
    if user is None:
        raise HTTPException(
            status_code=401,
            detail="User not found"
        )
    return user

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
        "token_type": "bearer"
    }

# Get the currently logged-in user
@app.get("/api/auth/me")
def get_me(user=Depends(get_current_user)):
    return {
        "user_id": user["user_id"],
        "name": user["name"],
        "email": user["email"],
        "username": user["username"]
    }