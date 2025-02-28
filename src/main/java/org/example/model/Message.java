package org.example.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Message {
    private String id; // PK
    private String senderEmail; // FK
    private String receiverEmail; // FK
    private String content;
    private LocalDateTime timestamp; // Creation date !!

    @JsonProperty("read")
    private boolean isRead;
    private String type; // "CHAT", "LOGOUT", etc.
    private String status; // PENDING, QUEUED, DELIVERED, ACKNOWLEDGED, EXPIRED

    // Constructeur par défaut pour Jackson
    public Message() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
        this.status = "PENDING";
    }

    public Message(final String senderEmail, final String receiverEmail, final String content) {
        this();
        this.senderEmail = senderEmail;
        this.receiverEmail = receiverEmail;
        this.content = content;
        this.type = "CHAT";
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(final String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getReceiverEmail() {
        return receiverEmail;
    }

    public void setReceiverEmail(final String receiverEmail) {
        this.receiverEmail = receiverEmail;
    }

    public String getContent() {
        return content;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(final boolean read) {
        this.isRead = read;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", senderEmail='" + senderEmail + '\'' +
                ", receiverEmail='" + receiverEmail + '\'' +
                ", content='"
                + (content != null ? content.substring(0, Math.min(content.length(), 20)) + "..." : "null") + '\'' +
                ", timestamp=" + timestamp +
                ", isRead=" + isRead +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Message))
            return false;
        Message message = (Message) o;
        return Objects.equals(id, message.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
