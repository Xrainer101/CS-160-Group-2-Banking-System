package database;

/** What happened when the app asked the database to do something (deposit, save profile, ...). */
public class OperationResult {
    public final boolean success;
    /** A message that can be shown to the user as-is (what happened, or what went wrong). */
    public final String message;
    /** For deposits, withdrawals and transfers: the account's balance afterwards. */
    public final double newBalance;

    private OperationResult(boolean success, String message, double newBalance) {
        this.success = success;
        this.message = message;
        this.newBalance = newBalance;
    }

    public static OperationResult ok(String message) {
        return new OperationResult(true, message, 0);
    }

    public static OperationResult ok(String message, double newBalance) {
        return new OperationResult(true, message, newBalance);
    }

    public static OperationResult fail(String message) {
        return new OperationResult(false, message, 0);
    }
}
