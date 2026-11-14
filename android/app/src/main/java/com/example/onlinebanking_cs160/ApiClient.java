package com.example.onlinebanking_cs160;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.List;

public final class ApiClient {
    private static final String BASE_URL = "http://10.0.2.2:8000";
    private static final String AUTH0_DOMAIN = "dev-fg7mzenn1walaym8.us.auth0.com"; 
    private static final String AUTH0_CLIENT_ID = "6Re8Wve0eqBGvcsu4zdXU2IBKkIEelvv";
    private static final String AUTH0_CONNECTION = "Username-Password-Authentication";
    private static final String AUTH0_AUDIENCE = "https://team2bank-api";

    private ApiClient() {
    }

    // Login user
    // Sends login request to the backend and if successful returns the access token
    public static LoginResult login(String username, String password) throws Exception {
        URL url = new URL("https://" + AUTH0_DOMAIN + "/oauth/token");
        HttpURLConnection connection =(HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setDoOutput(true);

        String requestBody = "grant_type=" + encode("http://auth0.com/oauth/grant-type/password-realm")
            + "&client_id=" + encode(AUTH0_CLIENT_ID)
            + "&username=" + encode(username)
            + "&password=" + encode(password)
            + "&realm=" + encode(AUTH0_CONNECTION)
            + "&audience=" + encode(AUTH0_AUDIENCE)
            + "&scope=" + encode("openid profile email");

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        InputStream responseStream = statusCode >= 200 && statusCode < 300
            ? connection.getInputStream()
            : connection.getErrorStream();

        String responseText = readResponse(responseStream);
        JSONObject responseJson = new JSONObject(responseText);

        if (statusCode >= 200 && statusCode < 300) {
            return new LoginResult(responseJson.getString("access_token"));
        }

        String error = responseJson.optString("error", "");
        String message = responseJson.optString("error_description", "Login failed");
        if ("mfa_required".equals(error)) {
            throw new MfaRequiredException(statusCode, message, responseJson.optString("mfa_token"));
        }
        throw new ApiException(statusCode, message);
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    // Sends the six-digit Duo code to Auth0 to finish the login
    public static LoginResult completeMfaLogin(String mfaToken, String otp) throws Exception {
        URL url = new URL("https://" + AUTH0_DOMAIN + "/oauth/token");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setDoOutput(true);

        String requestBody = "grant_type=" + encode("http://auth0.com/oauth/grant-type/mfa-otp")
        + "&client_id=" + encode(AUTH0_CLIENT_ID)
        + "&mfa_token=" + encode(mfaToken)
        + "&otp=" + encode(otp)
        + "&audience=" + encode(AUTH0_AUDIENCE)
        + "&scope=" + encode("openid profile email");

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        InputStream responseStream = statusCode >= 200 && statusCode < 300
            ? connection.getInputStream() : connection.getErrorStream();
        JSONObject responseJson = new JSONObject(readResponse(responseStream));

        if (statusCode >= 200 && statusCode < 300) {
            return new LoginResult(responseJson.getString("access_token"));
        }

        String message = responseJson.optString("error_description","MFA verification failed");
        throw new ApiException(statusCode, message);
    }

    // Starts first time Duo enrollment and returns the QR-code information from auth0
    public static MfaEnrollment startTotpEnrollment(String mfaToken) throws Exception {
        URL url = new URL("https://" + AUTH0_DOMAIN + "/mfa/associate");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + mfaToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        JSONObject requestBody = new JSONObject();
        requestBody.put("authenticator_types", new org.json.JSONArray().put("otp"));

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        InputStream responseStream = statusCode >= 200 && statusCode < 300 ? connection.getInputStream() : connection.getErrorStream();
        JSONObject responseJson = new JSONObject(readResponse(responseStream));

        if (statusCode >= 200 && statusCode < 300) {
            return new MfaEnrollment(responseJson.getString("barcode_uri"));
        }
        throw new ApiException(statusCode,responseJson.optString("error_description", "Could not start MFA enrollment"));
    }

    // Gets current user information from the backend
    public static UserProfile getCurrentUser(String token) throws Exception {
        URL url = new URL(BASE_URL + "/api/auth/me");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        int statusCode = connection.getResponseCode();

        InputStream responseStream = statusCode >= 200 && statusCode < 300 
            ? connection.getInputStream() : connection.getErrorStream();

        String responseText = readResponse(responseStream);
        JSONObject responseJson = new JSONObject(responseText);

    if (statusCode >= 200 && statusCode < 300) {
        // The backend returns the local banking profile inside banking_user
        // Keep the top-level fallback for older backend responses
        JSONObject bankingUser = responseJson.optJSONObject("banking_user");
        JSONObject profileJson = bankingUser != null ? bankingUser : responseJson;

        long userId = profileJson.optLong("user_id", responseJson.optLong("user_id", 0));
        String fullName = profileJson.optString("full_name", "");
        if (fullName.trim().isEmpty()) {
            fullName = profileJson.optString("name", responseJson.optString("name", ""));
        }
        String email = profileJson.optString("email", responseJson.optString("email", ""));
        String username = profileJson.optString("username", responseJson.optString("username", ""));

        return new UserProfile(userId, fullName, email, username);
    }
        String message = responseJson.optString("detail","Could not get user profile");
        throw new ApiException(statusCode,message);
    }

    // Gets the authenticated user's accounts and transactions from the backend
    public static AccountsResponse getAccounts(String token) throws Exception {
        URL url = new URL(BASE_URL + "/api/accounts");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        int statusCode = connection.getResponseCode();

        InputStream responseStream = statusCode >= 200 && statusCode < 300
            ? connection.getInputStream() : connection.getErrorStream();
        JSONObject responseJson = new JSONObject(readResponse(responseStream));

        if (statusCode < 200 || statusCode >= 300) {
            String message = responseJson.optString("detail", "Could not get accounts");
            throw new ApiException(statusCode, message);
        }
        List<BankAccount> accounts = new java.util.ArrayList<>();
        JSONArray accountArray = responseJson.optJSONArray("accounts");
        if (accountArray != null) {
            for (int i = 0; i < accountArray.length(); i++) {
                JSONObject account = accountArray.getJSONObject(i);
                accounts.add(new BankAccount(
                    account.getString("type"),
                    account.getString("number"),
                    account.getDouble("balance"),
                    account.getDouble("available"),
                    account.getString("status")
                ));
            }
        }
        return new AccountsResponse(accounts);
    }

    // Registers a user in auth0
    // Checks the banking database before Auth0 creates the account
    public static void checkProfileAvailability(String email,String username,String phoneNumber) throws Exception {
        URL url = new URL(BASE_URL + "/api/auth/check-profile-availability");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        JSONObject requestBody = new JSONObject();
        requestBody.put("email", email);
        requestBody.put("username", username);
        requestBody.put("phone_number", phoneNumber);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        InputStream responseStream = statusCode >= 200 && statusCode < 300
            ? connection.getInputStream()
            : connection.getErrorStream();
        String response = readResponse(responseStream);

        if (statusCode < 200 || statusCode >= 300) {
            JSONObject responseJson = new JSONObject(response);
            String message = responseJson.optString("detail","Unable to check profile availability");
            throw new ApiException(statusCode, message);
        }
        connection.disconnect();
    }

    public static void register(
        String email,
        String username,
        String password
    ) throws Exception {
        URL url = new URL("https://" + AUTH0_DOMAIN + "/dbconnections/signup");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type","application/json");
        connection.setDoOutput(true);

        JSONObject requestBody = new JSONObject();
        requestBody.put("client_id", AUTH0_CLIENT_ID);
        requestBody.put("connection", AUTH0_CONNECTION);
        requestBody.put("email",email);
        requestBody.put("username",username);
        requestBody.put("password",password);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
        }
        int statusCode = connection.getResponseCode();

        if (statusCode < 200 || statusCode >= 300) {
            InputStream errorStream = connection.getErrorStream();
            String responseText = readResponse(errorStream);
            JSONObject responseJson = new JSONObject(responseText);

            String message = responseJson.optString("description",responseJson.optString("error_description", "Registration failed"));
            throw new ApiException(statusCode, message);
        }
        connection.disconnect();
    }

    // Asks Auth0 to send a password-reset email
    public static void requestPasswordReset(String email) throws Exception {
        URL url = new URL("https://" + AUTH0_DOMAIN + "/dbconnections/change_password");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        JSONObject requestBody = new JSONObject();
        requestBody.put("client_id", AUTH0_CLIENT_ID);
        requestBody.put("email", email);
        requestBody.put("connection", AUTH0_CONNECTION);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            InputStream errorStream = connection.getErrorStream();
            JSONObject responseJson = new JSONObject(readResponse(errorStream));
            String message = responseJson.optString("error_description","Password reset request failed");
            throw new ApiException(statusCode, message);
        }
        connection.disconnect();
    }

    private static String readResponse(InputStream inputStream)
            throws Exception {
        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        return response.toString();
    }

    // Data that is returned after a successful backend login
    public static class LoginResult {
        public final String accessToken;

        public LoginResult(String accessToken) {
            this.accessToken = accessToken;
        }
    }
    // Exception thrown when multi-factor authentication is required for login
    public static class MfaRequiredException extends ApiException {
        public final String mfaToken;
        public MfaRequiredException(int statusCode, String message, String mfaToken) {
            super(statusCode, message);
            this.mfaToken = mfaToken;
        }
    }

    public static class MfaEnrollment {
        public final String barcodeUri;

        public MfaEnrollment(String barcodeUri) {
            this.barcodeUri = barcodeUri;
        }
    }

    public static class ApiException extends Exception {
        private final int statusCode;
        public ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    // user information returned by the backend
    public static class UserProfile {
        public final long userId;
        public final String name;
        public final String email;
        public final String username;

        public UserProfile(long userId, String name, String email, String username){
            this.userId=userId;
            this.name=name;
            this.email=email;
            this.username=username;
        }
    }
    public static class AccountsResponse {
        public final java.util.List<BankAccount> accounts;

        public AccountsResponse(java.util.List<BankAccount> accounts) {
            this.accounts = accounts;
        }
    }
    public static class BankAccount {
        public final String type;
        public final String number;
        public final double balance;
        public final double available;
        public final String status;

        public BankAccount(String type, String number, double balance, double available, String status) {
            this.type = type;
            this.number = number;
            this.balance = balance;
            this.available = available;
            this.status = status;
        }
    }
}
