package com.example.onlinebanking_cs160;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import database.DatabaseManager;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Auth0LocalIntegrationTest {
    private Context context;
    private DatabaseManager database;

    @Before public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getDatabasePath(String name) {
                return super.getDatabasePath("auth0_merge_test_" + name);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory) {
                return super.openOrCreateDatabase("auth0_merge_test_" + name, mode, factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                return super.openOrCreateDatabase("auth0_merge_test_" + name, mode, factory, handler);
            }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("auth0_merge_test_" + name, mode);
            }
        };
        target.deleteDatabase("auth0_merge_test_online_bank.db");
        new SessionManager(context).logout();
        database = new DatabaseManager(context);
    }

    @After public void tearDown() {
        database.close();
        context.deleteDatabase("auth0_merge_test_online_bank.db");
        new SessionManager(context).logout();
    }

    @Test public void repeatLoginPreservesAccountsAndSeparatesUsers() {
        DatabaseManager.LoginResult first = database.loginAuth0User(
                "auth0|one", "First User", "first@example.com", "first");
        long firstId = first.user.getUserId();
        String firstAccount = database.getAccountsForUser(firstId).get(0).getAccountNumber();
        DatabaseManager.LoginResult second = database.loginAuth0User(
                "auth0|two", "Second User", "second@example.com", "second");
        assertNotEquals(firstId, second.user.getUserId());
        assertNull(database.getAccount(second.user.getUserId(), firstAccount));
        DatabaseManager.LoginResult again = database.loginAuth0User(
                "auth0|one", "First User", "first@example.com", "first");
        assertEquals(firstId, again.user.getUserId());
        assertTrue(again.previousLogin > 0);
        assertEquals(1, database.getAccountsForUser(firstId).size());
        assertEquals(firstAccount, database.getAccountsForUser(firstId).get(0).getAccountNumber());
    }

    @Test public void existingLocalProfileCannotBeClaimedByMatchingEmail() {
        assertEquals(DatabaseManager.RegisterResult.SUCCESS, database.registerUser(
                "legacy", "Legacy", "User", null, "legacy@example.com", "5551234567", "Password123!"));
        try {
            database.loginAuth0User("auth0|stranger", "Another User", "legacy@example.com", "another");
            fail("A matching email must not claim existing local data");
        } catch (SQLiteConstraintException expected) {
            assertNotNull(database.loginUser("legacy", "Password123!").user);
        }
    }

    @Test public void sessionsUseLocalIdsAndRejectOldBackendIdSessions() {
        SessionManager session = new SessionManager(context);
        session.login("token", 999, "Old session");
        assertFalse(session.isLoggedIn());
        session.login("token", 3, "Local user", 123);
        SessionManager restored = new SessionManager(context);
        assertTrue(restored.isLoggedIn());
        assertEquals(3, restored.getUserId());
        assertEquals(123, restored.getPreviousLogin());
        restored.logout();
        assertFalse(session.isLoggedIn());
        assertEquals(-1, session.getUserId());
    }

    @Test public void upgradeFromVersionFourKeepsExistingAccounts() {
        database.close();
        try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath("online_bank.db"), null)) {
            old.execSQL("CREATE TABLE users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, first_name TEXT NOT NULL, last_name TEXT NOT NULL, date_of_birth TEXT, email TEXT NOT NULL UNIQUE, phone_number TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT DEFAULT 'customer', login_attempts INTEGER DEFAULT 0, lockout_until INTEGER, last_login_at INTEGER, created_at TEXT)");
            old.execSQL("CREATE TABLE bank_accounts (account_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, account_number TEXT NOT NULL UNIQUE, account_type TEXT DEFAULT 'checking', balance REAL DEFAULT 0, is_active INTEGER DEFAULT 1, created_at TEXT)");
            old.execSQL("INSERT INTO users (user_id,username,first_name,last_name,email,phone_number,password_hash) VALUES (77,'old','Old','User','old@example.com','5550000000','')");
            old.execSQL("INSERT INTO bank_accounts (user_id,account_number,balance) VALUES (77,'1234567890',25)");
            old.setVersion(4);
        }
        database = new DatabaseManager(context);
        assertEquals(25, database.getAccountsForUser(77).get(0).getBalance(), 0.001);
        DatabaseManager.LoginResult fresh = database.loginAuth0User(
                "auth0|new", "New User", "new@example.com", "new");
        assertNotEquals(77, fresh.user.getUserId());
        assertNull(database.getAccount(fresh.user.getUserId(), "1234567890"));
    }
}
