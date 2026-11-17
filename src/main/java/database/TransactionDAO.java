package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {
/**
     * Private helper to log a transaction record on an active connection.
     */
    private static void logTransaction(Connection conn, String accountNumber, String type, double amount, String relatedAccount, String status, String scheduledFor) throws SQLException {
        logTransaction(conn, accountNumber, type, amount, relatedAccount, status, 0, "none", scheduledFor);
    }

    // Full 9-argument method supporting custom recurring intervals
    public static void logTransaction(Connection conn, String accountNumber, String type, double amount,
                                    String relatedAccount, String status, int recurrenceInterval,
                                    String recurrenceUnit, String scheduledFor) throws SQLException {
        String sql = "INSERT INTO transactions " +
                    "(account_number, transaction_type, amount, related_account, status, " +
                    "recurrence_interval, recurrence_unit, scheduled_for) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            pstmt.setString(2, type);
            pstmt.setDouble(3, amount);

            if (relatedAccount != null) pstmt.setString(4, relatedAccount);
            else pstmt.setNull(4, java.sql.Types.VARCHAR);

            pstmt.setString(5, status);
            pstmt.setInt(6, recurrenceInterval);
            pstmt.setString(7, (recurrenceUnit != null) ? recurrenceUnit.toLowerCase() : "none");

            if (scheduledFor != null) pstmt.setString(8, scheduledFor);
            else pstmt.setNull(8, java.sql.Types.VARCHAR);

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

    // -------- EXTERNAL ACCOUNT TRANSFERS

    /**
     * Immediate transfer to external account
     */
    public static boolean transferToExternal(String fromInternalAccount, int externalAccountId, double amount) {
        return transferToExternal(fromInternalAccount, externalAccountId, amount, null);
    }

    /**
     * Unified transfer to external account (Immediate or Scheduled)
     */
    public static boolean transferToExternal(String fromInternalAccount, int externalAccountId,
                                             double amount, String scheduledFor) {
        ExternalAccount ext = ExternalAccountDAO.getExternalAccountById(externalAccountId);
        if (ext == null) {
            System.out.println("Transfer failed: External account not found.");
            return false;
        }

        String externalLabel = "EXT-" + ext.getAccountNumber();

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (scheduledFor == null) {
                    processWithdrawal(conn, fromInternalAccount, amount);
                    TransactionDAO.logTransaction(conn, fromInternalAccount, "transfer_out",
                                                  amount, externalLabel, "completed", null);
                } else {
                    TransactionDAO.logTransaction(conn, fromInternalAccount, "transfer_out",
                                                  amount, externalLabel, "pending", scheduledFor);
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("External outgoing transfer failed: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Immediate transfer from external account
     */
    public static boolean transferFromExternal(int externalAccountId, String toInternalAccount, double amount) {
        return transferFromExternal(externalAccountId, toInternalAccount, amount, null);
    }

    /**
     * Unified transfer from external account (Immediate or Scheduled)
     */
    public static boolean transferFromExternal(int externalAccountId, String toInternalAccount,
                                               double amount, String scheduledFor) {
        ExternalAccount ext = ExternalAccountDAO.getExternalAccountById(externalAccountId);
        if (ext == null) {
            System.out.println("Transfer failed: External account not found.");
            return false;
        }

        String externalLabel = "EXT-" + ext.getAccountNumber();

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (scheduledFor == null) {
                    processDeposit(conn, toInternalAccount, amount);
                    TransactionDAO.logTransaction(conn, toInternalAccount, "transfer_in",
                                                  amount, externalLabel, "completed", null);
                } else {
                    TransactionDAO.logTransaction(conn, toInternalAccount, "transfer_in",
                                                  amount, externalLabel, "pending", scheduledFor);
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("External incoming transfer failed: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
        return false;
    }

    // Reocccuring payments
    public static boolean scheduleRecurringBill(String fromAccountNumber, String payeeLabel, double amount,
                                            String firstPaymentDate, int interval, String unit) {
        if (amount <= 0) {
            System.out.println("Bill payment failed: Amount must be greater than zero.");
            return false;
        }

        String normalizedUnit = (unit != null) ? unit.toLowerCase() : "none";
        if (interval <= 0 || !java.util.List.of("days", "weeks", "months", "years").contains(normalizedUnit)) {
            System.out.println("Bill payment failed: Interval must be > 0 and unit must be days, weeks, months, or years.");
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String targetLabel = (payeeLabel.startsWith("EXT-") || payeeLabel.startsWith("BILL-"))
                                    ? payeeLabel
                                    : "BILL-" + payeeLabel;

                TransactionDAO.logTransaction(
                    conn, fromAccountNumber, "withdrawal", amount, targetLabel,
                    "pending", interval, normalizedUnit, firstPaymentDate
                );

                conn.commit();
                System.out.println("Scheduled bill of $" + String.format("%.2f", amount) +
                                " to " + targetLabel + " every " + interval + " " + normalizedUnit +
                                " starting " + firstPaymentDate);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Failed to schedule recurring bill: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
        return false;
    }

    public static String calculateNextScheduledDate(String currentScheduledFor, int interval, String unit) {
        if (interval <= 0 || unit == null || "none".equalsIgnoreCase(unit)) {
            return null;
        }

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        java.time.LocalDateTime date = java.time.LocalDateTime.parse(currentScheduledFor, formatter);

        java.time.LocalDateTime nextDate = switch (unit.toLowerCase()) {
            case "days"   -> date.plusDays(interval);
            case "weeks"  -> date.plusWeeks(interval);
            case "months" -> date.plusMonths(interval);
            case "years"  -> date.plusYears(interval);
            default       -> null;
        };

        return (nextDate != null) ? nextDate.format(formatter) : null;
    }
    

    // ------- EDITORS --------
    /**
     * Edits the amount and/or scheduled date of a pending payment.
     * Pass null for newScheduledFor if you only want to update the amount.
     */
    public static boolean editScheduledTransaction(int transactionId, double newAmount, String newScheduledFor) {
        return editScheduledTransaction(transactionId, newAmount, newScheduledFor, null, null);
    }

    /**
     * Full method: Edits amount, scheduled date, recurrence interval, and/or recurrence unit
     * on a pending scheduled transaction or recurring bill.
     * Pass null for newScheduledFor, newRecurrenceInterval, or newRecurrenceUnit to keep current values.
     */
    public static boolean editScheduledTransaction(int transactionId, double newAmount, String newScheduledFor,
                                                   Integer newRecurrenceInterval, String newRecurrenceUnit) {
        if (newAmount <= 0) {
            System.out.println("Edit failed: Amount must be greater than zero.");
            return false;
        }

        String selectSql = "SELECT account_number, transaction_type, amount, related_account, status, " +
                           "recurrence_interval, recurrence_unit, scheduled_for " +
                           "FROM transactions WHERE transaction_id = ?";
        String updateSql = "UPDATE transactions " +
                           "SET amount = ?, scheduled_for = ?, recurrence_interval = ?, recurrence_unit = ? " +
                           "WHERE transaction_id = ? AND status = 'pending'";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String accountNumber;
                String type;
                double oldAmount;
                String relatedAccount;
                String status;
                int oldInterval;
                String oldUnit;
                String oldScheduledFor;

                // Fetch and validate the existing transaction
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
                        oldInterval = rs.getInt("recurrence_interval");
                        oldUnit = rs.getString("recurrence_unit");
                        oldScheduledFor = rs.getString("scheduled_for");
                    }
                }

                if (!"pending".equals(status)) {
                    throw new SQLException("Only pending transactions can be edited (current status: " + status + ").");
                }
                if ("transfer_in".equals(type) && (relatedAccount == null || !relatedAccount.startsWith("EXT-"))) {
                    throw new SQLException("Recipients cannot edit an incoming internal transfer.");
                }

                // Resolve final values (keep existing values where null was passed)
                String finalScheduledFor = (newScheduledFor != null && !newScheduledFor.isEmpty())
                                           ? newScheduledFor
                                           : oldScheduledFor;

                String finalUnit = (newRecurrenceUnit != null && !newRecurrenceUnit.isEmpty())
                                   ? newRecurrenceUnit.toLowerCase()
                                   : oldUnit;

                int finalInterval = (newRecurrenceInterval != null)
                                    ? newRecurrenceInterval
                                    : oldInterval;

                // Validate recurrence rules
                if (!java.util.List.of("none", "days", "weeks", "months", "years").contains(finalUnit)) {
                    throw new SQLException("Invalid recurrence unit: " + finalUnit);
                }
                if ("none".equals(finalUnit)) {
                    finalInterval = 0; // Normalize interval to 0 if recurrence is turned off
                } else if (finalInterval <= 0) {
                    throw new SQLException("Recurrence interval must be greater than 0 for unit '" + finalUnit + "'.");
                }

                // Update the primary transaction
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setDouble(1, newAmount);
                    updateStmt.setString(2, finalScheduledFor);
                    updateStmt.setInt(3, finalInterval);
                    updateStmt.setString(4, finalUnit);
                    updateStmt.setInt(5, transactionId);
                    updateStmt.executeUpdate();
                }

                // If it's an outgoing internal transfer, sync the recipient's matching transfer_in leg
                if ("transfer_out".equals(type) && relatedAccount != null && !relatedAccount.startsWith("EXT-")) {
                    int counterpartId = findCounterpartTransferId(conn, relatedAccount, accountNumber, oldAmount, oldScheduledFor);
                    if (counterpartId != -1) {
                        try (PreparedStatement updateCounterpart = conn.prepareStatement(updateSql)) {
                            updateCounterpart.setDouble(1, newAmount);
                            updateCounterpart.setString(2, finalScheduledFor);
                            updateCounterpart.setInt(3, finalInterval);
                            updateCounterpart.setString(4, finalUnit);
                            updateCounterpart.setInt(5, counterpartId);
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
                if ("transfer_in".equals(type) && (relatedAccount == null || !relatedAccount.startsWith("EXT-"))) {
                    throw new SQLException("Recipients cannot cancel an incoming internal transfer.");
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
        String sql = "SELECT transaction_id, account_number, transaction_type, amount, related_account, status, recurrence_interval, recurrence_unit, scheduled_for, created_at " +
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
                    int recurrenceInterval = rs.getInt("recurrence_interval");
                    String recurrenceUnit = rs.getString("recurrence_unit");
                    String scheduledFor = rs.getString("scheduled_for");
                    String created_at = rs.getString("created_at");

                    // Create the model and add it to the list
                    Transaction transaction = new Transaction(id, accNum, type, amount, relatedAcc, status, recurrenceInterval, recurrenceUnit, scheduledFor, created_at);
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
        String sql = "SELECT transaction_id, account_number, transaction_type, amount, related_account, status, recurrence_interval, recurrence_unit, scheduled_for, created_at " +
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
                        rs.getInt("recurrence_interval"),
                        rs.getString("recurrence_unit"),
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
