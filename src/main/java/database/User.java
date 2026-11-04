package database;

public class User {
    private int userId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String role;
    private int failedLoginAttempts;

    // Constructor
    public User(int userId, String username, String firstName, String lastName, String email, String phoneNumber, String role, int failedLoginAttempts) {
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.failedLoginAttempts = failedLoginAttempts;
    }

    // Getters for the UI Toolkit to display profile information
    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getRole() { return role; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
}
