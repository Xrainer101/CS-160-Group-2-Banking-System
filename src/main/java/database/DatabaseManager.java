package database;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    // This will create a file named "bank.db" in your project root directory
    private static final String DB_URL =
        "jdbc:sqlite:" + System.getProperty("bank.db.path", "bank.db");
    private static final SecureRandom RANDOM = new SecureRandom();
    // Static constant for your bank's routing number (must be 9 digits)
    public static final String BANK_ROUTING_NUMBER = "123456789";

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

            System.out.println("Database and tables created successfully.");

        } catch (SQLException e) {
            throw new IllegalStateException("Database initialization failed", e);
        }
    }

    // ---------------- User Accounts ------------------

    // Registering a User
    public static void registerUser(String username, String firstName, String lastName, String email, String phoneNumber, String passwordHash) {
        String sql = "INSERT INTO users (username, first_name, last_name, email, phone_number, password_hash) VALUES (?, ?, ?, ?, ?, ?)";

        // Try-with-resources ensures connection and statement close automatically
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Bind parameters to the '?' placeholders (prevents SQL injection)
            pstmt.setString(1, username);
            pstmt.setString(2, firstName);
            pstmt.setString(3, lastName);
            pstmt.setString(4, email);
            pstmt.setString(5, phoneNumber);
            pstmt.setString(6, passwordHash);
            
            pstmt.executeUpdate();
            System.out.println("User '" + username + "' registered successfully!");

        } catch (SQLException e) {
            System.out.println("Registration failed (Username, email, or phone number might already exist): " + e.getMessage());
        }
    }

    // 3. Example: Running a SELECT Query (Fetching User Details)
    public static void getUserByUsername(String targetUsername) {
        String sql = "SELECT user_id, username, first_name, last_name, email, phone_number, role, failed_login_attempts FROM users WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, targetUsername);
            
            // Use executeQuery() for SELECT statements to get a ResultSet
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("user_id");
                    String user = rs.getString("username");
                    String firstName = rs.getString("first_name");
                    String lastName = rs.getString("last_name");
                    long phone = rs.getLong("phone_number");
                    String role = rs.getString("role");
                    int failedAttempts = rs.getInt("failed_login_attempts");

                    System.out.println("Found User -> ID: " + id
                        + ", Username: " + user
                        + ", Name: " + firstName + " " + lastName
                        + ", Phone: " + phone
                        + ", Role: " + role
                        + ", Failed Logins: " + failedAttempts);
                } else {
                    System.out.println("No user found with username: " + targetUsername);
                }
            }

        } catch (SQLException e) {
            System.out.println("Query execution error: " + e.getMessage());
        }
    }

    /**
     * Deletes a user profile by username. 
     * Re-enables foreign key constraints so ON DELETE CASCADE automatically 
     * removes any bank accounts linked to this user.
     */
    public static boolean deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL)) {

            // Enable foreign keys so linked bank_accounts are automatically cleaned up
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, username);
                int rowsDeleted = pstmt.executeUpdate();

                if (rowsDeleted > 0) {
                    System.out.println("User '" + username + "' and all linked bank accounts were deleted successfully.");
                    return true;
                } else {
                    System.out.println("No user found with username: " + username);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error deleting user: " + e.getMessage());
        }
        return false;
    }

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

    /**
     * Generates a unique 10-digit account number.
     */
    private static String generateUniqueAccountNumber(Connection conn) throws SQLException {
        String accountNumber;
        boolean isDuplicate;
        String checkSql = "SELECT COUNT(*) FROM bank_accounts WHERE account_number = ?";

        do {
            accountNumber = generateRandomDigits(10); // Generates 10 digits

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, accountNumber);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    isDuplicate = rs.next() && rs.getInt(1) > 0;
                }
            }
        } while (isDuplicate);

        return accountNumber;
    }

    /**
     * Creates and inserts a new bank account for an existing user.
     */
    public static boolean createBankAccount(int userId, String accountType, double initialDeposit) {
    String insertSql = "INSERT INTO bank_accounts (user_id, account_number, routing_number, account_type, balance) " +
                       "VALUES (?, ?, ?, ?, ?)";

    try (Connection conn = DriverManager.getConnection(DB_URL)) {

        // Unique 10-digit account number for the customer
        String accountNumber = generateUniqueAccountNumber(conn); 

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, accountNumber);
            pstmt.setString(3, BANK_ROUTING_NUMBER); // Use the static bank routing number
            pstmt.setString(4, accountType.toLowerCase());
            pstmt.setDouble(5, initialDeposit);

            int rowsInserted = pstmt.executeUpdate();
            if (rowsInserted > 0) {
                System.out.println("Account created! User ID: " + userId + 
                                   " | Acc #: " + accountNumber + 
                                   " | Routing #: " + BANK_ROUTING_NUMBER + 
                                   " | Type: " + accountType + 
                                   " | Balance: $" + String.format("%.2f", initialDeposit));
                return true;
            }
        }
    } catch (SQLException e) {
        System.out.println("Failed to create bank account: " + e.getMessage());
    }
    return false;
}

    /**
     * Searches for and displays all bank accounts belonging to a given username.
     */
    public static void getBankAccountsByUsername(String targetUsername) {
        // SQL JOIN ties bank_accounts to users using user_id
        String sql = "SELECT u.username, u.first_name, u.last_name, " +
                     "ba.account_id, ba.account_number, ba.routing_number, ba.account_type, ba.balance, ba.status " +
                     "FROM bank_accounts ba " +
                     "JOIN users u ON ba.user_id = u.user_id " +
                     "WHERE u.username = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, targetUsername);

            try (ResultSet rs = pstmt.executeQuery()) {
                boolean foundAccount = false;

                while (rs.next()) {
                    // Print header once on the first iteration
                    if (!foundAccount) {
                        String fullName = rs.getString("first_name") + " " + rs.getString("last_name");
                        System.out.println("\n=== Bank Accounts for " + targetUsername + " (" + fullName + ") ===");
                        foundAccount = true;
                    }

                    int accountId = rs.getInt("account_id");
                    String accountNumber = rs.getString("account_number");
                    String routingNumber = rs.getString("routing_number");
                    String type = rs.getString("account_type");
                    double balance = rs.getDouble("balance");
                    String status = rs.getString("status");

                    System.out.printf("Account ID: %d | Acc #: %s | Routing #: %s | Type: %-8s | Balance: $%.2f | Status: %s%n",
                            accountId, accountNumber, routingNumber, type, balance, status);
                }

                if (!foundAccount) {
                    System.out.println("No bank accounts found for username: " + targetUsername);
                }
            }

        } catch (SQLException e) {
            System.out.println("Error searching bank accounts: " + e.getMessage());
        }
    }

    /**
     * Deletes a specific bank account using its account number.
     */
    public static boolean deleteBankAccount(String accountNumber) {
        String sql = "DELETE FROM bank_accounts WHERE account_number = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, accountNumber);
            int rowsDeleted = pstmt.executeUpdate();

            if (rowsDeleted > 0) {
                System.out.println("Bank account #" + accountNumber + " deleted successfully.");
                return true;
            } else {
                System.out.println("No bank account found with account number: " + accountNumber);
            }
        } catch (SQLException e) {
            System.out.println("Error deleting bank account: " + e.getMessage());
        }
        return false;
    }

    // Main method to test it out
    public static void main(String[] args) {
        initializeDatabase();
        
        // Test inserting a user
        registerUser("john_doe", "John", "Doe", "john@example.com", "1234567890", "fake_hashed_password_123");
        
        // Test querying the user back
        getUserByUsername("john_doe");

        // Example: Create a Checking account with a $250.00 initial deposit for User ID 1
        createBankAccount(1, "checking", 250.00);

        // Example: Create a Savings account for the same user (User ID 1)
        createBankAccount(1, "savings", 1000.00);

        getBankAccountsByUsername("john_doe");

        // Test deleting a single bank account
        //deleteBankAccount("1029384756");

        // Test deleting a user (and cascading to their remaining accounts)
        //deleteUser("john_doe");
    }
}
