package com.example.onlinebanking_cs160;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.OperationResult;
import database.ScheduledPayment;

/** Schedule one-time or repeating bills, and see / cancel / retry upcoming ones. */
public class PayBillsActivity extends AppCompatActivity {

    // Choices in the "Repeat" drop-down: label, frequency, every N. The last one is "Custom...".
    private static final String[] REPEAT_LABELS = {
            "One time", "Every week", "Every 2 weeks", "Every month", "Every 3 months", "Every year", "Custom…"};
    private static final String[] REPEAT_FREQUENCIES = {
            ScheduledPayment.FREQUENCY_ONCE, ScheduledPayment.FREQUENCY_WEEKLY, ScheduledPayment.FREQUENCY_WEEKLY,
            ScheduledPayment.FREQUENCY_MONTHLY, ScheduledPayment.FREQUENCY_MONTHLY, ScheduledPayment.FREQUENCY_YEARLY,
            null};
    private static final int[] REPEAT_EVERY = {1, 1, 2, 1, 3, 1, 0};
    private static final int CUSTOM = REPEAT_LABELS.length - 1;

    // Units in the "Custom" row: Every [N] [days / weeks / months / years]
    private static final String[] UNIT_LABELS = {"days", "weeks", "months", "years"};
    private static final String[] UNIT_FREQUENCIES = {
            ScheduledPayment.FREQUENCY_DAILY, ScheduledPayment.FREQUENCY_WEEKLY,
            ScheduledPayment.FREQUENCY_MONTHLY, ScheduledPayment.FREQUENCY_YEARLY};

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private long userId;

    private List<Account> accounts = new ArrayList<>();
    private String dueDate = DatabaseManager.today(); // "yyyy-MM-dd"

    private EditText editPayee;
    private EditText editAmount;
    private Spinner spinnerFrom;
    private TextView textDate;
    private Spinner spinnerRepeat;
    private View groupCustomRepeat;
    private EditText editRepeatEvery;
    private Spinner spinnerRepeatUnit;
    private TextView textRepeatHint;
    private TextView textError;
    private Button buttonSchedule;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pay_bills);
        Ui.setUpScreen(this, "Pay Bills");

        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            Ui.goToLogin(this);
            return;
        }
        userId = session.getUserId();
        databaseManager = DatabaseManager.getInstance(this);

        editPayee = findViewById(R.id.editPayee);
        editAmount = findViewById(R.id.editAmount);
        spinnerFrom = findViewById(R.id.spinnerFrom);
        textDate = findViewById(R.id.textDate);
        spinnerRepeat = findViewById(R.id.spinnerRepeat);
        groupCustomRepeat = findViewById(R.id.groupCustomRepeat);
        editRepeatEvery = findViewById(R.id.editRepeatEvery);
        spinnerRepeatUnit = findViewById(R.id.spinnerRepeatUnit);
        textRepeatHint = findViewById(R.id.textRepeatHint);
        textError = findViewById(R.id.textError);
        buttonSchedule = findViewById(R.id.buttonSchedule);

        spinnerRepeat.setAdapter(adapter(REPEAT_LABELS));
        spinnerRepeatUnit.setAdapter(adapter(UNIT_LABELS));
        spinnerRepeatUnit.setSelection(1); // weeks

        AdapterView.OnItemSelectedListener refresh = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { showRepeatHint(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
        spinnerRepeat.setOnItemSelectedListener(refresh);
        spinnerRepeatUnit.setOnItemSelectedListener(refresh);
        editRepeatEvery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { showRepeatHint(); }
        });

        showDate();
        textDate.setOnClickListener(v -> pickDate());
        buttonSchedule.setOnClickListener(v -> schedule());

        load();
    }

    private ArrayAdapter<String> adapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner, items);
        adapter.setDropDownViewResource(R.layout.item_spinner);
        return adapter;
    }

    private void load() {
        executorService.execute(() -> {
            List<Account> loadedAccounts = databaseManager.getAccountsForUser(userId);
            List<ScheduledPayment> payments = databaseManager.getUpcomingPayments(userId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                accounts = loadedAccounts;
                List<String> labels = new ArrayList<>();
                for (Account a : accounts) labels.add(Ui.accountLabel(a));
                int keep = spinnerFrom.getSelectedItemPosition();
                spinnerFrom.setAdapter(adapter(labels.toArray(new String[0])));
                if (keep >= 0 && keep < labels.size()) spinnerFrom.setSelection(keep);
                showPayments(payments);
            });
        });
    }

    private void showPayments(List<ScheduledPayment> payments) {
        LinearLayout container = findViewById(R.id.paymentsContainer);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < payments.size(); i++) {
            ScheduledPayment p = payments.get(i);
            View row = Ui.paymentRow(inflater, container, p, i > 0);
            row.setOnClickListener(v -> onPaymentTapped(p));
            container.addView(row);
        }
        findViewById(R.id.textNoPayments).setVisibility(payments.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------
    // Date picker
    // ------------------------------------------------------------------

    private void pickDate() {
        Calendar current = Calendar.getInstance();
        java.util.Date parsed = Ui.parseDueDate(dueDate);
        if (parsed != null) current.setTime(parsed);

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            dueDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            showDate();
        }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH));
        // No dates in the past
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void showDate() {
        textDate.setText(Ui.friendlyDueDate(dueDate));
        showRepeatHint();
    }

    // ------------------------------------------------------------------
    // How often
    // ------------------------------------------------------------------

    /** The chosen frequency ("once", "weekly"...), reading the Custom row if Custom is picked. */
    private String chosenFrequency() {
        int i = spinnerRepeat.getSelectedItemPosition();
        if (i == CUSTOM) {
            int unit = spinnerRepeatUnit.getSelectedItemPosition();
            return UNIT_FREQUENCIES[unit < 0 ? 1 : unit];
        }
        return REPEAT_FREQUENCIES[Math.max(0, i)];
    }

    /** The chosen "every N", or -1 if the Custom number box isn't a number. */
    private int chosenEvery() {
        int i = spinnerRepeat.getSelectedItemPosition();
        if (i != CUSTOM) return REPEAT_EVERY[Math.max(0, i)];
        try {
            return Integer.parseInt(editRepeatEvery.getText().toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The line under the Repeat choice, e.g. "Paid every 2 weeks, starting Nov 2, 2026." */
    private void showRepeatHint() {
        if (spinnerRepeat == null || textRepeatHint == null) return;
        groupCustomRepeat.setVisibility(spinnerRepeat.getSelectedItemPosition() == CUSTOM ? View.VISIBLE : View.GONE);

        String frequency = chosenFrequency();
        int every = chosenEvery();
        String problem = DatabaseManager.checkFrequency(frequency, every);
        if (problem != null) {
            textRepeatHint.setText(problem);
        } else {
            textRepeatHint.setText(Ui.scheduleSentence(frequency, every, dueDate)
                    + (ScheduledPayment.FREQUENCY_ONCE.equals(frequency) ? "" : " Stop it any time from the list below."));
        }
    }

    // ------------------------------------------------------------------
    // Schedule
    // ------------------------------------------------------------------

    private void schedule() {
        textError.setVisibility(View.GONE);
        String payee = editPayee.getText().toString().trim();
        if (payee.isEmpty()) {
            editPayee.setError("Enter who you're paying");
            editPayee.requestFocus();
            return;
        }
        double amount = Ui.parseAmount(editAmount.getText().toString());
        String problem = Double.isNaN(amount) ? "Enter an amount like 25 or 25.50" : DatabaseManager.checkAmount(amount);
        if (problem != null) {
            editAmount.setError(problem);
            editAmount.requestFocus();
            return;
        }
        String frequency = chosenFrequency();
        int every = chosenEvery();
        String frequencyProblem = DatabaseManager.checkFrequency(frequency, every);
        if (frequencyProblem != null) {
            editRepeatEvery.setError(frequencyProblem);
            editRepeatEvery.requestFocus();
            return;
        }
        int i = spinnerFrom.getSelectedItemPosition();
        if (i < 0 || i >= accounts.size()) {
            showError("Open an account first so there's something to pay from.");
            return;
        }
        Account from = accounts.get(i);
        boolean once = ScheduledPayment.FREQUENCY_ONCE.equals(frequency);
        boolean payToday = DatabaseManager.today().equals(dueDate);

        String question = "Pay " + Ui.money(amount) + " to " + payee + " from your " + from.getDisplayName() + ".\n\n"
                + Ui.scheduleSentence(frequency, every, dueDate);

        new AlertDialog.Builder(this)
                .setTitle("Please confirm")
                .setMessage(question)
                .setPositiveButton(payToday && once ? "Pay now" : "Schedule",
                        (d, w) -> save(from, payee, amount, frequency, every, payToday))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void save(Account from, String payee, double amount, String frequency, int every, boolean payToday) {
        buttonSchedule.setEnabled(false);
        String date = dueDate;
        boolean once = ScheduledPayment.FREQUENCY_ONCE.equals(frequency);
        executorService.execute(() -> {
            OperationResult result = databaseManager.schedulePayment(
                    userId, from.getAccountNumber(), payee, amount, date, frequency, every);
            // A bill due today is paid straight away
            DatabaseManager.PaymentRunResult run = result.success && payToday
                    ? databaseManager.processDuePayments(userId) : null;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                buttonSchedule.setEnabled(true);
                if (!result.success) {
                    showError(result.message);
                    return;
                }
                if (run != null && run.failed > 0) {
                    showError("The payment couldn't be made today. Check the list below for the reason.");
                } else if (payToday) {
                    Ui.toast(this, "Paid " + Ui.money(amount) + " to " + payee + "."
                            + (once ? "" : " The next one is in the list below."));
                } else {
                    Ui.toast(this, result.message);
                }
                editPayee.setText("");
                editAmount.setText("");
                spinnerRepeat.setSelection(0);
                load();
            });
        });
    }

    // ------------------------------------------------------------------
    // Tapping a bill in the list
    // ------------------------------------------------------------------

    private void onPaymentTapped(ScheduledPayment p) {
        if (p.isFailed() && p.isRecurring()) {
            // Failed repeating bill: try again now, skip just this one, or stop it for good
            new AlertDialog.Builder(this)
                    .setTitle(p.getPayee() + " · " + Ui.money(p.getAmount()))
                    .setMessage(p.getFrequencyText() + ". This payment failed: " + p.getFailureReason()
                            + "\n\nTry again pays it now from your " + p.getAccountDisplayName()
                            + ". Skip leaves this one unpaid and waits for the next date.")
                    .setPositiveButton("Try again", (d, w) -> retry(p))
                    .setNeutralButton("Skip", (d, w) -> skip(p))
                    .setNegativeButton("Stop payments", (d, w) -> confirmStop(p))
                    .show();
        } else if (p.isRecurring()) {
            confirmStop(p);
        } else if (p.isFailed()) {
            new AlertDialog.Builder(this)
                    .setTitle(p.getPayee() + " · " + Ui.money(p.getAmount()))
                    .setMessage("This payment failed: " + p.getFailureReason()
                            + "\n\nTry again pays it now from your " + p.getAccountDisplayName() + ".")
                    .setPositiveButton("Try again", (d, w) -> retry(p))
                    .setNeutralButton("Remove", (d, w) -> cancel(p))
                    .setNegativeButton("Close", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(p.getPayee())
                    .setMessage("Cancel the " + Ui.money(p.getAmount()) + " payment to " + p.getPayee()
                            + " on " + Ui.friendlyDueDate(p.getDueDate()) + "?")
                    .setPositiveButton("Cancel payment", (d, w) -> cancel(p))
                    .setNegativeButton("Keep", null)
                    .show();
        }
    }

    private void confirmStop(ScheduledPayment p) {
        new AlertDialog.Builder(this)
                .setTitle("Stop payments to " + p.getPayee() + "?")
                .setMessage(p.getFrequencyText() + ", " + Ui.money(p.getAmount()) + ". Next payment: "
                        + Ui.friendlyDueDate(p.getDueDate()) + ".\n\nStopping cancels all future payments. "
                        + "Payments already made stay in your history.")
                .setPositiveButton("Stop payments", (d, w) -> cancel(p))
                .setNegativeButton("Keep", null)
                .show();
    }

    private void retry(ScheduledPayment p) {
        textError.setVisibility(View.GONE);
        executorService.execute(() -> {
            OperationResult result = databaseManager.retryPayment(userId, p.getPaymentId());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result.success) {
                    Ui.toast(this, result.message);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Payment failed again")
                            .setMessage(result.message
                                    + "\n\nAdd money to the account (Deposit or Transfer), then try again.")
                            .setPositiveButton("OK", null)
                            .show();
                }
                load();
            });
        });
    }

    private void skip(ScheduledPayment p) {
        executorService.execute(() -> {
            OperationResult result = databaseManager.skipPayment(userId, p.getPaymentId());
            runOnUiThread(() -> {
                Ui.toast(this, result.message);
                load();
            });
        });
    }

    private void cancel(ScheduledPayment p) {
        executorService.execute(() -> {
            OperationResult result = databaseManager.cancelPayment(userId, p.getPaymentId());
            runOnUiThread(() -> {
                Ui.toast(this, result.message);
                load();
            });
        });
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}