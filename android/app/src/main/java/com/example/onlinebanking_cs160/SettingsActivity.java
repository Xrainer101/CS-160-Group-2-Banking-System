package com.example.onlinebanking_cs160;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.OperationResult;
import database.ProfileDeletionCheck;
import database.User;

/** Edit profile, change password, notification preference and delete profile. */
public class SettingsActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private SessionManager session;
    private long userId;
    private String username; // shown in the delete-profile summary
    private String email;
    private boolean auth0User; // true if this person logs in with Auth0 (password checked by Auth0)

    private EditText editFirstName, editLastName, editEmail, editPhone;
    private EditText editCurrentPassword, editNewPassword, editConfirmPassword;
    private TextView textProfileError, textPasswordError;
    private Button buttonSaveProfile, buttonChangePassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        Ui.setUpScreen(this, "Settings");

        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            Ui.goToLogin(this);
            return;
        }
        userId = session.getUserId();
        databaseManager = DatabaseManager.getInstance(this);

        editFirstName = findViewById(R.id.editFirstName);
        editLastName = findViewById(R.id.editLastName);
        editEmail = findViewById(R.id.editEmail);
        editPhone = findViewById(R.id.editPhone);
        editCurrentPassword = findViewById(R.id.editCurrentPassword);
        editNewPassword = findViewById(R.id.editNewPassword);
        editConfirmPassword = findViewById(R.id.editConfirmPassword);
        textProfileError = findViewById(R.id.textProfileError);
        textPasswordError = findViewById(R.id.textPasswordError);
        buttonSaveProfile = findViewById(R.id.buttonSaveProfile);
        buttonChangePassword = findViewById(R.id.buttonChangePassword);

        buttonSaveProfile.setOnClickListener(v -> saveProfile());
        buttonChangePassword.setOnClickListener(v -> changePassword());
        findViewById(R.id.buttonDeleteProfile).setOnClickListener(v -> confirmDelete());

        CompoundButton switchAlerts = findViewById(R.id.switchAlerts);
        switchAlerts.setChecked(Ui.transactionAlertsOn(this));
        switchAlerts.setOnCheckedChangeListener((button, on) -> Ui.setTransactionAlerts(this, on));

        executorService.execute(() -> {
            User user = databaseManager.getUserById(userId);
            boolean usesAuth0 = databaseManager.isAuth0User(userId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (user == null) {
                    session.logout();
                    Ui.goToLogin(this);
                    return;
                }
                username = user.getUsername();
                email = user.getEmail();
                auth0User = usesAuth0;
                ((TextView) findViewById(R.id.textUsername)).setText("Signed in as @" + username);
                editFirstName.setText(user.getFirstName());
                editLastName.setText(user.getLastName());
                editEmail.setText(user.getEmail());
                // Auth0 doesn't send the phone number, so new Auth0 users start with an empty box
                editPhone.setText(DatabaseManager.isPlaceholderPhone(user.getPhoneNumber())
                        ? "" : user.getPhoneNumber());
            });
        });
    }

    // ------------------------------------------------------------------
    // Profile
    // ------------------------------------------------------------------

    private void saveProfile() {
        textProfileError.setVisibility(View.GONE);
        String first = editFirstName.getText().toString().trim();
        String last = editLastName.getText().toString().trim();
        String email = editEmail.getText().toString().trim().toLowerCase(Locale.US);
        String phone = Ui.normalizePhone(editPhone.getText().toString());

        if (first.isEmpty()) { editFirstName.setError("Enter your first name"); editFirstName.requestFocus(); return; }
        if (last.isEmpty()) { editLastName.setError("Enter your last name"); editLastName.requestFocus(); return; }
        if (!Ui.isValidEmail(email)) { editEmail.setError("Enter a valid email address"); editEmail.requestFocus(); return; }
        if (phone == null) { editPhone.setError("Enter a 10-digit phone number"); editPhone.requestFocus(); return; }

        buttonSaveProfile.setEnabled(false);
        executorService.execute(() -> {
            OperationResult result = databaseManager.updateProfile(userId, first, last, email, phone);
            runOnUiThread(() -> {
                buttonSaveProfile.setEnabled(true);
                if (result.success) {
                    Ui.toast(this, result.message);
                    editPhone.setText(phone);
                } else {
                    textProfileError.setText(result.message);
                    textProfileError.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    // ------------------------------------------------------------------
    // Password
    // ------------------------------------------------------------------

    private void changePassword() {
        textPasswordError.setVisibility(View.GONE);
        if (auth0User) {
            // Auth0 keeps this password, so it's changed with Auth0's reset email
            offerPasswordResetEmail();
            return;
        }
        String current = editCurrentPassword.getText().toString();
        String next = editNewPassword.getText().toString();
        String confirm = editConfirmPassword.getText().toString();

        if (current.isEmpty()) { editCurrentPassword.setError("Enter your current password"); editCurrentPassword.requestFocus(); return; }
        if (next.length() < MIN_PASSWORD_LENGTH) {
            editNewPassword.setError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            editNewPassword.requestFocus();
            return;
        }
        if (!next.equals(confirm)) { editConfirmPassword.setError("Passwords don't match"); editConfirmPassword.requestFocus(); return; }

        buttonChangePassword.setEnabled(false);
        executorService.execute(() -> {
            OperationResult result = databaseManager.changePassword(userId, current, next);
            runOnUiThread(() -> {
                buttonChangePassword.setEnabled(true);
                if (result.success) {
                    Ui.toast(this, result.message);
                    editCurrentPassword.setText("");
                    editNewPassword.setText("");
                    editConfirmPassword.setText("");
                } else {
                    textPasswordError.setText(result.message);
                    textPasswordError.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    /** For Auth0 users: Auth0 emails a link to set a new password. */
    private void offerPasswordResetEmail() {
        new AlertDialog.Builder(this)
                .setTitle("Change your password")
                .setMessage("Your password is managed by Auth0. We'll email a link to " + email
                        + " so you can set a new one.")
                .setPositiveButton("Send email", (d, w) -> executorService.execute(() -> {
                    String message;
                    try {
                        ApiClient.requestPasswordReset(email);
                        message = "Check " + email + " for a link to change your password.";
                    } catch (Exception e) {
                        message = "The email couldn't be sent. Check your internet connection and try again.";
                    }
                    String shown = message;
                    runOnUiThread(() -> Ui.toast(this, shown));
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------
    // Delete profile
    // ------------------------------------------------------------------

    // Steps: 1. look up what would be deleted  2. if money is left, move it or cash out
    //        3. show everything that will be removed  4. password  5. delete, log out, go to login

    /** Step 1: find out what deleting would remove (in the background), then show it. */
    private void confirmDelete() {
        executorService.execute(() -> {
            ProfileDeletionCheck check = databaseManager.getProfileDeletionCheck(userId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (check.hasMoneyLeft()) {
                    askAboutMoneyLeft(check);
                } else {
                    showDeletionSummary(check, false);
                }
            });
        });
    }

    /** Step 2: there's still money in the accounts. Move it first, or cash it all out. */
    private void askAboutMoneyLeft(ProfileDeletionCheck check) {
        StringBuilder message = new StringBuilder("You still have ")
                .append(Ui.money(check.totalBalance)).append(" in your accounts:\n");
        for (Account account : check.accountsWithMoney) {
            message.append("\n• ").append(account.getDisplayName()).append(": ").append(Ui.money(account.getBalance()));
        }
        message.append("\n\nMove it to another bank or person first, or cash out everything and delete your profile.");

        Account first = check.accountsWithMoney.get(0);
        new AlertDialog.Builder(this)
                .setTitle("Money left in your accounts")
                .setMessage(message)
                .setPositiveButton("Cash out & delete", (d, w) -> showDeletionSummary(check, true))
                .setNeutralButton("Move money", (d, w) ->
                        MoveMoneyActivity.open(this, MoveMoneyActivity.MODE_TRANSFER, first.getAccountNumber()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Step 3: list everything that will be removed, so nothing is a surprise. */
    private void showDeletionSummary(ProfileDeletionCheck check, boolean cashOut) {
        StringBuilder message = new StringBuilder("This permanently deletes:\n");
        message.append("\n• Your profile and login").append(username == null ? "" : " (@" + username + ")");
        if (!check.accounts.isEmpty()) {
            message.append("\n• ").append(check.accounts.size())
                    .append(check.accounts.size() == 1 ? " bank account: " : " bank accounts: ");
            for (int i = 0; i < check.accounts.size(); i++) {
                if (i > 0) message.append(", ");
                message.append(check.accounts.get(i).getDisplayName());
            }
        }
        message.append("\n• All your transaction history");
        if (check.scheduledPayments > 0) {
            message.append("\n• ").append(check.scheduledPayments)
                    .append(check.scheduledPayments == 1 ? " scheduled bill or transfer" : " scheduled bills and transfers");
        }
        if (check.incomingTransfers > 0) {
            message.append("\n\n").append(check.incomingTransfers)
                    .append(check.incomingTransfers == 1 ? " other customer has a transfer" : " transfers from other customers are")
                    .append(" scheduled to you. ").append(check.incomingTransfers == 1 ? "It" : "They")
                    .append(" will be cancelled.");
        }
        if (cashOut) {
            message.append("\n\n").append(Ui.money(check.totalBalance)).append(" will be paid out to you.");
        }
        message.append(auth0User
                ? "\n\nThis can't be undone. You'll be logged out. Your Auth0 login isn't deleted: if you log in "
                + "again, you'll start over with a new, empty profile."
                : "\n\nThis can't be undone. You'll be logged out, and your username can be used to register again.");

        new AlertDialog.Builder(this)
                .setTitle("Delete your profile?")
                .setMessage(message)
                .setPositiveButton("Continue", (d, w) -> askForPassword(cashOut))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Step 4: confirm with the password. */
    private void askForPassword(boolean cashOut) {
        EditText passwordBox = new EditText(this);
        passwordBox.setHint("Your password");
        passwordBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        FrameLayout wrapper = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, pad / 2, pad, 0);
        wrapper.addView(passwordBox);

        new AlertDialog.Builder(this)
                .setTitle("Enter your password")
                .setMessage("To delete your profile, enter your password.")
                .setView(wrapper)
                .setPositiveButton("Delete forever", (d, w) ->
                        deleteProfile(passwordBox.getText().toString(), cashOut))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Step 5: delete, log out and go back to the login screen. */
    private void deleteProfile(String password, boolean cashOut) {
        executorService.execute(() -> {
            OperationResult result = auth0User
                    ? deleteAuth0Profile(password, cashOut)
                    : databaseManager.deleteUser(userId, password, cashOut);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result.success) {
                    session.logout();
                    Toast.makeText(getApplicationContext(), result.message, Toast.LENGTH_LONG).show();
                    Ui.goToLogin(this);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Profile not deleted")
                            .setMessage(result.message)
                            .setPositiveButton("Try again", (d, w) -> askForPassword(cashOut))
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        });
    }

    /**
     * Auth0 users: the password is checked by Auth0 (the same way the login screen does), then
     * the local profile is deleted. Runs on the background thread.
     */
    private OperationResult deleteAuth0Profile(String password, boolean cashOut) {
        try {
            ApiClient.login(email, password);
        } catch (ApiClient.MfaRequiredException passwordWasRight) {
            // Auth0 only asks for the Duo code after the password is correct, so this counts
        } catch (ApiClient.ApiException wrongPassword) {
            return OperationResult.fail("Incorrect password.");
        } catch (Exception noConnection) {
            return OperationResult.fail("Your password couldn't be checked with Auth0. "
                    + "Check your internet connection and try again.");
        }
        return databaseManager.deleteAuth0User(userId, cashOut);
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}