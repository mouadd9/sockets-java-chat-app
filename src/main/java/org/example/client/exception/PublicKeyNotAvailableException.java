package org.example.client.exception;

/**
 * Exception levée lorsque la clé publique d'un destinataire n'est pas
 * disponible localement et qu'une demande a été envoyée au serveur.
 * L'opération (par ex. envoi de message) doit être retentée ultérieurement.
 */
public class PublicKeyNotAvailableException extends Exception {
    public PublicKeyNotAvailableException(final String message) {
        super(message);
    }
}
