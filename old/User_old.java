package org.example.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class User_old {
    private String email;        // PK
    private String displayName;  // user name for display
    private String password;     // hashed password
    
    @JsonProperty("online")
    private boolean isOnline;    // user online status
    
    @JsonProperty("contactEmails")
    private List<String> contacts; // sera mappé depuis "contactEmails" du JSON
    
    // Constructeur par défaut pour Jackson
    public User_old() {
        this.isOnline = false;
        this.contacts = new ArrayList<>();
    }
    
    public User_old(final String email, final String displayName, final String password) {
        this();
        this.email = email;
        this.displayName = displayName;
        this.password = password;
    }

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(final boolean online) {
        isOnline = online;
    }

    public List<String> getContacts() {
        return contacts;
    }

    public void setContacts(final List<String> contacts) {
        this.contacts = contacts;
    }
    
    public void addContact(final String contactEmail) {
        if (!contacts.contains(contactEmail)) {
            contacts.add(contactEmail);
        }
    }
    
    public boolean removeContact(final String contactEmail) {
        return contacts.remove(contactEmail);
    }

    @Override
    public String toString() {
        return "User{" +
                "email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                ", isOnline=" + isOnline +
                ", contactsCount=" + contacts.size() +
                '}';
    }
}
