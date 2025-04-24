package org.example.shared.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Représente une session d'appel audio entre deux utilisateurs.
 */
public class CallSession {
    private String sessionId;
    private long callerUserId;
    private long receiverUserId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private CallStatus status;
    
    /**
     * Statut possible d'un appel.
     */
    public enum CallStatus {
        INITIATING,    // L'appel est en cours d'initialisation
        RINGING,       // L'appel sonne chez le destinataire
        ACCEPTED,      // L'appel a été accepté
        REJECTED,      // L'appel a été rejeté
        ENDED,         // L'appel s'est terminé normalement
        MISSED,        // L'appel a été manqué
        ERROR          // Une erreur s'est produite pendant l'appel
    }
    
    /**
     * Constructeur par défaut pour la sérialisation JSON.
     */
    public CallSession() {
    }
    
    /**
     * Crée une nouvelle session d'appel.
     * 
     * @param callerUserId ID de l'utilisateur qui appelle
     * @param receiverUserId ID de l'utilisateur qui reçoit l'appel
     */
    public CallSession(long callerUserId, long receiverUserId) {
        this.sessionId = UUID.randomUUID().toString();
        this.callerUserId = callerUserId;
        this.receiverUserId = receiverUserId;
        this.startTime = LocalDateTime.now();
        this.status = CallStatus.INITIATING;
    }
    
    /**
     * Marque l'appel comme terminé.
     */
    public void endCall() {
        this.endTime = LocalDateTime.now();
        if (this.status == CallStatus.ACCEPTED) {
            this.status = CallStatus.ENDED;
        } else if (this.status == CallStatus.RINGING) {
            this.status = CallStatus.MISSED;
        }
    }
    
    /**
     * Calcule la durée de l'appel en secondes.
     * 
     * @return La durée de l'appel en secondes, ou 0 si l'appel n'est pas terminé
     */
    public long getDurationSeconds() {
        if (startTime != null && endTime != null && status == CallStatus.ENDED) {
            return java.time.Duration.between(startTime, endTime).getSeconds();
        }
        return 0;
    }

    // Getters et setters
    
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getCallerUserId() {
        return callerUserId;
    }

    public void setCallerUserId(long callerUserId) {
        this.callerUserId = callerUserId;
    }

    public long getReceiverUserId() {
        return receiverUserId;
    }

    public void setReceiverUserId(long receiverUserId) {
        this.receiverUserId = receiverUserId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public CallStatus getStatus() {
        return status;
    }

    public void setStatus(CallStatus status) {
        this.status = status;
    }
}
