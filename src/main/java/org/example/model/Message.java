package org.example.model;

import java.time.LocalDateTime;
import java.util.Objects;

import org.example.model.enums.MessageStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Message {

    private long id;
    private long senderUserId;   // FK vers User.id
    private Long receiverUserId; // FK vers User.id (Nullable)
    private Long groupId;        // FK vers Group.id (Nullable)
    private String content;
    private LocalDateTime timestamp;
    private MessageStatus status; // Utilise l'Enum MessageStatus

    // Constructeur par défaut
    public Message() {
        this.timestamp = LocalDateTime.now();
        this.status = MessageStatus.SENT;
    }

    // Méthode factory pour créer un message direct
    public static Message newDirectMessage(final long senderUserId, final long receiverUserId, final String content) {
        final Message msg = new Message();
        msg.senderUserId = senderUserId;
        msg.receiverUserId = receiverUserId;
        msg.content = content;
        return msg;
    }

    // Méthode factory pour créer un message de groupe
    public static Message newGroupMessage(final long senderUserId, final long groupId, final String content) {
        final Message msg = new Message();
        msg.senderUserId = senderUserId;
        msg.groupId = groupId;
        msg.content = content;
        return msg;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getSenderUserId() { return senderUserId; }
    public void setSenderUserId(final long senderUserId) { this.senderUserId = senderUserId; }
    public Long getReceiverUserId() { return receiverUserId; }
    public void setReceiverUserId(final Long receiverUserId) { this.receiverUserId = receiverUserId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(final Long groupId) { this.groupId = groupId; }
    public String getContent() { return content; }
    public void setContent(final String content) { this.content = content; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(final LocalDateTime timestamp) { this.timestamp = timestamp; }
    public MessageStatus getStatus() { return status; }
    public void setStatus(final MessageStatus status) { this.status = status; }

    @JsonIgnore
    public boolean isDirectMessage() {
        return receiverUserId != null && groupId == null;
    }

    @JsonIgnore
    public boolean isGroupMessage() {
        return groupId != null && receiverUserId == null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Message message = (Message) o;
        return id > 0 && id == message.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Message{" + "id=" + id + ", senderUserId=" + senderUserId +
               (isDirectMessage() ? ", receiverUserId=" + receiverUserId : "") +
               (isGroupMessage() ? ", groupId=" + groupId : "") +
               ", status=" + status + ", timestamp=" + timestamp + '}';
    }
}
