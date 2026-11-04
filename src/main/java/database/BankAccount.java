package database;

public class BankAccount {
    private int accountId;
    private String accountNumber;
    private String routingNumber;
    private String accountType;
    private double balance;
    private String status;

    // Constructor
    public BankAccount(int accountId, String accountNumber, String routingNumber, String accountType, double balance, String status) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.routingNumber = routingNumber;
        this.accountType = accountType;
        this.balance = balance;
        this.status = status;
    }

    // Getters (The UI Toolkit TableView will use these to populate the columns!)
    public int getAccountId() { return accountId; }
    public String getAccountNumber() { return accountNumber; }
    public String getRoutingNumber() { return routingNumber; }
    public String getAccountType() { return accountType; }
    public double getBalance() { return balance; }
    public String getStatus() { return status; }
}
