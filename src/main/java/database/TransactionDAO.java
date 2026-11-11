package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {
/**
     * Private helper to log a transaction record on an active connection.
     */
    private static void logTransaction(Connection conn, String accountNumber, String type, double amount, String relatedAccount, String status, String scheduledFor) throws SQLException {
        String sql = "INSERT INTO transactions (account_number, transaction_type, amount, related_account, status, scheduled_for) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            pstmt.setString(2, type);
            pstmt.setDouble(3, amount);
            
            if (relatedAccount != null) {
                pstmt.setString(4, relatedAccount);
            } else {
                pstmt.setNull(4, Types.VARCHAR); // Handles standard deposits/withdrawals
            }

            pstmt.setString(5, status);
        
            if (scheduledFor != null) pstmt.setString(6, scheduledFor);
            else pstmt.setNull(6, java.sql.Types.VARCHAR); // SQLite will use DEFAULT CURRENT_TIMESTAMP
            
            pstmt.executeUpdate();
        }
    }

    /**
     * Private helper to process a deposit on an active connection.
     */
    private static void processDeposit(Connection conn, String accountNumber, double amount) throws SQLException {
        if (amount <= 0) {
            throw new SQLException("Deposit amount must be greater than zero.");
        }

        // Verify account exists and is active
        String checkSql = "SELECT status FROM bank_accounts WHERE account_number = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, accountNumber);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) throw new SQLException("Account #" + accountNumber + " does not exist.");
                if (!"active".equals(rs.getString("status"))) {
                    throw new SQLException("Account #" + accountNumber + " is " + rs.getString("status") + ".");
                }
            }
        }

        // Add funds
        String updateSql = "UPDATE bank_accounts SET balance = balance + ? WHERE account_number = ?";
        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            updateStmt.setDouble(1, amount);
            updateStmt.setString(2, accountNumber);
            updateStmt.executeUpdate();
        }
    }

    /**
     * Private helper to process a withdrawal on an active connection.
     */
    private static void processWithdrawal(Connection conn, String accountNumber, double amount) throws SQLException {
        if (amount <= 0) {
            throw new SQLException("Withdrawal amount must be greater than zero.");
        }

        // Verify account exists, is active, and has sufficient funds
        String checkSql = "SELECT balance, status FROM bank_accounts WHERE account_number = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, accountNumber);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) throw new SQLException("Account #" + accountNumber + " does not exist.");
                if (!"active".equals(rs.getString("status"))) {
                    throw new SQLException("Account #" + accountNumber + " is " + rs.getString("status") + ".");
                }
                if (rs.getDouble("balance") < amount) {
                    throw new SQLException("Insufficient funds in account #" + accountNumber + ".");
                }
            }
        }

        // Deduct funds
        String updateSql = "UPDATE bank_accounts SET balance = balance - ? WHERE account_number = ?";
        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            updateStmt.setDouble(1, amount);
            updateStmt.setString(2, accountNumber);
            updateStmt.executeUpdate();
        }
    }

    /**
     * Public method to deposit money into a single account.
     */
    public static boolean deposit(String accountNumber, double amount) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false); // START TRANSACTION
            try {
                processDeposit(conn, accountNumber, amount);
                logTransaction(conn, accountNumber, "deposit", amount, null, "completed", null); // Log it

                conn.commit(); // SAVE TRANSACTION
                System.out.println("Successfully deposited $" + String.format("%.2f", amount) + " to #" + accountNumber);
                return true;
            } catch (SQLException e) {
                conn.rollback(); // CANCEL TRANSACTION
                System.out.println("Deposit failed: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Public method to withdraw money from a single account.
     */
    public static boolean withdraw(String accountNumber, double amount) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false); // START TRANSACTION
            try {
                processWithdrawal(conn, accountNumber, amount);
                logTransaction(conn, accountNumber, "withdrawal", amount, null, "completed", null); // Log it

                conn.commit(); // SAVE TRANSACTION
                System.out.println("Successfully withdrew $" + String.format("%.2f", amount) + " from #" + accountNumber);
                return true;
            } catch (SQLException e) {
                conn.rollback(); // CANCEL TRANSACTION
                System.out.println("Withdrawal failed: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Public method to transfer money between two accounts atomically.
     */
    public static boolean transfer(String fromAccountNumber, String toAccountNumber, double amount) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            System.out.println("Transfer failed: Cannot transfer money to the same account.");
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false); // START TRANSACTION
            try {
                // Execute both operations on the same connection.
                processWithdrawal(conn, fromAccountNumber, amount); //  Withdrawal from source account
                logTransaction(conn, fromAccountNumber, "transfer_out", amount, toAccountNumber, "completed", null);

                processDeposit(conn, toAccountNumber, amount); // Deposit in target account
                logTransaction(conn, toAccountNumber, "transfer_in", amount, fromAccountNumber, "completed", null);
                
                conn.commit(); // SAVE TRANSACTION
                System.out.println("Successfully transferred $" + String.format("%.2f", amount) + 
                                   " from #" + fromAccountNumber + " to #" + toAccountNumber);
                return true;
            } catch (SQLException e) {
                conn.rollback(); // CANCEL TRANSACTION
                System.out.println("Transfer failed (Transaction rolled back): " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
        return false;
    }

    // Scheduled transfers

    /**
     * Queries the ledger and prints a chronological bank statement for a specific account.
     */
    public static List<Transaction> getTransactionsByAccountNumber(String accountNumber) {
        List<Transaction> transactions = new ArrayList<>();

        // ORDER BY timestamp ASC ensures chronological order (oldest to newest)
        String sql = "SELECT transaction_id, account_number, transaction_type, amount, related_account, status, scheduled_for, timestamp " +
                     "FROM transactions WHERE account_number = ? ORDER BY timestamp ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, accountNumber);

            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println("\n=== Bank Statement for Account #" + accountNumber + " ===");
                System.out.printf("%-20s | %-14s | %-10s | %s%n", "Date", "Type", "Amount", "Details");
                System.out.println("----------------------------------------------------------------------");

                boolean hasTransactions = false;

                while (rs.next()) {
                    hasTransactions = true;
                    int id = rs.getInt("transaction_id");
                    String accNum = rs.getString("account_number");
                    String type = rs.getString("transaction_type");
                    double amount = rs.getDouble("amount");
                    String relatedAcc = rs.getString("related_account");
                    String status = rs.getString("status");
                    String scheduledFor = rs.getString("scheduled_for");
                    String timestamp = rs.getString("timestamp");

                    // Create the model and add it to the list
                    Transaction transaction = new Transaction(id, accNum, type, amount, relatedAcc, status, scheduledFor, timestamp);
                    transactions.add(transaction);

                    // Determine if money is coming in or going out for formatting
                    String amountDisplay;
                    if (type.equals("DEPOSIT") || type.equals("TRANSFER_IN")) {
                        amountDisplay = String.format("+$%.2f", amount);
                    } else {
                        amountDisplay = String.format("-$%.2f", amount);
                    }

                    // Show the related account only if it was a transfer
                    String details = (relatedAcc != null) ? "Account #" + relatedAcc : "-";

                    System.out.printf("%-20s | %-14s | %-10s | %s%n", timestamp, type, amountDisplay, details);
                }

                if (!hasTransactions) {
                    System.out.println("No transactions found for this account.");
                }
                System.out.println("----------------------------------------------------------------------\n");
            }
        } catch (SQLException e) {
            System.out.println("Error generating bank statement: " + e.getMessage());
        }

        return transactions;
    }

    // Method to edit payments
}
