package org.example.shared.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class Contact implements Serializable {

    private long userId;        // FK vers User.id (celui qui ajoute)
    private long contactUserId; // FK vers User.id (celui qui est ajouté)
    private LocalDateTime addedAt;

    // Constructeur par défaut
    public Contact() {
        this.addedAt = LocalDateTime.now();
    }

    // Constructeur principal
    public Contact(final long userId, final long contactUserId) {
        this();
        this.userId = userId;
        this.contactUserId = contactUserId;
    }

    // ...existing getters and setters...
    public long getUserId() { return userId; }
    public void setUserId(final long userId) { this.userId = userId; }
    public long getContactUserId() { return contactUserId; }
    public void setContactUserId(final long contactUserId) { this.contactUserId = contactUserId; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(final LocalDateTime addedAt) { this.addedAt = addedAt; }

    // ...equals, hashCode, toString...
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Contact contact = (Contact) o;
        return userId == contact.userId && contactUserId == contact.contactUserId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, contactUserId);
    }

    @Override
    public String toString() {
        return "Contact{" + "userId=" + userId + ", contactUserId=" + contactUserId + ", addedAt=" + addedAt + '}';
    }
}
