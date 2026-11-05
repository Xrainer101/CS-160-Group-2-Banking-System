package com.example.onlinebanking_cs160;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.OperationResult;

/**
 * One screen for all three ways of moving money: deposit, withdraw and transfer.
 * Open it with MoveMoneyActivity.open(context, mode, accountNumberOrNull).
 */
public class MoveMoneyActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_ACCOUNT_NUMBER = "account_number";

    public static final String MODE_DEPOSIT = "deposit";
    public static final String MODE_WITHDRAW = "withdraw";
    public static final String MODE_TRANSFER = "transfer";

    /** Opens this screen. accountNumber (optional) is pre-selected as the account to use. */
    public static void open(Context context, String mode, String accountNumber) {
        Intent intent = new Intent(context, MoveMoneyActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        if (accountNumber != null) intent.putExtra(EXTRA_ACCOUNT_NUMBER, accountNumber);
        context.startActivity(intent);
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private long userId;
    private String mode;

    private List<Account> accounts = new ArrayList<>();
    private List<Account> toAccounts = new ArrayList<>();

    private Spinner spinnerFrom;
    private Spinner spinnerTo;
    private TextView textAvailable;
    private RadioGroup radioSendTo;
    private RadioButton radioMyAccount;
    private View frameToAccount;
    private TextView textNoOtherAccounts;
    private EditText editRecipient;
    private TextView textRecipientName;
    private EditText editAmount;
    private EditText editMemo;
    private TextView textError;
    private Button buttonSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_move_money);

        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            Ui.goToLogin(this);
            return;
        }
        userId = session.getUserId();
        databaseManager = DatabaseManager.getInstance(this);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_TRANSFER;

        spinnerFrom = findViewById(R.id.spinnerFrom);
        spinnerTo = findViewById(R.id.spinnerTo);
        textAvailable = findViewById(R.id.textAvailable);
        radioSendTo = findViewById(R.id.radioSendTo);
        radioMyAccount = findViewById(R.id.radioMyAccount);
        frameToAccount = findViewById(R.id.frameToAccount);
        textNoOtherAccounts = findViewById(R.id.textNoOtherAccounts);
        editRecipient = findViewById(R.id.editRecipient);
        textRecipientName = findViewById(R.id.textRecipientName);
        editAmount = findViewById(R.id.editAmount);
        editMemo = findViewById(R.id.editMemo);
        textError = findViewById(R.id.textError);
        buttonSubmit = findViewById(R.id.buttonSubmit);

        TextView textTitle = findViewById(R.id.textTitle);
        TextView textSubtitle = findViewById(R.id.textSubtitle);
        TextView textFromLabel = findViewById(R.id.textFromLabel);

        switch (mode) {
            case MODE_DEPOSIT:
                Ui.setUpScreen(this, "Deposit");
                textTitle.setText("Deposit money");
                textSubtitle.setText("Add money to one of your accounts. It's available right away.");
                textFromLabel.setText("Deposit into");
                buttonSubmit.setText("Deposit");
                break;
            case MODE_WITHDRAW:
                Ui.setUpScreen(this, "Withdraw");
                textTitle.setText("Withdraw money");
                textSubtitle.setText("Take money out of one of your accounts.");
                textFromLabel.setText("Withdraw from");
                buttonSubmit.setText("Withdraw");
                break;
            default:
                Ui.setUpScreen(this, "Transfer");
                textTitle.setText("Transfer money");
                textSubtitle.setText("Move money between your accounts or send it to another Team 2 Bank customer.");
                textFromLabel.setText("From account");
                buttonSubmit.setText("Transfer");
                findViewById(R.id.groupTransferTo).setVisibility(View.VISIBLE);
                break;
        }

        spinnerFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showSelectedBalance();
                if (MODE_TRANSFER.equals(mode)) fillToAccounts();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        radioSendTo.setOnCheckedChangeListener((group, checkedId) -> updateSendToViews());

        editRecipient.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                lookUpRecipient(s.toString());
            }
        });

        buttonSubmit.setOnClickListener(v -> onSubmit());

        loadAccounts(getIntent().getStringExtra(EXTRA_ACCOUNT_NUMBER));
    }

    // ------------------------------------------------------------------
    // Account pickers
    // ------------------------------------------------------------------

    private void loadAccounts(String preselectNumber) {
        executorService.execute(() -> {
            List<Account> loaded = databaseManager.getAccountsForUser(userId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                accounts = loaded;
                if (accounts.isEmpty()) {
                    showError("You don't have any open accounts. Open one from the home screen first.");
                    buttonSubmit.setEnabled(false);
                    return;
                }
                spinnerFrom.setAdapter(adapterFor(accounts));
                for (int i = 0; i < accounts.size(); i++) {
                    if (accounts.get(i).getAccountNumber().equals(preselectNumber)) spinnerFrom.setSelection(i);
                }
                showSelectedBalance();
                if (MODE_TRANSFER.equals(mode)) fillToAccounts();
            });
        });
    }

    private ArrayAdapter<String> adapterFor(List<Account> list) {
        List<String> labels = new ArrayList<>();
        for (Account a : list) labels.add(Ui.accountLabel(a));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner, labels);
        adapter.setDropDownViewResource(R.layout.item_spinner);
        return adapter;
    }

    private Account selectedFrom() {
        int i = spinnerFrom.getSelectedItemPosition();
        return i >= 0 && i < accounts.size() ? accounts.get(i) : null;
    }

    private void showSelectedBalance() {
        Account from = selectedFrom();
        if (from == null) return;
        String text = (MODE_DEPOSIT.equals(mode) ? "Current balance: " : "Available: ") + Ui.money(from.getBalance());
        if (from.isFrozen()) text += "  ·  This account is frozen";
        textAvailable.setText(text);
    }

    /** "To" choices for a transfer: all the user's accounts except the "from" one. */
    private void fillToAccounts() {
        Account from = selectedFrom();
        toAccounts = new ArrayList<>();
        for (Account a : accounts) {
            if (from == null || !a.getAccountNumber().equals(from.getAccountNumber())) toAccounts.add(a);
        }
        spinnerTo.setAdapter(adapterFor(toAccounts));
        if (toAccounts.isEmpty()) {
            // Only one account: sending to another customer is the only option
            radioMyAccount.setEnabled(false);
            radioSendTo.check(R.id.radioSomeoneElse);
        } else {
            radioMyAccount.setEnabled(true);
        }
        updateSendToViews();
    }

    private boolean sendingToSomeoneElse() {
        return radioSendTo.getCheckedRadioButtonId() == R.id.radioSomeoneElse;
    }

    private void updateSendToViews() {
        boolean other = sendingToSomeoneElse();
        frameToAccount.setVisibility(!other && !toAccounts.isEmpty() ? View.VISIBLE : View.GONE);
        textNoOtherAccounts.setVisibility(!other && toAccounts.isEmpty() ? View.VISIBLE : View.GONE);
        editRecipient.setVisibility(other ? View.VISIBLE : View.GONE);
        textRecipientName.setVisibility(other && textRecipientName.getText().length() > 0 ? View.VISIBLE : View.GONE);
    }

    /** When 10 digits are typed, show whose account it is so the user can double-check. */
    private void lookUpRecipient(String number) {
        textRecipientName.setText("");
        textRecipientName.setVisibility(View.GONE);
        if (number.length() != 10) return;

        executorService.execute(() -> {
            String name = databaseManager.findRecipientName(number);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!number.equals(editRecipient.getText().toString())) return; // user kept typing
                if (name == null) {
                    textRecipientName.setText("No open account has this number.");
                    textRecipientName.setTextColor(ContextCompat.getColor(this, R.color.error_red));
                } else {
                    textRecipientName.setText("Account holder: " + name);
                    textRecipientName.setTextColor(ContextCompat.getColor(this, R.color.income_green));
                }
                textRecipientName.setVisibility(View.VISIBLE);
            });
        });
    }

    // ------------------------------------------------------------------
    // Submitting
    // ------------------------------------------------------------------

    private void onSubmit() {
        hideError();
        Account from = selectedFrom();
        if (from == null) {
            showError("Choose an account.");
            return;
        }

        double amount = Ui.parseAmount(editAmount.getText().toString());
        if (Double.isNaN(amount)) {
            editAmount.setError("Enter an amount like 25 or 25.50");
            editAmount.requestFocus();
            return;
        }
        String amountProblem = DatabaseManager.checkAmount(amount);
        if (amountProblem != null) {
            editAmount.setError(amountProblem);
            editAmount.requestFocus();
            return;
        }
        if (!MODE_DEPOSIT.equals(mode) && Math.round(amount * 100) > Math.round(from.getBalance() * 100)) {
            editAmount.setError("That's more than the " + Ui.money(from.getBalance()) + " available");
            editAmount.requestFocus();
            return;
        }

        String memo = editMemo.getText().toString().trim();
        String money = Ui.money(amount);
        String question;
        String toNumber = null;

        switch (mode) {
            case MODE_DEPOSIT:
                question = "Deposit " + money + " into your " + from.getDisplayName() + "?";
                break;
            case MODE_WITHDRAW:
                question = "Withdraw " + money + " from your " + from.getDisplayName() + "?";
                break;
            default:
                if (sendingToSomeoneElse()) {
                    toNumber = editRecipient.getText().toString().trim();
                    if (!toNumber.matches("\\d{10}")) {
                        editRecipient.setError("Enter their 10-digit account number");
                        editRecipient.requestFocus();
                        return;
                    }
                    String name = textRecipientName.getText().toString().replace("Account holder: ", "");
                    question = "Send " + money + " from your " + from.getDisplayName() + " to "
                            + (name.isEmpty() || name.startsWith("No open") ? "account " : name + ", account ")
                            + Account.maskNumber(toNumber) + "?";
                } else {
                    int i = spinnerTo.getSelectedItemPosition();
                    if (i < 0 || i >= toAccounts.size()) {
                        showError("Choose the account to send money to.");
                        return;
                    }
                    Account to = toAccounts.get(i);
                    toNumber = to.getAccountNumber();
                    question = "Move " + money + " from your " + from.getDisplayName()
                            + " to your " + to.getDisplayName() + "?";
                }
                break;
        }

        String finalToNumber = toNumber;
        new AlertDialog.Builder(this)
                .setTitle("Please confirm")
                .setMessage(question)
                .setPositiveButton(buttonSubmit.getText(), (d, w) -> run(from, finalToNumber, amount, memo))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void run(Account from, String toNumber, double amount, String memo) {
        buttonSubmit.setEnabled(false);
        executorService.execute(() -> {
            OperationResult result;
            switch (mode) {
                case MODE_DEPOSIT:
                    result = databaseManager.deposit(userId, from.getAccountNumber(), amount, memo);
                    break;
                case MODE_WITHDRAW:
                    result = databaseManager.withdraw(userId, from.getAccountNumber(), amount, memo);
                    break;
                default:
                    result = databaseManager.transfer(userId, from.getAccountNumber(), toNumber, amount, memo);
                    break;
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                buttonSubmit.setEnabled(true);
                if (!result.success) {
                    showError(result.message);
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Done")
                        .setMessage(result.message + "\n\nNew " + from.getDisplayName() + " balance: "
                                + Ui.money(result.newBalance))
                        .setCancelable(false)
                        .setPositiveButton("OK", (d, w) -> finish())
                        .show();
            });
        });
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        textError.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}
