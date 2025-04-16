package org.example.shared.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Group {

    private long id;
    private String name;
    private long ownerUserId; // FK vers User.id
    private LocalDateTime createdAt;

    // Constructeur par défaut
    public Group() {
        this.createdAt = LocalDateTime.now();
    }

    // Constructeur pour la création initiale
    public Group(final String name, final long ownerUserId) {
        this();
        this.name = name;
        this.ownerUserId = ownerUserId;
    }

    // ...existing getters and setters...
    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }
    public long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(final long ownerUserId) { this.ownerUserId = ownerUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(final LocalDateTime createdAt) { this.createdAt = createdAt; }

    // ...equals, hashCode, toString...
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Group group = (Group) o;
        return id > 0 && id == group.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Group{" + "id=" + id + ", name='" + name + '\'' + ", ownerUserId=" + ownerUserId + '}';
    }
}
