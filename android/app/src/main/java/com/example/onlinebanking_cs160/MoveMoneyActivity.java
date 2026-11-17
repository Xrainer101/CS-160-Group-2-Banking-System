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
 * Transfers can go to the user's own account, another customer of this bank, or an account at
 * another bank (routing number + account number) — right now, on a chosen date, or on repeat.
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
    private String sendDate = DatabaseManager.today(); // used when "On a date" is chosen

    private Spinner spinnerFrom;
    private Spinner spinnerTo;
    private TextView textAvailable;
    private RadioGroup radioSendTo;
    private RadioButton radioMyAccount;
    private View frameToAccount;
    private TextView textNoOtherAccounts;
    private EditText editRecipient;
    private TextView textRecipientName;
    private View groupOtherBank;
    private EditText editExternalName;
    private EditText editRoutingNumber;
    private TextView textRoutingCheck;
    private EditText editExternalAccount;
    private EditText editExternalAccountConfirm;
    private RadioGroup radioWhen;
    private TextView textSendDate;
    private RepeatPicker repeatPicker;
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
        groupOtherBank = findViewById(R.id.groupOtherBank);
        editExternalName = findViewById(R.id.editExternalName);
        editRoutingNumber = findViewById(R.id.editRoutingNumber);
        textRoutingCheck = findViewById(R.id.textRoutingCheck);
        editExternalAccount = findViewById(R.id.editExternalAccount);
        editExternalAccountConfirm = findViewById(R.id.editExternalAccountConfirm);
        radioWhen = findViewById(R.id.radioWhen);
        textSendDate = findViewById(R.id.textSendDate);
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
                textSubtitle.setText("Add money to one of your accounts. It's available right away. "
                        + "To deposit a paper check, use Deposit Check on the home screen.");
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
                textSubtitle.setText("Send money to your accounts, another Team 2 Bank customer, or an account "
                        + "at another bank. Send it now, on a date, or on repeat.");
                textFromLabel.setText("From account");
                findViewById(R.id.groupTransferTo).setVisibility(View.VISIBLE);
                repeatPicker = new RepeatPicker(this, () -> isOnDate() ? sendDate : DatabaseManager.today(), "Sent");
                repeatPicker.setOnChange(this::updateButtonText);
                repeatPicker.refresh();
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
        radioWhen.setOnCheckedChangeListener((group, checkedId) -> updateWhenViews());
        textSendDate.setOnClickListener(v -> Ui.pickDate(this, sendDate, date -> {
            sendDate = date;
            updateWhenViews();
        }));

        editRecipient.addTextChangedListener(new SimpleWatcher(() -> lookUpRecipient(editRecipient.getText().toString())));
        editRoutingNumber.addTextChangedListener(new SimpleWatcher(this::checkRoutingAsTyped));

        buttonSubmit.setOnClickListener(v -> onSubmit());

        loadAccounts(getIntent().getStringExtra(EXTRA_ACCOUNT_NUMBER));
    }

    /** A TextWatcher that only cares about "after the text changed". */
    private static class SimpleWatcher implements TextWatcher {
        private final Runnable action;

        SimpleWatcher(Runnable action) {
            this.action = action;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable s) { action.run(); }
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
            // Only one account: sending to someone else is the only option
            radioMyAccount.setEnabled(false);
            if (destination() == Destination.MY_ACCOUNT) radioSendTo.check(R.id.radioSomeoneElse);
        } else {
            radioMyAccount.setEnabled(true);
        }
        updateSendToViews();
    }

    // ------------------------------------------------------------------
    // Where to and when
    // ------------------------------------------------------------------

    private enum Destination { MY_ACCOUNT, CUSTOMER, OTHER_BANK }

    private Destination destination() {
        int id = radioSendTo.getCheckedRadioButtonId();
        if (id == R.id.radioSomeoneElse) return Destination.CUSTOMER;
        if (id == R.id.radioOtherBank) return Destination.OTHER_BANK;
        return Destination.MY_ACCOUNT;
    }

    private void updateSendToViews() {
        Destination d = destination();
        frameToAccount.setVisibility(d == Destination.MY_ACCOUNT && !toAccounts.isEmpty() ? View.VISIBLE : View.GONE);
        textNoOtherAccounts.setVisibility(d == Destination.MY_ACCOUNT && toAccounts.isEmpty() ? View.VISIBLE : View.GONE);
        editRecipient.setVisibility(d == Destination.CUSTOMER ? View.VISIBLE : View.GONE);
        textRecipientName.setVisibility(d == Destination.CUSTOMER && textRecipientName.getText().length() > 0
                ? View.VISIBLE : View.GONE);
        groupOtherBank.setVisibility(d == Destination.OTHER_BANK ? View.VISIBLE : View.GONE);
    }

    private boolean isOnDate() {
        return radioWhen.getCheckedRadioButtonId() == R.id.radioOnDate;
    }

    /** True when this transfer is saved for later (a date was chosen or it repeats) instead of sent now. */
    private boolean isScheduled() {
        return MODE_TRANSFER.equals(mode) && (isOnDate() || !repeatPicker.isOnce());
    }

    private String startDate() {
        return isOnDate() ? sendDate : DatabaseManager.today();
    }

    private void updateWhenViews() {
        if (repeatPicker == null) return; // only the Transfer screen has "When"
        textSendDate.setVisibility(isOnDate() ? View.VISIBLE : View.GONE);
        textSendDate.setText(Ui.friendlyDueDate(sendDate));
        repeatPicker.refresh(); // also updates the button text
    }

    private void updateButtonText() {
        if (!MODE_TRANSFER.equals(mode)) return;
        if (!isScheduled()) buttonSubmit.setText("Transfer now");
        else if (repeatPicker.isOnce()) buttonSubmit.setText("Schedule transfer");
        else buttonSubmit.setText("Set up repeating transfer");
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

    /** Checks the routing number's checksum as soon as 9 digits are typed. */
    private void checkRoutingAsTyped() {
        String routing = editRoutingNumber.getText().toString();
        if (routing.length() < 9) {
            textRoutingCheck.setText("The 9-digit number at the bottom left of their check, or in their banking app.");
            textRoutingCheck.setTextColor(ContextCompat.getColor(this, R.color.text_label));
        } else if (DatabaseManager.BANK_ROUTING_NUMBER.equals(routing)) {
            textRoutingCheck.setText("That's Team 2 Bank's own routing number, so this will be a transfer within the bank. "
                    + "The name must match the account holder's first and last name.");
            textRoutingCheck.setTextColor(ContextCompat.getColor(this, R.color.income_green));
        } else if (DatabaseManager.isValidRoutingNumber(routing)) {
            textRoutingCheck.setText("✓ Valid routing number");
            textRoutingCheck.setTextColor(ContextCompat.getColor(this, R.color.income_green));
        } else {
            textRoutingCheck.setText("That routing number isn't valid. Check the digits.");
            textRoutingCheck.setTextColor(ContextCompat.getColor(this, R.color.error_red));
        }
    }

    // ------------------------------------------------------------------
    // Submitting
    // ------------------------------------------------------------------

    /** Where a transfer goes, collected from the form. */
    private static class Target {
        String accountNumber;
        String routingNumber; // null = this bank
        String name;          // for other banks, and for the confirmation message
        String describe;      // "your Savings •••• 7391", "Bob L., account •••• 1234", ...
    }

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
        boolean scheduled = isScheduled();
        // Money is checked when a scheduled transfer is actually sent, not now
        if (!MODE_DEPOSIT.equals(mode) && !scheduled
                && Math.round(amount * 100) > Math.round(from.getBalance() * 100)) {
            editAmount.setError("That's more than the " + Ui.money(from.getBalance()) + " available");
            editAmount.requestFocus();
            return;
        }

        String memo = editMemo.getText().toString().trim();
        String money = Ui.money(amount);
        String question;
        Target target = null;

        switch (mode) {
            case MODE_DEPOSIT:
                question = "Deposit " + money + " into your " + from.getDisplayName() + "?";
                break;
            case MODE_WITHDRAW:
                question = "Withdraw " + money + " from your " + from.getDisplayName() + "?";
                break;
            default:
                target = readTarget();
                if (target == null) return; // a field showed its own error
                if (scheduled && repeatPicker.validate() != null) return;
                question = "Send " + money + " from your " + from.getDisplayName()
                        + " to " + target.describe + "?";
                if (scheduled) question += "\n\n" + Ui.scheduleSentence(repeatPicker.frequency(),
                        repeatPicker.every(), startDate()).replaceFirst("^Paid", "Sent");
                break;
        }

        Target finalTarget = target;
        new AlertDialog.Builder(this)
                .setTitle("Please confirm")
                .setMessage(question)
                .setPositiveButton(buttonSubmit.getText(), (d, w) -> run(from, finalTarget, amount, memo, scheduled))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Reads "Send to" from the form. Null (with an error on the field) if something's missing. */
    private Target readTarget() {
        Target t = new Target();
        switch (destination()) {
            case MY_ACCOUNT: {
                int i = spinnerTo.getSelectedItemPosition();
                if (i < 0 || i >= toAccounts.size()) {
                    showError("Choose the account to send money to.");
                    return null;
                }
                Account to = toAccounts.get(i);
                t.accountNumber = to.getAccountNumber();
                t.describe = "your " + to.getDisplayName();
                return t;
            }
            case CUSTOMER: {
                String number = editRecipient.getText().toString().trim();
                if (!number.matches("\\d{10}")) {
                    editRecipient.setError("Enter their 10-digit account number");
                    editRecipient.requestFocus();
                    return null;
                }
                String name = textRecipientName.getText().toString().replace("Account holder: ", "");
                t.accountNumber = number;
                t.describe = (name.isEmpty() || name.startsWith("No open") ? "account " : name + ", account ")
                        + Account.maskNumber(number);
                return t;
            }
            default: {
                String name = editExternalName.getText().toString().trim();
                String routing = editRoutingNumber.getText().toString().trim();
                String account = editExternalAccount.getText().toString().trim();
                String confirm = editExternalAccountConfirm.getText().toString().trim();
                if (name.isEmpty()) {
                    editExternalName.setError("Enter the name on their account");
                    editExternalName.requestFocus();
                    return null;
                }
                if (!DatabaseManager.isValidRoutingNumber(routing)) {
                    editRoutingNumber.setError("Enter a valid 9-digit routing number");
                    editRoutingNumber.requestFocus();
                    return null;
                }
                if (!account.matches("\\d{4,17}")) {
                    editExternalAccount.setError("Enter their account number (4 to 17 digits)");
                    editExternalAccount.requestFocus();
                    return null;
                }
                if (!account.equals(confirm)) {
                    editExternalAccountConfirm.setError("The account numbers don't match");
                    editExternalAccountConfirm.requestFocus();
                    return null;
                }
                t.accountNumber = account;
                t.routingNumber = routing;
                t.name = name;
                t.describe = name + " at another bank (routing " + routing + ", account "
                        + Account.maskNumber(account) + ")";
                return t;
            }
        }
    }

    private void run(Account from, Target target, double amount, String memo, boolean scheduled) {
        buttonSubmit.setEnabled(false);
        String start = startDate();
        String frequency = repeatPicker == null ? null : repeatPicker.frequency();
        int every = repeatPicker == null ? 1 : repeatPicker.every();

        executorService.execute(() -> {
            OperationResult result;
            DatabaseManager.PaymentRunResult sentToday = null;
            if (MODE_DEPOSIT.equals(mode)) {
                result = databaseManager.deposit(userId, from.getAccountNumber(), amount, memo);
            } else if (MODE_WITHDRAW.equals(mode)) {
                result = databaseManager.withdraw(userId, from.getAccountNumber(), amount, memo);
            } else if (scheduled) {
                result = databaseManager.scheduleTransfer(userId, from.getAccountNumber(), target.accountNumber,
                        target.routingNumber, target.name, amount, start, frequency, every, memo);
                // One that starts today is sent straight away
                if (result.success && DatabaseManager.today().equals(start)) {
                    sentToday = databaseManager.processDuePayments(userId);
                }
            } else if (target.routingNumber != null) {
                result = databaseManager.transferToOtherBank(userId, from.getAccountNumber(), target.routingNumber,
                        target.accountNumber, target.name, amount, memo);
            } else {
                result = databaseManager.transfer(userId, from.getAccountNumber(), target.accountNumber, amount, memo);
            }
            Account updated = databaseManager.getAccount(userId, from.getAccountNumber());
            DatabaseManager.PaymentRunResult finalSentToday = sentToday;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                buttonSubmit.setEnabled(true);
                if (!result.success) {
                    showError(result.message);
                    return;
                }
                String message = result.message;
                if (scheduled) {
                    message += "\n\n" + Ui.scheduleSentence(frequency, every, start).replaceFirst("^Paid", "Sent")
                            + " You can change or stop it on the Pay Bills screen.";
                    if (finalSentToday != null && finalSentToday.failed > 0) {
                        message += "\n\nToday's transfer couldn't be sent. See Pay Bills for the reason.";
                    }
                } else if (target != null && target.routingNumber != null
                        && !DatabaseManager.BANK_ROUTING_NUMBER.equals(target.routingNumber)) {
                    message += "\n\n(A real bank transfer to another bank takes 1–3 business days to arrive.)";
                }
                if (updated != null) {
                    message += "\n\n" + updated.getDisplayName() + " balance: " + Ui.money(updated.getBalance());
                }
                new AlertDialog.Builder(this)
                        .setTitle("Done")
                        .setMessage(message)
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