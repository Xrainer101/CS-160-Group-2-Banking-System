import pytest
from fastapi.testclient import TestClient

'''
    Test client for the authentication API
    
    There are 10 tests in total that test different aspects of the authentication system.
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

### Test 4: Auth0 token can access current user
def test_auth0_token_can_access_current_user(client, monkeypatch):
    import main
    monkeypatch.setattr(
        main,
        "validate_auth0_token",
        lambda token: {
            "sub": "auth0|test-user",
            "name": "Ivana Aguilar",
            "email": "ivana@example.com",
            "preferred_username": "ivanaaguilar",
        },
    )
    monkeypatch.setattr(
        main,
        "link_auth0_user",
        lambda token_data: {
            "user_id": 7,
            "username": "ivanaaguilar",
            "name": "Ivana Aguilar",
            "email": "ivana@example.com",
        },
    )
    response = client.get(
        "/api/auth/me",
        headers={"Authorization": "Bearer test-auth0-token"}
    )
    assert response.status_code == 200
    assert response.json()["auth0_user_id"] == "auth0|test-user"
    assert response.json()["user_id"] == 7
    assert response.json()["username"] == "ivanaaguilar"

### Test 5: Tests authentiated banking user can access accounts and transactions
def test_accounts_use_authenticated_banking_user(client, monkeypatch):
    import main
    monkeypatch.setattr(
        main,
        "validate_auth0_token",
        lambda token: {"sub": "auth0|test-user", "email": "ivana@example.com"},
    )
    monkeypatch.setattr(
        main,
        "link_auth0_user",
        lambda token_data: {
            "user_id": 7,
            "username": "ivanaaguilar",
            "name": "Ivana Aguilar",
            "email": "ivana@example.com",
        },
    )
    seen = {}
    class FakeBankAccountDAO:
        @staticmethod
        def getBankAccountsByUsername(username):
            seen["username"] = username
            return []
    class FakeTransactionDAO:
        pass
    def fake_jclass(name):
        if name == "database.BankAccountDAO":
            return FakeBankAccountDAO
        if name == "database.TransactionDAO":
            return FakeTransactionDAO
        raise AssertionError(f"Unexpected Java class: {name}")
    monkeypatch.setattr(main.jpype, "JClass", fake_jclass)

    response = client.get(
        "/api/accounts",
        headers={"Authorization": "Bearer test-auth0-token"}
    )
    assert response.status_code == 200
    assert seen["username"] == "ivanaaguilar"
    assert response.json() == {"accounts": [], "transactions": []}

### Test 6: New Auth0 users get default accounts and demo balances
def test_new_auth0_user_is_provisioned(monkeypatch):
    import java_database
    import main
    class FakeUser:
        def getUserId(self):
            return 42
        def getUsername(self):
            return "ivana_auth0test"
        def getFirstName(self):
            return "Ivana"
        def getLastName(self):
            return "Aguilar"
        def getEmail(self):
            return "ivana@example.com"
        def getPhoneNumber(self):
            return "123-4567"
        def getDateOfBirth(self):
            return "2003-07-22"
    class FakeUserDAO:
        @staticmethod
        def getUserByAuth0Sub(auth0_sub):
            return None
        @staticmethod
        def linkAuth0SubByEmail(auth0_sub, email):
            return None
        @staticmethod
        def findAuth0ProfileConflict(username, email, phone_number):
            return None
        @staticmethod
        def registerAuth0User(auth0_sub, username, first_name, last_name, email, phone_number, date_of_birth):
            assert auth0_sub == "auth0|test-user"
            assert email == "ivana@example.com"
            assert phone_number == "123-4567"
            assert date_of_birth == "2003-07-22"
            return FakeUser()
    account_calls = []

    def fake_default_accounts(user_id):
        account_calls.append(user_id)
        return {"checking": "checking-account", "savings": "savings-account"}
    monkeypatch.setattr(main.jpype, "JClass", lambda name: FakeUserDAO)
    monkeypatch.setattr(java_database, "create_default_accounts", fake_default_accounts)

    result = main.link_auth0_user(
        {
            "sub": "auth0|test-user",
            "name": "Ivana Aguilar",
            "given_name": "Ivana",
            "family_name": "Aguilar",
            "email": "ivana@example.com",
            # Copies the custom data submitted during Auth0 registration
            "user_metadata": {
                "username": "ivana_auth0test",
                "phone_number": "123-4567",
                "date_of_birth": "2003-07-22",
            },
        }
    )
    assert account_calls == [42]
    assert result["user_id"] == 42
    assert result["email"] == "ivana@example.com"


def test_new_auth0_user_with_duplicate_phone_is_rejected(monkeypatch):
    import main
    from fastapi import HTTPException

    class FakeUserDAO:
        @staticmethod
        def getUserByAuth0Sub(auth0_sub):
            return None
        @staticmethod
        def linkAuth0SubByEmail(auth0_sub, email):
            return None
        @staticmethod
        def findAuth0ProfileConflict(username, email, phone_number):
            assert phone_number == "123-4567"
            return "phone_number"

    monkeypatch.setattr(main.jpype, "JClass", lambda name: FakeUserDAO)

    # The backend should reject the new profile before creating accounts
    with pytest.raises(HTTPException) as error:
        main.link_auth0_user({
            "sub": "auth0|duplicate-phone",
            "name": "Duplicate Phone",
            "email": "different@example.com",
            "user_metadata": {
                "username": "different-user",
                "phone_number": "123-4567",
            },
        })
    # HTTP 409 means the submitted profile data conflicts with an existing user
    assert error.value.status_code == 409
    assert error.value.detail == "Phone number is already in use"


def test_new_auth0_user_without_phone_is_rejected(monkeypatch):
    import main
    from fastapi import HTTPException

    class FakeUserDAO:
        @staticmethod
        def getUserByAuth0Sub(auth0_sub):
            return None
        @staticmethod
        def linkAuth0SubByEmail(auth0_sub, email):
            return None

    monkeypatch.setattr(main.jpype, "JClass", lambda name: FakeUserDAO)

    # No phone metadata means the backend cannot safely create the profile
    with pytest.raises(HTTPException) as error:
        main.link_auth0_user({
            "sub": "auth0|missing-phone",
            "name": "Missing Phone",
            "email": "missing-phone@example.com",
        })
    # HTTP 400 means the Auth0 profile data is incomplete for registration
    assert error.value.status_code == 400
    assert "missing a phone number" in error.value.detail


### Test 7: Existing Auth0 users are reused and not re-registered
def test_existing_auth0_user_is_reused(monkeypatch):
    import main
    class ExistingUser:
        def getUserId(self):
            return 7
        def getUsername(self):
            return "ivanaaguilar"
        def getFirstName(self):
            return "Ivana"
        def getLastName(self):
            return "Aguilar"
        def getEmail(self):
            return "ivana@example.com"
        def getPhoneNumber(self):
            return "123-4567"
        def getDateOfBirth(self):
            return "2003-07-22"
    class FakeUserDAO:
        @staticmethod
        def getUserByAuth0Sub(auth0_sub):
            return ExistingUser()
        @staticmethod
        def registerAuth0User(*args):
            raise AssertionError("An existing Auth0 user should not be registered again")
    monkeypatch.setattr(main.jpype, "JClass", lambda name: FakeUserDAO)

    result = main.link_auth0_user(
        {"sub": "auth0|existing-user", "email": "ivana@example.com"}
    )
    assert result["user_id"] == 7
    assert result["username"] == "ivanaaguilar"


### Test 8: Auth0 users without an email are rejected
def test_auth0_user_without_email_is_rejected(monkeypatch):
    import main
    from fastapi import HTTPException
    class FakeUserDAO:
        @staticmethod
        def getUserByAuth0Sub(auth0_sub):
            return None
    monkeypatch.setattr(main.jpype, "JClass", lambda name: FakeUserDAO)

    with pytest.raises(HTTPException) as error:
        main.link_auth0_user({"sub": "auth0|missing-email"})
    assert error.value.status_code == 400
    assert "missing the email" in error.value.detail


### Test 9: Default accounts are created with demo balances
def test_default_accounts_use_demo_balances(monkeypatch):
    import java_database
    calls = []
    class FakeBankAccountDAO:
        @staticmethod
        def createBankAccount(user_id, account_type, opening_balance):
            calls.append((user_id, account_type, opening_balance))
            return f"{account_type}-account"
    monkeypatch.setattr(
        java_database.jpype,
        "JClass",
        lambda name: FakeBankAccountDAO,
    )
    result = java_database.create_default_accounts(42)
    assert result == {
        "checking": "checking-account",
        "savings": "savings-account",
    }
    assert calls == [
        (42, "checking", 6551.43),
        (42, "savings", 4227.70),
    ]
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

### Test 10: Account locks after 10 failed attempts
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
