package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BankAccountDAO {
    /**
     * Generates a unique 10-digit account number.
     */
    private static String generateUniqueAccountNumber(Connection conn) throws SQLException {
        String accountNumber;
        boolean isDuplicate;
        String checkSql = "SELECT COUNT(*) FROM bank_accounts WHERE account_number = ?";

        do {
            accountNumber = DatabaseManager.generateRandomDigits(10); // Generates 10 digits

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
     * Returns account number on success
     * Returns null on failure
     */
    public static String createBankAccount(int userId, String accountType, double initialDeposit) {
        String insertSql = "INSERT INTO bank_accounts (user_id, account_number, routing_number, account_type, balance) " +
                        "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {

            // Unique 10-digit account number for the customer
            String accountNumber = generateUniqueAccountNumber(conn); 

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, accountNumber);
                pstmt.setString(3, DatabaseManager.BANK_ROUTING_NUMBER); // Use the static bank routing number
                pstmt.setString(4, accountType.toLowerCase());
                pstmt.setDouble(5, initialDeposit);

                int rowsInserted = pstmt.executeUpdate();
                if (rowsInserted > 0) {
                    System.out.println("Account created! User ID: " + userId + 
                                    " | Acc #: " + accountNumber + 
                                    " | Routing #: " + DatabaseManager.BANK_ROUTING_NUMBER + 
                                    " | Type: " + accountType + 
                                    " | Balance: $" + String.format("%.2f", initialDeposit));
                    return accountNumber;
                }
            }
        } catch (SQLException e) {
            System.out.println("Failed to create bank account: " + e.getMessage());
        }
        return null;
    }

    // Return someting useful
    /**
     * Searches for and displays all bank accounts belonging to a given username.
     */
    public static List<BankAccount> getBankAccountsByUsername(String targetUsername) {
        List<BankAccount> accounts = new ArrayList<>();
        
        // SQL JOIN ties bank_accounts to users using user_id
        String sql = "SELECT u.username, u.first_name, u.last_name, " +
                     "ba.account_id, ba.user_id, ba.account_number, ba.routing_number, ba.account_type, ba.balance, ba.status, ba.opened_at, ba.closed_at " +
                     "FROM bank_accounts ba " +
                     "JOIN users u ON ba.user_id = u.user_id " +
                     "WHERE u.username = ?";

        try (Connection conn = DatabaseManager.getConnection();
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
                    int userId = rs.getInt("user_id");
                    String accountNumber = rs.getString("account_number");
                    String routingNumber = rs.getString("routing_number");
                    String type = rs.getString("account_type");
                    double balance = rs.getDouble("balance");
                    String status = rs.getString("status");
                    LocalDateTime opened_at = rs.getObject("opened_at", LocalDateTime.class);
                    LocalDateTime closed_at = rs.getObject("closed_at", LocalDateTime.class);

                    BankAccount account = new BankAccount(accountId, userId, accountNumber, routingNumber, type, balance, status, opened_at, closed_at);
                    accounts.add(account);

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

        return accounts;
    }

    public static boolean closeBankAccount(String accountNumber) {
        String sql = "UPDATE bank_accounts " +
                 "SET status = 'closed', closed_at = datetime('now') " +
                 "WHERE account_number = ? AND status != 'closed'";

        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, accountNumber);
            int rowsUpdated = pstmt.executeUpdate();

            if (rowsUpdated > 0) {
                System.out.println("Bank account #" + accountNumber + " has been successfully closed.");
                return true;
            } else {
                System.out.println("Account closure failed: #" + accountNumber + " not found or already closed.");
            }
        } catch (SQLException e) {
            System.out.println("Error closing bank account: " + e.getMessage());
        }
        return false;
    }

    /**
     * Deletes a specific bank account using its account number.
     */
    public static boolean deleteBankAccount(String accountNumber) {
        String sql = "DELETE FROM bank_accounts WHERE account_number = ?";

        try (Connection conn = DatabaseManager.getConnection();
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
}
