import database.BankAccountDAO;
import database.DatabaseManager;
import database.ExternalAccountDAO;
import database.TransactionDAO;
import database.UserDAO;
import java.sql.SQLException;
import java.util.concurrent.Executors;

class DueTransferIntegration {
    private static final String DUE = "2026-11-25 00:00:00";

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void sql(String statement) throws SQLException {
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute(statement);
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("bank.db.path", args[0]);
        DatabaseManager.initializeDatabase();
        // Recreate the pre-upgrade schema, then initialize it twice.
        sql("ALTER TABLE transactions DROP COLUMN counterpart_transaction_id");
        sql("ALTER TABLE transactions DROP COLUMN external_account_id");
        sql("ALTER TABLE transactions DROP COLUMN failure_reason");
        DatabaseManager.initializeDatabase();
        DatabaseManager.initializeDatabase();

        UserDAO.registerUser("sender", "Test", "Sender", "sender@example.test", "1234567890", "test");
        UserDAO.registerUser("recipient", "Test", "Recipient", "recipient@example.test", "1234567891", "test");
        int sender = UserDAO.getUserIdByUsername("sender");
        int recipient = UserDAO.getUserIdByUsername("recipient");
        String source = BankAccountDAO.createBankAccount(sender, "checking", 100);
        String own = BankAccountDAO.createBankAccount(sender, "savings", 0);
        String other = BankAccountDAO.createBankAccount(recipient, "checking", 0);
        check(ExternalAccountDAO.addExternalAccount(sender, "Test Bank", "Test Sender",
            "11111111", "987654321", "checking", null), "external recipient");
        int external = ExternalAccountDAO.getExternalAccountsByUserId(sender).get(0).getExternalAccountId();

        check(TransactionDAO.transfer(source, own, 10, DUE), "legacy internal setup");
        sql("UPDATE transactions SET counterpart_transaction_id = NULL");
        check(TransactionDAO.transfer(source, own, 10, DUE), "identical internal setup");
        check(TransactionDAO.transfer(source, other, 20, DUE), "other customer setup");
        check(TransactionDAO.transferToExternal(source, external, 30, DUE), "external setup");
        check(TransactionDAO.transfer(source, own, 150, DUE), "insufficient-funds setup");
        check(TransactionDAO.transfer(source, own, 1, "2026-11-26 00:00:00"), "future setup");

        check(TransactionDAO.processDueTransfers(recipient, DUE).completed() == 0, "recipient scope");
        check(TransactionDAO.processDueTransfers(sender, "2026-11-24 23:59:59").completed() == 0, "UTC boundary");

        sql("CREATE TRIGGER block_settlement BEFORE UPDATE OF status ON transactions "
            + "WHEN NEW.status = 'completed' AND OLD.transaction_type = 'transfer_in' "
            + "BEGIN SELECT RAISE(ABORT, 'simulated database failure'); END");
        try {
            TransactionDAO.processDueTransfers(sender, DUE);
            throw new AssertionError("database failure must propagate");
        } catch (SQLException expected) {
            check(BankAccountDAO.getBankAccountByNumber(source).getBalance() == 100, "debit rolled back");
            check(BankAccountDAO.getBankAccountByNumber(own).getBalance() == 0, "credit rolled back");
            check(TransactionDAO.getPendingTransactionsByAccountNumber(source).size() == 6, "status rolled back");
        }
        sql("DROP TRIGGER block_settlement");

        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> TransactionDAO.processDueTransfers(sender, DUE));
            var second = workers.submit(() -> TransactionDAO.processDueTransfers(sender, DUE));
            var left = first.get();
            var right = second.get();
            check(left.completed() + right.completed() == 4, "each due transfer completes once");
            check(left.failed() + right.failed() == 1, "insufficient funds fails once");
        } finally {
            workers.shutdownNow();
        }

        check(BankAccountDAO.getBankAccountByNumber(source).getBalance() == 30, "source balance");
        check(BankAccountDAO.getBankAccountByNumber(own).getBalance() == 20, "own balance");
        check(BankAccountDAO.getBankAccountByNumber(other).getBalance() == 20, "recipient balance");
        var ledger = TransactionDAO.getTransactionsByAccountNumber(source);
        check(ledger.size() == 6, "settlement reuses original ledger rows");
        check(ledger.stream().anyMatch(row -> "failed".equals(row.getStatus()) && row.getFailureReason() != null),
            "failure reason retained");
        check(TransactionDAO.getPendingTransactionsByAccountNumber(source).size() == 1, "future row preserved");

        check(TransactionDAO.transferToExternal(source, external, 5, DUE), "removed recipient setup");
        check(ExternalAccountDAO.deleteExternalAccount(external), "remove recipient");
        check(TransactionDAO.processDueTransfers(sender, DUE).failed() == 1, "removed recipient fails");
        check(BankAccountDAO.getBankAccountByNumber(source).getBalance() == 30, "no debit for unavailable recipient");
        check(TransactionDAO.processDueTransfers(sender, DUE).completed() == 0, "processing can be repeated");

        check(TransactionDAO.transfer(source, other, 5, DUE), "inactive destination setup");
        check(BankAccountDAO.closeBankAccount(other), "close recipient account");
        check(TransactionDAO.processDueTransfers(sender, DUE).failed() == 1, "inactive destination fails");
        check(BankAccountDAO.getBankAccountByNumber(source).getBalance() == 30, "no debit for closed destination");
    }
}
