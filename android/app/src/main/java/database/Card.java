package database;

/** The user's debit card (one row of the cards table). It's linked to one checking account. */
public class Card {
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_FROZEN = "frozen";
    public static final String STATUS_REPLACED = "replaced";
    public static final String STATUS_CANCELLED = "cancelled";

    private final long cardId;
    private final long userId;
    private final String accountNumber;   // the checking account the card uses
    private final String cardNumber;      // 16 digits
    private final String expires;         // "MM/yy"
    private final String status;

    public Card(long cardId, long userId, String accountNumber, String cardNumber, String expires, String status) {
        this.cardId = cardId;
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.cardNumber = cardNumber;
        this.expires = expires;
        this.status = status;
    }

    public long getCardId() { return cardId; }
    public long getUserId() { return userId; }
    public String getAccountNumber() { return accountNumber; }
    public String getCardNumber() { return cardNumber; }
    public String getExpires() { return expires; }
    public String getStatus() { return status; }

    public boolean isFrozen() { return STATUS_FROZEN.equals(status); }

    public String getLastFour() {
        return cardNumber == null || cardNumber.length() < 4 ? "" : cardNumber.substring(cardNumber.length() - 4);
    }

    /** "••••  ••••  ••••  1234" for the card picture. */
    public String getMaskedNumber() {
        return "••••  ••••  ••••  " + getLastFour();
    }

    /** "4123  4567  8901  2345" */
    public String getFormattedNumber() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cardNumber.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append("  ");
            sb.append(cardNumber.charAt(i));
        }
        return sb.toString();
    }
}
