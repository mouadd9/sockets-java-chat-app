package org.example.service;

import org.example.dao.GroupDAO;
import org.example.dao.GroupMembershipDAO;
import org.example.model.Group;
import org.example.model.GroupMembership;

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
}
