package org.example.Entities;/*package Entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Group {
    private String id;
    private String name;
    private String adminEmail;
    private List<String> members;
    private LocalDateTime createdAt;

    public Group() {
        this.members = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }

    public Group(String name, String adminEmail) {
        this();
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.adminEmail = adminEmail;
        this.members.add(adminEmail);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Methods
    public void addMember(String memberEmail) {
        if (!members.contains(memberEmail)) {
            members.add(memberEmail);
        }
    }

    public void removeMember(String memberEmail) {
        if (!memberEmail.equals(adminEmail)) {
            members.remove(memberEmail);
        }
    }

    public boolean isMember(String email) {
        return members.contains(email);
    }
}
*/