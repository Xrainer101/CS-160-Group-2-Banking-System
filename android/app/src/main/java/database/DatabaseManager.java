package database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import com.example.onlinebanking_cs160.PasswordUtils;

public class DatabaseManager extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "online_bank.db";
    private static final int DATABASE_VERSION = 4;
    private static final String TABLE_USERS = "users";
    private static final String TABLE_BANK_ACCOUNTS = "bank_accounts";
    private static final String COL_USER_ID = "user_id";
    private static final String COL_USERNAME = "username";
    private static final String COL_FIRST_NAME = "first_name";
    private static final String COL_LAST_NAME = "last_name";
    private static final String COL_DATE_OF_BIRTH = "date_of_birth";
    private static final String COL_EMAIL = "email";
    private static final String COL_PHONE_NUMBER = "phone_number";
    private static final String COL_PASSWORD_HASH = "password_hash";
    private static final String COL_ROLE = "role";
    private static final String COL_FAILED_LOGIN_ATTEMPTS = "login_attempts";
    private static final String COL_LOCKOUT_UNTIL = "lockout_until";
    private static final String COL_LAST_LOGIN_AT = "last_login_at";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_ACCOUNT_ID = "account_id";
    private static final String COL_ACCOUNT_NUMBER = "account_number";
    private static final String COL_ACCOUNT_TYPE = "account_type";
    private static final String COL_BALANCE = "balance";
    private static final String COL_IS_ACTIVE = "is_active";

    // Lock the account for 15 minutes after 5 wrong passwords in a row
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MILLIS = 15 * 60 * 1000L;

    // COLLATE NOCASE makes "Jane" and "jane" count as the same username/email
    private static final String CREATE_USERS = "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " ("
            + COL_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_USERNAME + " TEXT NOT NULL UNIQUE COLLATE NOCASE, "
            + COL_FIRST_NAME + " TEXT NOT NULL, "
            + COL_LAST_NAME + " TEXT NOT NULL, "
            + COL_DATE_OF_BIRTH + " TEXT, "
            + COL_EMAIL + " TEXT NOT NULL UNIQUE COLLATE NOCASE, "
            + COL_PHONE_NUMBER + " TEXT NOT NULL UNIQUE, "
            + COL_PASSWORD_HASH + " TEXT NOT NULL, "
            + COL_ROLE + " TEXT NOT NULL DEFAULT 'customer' CHECK(role IN ('customer', 'admin', 'manager')), "
            + COL_FAILED_LOGIN_ATTEMPTS + " INTEGER NOT NULL DEFAULT 0, "
            // Stored as milliseconds since 1970 (System.currentTimeMillis()), NULL when not locked
            + COL_LOCKOUT_UNTIL + " DATETIME DEFAULT NULL, "
            // Time of the most recent successful login, in milliseconds since 1970
            + COL_LAST_LOGIN_AT + " INTEGER DEFAULT NULL, "
            + COL_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP "
            + ");";

    private static final String CREATE_BANK_ACCOUNTS = "CREATE TABLE IF NOT EXISTS " + TABLE_BANK_ACCOUNTS + " ("
            + COL_ACCOUNT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_USER_ID + " INTEGER NOT NULL, "
            + COL_ACCOUNT_NUMBER + " TEXT NOT NULL UNIQUE, "
            + COL_ACCOUNT_TYPE + " TEXT NOT NULL DEFAULT 'checking' CHECK(account_type IN ('checking', 'savings')), "
            + COL_BALANCE + " REAL NOT NULL DEFAULT 0.00, "
            + COL_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, "
            + COL_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE"
            + ");";

    private static DatabaseManager instance;

    /** One shared database connection for the whole app. Use this instead of "new DatabaseManager(...)". */
    public static synchronized DatabaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseManager(context.getApplicationContext());
        }
        return instance;
    }

    public DatabaseManager(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // SQLite ignores FOREIGN KEY / ON DELETE CASCADE unless this is turned on
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USERS);
        db.execSQL(CREATE_BANK_ACCOUNTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 3) {
            // Coming from version 3: just add the new column and keep everyone's accounts
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COL_LAST_LOGIN_AT + " INTEGER DEFAULT NULL");
            return;
        }
        // Anything older: wipe and rebuild (development only).
        // Drop the child table first because of the foreign key.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BANK_ACCOUNTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    public enum RegisterResult { SUCCESS, USERNAME_TAKEN, EMAIL_TAKEN, PHONE_TAKEN, ERROR }

    /**
     * Saves a new customer. The password is hashed before it is stored; the plain
     * password never goes into the database. Call this from a background thread.
     */
    public RegisterResult registerUser(String username, String firstName, String lastName,
                                       String dateOfBirth, String email, String phoneNumber,
                                       String password) {
        // Check each unique field first so the screen can say exactly which one is taken
        if (exists(COL_USERNAME, username)) return RegisterResult.USERNAME_TAKEN;
        if (exists(COL_EMAIL, email)) return RegisterResult.EMAIL_TAKEN;
        if (exists(COL_PHONE_NUMBER, phoneNumber)) return RegisterResult.PHONE_TAKEN;

        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);
        values.put(COL_FIRST_NAME, firstName);
        values.put(COL_LAST_NAME, lastName);
        values.put(COL_DATE_OF_BIRTH, dateOfBirth);
        values.put(COL_EMAIL, email);
        values.put(COL_PHONE_NUMBER, phoneNumber);
        values.put(COL_PASSWORD_HASH, PasswordUtils.hashPassword(password));
        // role, login_attempts, lockout_until and created_at use their DEFAULT values

        SQLiteDatabase db = getWritableDatabase();
        // A transaction makes the user + their first account save together, or not at all
        db.beginTransaction();
        try {
            long userId = db.insertOrThrow(TABLE_USERS, null, values);
            // Every new customer starts with a checking account at $0.00
            insertAccount(db, userId, Account.TYPE_CHECKING);
            db.setTransactionSuccessful();
            return RegisterResult.SUCCESS;
        } catch (SQLiteConstraintException e) {
            // Someone registered the same details between the check above and this insert
            return RegisterResult.USERNAME_TAKEN;
        } catch (Exception e) {
            return RegisterResult.ERROR;
        } finally {
            db.endTransaction();
        }
    }

    private boolean exists(String column, String value) {
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_USERS, new String[]{COL_USER_ID},
                column + " = ?", new String[]{value},
                null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    public static class LoginResult {
        public enum Status { SUCCESS, INVALID_CREDENTIALS, LOCKED }

        public final Status status;
        public final User user;            // set when status == SUCCESS
        public final long lockedUntil;     // set when status == LOCKED (millis since 1970)
        public final int attemptsLeft;     // set when status == INVALID_CREDENTIALS and the username exists
        public final long previousLogin;   // set when status == SUCCESS: the login before this one, 0 if first time

        private LoginResult(Status status, User user, long lockedUntil, int attemptsLeft) {
            this(status, user, lockedUntil, attemptsLeft, 0);
        }

        private LoginResult(Status status, User user, long lockedUntil, int attemptsLeft, long previousLogin) {
            this.status = status;
            this.user = user;
            this.lockedUntil = lockedUntil;
            this.attemptsLeft = attemptsLeft;
            this.previousLogin = previousLogin;
        }
    }

    /**
     * Checks a username + password against the database. Tracks failed attempts and
     * locks the account after too many wrong passwords. Call this from a background thread.
     */
    public LoginResult loginUser(String username, String password) {
        SQLiteDatabase db = getWritableDatabase();

        long userId;
        String storedHash;
        int failedAttempts;
        long lockoutUntil;
        long previousLogin;

        try (Cursor cursor = db.query(TABLE_USERS,
                new String[]{COL_USER_ID, COL_PASSWORD_HASH, COL_FAILED_LOGIN_ATTEMPTS, COL_LOCKOUT_UNTIL,
                        COL_LAST_LOGIN_AT},
                COL_USERNAME + " = ?", new String[]{username},
                null, null, null)) {

            if (!cursor.moveToFirst()) {
                // No such username. Same message as a wrong password so nobody can tell which usernames exist.
                return new LoginResult(LoginResult.Status.INVALID_CREDENTIALS, null, 0, -1);
            }
            userId = cursor.getLong(0);
            storedHash = cursor.getString(1);
            failedAttempts = cursor.getInt(2);
            lockoutUntil = cursor.isNull(3) ? 0 : cursor.getLong(3);
            previousLogin = cursor.isNull(4) ? 0 : cursor.getLong(4);
        }

        long now = System.currentTimeMillis();
        if (lockoutUntil > now) {
            return new LoginResult(LoginResult.Status.LOCKED, null, lockoutUntil, 0);
        }

        ContentValues update = new ContentValues();
        String[] whereArgs = {String.valueOf(userId)};

        if (PasswordUtils.verifyPassword(password, storedHash)) {
            // Correct password: reset the counter, clear any old lock, and record this login
            update.put(COL_FAILED_LOGIN_ATTEMPTS, 0);
            update.putNull(COL_LOCKOUT_UNTIL);
            update.put(COL_LAST_LOGIN_AT, now);
            db.update(TABLE_USERS, update, COL_USER_ID + " = ?", whereArgs);
            return new LoginResult(LoginResult.Status.SUCCESS, getUserById(userId), 0, 0, previousLogin);
        }

        // Wrong password. If an earlier lock has expired, start counting from zero again.
        if (lockoutUntil != 0) failedAttempts = 0;
        failedAttempts++;

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            long until = now + LOCKOUT_MILLIS;
            update.put(COL_FAILED_LOGIN_ATTEMPTS, failedAttempts);
            update.put(COL_LOCKOUT_UNTIL, until);
            db.update(TABLE_USERS, update, COL_USER_ID + " = ?", whereArgs);
            return new LoginResult(LoginResult.Status.LOCKED, null, until, 0);
        }

        update.put(COL_FAILED_LOGIN_ATTEMPTS, failedAttempts);
        update.putNull(COL_LOCKOUT_UNTIL);
        db.update(TABLE_USERS, update, COL_USER_ID + " = ?", whereArgs);
        return new LoginResult(LoginResult.Status.INVALID_CREDENTIALS, null, 0,
                MAX_FAILED_ATTEMPTS - failedAttempts);
    }

    // ------------------------------------------------------------------
    // Bank accounts
    // ------------------------------------------------------------------

    /** All open accounts for a user, checking first. Call this from a background thread. */
    public java.util.List<Account> getAccountsForUser(long userId) {
        java.util.List<Account> accounts = new java.util.ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_BANK_ACCOUNTS,
                new String[]{COL_ACCOUNT_ID, COL_ACCOUNT_NUMBER, COL_ACCOUNT_TYPE, COL_BALANCE},
                COL_USER_ID + " = ? AND " + COL_IS_ACTIVE + " = 1", new String[]{String.valueOf(userId)},
                null, null,
                // 'checking' sorts before 'savings'; oldest account first within each type
                COL_ACCOUNT_TYPE + " ASC, " + COL_ACCOUNT_ID + " ASC")) {
            while (cursor.moveToNext()) {
                accounts.add(new Account(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getDouble(3)));
            }
        }
        return accounts;
    }

    /** Opens a new $0.00 account ("checking" or "savings"). Returns null if it couldn't be saved. */
    @Nullable
    public database.Account openAccount(long userId, String accountType) {
        try {
            return insertAccount(getWritableDatabase(), userId, accountType);
        } catch (Exception e) {
            return null;
        }
    }

    private Account insertAccount(SQLiteDatabase db, long userId, String accountType) {
        // Try a few random numbers in case one is already used (very unlikely)
        for (int attempt = 0; attempt < 5; attempt++) {
            String accountNumber = generateAccountNumber();
            ContentValues values = new ContentValues();
            values.put(COL_USER_ID, userId);
            values.put(COL_ACCOUNT_NUMBER, accountNumber);
            values.put(COL_ACCOUNT_TYPE, accountType);
            // balance (0.00), is_active (1) and created_at use their DEFAULT values
            try {
                long accountId = db.insertOrThrow(TABLE_BANK_ACCOUNTS, null, values);
                return new database.Account(accountId, accountNumber, accountType, 0.0);
            } catch (SQLiteConstraintException duplicateNumber) {
                // pick another number and try again
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }

    /** A random 10-digit account number that doesn't start with 0. */
    private static String generateAccountNumber() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder number = new StringBuilder();
        number.append(1 + random.nextInt(9));
        for (int i = 1; i < 10; i++) number.append(random.nextInt(10));
        return number.toString();
    }

    // ------------------------------------------------------------------
    // Reading users
    // ------------------------------------------------------------------

    /** Returns the user with this id, or null if they don't exist. */
    @Nullable
    public User getUserById(long userId) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_USERS,
                new String[]{COL_USER_ID, COL_USERNAME, COL_FIRST_NAME, COL_LAST_NAME,
                        COL_DATE_OF_BIRTH, COL_EMAIL, COL_PHONE_NUMBER, COL_ROLE},
                COL_USER_ID + " = ?", new String[]{String.valueOf(userId)},
                null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            return new User(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getString(4),
                    cursor.getString(5),
                    cursor.getString(6),
                    cursor.getString(7));
        }
    }
}