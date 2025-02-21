package org.example.Entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;


public class Message {
    private String id; // PK
    private String senderEmail; // FK
    private String receiverEmail; //FK
    private String content;
    

    private LocalDateTime timestamp; // Creation date !!
    
    @JsonProperty("read")
    private boolean isRead;
    private String type; // "CHAT", "LOGOUT", etc.
    private String status; // "delivered", "queued", etc.

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
        this.type = "CHAT"; // Default type
        this.isRead = false;
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
    
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { this.isRead = read; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

}
