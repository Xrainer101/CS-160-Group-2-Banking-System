package database;

import java.time.LocalDateTime;

public class BankAccount {
    private int accountId;
    private int userId;
    private String accountNumber;
    private String routingNumber;
    private String accountType;
    private double balance;
    private String status;
    private LocalDateTime opened_at;
    private LocalDateTime closed_at;

    // Constructor
    public BankAccount(int accountId, int userId, String accountNumber, String routingNumber, String accountType, double balance, String status, LocalDateTime opened_at, LocalDateTime closed_at) {
        this.accountId = accountId;
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.routingNumber = routingNumber;
        this.accountType = accountType;
        this.balance = balance;
        this.status = status;
        this.opened_at = opened_at;
        this.closed_at = closed_at;
    }

    // Getters (The UI Toolkit TableView will use these to populate the columns!)
    public int getAccountId() { return accountId; }
    public int getUserId() { return userId; }
    public String getAccountNumber() { return accountNumber; }
    public String getRoutingNumber() { return routingNumber; }
    public String getAccountType() { return accountType; }
    public double getBalance() { return balance; }
    public String getStatus() { return status; }
    public LocalDateTime getOpenedAt() { return opened_at; }
    public LocalDateTime getClosedAt() { return closed_at; }
}
