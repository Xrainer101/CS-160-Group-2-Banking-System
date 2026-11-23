package database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.security.SecureRandom;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Everything that touches the app's SQLite database.
 *
 * The bank-account, transaction, deposit, withdrawal, transfer and close-account logic is ported
 * from the team's desktop DatabaseManager (the JDBC version in the CS-160-Group-2-Banking-System
 * repo) so the Android app follows the same rules:
 *  - amounts must be greater than zero
 *  - only "active" accounts can send or receive money
 *  - withdrawals and transfers need enough money in the account
 *  - every money movement is saved in the transactions table
 *  - a transfer's two halves happen inside one database transaction (all or nothing)
 *
 * All public methods do database work, so call them from a background thread.
 */
public class DatabaseManager extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "online_bank.db";
    // 3: users.date_of_birth
    // 4: users.last_login_at
    // 5: transactions + scheduled_payments tables, bank_accounts.routing_number/status/closed_at,
    //    users.card_frozen
    // 6: scheduled_payments.frequency/repeat_day for monthly bills
    // 7: scheduled_payments can repeat daily/weekly/monthly/yearly, every N (repeat_every)
    // 8: cards table (real card numbers, replacement), deposited_checks table (no double deposits),
    //    scheduled_payments.kind/to_account_number/to_routing_number/memo (scheduled transfers)
    // 9: debit cards removed (cards table dropped; freezing went with them)
    //    (upgrades from 3 and up keep existing data, see onUpgrade)
    private static final int DATABASE_VERSION = 9;

    /** The bank's routing number, same as BANK_ROUTING_NUMBER in the team's backend. */
    public static final String BANK_ROUTING_NUMBER = "123456789";

    /** Largest single deposit / withdrawal / transfer / bill payment the app accepts. */
    public static final double MAX_TRANSACTION_AMOUNT = 100_000.00;

    private static final String TABLE_USERS = "users";
    private static final String TABLE_BANK_ACCOUNTS = "bank_accounts";
    private static final String TABLE_TRANSACTIONS = "transactions";
    private static final String TABLE_SCHEDULED_PAYMENTS = "scheduled_payments";
    private static final String TABLE_DEPOSITED_CHECKS = "deposited_checks";

    // users
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

    // bank_accounts
    private static final String COL_ACCOUNT_ID = "account_id";
    private static final String COL_ACCOUNT_NUMBER = "account_number";
    private static final String COL_ROUTING_NUMBER = "routing_number";
    private static final String COL_ACCOUNT_TYPE = "account_type";
    private static final String COL_BALANCE = "balance";
    private static final String COL_STATUS = "status";
    private static final String COL_CLOSED_AT = "closed_at";

    // transactions
    private static final String COL_TRANSACTION_ID = "transaction_id";
    private static final String COL_TRANSACTION_TYPE = "transaction_type";
    private static final String COL_AMOUNT = "amount";
    private static final String COL_RELATED_ACCOUNT = "related_account";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_TIMESTAMP = "timestamp";

    // scheduled_payments
    private static final String COL_PAYMENT_ID = "payment_id";
    private static final String COL_PAYEE = "payee";
    private static final String COL_DUE_DATE = "due_date";
    private static final String COL_PAYMENT_STATUS = "status";
    private static final String COL_FAILURE_REASON = "failure_reason";
    private static final String COL_PAID_AT = "paid_at";
    private static final String COL_FREQUENCY = "frequency";
    private static final String COL_REPEAT_DAY = "repeat_day";
    private static final String COL_REPEAT_EVERY = "repeat_every";
    private static final String COL_KIND = "kind";
    private static final String COL_TO_ACCOUNT_NUMBER = "to_account_number";
    private static final String COL_TO_ROUTING_NUMBER = "to_routing_number";
    private static final String COL_MEMO = "memo";

    // deposited_checks
    private static final String COL_CHECK_ID = "check_id";
    private static final String COL_CHECK_KEY = "check_key";
    private static final String COL_CHECK_NUMBER = "check_number";
    private static final String COL_DEPOSITED_AT = "deposited_at";

    /** Safety limit for catching up on missed repeating payments in one run. */
    private static final int MAX_PAYMENTS_PER_RUN = 1000;

    /** Allowed values of scheduled_payments.frequency, as SQL for the CHECK constraint. */
    private static final String FREQUENCY_CHECK =
            "CHECK(frequency IN ('once', 'daily', 'weekly', 'monthly', 'yearly'))";

    /** Allowed values of scheduled_payments.kind. */
    private static final String KIND_CHECK = "CHECK(kind IN ('bill', 'transfer'))";

    // Lock the login for 15 minutes after 5 wrong passwords in a row
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

    // Same columns as the team backend's bank_accounts table
    private static final String CREATE_BANK_ACCOUNTS = "CREATE TABLE IF NOT EXISTS " + TABLE_BANK_ACCOUNTS + " ("
            + COL_ACCOUNT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_USER_ID + " INTEGER NOT NULL, "
            + COL_ACCOUNT_NUMBER + " TEXT NOT NULL UNIQUE, "
            + COL_ROUTING_NUMBER + " TEXT NOT NULL DEFAULT '" + BANK_ROUTING_NUMBER + "', "
            + COL_ACCOUNT_TYPE + " TEXT NOT NULL DEFAULT 'checking' CHECK(account_type IN ('checking', 'savings')), "
            + COL_BALANCE + " REAL NOT NULL DEFAULT 0.00, "
            + COL_STATUS + " TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active', 'frozen', 'closed')), "
            + COL_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + COL_CLOSED_AT + " INTEGER DEFAULT NULL, "
            + "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE"
            + ");";

    // Same as the team backend's transactions table, plus a description (shown in the activity
    // list) and a 'bill_payment' type for the Pay Bills screen. timestamp = millis since 1970.
    // A transfer to another bank is a 'transfer_out' whose related_account is "routing:account".
    private static final String CREATE_TRANSACTIONS = "CREATE TABLE IF NOT EXISTS " + TABLE_TRANSACTIONS + " ("
            + COL_TRANSACTION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_ACCOUNT_NUMBER + " TEXT NOT NULL, "
            + COL_TRANSACTION_TYPE + " TEXT NOT NULL CHECK(transaction_type IN "
            + "('deposit', 'withdrawal', 'transfer_in', 'transfer_out', 'bill_payment')), "
            + COL_AMOUNT + " REAL NOT NULL, "
            + COL_RELATED_ACCOUNT + " TEXT DEFAULT NULL, "
            + COL_DESCRIPTION + " TEXT, "
            + COL_TIMESTAMP + " INTEGER NOT NULL, "
            + "FOREIGN KEY (account_number) REFERENCES bank_accounts(account_number) ON DELETE CASCADE"
            + ");";

    private static final String CREATE_TRANSACTIONS_INDEX = "CREATE INDEX IF NOT EXISTS idx_transactions_account "
            + "ON " + TABLE_TRANSACTIONS + " (" + COL_ACCOUNT_NUMBER + ", " + COL_TIMESTAMP + ")";

    // Bills and transfers scheduled for a date. due_date is "yyyy-MM-dd".
    // kind = 'bill' pays payee; kind = 'transfer' sends money to to_account_number (at this bank when
    // to_routing_number is NULL, otherwise at the bank with that routing number).
    // A repeating one (frequency other than 'once') stays 'scheduled' after each payment and its
    // due_date moves forward by repeat_every days/weeks/months/years. For monthly and yearly ones,
    // repeat_day remembers the day of the month it was first set for (so the 31st stays the 31st).
    private static final String CREATE_SCHEDULED_PAYMENTS = createScheduledPaymentsSql(TABLE_SCHEDULED_PAYMENTS);

    private static String createScheduledPaymentsSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + COL_PAYMENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_USER_ID + " INTEGER NOT NULL, "
                + COL_ACCOUNT_NUMBER + " TEXT NOT NULL, "
                + COL_PAYEE + " TEXT NOT NULL, "
                + COL_AMOUNT + " REAL NOT NULL, "
                + COL_DUE_DATE + " TEXT NOT NULL, "
                + COL_PAYMENT_STATUS + " TEXT NOT NULL DEFAULT 'scheduled' "
                + "CHECK(status IN ('scheduled', 'paid', 'failed', 'cancelled')), "
                + COL_FAILURE_REASON + " TEXT DEFAULT NULL, "
                + COL_PAID_AT + " INTEGER DEFAULT NULL, "
                + COL_FREQUENCY + " TEXT NOT NULL DEFAULT 'once' " + FREQUENCY_CHECK + ", "
                + COL_REPEAT_EVERY + " INTEGER NOT NULL DEFAULT 1 CHECK(repeat_every >= 1), "
                + COL_REPEAT_DAY + " INTEGER DEFAULT NULL, "
                + COL_KIND + " TEXT NOT NULL DEFAULT 'bill' " + KIND_CHECK + ", "
                + COL_TO_ACCOUNT_NUMBER + " TEXT DEFAULT NULL, "
                + COL_TO_ROUTING_NUMBER + " TEXT DEFAULT NULL, "
                + COL_MEMO + " TEXT DEFAULT NULL, "
                + COL_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE, "
                + "FOREIGN KEY (account_number) REFERENCES bank_accounts(account_number) ON DELETE CASCADE"
                + ");";
    }

    // Checks deposited by photo. check_key = "routing-account-checkNumber" from the check, so the
    // same check can't be deposited twice (NULL when those details weren't all available).
    private static final String CREATE_DEPOSITED_CHECKS = "CREATE TABLE IF NOT EXISTS " + TABLE_DEPOSITED_CHECKS + " ("
            + COL_CHECK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_USER_ID + " INTEGER NOT NULL, "
            + COL_ACCOUNT_NUMBER + " TEXT NOT NULL, "
            + COL_CHECK_KEY + " TEXT UNIQUE, "
            + COL_CHECK_NUMBER + " TEXT, "
            + COL_AMOUNT + " REAL NOT NULL, "
            + COL_DEPOSITED_AT + " INTEGER NOT NULL, "
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
        db.execSQL(CREATE_TRANSACTIONS);
        db.execSQL(CREATE_TRANSACTIONS_INDEX);
        db.execSQL(CREATE_SCHEDULED_PAYMENTS);
        db.execSQL(CREATE_DEPOSITED_CHECKS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            // Very old development database: wipe and rebuild.
            // Drop child tables first because of the foreign keys.
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_DEPOSITED_CHECKS);
            db.execSQL("DROP TABLE IF EXISTS cards"); // from version 8
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCHEDULED_PAYMENTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_BANK_ACCOUNTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
            onCreate(db);
            return;
        }
        // From here on, each step adds what the next version needs and keeps everyone's data
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COL_LAST_LOGIN_AT + " INTEGER DEFAULT NULL");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE " + TABLE_BANK_ACCOUNTS + " ADD COLUMN " + COL_ROUTING_NUMBER
                    + " TEXT NOT NULL DEFAULT '" + BANK_ROUTING_NUMBER + "'");
            db.execSQL("ALTER TABLE " + TABLE_BANK_ACCOUNTS + " ADD COLUMN " + COL_STATUS
                    + " TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active', 'frozen', 'closed'))");
            db.execSQL("ALTER TABLE " + TABLE_BANK_ACCOUNTS + " ADD COLUMN " + COL_CLOSED_AT + " INTEGER DEFAULT NULL");
            // Version 4 used is_active (1/0). Carry closed accounts over to the new status column.
            // (The old is_active column stays in upgraded databases but is no longer used.)
            db.execSQL("UPDATE " + TABLE_BANK_ACCOUNTS + " SET " + COL_STATUS + " = 'closed' WHERE is_active = 0");
            db.execSQL(CREATE_TRANSACTIONS);
            db.execSQL(CREATE_TRANSACTIONS_INDEX);
            // Created with the latest columns already, so the column steps below skip it
            db.execSQL(CREATE_SCHEDULED_PAYMENTS);
        }
        if (oldVersion == 5) {
            db.execSQL("ALTER TABLE " + TABLE_SCHEDULED_PAYMENTS + " ADD COLUMN " + COL_FREQUENCY
                    + " TEXT NOT NULL DEFAULT 'once' " + FREQUENCY_CHECK);
            db.execSQL("ALTER TABLE " + TABLE_SCHEDULED_PAYMENTS + " ADD COLUMN " + COL_REPEAT_EVERY
                    + " INTEGER NOT NULL DEFAULT 1 CHECK(repeat_every >= 1)");
            db.execSQL("ALTER TABLE " + TABLE_SCHEDULED_PAYMENTS + " ADD COLUMN " + COL_REPEAT_DAY
                    + " INTEGER DEFAULT NULL");
        }
        if (oldVersion == 6) {
            // Version 6 only allowed 'once' or 'monthly'. SQLite can't change a CHECK rule in place,
            // so build the new table, copy every bill across, and swap it in.
            String newTable = TABLE_SCHEDULED_PAYMENTS + "_new";
            String columns = COL_PAYMENT_ID + ", " + COL_USER_ID + ", " + COL_ACCOUNT_NUMBER + ", " + COL_PAYEE
                    + ", " + COL_AMOUNT + ", " + COL_DUE_DATE + ", " + COL_PAYMENT_STATUS + ", " + COL_FAILURE_REASON
                    + ", " + COL_PAID_AT + ", " + COL_FREQUENCY + ", " + COL_REPEAT_DAY + ", " + COL_CREATED_AT;
            db.execSQL(createScheduledPaymentsSql(newTable));
            db.execSQL("INSERT INTO " + newTable + " (" + columns + ", " + COL_REPEAT_EVERY + ") "
                    + "SELECT " + columns + ", 1 FROM " + TABLE_SCHEDULED_PAYMENTS);
            db.execSQL("DROP TABLE " + TABLE_SCHEDULED_PAYMENTS);
            db.execSQL("ALTER TABLE " + newTable + " RENAME TO " + TABLE_SCHEDULED_PAYMENTS);
        }
        if (oldVersion < 8) {
            // Scheduled transfers: only add the columns the table doesn't have yet
            addColumnIfMissing(db, TABLE_SCHEDULED_PAYMENTS, COL_KIND,
                    "TEXT NOT NULL DEFAULT 'bill' " + KIND_CHECK);
            addColumnIfMissing(db, TABLE_SCHEDULED_PAYMENTS, COL_TO_ACCOUNT_NUMBER, "TEXT DEFAULT NULL");
            addColumnIfMissing(db, TABLE_SCHEDULED_PAYMENTS, COL_TO_ROUTING_NUMBER, "TEXT DEFAULT NULL");
            addColumnIfMissing(db, TABLE_SCHEDULED_PAYMENTS, COL_MEMO, "TEXT DEFAULT NULL");
            db.execSQL(CREATE_DEPOSITED_CHECKS);
        }
        if (oldVersion < 9) {
            // Debit cards were removed from the app. Databases from versions 5 to 8 also keep an
            // unused users.card_frozen column; SQLite can't easily drop it, and it does no harm.
            db.execSQL("DROP TABLE IF EXISTS cards");
        }
    }

    private static void addColumnIfMissing(SQLiteDatabase db, String table, String column, String definition) {
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (c.moveToNext()) {
                if (column.equals(c.getString(1))) return; // column 1 of table_info is the name
            }
        }
        db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    // ==================================================================
    // Registration
    // ==================================================================

    public enum RegisterResult { SUCCESS, USERNAME_TAKEN, EMAIL_TAKEN, PHONE_TAKEN, ERROR }

    /**
     * Saves a new customer and opens their first checking account. The password is hashed
     * before it is stored; the plain password never goes into the database.
     */
    public RegisterResult registerUser(String username, String firstName, String lastName,
                                       String dateOfBirth, String email, String phoneNumber,
                                       String password) {
        // Check each unique field first so the screen can say exactly which one is taken
        if (userExists(COL_USERNAME, username, -1)) return RegisterResult.USERNAME_TAKEN;
        if (userExists(COL_EMAIL, email, -1)) return RegisterResult.EMAIL_TAKEN;
        if (userExists(COL_PHONE_NUMBER, phoneNumber, -1)) return RegisterResult.PHONE_TAKEN;

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

    /** True if another user (not exceptUserId) already has this value in the column. */
    private boolean userExists(String column, String value, long exceptUserId) {
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_USERS, new String[]{COL_USER_ID},
                column + " = ? AND " + COL_USER_ID + " != ?",
                new String[]{value, String.valueOf(exceptUserId)},
                null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    // ==================================================================
    // Login
    // ==================================================================

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
     * locks the login after too many wrong passwords.
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

    // ==================================================================
    // Users / profile (Settings screen)
    // ==================================================================

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

    /** Updates name, email and phone. Email and phone must not belong to another user. */
    public OperationResult updateProfile(long userId, String firstName, String lastName,
                                         String email, String phoneNumber) {
        if (userExists(COL_EMAIL, email, userId)) {
            return OperationResult.fail("Another account already uses this email.");
        }
        if (userExists(COL_PHONE_NUMBER, phoneNumber, userId)) {
            return OperationResult.fail("Another account already uses this phone number.");
        }
        ContentValues values = new ContentValues();
        values.put(COL_FIRST_NAME, firstName);
        values.put(COL_LAST_NAME, lastName);
        values.put(COL_EMAIL, email);
        values.put(COL_PHONE_NUMBER, phoneNumber);
        try {
            int rows = getWritableDatabase().update(TABLE_USERS, values,
                    COL_USER_ID + " = ?", new String[]{String.valueOf(userId)});
            return rows == 1 ? OperationResult.ok("Profile saved.")
                    : OperationResult.fail("Your profile could not be found.");
        } catch (SQLiteConstraintException e) {
            return OperationResult.fail("That email or phone number is already in use.");
        }
    }

    public OperationResult changePassword(long userId, String currentPassword, String newPassword) {
        if (!checkPassword(userId, currentPassword)) {
            return OperationResult.fail("Your current password is incorrect.");
        }
        ContentValues values = new ContentValues();
        values.put(COL_PASSWORD_HASH, PasswordUtils.hashPassword(newPassword));
        getWritableDatabase().update(TABLE_USERS, values,
                COL_USER_ID + " = ?", new String[]{String.valueOf(userId)});
        return OperationResult.ok("Password changed.");
    }

    private boolean checkPassword(long userId, String password) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_USERS,
                new String[]{COL_PASSWORD_HASH}, COL_USER_ID + " = ?",
                new String[]{String.valueOf(userId)}, null, null, null)) {
            return cursor.moveToFirst() && PasswordUtils.verifyPassword(password, cursor.getString(0));
        }
    }

    // ==================================================================
    // Deleting a user (the whole profile, not one bank account)
    // ==================================================================

    /** What deleting this user's profile would remove. Shown on Settings before confirming. */
    public ProfileDeletionCheck getProfileDeletionCheck(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        List<Account> accounts = getAccountsForUser(userId);
        int outgoing = count(db, "SELECT COUNT(*) FROM " + TABLE_SCHEDULED_PAYMENTS
                        + " WHERE " + COL_USER_ID + " = ? AND " + COL_PAYMENT_STATUS + " IN ('scheduled', 'failed')",
                String.valueOf(userId));
        int incoming = count(db, "SELECT COUNT(*) FROM " + TABLE_SCHEDULED_PAYMENTS + " p JOIN "
                        + TABLE_BANK_ACCOUNTS + " a ON a." + COL_ACCOUNT_NUMBER + " = p." + COL_TO_ACCOUNT_NUMBER
                        + " WHERE a." + COL_USER_ID + " = ? AND p." + COL_USER_ID + " != ?"
                        + " AND (p." + COL_TO_ROUTING_NUMBER + " IS NULL OR p." + COL_TO_ROUTING_NUMBER + " = ?)"
                        + " AND p." + COL_PAYMENT_STATUS + " IN ('scheduled', 'failed')",
                String.valueOf(userId), String.valueOf(userId), BANK_ROUTING_NUMBER);
        return new ProfileDeletionCheck(accounts, outgoing, incoming);
    }

    private static int count(SQLiteDatabase db, String sql, String... args) {
        try (Cursor c = db.rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Deletes the profile only if every account is at $0.00. */
    public OperationResult deleteUser(long userId, String password) {
        return deleteUser(userId, password, false);
    }

    /**
     * Permanently deletes a user: their profile, every bank account (open or closed), transaction
     * history, scheduled bills and transfers, and check deposit records. Afterwards
     * the username, email and phone number can be used to register again.
     *
     * Money left in the accounts: if cashOut is false the delete is refused, so no money
     * disappears by accident. If cashOut is true the remaining balance is paid out to the user
     * (a real bank would mail a check or send it to another bank) and then everything is deleted.
     *
     * Other customers' scheduled transfers into this user's accounts are cancelled, since those
     * accounts won't exist. Their past transactions with this user stay in THEIR history.
     *
     * It all happens in one database transaction: either everything is deleted or nothing is.
     */
    public OperationResult deleteUser(long userId, String password, boolean cashOut) {
        if (password == null || !checkPassword(userId, password)) {
            return OperationResult.fail("Incorrect password.");
        }
        ProfileDeletionCheck check = getProfileDeletionCheck(userId);
        if (check.hasMoneyLeft() && !cashOut) {
            Account first = check.accountsWithMoney.get(0);
            return OperationResult.fail(check.accountsWithMoney.size() == 1
                    ? "Your " + first.getDisplayName() + " still has " + money(first.getBalance())
                    + ". Move it out first, or choose to cash out."
                    : "Your accounts still have " + money(check.totalBalance)
                    + " in total. Move it out first, or choose to cash out.");
        }

        SQLiteDatabase db = getWritableDatabase();
        String id = String.valueOf(userId);
        // All of the user's account numbers, closed ones included
        String myAccounts = "SELECT " + COL_ACCOUNT_NUMBER + " FROM " + TABLE_BANK_ACCOUNTS
                + " WHERE " + COL_USER_ID + " = ?";
        db.beginTransaction();
        try {
            // 1. Other customers' scheduled transfers to this user can't be sent any more
            ContentValues cancel = new ContentValues();
            cancel.put(COL_PAYMENT_STATUS, ScheduledPayment.STATUS_CANCELLED);
            int incomingCancelled = db.update(TABLE_SCHEDULED_PAYMENTS, cancel,
                    COL_USER_ID + " != ? AND " + COL_TO_ACCOUNT_NUMBER + " IN (" + myAccounts + ") AND ("
                            + COL_TO_ROUTING_NUMBER + " IS NULL OR " + COL_TO_ROUTING_NUMBER + " = ?) AND "
                            + COL_PAYMENT_STATUS + " IN ('scheduled', 'failed')",
                    new String[]{id, id, BANK_ROUTING_NUMBER});

            // 2. Delete everything that belongs to the user. Done one table at a time (children
            //    first) rather than relying on ON DELETE CASCADE, so it also works on databases
            //    created by older versions of the app.
            db.delete(TABLE_TRANSACTIONS, COL_ACCOUNT_NUMBER + " IN (" + myAccounts + ")", new String[]{id});
            db.delete(TABLE_SCHEDULED_PAYMENTS, COL_USER_ID + " = ? OR " + COL_ACCOUNT_NUMBER
                    + " IN (" + myAccounts + ")", new String[]{id, id});
            db.delete(TABLE_DEPOSITED_CHECKS, COL_USER_ID + " = ?", new String[]{id});
            db.delete(TABLE_BANK_ACCOUNTS, COL_USER_ID + " = ?", new String[]{id});
            int rows = db.delete(TABLE_USERS, COL_USER_ID + " = ?", new String[]{id});
            if (rows != 1) return OperationResult.fail("Your profile could not be found.");

            db.setTransactionSuccessful();
            StringBuilder message = new StringBuilder("Your profile was deleted.");
            if (check.hasMoneyLeft()) {
                message.append(" ").append(money(check.totalBalance)).append(" was paid out to you.");
            }
            if (incomingCancelled > 0) {
                message.append(" ").append(incomingCancelled).append(incomingCancelled == 1
                        ? " scheduled transfer to you from another customer was"
                        : " scheduled transfers to you from other customers were").append(" cancelled.");
            }
            return OperationResult.ok(message.toString());
        } finally {
            db.endTransaction();
        }
    }

    // ==================================================================
    // Bank accounts
    // ==================================================================

    private static final String ACCOUNT_COLUMNS = COL_ACCOUNT_ID + ", " + COL_USER_ID + ", "
            + COL_ACCOUNT_NUMBER + ", " + COL_ROUTING_NUMBER + ", " + COL_ACCOUNT_TYPE + ", "
            + COL_BALANCE + ", " + COL_STATUS;

    private static Account readAccount(Cursor c) {
        return new Account(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
                c.getString(4), c.getDouble(5), c.getString(6));
    }

    /** All of a user's accounts that aren't closed, checking first. */
    public List<Account> getAccountsForUser(long userId) {
        List<Account> accounts = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT " + ACCOUNT_COLUMNS + " FROM " + TABLE_BANK_ACCOUNTS
                        + " WHERE " + COL_USER_ID + " = ? AND " + COL_STATUS + " != 'closed'"
                        // 'checking' sorts before 'savings'; oldest account first within each type
                        + " ORDER BY " + COL_ACCOUNT_TYPE + " ASC, " + COL_ACCOUNT_ID + " ASC",
                new String[]{String.valueOf(userId)})) {
            while (cursor.moveToNext()) accounts.add(readAccount(cursor));
        }
        return accounts;
    }

    /** One of this user's accounts, or null if it doesn't exist or belongs to someone else. */
    @Nullable
    public Account getAccount(long userId, String accountNumber) {
        Account account = findAccount(getReadableDatabase(), accountNumber);
        return account != null && account.getUserId() == userId ? account : null;
    }

    @Nullable
    private static Account findAccount(SQLiteDatabase db, String accountNumber) {
        try (Cursor cursor = db.rawQuery(
                "SELECT " + ACCOUNT_COLUMNS + " FROM " + TABLE_BANK_ACCOUNTS
                        + " WHERE " + COL_ACCOUNT_NUMBER + " = ?",
                new String[]{accountNumber})) {
            return cursor.moveToFirst() ? readAccount(cursor) : null;
        }
    }

    /** Opens a new $0.00 account ("checking" or "savings"). Returns null if it couldn't be saved. */
    @Nullable
    public Account openAccount(long userId, String accountType) {
        try {
            return insertAccount(getWritableDatabase(), userId, accountType);
        } catch (Exception e) {
            return null;
        }
    }

    private static Account insertAccount(SQLiteDatabase db, long userId, String accountType) {
        // Try a few random numbers in case one is already used (very unlikely)
        for (int attempt = 0; attempt < 5; attempt++) {
            String accountNumber = generateAccountNumber();
            ContentValues values = new ContentValues();
            values.put(COL_USER_ID, userId);
            values.put(COL_ACCOUNT_NUMBER, accountNumber);
            values.put(COL_ROUTING_NUMBER, BANK_ROUTING_NUMBER);
            values.put(COL_ACCOUNT_TYPE, accountType);
            // balance (0.00), status ('active') and created_at use their DEFAULT values
            try {
                long accountId = db.insertOrThrow(TABLE_BANK_ACCOUNTS, null, values);
                return new Account(accountId, userId, accountNumber, BANK_ROUTING_NUMBER,
                        accountType, 0.0, Account.STATUS_ACTIVE);
            } catch (SQLiteConstraintException duplicateNumber) {
                // pick another number and try again
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }

    /** A random 10-digit account number that doesn't start with 0 (same rule as the team backend). */
    private static String generateAccountNumber() {
        SecureRandom random = new SecureRandom();
        StringBuilder number = new StringBuilder();
        number.append(1 + random.nextInt(9));
        for (int i = 1; i < 10; i++) number.append(random.nextInt(10));
        return number.toString();
    }

    /**
     * Everything the Remove Account screen needs to check first: money left, bills and transfers
     * paid from the account, transfers scheduled into it, and whether it's the
     * user's last account. Null if the account isn't the user's or is already closed.
     */
    @Nullable
    public AccountRemovalCheck getRemovalCheck(long userId, String accountNumber) {
        SQLiteDatabase db = getWritableDatabase();
        Account account = findAccount(db, accountNumber);
        if (account == null || account.getUserId() != userId || account.isClosed()) return null;

        AccountRemovalCheck check = new AccountRemovalCheck(account);
        for (ScheduledPayment p : getUpcomingPayments(userId)) {
            if (accountNumber.equals(p.getAccountNumber())) {
                check.outgoing.add(p);
            } else if (p.isTransfer() && p.getToRoutingNumber() == null && accountNumber.equals(p.getToAccountNumber())) {
                check.incoming.add(p);
            }
        }
        for (Account other : getAccountsForUser(userId)) {
            if (!other.getAccountNumber().equals(accountNumber)) check.otherAccounts.add(other);
        }
        return check;
    }

    /** Removes an account and cancels everything scheduled from or into it. */
    public OperationResult closeAccount(long userId, String accountNumber) {
        return closeAccount(userId, accountNumber, null);
    }

    /**
     * Removes (closes) an account, like the team backend's closeBankAccount: it disappears from the
     * user's accounts, and its history is kept. The balance must be $0.00 first so no money is lost.
     *
     * moveScheduledTo: another of the user's accounts to move this account's scheduled bills and
     * transfers to. Null cancels them instead. Transfers scheduled INTO this account are always
     * cancelled.
     */
    public OperationResult closeAccount(long userId, String accountNumber, @Nullable String moveScheduledTo) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Account account = findAccount(db, accountNumber);
            if (account == null || account.getUserId() != userId) {
                return OperationResult.fail("Account not found.");
            }
            if (account.isClosed()) {
                return OperationResult.fail("This account is already closed.");
            }
            if (toCents(account.getBalance()) != 0) {
                return OperationResult.fail("This account still has " + money(account.getBalance())
                        + ". Move it to another account before removing it.");
            }
            String[] open = {"scheduled", "failed"};
            int moved = 0;
            if (moveScheduledTo != null) {
                Account target = findAccount(db, moveScheduledTo);
                if (target == null || target.getUserId() != userId || target.isClosed()
                        || moveScheduledTo.equals(accountNumber)) {
                    return OperationResult.fail("Choose one of your other open accounts for the scheduled payments.");
                }
                // A transfer from this account INTO the target would become "target to target": cancel it
                ContentValues cancelSame = new ContentValues();
                cancelSame.put(COL_PAYMENT_STATUS, ScheduledPayment.STATUS_CANCELLED);
                db.update(TABLE_SCHEDULED_PAYMENTS, cancelSame, COL_ACCOUNT_NUMBER + " = ? AND "
                                + COL_TO_ACCOUNT_NUMBER + " = ? AND " + COL_TO_ROUTING_NUMBER + " IS NULL AND "
                                + COL_PAYMENT_STATUS + " IN (?, ?)",
                        new String[]{accountNumber, moveScheduledTo, open[0], open[1]});
                ContentValues move = new ContentValues();
                move.put(COL_ACCOUNT_NUMBER, moveScheduledTo);
                moved = db.update(TABLE_SCHEDULED_PAYMENTS, move,
                        COL_ACCOUNT_NUMBER + " = ? AND " + COL_PAYMENT_STATUS + " IN (?, ?)",
                        new String[]{accountNumber, open[0], open[1]});
            }
            ContentValues cancel = new ContentValues();
            cancel.put(COL_PAYMENT_STATUS, ScheduledPayment.STATUS_CANCELLED);
            int cancelled = db.update(TABLE_SCHEDULED_PAYMENTS, cancel,
                    COL_ACCOUNT_NUMBER + " = ? AND " + COL_PAYMENT_STATUS + " IN (?, ?)",
                    new String[]{accountNumber, open[0], open[1]});
            cancelled += db.update(TABLE_SCHEDULED_PAYMENTS, cancel,
                    COL_TO_ACCOUNT_NUMBER + " = ? AND " + COL_TO_ROUTING_NUMBER + " IS NULL AND "
                            + COL_PAYMENT_STATUS + " IN (?, ?)",
                    new String[]{accountNumber, open[0], open[1]});


            ContentValues values = new ContentValues();
            values.put(COL_STATUS, Account.STATUS_CLOSED);
            values.put(COL_CLOSED_AT, System.currentTimeMillis());
            db.update(TABLE_BANK_ACCOUNTS, values, COL_ACCOUNT_NUMBER + " = ?", new String[]{accountNumber});

            db.setTransactionSuccessful();
            StringBuilder message = new StringBuilder(account.getDisplayName()).append(" removed.");
            if (moved > 0) message.append(" ").append(moved).append(" scheduled ").append(moved == 1 ? "payment" : "payments").append(" moved.");
            if (cancelled > 0) message.append(" ").append(cancelled).append(" scheduled ").append(cancelled == 1 ? "payment" : "payments").append(" cancelled.");
            return OperationResult.ok(message.toString());
        } finally {
            db.endTransaction();
        }
    }

    // ==================================================================
    // Deposits, withdrawals and transfers
    // ==================================================================

    /** Adds money to one of the user's accounts. memo is optional (shown in the activity list). */
    public OperationResult deposit(long userId, String accountNumber, double amount, @Nullable String memo) {
        String amountError = checkAmount(amount);
        if (amountError != null) return OperationResult.fail(amountError);

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction(); // START TRANSACTION
        try {
            OperationResult result = depositInTx(db, userId, accountNumber, amount, withMemo("Deposit", memo));
            if (result.success) db.setTransactionSuccessful(); // SAVE TRANSACTION
            return result;
        } finally {
            db.endTransaction(); // without setTransactionSuccessful this cancels everything
        }
    }

    private OperationResult depositInTx(SQLiteDatabase db, long userId, String accountNumber, double amount,
                                        String description) {
        Account account = findAccount(db, accountNumber);
        String error = checkCanUse(account, userId);
        if (error != null) return OperationResult.fail(error);

        double newBalance = changeBalance(db, accountNumber, amount);
        logTransaction(db, accountNumber, Transaction.TYPE_DEPOSIT, amount, null, description);
        return OperationResult.ok("Deposited " + money(amount) + " into your "
                + account.getDisplayName() + ".", newBalance);
    }

    /**
     * Deposits a check photographed on the Deposit Check screen. routing / checkAccount /
     * checkNumber come from the check and are all required; together they identify the check, so
     * the same check can't be deposited twice.
     */
    public OperationResult depositCheck(long userId, String accountNumber, double amount,
                                        @Nullable String checkNumber, @Nullable String routing,
                                        @Nullable String checkAccount) {
        String amountError = checkAmount(amount);
        if (amountError != null) return OperationResult.fail(amountError);
        String num = digitsOrNull(checkNumber);
        String rout = digitsOrNull(routing);
        String acct = digitsOrNull(checkAccount);
        // All three are required: together they identify the check, so it can't be deposited twice
        String detailsProblem = checkCheckDetails(num, rout, acct);
        if (detailsProblem != null) return OperationResult.fail(detailsProblem);
        String key = rout + "-" + acct + "-" + num;

        SQLiteDatabase db = getWritableDatabase();
        if (key != null) {
            try (Cursor c = db.rawQuery("SELECT " + COL_DEPOSITED_AT + " FROM " + TABLE_DEPOSITED_CHECKS
                    + " WHERE " + COL_CHECK_KEY + " = ?", new String[]{key})) {
                if (c.moveToFirst()) {
                    return OperationResult.fail("Check #" + num + " was already deposited on "
                            + new SimpleDateFormat("MMM d, yyyy", Locale.US).format(new Date(c.getLong(0))) + ".");
                }
            }
        }
        db.beginTransaction();
        try {
            // A check written on THIS bank (routing 123456789): the account on the check must be a
            // real open account here, and the money comes out of it, like a real check clearing.
            // Checks from other banks are just deposited (their bank isn't part of this app).
            String from = "";
            if (BANK_ROUTING_NUMBER.equals(rout)) {
                Account payer = findAccount(db, acct);
                String problem = checkPayerAccount(db, payer, accountNumber, amount);
                if (problem != null) return OperationResult.fail(problem);
                from = " from " + shortName(db, payer.getUserId());
                changeBalance(db, acct, -amount);
                logTransaction(db, acct, Transaction.TYPE_WITHDRAWAL, amount, accountNumber,
                        "Check #" + num + " paid · deposited by " + shortName(db, userId));
            }

            OperationResult result = depositInTx(db, userId, accountNumber, amount,
                    "Mobile check deposit · Check #" + num + from);
            if (!result.success) return result; // nothing is saved, including the payer's side
            ContentValues values = new ContentValues();
            values.put(COL_USER_ID, userId);
            values.put(COL_ACCOUNT_NUMBER, accountNumber);
            if (key != null) values.put(COL_CHECK_KEY, key);
            else values.putNull(COL_CHECK_KEY);
            if (num != null) values.put(COL_CHECK_NUMBER, num);
            else values.putNull(COL_CHECK_NUMBER);
            values.put(COL_AMOUNT, Math.round(amount * 100) / 100.0);
            values.put(COL_DEPOSITED_AT, System.currentTimeMillis());
            db.insertOrThrow(TABLE_DEPOSITED_CHECKS, null, values);
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * For a check written on this bank: null if the account on the check (payer) can pay it into
     * depositAccount; otherwise why not. The messages don't reveal whose account it is.
     */
    @Nullable
    private static String checkPayerAccount(SQLiteDatabase db, @Nullable Account payer,
                                            String depositAccount, double amount) {
        if (payer == null || payer.isClosed()) {
            return "No open Team 2 Bank account has the account number on this check. "
                    + "Check the numbers along the bottom of the check.";
        }
        if (payer.getAccountNumber().equals(depositAccount)) {
            return "A check can't be deposited into the same account it's written on.";
        }
        if (!payer.isActive()) {
            return "The account this check is written on can't pay it right now.";
        }
        if (toCents(payer.getBalance()) < toCents(amount)) {
            return "This check can't be deposited: the account it's written on doesn't have enough "
                    + "money to cover it.";
        }
        return null;
    }

    /**
     * Null if a check's number, routing number and account number are all filled in and look
     * right; otherwise what's wrong. Used by depositCheck and the Deposit Check screen.
     */
    @Nullable
    public static String checkCheckDetails(@Nullable String checkNumber, @Nullable String routing,
                                           @Nullable String checkAccount) {
        String problem = checkCheckNumber(checkNumber);
        if (problem == null) problem = checkCheckRouting(routing);
        if (problem == null) problem = checkCheckAccount(checkAccount);
        return problem;
    }

    /** The check number, top right of the check: required, up to 8 digits. */
    @Nullable
    public static String checkCheckNumber(@Nullable String checkNumber) {
        String num = digitsOrNull(checkNumber);
        if (num == null || num.length() > 8) {
            return "Enter the check number (top right of the check, up to 8 digits).";
        }
        return null;
    }

    /** The routing number on the check, bottom left: required, must pass the routing checksum. */
    @Nullable
    public static String checkCheckRouting(@Nullable String routing) {
        String rout = digitsOrNull(routing);
        if (rout == null) return "Enter the routing number from the bottom left of the check.";
        if (!isValidRoutingNumber(rout)) {
            return "That routing number doesn't look right. It's the 9 digits at the bottom left of the check.";
        }
        return null;
    }

    /** The account number on the check, after the routing number: required, 4 to 17 digits. */
    @Nullable
    public static String checkCheckAccount(@Nullable String checkAccount) {
        String acct = digitsOrNull(checkAccount);
        if (acct == null || acct.length() < 4 || acct.length() > 17) {
            return "Enter the account number from the bottom of the check (4 to 17 digits).";
        }
        return null;
    }

    @Nullable
    private static String digitsOrNull(@Nullable String text) {
        if (text == null) return null;
        String digits = text.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Takes money out of one of the user's accounts. */
    public OperationResult withdraw(long userId, String accountNumber, double amount, @Nullable String memo) {
        String amountError = checkAmount(amount);
        if (amountError != null) return OperationResult.fail(amountError);

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Account account = findAccount(db, accountNumber);
            String error = checkCanUse(account, userId);
            if (error != null) return OperationResult.fail(error);
            if (toCents(account.getBalance()) < toCents(amount)) {
                return OperationResult.fail("Not enough money. Your " + account.getDisplayName()
                        + " has " + money(account.getBalance()) + " available.");
            }

            double newBalance = changeBalance(db, accountNumber, -amount);
            logTransaction(db, accountNumber, Transaction.TYPE_WITHDRAWAL, amount, null,
                    withMemo("Withdrawal", memo));

            db.setTransactionSuccessful();
            return OperationResult.ok("Withdrew " + money(amount) + " from your "
                    + account.getDisplayName() + ".", newBalance);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Moves money from one of the user's accounts to any active account at this bank
     * (their own, or another customer's). Both halves happen together or not at all.
     */
    public OperationResult transfer(long userId, String fromAccountNumber, String toAccountNumber,
                                    double amount, @Nullable String memo) {
        String amountError = checkAmount(amount);
        if (amountError != null) return OperationResult.fail(amountError);

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            OperationResult result = transferInTx(db, userId, fromAccountNumber, toAccountNumber, amount, memo, "");
            if (result.success) db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    /** The transfer itself, inside a database transaction the caller started. */
    private OperationResult transferInTx(SQLiteDatabase db, long userId, String fromAccountNumber,
                                         String toAccountNumber, double amount, @Nullable String memo,
                                         String note) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            return OperationResult.fail("Choose two different accounts.");
        }
        Account from = findAccount(db, fromAccountNumber);
        String error = checkCanUse(from, userId);
        if (error != null) return OperationResult.fail(error);

        Account to = findAccount(db, toAccountNumber);
        if (to == null || to.isClosed()) {
            return OperationResult.fail("No open account was found with that account number.");
        }
        if (!to.isActive()) {
            return OperationResult.fail("The receiving account can't accept money right now.");
        }
        if (toCents(from.getBalance()) < toCents(amount)) {
            return OperationResult.fail("Not enough money. Your " + from.getDisplayName()
                    + " has " + money(from.getBalance()) + " available.");
        }

        String outText;
        String inText;
        if (to.getUserId() == userId) {
            outText = "Transfer to " + to.getDisplayName();
            inText = "Transfer from " + from.getDisplayName();
        } else {
            outText = "Transfer to " + shortName(db, to.getUserId());
            inText = "Transfer from " + shortName(db, userId);
        }

        double newBalance = changeBalance(db, fromAccountNumber, -amount);
        logTransaction(db, fromAccountNumber, Transaction.TYPE_TRANSFER_OUT, amount,
                toAccountNumber, withMemo(outText, memo) + note);
        changeBalance(db, toAccountNumber, amount);
        logTransaction(db, toAccountNumber, Transaction.TYPE_TRANSFER_IN, amount,
                fromAccountNumber, withMemo(inText, memo) + note);

        return OperationResult.ok("Sent " + money(amount) + " — " + outText + ".", newBalance);
    }

    /**
     * Sends money to an account at another bank, identified by its 9-digit routing number and the
     * account number there. If the routing number is this bank's, it's an ordinary transfer.
     *
     * (A real bank would send this through the ACH network and it would take 1-3 business days;
     * this prototype takes the money out of the account straight away and records it.)
     */
    public OperationResult transferToOtherBank(long userId, String fromAccountNumber, String routingNumber,
                                               String toAccountNumber, String recipientName,
                                               double amount, @Nullable String memo) {
        String amountError = checkAmount(amount);
        if (amountError != null) return OperationResult.fail(amountError);
        String problem = checkExternalDetails(routingNumber, toAccountNumber, recipientName);
        if (problem != null) return OperationResult.fail(problem);
        if (BANK_ROUTING_NUMBER.equals(routingNumber)) {
            // This bank: the name typed must be the account owner's, or nothing is sent
            String ownerProblem = checkOwnerName(getReadableDatabase(), toAccountNumber, recipientName);
            if (ownerProblem != null) return OperationResult.fail(ownerProblem);
            return transfer(userId, fromAccountNumber, toAccountNumber, amount, memo);
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            OperationResult result = externalTransferInTx(db, userId, fromAccountNumber, routingNumber,
                    toAccountNumber, recipientName, amount, memo, "");
            if (result.success) db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    private OperationResult externalTransferInTx(SQLiteDatabase db, long userId, String fromAccountNumber,
                                                 String routingNumber, String toAccountNumber,
                                                 String recipientName, double amount,
                                                 @Nullable String memo, String note) {
        Account from = findAccount(db, fromAccountNumber);
        String error = checkCanUse(from, userId);
        if (error != null) return OperationResult.fail(error);
        if (toCents(from.getBalance()) < toCents(amount)) {
            return OperationResult.fail("Not enough money. Your " + from.getDisplayName()
                    + " has " + money(from.getBalance()) + " available.");
        }
        String outText = "Transfer to " + externalLabel(recipientName.trim(), toAccountNumber);
        double newBalance = changeBalance(db, fromAccountNumber, -amount);
        logTransaction(db, fromAccountNumber, Transaction.TYPE_TRANSFER_OUT, amount,
                routingNumber + ":" + toAccountNumber, withMemo(outText, memo) + note);
        return OperationResult.ok("Sent " + money(amount) + " — " + outText + ".", newBalance);
    }

    /**
     * For a transfer sent with this bank's routing number (123456789): null if an open account has
     * that number AND the name typed is its owner's first and last name; otherwise what's wrong.
     * Capitals, extra spaces and punctuation don't matter ("jane  doe" matches "Jane Doe").
     * The error never says whose account it is, so it can't be used to look up names.
     */
    @Nullable
    private static String checkOwnerName(SQLiteDatabase db, String accountNumber, @Nullable String name) {
        Account account = findAccount(db, accountNumber);
        if (account == null || account.isClosed()) {
            return "No open account was found with that account number.";
        }
        String owner = null;
        try (Cursor c = db.query(TABLE_USERS, new String[]{COL_FIRST_NAME, COL_LAST_NAME},
                COL_USER_ID + " = ?", new String[]{String.valueOf(account.getUserId())}, null, null, null)) {
            if (c.moveToFirst()) {
                owner = (c.isNull(0) ? "" : c.getString(0)) + " " + (c.isNull(1) ? "" : c.getString(1));
            }
        }
        if (owner == null || !normalizeName(owner).equals(normalizeName(name))) {
            return "The name doesn't match the account holder for that account number. "
                    + "Check both and try again.";
        }
        return null;
    }

    /** "  Jane   O'Doe " -> "jane odoe": lower case, letters/digits/spaces only, single spaces. */
    private static String normalizeName(@Nullable String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N} ]", "")
                .replaceAll("\\s+", " ").trim();
    }

    /** "Jane Smith · Other bank •••• 6789" */
    private static String externalLabel(String name, String accountNumber) {
        return name + " · Other bank " + Account.maskNumber(accountNumber);
    }

    /** Null if a transfer to another bank has everything it needs; otherwise what's wrong. */
    @Nullable
    public static String checkExternalDetails(String routingNumber, String accountNumber, String recipientName) {
        if (!isValidRoutingNumber(routingNumber)) {
            return "That routing number isn't valid. It's the 9-digit number at the bottom left of a check.";
        }
        if (accountNumber == null || !accountNumber.matches("\\d{4,17}")) {
            return "Enter the account number (4 to 17 digits).";
        }
        if (recipientName == null || recipientName.trim().isEmpty()) {
            return "Enter the name on the account you're sending to.";
        }
        return null;
    }

    /**
     * True for a real-looking US bank routing (ABA) number: 9 digits whose checksum works out,
     * 3×(d1+d4+d7) + 7×(d2+d5+d8) + (d3+d6+d9) divisible by 10. This bank's own made-up
     * routing number (123456789) is also accepted.
     */
    public static boolean isValidRoutingNumber(@Nullable String routing) {
        if (routing == null || !routing.matches("\\d{9}")) return false;
        if (BANK_ROUTING_NUMBER.equals(routing)) return true;
        if (routing.equals("000000000")) return false;
        int[] d = new int[9];
        for (int i = 0; i < 9; i++) d[i] = routing.charAt(i) - '0';
        int sum = 3 * (d[0] + d[3] + d[6]) + 7 * (d[1] + d[4] + d[7]) + (d[2] + d[5] + d[8]);
        return sum % 10 == 0;
    }

    /**
     * For transfers to another customer: returns the account owner's name as "Jane D.",
     * so the sender can check they typed the right number. Null if there's no open account.
     */
    @Nullable
    public String findRecipientName(String accountNumber) {
        SQLiteDatabase db = getReadableDatabase();
        Account account = findAccount(db, accountNumber);
        if (account == null || account.isClosed()) return null;
        return shortName(db, account.getUserId());
    }

    private static String shortName(SQLiteDatabase db, long userId) {
        try (Cursor cursor = db.query(TABLE_USERS, new String[]{COL_FIRST_NAME, COL_LAST_NAME},
                COL_USER_ID + " = ?", new String[]{String.valueOf(userId)}, null, null, null)) {
            if (!cursor.moveToFirst()) return "another customer";
            String last = cursor.getString(1);
            return cursor.getString(0) + (last == null || last.isEmpty() ? "" : " " + last.charAt(0) + ".");
        }
    }

    /** Null if the account can be used by this user; otherwise the reason it can't. */
    @Nullable
    private static String checkCanUse(@Nullable Account account, long userId) {
        if (account == null || account.getUserId() != userId) return "Account not found.";
        if (account.isClosed()) return "This account is closed.";
        if (!account.isActive()) return "This account is frozen. Please contact the bank.";
        return null;
    }

    /** Null if the amount is OK; otherwise what's wrong with it. */
    @Nullable
    public static String checkAmount(double amount) {
        if (Double.isNaN(amount) || toCents(amount) <= 0) return "Enter an amount greater than $0.00.";
        if (amount > MAX_TRANSACTION_AMOUNT) return "The most you can move at once is " + money(MAX_TRANSACTION_AMOUNT) + ".";
        return null;
    }

    /** Adds delta to the balance (rounded to cents) and returns the new balance. */
    private static double changeBalance(SQLiteDatabase db, String accountNumber, double delta) {
        db.execSQL("UPDATE " + TABLE_BANK_ACCOUNTS + " SET " + COL_BALANCE + " = ROUND(" + COL_BALANCE + " + ?, 2)"
                        + " WHERE " + COL_ACCOUNT_NUMBER + " = ?",
                new Object[]{delta, accountNumber});
        Account updated = findAccount(db, accountNumber);
        return updated == null ? 0 : updated.getBalance();
    }

    private static void logTransaction(SQLiteDatabase db, String accountNumber, String type, double amount,
                                       @Nullable String relatedAccount, String description) {
        ContentValues values = new ContentValues();
        values.put(COL_ACCOUNT_NUMBER, accountNumber);
        values.put(COL_TRANSACTION_TYPE, type);
        values.put(COL_AMOUNT, Math.round(amount * 100) / 100.0);
        if (relatedAccount != null) values.put(COL_RELATED_ACCOUNT, relatedAccount);
        else values.putNull(COL_RELATED_ACCOUNT);
        values.put(COL_DESCRIPTION, description);
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        db.insertOrThrow(TABLE_TRANSACTIONS, null, values);
    }

    private static String withMemo(String text, @Nullable String memo) {
        return memo == null || memo.trim().isEmpty() ? text : text + " · " + memo.trim();
    }

    // ==================================================================
    // Transaction history
    // ==================================================================

    private static final String TRANSACTION_SELECT = "SELECT t." + COL_TRANSACTION_ID + ", t." + COL_ACCOUNT_NUMBER
            + ", a." + COL_ACCOUNT_TYPE + ", t." + COL_TRANSACTION_TYPE + ", t." + COL_AMOUNT
            + ", t." + COL_RELATED_ACCOUNT + ", t." + COL_DESCRIPTION + ", t." + COL_TIMESTAMP
            + " FROM " + TABLE_TRANSACTIONS + " t JOIN " + TABLE_BANK_ACCOUNTS + " a"
            + " ON a." + COL_ACCOUNT_NUMBER + " = t." + COL_ACCOUNT_NUMBER;

    private static final String TRANSACTION_ORDER = " ORDER BY t." + COL_TIMESTAMP + " DESC, t."
            + COL_TRANSACTION_ID + " DESC LIMIT ";

    /** The user's most recent transactions across all their accounts (including closed ones). */
    public List<Transaction> getTransactionsForUser(long userId, int limit) {
        return readTransactions(TRANSACTION_SELECT + " WHERE a." + COL_USER_ID + " = ?" + TRANSACTION_ORDER + limit,
                new String[]{String.valueOf(userId)});
    }

    /** Most recent transactions for one of the user's accounts. */
    public List<Transaction> getTransactionsForAccount(long userId, String accountNumber, int limit) {
        return readTransactions(TRANSACTION_SELECT + " WHERE a." + COL_USER_ID + " = ? AND t."
                        + COL_ACCOUNT_NUMBER + " = ?" + TRANSACTION_ORDER + limit,
                new String[]{String.valueOf(userId), accountNumber});
    }

    private List<Transaction> readTransactions(String sql, String[] args) {
        List<Transaction> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                list.add(new Transaction(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getDouble(4), c.isNull(5) ? null : c.getString(5), c.getString(6), c.getLong(7)));
            }
        }
        return list;
    }


    // ==================================================================
    // Scheduled bills and transfers (Pay Bills and Transfer screens)
    // ==================================================================

    /**
     * Schedules a bill. dueDate is "yyyy-MM-dd" and can't be in the past.
     *
     * frequency is one of ScheduledPayment.FREQUENCY_ONCE / _DAILY / _WEEKLY / _MONTHLY / _YEARLY,
     * and repeatEvery says how many of those between payments: ("weekly", 2) = every 2 weeks,
     * ("monthly", 3) = every 3 months. A repeating bill keeps going until it's cancelled.
     */
    public OperationResult schedulePayment(long userId, String fromAccountNumber, String payee,
                                           double amount, String dueDate, String frequency, int repeatEvery) {
        if (payee == null || payee.trim().isEmpty()) return OperationResult.fail("Enter who you're paying.");
        String problem = checkSchedule(userId, fromAccountNumber, amount, dueDate, frequency, repeatEvery);
        if (problem != null) return OperationResult.fail(problem);

        ContentValues values = scheduleValues(userId, fromAccountNumber, amount, dueDate, frequency, repeatEvery);
        values.put(COL_PAYEE, payee.trim());
        values.put(COL_KIND, ScheduledPayment.KIND_BILL);
        getWritableDatabase().insertOrThrow(TABLE_SCHEDULED_PAYMENTS, null, values);
        boolean repeating = !ScheduledPayment.FREQUENCY_ONCE.equals(frequency);
        return OperationResult.ok((repeating ? "Repeating payment to " : "Payment to ") + payee.trim() + " scheduled.");
    }

    /**
     * Schedules a transfer for a date, once or on repeat.
     * toRoutingNumber: null (or this bank's routing number) for an account at this bank;
     * otherwise the other bank's routing number, and recipientName is required.
     */
    public OperationResult scheduleTransfer(long userId, String fromAccountNumber, String toAccountNumber,
                                            @Nullable String toRoutingNumber, @Nullable String recipientName,
                                            double amount, String startDate, String frequency, int repeatEvery,
                                            @Nullable String memo) {
        String problem = checkSchedule(userId, fromAccountNumber, amount, startDate, frequency, repeatEvery);
        if (problem != null) return OperationResult.fail(problem);

        SQLiteDatabase db = getWritableDatabase();
        boolean external = toRoutingNumber != null && !BANK_ROUTING_NUMBER.equals(toRoutingNumber);
        String payee;
        if (external) {
            String detailsProblem = checkExternalDetails(toRoutingNumber, toAccountNumber, recipientName);
            if (detailsProblem != null) return OperationResult.fail(detailsProblem);
            payee = externalLabel(recipientName.trim(), toAccountNumber);
        } else {
            if (fromAccountNumber.equals(toAccountNumber)) return OperationResult.fail("Choose two different accounts.");
            if (BANK_ROUTING_NUMBER.equals(toRoutingNumber)) {
                // Sent with this bank's routing number: the name must be the account owner's
                String ownerProblem = checkOwnerName(db, toAccountNumber, recipientName);
                if (ownerProblem != null) return OperationResult.fail(ownerProblem);
            }
            Account to = findAccount(db, toAccountNumber);
            if (to == null || to.isClosed()) return OperationResult.fail("No open account was found with that account number.");
            payee = to.getUserId() == userId ? to.getDisplayName()
                    : shortName(db, to.getUserId()) + " " + to.getMaskedNumber();
        }

        ContentValues values = scheduleValues(userId, fromAccountNumber, amount, startDate, frequency, repeatEvery);
        values.put(COL_PAYEE, payee);
        values.put(COL_KIND, ScheduledPayment.KIND_TRANSFER);
        values.put(COL_TO_ACCOUNT_NUMBER, toAccountNumber);
        if (external) values.put(COL_TO_ROUTING_NUMBER, toRoutingNumber);
        else values.putNull(COL_TO_ROUTING_NUMBER);
        if (memo != null && !memo.trim().isEmpty()) values.put(COL_MEMO, memo.trim());
        db.insertOrThrow(TABLE_SCHEDULED_PAYMENTS, null, values);
        return OperationResult.ok("Transfer to " + payee + " scheduled.");
    }

    /**
     * Changes a scheduled (or failed) bill or transfer: which account pays, the amount, the next
     * date and how often it repeats. newPayee changes a bill's payee (ignored for transfers, whose
     * destination can't change; null keeps it). A failed one goes back to 'scheduled'.
     */
    public OperationResult updatePayment(long userId, long paymentId, String fromAccountNumber,
                                         @Nullable String newPayee, double amount, String dueDate,
                                         String frequency, int repeatEvery) {
        ScheduledPayment existing = getPayment(userId, paymentId);
        if (existing == null) return OperationResult.fail("This payment can't be changed any more.");
        String problem = checkSchedule(userId, fromAccountNumber, amount, dueDate, frequency, repeatEvery);
        if (problem != null) return OperationResult.fail(problem);
        if (existing.isTransfer() && existing.getToRoutingNumber() == null
                && fromAccountNumber.equals(existing.getToAccountNumber())) {
            return OperationResult.fail("A transfer can't come from the account it's sending to.");
        }

        ContentValues values = scheduleValues(userId, fromAccountNumber, amount, dueDate, frequency, repeatEvery);
        values.remove(COL_USER_ID);
        if (!existing.isTransfer() && newPayee != null) {
            if (newPayee.trim().isEmpty()) return OperationResult.fail("Enter who you're paying.");
            values.put(COL_PAYEE, newPayee.trim());
        }
        if (ScheduledPayment.FREQUENCY_ONCE.equals(frequency)) values.putNull(COL_REPEAT_DAY);
        values.put(COL_PAYMENT_STATUS, ScheduledPayment.STATUS_SCHEDULED);
        values.putNull(COL_FAILURE_REASON);
        getWritableDatabase().update(TABLE_SCHEDULED_PAYMENTS, values,
                COL_PAYMENT_ID + " = ? AND " + COL_USER_ID + " = ?",
                new String[]{String.valueOf(paymentId), String.valueOf(userId)});
        return OperationResult.ok("Changes saved.");
    }

    /** Null if a schedule is OK; otherwise what's wrong with it. */
    @Nullable
    private String checkSchedule(long userId, String fromAccountNumber, double amount, String date,
                                 String frequency, int repeatEvery) {
        String amountError = checkAmount(amount);
        if (amountError != null) return amountError;
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) return "Choose a date.";
        if (date.compareTo(today()) < 0) return "Choose today or a future date.";
        String frequencyError = checkFrequency(frequency, repeatEvery);
        if (frequencyError != null) return frequencyError;
        return checkCanUse(getAccount(userId, fromAccountNumber), userId);
    }

    /** The columns every new or edited schedule sets. */
    private static ContentValues scheduleValues(long userId, String fromAccountNumber, double amount,
                                                String dueDate, String frequency, int repeatEvery) {
        boolean repeating = !ScheduledPayment.FREQUENCY_ONCE.equals(frequency);
        ContentValues values = new ContentValues();
        values.put(COL_USER_ID, userId);
        values.put(COL_ACCOUNT_NUMBER, fromAccountNumber);
        values.put(COL_AMOUNT, Math.round(amount * 100) / 100.0);
        values.put(COL_DUE_DATE, dueDate);
        values.put(COL_FREQUENCY, frequency);
        values.put(COL_REPEAT_EVERY, repeating ? repeatEvery : 1);
        if (repeating) {
            // Remember the day (e.g. the 31st) so short months don't move it for good
            values.put(COL_REPEAT_DAY, Integer.parseInt(dueDate.substring(8, 10)));
        }
        return values;
    }

    /** Null if the frequency is OK; otherwise what's wrong with it. */
    @Nullable
    public static String checkFrequency(String frequency, int repeatEvery) {
        if (ScheduledPayment.FREQUENCY_ONCE.equals(frequency)) return null;
        int max = ScheduledPayment.maxRepeatEvery(frequency);
        if (max == 0) return "Choose how often to pay.";
        if (repeatEvery < 1 || repeatEvery > max) {
            return "Choose a number from 1 to " + max + ".";
        }
        return null;
    }

    private static final String PAYMENT_SELECT = "SELECT p." + COL_PAYMENT_ID + ", p." + COL_PAYEE + ", p." + COL_AMOUNT
            + ", p." + COL_DUE_DATE + ", p." + COL_PAYMENT_STATUS + ", p." + COL_FAILURE_REASON
            + ", p." + COL_ACCOUNT_NUMBER + ", a." + COL_ACCOUNT_TYPE + ", p." + COL_FREQUENCY
            + ", p." + COL_REPEAT_EVERY + ", p." + COL_KIND + ", p." + COL_TO_ACCOUNT_NUMBER
            + ", p." + COL_TO_ROUTING_NUMBER + ", p." + COL_MEMO
            + " FROM " + TABLE_SCHEDULED_PAYMENTS + " p JOIN " + TABLE_BANK_ACCOUNTS + " a"
            + " ON a." + COL_ACCOUNT_NUMBER + " = p." + COL_ACCOUNT_NUMBER;

    private static ScheduledPayment readPayment(Cursor c) {
        return new ScheduledPayment(c.getLong(0), c.getString(1), c.getDouble(2), c.getString(3),
                c.getString(4), c.isNull(5) ? null : c.getString(5), c.getString(6), c.getString(7),
                c.getString(8), c.getInt(9), c.getString(10), c.isNull(11) ? null : c.getString(11),
                c.isNull(12) ? null : c.getString(12), c.isNull(13) ? null : c.getString(13));
    }

    /** Scheduled bills and transfers, plus failed ones (so the user can see and fix them), soonest first. */
    public List<ScheduledPayment> getUpcomingPayments(long userId) {
        List<ScheduledPayment> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(PAYMENT_SELECT
                        + " WHERE p." + COL_USER_ID + " = ? AND p." + COL_PAYMENT_STATUS + " IN ('scheduled', 'failed')"
                        + " ORDER BY p." + COL_DUE_DATE + " ASC, p." + COL_PAYMENT_ID + " ASC",
                new String[]{String.valueOf(userId)})) {
            while (c.moveToNext()) list.add(readPayment(c));
        }
        return list;
    }

    /** One of the user's scheduled or failed bills/transfers, or null. */
    @Nullable
    public ScheduledPayment getPayment(long userId, long paymentId) {
        try (Cursor c = getReadableDatabase().rawQuery(PAYMENT_SELECT
                        + " WHERE p." + COL_PAYMENT_ID + " = ? AND p." + COL_USER_ID + " = ?"
                        + " AND p." + COL_PAYMENT_STATUS + " IN ('scheduled', 'failed')",
                new String[]{String.valueOf(paymentId), String.valueOf(userId)})) {
            return c.moveToFirst() ? readPayment(c) : null;
        }
    }

    /** Cancels a bill or transfer. For a repeating one this stops all future payments. */
    public OperationResult cancelPayment(long userId, long paymentId) {
        ContentValues values = new ContentValues();
        values.put(COL_PAYMENT_STATUS, ScheduledPayment.STATUS_CANCELLED);
        int rows = getWritableDatabase().update(TABLE_SCHEDULED_PAYMENTS, values,
                COL_PAYMENT_ID + " = ? AND " + COL_USER_ID + " = ? AND " + COL_PAYMENT_STATUS + " IN ('scheduled', 'failed')",
                new String[]{String.valueOf(paymentId), String.valueOf(userId)});
        return rows == 1 ? OperationResult.ok("Payment removed.") : OperationResult.fail("Payment not found.");
    }

    /**
     * Tries a failed payment again, right now. It's paid immediately if the account can cover it
     * (a repeating one then moves on to its next date); otherwise it's marked 'failed' again with
     * the new reason, which is returned as the message.
     */
    public OperationResult retryPayment(long userId, long paymentId) {
        SQLiteDatabase db = getWritableDatabase();

        String payee;
        double amount;
        try (Cursor c = db.query(TABLE_SCHEDULED_PAYMENTS, new String[]{COL_PAYEE, COL_AMOUNT},
                COL_PAYMENT_ID + " = ? AND " + COL_USER_ID + " = ? AND " + COL_PAYMENT_STATUS + " = 'failed'",
                new String[]{String.valueOf(paymentId), String.valueOf(userId)}, null, null, null)) {
            if (!c.moveToFirst()) return OperationResult.fail("This payment can't be retried.");
            payee = c.getString(0);
            amount = c.getDouble(1);
        }

        // Back to 'scheduled' (keeping its due date, which has already arrived), then pay it now
        ContentValues reset = new ContentValues();
        reset.put(COL_PAYMENT_STATUS, ScheduledPayment.STATUS_SCHEDULED);
        reset.putNull(COL_FAILURE_REASON);
        db.update(TABLE_SCHEDULED_PAYMENTS, reset, COL_PAYMENT_ID + " = ?", new String[]{String.valueOf(paymentId)});

        if (payOneBill(db, userId, paymentId)) {
            return OperationResult.ok("Paid " + money(amount) + " to " + payee + ".");
        }

        // Failed again: report the new reason
        try (Cursor c = db.query(TABLE_SCHEDULED_PAYMENTS, new String[]{COL_FAILURE_REASON},
                COL_PAYMENT_ID + " = ?", new String[]{String.valueOf(paymentId)}, null, null, null)) {
            String reason = c.moveToFirst() && !c.isNull(0) ? c.getString(0) : "The payment couldn't be made.";
            return OperationResult.fail(reason);
        }
    }

    /**
     * Repeating payment that failed: skip this one without paying it, and go back to waiting
     * for the next date.
     */
    public OperationResult skipPayment(long userId, long paymentId) {
        SQLiteDatabase db = getWritableDatabase();
        String dueDate;
        String frequency;
        int repeatEvery;
        int repeatDay;
        try (Cursor c = db.query(TABLE_SCHEDULED_PAYMENTS,
                new String[]{COL_DUE_DATE, COL_FREQUENCY, COL_REPEAT_EVERY, COL_REPEAT_DAY},
                COL_PAYMENT_ID + " = ? AND " + COL_USER_ID + " = ? AND " + COL_PAYMENT_STATUS + " = 'failed' AND "
                        + COL_FREQUENCY + " != 'once'",
                new String[]{String.valueOf(paymentId), String.valueOf(userId)}, null, null, null)) {
            if (!c.moveToFirst()) return OperationResult.fail("This payment can't be skipped.");
            dueDate = c.getString(0);
            frequency = c.getString(1);
            repeatEvery = c.getInt(2);
            repeatDay = c.isNull(3) ? Integer.parseInt(dueDate.substring(8, 10)) : c.getInt(3);
        }
        String next = nextDueDate(dueDate, frequency, repeatEvery, repeatDay);
        ContentValues values = new ContentValues();
        values.put(COL_PAYMENT_STATUS, ScheduledPayment.STATUS_SCHEDULED);
        values.putNull(COL_FAILURE_REASON);
        values.put(COL_DUE_DATE, next);
        db.update(TABLE_SCHEDULED_PAYMENTS, values, COL_PAYMENT_ID + " = ?", new String[]{String.valueOf(paymentId)});

        String nextText = next;
        try {
            nextText = new SimpleDateFormat("MMM d, yyyy", Locale.US)
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(next));
        } catch (java.text.ParseException ignored) {
            // keep yyyy-MM-dd
        }
        return OperationResult.ok("Skipped. Next payment on " + nextText + ".");
    }

    /** How many due bills/transfers were paid and how many failed in one processDuePayments run. */
    public static class PaymentRunResult {
        public final int paid;
        public final int failed;

        PaymentRunResult(int paid, int failed) {
            this.paid = paid;
            this.failed = failed;
        }
    }

    /**
     * Pays every scheduled bill and sends every scheduled transfer for this user whose date has
     * arrived. If there isn't enough money or an account can't be used, that one is marked
     * 'failed' with the reason.
     *
     * A repeating one moves to its next date after each payment. If the app wasn't opened for a
     * while, this keeps going until every missed payment is made (or one fails).
     *
     * The app runs this whenever the home screen opens, since a phone app can't run on a server schedule.
     */
    public PaymentRunResult processDuePayments(long userId) {
        SQLiteDatabase db = getWritableDatabase();
        int paid = 0;
        int failed = 0;
        for (int i = 0; i < MAX_PAYMENTS_PER_RUN; i++) {
            long paymentId;
            // The one that's been due the longest. Each pass handles one payment, then looks again,
            // so a repeating one's next (already due) date is picked up on a later pass.
            try (Cursor c = db.query(TABLE_SCHEDULED_PAYMENTS, new String[]{COL_PAYMENT_ID},
                    COL_USER_ID + " = ? AND " + COL_PAYMENT_STATUS + " = 'scheduled' AND " + COL_DUE_DATE + " <= ?",
                    new String[]{String.valueOf(userId), today()}, null, null,
                    COL_DUE_DATE + " ASC, " + COL_PAYMENT_ID + " ASC", "1")) {
                if (!c.moveToFirst()) break;
                paymentId = c.getLong(0);
            }
            if (payOneBill(db, userId, paymentId)) paid++;
            else failed++;
        }
        return new PaymentRunResult(paid, failed);
    }

    /** Pays one due bill or sends one due transfer. True if it went through. */
    private boolean payOneBill(SQLiteDatabase db, long userId, long paymentId) {
        db.beginTransaction();
        try {
            ScheduledPayment p;
            int repeatDay;
            try (Cursor c = db.rawQuery(PAYMENT_SELECT + " WHERE p." + COL_PAYMENT_ID + " = ? AND p."
                    + COL_PAYMENT_STATUS + " = 'scheduled'", new String[]{String.valueOf(paymentId)})) {
                if (!c.moveToFirst()) return false;
                p = readPayment(c);
            }
            try (Cursor c = db.rawQuery("SELECT " + COL_REPEAT_DAY + " FROM " + TABLE_SCHEDULED_PAYMENTS
                    + " WHERE " + COL_PAYMENT_ID + " = ?", new String[]{String.valueOf(paymentId)})) {
                repeatDay = c.moveToFirst() && !c.isNull(0) ? c.getInt(0) : Integer.parseInt(p.getDueDate().substring(8, 10));
            }
            String note = " (" + (p.isRecurring() ? p.getFrequencyText().toLowerCase(Locale.US) : "scheduled") + ")";

            String reason;
            if (p.isTransfer()) {
                OperationResult r = p.getToRoutingNumber() == null
                        ? transferInTx(db, userId, p.getAccountNumber(), p.getToAccountNumber(), p.getAmount(), p.getMemo(), note)
                        : externalTransferInTx(db, userId, p.getAccountNumber(), p.getToRoutingNumber(),
                        p.getToAccountNumber(), p.getPayee().split(" · ")[0], p.getAmount(), p.getMemo(), note);
                reason = r.success ? null : r.message;
            } else {
                Account account = findAccount(db, p.getAccountNumber());
                reason = checkCanUse(account, userId);
                if (reason == null && toCents(account.getBalance()) < toCents(p.getAmount())) {
                    reason = "Not enough money in " + account.getDisplayName() + ".";
                }
                if (reason == null) {
                    changeBalance(db, p.getAccountNumber(), -p.getAmount());
                    logTransaction(db, p.getAccountNumber(), Transaction.TYPE_BILL_PAYMENT, p.getAmount(), null,
                            "Bill payment · " + p.getPayee()
                                    + (p.isRecurring() ? " (" + p.getFrequencyText().toLowerCase(Locale.US) + ")" : ""));
                }
            }

            ContentValues values = new ContentValues();
            if (reason == null) {
                values.put(COL_PAID_AT, System.currentTimeMillis());
                if (p.isRecurring()) {
                    // Stay scheduled, due again on the next date
                    values.put(COL_DUE_DATE, nextDueDate(p.getDueDate(), p.getFrequency(), p.getRepeatEvery(), repeatDay));
                } else {
                    values.put(COL_PAYMENT_STATUS, ScheduledPayment.STATUS_PAID);
                }
            } else {
                values.put(COL_PAYMENT_STATUS, ScheduledPayment.STATUS_FAILED);
                values.put(COL_FAILURE_REASON, reason);
            }
            db.update(TABLE_SCHEDULED_PAYMENTS, values, COL_PAYMENT_ID + " = ?",
                    new String[]{String.valueOf(paymentId)});
            db.setTransactionSuccessful();
            return reason == null;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * The date after dueDate for a repeating payment.
     *  daily / weekly:   dueDate + every days / every weeks
     *  monthly / yearly: every months / years later, on repeatDay — or that month's last day if
     *                    it's shorter ("2026-01-31" monthly -> "2026-02-28" -> "2026-03-31").
     */
    public static String nextDueDate(String dueDate, String frequency, int every, int repeatDay) {
        int year = Integer.parseInt(dueDate.substring(0, 4));
        int month = Integer.parseInt(dueDate.substring(5, 7)) - 1; // Calendar months are 0-11
        int day = Integer.parseInt(dueDate.substring(8, 10));
        Calendar cal = Calendar.getInstance();
        cal.clear();

        switch (frequency) {
            case ScheduledPayment.FREQUENCY_DAILY:
                cal.set(year, month, day);
                cal.add(Calendar.DAY_OF_MONTH, every);
                break;
            case ScheduledPayment.FREQUENCY_WEEKLY:
                cal.set(year, month, day);
                cal.add(Calendar.DAY_OF_MONTH, 7 * every);
                break;
            case ScheduledPayment.FREQUENCY_YEARLY:
                cal.set(year + every, month, 1);
                cal.set(Calendar.DAY_OF_MONTH, Math.min(repeatDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)));
                break;
            default: // monthly
                cal.set(year, month, 1);
                cal.add(Calendar.MONTH, every);
                cal.set(Calendar.DAY_OF_MONTH, Math.min(repeatDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)));
                break;
        }
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Today's date as "yyyy-MM-dd" (the format used for due dates). */
    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Dollars -> whole cents, so comparisons aren't thrown off by tiny decimal errors. */
    static long toCents(double amount) {
        return Math.round(amount * 100);
    }

    static String money(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }
}