package database;

import androidx.annotation.Nullable;

/** One bill from the scheduled_payments table (with the paying account's type joined in). */
public class ScheduledPayment {
    public static final String STATUS_SCHEDULED = "scheduled";
    public static final String STATUS_PAID = "paid";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";

    // how frequently u want 2 pay a bill...
    public static final String FREQUENCY_ONCE = "once";
    public static final String FREQUENCY_DAILY = "daily";
    public static final String FREQUENCY_WEEKLY = "weekly";
    public static final String FREQUENCY_MONTHLY = "monthly";
    public static final String FREQUENCY_YEARLY = "yearly";

    private final long paymentId;
    private final String payee;
    private final double amount;
    private final String dueDate;         // "yyyy-MM-dd"; for a repeating bill, the next payment date
    private final String status;
    @Nullable private final String failureReason;
    private final String accountNumber;
    private final String accountType;
    private final String frequency;
    private final int repeatEvery;

    public ScheduledPayment(long paymentId, String payee, double amount, String dueDate, String status,
                            @Nullable String failureReason, String accountNumber, String accountType,
                            String frequency, int repeatEvery) {
        this.paymentId = paymentId;
        this.payee = payee;
        this.amount = amount;
        this.dueDate = dueDate;
        this.status = status;
        this.failureReason = failureReason;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.frequency = frequency;
        this.repeatEvery = Math.max(1, repeatEvery);
    }

    public long getPaymentId() { return paymentId; }
    public String getPayee() { return payee; }
    public double getAmount() { return amount; }
    public String getDueDate() { return dueDate; }
    public String getStatus() { return status; }
    @Nullable public String getFailureReason() { return failureReason; }
    public String getAccountNumber() { return accountNumber; }
    public String getFrequency() { return frequency; }
    public int getRepeatEvery() { return repeatEvery; }

    public boolean isFailed() { return STATUS_FAILED.equals(status); }

    /** True for any bill that repeats (daily, weekly, monthly or yearly). */
    public boolean isRecurring() { return frequency != null && !FREQUENCY_ONCE.equals(frequency); }

    /** "Weekly", "Every 2 weeks", "Monthly", "Every 3 months", "Yearly"... */
    public String getFrequencyText() {
        return describeFrequency(frequency, repeatEvery);
    }

    /** "Checking •••• 2048" */
    public String getAccountDisplayName() {
        return Account.capitalize(accountType) + " " + Account.maskNumber(accountNumber);
    }

    // ------------------------------------------------------------------

    /** ("weekly", 1) -> "Weekly", ("weekly", 2) -> "Every 2 weeks", ("once", 1) -> "One time". */
    public static String describeFrequency(String frequency, int every) {
        String unit;
        String single;
        switch (frequency == null ? FREQUENCY_ONCE : frequency) {
            case FREQUENCY_DAILY: unit = "day"; single = "Daily"; break;
            case FREQUENCY_WEEKLY: unit = "week"; single = "Weekly"; break;
            case FREQUENCY_MONTHLY: unit = "month"; single = "Monthly"; break;
            case FREQUENCY_YEARLY: unit = "year"; single = "Yearly"; break;
            default: return "One time";
        }
        return every <= 1 ? single : "Every " + every + " " + unit + "s";
    }

    /** Biggest "every N" allowed for each kind of repeat (0 = not a repeating frequency). */
    public static int maxRepeatEvery(String frequency) {
        switch (frequency == null ? "" : frequency) {
            case FREQUENCY_DAILY: return 365;
            case FREQUENCY_WEEKLY: return 52;
            case FREQUENCY_MONTHLY: return 24;
            case FREQUENCY_YEARLY: return 10;
            default: return 0;
        }
    }
}