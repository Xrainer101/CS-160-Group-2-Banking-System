package com.example.onlinebanking_cs160;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
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

/** Edit profile, change password, notification preference and delete profile. */
public class SettingsActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private SessionManager session;
    private long userId;

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
                editPhone.setText(user.getPhoneNumber());
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

    // ------------------------------------------------------------------
    // Delete profile
    // ------------------------------------------------------------------

    private void confirmDelete() {
        EditText passwordBox = new EditText(this);
        passwordBox.setHint("Your password");
        passwordBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        FrameLayout wrapper = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, pad / 2, pad, 0);
        wrapper.addView(passwordBox);

        new AlertDialog.Builder(this)
                .setTitle("Delete your profile?")
                .setMessage("This permanently deletes your profile, accounts and transaction history. "
                        + "It can't be undone. Enter your password to confirm.")
                .setView(wrapper)
                .setPositiveButton("Delete", (d, w) -> deleteProfile(passwordBox.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteProfile(String password) {
        executorService.execute(() -> {
            OperationResult result = databaseManager.deleteUser(userId, password);
            runOnUiThread(() -> {
                if (result.success) {
                    Ui.toast(this, result.message);
                    session.logout();
                    Ui.goToLogin(this);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Profile not deleted")
                            .setMessage(result.message)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}
