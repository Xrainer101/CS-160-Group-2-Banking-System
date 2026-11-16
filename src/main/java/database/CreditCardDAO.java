package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class CreditCardDAO {

    /**
     * Adds a new credit card for a user.
     */
    public static boolean addCreditCard(int userId, String cardNickname, String cardholderName,
                                        String cardNumber, String expirationDate, String cvv,
                                        double creditLimit) {
        if (creditLimit <= 0) {
            System.out.println("Failed to add card: Credit limit must be greater than zero.");
            return false;
        }

        String sql = "INSERT INTO credit_cards " +
                     "(user_id, card_nickname, cardholder_name, card_number, expiration_date, cvv, credit_limit) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);

            if (cardNickname != null && !cardNickname.isEmpty()) {
                pstmt.setString(2, cardNickname);
            } else {
                pstmt.setNull(2, Types.VARCHAR);
            }

            pstmt.setString(3, cardholderName);
            pstmt.setString(4, cardNumber);
            pstmt.setString(5, expirationDate);
            pstmt.setString(6, cvv);
            pstmt.setDouble(7, creditLimit);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error adding credit card: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves all credit cards belonging to a user.
     */
    public static List<CreditCard> getCreditCardsByUserId(int userId) {
        List<CreditCard> cards = new ArrayList<>();
        String sql = "SELECT * FROM credit_cards WHERE user_id = ? ORDER BY created_at ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    cards.add(new CreditCard(
                        rs.getInt("card_id"),
                        rs.getInt("user_id"),
                        rs.getString("card_nickname"),
                        rs.getString("cardholder_name"),
                        rs.getString("card_number"),
                        rs.getString("expiration_date"),
                        rs.getString("cvv"),
                        rs.getDouble("credit_limit"),
                        rs.getDouble("current_balance"),
                        rs.getString("status")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching credit cards: " + e.getMessage());
        }
        return cards;
    }

    /**
     * Updates a card's status ('active', 'frozen', or 'closed').
     */
    public static boolean updateCardStatus(int cardId, String newStatus) {
        String normalized = newStatus.toLowerCase();
        if (!List.of("active", "frozen", "closed").contains(normalized)) {
            System.out.println("Invalid card status: " + newStatus);
            return false;
        }

        String sql = "UPDATE credit_cards SET status = ? WHERE card_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, normalized);
            pstmt.setInt(2, cardId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating card status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates a card's current balance or nickname (useful if the UI lets users simulate a charge or payment).
     */
    public static boolean updateCardBalance(int cardId, double newBalance) {
        if (newBalance < 0) {
            System.out.println("Balance cannot be negative.");
            return false;
        }

        String sql = "UPDATE credit_cards SET current_balance = ? WHERE card_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, newBalance);
            pstmt.setInt(2, cardId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating card balance: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes a credit card record.
     */
    public static boolean deleteCreditCard(int cardId) {
        String sql = "DELETE FROM credit_cards WHERE card_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, cardId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error deleting credit card: " + e.getMessage());
            return false;
        }
    }
}
