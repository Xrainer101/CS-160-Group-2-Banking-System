package com.example.onlinebanking_cs160;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.format.DateUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import database.Account;
import database.ScheduledPayment;
import database.Transaction;

/** Small helpers shared by all the screens (formatting, screen setup, list rows). */
public final class Ui {
    private Ui() { }

    private static final String PREFS_SETTINGS = "settings";
    private static final String KEY_TRANSACTION_ALERTS = "transaction_alerts";

    // ------------------------------------------------------------------
    // Screen setup
    // ------------------------------------------------------------------

    /**
     * For screens built on view_screen_header: sets the title, makes the back arrow close the
     * screen, and keeps content clear of the status bar and navigation bar.
     * Call after setContentView.
     */
    public static void setUpScreen(AppCompatActivity activity, String title) {
        ((TextView) activity.findViewById(R.id.headerTitle)).setText(title);
        activity.findViewById(R.id.buttonBack).setOnClickListener(v -> activity.finish());

        View scroll = activity.findViewById(R.id.scrollContent);
        int baseBottom = scroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(),
                    scroll.getPaddingRight(), baseBottom + bars.bottom);
            return insets;
        });
    }

    /** Sends the user back to the login screen (used when nobody is logged in). */
    public static void goToLogin(AppCompatActivity activity) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------
    // Money
    // ------------------------------------------------------------------

    public static String money(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    /** "+$25.00" for money in, "−$25.00" for money out. */
    public static String signedMoney(double amount) {
        return (amount >= 0 ? "+" : "−") + money(Math.abs(amount));
    }

    /**
     * Reads what the user typed in an amount box. Accepts "25", "25.5", "$1,250.00".
     * Returns NaN if it isn't a valid dollar amount with at most 2 decimal places.
     */
    public static double parseAmount(String text) {
        String cleaned = text == null ? "" : text.trim().replace("$", "").replace(",", "").replace(" ", "");
        if (!cleaned.matches("\\d{1,9}(\\.\\d{0,2})?|\\.\\d{1,2}")) return Double.NaN;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** "Checking •••• 2048 · $8,240.10" for account pickers. */
    public static String accountLabel(Account account) {
        return account.getDisplayName() + " · " + money(account.getBalance());
    }

    // ------------------------------------------------------------------
    // Dates
    // ------------------------------------------------------------------

    /** "Today", "Yesterday", "Oct 25", or "Oct 25, 2025" for older years. */
    public static String shortDate(long millis) {
        if (DateUtils.isToday(millis)) return "Today";
        if (DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS)) return "Yesterday";
        SimpleDateFormat sameYear = new SimpleDateFormat("yyyy", Locale.US);
        boolean thisYear = sameYear.format(new Date(millis)).equals(sameYear.format(new Date()));
        return new SimpleDateFormat(thisYear ? "MMM d" : "MMM d, yyyy", Locale.US).format(new Date(millis));
    }

    /** "2026-11-05" -> Date, or null if it can't be read. */
    public static Date parseDueDate(String dueDate) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dueDate);
        } catch (ParseException e) {
            return null;
        }
    }

    /** "2026-11-05" -> "Nov 5" (or "Today"). */
    public static String friendlyDueDate(String dueDate) {
        if (database.DatabaseManager.today().equals(dueDate)) return "Today";
        Date date = parseDueDate(dueDate);
        return date == null ? dueDate : new SimpleDateFormat("MMM d, yyyy", Locale.US).format(date);
    }

    /** "2026-11-15" -> "15th", "2026-11-01" -> "1st", "2026-11-22" -> "22nd". */
    public static String dayOfMonthText(String dueDate) {
        int day = Integer.parseInt(dueDate.substring(8, 10));
        String suffix = "th";
        if (day < 11 || day > 13) {
            switch (day % 10) {
                case 1: suffix = "st"; break;
                case 2: suffix = "nd"; break;
                case 3: suffix = "rd"; break;
                default: break;
            }
        }
        return day + suffix;
    }

    /**
     * Describes a bill's schedule in a sentence:
     *  once:            "Paid once, on Nov 15, 2026."
     *  monthly, 1:      "Paid on the 15th of every month, starting Nov 15, 2026."
     *  monthly, 3:      "Paid on the 15th every 3 months, starting Nov 15, 2026."
     *  weekly, 2:       "Paid every 2 weeks, starting Nov 15, 2026."
     *  daily / yearly:  "Paid daily, starting ..." / "Paid every year, starting ..."
     */
    public static String scheduleSentence(String frequency, int every, String dueDate) {
        String start = friendlyDueDate(dueDate);
        String startText = "Today".equals(start) ? "today" : start;
        if (database.ScheduledPayment.FREQUENCY_ONCE.equals(frequency)) {
            return "Paid once, " + ("Today".equals(start) ? "today." : "on " + start + ".");
        }
        if (database.ScheduledPayment.FREQUENCY_MONTHLY.equals(frequency)) {
            return "Paid on the " + dayOfMonthText(dueDate)
                    + (every <= 1 ? " of every month" : " every " + every + " months")
                    + ", starting " + startText + ".";
        }
        String how = database.ScheduledPayment.describeFrequency(frequency, every).toLowerCase(Locale.US);
        if (database.ScheduledPayment.FREQUENCY_YEARLY.equals(frequency) && every <= 1) how = "every year";
        return "Paid " + how + ", starting " + startText + ".";
    }

    // ------------------------------------------------------------------
    // Validation (also used by Settings)
    // ------------------------------------------------------------------

    public static boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    /** Keeps only the digits; returns the 10-digit number, or null if it isn't one. */
    public static String normalizePhone(String input) {
        String digits = input.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("1")) digits = digits.substring(1);
        return digits.length() == 10 ? digits : null;
    }

    // ------------------------------------------------------------------
    // Settings stored on the phone
    // ------------------------------------------------------------------

    public static boolean transactionAlertsOn(Context context) {
        return prefs(context).getBoolean(KEY_TRANSACTION_ALERTS, true);
    }

    public static void setTransactionAlerts(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_TRANSACTION_ALERTS, on).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    // List rows
    // ------------------------------------------------------------------

    /** Builds one row of item_transaction for the given transaction. */
    public static View transactionRow(LayoutInflater inflater, ViewGroup parent, Transaction t,
                                      boolean showDivider, boolean showAccount) {
        View row = inflater.inflate(R.layout.item_transaction, parent, false);
        Context context = row.getContext();

        row.findViewById(R.id.rowDivider).setVisibility(showDivider ? View.VISIBLE : View.GONE);

        int icon;
        switch (t.getType()) {
            case Transaction.TYPE_DEPOSIT: icon = R.drawable.ic_arrow_down; break;
            case Transaction.TYPE_WITHDRAWAL: icon = R.drawable.ic_arrow_up; break;
            case Transaction.TYPE_BILL_PAYMENT: icon = R.drawable.ic_receipt; break;
            default: icon = R.drawable.ic_transfer; break;
        }
        ((ImageView) row.findViewById(R.id.imageTransactionIcon)).setImageResource(icon);

        ((TextView) row.findViewById(R.id.textTransactionTitle)).setText(t.getDescription());

        String meta = t.getCategory() + (showAccount ? " · " + t.getAccountDisplayName() : "")
                + " · " + shortDate(t.getTimestamp());
        ((TextView) row.findViewById(R.id.textTransactionMeta)).setText(meta);

        TextView amount = row.findViewById(R.id.textTransactionAmount);
        amount.setText(signedMoney(t.getSignedAmount()));
        amount.setTextColor(ContextCompat.getColor(context, t.isMoneyIn() ? R.color.income_green : R.color.text_dark));
        return row;
    }

    /** Builds one row of item_payment for the given scheduled bill. */
    public static View paymentRow(LayoutInflater inflater, ViewGroup parent, ScheduledPayment p, boolean showDivider) {
        View row = inflater.inflate(R.layout.item_payment, parent, false);
        Context context = row.getContext();

        row.findViewById(R.id.rowDivider).setVisibility(showDivider ? View.VISIBLE : View.GONE);

        Date due = parseDueDate(p.getDueDate());
        ((TextView) row.findViewById(R.id.textPaymentMonth)).setText(
                due == null ? "" : new SimpleDateFormat("MMM", Locale.US).format(due).toUpperCase(Locale.US));
        ((TextView) row.findViewById(R.id.textPaymentDay)).setText(
                due == null ? "" : new SimpleDateFormat("d", Locale.US).format(due));

        ((TextView) row.findViewById(R.id.textPaymentPayee)).setText(p.getPayee());

        TextView meta = row.findViewById(R.id.textPaymentMeta);
        if (p.isFailed()) {
            meta.setText("Payment failed · " + (p.getFailureReason() == null ? "" : p.getFailureReason() + " ")
                    + "Tap to try again.");
            meta.setTextColor(ContextCompat.getColor(context, R.color.error_red));
        } else if (p.isRecurring()) {
            meta.setText(p.getFrequencyText() + " · Next " + friendlyDueDate(p.getDueDate())
                    + " · From " + p.getAccountDisplayName());
            meta.setTextColor(ContextCompat.getColor(context, R.color.text_label));
        } else {
            meta.setText(friendlyDueDate(p.getDueDate()) + " · From " + p.getAccountDisplayName());
            meta.setTextColor(ContextCompat.getColor(context, R.color.text_label));
        }

        ((TextView) row.findViewById(R.id.textPaymentAmount)).setText(money(p.getAmount()));
        return row;
    }
}