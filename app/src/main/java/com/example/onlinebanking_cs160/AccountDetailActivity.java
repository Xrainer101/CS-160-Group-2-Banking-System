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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.OperationResult;
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
        findViewById(R.id.buttonCloseAccount).setOnClickListener(v -> confirmClose());
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
    // Close account
    // ------------------------------------------------------------------

    private void confirmClose() {
        if (account == null) return;

        if (Math.round(account.getBalance() * 100) != 0) {
            // Money has to go somewhere first
            new AlertDialog.Builder(this)
                    .setTitle("Move your money first")
                    .setMessage("This account still has " + Ui.money(account.getBalance())
                            + ". Transfer or withdraw it, then you can close the account.")
                    .setPositiveButton("Transfer", (d, w) ->
                            MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_TRANSFER, accountNumber))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Close " + account.getDisplayName() + "?")
                .setMessage("You won't be able to use this account after it's closed. "
                        + "Any bills scheduled from it will be cancelled.")
                .setPositiveButton("Close account", (d, w) -> close())
                .setNegativeButton("Keep it", null)
                .show();
    }

    private void close() {
        executorService.execute(() -> {
            OperationResult result = databaseManager.closeAccount(userId, accountNumber);
            runOnUiThread(() -> {
                Ui.toast(this, result.message);
                if (result.success) finish();
            });
        });
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}
