package com.example.onlinebanking_cs160;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hashes passwords with PBKDF2 and a random salt per user.
 *
 * The salt and settings are stored together with the hash in the password_hash column,
 * in the form  pbkdf2_sha1$iterations$salt$hash , so no extra column is needed.
 *
 * PBKDF2WithHmacSHA1 is used because the app's minSdk is 24
 * (PBKDF2WithHmacSHA256 is only available from API 26).
 */
public final class PasswordUtils {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String PREFIX = "pbkdf2_sha1";
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private PasswordUtils() { }

    public static String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + encode(salt) + "$" + encode(hash);
    }

    public static boolean verifyPassword(String password, String stored) {
        if (stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = decode(parts[2]);
            byte[] expected = decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            // Constant-time comparison
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing failed", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] decode(String text) {
        return Base64.decode(text, Base64.NO_WRAP);
    }
}
