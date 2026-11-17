package database;

public class CreditCard {
    private int cardId;
    private int userId;
    private String cardNickname;
    private String cardholderName;
    private String cardNumber;
    private String expirationDate;
    private String cvv;
    private double creditLimit;
    private double currentBalance;
    private String status;

    public CreditCard(int cardId, int userId, String cardNickname, String cardholderName,
                      String cardNumber, String expirationDate, String cvv,
                      double creditLimit, double currentBalance, String status) {
        this.cardId = cardId;
        this.userId = userId;
        this.cardNickname = cardNickname;
        this.cardholderName = cardholderName;
        this.cardNumber = cardNumber;
        this.expirationDate = expirationDate;
        this.cvv = cvv;
        this.creditLimit = creditLimit;
        this.currentBalance = currentBalance;
        this.status = status;
    }

    // Getters
    public int getCardId() { return cardId; }
    public int getUserId() { return userId; }
    public String getCardNickname() { return cardNickname; }
    public String getCardholderName() { return cardholderName; }
    public String getCardNumber() { return cardNumber; }
    public String getExpirationDate() { return expirationDate; }
    public String getCvv() { return cvv; }
    public double getCreditLimit() { return creditLimit; }
    public double getCurrentBalance() { return currentBalance; }
    public String getStatus() { return status; }

    // Setters
    public void setCardNickname(String cardNickname) { this.cardNickname = cardNickname; }
    public void setCreditLimit(double creditLimit) { this.creditLimit = creditLimit; }
    public void setCurrentBalance(double currentBalance) { this.currentBalance = currentBalance; }
    public void setStatus(String status) { this.status = status; }

    // UI Helper: Returns masked format (e.g., "**** **** **** 4321")
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        String lastFour = cardNumber.substring(cardNumber.length() - 4);
        return "**** **** **** " + lastFour;
    }

    // UI Helper: Remaining available credit
    public double getAvailableCredit() {
        return creditLimit - currentBalance;
    }
}
