package com.example.onlinebanking_cs160;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.DatabaseManager;
import database.User;

// Home screen shown after a successful login
public class HomeActivity extends AppCompatActivity {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goToLogin();
            return;
        }

        TextView textWelcome = findViewById(R.id.textWelcome);
        TextView textUsername = findViewById(R.id.textUsername);
        TextView textEmail = findViewById(R.id.textEmail);

        Button buttonLogout = findViewById(R.id.buttonLogout);
        buttonLogout.setOnClickListener(v -> {
            session.logout();
            goToLogin();
        });

        // Load the logged-in user's details from the database
        long userId = session.getUserId();
        DatabaseManager databaseManager = DatabaseManager.getInstance(this);
        executorService.execute(() -> {
            User user = databaseManager.getUserById(userId);
            runOnUiThread(() -> {
                if (user == null) {
                    // The saved session points to a user that no longer exists (e.g. the database was reset)
                    session.logout();
                    goToLogin();
                    return;
                }
                textWelcome.setText("Hi, " + user.getFirstName());
                textUsername.setText("@" + user.getUsername());
                textEmail.setText(user.getEmail());
            });
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
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