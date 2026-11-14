package com.example.onlinebanking_cs160;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Registration screen
public class RegisterActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private EditText editFullName;
    private EditText editDateOfBirth;
    private EditText editEmail;
    private EditText editPhone;
    private EditText editUsername;
    private EditText editPassword;
    private EditText editConfirmPassword;
    private Button buttonRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        // Keeps the content clear of the status bar and navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editFullName = findViewById(R.id.editFullName);
        editDateOfBirth = findViewById(R.id.editDateOfBirth);
        editEmail = findViewById(R.id.editEmail);
        editPhone = findViewById(R.id.editPhone);
        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        editConfirmPassword = findViewById(R.id.editConfirmPassword);

        buttonRegister = findViewById(R.id.buttonRegister);
        buttonRegister.setOnClickListener(v -> attemptRegister());

        // Goes back to the login screen
        Button buttonGoToLogin = findViewById(R.id.buttonGoToLogin);
        buttonGoToLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String fullName = editFullName.getText().toString().trim().replaceAll("\\s+", " ");
        String dobInput = editDateOfBirth.getText().toString().trim();
        String email = editEmail.getText().toString().trim().toLowerCase(Locale.US);
        String phoneInput = editPhone.getText().toString().trim();
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString();
        String confirmPassword = editConfirmPassword.getText().toString();

        // ---- Check the form before touching the database ----

        // Full name -> first name + last name (the database stores them separately)
        int lastSpace = fullName.lastIndexOf(' ');
        if (lastSpace <= 0) {
            editFullName.setError("Enter your first and last name");
            editFullName.requestFocus();
            return;
        }
        String firstName = fullName.substring(0, lastSpace);
        String lastName = fullName.substring(lastSpace + 1);

        String dateOfBirth = parseDateOfBirth(dobInput);
        if (dateOfBirth == null) {
            editDateOfBirth.setError("Enter a valid date as MM/DD/YYYY");
            editDateOfBirth.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Enter a valid email address");
            editEmail.requestFocus();
            return;
        }

        String phoneNumber = normalizePhone(phoneInput);
        if (phoneNumber == null) {
            editPhone.setError("Enter a 10-digit phone number");
            editPhone.requestFocus();
            return;
        }

        if (!username.matches("[A-Za-z0-9_.]{3,30}")) {
            editUsername.setError("3–30 characters: letters, numbers, _ or .");
            editUsername.requestFocus();
            return;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            editPassword.setError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            editPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            editConfirmPassword.setError("Passwords don't match");
            editConfirmPassword.requestFocus();
            return;
        }

        // ---- Save to the database on a background thread ----
        buttonRegister.setEnabled(false);
        executorService.execute(() -> {
            try {
                ApiClient.register(fullName,email,username,password,phoneNumber);
            
                runOnUiThread(() -> {
                    buttonRegister.setEnabled(true);
                    Toast.makeText(this, "Account created! You can now log in.", Toast.LENGTH_LONG).show();
                            finish(); // back to the login screen
                });
            } catch (ApiClient.ApiException e) {
                runOnUiThread(() -> {
                    buttonRegister.setEnabled(true);
                    String message = e.getMessage();
                    
                    if(message != null && message.contains("USERNAME_TAKEN")) {
                            editUsername.setError("That username is already taken");
                            editUsername.requestFocus();
                    } else if (message != null && message.contains("EMAIL_TAKEN")) {
                            editEmail.setError("An account with this email already exists");
                            editEmail.requestFocus();
                    } else if (message != null && message.contains("PHONE_TAKEN")) {
                            editPhone.setError("An account with this phone number already exists");
                            editPhone.requestFocus();
                    } else {
                        Toast.makeText(this, "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    buttonRegister.setEnabled(true);
                    Toast.makeText(this,"Couldn't connect to the backend.", Toast.LENGTH_SHORT).show();
                });
            }
            
        });
    }

    /** Turns "MM/DD/YYYY" into "YYYY-MM-DD" for the database, or returns null if it's not a real past date. */
    private String parseDateOfBirth(String input) {
        SimpleDateFormat inputFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        inputFormat.setLenient(false); // rejects dates like 02/31/2000
        try {
            if (!input.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) return null;
            Date date = inputFormat.parse(input);
            Calendar earliest = Calendar.getInstance();
            earliest.set(1900, Calendar.JANUARY, 1);
            if (date == null || date.after(new Date()) || date.before(earliest.getTime())) return null;
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
        } catch (ParseException e) {
            return null;
        }
    }

    /** Keeps only the digits, so "(555) 123-4567" and "555-123-4567" are stored the same way. */
    private String normalizePhone(String input) {
        String digits = input.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("1")) digits = digits.substring(1);
        return digits.length() == 10 ? digits : null;
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}