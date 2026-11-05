package com.example.onlinebanking_cs160;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.ScheduledPayment;
import database.Transaction;
import database.User;

// Home screen shown after a successful login (based on the team's homepage design)
public class HomeActivity extends AppCompatActivity {
    private static final int MENU_CARDS = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int MENU_LOG_OUT = 3;

    private static final int RECENT_TRANSACTIONS = 5;
    private static final int UPCOMING_PAYMENTS = 3;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private SessionManager session;
    private DatabaseManager databaseManager;
    private User currentUser;

    private TextView textAvatar;
    private TextView textWelcome;
    private TextView textLastLogin;
    private TextView textTotalBalance;
    private LinearLayout accountsContainer;
    private TextView textNoAccounts;

    /** Holds everything the home screen shows, loaded together on a background thread. */
    private static class HomeData {
        User user;
        List<Account> accounts;
        List<Transaction> transactions;
        List<ScheduledPayment> payments;
        DatabaseManager.PaymentRunResult billRun;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            Ui.goToLogin(this);
            return;
        }
        databaseManager = DatabaseManager.getInstance(this);

        // Top inset goes above the white top bar; bottom inset goes inside the dark footer
        View footer = findViewById(R.id.footer);
        int footerPaddingBottom = footer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            footer.setPadding(footer.getPaddingLeft(), footer.getPaddingTop(),
                    footer.getPaddingRight(), footerPaddingBottom + bars.bottom);
            return insets;
        });

        textAvatar = findViewById(R.id.textAvatar);
        textWelcome = findViewById(R.id.textWelcome);
        textLastLogin = findViewById(R.id.textLastLogin);
        textTotalBalance = findViewById(R.id.textTotalBalance);
        accountsContainer = findViewById(R.id.accountsContainer);
        textNoAccounts = findViewById(R.id.textNoAccounts);

        textLastLogin.setText(formatLastLogin(session.getPreviousLogin()));

        textAvatar.setOnClickListener(this::showAccountMenu);
        findViewById(R.id.buttonNotifications).setOnClickListener(v ->
                Ui.toast(this, "You have no new notifications"));
        findViewById(R.id.buttonOpenAccount).setOnClickListener(v -> showOpenAccountDialog());

        setUpQuickActions();

        findViewById(R.id.buttonViewAllTransactions).setOnClickListener(v ->
                startActivity(new Intent(this, TransactionsActivity.class)));
        findViewById(R.id.buttonManagePayments).setOnClickListener(v ->
                startActivity(new Intent(this, PayBillsActivity.class)));
        findViewById(R.id.buttonSchedulePayment).setOnClickListener(v ->
                startActivity(new Intent(this, PayBillsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload every time the screen comes back, so balances and activity stay up to date
        if (session.isLoggedIn() && databaseManager != null) loadHome();
    }

    // ------------------------------------------------------------------
    // Loading data
    // ------------------------------------------------------------------

    private void loadHome() {
        long userId = session.getUserId();
        executorService.execute(() -> {
            HomeData data = new HomeData();
            data.user = databaseManager.getUserById(userId);
            if (data.user != null) {
                // Pay any scheduled bills whose date has arrived, before showing balances
                data.billRun = databaseManager.processDuePayments(userId);
                data.accounts = databaseManager.getAccountsForUser(userId);
                data.transactions = databaseManager.getTransactionsForUser(userId, RECENT_TRANSACTIONS);
                data.payments = databaseManager.getUpcomingPayments(userId);
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (data.user == null) {
                    // The saved session points to a user that no longer exists (e.g. the database was reset)
                    session.logout();
                    Ui.goToLogin(this);
                    return;
                }
                currentUser = data.user;
                textWelcome.setText("Welcome back, " + data.user.getFirstName() + ".");
                textAvatar.setText(initials(data.user));
                showAccounts(data.accounts);
                showTransactions(data.transactions);
                showPayments(data.payments);
                announceBillRun(data.billRun);
            });
        });
    }

    private void showAccounts(List<Account> accounts) {
        accountsContainer.removeAllViews();
        textNoAccounts.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);

        double total = 0;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Account account : accounts) {
            total += account.getBalance();
            View card = inflater.inflate(R.layout.item_account, accountsContainer, false);

            String type = account.getDisplayType() + (account.isFrozen() ? "  ·  Frozen" : "");
            ((TextView) card.findViewById(R.id.textAccountType)).setText(type);
            ((TextView) card.findViewById(R.id.textAccountNumber)).setText(account.getMaskedNumber());

            String balance = Ui.money(account.getBalance());
            ((TextView) card.findViewById(R.id.textBalance)).setText(balance);
            // No pending holds, so the available balance equals the current balance
            ((TextView) card.findViewById(R.id.textAvailable)).setText(balance);

            String number = account.getAccountNumber();
            card.findViewById(R.id.buttonViewAccount).setOnClickListener(v -> AccountDetailActivity.open(this, number));
            card.findViewById(R.id.buttonAccountMenu).setOnClickListener(v -> showAccountCardMenu(v, number));
            card.setOnClickListener(v -> AccountDetailActivity.open(this, number));

            accountsContainer.addView(card);
        }

        textTotalBalance.setText(accounts.isEmpty() ? ""
                : "Total balance: " + Ui.money(total) + " across " + accounts.size()
                + (accounts.size() == 1 ? " account" : " accounts"));
        textTotalBalance.setVisibility(accounts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showTransactions(List<Transaction> transactions) {
        LinearLayout container = findViewById(R.id.transactionsContainer);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < transactions.size(); i++) {
            container.addView(Ui.transactionRow(inflater, container, transactions.get(i), i > 0, true));
        }
        findViewById(R.id.textNoTransactions).setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPayments(List<ScheduledPayment> payments) {
        LinearLayout container = findViewById(R.id.paymentsContainer);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int count = Math.min(payments.size(), UPCOMING_PAYMENTS);
        for (int i = 0; i < count; i++) {
            View row = Ui.paymentRow(inflater, container, payments.get(i), i > 0);
            row.setOnClickListener(v -> startActivity(new Intent(this, PayBillsActivity.class)));
            container.addView(row);
        }
        findViewById(R.id.textNoPayments).setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** If any bills were paid (or failed) just now, say so, if the user wants transaction alerts. */
    private void announceBillRun(DatabaseManager.PaymentRunResult run) {
        if (run == null || !Ui.transactionAlertsOn(this)) return;
        if (run.failed > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("A bill payment failed")
                    .setMessage(run.failed + (run.failed == 1 ? " scheduled payment" : " scheduled payments")
                            + " couldn't be made. Open Pay Bills to see why and try again.")
                    .setPositiveButton("Pay Bills", (d, w) -> startActivity(new Intent(this, PayBillsActivity.class)))
                    .setNegativeButton("Later", null)
                    .show();
        } else if (run.paid > 0) {
            Ui.toast(this, run.paid + (run.paid == 1 ? " scheduled bill was paid." : " scheduled bills were paid."));
        }
    }

    // ------------------------------------------------------------------
    // Account cards
    // ------------------------------------------------------------------

    private void showAccountCardMenu(View anchor, String accountNumber) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Deposit");
        menu.getMenu().add(0, 2, 1, "Withdraw");
        menu.getMenu().add(0, 3, 2, "Transfer");
        menu.getMenu().add(0, 4, 3, "Account details");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_DEPOSIT, accountNumber); return true;
                case 2: MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_WITHDRAW, accountNumber); return true;
                case 3: MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_TRANSFER, accountNumber); return true;
                case 4: AccountDetailActivity.open(this, accountNumber); return true;
                default: return false;
            }
        });
        menu.show();
    }

    private void showOpenAccountDialog() {
        String[] labels = {"Checking", "Savings"};
        String[] types = {Account.TYPE_CHECKING, Account.TYPE_SAVINGS};

        new AlertDialog.Builder(this)
                .setTitle("Open a new account")
                .setItems(labels, (dialog, which) -> openAccount(types[which], labels[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAccount(String type, String label) {
        long userId = session.getUserId();
        executorService.execute(() -> {
            Account account = databaseManager.openAccount(userId, type);
            runOnUiThread(() -> {
                if (account == null) {
                    Ui.toast(this, "Couldn't open the account. Please try again.");
                    return;
                }
                Ui.toast(this, label + " account " + account.getMaskedNumber() + " opened");
                loadHome();
            });
        });
    }

    // ------------------------------------------------------------------
    // Quick actions (two per row)
    // ------------------------------------------------------------------

    private void setUpQuickActions() {
        // icon, title, description, what happens on tap
        Object[][] actions = {
                {R.drawable.ic_arrow_down, "Deposit", "Add money to an account",
                        (Runnable) () -> MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_DEPOSIT, null)},
                {R.drawable.ic_arrow_up, "Withdraw", "Take money out",
                        (Runnable) () -> MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_WITHDRAW, null)},
                {R.drawable.ic_transfer, "Transfer", "Between accounts or people",
                        (Runnable) () -> MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_TRANSFER, null)},
                {R.drawable.ic_pay_bills, "Pay Bills", "Schedule a payment",
                        (Runnable) () -> startActivity(new Intent(this, PayBillsActivity.class))},
                {R.drawable.ic_card, "My Cards", "Freeze or replace",
                        (Runnable) () -> startActivity(new Intent(this, CardsActivity.class))},
                {R.drawable.ic_list, "Activity", "Search your history",
                        (Runnable) () -> startActivity(new Intent(this, TransactionsActivity.class))},
        };

        LinearLayout container = findViewById(R.id.quickActionsContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        int gap = Math.round(12 * getResources().getDisplayMetrics().density);

        LinearLayout row = null;
        for (int i = 0; i < actions.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = gap;
                container.addView(row, rowParams);
            }
            Object[] action = actions[i];
            View tile = inflater.inflate(R.layout.item_quick_action, row, false);
            ((ImageView) tile.findViewById(R.id.imageActionIcon)).setImageResource((Integer) action[0]);
            ((TextView) tile.findViewById(R.id.textActionTitle)).setText((String) action[1]);
            ((TextView) tile.findViewById(R.id.textActionDescription)).setText((String) action[2]);
            Runnable onTap = (Runnable) action[3];
            tile.setOnClickListener(v -> onTap.run());

            if (i % 2 == 0) {
                // left tile: space between it and the right tile
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tile.getLayoutParams();
                params.setMarginEnd(gap);
                tile.setLayoutParams(params);
            }
            row.addView(tile);
        }
    }

    // ------------------------------------------------------------------
    // Avatar menu
    // ------------------------------------------------------------------

    private void showAccountMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (currentUser != null) {
            String role = currentUser.getRole() == null ? "" : currentUser.getRole();
            if (!role.isEmpty()) role = Character.toUpperCase(role.charAt(0)) + role.substring(1);
            menu.getMenu().add(0, 0, 0, currentUser.getFullName() + " · " + role).setEnabled(false);
        }
        menu.getMenu().add(0, MENU_CARDS, 1, "My Cards");
        menu.getMenu().add(0, MENU_SETTINGS, 2, "Settings");
        menu.getMenu().add(0, MENU_LOG_OUT, 3, "Log out");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_CARDS:
                    startActivity(new Intent(this, CardsActivity.class));
                    return true;
                case MENU_SETTINGS:
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                case MENU_LOG_OUT:
                    session.logout();
                    Ui.goToLogin(this);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** "Jane", "Doe" -> "JD" */
    private static String initials(User user) {
        StringBuilder sb = new StringBuilder();
        if (user.getFirstName() != null && !user.getFirstName().isEmpty()) sb.append(user.getFirstName().charAt(0));
        if (user.getLastName() != null && !user.getLastName().isEmpty()) sb.append(user.getLastName().charAt(0));
        return sb.toString().toUpperCase(Locale.US);
    }

    /** "Today, 4:18 PM", "Yesterday, 9:02 AM", "Oct 12, 4:18 PM", or a first-login message. */
    private String formatLastLogin(long millis) {
        if (millis <= 0) return "This is your first login";

        Date date = new Date(millis);
        String time = DateFormat.getTimeFormat(this).format(date);

        if (DateUtils.isToday(millis)) return "Today, " + time;
        if (DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS)) return "Yesterday, " + time;

        Calendar then = Calendar.getInstance();
        then.setTime(date);
        boolean sameYear = then.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR);
        String day = new SimpleDateFormat(sameYear ? "MMM d" : "MMM d, yyyy", Locale.US).format(date);
        return day + ", " + time;
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}