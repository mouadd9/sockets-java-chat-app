package org.example.shared.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.example.shared.model.Contact;

public class ContactDAO {

    public void createContact(final Contact contact) {
        final String sql = "INSERT INTO contacts (user_id, contact_user_id, added_at) VALUES (?,?,?)";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, contact.getUserId());
            stmt.setLong(2, contact.getContactUserId());
            stmt.setTimestamp(3, Timestamp.valueOf(contact.getAddedAt()));
            stmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    public Contact findContactById(final long id) {
        final String sql = "SELECT * FROM contacts WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final Contact contact = new Contact();
                    // Remplacer par l'attribution des champs si un id existe dans Contact
                    contact.setUserId(rs.getLong("user_id"));
                    contact.setContactUserId(rs.getLong("contact_user_id"));
                    contact.setAddedAt(rs.getTimestamp("added_at").toLocalDateTime());
                    return contact;
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean deleteContact(final long userId, final long contactId) {
        final String sql = "DELETE FROM contacts WHERE user_id = ? AND contact_user_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, contactId);
            final int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<String> getContactsByUserId(final long userId) throws IOException {
        final List<String> contactEmails = new ArrayList<>();
        final String sql = "SELECT u.email FROM contacts c JOIN users u ON c.contact_user_id = u.id WHERE c.user_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    contactEmails.add(rs.getString("email"));
                }
            }
        } catch (final SQLException e) {
            throw new IOException("Erreur lors de la récupération des contacts", e);
        }
        return contactEmails;
    }
}
