package org.example.Entities;

import java.util.ArrayList;
import java.util.List;
// import com.google.gson.annotations.Expose;

public class Utilisateur {
    private String email; // PK
    private String password;
    private boolean isOnline;
    private List<String> contactEmails; // Store only email addresses
    //private List<String> groupIds;

    public Utilisateur() {
        this.contactEmails = new ArrayList<>();
     //   this.groupIds = new ArrayList<>();
        this.isOnline = false;
    }

    public Utilisateur(String email, String password) {
        this();
        this.email = email;
        this.password = password;
    }

    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
    public List<String> getContactEmails() { return contactEmails; }
    public void setContactEmails(List<String> contactEmails) { this.contactEmails = contactEmails; }
  //  public List<String> getGroupIds() { return groupIds; }
  //  public void setGroupIds(List<String> groupIds) { this.groupIds = groupIds; }

    // Methods
    public void addContact(String contactEmail) {
        if (!contactEmails.contains(contactEmail)) {
            contactEmails.add(contactEmail);
        }
    }

    public void removeContact(String contactEmail) {
        contactEmails.remove(contactEmail);
    }

    public boolean hasContact(String email) {
        return contactEmails.contains(email);
    }

    public void joinGroup(String groupId) {
 //       if (!groupIds.contains(groupId)) {
      //      groupIds.add(groupId);
        }
  //  }

    public void leaveGroup(String groupId) {
      //  groupIds.remove(groupId);
    }
}
