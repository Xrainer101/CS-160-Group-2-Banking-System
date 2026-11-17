package com.example.onlinebanking_cs160;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.Card;
import database.DatabaseManager;
import database.OperationResult;
import database.User;

/**
 * The user's debit card. It's linked to a checking account.
 *  - Freeze: deposits, withdrawals, transfers (in and out) and bill payments on that checking
 *    account are blocked until it's unfrozen.
 *  - Replace: new card number AND new checking account number; the old account's money and
 *    scheduled payments move to the new one and the old account is closed.
 */
public class CardsActivity extends AppCompatActivity {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private long userId;

    private Card card;
    private Account linkedAccount;
    private boolean numberShown = false;

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
        buttonFreeze.setOnClickListener(v -> {
            if (card != null) setFrozen(!card.isFrozen());
        });
        findViewById(R.id.buttonShowNumber).setOnClickListener(v -> {
            numberShown = !numberShown;
            showCard();
        });
        findViewById(R.id.buttonReplace).setOnClickListener(v -> confirmReplace());

        load();
    }

    private void load() {
        executorService.execute(() -> {
            User user = databaseManager.getUserById(userId);
            Card loaded = databaseManager.getCard(userId);
            Account account = loaded == null ? null : databaseManager.getAccount(userId, loaded.getAccountNumber());

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (user == null || loaded == null || account == null) {
                    findViewById(R.id.groupCard).setVisibility(View.GONE);
                    findViewById(R.id.textNoCard).setVisibility(View.VISIBLE);
                    return;
                }
                findViewById(R.id.groupCard).setVisibility(View.VISIBLE);
                findViewById(R.id.textNoCard).setVisibility(View.GONE);
                card = loaded;
                linkedAccount = account;
                ((TextView) findViewById(R.id.textCardholder)).setText(user.getFullName().toUpperCase(Locale.US));
                showCard();
            });
        });
    }

    private void showCard() {
        if (card == null) return;
        ((TextView) findViewById(R.id.textCardNumber)).setText(
                numberShown ? card.getFormattedNumber() : card.getMaskedNumber());
        ((Button) findViewById(R.id.buttonShowNumber)).setText(numberShown ? "Hide card number" : "Show card number");
        ((TextView) findViewById(R.id.textCardExpiry)).setText(card.getExpires());
        ((TextView) findViewById(R.id.textLinkedAccount)).setText(
                "Linked to your " + linkedAccount.getDisplayName() + " · " + Ui.money(linkedAccount.getBalance()));

        boolean frozen = card.isFrozen();
        findViewById(R.id.frozenBadge).setVisibility(frozen ? View.VISIBLE : View.GONE);
        findViewById(R.id.cardFace).setAlpha(frozen ? 0.55f : 1f);
        ((TextView) findViewById(R.id.textCardStatus)).setText(frozen ? "Card is frozen" : "Card is active");
        ((TextView) findViewById(R.id.textCardStatusHint)).setText(frozen
                ? "Deposits, withdrawals, transfers and bill payments on " + linkedAccount.getDisplayName()
                + " are paused until you unfreeze the card. Scheduled bills and transfers that come "
                + "due meanwhile are marked failed; use Try again on Pay Bills after unfreezing."
                : "Misplaced your card? Freeze it to pause deposits, withdrawals, transfers and bill payments on "
                + linkedAccount.getDisplayName() + ", then unfreeze it once it turns up.");
        buttonFreeze.setText(frozen ? "Unfreeze card" : "Freeze card");
    }

    private void setFrozen(boolean freeze) {
        buttonFreeze.setEnabled(false);
        executorService.execute(() -> {
            OperationResult result = databaseManager.setCardFrozen(userId, freeze);
            runOnUiThread(() -> {
                buttonFreeze.setEnabled(true);
                Ui.toast(this, result.message);
                load();
            });
        });
    }

    private void confirmReplace() {
        if (card == null || linkedAccount == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Replace your card?")
                .setMessage("Use this if your card is lost, stolen or damaged.\n\n"
                        + "• Card •••• " + card.getLastFour() + " stops working and you get a new card number.\n"
                        + "• " + linkedAccount.getDisplayName() + " is closed and a new checking account is opened "
                        + "with a new account number.\n"
                        + "• Your " + Ui.money(linkedAccount.getBalance()) + " balance and any scheduled payments "
                        + "move to the new account. Past activity stays in your history.\n\n"
                        + "Update anyone who pays you by direct deposit with the new account number.")
                .setPositiveButton("Replace card", (d, w) -> replace())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void replace() {
        executorService.execute(() -> {
            OperationResult result = databaseManager.replaceCard(userId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this)
                        .setTitle(result.success ? "Card replaced" : "Couldn't replace the card")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show();
                numberShown = false;
                load();
            });
        });
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}