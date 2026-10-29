package com.example.onlinebanking_cs160;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.onlinebanking_cs160.databinding.ActivityMainBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Login screen
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private SessionManager session;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);

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
            try {
                String token = ApiClient.login(username, password); // calls backend login method from Android
                runOnUiThread(() -> {
                    binding.buttonLogin.setEnabled(true);
                    // saves the backend JWT token
                    session.login(token);
                    goToHome();
                });
            } catch (ApiClient.ApiException e) {
                runOnUiThread(() -> {
                    binding.buttonLogin.setEnabled(true);

                    if (e.getStatusCode() == 423){
                        Toast.makeText(this,"Account temporarily locked.",Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,"Incorrect username or password.",Toast.LENGTH_LONG).show();
                    }
                    binding.editPassword.setText("");
                    binding.editPassword.requestFocus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.buttonLogin.setEnabled(true);
                    Toast.makeText(this,"Could not connect to the backend.",Toast.LENGTH_LONG).show();
                });
            }
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