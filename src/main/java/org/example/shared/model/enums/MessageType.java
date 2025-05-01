package org.example.shared.model.enums;

public enum MessageType {
    TEXT,    // Message texte simple (peut être chiffré ou non, voir champ encryptedContent)
    IMAGE,   // Message image
    VIDEO,   // Message vidéo
    AUDIO,   // Message audio
    DOCUMENT,// Message document

    // --- NOUVEAUX TYPES POUR E2EE ---
    E2E_SESSION_INIT,    // Message contenant une clé de session E2EE chiffrée
    PUBLIC_KEY_REQUEST,  // Demande de clé publique d'un utilisateur
    PUBLIC_KEY_RESPONSE, // Réponse contenant une clé publique

    // --- AUTRES TYPES POSSIBLES ---
    SYSTEM,              // Message système (ex: user joined/left, non chiffré)
    STATUS_UPDATE        // Mise à jour de statut (typing, read receipt)
    // Ajouter d'autres types si nécessaire
}