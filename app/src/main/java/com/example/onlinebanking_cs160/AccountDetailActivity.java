package com.example.onlinebanking_cs160;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.AccountRemovalCheck;
import database.DatabaseManager;
import database.OperationResult;
import database.ScheduledPayment;
import database.Transaction;

/** Details for one account. Open with AccountDetailActivity.open(context, accountNumber). */
public class AccountDetailActivity extends AppCompatActivity {
    private static final String EXTRA_ACCOUNT_NUMBER = "account_number";

    public static void open(Context context, String accountNumber) {
        Intent intent = new Intent(context, AccountDetailActivity.class);
        intent.putExtra(EXTRA_ACCOUNT_NUMBER, accountNumber);
        context.startActivity(intent);
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private long userId;
    private String accountNumber;
    private Account account;
    private boolean numberShown = false;

    private TextView textAccountNumber;
    private TextView buttonShowNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account_detail);
        Ui.setUpScreen(this, "Account");

        SessionManager session = new SessionManager(this);
        accountNumber = getIntent().getStringExtra(EXTRA_ACCOUNT_NUMBER);
        if (!session.isLoggedIn() || accountNumber == null) {
            Ui.goToLogin(this);
            return;
        }
        userId = session.getUserId();
        databaseManager = DatabaseManager.getInstance(this);

        textAccountNumber = findViewById(R.id.textAccountNumber);
        buttonShowNumber = findViewById(R.id.buttonShowNumber);
        buttonShowNumber.setOnClickListener(v -> {
            numberShown = !numberShown;
            showAccountNumber();
        });

        findViewById(R.id.buttonDeposit).setOnClickListener(v ->
                MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_DEPOSIT, accountNumber));
        findViewById(R.id.buttonWithdraw).setOnClickListener(v ->
                MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_WITHDRAW, accountNumber));
        findViewById(R.id.buttonTransfer).setOnClickListener(v ->
                MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_TRANSFER, accountNumber));
        findViewById(R.id.buttonCloseAccount).setOnClickListener(v -> confirmRemove());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload every time, so the balance is right after coming back from a deposit etc.
        if (databaseManager != null) load();
    }

    private void load() {
        executorService.execute(() -> {
            Account loaded = databaseManager.getAccount(userId, accountNumber);
            List<Transaction> transactions = loaded == null ? null
                    : databaseManager.getTransactionsForAccount(userId, accountNumber, 100);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (loaded == null || loaded.isClosed()) {
                    Ui.toast(this, "This account is no longer open.");
                    finish();
                    return;
                }
                account = loaded;
                show(transactions);
            });
        });
    }

    private void show(List<Transaction> transactions) {
        ((TextView) findViewById(R.id.headerTitle)).setText(account.getDisplayType() + " account");
        ((TextView) findViewById(R.id.textAccountType)).setText(account.getDisplayType());
        ((TextView) findViewById(R.id.textMaskedNumber)).setText(account.getMaskedNumber());
        ((TextView) findViewById(R.id.textBalance)).setText(Ui.money(account.getBalance()));
        ((TextView) findViewById(R.id.textRoutingNumber)).setText(account.getRoutingNumber());
        showAccountNumber();

        TextView textStatus = findViewById(R.id.textStatus);
        if (account.isFrozen()) {
            textStatus.setText("Frozen");
            textStatus.setVisibility(View.VISIBLE);
        } else {
            textStatus.setVisibility(View.GONE);
        }

        LinearLayout container = findViewById(R.id.transactionsContainer);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < transactions.size(); i++) {
            container.addView(Ui.transactionRow(inflater, container, transactions.get(i), i > 0, false));
        }
        findViewById(R.id.textNoTransactions).setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAccountNumber() {
        if (account == null) return;
        textAccountNumber.setText(numberShown ? account.getAccountNumber() : account.getMaskedNumber());
        buttonShowNumber.setText(numberShown ? "Hide" : "Show");
    }

    // ------------------------------------------------------------------
    // Remove account (with checks first)
    // ------------------------------------------------------------------

    /**
     * Checks everything before removing the account:
     *  1. money left           -> must be moved out first (offers Transfer / Withdraw)
     *  2. scheduled payments   -> bills/transfers paid FROM it can move to another account or be cancelled
     *  3. incoming transfers   -> transfers scheduled INTO it will be cancelled
     *  4. debit card           -> moves to another checking account, or is cancelled
     *  5. last open account    -> warned
     */
    private void confirmRemove() {
        if (account == null) return;
        executorService.execute(() -> {
            AccountRemovalCheck check = databaseManager.getRemovalCheck(userId, accountNumber);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (check == null) {
                    Ui.toast(this, "This account can't be removed.");
                    return;
                }
                if (check.hasMoneyLeft()) {
                    askToMoveMoney(check);
                } else {
                    showRemovalSummary(check);
                }
            });
        });
    }

    private void askToMoveMoney(AccountRemovalCheck check) {
        new AlertDialog.Builder(this)
                .setTitle("Move your money first")
                .setMessage("This account still has " + Ui.money(check.account.getBalance())
                        + ". Transfer it to another account or withdraw it, then you can remove the account.")
                .setPositiveButton("Transfer", (d, w) ->
                        MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_TRANSFER, accountNumber))
                .setNeutralButton("Withdraw", (d, w) ->
                        MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_WITHDRAW, accountNumber))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRemovalSummary(AccountRemovalCheck check) {
        StringBuilder message = new StringBuilder("Before removing ").append(check.account.getDisplayName())
                .append(", please check:\n\n✓ No money left in it ($0.00).");

        if (!check.outgoing.isEmpty()) {
            message.append("\n\n• ").append(check.outgoing.size())
                    .append(check.outgoing.size() == 1 ? " scheduled payment comes" : " scheduled payments come")
                    .append(" out of this account:");
            for (ScheduledPayment p : check.outgoing) {
                message.append("\n   – ").append(p.getTitle()).append(", ").append(Ui.money(p.getAmount()))
                        .append(p.isRecurring() ? " (" + p.getFrequencyText().toLowerCase(java.util.Locale.US) + ")" : "")
                        .append(p.isFailed() ? " — failed" : "");
            }
        }
        if (!check.incoming.isEmpty()) {
            message.append("\n\n• ").append(check.incoming.size())
                    .append(check.incoming.size() == 1 ? " scheduled transfer sends" : " scheduled transfers send")
                    .append(" money into this account. ").append(check.incoming.size() == 1 ? "It" : "They")
                    .append(" will be cancelled.");
        }
        if (check.linkedCard != null) {
            message.append("\n\n• Your debit card •••• ").append(check.linkedCard.getLastFour())
                    .append(" uses this account. ");
            message.append(check.cardMovesTo != null
                    ? "It will switch to your " + check.cardMovesTo.getDisplayName() + "."
                    : "You have no other checking account, so the card will be cancelled.");
        }
        if (check.isLastAccount()) {
            message.append("\n\n• This is your last open account.");
        }
        message.append("\n\nPast activity stays in your history.");

        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("Remove " + check.account.getDisplayName() + "?")
                .setMessage(message.toString())
                .setNegativeButton("Keep account", null);

        if (!check.outgoing.isEmpty() && !check.otherAccounts.isEmpty()) {
            // Payments can be moved to another account instead of being lost
            dialog.setPositiveButton("Move payments & remove", (d, w) -> pickAccountForPayments(check));
            dialog.setNeutralButton("Cancel payments & remove", (d, w) -> remove(null));
        } else if (!check.outgoing.isEmpty()) {
            dialog.setPositiveButton("Cancel payments & remove", (d, w) -> remove(null));
        } else {
            dialog.setPositiveButton("Remove account", (d, w) -> remove(null));
        }
        dialog.show();
    }

    private void pickAccountForPayments(AccountRemovalCheck check) {
        List<String> labels = new ArrayList<>();
        for (Account a : check.otherAccounts) labels.add(Ui.accountLabel(a));
        new AlertDialog.Builder(this)
                .setTitle("Move scheduled payments to…")
                .setItems(labels.toArray(new String[0]), (d, which) ->
                        remove(check.otherAccounts.get(which).getAccountNumber()))
                .setNegativeButton("Back", null)
                .show();
    }

    private void remove(String movePaymentsTo) {
        executorService.execute(() -> {
            OperationResult result = databaseManager.closeAccount(userId, accountNumber, movePaymentsTo);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result.success) {
                    Ui.toast(this, result.message);
                    finish();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Account not removed")
                            .setMessage(result.message)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}
