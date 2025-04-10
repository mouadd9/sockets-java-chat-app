package org.example.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class GroupMembership {

    private long userId;   // FK vers User.id
    private long groupId;  // FK vers Group.id
    private LocalDateTime joinedAt;

    // Constructeur par défaut
    public GroupMembership() {
        this.joinedAt = LocalDateTime.now();
    }

    // Constructeur principal
    public GroupMembership(final long userId, final long groupId) {
        this();
        this.userId = userId;
        this.groupId = groupId;
    }

    // ...existing getters and setters...
    public long getUserId() { return userId; }
    public void setUserId(final long userId) { this.userId = userId; }
    public long getGroupId() { return groupId; }
    public void setGroupId(final long groupId) { this.groupId = groupId; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(final LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    // ...equals, hashCode, toString...
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final GroupMembership that = (GroupMembership) o;
        return userId == that.userId && groupId == that.groupId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, groupId);
    }

    @Override
    public String toString() {
        return "GroupMembership{" + "userId=" + userId + ", groupId=" + groupId + ", joinedAt=" + joinedAt + '}';
    }
}
