package com.example.onlinebanking_cs160;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    private static final String BASE_URL = "http://10.0.2.2:8000";

    private ApiClient() {
    }

    public static LoginResult login(String username, String password) throws Exception {
        URL url = new URL(BASE_URL + "/api/auth/login");
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        JSONObject requestBody = new JSONObject();
        requestBody.put("username", username);
        requestBody.put("password", password);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(
                    requestBody.toString().getBytes(StandardCharsets.UTF_8)
            );
        }

        int statusCode = connection.getResponseCode();

        InputStream responseStream =
                statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        String responseText = readResponse(responseStream);
        JSONObject responseJson = new JSONObject(responseText);

        if (statusCode >= 200 && statusCode < 300) {
            JSONObject userJson = responseJson.getJSONObject("user");

            return new LoginResult(
                responseJson.getString("access_token"),
                userJson.getLong("user_id"),
                userJson.getString("name"),
                userJson.getString("email"),
                userJson.getString("username")
            );    
        }

        String message = responseJson.optString(
                "detail",
                "Login failed"
        );

        throw new ApiException(statusCode, message);
    }

    // Gets the currently logged in user's profile from the backend
    public static UserProfile getCurrentUser(String token) throws Exception {
        URL url = new URL(BASE_URL + "/api/auth/me");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token); // creates authorized header
        int statusCode = connection.getResponseCode();

        InputStream responseStream = statusCode >= 200 && statusCode < 300 
            ? connection.getInputStream() : connection.getErrorStream();

        String responseText = readResponse(responseStream);
        JSONObject responseJson = new JSONObject(responseText);

        if (statusCode >= 200 && statusCode < 300) {
            return new UserProfile(
                responseJson.getLong("user_id"),
                responseJson.getString("name"),
                responseJson.getString("email"),
                responseJson.getString("username")
            );
        }
        String message = responseJson.optString("detail","Could not get user profile");
        throw new ApiException(statusCode,message);
    }

    // Added a method for register and login from the backend
    public static void register(
        String name,
        String email,
        String username,
        String password,
        String phoneNumber
    ) throws Exception {
        URL url = new URL(BASE_URL + "/api/auth/register");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type","application/json");
        connection.setDoOutput(true);

        JSONObject requestBody = new JSONObject();
        requestBody.put("name",name);
        requestBody.put("email",email);
        requestBody.put("username",username);
        requestBody.put("password",password);
        requestBody.put("phone_number",phoneNumber);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();

        if (statusCode < 200 || statusCode >= 300) {
            InputStream errorStream = connection.getErrorStream();
            String responseText = readResponse(errorStream);
            JSONObject responseJson = new JSONObject(responseText);

            String message = responseJson.optString("detail","Registration failed");
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
        public final long userId;
        public final String name;
        public final String email;
        public final String username;

        public LoginResult(String accessToken,long userId, String name, String email, String username) {
            this.accessToken = accessToken;
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.username = username;
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
}