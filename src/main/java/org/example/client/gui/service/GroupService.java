package org.example.client.gui.service;

import java.io.IOException;
import java.util.List;

import org.example.shared.dao.GroupDAO;
import org.example.shared.dao.GroupMembershipDAO;
import org.example.shared.model.Group;
import org.example.shared.model.GroupMembership;

public class GroupService {

    private final GroupDAO groupDAO;
    private final GroupMembershipDAO groupMembershipDAO;

    public GroupService() {
        this.groupDAO = new GroupDAO();
        this.groupMembershipDAO = new GroupMembershipDAO();
    }

    public Group createGroup(final String groupName, final long ownerUserId) {
        final Group group = new Group(groupName, ownerUserId); // constructeur qui initialise aussi createdAt
        groupDAO.createGroup(group);
        if (group.getId() > 0) {
            final GroupMembership membership = new GroupMembership(ownerUserId, group.getId());
            groupMembershipDAO.createGroupMembership(membership);
        }
        return group;
    }

    public boolean addMemberToGroup(final long groupId, final long userId) {
        if (groupMembershipDAO.findGroupMembership(userId, groupId) == null) {
            final GroupMembership membership = new GroupMembership(userId, groupId);
            groupMembershipDAO.createGroupMembership(membership);
            return true;
        }
        return false;
    }
    
    public List<Group> getGroupsForUser(final long userId) throws IOException {
        return groupDAO.getGroupsForUser(userId);
    }

    /**
     * Récupère les identifiants des membres d'un groupe
     */
    public List<Long> getMembersForGroup(final long groupId) {
        return groupDAO.getMembersForGroup(groupId);
    }

    /**
     * Supprime un membre d'un groupe
     */
    public boolean removeMemberFromGroup(final long groupId, final long userId) {
        return groupMembershipDAO.removeGroupMembership(userId, groupId);
    }
}
