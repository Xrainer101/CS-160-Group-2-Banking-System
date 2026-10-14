package com.example.onlinebanking_cs160;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.onlinebanking_cs160.databinding.ActivityMainBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.DatabaseManager;

// Login screen
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private DatabaseManager databaseManager;
    private SessionManager session;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        databaseManager = DatabaseManager.getInstance(this);

        // Already logged in from last time? Skip the login screen.
        if (session.isLoggedIn()) {
            goToHome();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonLogin.setOnClickListener(v -> attemptLogin());

        binding.buttonGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        binding.textForgotPassword.setOnClickListener(v ->
                Toast.makeText(this, "Password reset isn't available yet", Toast.LENGTH_SHORT).show());

        // Keeps the content clear of the status bar and navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void attemptLogin() {
        String username = binding.editUsername.getText().toString().trim();
        String password = binding.editPassword.getText().toString();

        if (username.isEmpty()) {
            binding.editUsername.setError("Enter your username");
            binding.editUsername.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            binding.editPassword.setError("Enter your password");
            binding.editPassword.requestFocus();
            return;
        }

        // Check the database on a background thread (password hashing takes a moment)
        binding.buttonLogin.setEnabled(false);
        executorService.execute(() -> {
            DatabaseManager.LoginResult result = databaseManager.loginUser(username, password);

            runOnUiThread(() -> {
                binding.buttonLogin.setEnabled(true);
                switch (result.status) {
                    case SUCCESS:
                        session.login(result.user.getUserId());
                        goToHome();
                        break;

                    case LOCKED:
                        String time = DateFormat.getTimeFormat(this).format(new java.util.Date(result.lockedUntil));
                        Toast.makeText(this,
                                "Too many failed attempts. Try again after " + time + ".",
                                Toast.LENGTH_LONG).show();
                        binding.editPassword.setText("");
                        break;

                    case INVALID_CREDENTIALS:
                    default:
                        String message = "Incorrect username or password.";
                        if (result.attemptsLeft > 0 && result.attemptsLeft <= 2) {
                            message += " " + result.attemptsLeft
                                    + (result.attemptsLeft == 1 ? " attempt" : " attempts")
                                    + " left before your account is locked.";
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        binding.editPassword.setText("");
                        binding.editPassword.requestFocus();
                        break;
                }
            });
        });
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        // Clears the back stack so pressing Back on the home screen doesn't return to login
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}