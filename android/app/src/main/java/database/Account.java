package database;

/** One row from the bank_accounts table. */
public class Account {
    public static final String TYPE_CHECKING = "checking";
    public static final String TYPE_SAVINGS = "savings";

    private final long accountId;
    private final String accountNumber;
    private final String accountType;
    private final double balance;

    public Account(long accountId, String accountNumber, String accountType, double balance) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.balance = balance;
    }

    public long getAccountId() { return accountId; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountType() { return accountType; }
    public double getBalance() { return balance; }

    /** "checking" -> "Checking" */
    public String getDisplayType() {
        if (accountType == null || accountType.isEmpty()) return "";
        return Character.toUpperCase(accountType.charAt(0)) + accountType.substring(1);
    }

    /** "1234562048" -> "???????????? 2048" */
    public String getMaskedNumber() {
        if (accountNumber == null || accountNumber.length() < 4) return "????????????";
        return "???????????? " + accountNumber.substring(accountNumber.length() - 4);
    }
}