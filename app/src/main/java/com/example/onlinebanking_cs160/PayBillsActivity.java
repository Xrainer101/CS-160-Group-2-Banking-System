package com.example.onlinebanking_cs160;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.OperationResult;
import database.ScheduledPayment;

/**
 * Schedule one-time or repeating bills, and see / edit / stop / retry upcoming bills and
 * scheduled transfers (transfers are set up on the Transfer screen and listed here too).
 */
public class PayBillsActivity extends AppCompatActivity {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private long userId;

    private List<Account> accounts = new ArrayList<>();
    private String dueDate = DatabaseManager.today(); // "yyyy-MM-dd"
    /** The bill/transfer being edited, or null when the form is for a new bill. */
    private ScheduledPayment editing;

    private TextView textFormTitle;
    private TextView textFormSubtitle;
    private TextView labelPayee;
    private TextView labelDate;
    private EditText editPayee;
    private EditText editAmount;
    private Spinner spinnerFrom;
    private TextView textDate;
    private RepeatPicker repeatPicker;
    private TextView textError;
    private Button buttonSchedule;
    private Button buttonCancelEdit;

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

        textFormTitle = findViewById(R.id.textFormTitle);
        textFormSubtitle = findViewById(R.id.textFormSubtitle);
        labelPayee = findViewById(R.id.labelPayee);
        labelDate = findViewById(R.id.labelDate);
        editPayee = findViewById(R.id.editPayee);
        editAmount = findViewById(R.id.editAmount);
        spinnerFrom = findViewById(R.id.spinnerFrom);
        textDate = findViewById(R.id.textDate);
        textError = findViewById(R.id.textError);
        buttonSchedule = findViewById(R.id.buttonSchedule);
        buttonCancelEdit = findViewById(R.id.buttonCancelEdit);

        repeatPicker = new RepeatPicker(this, () -> dueDate, "Paid");
        showDate();
        textDate.setOnClickListener(v -> Ui.pickDate(this, dueDate, date -> {
            dueDate = date;
            showDate();
        }));
        buttonSchedule.setOnClickListener(v -> save());
        buttonCancelEdit.setOnClickListener(v -> stopEditing());

        load();
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
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner, labels);
                adapter.setDropDownViewResource(R.layout.item_spinner);
                spinnerFrom.setAdapter(adapter);
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

    private void showDate() {
        textDate.setText(Ui.friendlyDueDate(dueDate));
        repeatPicker.refresh();
    }

    // ------------------------------------------------------------------
    // New bill, or saving an edit
    // ------------------------------------------------------------------

    private void save() {
        textError.setVisibility(View.GONE);
        boolean isTransfer = editing != null && editing.isTransfer();
        String payee = editPayee.getText().toString().trim();
        if (!isTransfer && payee.isEmpty()) {
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
        if (repeatPicker.validate() != null) return;
        int i = spinnerFrom.getSelectedItemPosition();
        if (i < 0 || i >= accounts.size()) {
            showError("Open an account first so there's something to pay from.");
            return;
        }
        Account from = accounts.get(i);
        String frequency = repeatPicker.frequency();
        int every = repeatPicker.every();
        boolean once = ScheduledPayment.FREQUENCY_ONCE.equals(frequency);
        boolean today = DatabaseManager.today().equals(dueDate);

        String what = isTransfer ? editing.getTitle() : "Pay " + Ui.money(amount) + " to " + payee;
        String question = (editing != null ? "Save these changes?\n\n" : "")
                + (isTransfer ? what + ", " + Ui.money(amount) : what) + " from your " + from.getDisplayName() + ".\n\n"
                + Ui.scheduleSentence(frequency, every, dueDate);

        new AlertDialog.Builder(this)
                .setTitle("Please confirm")
                .setMessage(question)
                .setPositiveButton(editing != null ? "Save" : today && once ? "Pay now" : "Schedule",
                        (d, w) -> saveConfirmed(from, payee, amount, frequency, every, today))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveConfirmed(Account from, String payee, double amount, String frequency, int every, boolean today) {
        buttonSchedule.setEnabled(false);
        String date = dueDate;
        ScheduledPayment edited = editing;
        executorService.execute(() -> {
            OperationResult result = edited != null
                    ? databaseManager.updatePayment(userId, edited.getPaymentId(), from.getAccountNumber(),
                            edited.isTransfer() ? null : payee, amount, date, frequency, every)
                    : databaseManager.schedulePayment(userId, from.getAccountNumber(), payee, amount, date,
                            frequency, every);
            // Anything due today is paid straight away
            DatabaseManager.PaymentRunResult run = result.success && today
                    ? databaseManager.processDuePayments(userId) : null;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                buttonSchedule.setEnabled(true);
                if (!result.success) {
                    showError(result.message);
                    return;
                }
                if (run != null && run.failed > 0) {
                    showError("Today's payment couldn't be made. Check the list below for the reason.");
                } else if (run != null && run.paid > 0) {
                    Ui.toast(this, "Paid " + Ui.money(amount) + " today."
                            + (ScheduledPayment.FREQUENCY_ONCE.equals(frequency) ? "" : " The next one is in the list below."));
                } else {
                    Ui.toast(this, result.message);
                }
                stopEditing();
                load();
            });
        });
    }

    // ------------------------------------------------------------------
    // Edit mode
    // ------------------------------------------------------------------

    /** Fills the form with a scheduled bill/transfer so it can be changed. */
    private void startEditing(ScheduledPayment p) {
        editing = p;
        textError.setVisibility(View.GONE);
        textFormTitle.setText(p.isTransfer() ? "Edit transfer" : "Edit payment");
        textFormSubtitle.setText(p.isTransfer()
                ? "Change the amount, the account it comes from, the next date or how often it repeats. "
                    + "To send it somewhere else, stop it and set up a new transfer."
                : "Change who's paid, the amount, the account, the next date or how often it repeats.");
        labelPayee.setText(p.isTransfer() ? "Sends to" : "Pay to");
        editPayee.setText(p.getPayee());
        editPayee.setEnabled(!p.isTransfer());
        editAmount.setText(String.format(java.util.Locale.US, "%.2f", p.getAmount()));
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getAccountNumber().equals(p.getAccountNumber())) spinnerFrom.setSelection(i);
        }
        // A failed one's date has passed: start from today
        dueDate = p.getDueDate().compareTo(DatabaseManager.today()) < 0 ? DatabaseManager.today() : p.getDueDate();
        labelDate.setText(p.isRecurring() ? "Next payment on" : "Pay on");
        repeatPicker.set(p.getFrequency(), p.getRepeatEvery());
        showDate();
        buttonSchedule.setText("Save changes");
        buttonCancelEdit.setVisibility(View.VISIBLE);
        ScrollView scroll = findViewById(R.id.scrollContent);
        scroll.post(() -> scroll.smoothScrollTo(0, 0));
    }

    private void stopEditing() {
        editing = null;
        textFormTitle.setText("Pay a bill");
        textFormSubtitle.setText("Pick a date and the money is paid from your account on that day.");
        labelPayee.setText("Pay to");
        labelDate.setText("Pay on");
        editPayee.setEnabled(true);
        editPayee.setText("");
        editAmount.setText("");
        dueDate = DatabaseManager.today();
        repeatPicker.reset();
        showDate();
        buttonSchedule.setText("Schedule payment");
        buttonCancelEdit.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------
    // Tapping something in the list
    // ------------------------------------------------------------------

    private void onPaymentTapped(ScheduledPayment p) {
        String title = p.getTitle() + " · " + Ui.money(p.getAmount());
        String details = p.isFailed()
                ? "This payment failed: " + p.getFailureReason()
                : (p.isRecurring() ? p.getFrequencyText() + ". Next: " : "On ") + Ui.friendlyDueDate(p.getDueDate())
                    + ", from " + p.getAccountDisplayName() + ".";

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (p.isFailed()) {
            labels.add("Try again now");
            actions.add(() -> retry(p));
        }
        labels.add("Edit");
        actions.add(() -> startEditing(p));
        if (p.isFailed() && p.isRecurring()) {
            labels.add("Skip this one");
            actions.add(() -> skip(p));
        }
        labels.add(p.isRecurring() ? "Stop all future payments" : p.isFailed() ? "Remove" : "Cancel payment");
        actions.add(() -> confirmCancel(p));

        new AlertDialog.Builder(this)
                .setTitle(title + "\n" + details)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton("Close", null)
                .show();
    }

    private void confirmCancel(ScheduledPayment p) {
        String message = p.isRecurring()
                ? p.getFrequencyText() + ", " + Ui.money(p.getAmount()) + ". Stopping cancels all future payments. "
                    + "Payments already made stay in your history."
                : "Cancel " + p.getTitle() + " (" + Ui.money(p.getAmount()) + ") on " + Ui.friendlyDueDate(p.getDueDate()) + "?";
        new AlertDialog.Builder(this)
                .setTitle(p.isRecurring() ? "Stop " + p.getTitle() + "?" : "Cancel this payment?")
                .setMessage(message)
                .setPositiveButton(p.isRecurring() ? "Stop payments" : "Cancel payment", (d, w) -> cancel(p))
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
                                    + "\n\nAdd money to the account (Deposit or Transfer), or Edit it to pay from another account.")
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
                if (editing != null && editing.getPaymentId() == p.getPaymentId()) stopEditing();
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
