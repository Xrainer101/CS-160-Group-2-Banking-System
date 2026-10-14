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
    // Bumped from 2 to 3 because the users table gained a date_of_birth column
    private static final int DATABASE_VERSION = 3;
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
        // Development only: wipes all data. Drop the child table first because of the foreign key.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BANK_ACCOUNTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // REGISTRATION

    public enum RegisterResult { SUCCESS, USERNAME_TAKEN, EMAIL_TAKEN, PHONE_TAKEN, ERROR }
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

        try {
            getWritableDatabase().insertOrThrow(TABLE_USERS, null, values);
            return RegisterResult.SUCCESS;
        } catch (SQLiteConstraintException e) {
            // Someone registered the same details between the check above and this insert
            return RegisterResult.USERNAME_TAKEN;
        } catch (Exception e) {
            return RegisterResult.ERROR;
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

    // LOGIN

    public static class LoginResult {
        public enum Status { SUCCESS, INVALID_CREDENTIALS, LOCKED }

        public final Status status;
        public final User user;            // set when status == SUCCESS
        public final long lockedUntil;     // set when status == LOCKED (millis since 1970)
        public final int attemptsLeft;     // set when status == INVALID_CREDENTIALS and the username exists

        private LoginResult(Status status, User user, long lockedUntil, int attemptsLeft) {
            this.status = status;
            this.user = user;
            this.lockedUntil = lockedUntil;
            this.attemptsLeft = attemptsLeft;
        }
    }

    public LoginResult loginUser(String username, String password) {
        SQLiteDatabase db = getWritableDatabase();

        long userId;
        String storedHash;
        int failedAttempts;
        long lockoutUntil;

        try (Cursor cursor = db.query(TABLE_USERS,
                new String[]{COL_USER_ID, COL_PASSWORD_HASH, COL_FAILED_LOGIN_ATTEMPTS, COL_LOCKOUT_UNTIL},
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
        }

        long now = System.currentTimeMillis();
        if (lockoutUntil > now) {
            return new LoginResult(LoginResult.Status.LOCKED, null, lockoutUntil, 0);
        }

        ContentValues update = new ContentValues();
        String[] whereArgs = {String.valueOf(userId)};

        if (PasswordUtils.verifyPassword(password, storedHash)) {
            // Correct password: reset the counter and clear any old lock
            update.put(COL_FAILED_LOGIN_ATTEMPTS, 0);
            update.putNull(COL_LOCKOUT_UNTIL);
            db.update(TABLE_USERS, update, COL_USER_ID + " = ?", whereArgs);
            return new LoginResult(LoginResult.Status.SUCCESS, getUserById(userId), 0, 0);
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