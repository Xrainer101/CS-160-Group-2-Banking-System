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

    // --- Registering an Auth0 User ---
    public static User registerAuth0User(String auth0Sub, String username, String firstName, String lastName, String email, String phoneNumber) {
        String userSql = "INSERT INTO users (username, first_name, last_name, email, phone_number, password_hash) VALUES (?, ?, ?, ?, ?, ?)";
        String identitySql = "INSERT INTO auth0_identities (auth0_sub, user_id) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement userStatement = conn.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS)) {
                userStatement.setString(1, username);
                userStatement.setString(2, firstName);
                userStatement.setString(3, lastName);
                userStatement.setString(4, email);
                userStatement.setString(5, phoneNumber);
                userStatement.setString(6, "auth0-managed");
                userStatement.executeUpdate();

                try (ResultSet generatedKeys = userStatement.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        conn.rollback();
                        return null;
                    }
                    try (PreparedStatement identityStatement = conn.prepareStatement(identitySql)) {
                        identityStatement.setString(1, auth0Sub);
                        identityStatement.setInt(2, generatedKeys.getInt(1));
                        identityStatement.executeUpdate();
                    }
                }
            }
            conn.commit();
        } catch (SQLException e) {
            System.out.println("Auth0 user registration failed: " + e.getMessage());
            return null;
        }
        return getUserByAuth0Sub(auth0Sub);
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

                    return userFromResultSet(rs);
                } else {
                    System.out.println("No user found with username: " + targetUsername);
                }
            }

        } catch (SQLException e) {
            System.out.println("Query execution error: " + e.getMessage());
        }
        return null;
    }

    // --- Finds a banking user by their Auth0 identifier ---
    public static User getUserByAuth0Sub(String auth0Sub) {
        String sql = "SELECT u.user_id, u.username, u.first_name, u.last_name, u.email, " +
            "u.phone_number, u.password_hash, u.role, u.failed_login_attempts, " +
            "u.lockout_until, u.created_at " +
            "FROM users u JOIN auth0_identities ai ON ai.user_id = u.user_id " +
            "WHERE ai.auth0_sub = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auth0Sub);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? userFromResultSet(rs) : null;
            }
        } catch (SQLException e) {
            System.out.println("Error finding Auth0 user: " + e.getMessage());
            return null;
        }
    }
    // --- Links an Auth0 identifier to an existing banking user using email only for first-time linking ---
    public static User linkAuth0SubByEmail(String auth0Sub, String email) {
        User alreadyLinked = getUserByAuth0Sub(auth0Sub);
        if (alreadyLinked != null) return alreadyLinked;

        String findUserSql = "SELECT user_id FROM users WHERE lower(email) = lower(?)";
        String linkSql = "INSERT INTO auth0_identities (auth0_sub, user_id) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement findUser = conn.prepareStatement(findUserSql)) {
            findUser.setString(1, email);
            try (ResultSet rs = findUser.executeQuery()) {
                if (!rs.next()) return null;

                int userId = rs.getInt("user_id");
                try (PreparedStatement link = conn.prepareStatement(linkSql)) {
                    link.setString(1, auth0Sub);
                    link.setInt(2, userId);
                    link.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error linking Auth0 user: " + e.getMessage());
            return null;
        }
        return getUserByAuth0Sub(auth0Sub);
    }
    private static User userFromResultSet(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("user_id"),
            rs.getString("username"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("email"),
            rs.getString("phone_number"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getInt("failed_login_attempts"),
            rs.getObject("lockout_until", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class)
        );
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
