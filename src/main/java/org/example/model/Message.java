package org.example.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Classe représentant un message dans l'application de chat.
 * Cette classe gère les messages textuels, les fichiers multimédias et les citations.
 * Elle utilise Jackson pour la sérialisation/désérialisation JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    // Identifiant unique du message
    private String id; // PK
    // Email de l'expéditeur du message
    private String senderEmail; // FK
    // Email du destinataire du message
    private String receiverEmail; // FK
    // Contenu textuel du message
    private String content;
    // Date et heure de création du message
    private LocalDateTime timestamp; // Creation date !!

    // État de lecture du message
    @JsonProperty("read")
    private boolean isRead;
    // Type de message (CHAT, LOGOUT, TYPING, etc.)
    private MessageType type;
    // État du message dans le système
    private String status; // PENDING, QUEUED, DELIVERED, ACKNOWLEDGED, EXPIRED

    // Champs pour la gestion des fichiers multimédias
    private String fileName;    // Nom du fichier
    private String fileType;    // Type de fichier (image, audio, video)
    private String fileData;    // Données du fichier encodées en base64
    private long fileSize;      // Taille du fichier en octets

    // Gestion des citations de messages
    private String quotedMessageId;         // ID du message cité
    private QuotedMessageInfo quotedMessageInfo; // Informations sur le message cité

    /**
     * Classe interne pour stocker les informations d'un message cité.
     * Contient les informations essentielles du message original.
     */
    public static class QuotedMessageInfo {
        private String senderEmail;     // Email de l'expéditeur du message cité
        private String content;         // Contenu du message cité
        private LocalDateTime timestamp; // Date du message cité
        
        public QuotedMessageInfo() {}
        
        public QuotedMessageInfo(String senderEmail, String content, LocalDateTime timestamp) {
            this.senderEmail = senderEmail;
            this.content = content;
            this.timestamp = timestamp;
        }
        
        // Getters et Setters
        public String getSenderEmail() { return senderEmail; }
        public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Constructeur par défaut.
     * Initialise les valeurs par défaut :
     * - Génère un ID unique
     * - Définit la date/heure actuelle
     * - Marque le message comme non lu
     * - Définit le statut initial à PENDING
     * - Définit le type par défaut à CHAT
     */
    public Message() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
        this.status = "PENDING";
        this.type = MessageType.CHAT;
    }

    /**
     * Constructeur pour un message texte simple.
     * @param senderEmail Email de l'expéditeur
     * @param receiverEmail Email du destinataire
     * @param content Contenu du message
     */
    public Message(final String senderEmail, final String receiverEmail, final String content) {
        this();
        this.senderEmail = senderEmail;
        this.receiverEmail = receiverEmail;
        this.content = content;
    }

    /**
     * Constructeur pour un message avec citation.
     * @param senderEmail Email de l'expéditeur
     * @param receiverEmail Email du destinataire
     * @param content Contenu du message
     * @param quotedMessageId ID du message cité
     */
    public Message(final String senderEmail, final String receiverEmail, final String content, final String quotedMessageId) {
        this(senderEmail, receiverEmail, content);
        this.quotedMessageId = quotedMessageId;
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

    // Alias pour setReceiverEmail pour plus de clarté
    public void setRecipientEmail(final String recipientEmail) {
        this.receiverEmail = recipientEmail;
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

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getQuotedMessageId() {
        return quotedMessageId;
    }

    public void setQuotedMessageId(final String quotedMessageId) {
        this.quotedMessageId = quotedMessageId;
    }

    public QuotedMessageInfo getQuotedMessageInfo() {
        return quotedMessageInfo;
    }

    public void setQuotedMessageInfo(QuotedMessageInfo quotedMessageInfo) {
        this.quotedMessageInfo = quotedMessageInfo;
    }

    // Méthode utilitaire pour créer une citation
    public void quoteMessage(Message messageToQuote) {
        this.quotedMessageId = messageToQuote.getId();
        this.quotedMessageInfo = new QuotedMessageInfo(
            messageToQuote.getSenderEmail(),
            messageToQuote.getContent(),
            messageToQuote.getTimestamp()
        );
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileData() {
        return fileData;
    }

    public void setFileData(String fileData) {
        this.fileData = fileData;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Vérifie si le message contient un fichier.
     * @return true si le message contient un fichier, false sinon
     */
    public boolean hasFile() {
        return fileName != null && fileData != null;
    }

    /**
     * Crée un nouveau message contenant un fichier.
     * @param senderEmail Email de l'expéditeur
     * @param receiverEmail Email du destinataire
     * @param fileName Nom du fichier
     * @param fileType Type du fichier
     * @param fileData Données du fichier en base64
     * @param fileSize Taille du fichier
     * @return Un nouveau message de type FILE
     */
    public static Message createFileMessage(String senderEmail, String receiverEmail, 
                                          String fileName, String fileType, 
                                          String fileData, long fileSize) {
        Message message = new Message();
        message.setSenderEmail(senderEmail);
        message.setReceiverEmail(receiverEmail);
        message.setType(MessageType.FILE);
        message.setFileName(fileName);
        message.setFileType(fileType);
        message.setFileData(fileData);
        message.setFileSize(fileSize);
        return message;
    }

    /**
     * Représentation textuelle du message.
     * Affiche les informations essentielles du message.
     */
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

    /**
     * Compare deux messages basé sur leur ID.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Message))
            return false;
        Message message = (Message) o;
        return Objects.equals(id, message.id);
    }

    /**
     * Génère un hash code basé sur l'ID du message.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
