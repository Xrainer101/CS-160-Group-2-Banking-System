package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
                    int outgoingId = lastTransactionId(conn);
                    TransactionDAO.logTransaction(conn, toAccountNumber, "transfer_in", amount, fromAccountNumber, "pending", scheduledFor);
                    updateTransferReference(conn, "counterpart_transaction_id", outgoingId, lastTransactionId(conn));
                    
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
                    updateTransferReference(conn, "external_account_id", lastTransactionId(conn), externalAccountId);
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
    

    private static int lastTransactionId(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet row = stmt.executeQuery("SELECT last_insert_rowid()")) {
            if (row.next()) return row.getInt(1);
            throw new SQLException("Missing transaction ID after insert.");
        }
    }

    // column is supplied only by the two scheduled-transfer creation paths.
    private static void updateTransferReference(Connection conn, String column, int id, int reference) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE transactions SET " + column + " = ? WHERE transaction_id = ?")) {
            stmt.setInt(1, reference);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        }
    }

    public record TransferRunResult(int completed, int failed) {
        public int getCompleted() { return completed; }
        public int getFailed() { return failed; }
    }

    private record DueTransfer(int id, String source, String destination, double amount,
                               String scheduledFor, Integer externalAccountId,
                               String sourceStatus, double balance) {}

    public static TransferRunResult processDueTransfers(int userId, String cutoffUtc) throws SQLException {
        int completed = 0;
        int failed = 0;
        try (Connection conn = DatabaseManager.getConnection(); Statement transaction = conn.createStatement()) {
            transaction.execute("PRAGMA busy_timeout = 5000");
            while (true) {
                // Acquire SQLite's write lock before selecting the next pending row.
                transaction.execute("BEGIN IMMEDIATE");
                try {
                    DueTransfer due = findNextDueTransfer(conn, userId, cutoffUtc);
                    String failure = due == null ? null : settleDueTransfer(conn, userId, due);
                    transaction.execute("COMMIT");
                    if (due == null) return new TransferRunResult(completed, failed);
                    if (failure == null) completed++;
                    else failed++;
                } catch (SQLException | RuntimeException failure) {
                    try {
                        transaction.execute("ROLLBACK");
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                    throw failure;
                }
            }
        }
    }

    private static DueTransfer findNextDueTransfer(Connection conn, int userId, String cutoffUtc) throws SQLException {
        String sql = """
            SELECT t.transaction_id, t.account_number, t.related_account, t.amount, t.scheduled_for,
                   t.external_account_id, a.status AS source_status, a.balance
            FROM transactions t
            JOIN bank_accounts a ON a.account_number = t.account_number
            WHERE a.user_id = ? AND t.transaction_type = 'transfer_out' AND t.status = 'pending'
              AND t.recurrence_interval = 0 AND t.recurrence_unit = 'none'
              AND datetime(t.scheduled_for) <= datetime(?)
            ORDER BY datetime(t.scheduled_for), t.transaction_id
            LIMIT 1
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, cutoffUtc);
            try (ResultSet row = stmt.executeQuery()) {
                if (!row.next()) return null;
                int externalId = row.getInt("external_account_id");
                Integer externalReference = row.wasNull() ? null : externalId;
                return new DueTransfer(row.getInt("transaction_id"), row.getString("account_number"),
                    row.getString("related_account"), row.getDouble("amount"), row.getString("scheduled_for"),
                    externalReference, row.getString("source_status"), row.getDouble("balance"));
            }
        }
    }

    private static String settleDueTransfer(Connection conn, int userId, DueTransfer due) throws SQLException {
        String destination = due.destination();
        boolean external = destination != null && destination.startsWith("EXT-");
        int counterpartId = destination != null && !external
            ? findCounterpartTransferId(conn, due.id(), destination, due.source(), due.amount(), due.scheduledFor())
            : -1;

        String failure = null;
        if (!"active".equals(due.sourceStatus())) failure = "The source account is inactive.";
        else if (!Double.isFinite(due.amount()) || due.amount() <= 0) failure = "The transfer amount is invalid.";
        else if (due.balance() < due.amount()) failure = "Insufficient funds on the day the transfer was processed.";
        else if (external && !externalRecipientAvailable(conn, userId, due)) failure = "The saved external account is unavailable.";
        else if (!external && (counterpartId == -1 || destination.equals(due.source())
                || !activeDestination(conn, destination))) failure = "The destination account is unavailable.";

        if (failure == null) {
            processWithdrawal(conn, due.source(), due.amount());
            if (!external) processDeposit(conn, destination, due.amount());
        }
        updateDueTransferStatus(conn, due.id(), failure);
        if (counterpartId != -1) updateDueTransferStatus(conn, counterpartId, failure);
        return failure;
    }

    private static boolean externalRecipientAvailable(Connection conn, int userId, DueTransfer due) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM external_accounts
            WHERE user_id = ? AND account_number = ?
            """;
        if (due.externalAccountId() != null) sql += " AND external_account_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, due.destination().substring("EXT-".length()));
            if (due.externalAccountId() != null) stmt.setInt(3, due.externalAccountId());
            try (ResultSet row = stmt.executeQuery()) {
                return row.next() && row.getInt(1) == 1;
            }
        }
    }

    private static boolean activeDestination(Connection conn, String destination) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM bank_accounts WHERE account_number = ? AND status = 'active'")) {
            stmt.setString(1, destination);
            try (ResultSet row = stmt.executeQuery()) {
                return row.next();
            }
        }
    }

    private static void updateDueTransferStatus(Connection conn, int id, String failure) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE transactions SET status = ?, failure_reason = ? WHERE transaction_id = ? AND status = 'pending'")) {
            stmt.setString(1, failure == null ? "completed" : "failed");
            stmt.setString(2, failure);
            stmt.setInt(3, id);
            if (stmt.executeUpdate() != 1) throw new SQLException("Pending transfer changed during settlement.");
        }
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
                    int counterpartId = findCounterpartTransferId(conn, transactionId, relatedAccount, accountNumber, oldAmount, oldScheduledFor);
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
    private static int findCounterpartTransferId(Connection conn, int outgoingId, String recipientAcc,
                                                 String senderAcc, double amount, String scheduledFor) throws SQLException {
        String sql = """
            SELECT transaction_id FROM transactions
            WHERE transaction_id = (SELECT COALESCE(counterpart_transaction_id, transaction_id + 1)
                                    FROM transactions WHERE transaction_id = ?)
              AND account_number = ? AND related_account = ?
              AND transaction_type = 'transfer_in' AND amount = ?
              AND scheduled_for = ? AND status = 'pending'
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, outgoingId);
            stmt.setString(2, recipientAcc);
            stmt.setString(3, senderAcc);
            stmt.setDouble(4, amount);
            stmt.setString(5, scheduledFor);
            try (ResultSet row = stmt.executeQuery()) {
                return row.next() ? row.getInt("transaction_id") : -1;
            }
        }
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
                if ("transfer_out".equals(type) && relatedAccount != null && !relatedAccount.startsWith("EXT-")) {
                    int counterpartId = findCounterpartTransferId(conn, transactionId, relatedAccount, accountNumber, amount, scheduledFor);
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
                     ", failure_reason FROM transactions WHERE account_number = ? ORDER BY created_at ASC";

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
                    Transaction transaction = new Transaction(id, accNum, type, amount, relatedAcc, status, recurrenceInterval, recurrenceUnit, scheduledFor, created_at, rs.getString("failure_reason"));
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
