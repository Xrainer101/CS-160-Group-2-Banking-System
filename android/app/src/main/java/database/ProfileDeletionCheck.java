package database;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that deleting a user's profile would remove, so the Settings screen can show it
 * before the user confirms. Built by DatabaseManager.getProfileDeletionCheck.
 */
public class ProfileDeletionCheck {
    /** The user's open accounts (all of them are removed). */
    public final List<Account> accounts;
    /** The open accounts that still have money in them. */
    public final List<Account> accountsWithMoney = new ArrayList<>();
    /** Money left across all open accounts. */
    public final double totalBalance;
    /** The user's scheduled or failed bills and transfers (all cancelled). */
    public final int scheduledPayments;
    /** Other customers' scheduled transfers INTO this user's accounts (cancelled). */
    public final int incomingTransfers;

    public ProfileDeletionCheck(List<Account> accounts, int scheduledPayments, int incomingTransfers) {
        this.accounts = accounts;
        this.scheduledPayments = scheduledPayments;
        this.incomingTransfers = incomingTransfers;
        long cents = 0;
        for (Account account : accounts) {
            long balance = Math.round(account.getBalance() * 100);
            if (balance != 0) {
                accountsWithMoney.add(account);
                cents += balance;
            }
        }
        this.totalBalance = cents / 100.0;
    }

    public boolean hasMoneyLeft() {
        return !accountsWithMoney.isEmpty();
    }
}