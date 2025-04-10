package org.example.model.enums;

/**
 * Définit les statuts possibles d'un message.
 */
public enum MessageStatus {
    SENT,       // Envoyé par le client, potentiellement en transit vers le destinataire/groupe
    DELIVERED,  // Confirmé comme reçu par le serveur/broker du destinataire (pas forcément lu)
    READ,       // Confirmé comme lu par le client destinataire (nécessite logique d'ACK)
    FAILED,     // Échec de l'envoi ou de la persistance
    QUEUED      // Mis en file d'attente car le destinataire était hors ligne
}
