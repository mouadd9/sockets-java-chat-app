package org.example.broker;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.example.model.Message;
import org.example.repository.JsonMessageRepository;

/**
 * Message Broker pour gérer les files d'attente de messages.
 * Seule la file d'attente persiste les messages pour les destinataires
 * hors-ligne.
 */
public class MessageBroker {
    // Instance singleton
    private static MessageBroker instance;

    // Files d'attente par utilisateur (email -> file)
    private final Map<String, MessageQueue> queues;

    // Repository pour persister uniquement les messages non livrés (offline)
    private final JsonMessageRepository messageRepository;

    // Thread de surveillance pour gérer d'éventuels messages expirés
    private final Thread monitorThread;
    private boolean running = true;

    private MessageBroker() {
        this.queues = new ConcurrentHashMap<>();
        this.messageRepository = new JsonMessageRepository();

        // Démarrer le thread de surveillance
        this.monitorThread = new Thread(this::monitorMessages);
        this.monitorThread.setDaemon(true);
        this.monitorThread.start();

        // Charger les messages non lus déjà en file d'attente
        loadUnreadMessages();
    }

    public static synchronized MessageBroker getInstance() {
        if (instance == null) {
            instance = new MessageBroker();
        }
        return instance;
    }

    /**
     * Charge les messages non lus depuis le fichier dans leurs files d'attente
     * respectives.
     */
    private void loadUnreadMessages() {
        try {
            System.out.println("Chargement des messages non lus...");
            final List<Message> allMessages = messageRepository.loadMessages();
            allMessages.stream()
                    .filter(message -> !message.isRead() && "QUEUED".equals(message.getStatus()))
                    .forEach(message -> getOrCreateQueue(message.getReceiverEmail()).addPersistedMessage(message));
            System.out.println("Chargement terminé.");
        } catch (final IOException e) {
            System.err.println("Erreur lors du chargement: " + e.getMessage());
        }
    }

    private MessageQueue getOrCreateQueue(final String userEmail) {
        return queues.computeIfAbsent(userEmail, email -> new MessageQueue(email));
    }

    /**
     * Envoie un message :
     * - Si le destinataire est en ligne, on tente une livraison directe (sans
     * persistance côté serveur).
     * - Sinon, ou en cas d'échec de la livraison directe, on ajoute le message en
     * file d'attente, qui est persistée.
     *
     * @param message                Le message à envoyer
     * @param directDeliveryCallback Callback pour livraison directe, null si
     *                               inexistant.
     * @return true si livré directement, false si mis en file d'attente.
     */
    public boolean sendMessage(final Message message, final Consumer<Message> directDeliveryCallback) {
        // S'assurer que le message possède un ID et timestamp
        if (message.getId() == null || message.getId().isEmpty()) {
            message.setId(java.util.UUID.randomUUID().toString());
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }

        // Initialement, le status est "PENDING"
        message.setStatus("PENDING");
        message.setRead(false);

        // Tenter une livraison directe s'il y a un callback
        if (directDeliveryCallback != null) {
            try {
                directDeliveryCallback.accept(message);
                // Si aucun problème, livraison réussie : mettre à jour le status (sans
                // persistance)
                message.setStatus("DELIVERED");
                System.out.println("Message " + message.getId() + " livré directement à " + message.getReceiverEmail());
                return true;
            } catch (final Exception e) {
                System.err.println("Livraison directe échouée: " + e.getMessage());
            }
        }

        // Si la livraison directe n'est pas possible, persister le message avec le
        // statut QUEUED
        message.setStatus("QUEUED");
        try {
            messageRepository.saveMessage(message);
        } catch (final IOException e) {
            System.err.println("Erreur lors de la persistance du message: " + e.getMessage());
            return false;
        }

        // Ajouter le message à la file d'attente après persistance réussie
        final MessageQueue queue = getOrCreateQueue(message.getReceiverEmail());
        queue.addToQueue(message);

        System.out.println("Message " + message.getId() + " mis en file d'attente pour " + message.getReceiverEmail());
        return false;
    }

    /**
     * Enregistre un consommateur pour l'utilisateur et déclenche la livraison des
     * messages en attente.
     */
    public void registerConsumer(final String userEmail, final Consumer<Message> consumer) {
        if (consumer == null) {
            return;
        }
        System.out.println("Enregistrement du consommateur pour " + userEmail);
        final MessageQueue queue = getOrCreateQueue(userEmail);
        queue.clearQueue();
        // Recharger les messages persistés non délivrés pour cet utilisateur
        queue.reloadPersistedMessages();
        // Enregistrer le consommateur et déclencher la livraison
        queue.setConsumer(consumer);
    }

    public void unregisterConsumer(final String userEmail) {
        final MessageQueue queue = queues.get(userEmail);
        if (queue != null) {
            queue.setConsumer(null);
        }
    }

    /**
     * Acquitte un message en le marquant comme lu et en supprimant sa persistance
     * si nécessaire.
     */
    public void acknowledgeMessage(final String messageId) {
        try {
            final var optMsg = messageRepository.findById(messageId);
            if (optMsg.isPresent()) {
                final Message msg = optMsg.get();
                // Si le message n'est pas déjà acquitté, on met à jour son status et on le
                // supprime
                if (!"ACKNOWLEDGED".equals(msg.getStatus())) {
                    msg.setRead(true);
                    msg.setStatus("ACKNOWLEDGED");
                    messageRepository.updateMessage(msg);
                    if (messageRepository.deleteMessage(msg.getId())) {
                        System.out.println("Message " + messageId + " supprimé après acquittement.");
                    }
                }
            } else {
                System.out.println("Message " + messageId + " introuvable (probablement déjà supprimé).");
            }
        } catch (final IOException e) {
            System.err.println("Erreur lors de l'acquittement du message " + messageId + ": " + e.getMessage());
        }
    }

    private void monitorMessages() {
        while (running) {
            try {
                Thread.sleep(5 * 1000);
                queues.values().forEach(MessageQueue::attemptRedelivery);
                checkExpiredMessages();
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (final Exception e) {
                System.err.println("Erreur dans le moniteur: " + e.getMessage());
            }
        }
    }

    private void checkExpiredMessages() {
        try {
            final List<Message> messages = messageRepository.loadMessages();
            final LocalDateTime threshold = LocalDateTime.now().minusDays(7);
            messages.stream()
                    .filter(m -> ("PENDING".equals(m.getStatus()) || "QUEUED".equals(m.getStatus()))
                            && m.getTimestamp().isBefore(threshold))
                    .forEach(m -> {
                        try {
                            m.setStatus("EXPIRED");
                            messageRepository.updateMessage(m);
                            System.out.println("Message " + m.getId() + " expiré.");
                        } catch (final IOException e) {
                            System.err.println("Erreur lors de l'expiration du message: " + e.getMessage());
                        }
                    });
        } catch (final IOException e) {
            System.err.println("Erreur de vérification des messages expirés: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        monitorThread.interrupt();
    }

    /**
     * Classe interne gérant la file d'attente d'un utilisateur.
     * Les messages mis en file d'attente sont persistés pour livraison ultérieure.
     */
    private class MessageQueue {
        private final String userEmail;
        private final BlockingQueue<Message> queue;
        private Consumer<Message> consumer;

        public MessageQueue(final String userEmail) {
            this.userEmail = userEmail;
            this.queue = new LinkedBlockingQueue<>();
            System.out.println("File d'attente créée pour " + userEmail);
        }

        public synchronized void setConsumer(final Consumer<Message> consumer) {
            this.consumer = consumer;
            if (consumer != null) {
                deliverQueuedMessages();
            }
        }

        public synchronized void addToQueue(final Message message) {
            queue.offer(message);
            System.out.println("Message " + message.getId() + " ajouté en file pour " + userEmail);
            if (consumer != null) {
                deliverQueuedMessages();
            }
        }

        public synchronized void addPersistedMessage(final Message message) {
            queue.offer(message);
            System.out.println("Message persisté " + message.getId() + " chargé dans la file de " + userEmail);
        }

        /**
         * Vide la file d'attente actuelle
         */
        public synchronized void clearQueue() {
            int size = queue.size();
            if (size > 0) {
                System.out.println("Nettoyage de " + size + " messages en file pour " + userEmail);
                queue.clear();
            }
        }

        public synchronized void reloadPersistedMessages() {
            try {
                final List<Message> pending = messageRepository.loadMessages().stream()
                        .filter(m -> m.getReceiverEmail().equals(userEmail)
                                && "QUEUED".equals(m.getStatus()))
                        .collect(Collectors.toList());
                for (final Message msg : pending) {
                    if (!queue.contains(msg)) {
                        queue.offer(msg);
                        System.out.println("Rechargement du message " + msg.getId() + " pour " + userEmail);
                    }
                }
            } catch (final IOException e) {
                System.err.println("Erreur lors du rechargement des messages pour " + userEmail + ": "
                        + e.getMessage());
            }
        }

        private synchronized void deliverQueuedMessages() {
            if (consumer == null) {
                return;
            }
            while (!queue.isEmpty()) {
                final Message msg = queue.peek();
                try {
                    // Tenter de livrer le message via le callback du consommateur
                    consumer.accept(msg);
                    // Une fois livré, mettre à jour le status
                    msg.setStatus("DELIVERED");
                    messageRepository.updateMessage(msg);
                    // Ne pas supprimer le message ici ; le message sera supprimé lorsqu'il sera
                    // acquitté
                    queue.poll();
                } catch (final Exception e) {
                    System.err.println("Échec de livraison pour " + msg.getId() + ": " + e.getMessage());
                    // Interrompre la boucle afin d'essayer de redélivrer ce message plus tard
                    break;
                }
            }
        }

        public synchronized void attemptRedelivery() {
            if (consumer != null) {
                deliverQueuedMessages();
            }
        }
    }
}