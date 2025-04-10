package org.example.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import org.example.model.Group;

public class GroupDAO {

    public void createGroup(final Group group) {
        final String sql = "INSERT INTO groups (name, owner_user_id, created_at) VALUES (?,?,?)";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, group.getName());
            stmt.setLong(2, group.getOwnerUserId());
            stmt.setTimestamp(3, Timestamp.valueOf(group.getCreatedAt()));
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    group.setId(generatedKeys.getLong(1));
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    public Group findGroupById(final long id) {
        final String sql = "SELECT * FROM groups WHERE id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final Group group = new Group();
                    group.setId(rs.getLong("id"));
                    group.setName(rs.getString("name"));
                    group.setOwnerUserId(rs.getLong("owner_user_id"));
                    group.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return group;
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
