package com.example.onlinebanking_cs160;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.DatabaseManager;
import database.OperationResult;
import database.User;

/** Local contact profile, Auth0 password reset and notification preferences. */
public class SettingsActivity extends AppCompatActivity {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private SessionManager session;
    private long userId;

    private EditText editFirstName, editLastName, editEmail, editPhone;
    private TextView textProfileError;
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
        textProfileError = findViewById(R.id.textProfileError);
        buttonSaveProfile = findViewById(R.id.buttonSaveProfile);
        buttonChangePassword = findViewById(R.id.buttonChangePassword);

        buttonSaveProfile.setOnClickListener(v -> saveProfile());
        buttonChangePassword.setText("Send password reset email");
        buttonChangePassword.setOnClickListener(v -> sendAuth0PasswordReset());
        findViewById(R.id.buttonDeleteProfile).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Auth0 account")
                        .setMessage("Account deletion is not available in this local client. Contact your bank to delete your Auth0 account.")
                        .setPositiveButton("OK", null).show());

        CompoundButton switchAlerts = findViewById(R.id.switchAlerts);
        switchAlerts.setChecked(Ui.transactionAlertsOn(this));
        switchAlerts.setOnCheckedChangeListener((button, on) -> Ui.setTransactionAlerts(this, on));

        executorService.execute(() -> {
            User user = databaseManager.getUserById(userId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (user == null) {
                    session.logout();
                    Ui.goToLogin(this);
                    return;
                }
                ((TextView) findViewById(R.id.textUsername)).setText("Signed in as @" + user.getUsername());
                editFirstName.setText(user.getFirstName());
                editLastName.setText(user.getLastName());
                editEmail.setText(user.getEmail());
                String phone = user.getPhoneNumber();
                editPhone.setText(phone.startsWith("auth0:") ? "" : phone);
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

    private void sendAuth0PasswordReset() {
        buttonChangePassword.setEnabled(false);
        executorService.execute(() -> {
            try {
                // Use the authenticated profile, not the editable local contact email.
                ApiClient.UserProfile profile = ApiClient.getCurrentUser(session.getToken());
                ApiClient.requestPasswordReset(profile.email);
                runOnUiThread(() -> {
                    buttonChangePassword.setEnabled(true);
                    Ui.toast(this, "Check your email for Auth0 password-reset instructions.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    buttonChangePassword.setEnabled(true);
                    Ui.toast(this, "Could not send the password-reset email. Please sign in again.");
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}
