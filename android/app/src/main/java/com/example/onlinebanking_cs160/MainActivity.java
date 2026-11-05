package com.example.onlinebanking_cs160;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.onlinebanking_cs160.databinding.ActivityMainBinding;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

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

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonLogin.setOnClickListener(v -> attemptLogin());

        binding.buttonGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        binding.textForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

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
                ApiClient.LoginResult result = ApiClient.login(username, password);
                runOnUiThread(() -> {
                    binding.buttonLogin.setEnabled(true);
                    session.login(result.accessToken, username);
                    goToHome();
                });
            } catch (ApiClient.MfaRequiredException e) { // Multi-factor authentication is required for this account
                runOnUiThread(() -> handleMfaEnrollment(e.mfaToken, username));
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

    // Handles MFA enrollment for a user 
    private void handleMfaEnrollment(String mfaToken, String username) {
        executorService.execute(() -> {
            try {
                ApiClient.MfaEnrollment enrollment = ApiClient.startTotpEnrollment(mfaToken);
                runOnUiThread(() -> showTotpEnrollmentDialog(mfaToken, username, enrollment.barcodeUri));
            } catch (ApiClient.ApiException e) {
                runOnUiThread(() -> showMfaCodeDialog(mfaToken, username));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.buttonLogin.setEnabled(true);
                    Toast.makeText(this, "Could not start MFA enrollment.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // Shows the email form for forgot password
    private void showForgotPasswordDialog() {
        EditText emailInput = new EditText(this);
        emailInput.setHint("Enter your email");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setSingleLine(true);

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(24, 8, 24, 8);
        dialogLayout.addView(emailInput);

        new AlertDialog.Builder(this)
            .setTitle("Reset your password")
            .setMessage("Enter your email, click SUBMIT, and check your inbox for reset instructions.")
            .setView(dialogLayout)
            .setNegativeButton("Back to sign in ", null)
            .setPositiveButton("Submit", (dialog, which) -> sendPasswordReset(emailInput.getText().toString().trim()))
            .show();
    }

    // Sends the email to auth0 and shows the result to the user
    private void sendPasswordReset(String email) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email address.", Toast.LENGTH_LONG).show();
            return;
        }
        executorService.execute(() -> {
            try {
                ApiClient.requestPasswordReset(email);
                runOnUiThread(() -> Toast.makeText(this,"If an account exists, check your email for reset instructions.",Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,"Could not send the password-reset email.",Toast.LENGTH_LONG).show());
            }
        });
    }

    // Shows auth0's QR code so the user can add this account to Duo Mobile.
    private void showTotpEnrollmentDialog(String mfaToken, String username, String barcodeUri) {
        try {
            ImageView qrCode = new ImageView(this);
            qrCode.setImageBitmap(createQrCode(barcodeUri));

            EditText otpInput = new EditText(this);
            otpInput.setHint("First six-digit Duo code");
            otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            otpInput.setSingleLine(true);

            LinearLayout dialogLayout = new LinearLayout(this);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setPadding(24, 8, 24, 8);
            dialogLayout.addView(qrCode);
            dialogLayout.addView(otpInput);

            String instructions =
                "\n1. Download and open the Duo Mobile app.\n\n"
                + "2. Tap Add + , and select \"Use QR Code\".\n\n"
                + "3. Scan the QR code below. Name account.\n\n"
                + "4. Enter the six-digit code from Duo Mobile.";

            new AlertDialog.Builder(this)
                .setTitle("Verify with Duo Mobile")
                .setMessage(instructions)
                .setView(dialogLayout)
                .setNegativeButton("Cancel", (dialog, which) -> binding.buttonLogin.setEnabled(true))
                .setPositiveButton("Verify", (dialog, which) -> completeMfaLogin(mfaToken, username, otpInput.getText().toString().trim()))
                .setOnCancelListener(dialog -> binding.buttonLogin.setEnabled(true))
                .show();
        } catch (Exception e) {
            binding.buttonLogin.setEnabled(true);
            Toast.makeText(this, "Could not display the MFA QR code.", Toast.LENGTH_LONG).show();
        }
    }

    // Converts auth0's barcode URI into a QR image for the Android app to display to the user for duo enrollment
    private Bitmap createQrCode(String barcodeUri) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(barcodeUri,BarcodeFormat.QR_CODE,600,600);
        Bitmap bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.RGB_565);
        for (int x = 0; x < 600; x++) {
            for (int y = 0; y < 600; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    // Shows directions for the user to enter their Duo code for multi-factor authentication
    private void showMfaCodeDialog(String mfaToken, String username) {
        EditText otpInput = new EditText(this);
        otpInput.setHint(" Enter the 6-digit Duo code");
        otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        otpInput.setSingleLine(true);

        new AlertDialog.Builder(this)
            .setTitle("Verify with Duo Mobile")
            .setMessage("Enter the six-digit code from Duo Mobile.")
            .setView(otpInput)
            .setNegativeButton("Cancel", (dialog, which) -> binding.buttonLogin.setEnabled(true))
            .setPositiveButton("Verify", (dialog, which) -> {
                String otp = otpInput.getText().toString().trim();
                completeMfaLogin(mfaToken, username, otp);
            })
            .setOnCancelListener(dialog -> binding.buttonLogin.setEnabled(true))
            .show();   
    }

    // Completes the multi-factor authentication login with the Duo code
    private void completeMfaLogin(String mfaToken, String username, String otp) {
        if (otp.length() != 6) {
            binding.buttonLogin.setEnabled(true);
            Toast.makeText(this, "Enter the six-digit Duo code.", Toast.LENGTH_LONG).show();
            return;
        }

        // Complete multi-factor authentication login
        executorService.execute(() -> {
            try {
                ApiClient.LoginResult result = ApiClient.completeMfaLogin(mfaToken, otp);
                runOnUiThread(() -> {
                    binding.buttonLogin.setEnabled(true);
                    session.login(result.accessToken, username);
                    goToHome();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.buttonLogin.setEnabled(true);
                    Toast.makeText(this, "The Duo code was invalid or expired.", Toast.LENGTH_LONG).show();
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
