package security;
import org.mindrot.jbcrypt.BCrypt;


public class PasswordUtil {
    /* Hash a plain text password */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    } 
    public static boolean verifyPassword(String password, String storedHash) {
        return BCrypt.checkpw(password, storedHash);
    }


    public static void main(String[] args) {
        String password = "Password123";
        String hash = hashPassword(password);

        System.out.println("Hash: ");
        System.out.println(hash);

        System.out.println(verifyPassword("Password123", hash));
        System.out.println(verifyPassword("WrongPassword", hash));
    }
}