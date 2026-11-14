package security;

public class AuthenitcationService {

    //* Hashes a user's password before storage */
    public static String preparePasswordForStorage(String password) {
        return PasswordUtil.hashPassword(password);
    }
    // Verifies login attempt
    public static boolean authenicateUser(String enteredPassword, String storedHash) {
        return PasswordUtil.verifyPassword(enteredPassword, storedHash);
    }

    // Test AuthenticationService
    public static void main(String[] args) {
        String originalPassword = "Password123";

        // "Registration"
        String storedHash = preparePasswordForStorage(originalPassword);
        System.out.println("Stored Hash: ");
        System.out.println(storedHash);

        // "Correct Login Attempt"
        boolean loginSuccess = authenicateUser("Password123", storedHash);
        System.out.println("Correct password:" + loginSuccess); // Should return true

        // "Incorrect Login Attempt"
        boolean loginFail = authenicateUser("WrongPassword", storedHash);
        System.out.println("Incorrect password:" + loginFail); // Should return false
    }
    
}
