package com.example.onlinebanking_cs160;

import android.content.Context;
import android.content.SharedPreferences;

/** Remembers which user is logged in, so they stay logged in after closing the app. */
public class SessionManager {
    private static final String PREFS_NAME = "session";
    private static final String KEY_USER_ID = "user_id";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void login(long userId) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply();
    }

    public boolean isLoggedIn() {
        return getUserId() != -1;
    }

    /** The logged-in user's id, or -1 if nobody is logged in. */
    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public void logout() {
        prefs.edit().clear().apply();
    }
}