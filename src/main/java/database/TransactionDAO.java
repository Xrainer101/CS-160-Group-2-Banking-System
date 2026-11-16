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
        return transfer(fromAccountNumber, toAccountNumber, amount, null);
    }

    public static boolean transfer(String fromAccountNumber, String toAccountNumber, double amount, String scheduledFor) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            System.out.println("Transfer failed: Cannot transfer money to the same account.");
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false); // START TRANSACTION
            try {
                if (scheduledFor == null) {
                    // IMMEDIATE: Move the money right now and log as completed
                    processWithdrawal(conn, fromAccountNumber, amount); //  Withdrawal from source account
                    TransactionDAO.logTransaction(conn, fromAccountNumber, "transfer_out", amount, toAccountNumber, "completed", null);
                    
                    processDeposit(conn, toAccountNumber, amount); // Deposit in target account
                    TransactionDAO.logTransaction(conn, toAccountNumber, "transfer_in", amount, fromAccountNumber, "completed", null);
                    
                    System.out.println("Successfully transferred $" + String.format("%.2f", amount) + " immediately.");
                } else {
                    // SCHEDULED: Do NOT move the money. Just log the intent as pending.
                    TransactionDAO.logTransaction(conn, fromAccountNumber, "transfer_out", amount, toAccountNumber, "pending", scheduledFor);
                    TransactionDAO.logTransaction(conn, toAccountNumber, "transfer_in", amount, fromAccountNumber, "pending", scheduledFor);
                    
                    System.out.println("Transfer of $" + String.format("%.2f", amount) + " scheduled for " + scheduledFor + ".");
                }
                
                conn.commit(); // SAVE TRANSACTION
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

    // -- EDITORS --
    /**
     * Edits the amount and/or scheduled date of a pending payment.
     * Pass null for newScheduledFor if you only want to update the amount.
     */
    public static boolean editScheduledTransaction(int transactionId, double newAmount, String newScheduledFor) {
        if (newAmount <= 0) {
            System.out.println("Edit failed: Amount must be greater than zero.");
            return false;
        }

        String selectSql = "SELECT account_number, transaction_type, amount, related_account, status, scheduled_for " +
                           "FROM transactions WHERE transaction_id = ?";
        String updateSql = "UPDATE transactions SET amount = ?, scheduled_for = ? " +
                           "WHERE transaction_id = ? AND status = 'pending'";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String accountNumber;
                String type;
                double oldAmount;
                String relatedAccount;
                String status;
                String oldScheduledFor;

                // 1. Fetch and validate the existing transaction
                try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                    selectStmt.setInt(1, transactionId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Transaction ID " + transactionId + " not found.");
                        }
                        accountNumber = rs.getString("account_number");
                        type = rs.getString("transaction_type");
                        oldAmount = rs.getDouble("amount");
                        relatedAccount = rs.getString("related_account");
                        status = rs.getString("status");
                        oldScheduledFor = rs.getString("scheduled_for");
                    }
                }

                if (!"pending".equals(status)) {
                    throw new SQLException("Only pending transactions can be edited (current status: " + status + ").");
                }
                if ("transfer_in".equals(type)) {
                    throw new SQLException("Recipients cannot edit an incoming scheduled transfer.");
                }

                String finalScheduledFor = (newScheduledFor != null && !newScheduledFor.isEmpty())
                                           ? newScheduledFor
                                           : oldScheduledFor;

                // 2. Update the primary transaction
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setDouble(1, newAmount);
                    updateStmt.setString(2, finalScheduledFor);
                    updateStmt.setInt(3, transactionId);
                    updateStmt.executeUpdate();
                }

                // 3. If it's an outgoing transfer, sync the recipient's matching transfer_in leg
                if ("transfer_out".equals(type) && relatedAccount != null) {
                    int counterpartId = findCounterpartTransferId(conn, relatedAccount, accountNumber, oldAmount, oldScheduledFor);
                    if (counterpartId != -1) {
                        try (PreparedStatement updateCounterpart = conn.prepareStatement(updateSql)) {
                            updateCounterpart.setDouble(1, newAmount);
                            updateCounterpart.setString(2, finalScheduledFor);
                            updateCounterpart.setInt(3, counterpartId);
                            updateCounterpart.executeUpdate();
                        }
                    }
                }

                conn.commit();
                System.out.println("Scheduled transaction #" + transactionId + " updated successfully.");
                return true;

            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Failed to edit scheduled transaction: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Private helper to locate the recipient's matching 'transfer_in' row for a scheduled transfer.
     */
    private static int findCounterpartTransferId(Connection conn, String recipientAcc, String senderAcc,
                                                 double amount, String scheduledFor) throws SQLException {
        String sql = "SELECT transaction_id FROM transactions " +
                     "WHERE account_number = ? AND related_account = ? " +
                     "AND transaction_type = 'transfer_in' AND amount = ? " +
                     "AND scheduled_for = ? AND status = 'pending' LIMIT 1";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recipientAcc);
            pstmt.setString(2, senderAcc);
            pstmt.setDouble(3, amount);
            pstmt.setString(4, scheduledFor);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("transaction_id");
                }
            }
        }
        return -1;
    }

    /**
     * Cancels a pending payment by updating its status to 'cancelled'.
     */
    public static boolean cancelScheduledTransaction(int transactionId) {
        String selectSql = "SELECT account_number, transaction_type, amount, related_account, status, scheduled_for " +
                           "FROM transactions WHERE transaction_id = ?";
        String cancelSql = "UPDATE transactions SET status = 'cancelled' " +
                           "WHERE transaction_id = ? AND status = 'pending'";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String accountNumber;
                String type;
                double amount;
                String relatedAccount;
                String status;
                String scheduledFor;

                // 1. Fetch and validate the existing transaction
                try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                    selectStmt.setInt(1, transactionId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Transaction ID " + transactionId + " not found.");
                        }
                        accountNumber = rs.getString("account_number");
                        type = rs.getString("transaction_type");
                        amount = rs.getDouble("amount");
                        relatedAccount = rs.getString("related_account");
                        status = rs.getString("status");
                        scheduledFor = rs.getString("scheduled_for");
                    }
                }

                if (!"pending".equals(status)) {
                    throw new SQLException("Only pending transactions can be cancelled (current status: " + status + ").");
                }
                if ("transfer_in".equals(type)) {
                    throw new SQLException("Recipients cannot cancel an incoming scheduled transfer.");
                }

                // 2. Cancel the primary transaction
                try (PreparedStatement cancelStmt = conn.prepareStatement(cancelSql)) {
                    cancelStmt.setInt(1, transactionId);
                    cancelStmt.executeUpdate();
                }

                // 3. If it's an outgoing transfer, cancel the recipient's matching transfer_in leg
                if ("transfer_out".equals(type) && relatedAccount != null) {
                    int counterpartId = findCounterpartTransferId(conn, relatedAccount, accountNumber, amount, scheduledFor);
                    if (counterpartId != -1) {
                        try (PreparedStatement cancelCounterpart = conn.prepareStatement(cancelSql)) {
                            cancelCounterpart.setInt(1, counterpartId);
                            cancelCounterpart.executeUpdate();
                        }
                    }
                }

                conn.commit();
                System.out.println("Scheduled transaction #" + transactionId + " cancelled successfully.");
                return true;

            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Failed to cancel scheduled transaction: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
        return false;
    }

    // -- GETTERS --
    /**
     * Queries the ledger and prints a chronological bank statement for a specific account.
     */
    public static List<Transaction> getTransactionsByAccountNumber(String accountNumber) {
        List<Transaction> transactions = new ArrayList<>();

        // ORDER BY timestamp ASC ensures chronological order (oldest to newest)
        String sql = "SELECT transaction_id, account_number, transaction_type, amount, related_account, status, scheduled_for, created_at " +
                     "FROM transactions WHERE account_number = ? ORDER BY created_at ASC";

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
                    String created_at = rs.getString("created_at");

                    // Create the model and add it to the list
                    Transaction transaction = new Transaction(id, accNum, type, amount, relatedAcc, status, scheduledFor, created_at);
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

                    System.out.printf("%-20s | %-14s | %-10s | %s%n", created_at, type, amountDisplay, details);
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

    /**
     * Fetches only upcoming 'pending' transactions for an account, ordered soonest first.
     * Useful for populating a "Scheduled Payments" table in the UI.
     */
    public static List<Transaction> getPendingTransactionsByAccountNumber(String accountNumber) {
        List<Transaction> pendingList = new ArrayList<>();
        String sql = "SELECT transaction_id, account_number, transaction_type, amount, related_account, status, scheduled_for, created_at " +
                     "FROM transactions WHERE account_number = ? AND status = 'pending' ORDER BY scheduled_for ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, accountNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    pendingList.add(new Transaction(
                        rs.getInt("transaction_id"),
                        rs.getString("account_number"),
                        rs.getString("transaction_type"),
                        rs.getDouble("amount"),
                        rs.getString("related_account"),
                        rs.getString("status"),
                        rs.getString("scheduled_for"),
                        rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching pending transactions: " + e.getMessage());
        }
        return pendingList;
    }
}
