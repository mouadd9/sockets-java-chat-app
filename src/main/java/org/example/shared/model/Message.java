package org.example.shared.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID; // Ajout pour générer le clientTempId

import org.example.shared.model.enums.MessageStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Message implements Serializable {

    private long id;
    private long senderUserId;   // FK vers User.id
    private Long receiverUserId; // FK vers User.id (Nullable)
    private Long groupId;        // FK vers Group.id (Nullable)
    private String content;
    private LocalDateTime timestamp;
    private MessageStatus status; // Utilise l'Enum MessageStatus
    private Long originalMessageId; // Nouveau champ pour tracer l'ACK
    private String clientTempId; // Nouveau champ pour l'ID temporaire

    // Constructeur par défaut
    public Message() {
        this.timestamp = LocalDateTime.now();
        this.status = MessageStatus.SENT;
    }

    // Méthode factory pour créer un message direct
    public static Message newDirectMessage(final long senderUserId, final long receiverUserId, final String content) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setReceiverUserId(receiverUserId);
        msg.setContent(content);
        msg.setClientTempId(UUID.randomUUID().toString()); // Génération de l'ID temporaire
        return msg;
    }

    // Méthode factory pour créer un message de groupe
    public static Message newGroupMessage(final long senderUserId, final long groupId, final String content) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setGroupId(groupId);
        msg.setContent(content);
        msg.setClientTempId(UUID.randomUUID().toString()); // Génération de l'ID temporaire
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
    public Long getOriginalMessageId() { return originalMessageId; }
    public void setOriginalMessageId(final Long originalMessageId) { this.originalMessageId = originalMessageId; }
    public String getClientTempId() { return clientTempId; }
    public void setClientTempId(final String clientTempId) { this.clientTempId = clientTempId; }

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
        if (id > 0 && message.id > 0) return id == message.id;
        if (clientTempId != null && message.clientTempId != null)
            return clientTempId.equals(message.clientTempId);
        return false;
    }

    @Override
    public int hashCode() {
        if (id > 0) return Objects.hash(id);
        if (clientTempId != null) return Objects.hash(clientTempId);
        return Objects.hash(senderUserId, receiverUserId, groupId, timestamp, content);
    }

    @Override
    public String toString() {
        return "Message{" + "id=" + id + ", tempId=" + clientTempId + ", senderUserId=" + senderUserId +
               (isDirectMessage() ? ", receiverUserId=" + receiverUserId : "") +
               (isGroupMessage() ? ", groupId=" + groupId : "") +
               ", status=" + status + ", timestamp=" + timestamp + '}';
    }
}
