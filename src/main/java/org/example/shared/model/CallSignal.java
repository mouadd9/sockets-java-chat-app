package org.example.shared.model;

import java.time.LocalDateTime;

/**
 * Représente un signal de contrôle pour les appels audio.
 * Ces signaux sont envoyés via TCP pour la signalisation des appels.
 */
public class CallSignal {
    private String sessionId;
    private long senderUserId;
    private long receiverUserId;
    private SignalType type;
    private String ipAddress;
    private int port;
    private LocalDateTime timestamp;
    
    /**
     * Types de signaux pour la gestion des appels.
     */
    public enum SignalType {
        CALL_REQUEST,   // Demande d'appel
        CALL_ACCEPT,    // Acceptation d'appel
        CALL_REJECT,    // Rejet d'appel
        CALL_END,       // Fin d'appel
        CALL_BUSY       // Destinataire occupé
    }
    
    /**
     * Constructeur par défaut pour la sérialisation JSON.
     */
    public CallSignal() {
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * Crée un nouveau signal d'appel.
     * 
     * @param sessionId ID de la session d'appel
     * @param senderUserId ID de l'utilisateur qui envoie le signal
     * @param receiverUserId ID de l'utilisateur qui reçoit le signal
     * @param type Type de signal
     */
    public CallSignal(String sessionId, long senderUserId, long receiverUserId, SignalType type) {
        this();
        this.sessionId = sessionId;
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
        this.type = type;
    }
    
    /**
     * Crée un signal de demande d'appel.
     * 
     * @param sessionId ID de la session d'appel
     * @param callerUserId ID de l'appelant
     * @param receiverUserId ID du destinataire
     * @return Un nouveau signal de demande d'appel
     */
    public static CallSignal createCallRequest(String sessionId, long callerUserId, long receiverUserId) {
        return new CallSignal(sessionId, callerUserId, receiverUserId, SignalType.CALL_REQUEST);
    }
    
    /**
     * Crée un signal d'acceptation d'appel.
     * 
     * @param sessionId ID de la session d'appel
     * @param receiverUserId ID du destinataire qui accepte l'appel
     * @param callerUserId ID de l'appelant
     * @param ipAddress Adresse IP du destinataire pour la communication UDP
     * @param port Port UDP du destinataire
     * @return Un nouveau signal d'acceptation d'appel
     */
    public static CallSignal createCallAccept(String sessionId, long receiverUserId, long callerUserId, 
                                             String ipAddress, int port) {
        CallSignal signal = new CallSignal(sessionId, receiverUserId, callerUserId, SignalType.CALL_ACCEPT);
        signal.setIpAddress(ipAddress);
        signal.setPort(port);
        return signal;
    }
    
    /**
     * Crée un signal de rejet d'appel.
     * 
     * @param sessionId ID de la session d'appel
     * @param receiverUserId ID du destinataire qui rejette l'appel
     * @param callerUserId ID de l'appelant
     * @return Un nouveau signal de rejet d'appel
     */
    public static CallSignal createCallReject(String sessionId, long receiverUserId, long callerUserId) {
        return new CallSignal(sessionId, receiverUserId, callerUserId, SignalType.CALL_REJECT);
    }
    
    /**
     * Crée un signal de fin d'appel.
     * 
     * @param sessionId ID de la session d'appel
     * @param endingUserId ID de l'utilisateur qui termine l'appel
     * @param otherUserId ID de l'autre utilisateur
     * @return Un nouveau signal de fin d'appel
     */
    public static CallSignal createCallEnd(String sessionId, long endingUserId, long otherUserId) {
        return new CallSignal(sessionId, endingUserId, otherUserId, SignalType.CALL_END);
    }
    
    /**
     * Crée un signal d'occupation.
     * 
     * @param sessionId ID de la session d'appel
     * @param busyUserId ID de l'utilisateur occupé
     * @param callerUserId ID de l'appelant
     * @return Un nouveau signal d'occupation
     */
    public static CallSignal createCallBusy(String sessionId, long busyUserId, long callerUserId) {
        return new CallSignal(sessionId, busyUserId, callerUserId, SignalType.CALL_BUSY);
    }

    // Getters et setters
    
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(long senderUserId) {
        this.senderUserId = senderUserId;
    }

    public long getReceiverUserId() {
        return receiverUserId;
    }

    public void setReceiverUserId(long receiverUserId) {
        this.receiverUserId = receiverUserId;
    }

    public SignalType getType() {
        return type;
    }

    public void setType(SignalType type) {
        this.type = type;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
