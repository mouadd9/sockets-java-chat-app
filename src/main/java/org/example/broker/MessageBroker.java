package org.example.broker;

// Importation des classes nécessaires pour le fonctionnement du broker
import java.io.IOException;  // Pour gérer les exceptions d'entrée/sortie
import java.time.LocalDateTime;  // Pour gérer les timestamps des messages
import java.util.Map;  // Pour stocker les files d'attente par utilisateur
import java.util.UUID;  // Pour générer des identifiants uniques
import java.util.concurrent.BlockingQueue;  // Interface pour les files d'attente thread-safe
import java.util.concurrent.ConcurrentHashMap;  // Map thread-safe pour stocker les files d'attente
import java.util.concurrent.LinkedBlockingQueue;  // Implémentation de BlockingQueue

// Importation des classes du projet
import org.example.model.Message;  // Classe représentant un message
import org.example.model.MessageType;  // Enumération des types de messages
import org.example.repository.JsonMessageRepository;  // Repository pour la persistance des messages
import org.example.server.ClientHandler;  // Handler pour gérer les connexions client

/**
 * MessageBroker est un singleton qui gère la distribution des messages dans l'application.
 * Il maintient une file d'attente de messages pour chaque utilisateur et assure leur livraison.
 */
public class MessageBroker {
    // Instance unique du MessageBroker (pattern Singleton)
    private static MessageBroker instance;  // Variable statique pour stocker l'instance unique
    
    // Map des files d'attente par utilisateur (email)
    private final Map<String, MessageQueue> userQueues;  // Stocke une file d'attente par email d'utilisateur
    
    // Repository pour la persistance des messages
    private final JsonMessageRepository messageRepo;  // Gère le stockage des messages dans un fichier JSON

    /**
     * Obtient l'instance unique du MessageBroker (pattern Singleton)
     * @return l'instance du MessageBroker
     */
    public static synchronized MessageBroker getInstance() {
        if (instance == null) {  // Si l'instance n'existe pas encore
            instance = new MessageBroker();  // Crée une nouvelle instance
        }
        return instance;  // Retourne l'instance existante ou nouvellement créée
    }

    /**
     * Constructeur privé pour le pattern Singleton
     * Initialise les structures de données et charge les messages en attente
     */
    private MessageBroker() {
        this.userQueues = new ConcurrentHashMap<>();  // Initialise la map des files d'attente
        this.messageRepo = new JsonMessageRepository();  // Initialise le repository
        loadPendingMessages();  // Charge les messages en attente au démarrage
    }

    /**
     * Enregistre un nouveau client (listener) pour un utilisateur
     * @param email l'email de l'utilisateur
     * @param listener le handler du client
     */
    public void registerListener(final String email, final ClientHandler listener) {
        final MessageQueue queue = getOrCreateQueue(email);  // Obtient ou crée la file d'attente pour l'utilisateur
        queue.setListener(listener);  // Associe le listener à la file d'attente
        queue.clearQueue();  // Vide la file d'attente
        queue.reloadPersistedMessages();  // Recharge les messages persistés
        queue.deliverPendingMessages();  // Livre les messages en attente
    }

    /**
     * Désenregistre un client (listener) pour un utilisateur
     * @param email l'email de l'utilisateur
     */
    public void unregisterListener(final String email) {
        final MessageQueue queue = userQueues.remove(email);  // Supprime la file d'attente de l'utilisateur
        if (queue != null) {  // Si la file d'attente existait
            queue.setListener(null);  // Désassocie le listener
            queue.clearQueue();  // Vide la file d'attente
        }
    }

    /**
     * Envoie un message à son destinataire
     * @param message le message à envoyer
     */
    public void sendMessage(final Message message) {
        // Initialisation et persistance des messages de type CHAT ou FILE
        if (message.getType() == MessageType.CHAT || message.getType() == MessageType.FILE) {
            initializeMessage(message);  // Initialise le message avec un ID et un timestamp
        }

        // Diffusion des messages de statut à tous les clients concernés
        if (message.getType() == MessageType.STATUS_UPDATE) {
            broadcastStatusUpdate(message);  // Diffuse le statut à tous les contacts
            return;  // Sort de la méthode car le message est traité
        }

        // Livraison du message au destinataire
        final MessageQueue queue = userQueues.get(message.getReceiverEmail());  // Obtient la file d'attente du destinataire
        if (queue != null && queue.tryDeliver(message)) {  // Si le destinataire est connecté et que la livraison réussit
            if (message.getType() == MessageType.CHAT || message.getType() == MessageType.FILE) {
                message.setStatus("DELIVERED");  // Met à jour le statut du message
                try {
                    messageRepo.updateMessage(message);  // Persiste le changement de statut
                } catch (IOException e) {
                    System.err.println("Erreur lors de la mise à jour du statut du message: " + e.getMessage());
                }
            }
        } else if (message.getType() == MessageType.CHAT || message.getType() == MessageType.FILE) {
            PersistMessage(message);  // Si le destinataire n'est pas connecté, persiste le message
        }
    }

    /**
     * Envoie un événement de frappe (typing) à un destinataire
     * @param message le message contenant l'événement de frappe
     */
    public void sendTypingEvent(final Message message) {
        final MessageQueue queue = userQueues.get(message.getReceiverEmail());  // Obtient la file d'attente du destinataire
        if (queue != null) {  // Si le destinataire est connecté
            queue.tryDeliver(message);  // Envoie l'événement de frappe
        }
    }

    /**
     * Initialise un nouveau message avec un ID unique et un timestamp
     * @param message le message à initialiser
     */
    private void initializeMessage(final Message message) {
        if (message.getId() == null) {  // Si le message n'a pas d'ID
            message.setId(UUID.randomUUID().toString());  // Génère un ID unique
        }
        if (message.getTimestamp() == null) {  // Si le message n'a pas de timestamp
            message.setTimestamp(LocalDateTime.now());  // Définit le timestamp actuel
        }
        message.setStatus("PENDING");  // Initialise le statut du message
    }

    /**
     * Persiste un message dans le repository
     * @param message le message à persister
     */
    private void PersistMessage(final Message message) {
        message.setStatus("QUEUED");  // Met à jour le statut du message
        try {
            messageRepo.saveMessage(message);  // Sauvegarde le message dans le repository
        } catch (final IOException e) {
            System.err.println("Failed to queue message: " + e.getMessage());  // Gère les erreurs de persistance
        }
    }

    /**
     * Diffuse une mise à jour de statut à tous les contacts d'un utilisateur
     * @param statusMessage le message contenant la mise à jour de statut
     */
    private void broadcastStatusUpdate(Message statusMessage) {
        String senderEmail = statusMessage.getSenderEmail();  // Récupère l'email de l'expéditeur
        boolean isOnline = Boolean.parseBoolean(statusMessage.getContent());  // Convertit le contenu en booléen
        
        userQueues.forEach((userEmail, queue) -> {  // Parcourt toutes les files d'attente
            try {
                if (!userEmail.equals(senderEmail)) {  // Si ce n'est pas l'expéditeur
                    if (queue.hasListener()) {  // Si l'utilisateur est connecté
                        Message notif = new Message();  // Crée un nouveau message de notification
                        notif.setType(MessageType.STATUS_UPDATE);  // Définit le type de message
                        notif.setSenderEmail(senderEmail);  // Définit l'expéditeur
                        notif.setContent(statusMessage.getContent());  // Copie le contenu
                        queue.tryDeliver(notif);  // Envoie la notification
                    }
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de la diffusion du statut: " + e.getMessage());
            }
        });
        
        System.out.println("Statut " + (isOnline ? "en ligne" : "hors ligne") + " de " + senderEmail + " diffusé à tous les contacts");
    }

    /**
     * Obtient ou crée une file d'attente pour un utilisateur
     * @param email l'email de l'utilisateur
     * @return la file d'attente de l'utilisateur
     */
    private MessageQueue getOrCreateQueue(final String email) {
        return userQueues.computeIfAbsent(email, MessageQueue::new);  // Crée une nouvelle file d'attente si elle n'existe pas
    }

    /**
     * Charge les messages en attente depuis le repository
     */
    private void loadPendingMessages() {
        try {
            messageRepo.loadMessages().stream()  // Charge tous les messages
                    .filter(m -> "QUEUED".equals(m.getStatus()))  // Filtre les messages en attente
                    .forEach(m -> getOrCreateQueue(m.getReceiverEmail()).addMessageToQueue(m));  // Ajoute chaque message à la file d'attente du destinataire
        } catch (final IOException e) {
            System.err.println("Error loading pending messages: " + e.getMessage());  // Gère les erreurs de chargement
        }
    }

    /**
     * Vérifie si un client est actuellement connecté
     * @param email l'email de l'utilisateur à vérifier
     * @return true si l'utilisateur est connecté, false sinon
     */
    public boolean hasActiveClient(final String email) {
        MessageQueue queue = userQueues.get(email);  // Obtient la file d'attente de l'utilisateur
        return queue != null && queue.hasListener();  // Vérifie si la file d'attente existe et a un listener
    }

    /**
     * Classe interne représentant une file d'attente de messages pour un utilisateur
     */
    private class MessageQueue {
        private final String userEmail;  // Email de l'utilisateur
        private final BlockingQueue<Message> messages;  // File d'attente des messages
        private ClientHandler listener;  // Handler du client connecté

        /**
         * Constructeur de la file d'attente
         * @param userEmail l'email de l'utilisateur
         */
        MessageQueue(final String userEmail) {
            this.userEmail = userEmail;  // Initialise l'email
            this.messages = new LinkedBlockingQueue<>();  // Crée une nouvelle file d'attente
        }

        /**
         * Définit le listener (client) pour cette file d'attente
         * @param listener le handler du client
         */
        synchronized void setListener(final ClientHandler listener) {
            this.listener = listener;  // Associe le listener à la file d'attente
        }

        /**
         * Ajoute un message à la file d'attente
         * @param message le message à ajouter
         */
        void addMessageToQueue(final Message message) {
            messages.offer(message);  // Ajoute le message à la file d'attente
        }

        /**
         * Vide la file d'attente
         */
        synchronized void clearQueue() {
            messages.clear();  // Supprime tous les messages de la file d'attente
        }

        /**
         * Recharge les messages persistés pour cet utilisateur
         */
        synchronized void reloadPersistedMessages() {
            try {
                messageRepo.loadMessages().stream()  // Charge tous les messages
                        .filter(m -> "QUEUED".equals(m.getStatus()) &&  // Filtre les messages en attente
                                userEmail.equals(m.getReceiverEmail()))  // Filtre les messages destinés à cet utilisateur
                        .forEach(this::addMessageToQueue);  // Ajoute chaque message à la file d'attente
            } catch (final IOException e) {
                System.err.println("Error reloading persisted messages for " + userEmail + ": " + e.getMessage());
            }
        }

        /**
         * Tente de livrer un message au client
         * @param message le message à livrer
         * @return true si la livraison a réussi, false sinon
         */
        boolean tryDeliver(final Message message) {
            if (listener != null) {  // Si un client est connecté
                try {
                    listener.onMessageReceived(message);  // Envoie le message au client
                    return true;  // La livraison a réussi
                } catch (final IOException e) {
                    System.err.println("Delivery failed for " + message.getId());  // Gère les erreurs de livraison
                }
            }
            return false;  // La livraison a échoué
        }

        /**
         * Livre tous les messages en attente
         */
        void deliverPendingMessages() {
            while (!messages.isEmpty()) {  // Tant qu'il y a des messages en attente
                final Message message = messages.peek();  // Regarde le prochain message sans le retirer
                if (tryDeliver(message)) {  // Si la livraison réussit
                    try {
                        messages.poll();  // Retire le message de la file d'attente
                    } catch (final Exception e) {
                        System.err.println("Delivery failed, keeping message in queue");  // Gère les erreurs
                        break;  // Sort de la boucle
                    }
                } else {
                    break;  // Si la livraison échoue, sort de la boucle
                }
            }
        }

        /**
         * Vérifie si un listener est actif
         * @return true si un listener est actif, false sinon
         */
        boolean hasListener() {
            return listener != null;  // Retourne true si un listener est associé
        }
    }
}