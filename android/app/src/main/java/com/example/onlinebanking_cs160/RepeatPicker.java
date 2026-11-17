package com.example.onlinebanking_cs160;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import database.DatabaseManager;
import database.ScheduledPayment;

/**
 * Runs the "Repeat" choice (layout view_repeat_picker.xml) on the Pay Bills and Transfer screens:
 * a drop-down of common choices plus "Custom…" = Every [N] [days/weeks/months/years],
 * and a line underneath that says the schedule in words.
 */
public class RepeatPicker {
    // Choices in the drop-down: label, frequency, every N. The last one is "Custom...".
    private static final String[] LABELS = {
            "One time", "Every week", "Every 2 weeks", "Every month", "Every 3 months", "Every year", "Custom…"};
    private static final String[] FREQUENCIES = {
            ScheduledPayment.FREQUENCY_ONCE, ScheduledPayment.FREQUENCY_WEEKLY, ScheduledPayment.FREQUENCY_WEEKLY,
            ScheduledPayment.FREQUENCY_MONTHLY, ScheduledPayment.FREQUENCY_MONTHLY, ScheduledPayment.FREQUENCY_YEARLY,
            null};
    private static final int[] EVERY = {1, 1, 2, 1, 3, 1, 0};
    private static final int CUSTOM = LABELS.length - 1;

    // Units in the "Custom" row
    private static final String[] UNIT_LABELS = {"days", "weeks", "months", "years"};
    private static final String[] UNIT_FREQUENCIES = {
            ScheduledPayment.FREQUENCY_DAILY, ScheduledPayment.FREQUENCY_WEEKLY,
            ScheduledPayment.FREQUENCY_MONTHLY, ScheduledPayment.FREQUENCY_YEARLY};

    /** Tells the picker the start date ("yyyy-MM-dd") so the hint can say it. */
    public interface DateSource {
        String date();
    }

    private final Spinner spinnerRepeat;
    private final View groupCustom;
    private final EditText editEvery;
    private final Spinner spinnerUnit;
    private final TextView textHint;
    private final DateSource dateSource;
    private final String verb; // "Paid" or "Sent"
    private Runnable onChange;

    public RepeatPicker(AppCompatActivity activity, DateSource dateSource, String verb) {
        this.dateSource = dateSource;
        this.verb = verb;
        spinnerRepeat = activity.findViewById(R.id.spinnerRepeat);
        groupCustom = activity.findViewById(R.id.groupCustomRepeat);
        editEvery = activity.findViewById(R.id.editRepeatEvery);
        spinnerUnit = activity.findViewById(R.id.spinnerRepeatUnit);
        textHint = activity.findViewById(R.id.textRepeatHint);

        spinnerRepeat.setAdapter(adapter(activity, LABELS));
        spinnerUnit.setAdapter(adapter(activity, UNIT_LABELS));
        spinnerUnit.setSelection(1); // weeks

        AdapterView.OnItemSelectedListener refresh = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refresh(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
        spinnerRepeat.setOnItemSelectedListener(refresh);
        spinnerUnit.setOnItemSelectedListener(refresh);
        editEvery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
    }

    private static ArrayAdapter<String> adapter(AppCompatActivity activity, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.item_spinner, items);
        adapter.setDropDownViewResource(R.layout.item_spinner);
        return adapter;
    }

    /** Called whenever the choice changes (e.g. so the screen can change its button text). */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** "once", "daily", "weekly", "monthly" or "yearly". */
    public String frequency() {
        int i = spinnerRepeat.getSelectedItemPosition();
        if (i == CUSTOM) {
            int unit = spinnerUnit.getSelectedItemPosition();
            return UNIT_FREQUENCIES[unit < 0 ? 1 : unit];
        }
        return FREQUENCIES[Math.max(0, i)];
    }

    /** "every N", or -1 if the Custom number box isn't a number. */
    public int every() {
        int i = spinnerRepeat.getSelectedItemPosition();
        if (i != CUSTOM) return EVERY[Math.max(0, i)];
        try {
            return Integer.parseInt(editEvery.getText().toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public boolean isOnce() {
        return ScheduledPayment.FREQUENCY_ONCE.equals(frequency());
    }

    /** Null if the choice is OK; otherwise what's wrong (also shown on the number box). */
    public String validate() {
        String problem = DatabaseManager.checkFrequency(frequency(), every());
        if (problem != null) {
            editEvery.setError(problem);
            editEvery.requestFocus();
        }
        return problem;
    }

    /** Shows a saved schedule (used when editing): picks the matching choice, or fills in Custom. */
    public void set(String frequency, int every) {
        for (int i = 0; i < CUSTOM; i++) {
            if (FREQUENCIES[i].equals(frequency) && EVERY[i] == every) {
                spinnerRepeat.setSelection(i);
                refresh();
                return;
            }
        }
        for (int u = 0; u < UNIT_FREQUENCIES.length; u++) {
            if (UNIT_FREQUENCIES[u].equals(frequency)) spinnerUnit.setSelection(u);
        }
        editEvery.setText(String.valueOf(every));
        spinnerRepeat.setSelection(CUSTOM);
        refresh();
    }

    public void reset() {
        spinnerRepeat.setSelection(0);
        refresh();
    }

    /** Updates the Custom row and the hint line, e.g. "Sent every 2 weeks, starting Nov 2, 2026." */
    public void refresh() {
        groupCustom.setVisibility(spinnerRepeat.getSelectedItemPosition() == CUSTOM ? View.VISIBLE : View.GONE);
        String frequency = frequency();
        int every = every();
        String problem = DatabaseManager.checkFrequency(frequency, every);
        if (problem != null) {
            textHint.setText(problem);
        } else {
            String sentence = Ui.scheduleSentence(frequency, every, dateSource.date());
            if (!"Paid".equals(verb)) sentence = sentence.replaceFirst("^Paid", verb);
            textHint.setText(sentence + (ScheduledPayment.FREQUENCY_ONCE.equals(frequency)
                    ? "" : " Stop or change it any time from Pay Bills."));
        }
        if (onChange != null) onChange.run();
    }
}
