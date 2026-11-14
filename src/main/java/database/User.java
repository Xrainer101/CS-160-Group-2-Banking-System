package database;

import java.time.LocalDateTime;

public class User {
    private int userId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String dateOfBirth;
    private String password_hash;
    private String role;
    private int failedLoginAttempts;
    private LocalDateTime lockout_until;
    private LocalDateTime created_at;

    // Constructor
    public User(int userId, String username, String firstName, String lastName, String email, String phoneNumber, String dateOfBirth, String password_hash, String role, int failedLoginAttempts, LocalDateTime lockout_until, LocalDateTime created_at) {
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.password_hash = password_hash;
        this.role = role;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockout_until = lockout_until;
        this.created_at = created_at;
    }

    // Getters for the UI Toolkit to display profile information
    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getDateOfBirth() { return dateOfBirth; }
    public String getPasswordHash() { return password_hash; }
    public String getRole() { return role; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public LocalDateTime getLockoutUntil() { return lockout_until; }
    public LocalDateTime getCreatedAt() { return created_at; }
}
