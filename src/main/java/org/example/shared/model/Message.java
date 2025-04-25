package org.example.shared.model;

import java.time.LocalDateTime;
import java.util.Objects;

import org.example.shared.model.enums.MessageStatus;
import org.example.shared.model.enums.MessageType;  // New import

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Message {

    private long id;
    private long senderUserId;   // FK vers User.id
    private Long receiverUserId; // FK vers User.id (Nullable)
    private Long groupId;        // FK vers Group.id (Nullable)
    private String content;
    private LocalDateTime timestamp;
    private MessageStatus status; // Utilise l'Enum MessageStatus

    // New fields for multimedia support
    private MessageType type;    // Type of message (TEXT, IMAGE, VIDEO, DOCUMENT, AUDIO)
    private String fileName;     // Original file name
    private Long fileSize;       // Size of file in bytes
    private String mimeType;     // MIME type of the file


    // Constructeur par défaut
    public Message() {
        this.timestamp = LocalDateTime.now();
        this.status = MessageStatus.SENT;
        this.type = MessageType.TEXT;  // Default type is TEXT
    }

    // Méthode factory pour créer un message direct
    public static Message newDirectMessage(final long senderUserId, final long receiverUserId, final String content) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setReceiverUserId(receiverUserId);
        msg.setContent(content);
        return msg;
    }

    // Méthode factory pour créer un message de groupe
    public static Message newGroupMessage(final long senderUserId, final long groupId, final String content) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setGroupId(groupId);
        msg.setContent(content);
        return msg;
    }

    // New factory methods for media messages
    public static Message newDirectMediaMessage(final long senderUserId, final long receiverUserId,
                                                final String filePath, final MessageType type, final String fileName,
                                                final Long fileSize, final String mimeType) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setReceiverUserId(receiverUserId);
        msg.setContent(filePath);
        msg.setType(type);
        msg.setFileName(fileName);
        msg.setFileSize(fileSize);
        msg.setMimeType(mimeType);
        return msg;
    }

    public static Message newGroupMediaMessage(final long senderUserId, final long groupId,
                                               final String filePath, final MessageType type, final String fileName,
                                               final Long fileSize, final String mimeType) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setGroupId(groupId);
        msg.setContent(filePath);
        msg.setType(type);
        msg.setFileName(fileName);
        msg.setFileSize(fileSize);
        msg.setMimeType(mimeType);
        return msg;
    }

    // New utility method to clone a message for a specific receiver
    public static Message copyForReceiver(final Message original, final long receiverUserId) {
        final Message copy = new Message();
        copy.setSenderUserId(original.getSenderUserId());
        copy.setReceiverUserId(receiverUserId);
        copy.setGroupId(original.getGroupId());
        copy.setContent(original.getContent());
        copy.setTimestamp(original.getTimestamp());
        copy.setStatus(original.getStatus());
        copy.setType(original.getType());
        copy.setFileName(original.getFileName());
        copy.setFileSize(original.getFileSize());
        copy.setMimeType(original.getMimeType());
        return copy;
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

    // New getters and setters for multimedia
    public MessageType getType() { return type; }
    public void setType(final MessageType type) { this.type = type; }
    public String getFileName() { return fileName; }
    public void setFileName(final String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(final Long fileSize) { this.fileSize = fileSize; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(final String mimeType) { this.mimeType = mimeType; }




    @JsonIgnore
    public boolean isDirectMessage() {
        return receiverUserId != null && groupId == null;
    }

    @JsonIgnore
    public boolean isGroupMessage() {
        return groupId != null && receiverUserId == null;
    }

    @JsonIgnore
    public boolean isTextMessage() {
        return type == MessageType.TEXT;
    }

    @JsonIgnore
    public boolean isMediaMessage() {
        return type != MessageType.TEXT;
    }

    @JsonIgnore
    public boolean isImageMessage() {
        return type == MessageType.IMAGE;
    }

    @JsonIgnore
    public boolean isVideoMessage() {
        return type == MessageType.VIDEO;
    }

    @JsonIgnore
    public boolean isDocumentMessage() {
        return type == MessageType.DOCUMENT;
    }

    @JsonIgnore
    public boolean isAudioMessage() {
        return type == MessageType.AUDIO;
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
