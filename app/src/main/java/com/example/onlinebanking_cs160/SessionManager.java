package com.example.onlinebanking_cs160;

import android.content.Context;
import android.content.SharedPreferences;

/** Remembers which user is logged in, so they stay logged in after closing the app. */
public class SessionManager {
    private static final String PREFS_NAME = "session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_PREVIOUS_LOGIN = "previous_login";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void login(long userId, long previousLogin) {
        prefs.edit().putLong(KEY_USER_ID, userId).putLong(KEY_PREVIOUS_LOGIN, previousLogin).apply();
    }

    public boolean isLoggedIn() {
        return getUserId() != -1;
    }

    // logged in user's id, or -1 if nobody is logged in
    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    // login before the current one, or 0 if this is the first one
    public long getPreviousLogin() {
        return prefs.getLong(KEY_PREVIOUS_LOGIN, 0);
    }

    public void logout() {
        prefs.edit().clear().apply();
    }
}