package database;

public class Transaction {
    private int transactionId;
    private String accountNumber;
    private String transactionType;
    private double amount;
    private String relatedAccount;
    private String status;
    private String scheduledFor;
    private String timestamp;

    // Constructor
    public Transaction(int transactionId, String accountNumber, String transactionType, double amount, String relatedAccount, String status, String scheduledFor, String timestamp) {
        this.transactionId = transactionId;
        this.accountNumber = accountNumber;
        this.transactionType = transactionType;
        this.amount = amount;
        this.relatedAccount = relatedAccount;
        this.status = status;
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
    public String getScheduledFor() { return scheduledFor; }
    public String getTimestamp() { return timestamp; }
}
