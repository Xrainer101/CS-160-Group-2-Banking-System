package database;

/** One row from the users table (without the password hash). */
public class User {
    private final long userId;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String dateOfBirth;
    private final String email;
    private final String phoneNumber;
    private final String role;

    public User(long userId, String username, String firstName, String lastName,
                String dateOfBirth, String email, String phoneNumber, String role) {
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.role = role;
    }

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getFullName() { return firstName + " " + lastName; }
    public String getDateOfBirth() { return dateOfBirth; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getRole() { return role; }
}