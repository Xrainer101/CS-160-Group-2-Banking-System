package com.example.onlinebanking_cs160;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.User;

/**
 * The user's debit card (linked to their first checking account). Freezing is saved in the
 * database (users.card_frozen). The app has no card purchases yet, so freezing doesn't block
 * anything else in the app — it's the same as the website's card controls.
 */
public class CardsActivity extends AppCompatActivity {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private long userId;
    private boolean frozen;

    private Button buttonFreeze;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cards);
        Ui.setUpScreen(this, "My Cards");

        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            Ui.goToLogin(this);
            return;
        }
        userId = session.getUserId();
        databaseManager = DatabaseManager.getInstance(this);

        buttonFreeze = findViewById(R.id.buttonFreeze);
        buttonFreeze.setOnClickListener(v -> setFrozen(!frozen));

        findViewById(R.id.buttonReplace).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Request a replacement card?")
                .setMessage("Use this if your card is lost, stolen or damaged. "
                        + "(This is a class prototype, so no physical card is sent.)")
                .setPositiveButton("Request", (d, w) -> Ui.toast(this, "Replacement card requested."))
                .setNegativeButton("Cancel", null)
                .show());

        load();
    }

    private void load() {
        executorService.execute(() -> {
            User user = databaseManager.getUserById(userId);
            List<Account> accounts = databaseManager.getAccountsForUser(userId);
            boolean isFrozen = databaseManager.isCardFrozen(userId);

            Account checking = null;
            for (Account a : accounts) {
                if (Account.TYPE_CHECKING.equals(a.getAccountType())) {
                    checking = a;
                    break;
                }
            }
            Account linked = checking;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (user == null || linked == null) {
                    findViewById(R.id.groupCard).setVisibility(View.GONE);
                    findViewById(R.id.textNoCard).setVisibility(View.VISIBLE);
                    return;
                }
                String number = linked.getAccountNumber();
                ((TextView) findViewById(R.id.textCardNumber)).setText(
                        "••••  ••••  ••••  " + number.substring(number.length() - 4));
                ((TextView) findViewById(R.id.textCardholder)).setText(
                        user.getFullName().toUpperCase(Locale.US));
                ((TextView) findViewById(R.id.textLinkedAccount)).setText(
                        "Linked to your " + linked.getDisplayName());
                frozen = isFrozen;
                showFrozenState();
            });
        });
    }

    private void setFrozen(boolean freeze) {
        buttonFreeze.setEnabled(false);
        executorService.execute(() -> {
            databaseManager.setCardFrozen(userId, freeze);
            runOnUiThread(() -> {
                buttonFreeze.setEnabled(true);
                frozen = freeze;
                showFrozenState();
                Ui.toast(this, freeze ? "Card frozen." : "Card unfrozen.");
            });
        });
    }

    private void showFrozenState() {
        findViewById(R.id.frozenBadge).setVisibility(frozen ? View.VISIBLE : View.GONE);
        findViewById(R.id.cardFace).setAlpha(frozen ? 0.55f : 1f);
        ((TextView) findViewById(R.id.textCardStatus)).setText(frozen ? "Card is frozen" : "Card is active");
        ((TextView) findViewById(R.id.textCardStatusHint)).setText(frozen
                ? "Purchases with this card would be declined until you unfreeze it."
                : "Misplaced your card? Freeze it here, then unfreeze it once it turns up.");
        buttonFreeze.setText(frozen ? "Unfreeze card" : "Freeze card");
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}
