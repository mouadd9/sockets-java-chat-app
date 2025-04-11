package org.example.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.example.model.Group;

public class GroupDAO {

    public void createGroup(final Group group) {
        // Utilisation de backticks pour le nom de la table "groups"
        final String sql = "INSERT INTO `groups` (name, owner_user_id, created_at) VALUES (?,?,?)";
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
        final String sql = "SELECT * FROM `groups` WHERE id = ?";
        Group group = null;
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    group = new Group();
                    group.setId(rs.getLong("id"));
                    group.setName(rs.getString("name"));
                    group.setOwnerUserId(rs.getLong("owner_user_id"));
                    group.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return group;
    }

    /**
     * Récupère les identifiants des utilisateurs membres du groupe.
     */
    public List<Long> getMembersForGroup(final long groupId) {
        final List<Long> memberIds = new ArrayList<>();
        final String sql = "SELECT user_id FROM group_memberships WHERE group_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    memberIds.add(rs.getLong("user_id"));
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return memberIds;
    }

    /**
     * Récupère les groupes auxquels un utilisateur appartient.
     */
    public List<Group> getGroupsForUser(final long userId) {
        final List<Group> groups = new ArrayList<>();
        final String sql = "SELECT g.id, g.name, g.owner_user_id, g.created_at " +
                     "FROM `groups` g " +
                     "JOIN group_memberships gm ON g.id = gm.group_id " +
                     "WHERE gm.user_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final Group group = new Group();
                    group.setId(rs.getLong("id"));
                    group.setName(rs.getString("name"));
                    group.setOwnerUserId(rs.getLong("owner_user_id"));
                    group.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    groups.add(group);
                }
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }
}