package com.example.onlinebanking_cs160;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.Transaction;

/** Full transaction history with search, money in/out filter and account filter. */
public class TransactionsActivity extends AppCompatActivity {
    private static final int MAX_TRANSACTIONS = 500;
    private static final int TYPE_ALL = 0, TYPE_IN = 1, TYPE_OUT = 2;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<Account> accounts = new ArrayList<>();

    private EditText editSearch;
    private Spinner spinnerType;
    private Spinner spinnerAccount;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transactions);
        Ui.setUpScreen(this, "Activity");

        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            Ui.goToLogin(this);
            return;
        }
        long userId = session.getUserId();
        DatabaseManager databaseManager = DatabaseManager.getInstance(this);

        editSearch = findViewById(R.id.editSearch);
        spinnerType = findViewById(R.id.spinnerType);
        spinnerAccount = findViewById(R.id.spinnerAccount);
        container = findViewById(R.id.transactionsContainer);

        spinnerType.setAdapter(adapter(new String[]{"All activity", "Money in", "Money out"}));

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { applyFilters(); }
        });
        AdapterView.OnItemSelectedListener refilter = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { applyFilters(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
        spinnerType.setOnItemSelectedListener(refilter);
        spinnerAccount.setOnItemSelectedListener(refilter);

        executorService.execute(() -> {
            List<Transaction> loaded = databaseManager.getTransactionsForUser(userId, MAX_TRANSACTIONS);
            List<Account> loadedAccounts = databaseManager.getAccountsForUser(userId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                allTransactions = loaded;
                accounts = loadedAccounts;

                List<String> accountLabels = new ArrayList<>();
                accountLabels.add("All accounts");
                for (Account a : accounts) accountLabels.add(a.getDisplayName());
                spinnerAccount.setAdapter(adapter(accountLabels.toArray(new String[0])));

                applyFilters();
            });
        });
    }

    private ArrayAdapter<String> adapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner, items);
        adapter.setDropDownViewResource(R.layout.item_spinner);
        return adapter;
    }

    private void applyFilters() {
        if (container == null) return;
        String search = editSearch.getText().toString().trim().toLowerCase(Locale.US);
        int type = spinnerType.getSelectedItemPosition();
        int accountIndex = spinnerAccount.getSelectedItemPosition(); // 0 = all accounts
        String accountNumber = accountIndex > 0 && accountIndex - 1 < accounts.size()
                ? accounts.get(accountIndex - 1).getAccountNumber() : null;

        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int shown = 0;
        for (Transaction t : allTransactions) {
            if (type == TYPE_IN && !t.isMoneyIn()) continue;
            if (type == TYPE_OUT && t.isMoneyIn()) continue;
            if (accountNumber != null && !accountNumber.equals(t.getAccountNumber())) continue;
            if (!search.isEmpty()) {
                String haystack = (t.getDescription() + " " + t.getCategory() + " "
                        + t.getAccountDisplayName()).toLowerCase(Locale.US);
                if (!haystack.contains(search)) continue;
            }
            container.addView(Ui.transactionRow(inflater, container, t, shown > 0, true));
            shown++;
        }

        findViewById(R.id.textEmpty).setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.textResultCount)).setText(
                shown + (shown == 1 ? " transaction" : " transactions"));
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}
