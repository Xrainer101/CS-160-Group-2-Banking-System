package database;

import androidx.annotation.Nullable;

/** One row from the transactions table (with the account's type joined in for display). */
public class Transaction {
    // Same types as the team backend, plus bill_payment for the Pay Bills screen
    public static final String TYPE_DEPOSIT = "deposit";
    public static final String TYPE_WITHDRAWAL = "withdrawal";
    public static final String TYPE_TRANSFER_IN = "transfer_in";
    public static final String TYPE_TRANSFER_OUT = "transfer_out";
    public static final String TYPE_BILL_PAYMENT = "bill_payment";

    private final long transactionId;
    private final String accountNumber;
    private final String accountType;
    private final String type;
    private final double amount;          // always positive; the type says which way it went
    @Nullable private final String relatedAccount;
    private final String description;
    private final long timestamp;         // millis since 1970

    public Transaction(long transactionId, String accountNumber, String accountType, String type,
                       double amount, @Nullable String relatedAccount, String description, long timestamp) {
        this.transactionId = transactionId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.type = type;
        this.amount = amount;
        this.relatedAccount = relatedAccount;
        this.description = description;
        this.timestamp = timestamp;
    }

    public long getTransactionId() { return transactionId; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountType() { return accountType; }
    public String getType() { return type; }
    public double getAmount() { return amount; }
    @Nullable public String getRelatedAccount() { return relatedAccount; }
    public long getTimestamp() { return timestamp; }

    public String getDescription() {
        return description == null || description.isEmpty() ? getCategory() : description;
    }

    /** True for money coming into the account. */
    public boolean isMoneyIn() {
        return TYPE_DEPOSIT.equals(type) || TYPE_TRANSFER_IN.equals(type);
    }

    /** Positive for money in, negative for money out. */
    public double getSignedAmount() {
        return isMoneyIn() ? amount : -amount;
    }

    /** Short label shown under the description. */
    public String getCategory() {
        switch (type) {
            case TYPE_DEPOSIT: return "Deposit";
            case TYPE_WITHDRAWAL: return "Withdrawal";
            case TYPE_TRANSFER_IN:
            case TYPE_TRANSFER_OUT: return "Transfer";
            case TYPE_BILL_PAYMENT: return "Bill payment";
            default: return "Transaction";
        }
    }

    /** "Checking •••• 2048" */
    public String getAccountDisplayName() {
        return Account.capitalize(accountType) + " " + Account.maskNumber(accountNumber);
    }
}
