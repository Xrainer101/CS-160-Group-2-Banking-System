package database;

/** One row from the bank_accounts table. */
public class Account {
    public static final String TYPE_CHECKING = "checking";
    public static final String TYPE_SAVINGS = "savings";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_FROZEN = "frozen";
    public static final String STATUS_CLOSED = "closed";

    private final long accountId;
    private final long userId;
    private final String accountNumber;
    private final String routingNumber;
    private final String accountType;
    private final double balance;
    private final String status;

    public Account(long accountId, long userId, String accountNumber, String routingNumber,
                   String accountType, double balance, String status) {
        this.accountId = accountId;
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.routingNumber = routingNumber;
        this.accountType = accountType;
        this.balance = balance;
        this.status = status;
    }

    public long getAccountId() { return accountId; }
    public long getUserId() { return userId; }
    public String getAccountNumber() { return accountNumber; }
    public String getRoutingNumber() { return routingNumber; }
    public String getAccountType() { return accountType; }
    public double getBalance() { return balance; }
    public String getStatus() { return status; }

    public boolean isActive() { return STATUS_ACTIVE.equals(status); }
    public boolean isFrozen() { return STATUS_FROZEN.equals(status); }
    public boolean isClosed() { return STATUS_CLOSED.equals(status); }

    /** "checking" -> "Checking" */
    public String getDisplayType() {
        return capitalize(accountType);
    }

    /** "1234562048" -> "•••• 2048" */
    public String getMaskedNumber() {
        return maskNumber(accountNumber);
    }

    /** "Checking •••• 2048" */
    public String getDisplayName() {
        return getDisplayType() + " " + getMaskedNumber();
    }

    public static String maskNumber(String number) {
        if (number == null || number.length() < 4) return "••••";
        return "•••• " + number.substring(number.length() - 4);
    }

    static String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
