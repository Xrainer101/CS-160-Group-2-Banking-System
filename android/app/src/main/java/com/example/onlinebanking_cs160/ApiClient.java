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

    public static String login(String username, String password) throws Exception {
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
            return responseJson.getString("access_token");
        }

        String message = responseJson.optString(
                "detail",
                "Login failed"
        );

        throw new ApiException(statusCode, message);
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
}