package org.example.shared.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private String email; // Unique
    private String displayName;
    private String passwordHash; // Important: C'est un HASH !
    private boolean isOnline;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt; // Peut être null
    private String profilePictureUrl; // URL de l'image de profil
    private String status;

    // Constructeur par défaut
    public User() {
        this.createdAt = LocalDateTime.now();
        this.isOnline = false;
        this.profilePictureUrl = "default_avatar.png"; // Image par défaut
    }

    // Constructeur pour la création initiale (avant sauvegarde)
    public User(final String email, final String displayName, final String passwordHash) {
        this();
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(final String displayName) { this.displayName = displayName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(final String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(final boolean online) { isOnline = online; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(final LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(final LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getProfilePictureUrl() { 
        return profilePictureUrl; 
    }
    public void setProfilePictureUrl(final String profilePictureUrl) { 
        this.profilePictureUrl = profilePictureUrl; 
    }
    public String getStatus() { return status; }
    public void setStatus(final String status) { this.status = status; }

    /**
     * Retourne le nom d'affichage si disponible, sinon l'email
     */
    public String getDisplayNameOrEmail() {
        return displayName != null && !displayName.isEmpty() ? displayName : email;
    }

    /**
     * Retourne l'URL de l'avatar ou une image par défaut
     */
    public String getAvatarUrl() {
        return profilePictureUrl != null && !profilePictureUrl.isEmpty() ? 
               profilePictureUrl : "/images/default_avatar.png";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final User user = (User) o;
        if (id > 0 && user.id > 0) return id == user.id;
        return Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id > 0 ? id : email);
    }

    @Override
    public String toString() {
        return "User{" + "id=" + id + ", email='" + email + '\'' + ", displayName='" + displayName + '\'' + '}';
    }
}
