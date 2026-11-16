package database;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

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

            // Create External Account table
            String createExtAccountTable = "CREATE TABLE IF NOT EXISTS external_accounts (" +
                "external_account_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "bank_name TEXT NOT NULL," + 
                "account_holder_name TEXT NOT NULL," +
                "account_number TEXT NOT NULL," +
                "routing_number TEXT NOT NULL," + 
                "account_type TEXT NOT NULL CHECK(account_type IN ('checking', 'savings'))," + 
                "nickname TEXT DEFAULT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE(user_id, routing_number, account_number)," +
                "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE);";
            stmt.execute(createExtAccountTable);
            System.out.println("External accounts table created successfully");

            // Create Transactions Table
            String createTransactionsTable = "CREATE TABLE IF NOT EXISTS transactions (" +
                "transaction_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "account_number TEXT NOT NULL," +
                "transaction_type TEXT NOT NULL CHECK(transaction_type IN ('deposit', 'withdrawal', 'transfer_in', 'transfer_out'))," +
                "amount REAL NOT NULL," +
                "related_account TEXT DEFAULT NULL," +
                "status TEXT NOT NULL DEFAULT 'completed' CHECK(status IN ('pending', 'completed', 'failed', 'cancelled'))," +
                "scheduled_for DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
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
        
        // Test inserting users
        System.out.println("=== SETUP: Creating Sender & Recipient ===");
        UserDAO.registerUser("john_doe", "John", "Doe", "john@example.com", "1234567890", "fake_hashed_password_123");
        UserDAO.registerUser("bob_recipient", "Bob", "Jones", "bob@example.com", "4445556666", "hash_bob");
        
        System.out.println();

        // Test querying the user back
        System.out.println("=== TEST: Getting user and ids ===");
        UserDAO.getUserByUsername("john_doe");

        int johnId = UserDAO.getUserIdByUsername("john_doe");
        int bobId = UserDAO.getUserIdByUsername("bob_recipient");

        System.out.println();

        // Example: Create a Checking account with a $250.00 initial deposit
        System.out.println("=== TEST: Creating and getting bank accounts ===");
        String johnCheckAcc = BankAccountDAO.createBankAccount(johnId, "checking", 250.00);

        // Example: Create a Savings account for the same user
        String johnSaveAcc = BankAccountDAO.createBankAccount(johnId, "savings", 1000.00);
        String bobAcc = BankAccountDAO.createBankAccount(bobId, "savings", 500.00);

        BankAccountDAO.getBankAccountsByUsername("john_doe");

        System.out.println();

        System.out.println("=== TEST: Deposit and withdrawal ===");
        // Test deposit
        TransactionDAO.deposit(johnCheckAcc, 50);

        // Test withdraw
        TransactionDAO.withdraw(johnCheckAcc, 25);

        // Test instant transfer
        System.out.println("=== TEST: Instant transfer ===");
        TransactionDAO.transfer(johnCheckAcc, johnSaveAcc, 100);

        BankAccountDAO.getBankAccountsByUsername("john_doe");

        TransactionDAO.getTransactionsByAccountNumber(johnCheckAcc);

        System.out.println();

        System.out.println("=== TEST: Creating Two Future Transfers ===");
        // Schedule Transfer #1 ($250) and Transfer #2 ($75)
        TransactionDAO.transfer(johnCheckAcc, bobAcc, 250.00, "2026-12-15 09:00:00");
        TransactionDAO.transfer(johnCheckAcc, bobAcc, 75.00, "2026-12-25 10:00:00");

        // Verify balances have NOT changed yet
        double johnBalance = BankAccountDAO.getBankAccountsByUsername("john_doe").get(0).getBalance();
        double bobBalance = BankAccountDAO.getBankAccountsByUsername("bob_recipient").get(0).getBalance();
        System.out.println("John balance (expected $1000.00): $" + String.format("%.2f", johnBalance));
        System.out.println("Bob balance   (expected $500.00):  $" + String.format("%.2f", bobBalance));

        System.out.println();

        // Modify scheduled transfer
        System.out.println("=== TEST: Modifying Scheduled Transfer #1 ===");
        List<Transaction> johnPending = TransactionDAO.getPendingTransactionsByAccountNumber(johnCheckAcc);
        Transaction firstTx = johnPending.get(0); // The $250.00 transfer
        System.out.println("Before Edit -> ID: " + firstTx.getTransactionId() +
                           " | Amount: $" + firstTx.getAmount() +
                           " | Date: " + firstTx.getScheduledFor());

        // Update amount to $300.00 and push date to Dec 20
        boolean editSuccess = TransactionDAO.editScheduledTransaction(
            firstTx.getTransactionId(), 300.00, "2026-12-20 14:30:00"
        );
        System.out.println("Edit returned: " + editSuccess);

        // Verify BOTH Alice's and Bob's pending records updated
        Transaction johnUpdated = TransactionDAO.getPendingTransactionsByAccountNumber(johnCheckAcc).get(0);
        Transaction bobUpdated = TransactionDAO.getPendingTransactionsByAccountNumber(bobAcc).get(0);
        System.out.println("John After Edit -> $" + johnUpdated.getAmount() + " on " + johnUpdated.getScheduledFor());
        System.out.println("Bob After Edit   -> $" + bobUpdated.getAmount() + " on " + bobUpdated.getScheduledFor());

        System.out.println("\n=== GUARDRAIL CHECK: Recipient Tries to Edit ===");
        boolean illegalEdit = TransactionDAO.editScheduledTransaction(bobUpdated.getTransactionId(), 9999.00, null);
        System.out.println("Recipient edit blocked (expected false): " + illegalEdit);

        System.out.println("\n=== TEST: Cancelling Scheduled Transfer #2 ===");
        Transaction secondTx = johnPending.get(1); // The $75.00 transfer
        boolean cancelSuccess = TransactionDAO.cancelScheduledTransaction(secondTx.getTransactionId());
        System.out.println("Cancel returned: " + cancelSuccess);
        System.out.println("John remaining pending count (expected 1): " +
                           TransactionDAO.getPendingTransactionsByAccountNumber(johnCheckAcc).size());

        // Print full ledger to verify the cancelled row is still preserved with status = 'cancelled'
        System.out.println("\n=== FULL LEDGER AUDIT (JOHN) ===");
        for (Transaction tx : TransactionDAO.getTransactionsByAccountNumber(johnCheckAcc)) {
            System.out.printf("ID: %d | Type: %-12s | Amount: $%-7.2f | Status: %-10s | Scheduled: %s%n",
                tx.getTransactionId(), tx.getTransactionType(), tx.getAmount(), tx.getStatus(), tx.getScheduledFor());
        }
        TransactionDAO.getTransactionsByAccountNumber(johnCheckAcc);
        TransactionDAO.getTransactionsByAccountNumber(bobAcc);

        System.out.println("\n========== EXTERNAL ACCOUNT CHECKS ==========");
        System.out.println("\n=== 1. LINKING: Adding External Account & Testing Duplicate Guard ===");
        boolean linked = ExternalAccountDAO.addExternalAccount(
            johnId, "Chase Bank", "John Doe", "987654321", "021000021", "checking", "My Chase Checking"
        );
        System.out.println("External account linked (expected true): " + linked);

        // Attempt to link the exact same routing + account number again
        boolean duplicateLinked = ExternalAccountDAO.addExternalAccount(
            johnId, "Chase Bank", "John Doe", "987654321", "021000021", "checking", "Duplicate"
        );
        System.out.println("Duplicate link blocked (expected false): " + duplicateLinked);

        // Fetch dynamically to capture the generated external_account_id
        List<ExternalAccount> extAccounts = ExternalAccountDAO.getExternalAccountsByUserId(johnId);
        ExternalAccount chaseAcc = extAccounts.get(0);
        int extId = chaseAcc.getExternalAccountId();
        System.out.println("Fetched External Account -> ID: " + extId + " | " + chaseAcc.getBankName() +
                           " (" + chaseAcc.getNickname() + ") #" + chaseAcc.getAccountNumber());

        System.out.println("\n=== 3. IMMEDIATE TRANSFERS: Outgoing & Incoming ===");
        // Send $200.00 to Chase ($1000.00 -> $800.00)
        TransactionDAO.transferToExternal(johnCheckAcc, extId, 200.00);
        double afterOut = BankAccountDAO.getBankAccountsByUsername("john_doe").get(0).getBalance();
        System.out.println("Balance after sending $200 to external: $" + String.format("%.2f", afterOut));

        // Pull $350.00 from Chase ($800.00 -> $1150.00)
        TransactionDAO.transferFromExternal(extId, johnCheckAcc, 350.00);
        double afterIn = BankAccountDAO.getBankAccountsByUsername("john_doe").get(0).getBalance();
        System.out.println("Balance after pulling $350 from external: $" + String.format("%.2f", afterIn));

        System.out.println("\n=== 4. SCHEDULED EXTERNAL TRANSFER & EDIT/CANCEL GUARDRAIL ===");
        // Schedule an incoming pull of $150.00 from Chase
        TransactionDAO.transferFromExternal(extId, johnCheckAcc, 150.00, "2026-12-15 09:00:00");

        double afterSchedule = BankAccountDAO.getBankAccountsByUsername("john_doe").get(0).getBalance();
        System.out.println("Balance after scheduling (expected unchanged): $" + String.format("%.2f", afterSchedule));

        // Edit the pending external transfer_in (tests the updated EXT- guardrail)
        Transaction pendingExtTx = TransactionDAO.getPendingTransactionsByAccountNumber(johnCheckAcc).get(0);
        boolean editWorked = TransactionDAO.editScheduledTransaction(
            pendingExtTx.getTransactionId(), 225.00, "2026-12-18 10:00:00"
        );
        System.out.println("Edited scheduled external transfer_in (expected true): " + editWorked);

        // Cancel the pending external transfer_in
        boolean cancelWorked = TransactionDAO.cancelScheduledTransaction(pendingExtTx.getTransactionId());
        System.out.println("Cancelled scheduled external transfer_in (expected true): " + cancelWorked);

        System.out.println("\n=== 5. LEDGER AUDIT ===");
        for (Transaction tx : TransactionDAO.getTransactionsByAccountNumber(johnCheckAcc)) {
            System.out.printf("ID: %d | Type: %-12s | Amount: $%-7.2f | Related: %-15s | Status: %-10s%n",
                tx.getTransactionId(), tx.getTransactionType(), tx.getAmount(),
                tx.getRelatedAccount(), tx.getStatus());
        }

        System.out.println("=== TEST: Close/deletion tests ===");
        // Test close        
        if (johnCheckAcc != null) {
            BankAccountDAO.closeBankAccount(johnCheckAcc);
        }

        boolean unlinked = ExternalAccountDAO.deleteExternalAccount(extId);
        System.out.println("External account unlinked (expected true): " + unlinked);

        // Test deleting a single bank account
        BankAccountDAO.deleteBankAccount(johnCheckAcc);

        // Test deleting a user (and cascading to their remaining accounts)
        UserDAO.deleteUser("john_doe");
        UserDAO.deleteUser("bob_recipient");

        // Test finding again
        BankAccountDAO.getBankAccountsByUsername("john_doe");
    }
}
