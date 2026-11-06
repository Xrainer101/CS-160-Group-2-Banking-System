package database;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    // This will create a file named "bank.db" in your project root directory
    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("bank.db.path", "bank.db");
    private static final SecureRandom RANDOM = new SecureRandom();
    // Static constant for your bank's routing number (must be 9 digits)
    public static final String BANK_ROUTING_NUMBER = "123456789";

    // Connection getter method
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    // Initialize Database and Tables
    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            // Create Users Table
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "user_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT NOT NULL UNIQUE," +
                "first_name TEXT NOT NULL," +
                "last_name TEXT NOT NULL," +
                "email TEXT NOT NULL UNIQUE," +
                "phone_number TEXT NOT NULL UNIQUE," +
                "password_hash TEXT NOT NULL," +
                "role TEXT NOT NULL DEFAULT 'customer' CHECK(role IN ('customer', 'admin', 'manager'))," +
                "failed_login_attempts INTEGER NOT NULL DEFAULT 0," +
                "lockout_until DATETIME DEFAULT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
            stmt.execute(createUsersTable);

            System.out.println("Users table created successfully.");

            // Create Bank Accounts Table
            String createAccountsTable = "CREATE TABLE IF NOT EXISTS bank_accounts (" +
                "account_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "account_number TEXT NOT NULL UNIQUE," +
                "routing_number TEXT NOT NULL DEFAULT '123456789'," +
                "account_type TEXT NOT NULL DEFAULT 'checking' CHECK(account_type IN ('checking', 'savings'))," +
                "balance REAL NOT NULL DEFAULT 0.00," +
                "status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active', 'frozen', 'closed'))," +
                "opened_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "closed_at DATETIME DEFAULT NULL," +
                "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE);";
            stmt.execute(createAccountsTable);

            System.out.println("Accounts table created successfully.");

            // Create Transactions Table
            String createTransactionsTable = "CREATE TABLE IF NOT EXISTS transactions (" +
                "transaction_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "account_number TEXT NOT NULL," +
                "transaction_type TEXT NOT NULL CHECK(transaction_type IN ('deposit', 'withdrawal', 'transfer_in', 'transfer_out'))," +
                "amount REAL NOT NULL," +
                "related_account TEXT DEFAULT NULL," +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (account_number) REFERENCES bank_accounts(account_number) ON DELETE CASCADE);";
            stmt.execute(createTransactionsTable);

            System.out.println("Transactions table created successfully.");

            System.out.println("Database and tables created successfully.");

        } catch (SQLException e) {
            throw new IllegalStateException("Database initialization failed", e);
        }
    }

    // ---------------- User Accounts ------------------

    

    // ---------------- Bank Accounts ------------------

    /**
     * Generates a random numeric string of a specified length.
     * Ensures the first digit is non-zero (1-9) so the length is preserved.
     */
    public static String generateRandomDigits(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0");
        }

        StringBuilder sb = new StringBuilder(length);
        
        // First digit: 1-9 (prevents leading zero)
        sb.append(RANDOM.nextInt(9) + 1);

        // Remaining digits: 0-9
        for (int i = 1; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }

        return sb.toString();
    }

    

    

    // ------------------- Transactions --------------------


    // External accounts

    // Manager management

    // Main method to test it out
    public static void main(String[] args) {
        initializeDatabase();
        
        // Test inserting a user
        UserDAO.registerUser("john_doe", "John", "Doe", "john@example.com", "1234567890", "fake_hashed_password_123");
        
        // Test querying the user back
        UserDAO.getUserByUsername("john_doe");

        int userID = UserDAO.getUserIdByUsername("john_doe");

        System.out.println();

        // Example: Create a Checking account with a $250.00 initial deposit for User ID 1
        String testNum1 = BankAccountDAO.createBankAccount(userID, "checking", 250.00);

        // Example: Create a Savings account for the same user (User ID 1)
        String testNum2 = BankAccountDAO.createBankAccount(userID, "savings", 1000.00);

        BankAccountDAO.getBankAccountsByUsername("john_doe");

        System.out.println();

        // Test deposit
        TransactionDAO.deposit(testNum1, 50);

        // Test withdraw
        TransactionDAO.withdraw(testNum1, 25);

        // Test transfer
        TransactionDAO.transfer(testNum1, testNum2, 100);

        BankAccountDAO.getBankAccountsByUsername("john_doe");

        TransactionDAO.getTransactionsByAccountNumber(testNum1);

        System.out.println();

        // Test close
        if (testNum1 != null) {
            BankAccountDAO.closeBankAccount(testNum1);
        }

        // Test deleting a single bank account
        BankAccountDAO.deleteBankAccount(testNum1);

        // Test deleting a user (and cascading to their remaining accounts)
        UserDAO.deleteUser("john_doe");

        // Test finding again
        BankAccountDAO.getBankAccountsByUsername("john_doe");
    }
}
