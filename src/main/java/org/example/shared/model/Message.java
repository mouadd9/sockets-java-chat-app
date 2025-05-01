package org.example.shared.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Base64; // Import Base64

import org.example.shared.model.enums.MessageStatus;
import org.example.shared.model.enums.MessageType;  // Assurez-vous que cet enum est mis à jour

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude; // Pour ne pas inclure les champs null

@JsonInclude(JsonInclude.Include.NON_NULL) // Ne pas inclure les champs null dans JSON
public class Message {

    private long id;
    private long senderUserId;   // FK vers User.id
    private Long receiverUserId; // FK vers User.id (Nullable pour les messages de groupe)
    private Long groupId;        // FK vers Group.id (Nullable pour les messages directs)
    private String content;      // Pour les messages TEXT non-E2EE, chemins de fichiers media, ou contenu spécial (ex: clé publique)
    private LocalDateTime timestamp;
    private MessageStatus status; // Utilise l'Enum MessageStatus

    // Champs existants pour multimedia
    private MessageType type;    // Type of message (TEXT, IMAGE, VIDEO, DOCUMENT, AUDIO, E2E_SESSION_INIT, etc.)
    private String fileName;     // Original file name
    private Long fileSize;       // Size of file in bytes
    private String mimeType;     // MIME type of the file

    // --- NOUVEAUX CHAMPS POUR E2EE ---
    private byte[] encryptedContent; // Contenu chiffré (pour TEXT E2EE, potentiellement media)
    private String sessionKeyId;     // Identifiant de la clé de session utilisée (optionnel)
    // --- FIN NOUVEAUX CHAMPS POUR E2EE ---


    // Constructeur par défaut
    public Message() {
        this.timestamp = LocalDateTime.now();
        this.status = MessageStatus.SENT; // Statut par défaut lors de la création côté client
        this.type = MessageType.TEXT;     // Type par défaut
    }

    // --- FACTORIES EXISTANTES (Peuvent rester pour compatibilité ou messages système) ---

    public static Message newDirectMessage(final long senderUserId, final long receiverUserId, final String content) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setReceiverUserId(receiverUserId);
        msg.setContent(content);
        msg.setType(MessageType.TEXT); // Message texte simple (non chiffré par défaut ici)
        return msg;
    }

    public static Message newGroupMessage(final long senderUserId, final long groupId, final String content) {
        // Les messages de groupe E2EE sont complexes, ce factory reste pour du texte simple non-E2EE
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setGroupId(groupId);
        msg.setContent(content);
        msg.setType(MessageType.TEXT);
        return msg;
    }

    // Factories pour media (content contient le chemin ou l'identifiant du fichier)
    public static Message newDirectMediaMessage(final long senderUserId, final long receiverUserId,
                                                final String filePath, final MessageType type, final String fileName,
                                                final Long fileSize, final String mimeType) {
        // Pour E2EE media, il faudrait chiffrer le fichier et potentiellement mettre les bytes dans encryptedContent
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setReceiverUserId(receiverUserId);
        msg.setContent(filePath); // Le chemin reste en clair ici
        msg.setType(type);
        msg.setFileName(fileName);
        msg.setFileSize(fileSize);
        msg.setMimeType(mimeType);
        return msg;
    }

     public static Message newGroupMediaMessage(final long senderUserId, final long groupId,
                                               final String filePath, final MessageType type, final String fileName,
                                               final Long fileSize, final String mimeType) {
        // Idem pour E2EE media groupe
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


    // --- NOUVELLES FACTORIES POUR E2EE ---

    /** Crée un message texte chiffré E2EE */
    public static Message newEncryptedTextMessage(final long senderUserId, final long receiverUserId,
                                                  final byte[] encryptedContent, final String sessionKeyId) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setReceiverUserId(receiverUserId);
        msg.setType(MessageType.TEXT); // On réutilise TEXT, mais on sait qu'il est chiffré car encryptedContent est non null
        msg.setEncryptedContent(encryptedContent);
        msg.setSessionKeyId(sessionKeyId);
        msg.setContent(null); // Le contenu clair est nul
        return msg;
    }

    /** Crée un message pour initier une session E2EE (envoi de clé symétrique chiffrée) */
    public static Message newSessionInitMessage(final long senderUserId, final long receiverUserId,
                                                final byte[] encryptedSessionKey) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        msg.setReceiverUserId(receiverUserId);
        msg.setType(MessageType.E2E_SESSION_INIT); // Nouveau type requis dans l'enum MessageType
        msg.setEncryptedContent(encryptedSessionKey); // La clé chiffrée est dans le contenu chiffré
        msg.setContent(null);
        return msg;
    }

     /** Crée un message pour demander la clé publique d'un utilisateur */
    public static Message newPublicKeyRequestMessage(final long senderUserId, final long targetUserId) {
        final Message msg = new Message();
        msg.setSenderUserId(senderUserId);
        // Pas de destinataire direct, le serveur interprète la demande
        // msg.setReceiverUserId(null); // Ou un ID spécial pour le serveur ?
        msg.setType(MessageType.PUBLIC_KEY_REQUEST); // Nouveau type requis
        msg.setContent(String.valueOf(targetUserId)); // Mettre l'ID demandé dans le contenu
        return msg;
    }

     /** Crée un message contenant une clé publique en réponse à une demande */
    public static Message newPublicKeyResponseMessage(final long keyOwnerUserId, final long recipientUserId, final String publicKeyString) {
        final Message msg = new Message();
        msg.setSenderUserId(keyOwnerUserId); // Qui possède la clé
        msg.setReceiverUserId(recipientUserId); // À qui envoyer la réponse
        msg.setType(MessageType.PUBLIC_KEY_RESPONSE); // Nouveau type requis
        msg.setContent(publicKeyString); // La clé publique (Base64) dans le contenu
        return msg;
    }

    // --- FIN NOUVELLES FACTORIES POUR E2EE ---


    // Méthode utilitaire existante pour cloner (à adapter si besoin)
    public static Message copyForReceiver(final Message original, final long receiverUserId) {
        final Message copy = new Message();
        copy.setSenderUserId(original.getSenderUserId());
        copy.setReceiverUserId(receiverUserId); // Définit le destinataire spécifique
        copy.setGroupId(original.getGroupId()); // Garde l'ID de groupe si c'est un message de groupe
        copy.setContent(original.getContent());
        copy.setTimestamp(original.getTimestamp());
        copy.setStatus(MessageStatus.QUEUED); // Le statut pour la copie est QUEUED initialement
        copy.setType(original.getType());
        copy.setFileName(original.getFileName());
        copy.setFileSize(original.getFileSize());
        copy.setMimeType(original.getMimeType());
        // Copier aussi les champs E2EE
        copy.setEncryptedContent(original.getEncryptedContent()); // Copie la référence du tableau de bytes
        copy.setSessionKeyId(original.getSessionKeyId());
        return copy;
    }

    // --- GETTERS ET SETTERS (Existants + Nouveaux) ---

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
    public MessageType getType() { return type; }
    public void setType(final MessageType type) { this.type = type; }
    public String getFileName() { return fileName; }
    public void setFileName(final String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(final Long fileSize) { this.fileSize = fileSize; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(final String mimeType) { this.mimeType = mimeType; }

    // Getters/Setters pour les champs E2EE
    public String getSessionKeyId() { return sessionKeyId; }
    public void setSessionKeyId(String sessionKeyId) { this.sessionKeyId = sessionKeyId; }

    // Gérer la conversion Base64 pour la sérialisation JSON de encryptedContent
    public String getEncryptedContentBase64() {
        return (encryptedContent != null) ? Base64.getEncoder().encodeToString(encryptedContent) : null;
    }

    public void setEncryptedContentBase64(String base64Data) {
        this.encryptedContent = (base64Data != null && !base64Data.isEmpty()) ? Base64.getDecoder().decode(base64Data) : null;
    }

    @JsonIgnore // Ignorer le byte[] brut pour Jackson, utiliser la version Base64
    public byte[] getEncryptedContent() { return encryptedContent; }
    @JsonIgnore
    public void setEncryptedContent(byte[] encryptedContent) { this.encryptedContent = encryptedContent; }


    // --- MÉTHODES UTILITAIRES (@JsonIgnore) ---

    @JsonIgnore
    public boolean isDirectMessage() {
        return receiverUserId != null && groupId == null;
    }

    @JsonIgnore
    public boolean isGroupMessage() {
        return groupId != null && receiverUserId == null; // Correction: receiverUserId doit être null pour groupe
    }

    @JsonIgnore
    public boolean isTextMessage() {
        // Un message TEXT est considéré comme texte simple s'il n'a pas de contenu chiffré
        return type == MessageType.TEXT && encryptedContent == null;
    }

     @JsonIgnore
    public boolean isEncryptedTextMessage() {
        // Un message TEXT est considéré comme chiffré s'il a du contenu chiffré
        return type == MessageType.TEXT && encryptedContent != null;
    }

    @JsonIgnore
    public boolean isMediaMessage() {
        // Inclut tous les types sauf TEXT et les types E2EE spécifiques
        return type != MessageType.TEXT &&
               type != MessageType.E2E_SESSION_INIT &&
               type != MessageType.PUBLIC_KEY_REQUEST &&
               type != MessageType.PUBLIC_KEY_RESPONSE; // Ajuster si d'autres types système sont ajoutés
    }

    // Les méthodes isImageMessage, isVideoMessage etc. restent valides

    @JsonIgnore
    public boolean isImageMessage() { return type == MessageType.IMAGE; }
    @JsonIgnore
    public boolean isVideoMessage() { return type == MessageType.VIDEO; }
    @JsonIgnore
    public boolean isDocumentMessage() { return type == MessageType.DOCUMENT; }
    @JsonIgnore
    public boolean isAudioMessage() { return type == MessageType.AUDIO; }

    // --- equals, hashCode, toString ---

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Message message = (Message) o;
        // L'égalité basée sur l'ID est ok si l'ID est généré par la DB et unique.
        // Si l'ID n'est pas encore défini (avant sauvegarde), cette égalité ne fonctionne pas.
        return id > 0 && id == message.id;
    }

    @Override
    public int hashCode() {
        // Basé sur l'ID si > 0, sinon basé sur d'autres champs immuables après création.
        return (id > 0) ? Objects.hash(id) : Objects.hash(senderUserId, receiverUserId, groupId, timestamp);
    }

    @Override
    public String toString() {
        String details = "id=" + id +
                         ", type=" + type +
                         ", senderUserId=" + senderUserId +
                         (isDirectMessage() ? ", receiverUserId=" + receiverUserId : "") +
                         (isGroupMessage() ? ", groupId=" + groupId : "") +
                         ", status=" + status +
                         ", timestamp=" + timestamp;
        if (encryptedContent != null) {
            details += ", encryptedContent=[bytes]";
        } else if (content != null) {
            details += ", content='" + content.substring(0, Math.min(content.length(), 30)) + "...'";
        }
        if (fileName != null) {
             details += ", fileName='" + fileName + "'";
        }
        return "Message{" + details + '}';
    }
}