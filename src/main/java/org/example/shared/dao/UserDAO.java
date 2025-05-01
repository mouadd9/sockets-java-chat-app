package org.example.shared.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;

import org.example.shared.model.User;

public class UserDAO {

    public void createUser(final User user) {
        final String sql = "INSERT INTO users (email, display_name, password_hash, is_online, created_at, last_login_at, profile_picture_url, public_key) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, user.getEmail());
            stmt.setString(2, user.getDisplayName());
            stmt.setString(3, user.getPasswordHash());
            stmt.setBoolean(4, user.isOnline());
            stmt.setTimestamp(5, Timestamp.valueOf(user.getCreatedAt()));
            stmt.setTimestamp(6, user.getLastLoginAt() != null ? Timestamp.valueOf(user.getLastLoginAt()) : null);
            stmt.setString(7, user.getProfilePictureUrl());
            stmt.setString(8, user.getPublicKey());

            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setId(generatedKeys.getLong(1));
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    public User findUserById(final long id) {
        final String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User findUserByEmail(final String email) {
        final String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean updateUser(final User user) {
        final String sql = "UPDATE users SET email=?, display_name=?, password_hash=?, is_online=?, created_at=?, last_login_at=?, profile_picture_url=?, public_key=? WHERE id=?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getEmail());
            stmt.setString(2, user.getDisplayName());
            stmt.setString(3, user.getPasswordHash());
            stmt.setBoolean(4, user.isOnline());
            stmt.setTimestamp(5, Timestamp.valueOf(user.getCreatedAt()));
            stmt.setTimestamp(6, user.getLastLoginAt() != null ? Timestamp.valueOf(user.getLastLoginAt()) : null);
            stmt.setString(7, user.getProfilePictureUrl());
            stmt.setString(8, user.getPublicKey());
            stmt.setLong(9, user.getId());

            final int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (final SQLException e) {
            System.err.println("Erreur lors de la mise à jour de l'utilisateur: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void deleteUser(final long id) {
        final String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean updatePublicKey(final long userId, final String publicKeyString) {
        final String sql = "UPDATE users SET public_key = ? WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, publicKeyString);
            pstmt.setLong(2, userId);

            final int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (final SQLException e) {
            System.err.println("Erreur SQL lors de la mise à jour de la clé publique pour l'ID " + userId + ": " + e.getMessage());
            return false;
        }
    }

    public Optional<String> getPublicKey(final long userId) {
        final String sql = "SELECT public_key FROM users WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("public_key"));
                }
            }
        } catch (final SQLException e) {
            System.err.println("Erreur SQL lors de la récupération de la clé publique pour l'ID " + userId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    private User mapRowToUser(final ResultSet rs) throws SQLException {
        final User user = new User();
        user.setId(rs.getLong("id"));
        user.setEmail(rs.getString("email"));
        user.setDisplayName(rs.getString("display_name"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setOnline(rs.getBoolean("is_online"));
        user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        final Timestamp ts = rs.getTimestamp("last_login_at");
        if (ts != null) {
            user.setLastLoginAt(ts.toLocalDateTime());
        }
        user.setProfilePictureUrl(rs.getString("profile_picture_url"));
        user.setPublicKey(rs.getString("public_key"));
        return user;
    }
}
