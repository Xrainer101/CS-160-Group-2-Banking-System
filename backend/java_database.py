from pathlib import Path

import jpype


PROJECT_ROOT = Path(__file__).resolve().parents[1]
JAVA_CLASSES = PROJECT_ROOT / "target" / "classes"
JAVA_DEPENDENCIES = PROJECT_ROOT / "target" / "dependency"
DATABASE_FILE = PROJECT_ROOT / "bank.db"

DEMO_ACCOUNTS = (
    ("checking", 6551.43),
    ("savings", 4227.70),
)
DEMO_TRANSACTIONS = (
    ("checking", "withdraw", 15.99),
    ("checking", "withdraw", 8.50),
    ("checking", "withdraw", 94.21),
    ("checking", "deposit", 1850.00),
    ("checking", "withdraw", 42.63),
    ("savings", "deposit", 200.00),
)

def create_default_accounts(user_id: int) -> dict[str, str]:
    bank_account_dao = jpype.JClass("database.BankAccountDAO")
    account_numbers = {}

    for account_type, opening_balance in DEMO_ACCOUNTS:
        account_number = bank_account_dao.createBankAccount(
            user_id,
            account_type,
            opening_balance,
        )
        if account_number is None:
            raise RuntimeError(f"Failed to create the default {account_type} account.")
        account_numbers[account_type] = str(account_number)
    return account_numbers

def _seed_demo_data() -> None:
    user_dao = jpype.JClass("database.UserDAO")
    transaction_dao = jpype.JClass("database.TransactionDAO")
    user_id = user_dao.getUserIdByUsername("john_doe")
    if user_id < 0:
        raise RuntimeError("The john_doe demo user could not be found.")

    bank_account_dao = jpype.JClass("database.BankAccountDAO")
    existing_accounts = bank_account_dao.getBankAccountsByUsername("john_doe")
    if not existing_accounts.isEmpty():
        return

    account_numbers = create_default_accounts(user_id)

    for account_type, operation, amount in DEMO_TRANSACTIONS:
        succeeded = getattr(transaction_dao, operation)(
            account_numbers[account_type],
            amount,
        )
        if not succeeded:
            raise RuntimeError(
                f"Failed to seed a demo {operation} transaction for {account_type}."
            )


def initialize_java_database() -> None:
    # Initialize the root-level bank.db using the Java DatabaseManager.
    if not (JAVA_CLASSES / "database" / "DatabaseManager.class").is_file():
        raise RuntimeError(
            "Java classes are missing. From the project root, run "
            "'mvn compile dependency:copy-dependencies "
            "-DoutputDirectory=target/dependency'."
        )

    jars = sorted(JAVA_DEPENDENCIES.glob("*.jar"))
    if not any(jar.name.startswith("sqlite-jdbc-") for jar in jars):
        raise RuntimeError(
            "The SQLite JDBC dependency is missing. From the project root, run "
            "'mvn compile dependency:copy-dependencies "
            "-DoutputDirectory=target/dependency'."
        )

    classpath = [str(JAVA_CLASSES), *(str(jar) for jar in jars)]
    if jpype.isJVMStarted():
        for entry in classpath:
            jpype.addClassPath(entry)
    else:
        jpype.startJVM(classpath=classpath)

    jpype.java.lang.System.setProperty("bank.db.path", str(DATABASE_FILE))
    database_manager = jpype.JClass("database.DatabaseManager")
    database_manager.initializeDatabase()

    user_dao = jpype.JClass("database.UserDAO")
    if user_dao.getUserIdByUsername("john_doe") < 0:
        user_dao.registerUser(
            "john_doe",
            "John",
            "Doe",
            "john@example.com",
            "1234567890",
            "fake_hashed_password_123",
        )
    _seed_demo_data()
