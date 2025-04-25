package org.example.shared.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.example.shared.model.Message;
import org.example.shared.model.enums.MessageStatus;
import org.example.shared.model.enums.MessageType;

public class MessageDAO {

    public void createMessage(final Message message) {
        final String sql = "INSERT INTO messages (sender_user_id, receiver_user_id, group_id, content, timestamp, status, "
                + "message_type, file_name, file_size, mime_type) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, message.getSenderUserId());
            if (message.getReceiverUserId() != null) {
                stmt.setLong(2, message.getReceiverUserId());
            } else {
                stmt.setNull(2, Types.BIGINT);
            }
            if (message.getGroupId() != null) {
                stmt.setLong(3, message.getGroupId());
            } else {
                stmt.setNull(3, Types.BIGINT);
            }
            stmt.setString(4, message.getContent());
            stmt.setTimestamp(5, Timestamp.valueOf(message.getTimestamp()));
            stmt.setString(6, message.getStatus().name());
            stmt.setString(7, message.getType().name());
            // Set multimedia fields
            if (message.getFileName() != null) {
                stmt.setString(8, message.getFileName());
            } else {
                stmt.setNull(8, Types.VARCHAR);
            }

            if (message.getFileSize() != null) {
                stmt.setLong(9, message.getFileSize());
            } else {
                stmt.setNull(9, Types.BIGINT);
            }

            if (message.getMimeType() != null) {
                stmt.setString(10, message.getMimeType());
            } else {
                stmt.setNull(10, Types.VARCHAR);
            }
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    message.setId(generatedKeys.getLong(1));
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    public Message findMessageById(final long id) {
        final String sql = "SELECT * FROM messages WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return extractMessageFromResultSet(rs);
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Message> getPendingMessagesForUser(final long receiverUserId) throws SQLException {
        final List<Message> messages = new ArrayList<>();
        // Récupérer les messages directs et les messages de groupe pour lesquels
        // l'utilisateur est membre
        final String sql = "SELECT * FROM messages " +
                "WHERE ((receiver_user_id = ? AND status = ?) " +
                "OR (group_id IS NOT NULL AND status = ? " +
                "    AND group_id IN (SELECT group_id FROM group_memberships WHERE user_id = ?)))";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, receiverUserId);
            stmt.setString(2, MessageStatus.QUEUED.name());
            stmt.setString(3, MessageStatus.QUEUED.name());
            stmt.setLong(4, receiverUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final Message message = new Message();
                    message.setId(rs.getLong("id"));
                    message.setSenderUserId(rs.getLong("sender_user_id"));
                    final long rec = rs.getLong("receiver_user_id");
                    if (!rs.wasNull()) {
                        message.setReceiverUserId(rec);
                    }
                    final long grp = rs.getLong("group_id");
                    if (!rs.wasNull()) {
                        message.setGroupId(grp);
                    }
                    message.setContent(rs.getString("content"));
                    final Timestamp ts = rs.getTimestamp("timestamp");
                    if (ts != null) {
                        message.setTimestamp(ts.toLocalDateTime());
                    }
                    message.setStatus(MessageStatus.valueOf(rs.getString("status")));
                    messages.add(message);
                }
            }
        }
        return messages;
    }

    public List<Message> getConversation(final long user1Id, final long user2Id) {
        final List<Message> messages = new ArrayList<>();
        final String sql = "SELECT * FROM messages WHERE " +
                " (sender_user_id = ? AND receiver_user_id = ?) OR (sender_user_id = ? AND receiver_user_id = ?)";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, user1Id);
            stmt.setLong(2, user2Id);
            stmt.setLong(3, user2Id);
            stmt.setLong(4, user1Id);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(extractMessageFromResultSet(rs));
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    public boolean deleteMessage(final long messageId) throws SQLException {
        final String sql = "DELETE FROM messages WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, messageId);
            final int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public void updateMessageStatus(final long messageId, final MessageStatus status) throws SQLException {
        final String sql = "UPDATE messages SET status = ? WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setLong(2, messageId);
            stmt.executeUpdate();
        }
    }

    // Helper method to extract a Message from a ResultSet
    private Message extractMessageFromResultSet(final ResultSet rs) throws SQLException {
        final Message message = new Message();
        message.setId(rs.getLong("id"));
        message.setSenderUserId(rs.getLong("sender_user_id"));

        final long receiverUserId = rs.getLong("receiver_user_id");
        if (!rs.wasNull()) {
            message.setReceiverUserId(receiverUserId);
        }

        final long groupId = rs.getLong("group_id");
        if (!rs.wasNull()) {
            message.setGroupId(groupId);
        }

        message.setContent(rs.getString("content"));
        final Timestamp ts = rs.getTimestamp("timestamp");
        if (ts != null) {
            message.setTimestamp(ts.toLocalDateTime());
        }

        message.setStatus(MessageStatus.valueOf(rs.getString("status")));

        // Extract multimedia fields
        String messageTypeStr = rs.getString("message_type");
        if (messageTypeStr != null) {
            message.setType(MessageType.valueOf(messageTypeStr));
        } else {
            message.setType(MessageType.TEXT); // Default to TEXT for backward compatibility
        }

        message.setFileName(rs.getString("file_name"));

        final long fileSize = rs.getLong("file_size");
        if (!rs.wasNull()) {
            message.setFileSize(fileSize);
        }

        message.setMimeType(rs.getString("mime_type"));

        return message;
    }
}
