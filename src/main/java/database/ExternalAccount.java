package database;

public class ExternalAccount {
    private int externalAccountId;
    private int userId;
    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String routingNumber;
    private String accountType;
    private String nickname;

    public ExternalAccount(int externalAccountId, int userId, String bankName,
                           String accountHolderName, String accountNumber,
                           String routingNumber, String accountType, String nickname) {
        this.externalAccountId = externalAccountId;
        this.userId = userId;
        this.bankName = bankName;
        this.accountHolderName = accountHolderName;
        this.accountNumber = accountNumber;
        this.routingNumber = routingNumber;
        this.accountType = accountType;
        this.nickname = nickname;
    }

    public int getExternalAccountId() { return externalAccountId; }
    public int getUserId() { return userId; }
    public String getBankName() { return bankName; }
    public String getAccountHolderName() { return accountHolderName; }
    public String getAccountNumber() { return accountNumber; }
    public String getRoutingNumber() { return routingNumber; }
    public String getAccountType() { return accountType; }
    public String getNickname() { return nickname; }
}
