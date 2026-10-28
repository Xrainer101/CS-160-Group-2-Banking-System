package com.example.onlinebanking_cs160;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.User;

// Home screen shown after a successful login (based on the team's homepage design)
public class HomeActivity extends AppCompatActivity {
    private static final int MENU_LOG_OUT = 1;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

    private SessionManager session;
    private DatabaseManager databaseManager;
    private User currentUser;

    private TextView textAvatar;
    private TextView textWelcome;
    private TextView textLastLogin;
    private LinearLayout accountsContainer;
    private TextView textNoAccounts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goToLogin();
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
        accountsContainer = findViewById(R.id.accountsContainer);
        textNoAccounts = findViewById(R.id.textNoAccounts);

        textLastLogin.setText(formatLastLogin(session.getPreviousLogin()));

        textAvatar.setOnClickListener(this::showAccountMenu);
        findViewById(R.id.buttonNotifications).setOnClickListener(v ->
                toast("You have no new notifications"));

        Button buttonOpenAccount = findViewById(R.id.buttonOpenAccount);
        buttonOpenAccount.setOnClickListener(v -> showOpenAccountDialog());

        setUpQuickActions();

        // Not built yet: these need transactions / bill-pay tables in the database
        findViewById(R.id.buttonViewAllTransactions).setOnClickListener(v -> toast("Transaction history is coming soon"));
        findViewById(R.id.buttonManagePayments).setOnClickListener(v -> toast("Bill Pay is coming soon"));
        findViewById(R.id.buttonSchedulePayment).setOnClickListener(v -> toast("Bill Pay is coming soon"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload every time the screen comes back, so balances stay up to date
        if (session.isLoggedIn()) loadUserAndAccounts();
    }

    // ------------------------------------------------------------------
    // Loading data
    // ------------------------------------------------------------------

    private void loadUserAndAccounts() {
        long userId = session.getUserId();
        executorService.execute(() -> {
            User user = databaseManager.getUserById(userId);
            List<Account> accounts = user == null ? null : databaseManager.getAccountsForUser(userId);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (user == null) {
                    runOnUiThread(() -> {
                        textWelcome.setText("Welcome back.");
                        textAvatar.setText("U");
                        showAccounts(java.util.Collections.emptyList());
                    });
                    return;
                }
                currentUser = user;
                textWelcome.setText("Welcome back, " + user.getFirstName() + ".");
                textAvatar.setText(initials(user));
                showAccounts(accounts);
            });
        });
    }

    private void showAccounts(List<Account> accounts) {
        accountsContainer.removeAllViews();
        textNoAccounts.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Account account : accounts) {
            View card = inflater.inflate(R.layout.item_account, accountsContainer, false);

            ((TextView) card.findViewById(R.id.textAccountType)).setText(account.getDisplayType());
            ((TextView) card.findViewById(R.id.textAccountNumber)).setText(account.getMaskedNumber());

            String balance = currency.format(account.getBalance());
            ((TextView) card.findViewById(R.id.textBalance)).setText(balance);
            // No pending holds yet, so the available balance equals the current balance
            ((TextView) card.findViewById(R.id.textAvailable)).setText(balance);

            card.findViewById(R.id.buttonViewAccount).setOnClickListener(v ->
                    toast("Account details are coming soon"));
            card.findViewById(R.id.buttonAccountMenu).setOnClickListener(v ->
                    toast(account.getDisplayType() + " account " + account.getMaskedNumber()));

            accountsContainer.addView(card);
        }
    }

    // ------------------------------------------------------------------
    // Open New Account
    // ------------------------------------------------------------------

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
                    toast("Couldn't open the account. Please try again.");
                    return;
                }
                toast(label + " account " + account.getMaskedNumber() + " opened");
                loadUserAndAccounts();
            });
        });
    }

    // ------------------------------------------------------------------
    // Quick actions
    // ------------------------------------------------------------------

    private void setUpQuickActions() {
        // icon, title, description: same four actions as the homepage design
        Object[][] actions = {
                {R.drawable.ic_transfer, "Transfer Money", "Move money between accounts"},
                {R.drawable.ic_pay_bills, "Pay Bills", "Manage automatic payments"},
                {R.drawable.ic_deposit_check, "Deposit Check", "Submit a mobile check deposit"},
                {R.drawable.ic_find_atm, "Find ATM", "Locate nearby ATMs"},
        };

        LinearLayout container = findViewById(R.id.quickActionsContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Object[] action : actions) {
            View card = inflater.inflate(R.layout.item_quick_action, container, false);
            String title = (String) action[1];

            ((ImageView) card.findViewById(R.id.imageActionIcon)).setImageResource((Integer) action[0]);
            ((TextView) card.findViewById(R.id.textActionTitle)).setText(title);
            ((TextView) card.findViewById(R.id.textActionDescription)).setText((String) action[2]);

            // Replace these toasts with startActivity(...) as each feature screen is built
            card.setOnClickListener(v -> toast(title + " is coming soon"));
            container.addView(card);
        }
    }

    // ------------------------------------------------------------------
    // Avatar menu / log out
    // ------------------------------------------------------------------

    private void showAccountMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (currentUser != null) {
            String role = currentUser.getRole() == null ? "" : currentUser.getRole();
            if (!role.isEmpty()) role = Character.toUpperCase(role.charAt(0)) + role.substring(1);
            menu.getMenu().add(currentUser.getFullName() + " ?? " + role).setEnabled(false);
        }
        menu.getMenu().add(0, MENU_LOG_OUT, 1, "Log out");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_LOG_OUT) {
                session.logout();
                goToLogin();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}