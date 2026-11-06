package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

public class UserDAO {
    // Registering a User
    public static void registerUser(String username, String firstName, String lastName, String email, String phoneNumber, String passwordHash) {
        String sql = "INSERT INTO users (username, first_name, last_name, email, phone_number, password_hash) VALUES (?, ?, ?, ?, ?, ?)";

        // Try-with-resources ensures connection and statement close automatically
        try (Connection conn = DatabaseManager.getConnection();
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

    public static User getUserByUsername(String targetUsername) {
        String sql = "SELECT user_id, username, first_name, last_name, email, phone_number, password_hash, role, failed_login_attempts, lockout_until, created_at FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, targetUsername);
            
            // Use executeQuery() for SELECT statements to get a ResultSet
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("user_id");
                    String user = rs.getString("username");
                    String firstName = rs.getString("first_name");
                    String lastName = rs.getString("last_name");
                    String email = rs.getString("email");
                    String phone = rs.getString("phone_number");
                    String password_hash = rs.getString("password_hash");
                    String role = rs.getString("role");
                    int failedAttempts = rs.getInt("failed_login_attempts");
                    LocalDateTime lockout_until = rs.getObject("lockout_until", LocalDateTime.class);
                    LocalDateTime created_at = rs.getObject("created_at", LocalDateTime.class);

                    System.out.println("Found User -> ID: " + id
                        + ", Username: " + user
                        + ", Name: " + firstName + " " + lastName
                        + ", Email: " + email
                        + ", Phone: " + phone
                        + ", Role: " + role
                        + ", Failed Logins: " + failedAttempts);

                    return new User(id, user, firstName, lastName, email, phone, password_hash, role, failedAttempts, lockout_until, created_at);
                } else {
                    System.out.println("No user found with username: " + targetUsername);
                }
            }

        } catch (SQLException e) {
            System.out.println("Query execution error: " + e.getMessage());
        }

        return null;
    }

    /**
     * Fetches a user's ID by their username to avoid hardcoding foreign keys.
     */
    public static int getUserIdByUsername(String username) {
        String sql = "SELECT user_id FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_id");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching user ID: " + e.getMessage());
        }
        return -1; // User not found
    }

    /**
     * Deletes a user profile by username. 
     * Re-enables foreign key constraints so ON DELETE CASCADE automatically 
     * removes any bank accounts linked to this user.
     */
    public static boolean deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection()) {

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
}
