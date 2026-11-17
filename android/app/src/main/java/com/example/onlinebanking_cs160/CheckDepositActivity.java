package com.example.onlinebanking_cs160;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.Account;
import database.DatabaseManager;
import database.OperationResult;

/**
 * Mobile check deposit: the user photographs a check (or picks a photo), Google ML Kit reads the
 * text on the phone (no internet needed), CheckParser picks out the amount / check number /
 * routing / account numbers, the user checks them, and the money is deposited.
 */
public class CheckDepositActivity extends AppCompatActivity {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DatabaseManager databaseManager;
    private long userId;
    private List<Account> accounts = new ArrayList<>();

    private TextRecognizer recognizer;
    private Uri photoUri; // where the camera saves the photo

    private ImageView imageCheck;
    private TextView textScanStatus;
    private Spinner spinnerAccount;
    private EditText editAmount;
    private EditText editCheckNumber;
    private EditText editCheckRouting;
    private EditText editCheckAccount;
    private TextView textError;
    private Button buttonDeposit;

    // Camera: saves a full-size photo to photoUri
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), saved -> {
                if (Boolean.TRUE.equals(saved) && photoUri != null) readCheck(photoUri);
            });

    // Photo picker (handy on the emulator: drag a check image onto it, then choose it here)
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) readCheck(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_check_deposit);
        Ui.setUpScreen(this, "Deposit Check");

        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            Ui.goToLogin(this);
            return;
        }
        userId = session.getUserId();
        databaseManager = DatabaseManager.getInstance(this);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        imageCheck = findViewById(R.id.imageCheck);
        textScanStatus = findViewById(R.id.textScanStatus);
        spinnerAccount = findViewById(R.id.spinnerAccount);
        editAmount = findViewById(R.id.editAmount);
        editCheckNumber = findViewById(R.id.editCheckNumber);
        editCheckRouting = findViewById(R.id.editCheckRouting);
        editCheckAccount = findViewById(R.id.editCheckAccount);
        textError = findViewById(R.id.textError);
        buttonDeposit = findViewById(R.id.buttonDeposit);

        findViewById(R.id.buttonTakePhoto).setOnClickListener(v -> takePhoto());
        findViewById(R.id.buttonChoosePhoto).setOnClickListener(v -> pickPhoto.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        buttonDeposit.setOnClickListener(v -> onDeposit());

        loadAccounts();
    }

    private void loadAccounts() {
        executorService.execute(() -> {
            List<Account> loaded = databaseManager.getAccountsForUser(userId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                accounts = loaded;
                List<String> labels = new ArrayList<>();
                for (Account a : accounts) labels.add(Ui.accountLabel(a));
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner, labels);
                adapter.setDropDownViewResource(R.layout.item_spinner);
                spinnerAccount.setAdapter(adapter);
                if (accounts.isEmpty()) {
                    showError("Open an account first so there's somewhere to deposit the check.");
                    buttonDeposit.setEnabled(false);
                }
            });
        });
    }

    // ------------------------------------------------------------------
    // Photo -> text -> numbers
    // ------------------------------------------------------------------

    private void takePhoto() {
        try {
            File folder = new File(getCacheDir(), "checks");
            if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("no folder");
            File photo = new File(folder, "check_" + System.currentTimeMillis() + ".jpg");
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            takePicture.launch(photoUri);
        } catch (Exception e) {
            showError("The camera couldn't be opened. Use Choose photo instead.");
        }
    }

    private void readCheck(Uri uri) {
        textError.setVisibility(View.GONE);
        imageCheck.setImageURI(uri);
        imageCheck.setVisibility(View.VISIBLE);
        setStatus("Reading the check…");

        InputImage image;
        try {
            image = InputImage.fromFilePath(this, uri);
        } catch (Exception e) {
            setStatus("That photo couldn't be opened. Try another one, or type the details below.");
            return;
        }
        recognizer.process(image)
                .addOnSuccessListener(this::fillFromText)
                .addOnFailureListener(e ->
                        setStatus("The check couldn't be read. Try a clearer photo, or type the details below."));
    }

    private void fillFromText(Text text) {
        List<String> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) lines.add(line.getText());
        }
        CheckParser.Result found = CheckParser.parse(lines);
        if (!found.foundAnything()) {
            setStatus("No amount or numbers were found. Try a clearer photo, or type the details below.");
            return;
        }
        List<String> parts = new ArrayList<>();
        if (found.amount != null) {
            editAmount.setText(String.format(Locale.US, "%.2f", found.amount));
            parts.add("amount " + Ui.money(found.amount));
        }
        if (found.checkNumber != null) {
            editCheckNumber.setText(found.checkNumber);
            parts.add("check #" + found.checkNumber);
        }
        if (found.routingNumber != null) {
            editCheckRouting.setText(found.routingNumber);
            parts.add("routing " + found.routingNumber);
        }
        if (found.accountNumber != null) {
            editCheckAccount.setText(found.accountNumber);
            parts.add("account " + Account.maskNumber(found.accountNumber));
        }
        setStatus("Found " + String.join(", ", parts) + ". Please check these below — "
                + (found.amount == null ? "and enter the amount." : "fix anything that's wrong."));
    }

    private void setStatus(String message) {
        textScanStatus.setText(message);
        textScanStatus.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // Deposit
    // ------------------------------------------------------------------

    private void onDeposit() {
        textError.setVisibility(View.GONE);
        int i = spinnerAccount.getSelectedItemPosition();
        if (i < 0 || i >= accounts.size()) {
            showError("Choose the account to deposit into.");
            return;
        }
        Account account = accounts.get(i);
        double amount = Ui.parseAmount(editAmount.getText().toString());
        String problem = Double.isNaN(amount) ? "Enter the amount on the check, like 1250.00"
                : DatabaseManager.checkAmount(amount);
        if (problem != null) {
            editAmount.setError(problem);
            editAmount.requestFocus();
            return;
        }
        String checkNumber = editCheckNumber.getText().toString().trim();
        String routing = editCheckRouting.getText().toString().trim();
        String checkAccount = editCheckAccount.getText().toString().trim();
        // Check number, routing number and account number are all required, so the same check
        // can't be deposited twice. Show the problem on the first box that's missing or wrong.
        if (showProblem(editCheckNumber, DatabaseManager.checkCheckNumber(checkNumber))) return;
        if (showProblem(editCheckRouting, DatabaseManager.checkCheckRouting(routing))) return;
        if (showProblem(editCheckAccount, DatabaseManager.checkCheckAccount(checkAccount))) return;

        new AlertDialog.Builder(this)
                .setTitle("Deposit this check?")
                .setMessage(Ui.money(amount) + (checkNumber.isEmpty() ? "" : " (check #" + checkNumber + ")")
                        + " into your " + account.getDisplayName() + ".")
                .setPositiveButton("Deposit", (d, w) -> deposit(account, amount, checkNumber, routing, checkAccount))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deposit(Account account, double amount, String checkNumber, String routing, String checkAccount) {
        buttonDeposit.setEnabled(false);
        executorService.execute(() -> {
            OperationResult result = databaseManager.depositCheck(userId, account.getAccountNumber(), amount,
                    checkNumber, routing, checkAccount);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                buttonDeposit.setEnabled(true);
                if (!result.success) {
                    showError(result.message);
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Check deposited")
                        .setMessage(result.message + "\n\nNew " + account.getDisplayName() + " balance: "
                                + Ui.money(result.newBalance) + "\n\nKeep the paper check for 14 days, then destroy it.")
                        .setCancelable(false)
                        .setPositiveButton("OK", (d, w) -> finish())
                        .show();
            });
        });
    }

    /** If there's a problem, shows it on the box and returns true. */
    private boolean showProblem(EditText box, String problem) {
        if (problem == null) return false;
        box.setError(problem);
        box.requestFocus();
        return true;
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        if (recognizer != null) recognizer.close();
        super.onDestroy();
    }
}