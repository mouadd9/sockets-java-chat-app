package org.example.shared.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.example.shared.model.GroupMembership;

public class GroupMembershipDAO {

    public void createGroupMembership(final GroupMembership membership) {
        final String sql = "INSERT INTO group_memberships (user_id, group_id, joined_at) VALUES (?,?,?)";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, membership.getUserId());
            stmt.setLong(2, membership.getGroupId());
            stmt.setTimestamp(3, Timestamp.valueOf(membership.getJoinedAt()));
            stmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    public GroupMembership findGroupMembership(final long userId, final long groupId) {
        final String sql = "SELECT * FROM group_memberships WHERE user_id = ? AND group_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final GroupMembership membership = new GroupMembership();
                    membership.setUserId(rs.getLong("user_id"));
                    membership.setGroupId(rs.getLong("group_id"));
                    membership.setJoinedAt(rs.getTimestamp("joined_at").toLocalDateTime());
                    return membership;
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
