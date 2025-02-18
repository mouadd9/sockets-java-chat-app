package org.example.Entities;

import java.time.LocalDateTime;

public class Message {
    private String id; // PK
    private String senderEmail; // FK
    private String receiverEmail; //FK
    private String content;
    private LocalDateTime timestamp; // Creation date !!
    private boolean isRead;

    // private String groupId;
    // private String fileAttachment;
    // private String fileType;

    public Message() {
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
    }

    public Message(String senderEmail, String receiverEmail, String content) {
        this();
        this.id = java.util.UUID.randomUUID().toString();
        this.senderEmail = senderEmail;
        this.receiverEmail = receiverEmail;
        this.content = content;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }
    public String getReceiverEmail() { return receiverEmail; }
    public void setReceiverEmail(String receiverEmail) { this.receiverEmail = receiverEmail; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    // public String getGroupId() { return groupId; }
    // public void setGroupId(String groupId) { this.groupId = groupId; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    // public String getFileAttachment() { return fileAttachment; }
    // public void setFileAttachment(String fileAttachment) { this.fileAttachment = fileAttachment; }
    // public String getFileType() { return fileType; }
    // public void setFileType(String fileType) { this.fileType = fileType; }
}
