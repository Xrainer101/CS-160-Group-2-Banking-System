package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    // This will create a file named "bank.db" in your project root directory
    private static 
    final String DB_URL = "jdbc:sqlite:bank.db";

    // 1. Initialize Database and Tables
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
                "account_type TEXT NOT NULL DEFAULT 'checking' CHECK(account_type IN ('checking', 'savings'))," +
                "balance REAL NOT NULL DEFAULT 0.00," +
                "is_active INTEGER NOT NULL DEFAULT 1," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)";
            stmt.execute(createAccountsTable);

            System.out.println("Database and tables created successfully.");

        } catch (SQLException e) {
            System.out.println("Database initialization error: " + e.getMessage());
        }
    }

    // 2. Example: Running an INSERT Query (Registering a User)
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

    // Main method to test it out
    public static void main(String[] args) {
        initializeDatabase();
        
        // Test inserting a user
        registerUser("john_doe", "John", "Doe", "john@example.com", "1234567890", "fake_hashed_password_123");
        
        // Test querying the user back
        getUserByUsername("john_doe");
    }
}
