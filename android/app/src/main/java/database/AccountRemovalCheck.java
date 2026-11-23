package database;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything to check before an account is removed. The screen shows these to the user so
 * nothing is lost or left behind by surprise.
 */
public class AccountRemovalCheck {
    /** The account being removed. */
    public final Account account;
    /** Bills and transfers that take money OUT of this account. */
    public final List<ScheduledPayment> outgoing = new ArrayList<>();
    /** Scheduled transfers that send money INTO this account (from the user's other accounts). */
    public final List<ScheduledPayment> incoming = new ArrayList<>();
    /** The user's other open accounts (where scheduled payments could be moved). */
    public final List<Account> otherAccounts = new ArrayList<>();

    AccountRemovalCheck(Account account) {
        this.account = account;
    }

    public boolean hasMoneyLeft() {
        return Math.round(account.getBalance() * 100) != 0;
    }

    public boolean isLastAccount() {
        return otherAccounts.isEmpty();
    }
}