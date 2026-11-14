import pytest
from fastapi.testclient import TestClient

'''
    Test client for the authentication API
    
    There are 6 tests in total that test different aspects of the authentication system.
    Every test that uses the client fixture will have a temporary database 
    created for it, ensures tests are isolated and don't interfere with each other.
'''
@pytest.fixture
def client(tmp_path):
    import main # Imports the main FastAPI app and database functions
    database_file = tmp_path / "test.db"
    main.DATABASE_PATH = str(database_file)

    with TestClient(main.app) as test_client:   # Create test client for FastAPI app
        yield test_client

# Creates default user register data for testing
def user_data(**changes):
    data = {
        "name": "Ivana Aguilar",
        "email": "ivana@example.com",
        "username": "ivanaaguilar",
        "password": "Password123"
    }
    data.update(changes)
    return data

### Test 1: Register User
def test_register_user(client):
    response = client.post(     # Send registration request to the API
        "/api/auth/register",
        json=user_data()
    )
    assert response.status_code == 200  # Code 200 = registration successful
    assert response.json()["message"] == "Registration successful"
    assert response.json()["username"] == "ivanaaguilar" # Verify username

### Test 2: Duplicate username is rejected
def test_duplicate_username_is_rejected(client):
    client.post(
        "/api/auth/register",
        json=user_data()
    )
    # Attempt second registration with same username but different email
    response = client.post(
        "/api/auth/register",
        json=user_data(email="ivana2@example.com")
    )
    assert response.status_code == 409 # Code 409 = conflict, username already exists
    assert response.json()["detail"] == "Username or email is already in use"

### Test 3: Valid login test, returns JWT token
def test_valid_login_returns_token(client):
    client.post(   # Register a user first
        "/api/auth/register",
        json=user_data()
    )
    response = client.post( # Login request with correct credentials
        "/api/auth/login",
        json={
            "username": "IVANAAGUILAR",  # Test case-insensitivity of username
            "password": "Password123"
        }
    )
    assert response.status_code == 200
    assert "access_token" in response.json()
    assert response.json()["token_type"] == "bearer"

### Test 4: Token can access current user
def test_token_can_access_current_user(client):
    client.post(    # Register a user first
        "/api/auth/register",
        json=user_data()
    )
    login = client.post(
        "/api/auth/login",
        json={
            "username": "IVANAAGUILAR",
            "password": "Password123"
        }
    )
    token = login.json()["access_token"] # Extract token from login

    response = client.get(  # Call protected endpoint
        "/api/auth/me",
        # Create Authorization header with Bearer token
        headers={
            "Authorization": f"Bearer {token}"
        }
    )
    assert response.status_code == 200
    assert response.json()["username"] == "ivanaaguilar" # Verify username

### Test 5: Wrong password is rejected
def test_wrong_password_is_rejected(client):
    client.post(
        "/api/auth/register",
        json=user_data()
    )
    response = client.post( # Login with bad/wrong password
        "/api/auth/login",
        json={
            "username": "IVANAAGUILAR",
            "password": "WrongPassword"
        }
    )
    assert response.status_code == 401  # 401 = Unauthorized
    assert response.json()["detail"] == "Invalid credentials"

### Test 6: Account locks after 10 failed attempts
def test_account_locks_after_ten_failed_attempts(client):
    client.post(  # Register a user first
        "/api/auth/register",
        json=user_data()
    )
    # Runs the first 9 failed login attempts
    for _ in range(9):
        response = client.post( # Login with bad/wrong password
            "/api/auth/login",
            json={
                "username": "IVANAAGUILAR",
                "password": "WrongPassword"
            }
        )
        assert response.status_code == 401 # 401 = Unauthorized, but not locked yet/ still allowed to try

    # 10th failed attempt should lock the account
    response = client.post( # Login with bad/wrong password
        "/api/auth/login",
        json={ 
            "username": "IVANAAGUILAR",
            "password": "WrongPassword"
        }
    )
    assert response.status_code == 423 # 423 = Locked, account is temporarily locked
    assert response.json()["detail"] == "Account is temporarily locked"