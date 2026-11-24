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
        verifyBills(recipient, sender);
    }

    private static void verifyBills(int owner, int otherUser) throws Exception {
        String number = BankAccountDAO.createBankAccount(owner, "checking", 40);
        int account = BankAccountDAO.getBankAccountByNumber(number).getAccountId();
        check(TransactionDAO.scheduleBill(otherUser, account, "Unowned", 1, "2027-01-31",
            "once", "Other", "") == null, "only the account owner can schedule");
        var monthly = TransactionDAO.scheduleBill(owner, account, "Internet", 10, "2027-01-31",
            "monthly", "Internet / Phone", "test memo");
        var once = TransactionDAO.scheduleBill(owner, account, "Utilities", 7, "2027-02-01",
            "once", "Utilities", "");
        check(TransactionDAO.getBillSchedules(otherUser).isEmpty(), "bill list is owner-scoped");
        check(!TransactionDAO.cancelBill(otherUser, monthly.id()), "cancellation is owner-scoped");
        check(TransactionDAO.processDueTransfers(owner, "2027-01-31 07:59:59").completed() == 0,
            "bill due at midnight Pacific");

        sql("CREATE TRIGGER block_repeat BEFORE INSERT ON transactions WHEN NEW.related_account = 'BILL-Internet' "
            + "BEGIN SELECT RAISE(ABORT, 'simulated reschedule failure'); END");
        try {
            TransactionDAO.processDueTransfers(owner, "2027-01-31 08:00:00");
            throw new AssertionError("rescheduling failure must propagate");
        } catch (SQLException expected) {
            check(BankAccountDAO.getBankAccountByNumber(number).getBalance() == 40, "bill debit rolled back");
            check(TransactionDAO.getBillSchedules(owner).get(0).status().equals("pending"), "bill remains pending");
        }
        sql("DROP TRIGGER block_repeat");

        var result = TransactionDAO.processDueTransfers(owner, "2027-03-31 06:59:59");
        check(result.completed() == 3, "January, February and one-time occurrence settled");
        var remaining = TransactionDAO.getBillSchedules(owner).stream()
            .filter(bill -> bill.id() == monthly.id()).findFirst().orElseThrow();
        check(remaining.paymentDate().equals("2027-03-31"), "original monthly day restored");
        check(remaining.memo().equals("test memo"), "recurrence copies metadata");
        check(TransactionDAO.processDueTransfers(owner, "2027-03-31 07:00:00").completed() == 1,
            "summer clock offset used");
        check(!TransactionDAO.cancelBill(owner, once.id()), "completed one-time bill cannot be cancelled");

        var failed = TransactionDAO.processDueTransfers(owner, "2027-05-31 07:00:00");
        check(failed.failed() == 1 && failed.completed() == 0, "first missed bill without funds stops the series");
        check(TransactionDAO.processDueTransfers(owner, "2027-06-30 07:00:00").completed() == 0, "failed series cannot advance");

        var weekly = TransactionDAO.scheduleBill(owner, account, "Subscription", 1, "2027-07-01", "weekly", "Other", "");
        check(TransactionDAO.processDueTransfers(owner, "2027-07-01 07:00:00").completed() == 1,
            "weekly occurrence settles");
        check(TransactionDAO.cancelBill(owner, weekly.id()), "stable series ID cancels the next occurrence");
        check(TransactionDAO.processDueTransfers(owner, "2027-08-01 07:00:00").completed() == 0,
            "cancelled series cannot advance");

        var failedBill = TransactionDAO.getBillSchedules(owner).stream()
            .filter(bill -> bill.id() == monthly.id()).findFirst().orElseThrow();
        int occurrence = failedBill.occurrenceId();
        check(TransactionDAO.retryFailedBill(otherUser, occurrence) == null, "retry is owner-scoped");
        String checkingFailure = "Not enough money in Checking •••• " + number.substring(number.length() - 4) + ".";
        check(checkingFailure.equals(failedBill.failureReason()), "automatic failure names the masked checking account");
        check(checkingFailure.equals(TransactionDAO.retryFailedBill(owner, occurrence).failureReason()),
            "retry rechecks funds and names the masked account");

        String savings = BankAccountDAO.createBankAccount(owner, "savings", 0);
        var savingsBill = TransactionDAO.scheduleBill(owner, BankAccountDAO.getBankAccountByNumber(savings).getAccountId(),
            "Savings bill", 1, "2027-06-30", "once", "Other", "");
        check(TransactionDAO.processDueTransfers(owner, "2027-06-30 07:00:00").failed() == 1,
            "unfunded savings bill fails");
        String savingsFailure = "Not enough money in Savings •••• " + savings.substring(savings.length() - 4) + ".";
        check(savingsFailure.equals(TransactionDAO.retryFailedBill(owner, savingsBill.occurrenceId()).failureReason()),
            "retry names the masked savings account");
        check(TransactionDAO.deposit(number, 30), "fund failed bill");
        double fundedBalance = BankAccountDAO.getBankAccountByNumber(number).getBalance();

        sql("CREATE TRIGGER block_retry BEFORE INSERT ON transactions WHEN NEW.related_account = 'BILL-Internet' "
            + "BEGIN SELECT RAISE(ABORT, 'simulated retry failure'); END");
        try {
            TransactionDAO.retryFailedBill(owner, occurrence);
            throw new AssertionError("retry database failure must propagate");
        } catch (SQLException expected) {
            check(BankAccountDAO.getBankAccountByNumber(number).getBalance() == fundedBalance, "retry debit rolled back");
        }
        sql("DROP TRIGGER block_retry");

        var retryWorkers = Executors.newFixedThreadPool(2);
        try {
            var first = retryWorkers.submit(() -> TransactionDAO.retryFailedBill(owner, occurrence));
            var second = retryWorkers.submit(() -> TransactionDAO.retryFailedBill(owner, occurrence));
            var left = first.get();
            var right = second.get();
            check((left == null) != (right == null), "only one concurrent retry can settle");
            check((left == null ? right : left).failureReason() == null, "retry succeeds after funding");
        } finally {
            retryWorkers.shutdownNow();
        }
        check(BankAccountDAO.getBankAccountByNumber(number).getBalance() == fundedBalance - 10, "debit only one occurrence");
        var next = TransactionDAO.getBillSchedules(owner).stream()
            .filter(bill -> bill.id() == monthly.id()).findFirst().orElseThrow();
        check(next.paymentDate().equals("2027-05-31") && next.status().equals("pending"),
            "retry preserves the original schedule and leaves its successor for normal processing");
        check(TransactionDAO.retryFailedBill(owner, occurrence) == null, "completed occurrence cannot be retried");
    }
}
