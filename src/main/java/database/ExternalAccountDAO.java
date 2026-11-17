package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class ExternalAccountDAO {
    /**
     * Links an external bank account to a user.
     */
    public static boolean addExternalAccount(int userId, String bankName, String accountHolderName,
                                             String accountNumber, String routingNumber,
                                             String accountType, String nickname) {
        String sql = "INSERT INTO external_accounts " +
                     "(user_id, bank_name, account_holder_name, account_number, routing_number, account_type, nickname) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setString(2, bankName);
            pstmt.setString(3, accountHolderName);
            pstmt.setString(4, accountNumber);
            pstmt.setString(5, routingNumber);
            pstmt.setString(6, accountType.toLowerCase());

            if (nickname != null && !nickname.isEmpty()) {
                pstmt.setString(7, nickname);
            } else {
                pstmt.setNull(7, Types.VARCHAR);
            }

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error linking external account: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fetches all external accounts linked to a specific user ID.
     */
    public static List<ExternalAccount> getExternalAccountsByUserId(int userId) {
        List<ExternalAccount> accounts = new ArrayList<>();
        String sql = "SELECT * FROM external_accounts WHERE user_id = ? ORDER BY created_at ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    accounts.add(new ExternalAccount(
                        rs.getInt("external_account_id"),
                        rs.getInt("user_id"),
                        rs.getString("bank_name"),
                        rs.getString("account_holder_name"),
                        rs.getString("account_number"),
                        rs.getString("routing_number"),
                        rs.getString("account_type"),
                        rs.getString("nickname")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching external accounts: " + e.getMessage());
        }
        return accounts;
    }

    /**
     * Fetches a single external account by its primary key ID.
     */
    public static ExternalAccount getExternalAccountById(int externalAccountId) {
        String sql = "SELECT * FROM external_accounts WHERE external_account_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, externalAccountId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new ExternalAccount(
                        rs.getInt("external_account_id"),
                        rs.getInt("user_id"),
                        rs.getString("bank_name"),
                        rs.getString("account_holder_name"),
                        rs.getString("account_number"),
                        rs.getString("routing_number"),
                        rs.getString("account_type"),
                        rs.getString("nickname")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching external account: " + e.getMessage());
        }
        return null;
    }

    /**
     * Unlinks (deletes) an external account.
     */
    public static boolean deleteExternalAccount(int externalAccountId) {
        String sql = "DELETE FROM external_accounts WHERE external_account_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, externalAccountId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error removing external account: " + e.getMessage());
            return false;
        }
    }
}
