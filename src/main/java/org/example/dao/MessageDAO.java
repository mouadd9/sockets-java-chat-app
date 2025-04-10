package org.example.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.example.model.Message;
import org.example.model.enums.MessageStatus;

public class MessageDAO {

    public void createMessage(final Message message) {
        final String sql = "INSERT INTO messages (sender_user_id, receiver_user_id, group_id, content, timestamp, status) VALUES (?,?,?,?,?,?)";
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
                    message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                    message.setStatus(MessageStatus.valueOf(rs.getString("status")));
                    return message;
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Message> getPendingMessagesForReceiver(long receiverUserId) throws SQLException {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE receiver_user_id = ? AND status = ?";
        try (Connection conn = JDBCUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, receiverUserId);
            stmt.setString(2, MessageStatus.QUEUED.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message message = new Message();
                    message.setId(rs.getLong("id"));
                    message.setSenderUserId(rs.getLong("sender_user_id"));
                    long receiver = rs.getLong("receiver_user_id");
                    if (rs.wasNull()) {
                        message.setReceiverUserId(null);
                    } else {
                        message.setReceiverUserId(receiver);
                    }
                    long group = rs.getLong("group_id");
                    if (rs.wasNull()) {
                        message.setGroupId(null);
                    } else {
                        message.setGroupId(group);
                    }
                    message.setContent(rs.getString("content"));
                    message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                    message.setStatus(MessageStatus.valueOf(rs.getString("status")));
                    messages.add(message);
                }
            }
        }
        return messages;
    }

    public List<Message> getConversation(final long user1Id, final long user2Id) {
        List<Message> messages = new ArrayList<>();
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
                    Message message = new Message();
                    message.setId(rs.getLong("id"));
                    message.setSenderUserId(rs.getLong("sender_user_id"));
                    long receiver = rs.getLong("receiver_user_id");
                    if (!rs.wasNull()) {
                        message.setReceiverUserId(receiver);
                    }
                    long group = rs.getLong("group_id");
                    if (!rs.wasNull()) {
                        message.setGroupId(group);
                    }
                    message.setContent(rs.getString("content"));
                    Timestamp ts = rs.getTimestamp("timestamp");
                    if (ts != null) {
                        message.setTimestamp(ts.toLocalDateTime());
                    }
                    message.setStatus(MessageStatus.valueOf(rs.getString("status")));
                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }
    public boolean deleteMessage(final long messageId) throws SQLException {
        final String sql = "DELETE FROM messages WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, messageId);
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }
}
