package database;

public class Transaction {
    private int transactionId;
    private String accountNumber;
    private String transactionType;
    private double amount;
    private String relatedAccount;
    private String status;
    private int recurrenceInterval;
    private String recurrenceUnit;
    private String scheduledFor;
    private String timestamp;
    private final String failureReason;

    // Constructor
    public Transaction(int transactionId, String accountNumber, String transactionType, double amount, String relatedAccount, String status, int recurrenceInterval,
                       String recurrenceUnit, String scheduledFor, String timestamp) {
        this(transactionId, accountNumber, transactionType, amount, relatedAccount, status,
             recurrenceInterval, recurrenceUnit, scheduledFor, timestamp, null);
    }

    public Transaction(int transactionId, String accountNumber, String transactionType, double amount,
                       String relatedAccount, String status, int recurrenceInterval,
                       String recurrenceUnit, String scheduledFor, String timestamp, String failureReason) {
        this.failureReason = failureReason;
        this.transactionId = transactionId;
        this.accountNumber = accountNumber;
        this.transactionType = transactionType;
        this.amount = amount;
        this.relatedAccount = relatedAccount;
        this.status = status;
        this.recurrenceInterval = recurrenceInterval;
        this.recurrenceUnit = recurrenceUnit;
        this.scheduledFor = scheduledFor;
        this.timestamp = timestamp;
    }

    // Getters for the UI Toolkit TableView columns
    public int getTransactionId() { return transactionId; }
    public String getAccountNumber() { return accountNumber; }
    public String getTransactionType() { return transactionType; }
    public double getAmount() { return amount; }
    public String getRelatedAccount() { return relatedAccount; }
    public String getStatus() { return status; }
    public int getRecurrenceInterval() { return recurrenceInterval; }
    public String getRecurrenceUnit() { return recurrenceUnit; }
    public String getScheduledFor() { return scheduledFor; }
    public String getTimestamp() { return timestamp; }
    public String getFailureReason() { return failureReason; }
}
