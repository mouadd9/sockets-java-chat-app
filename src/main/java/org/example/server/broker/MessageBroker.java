package org.example.server.broker;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.example.server.ClientHandler;
import org.example.server.UdpCallServer;
import org.example.shared.dao.GroupDAO;
import org.example.shared.dao.MessageDAO;
import org.example.shared.model.CallSignal;
import org.example.shared.model.Message;
import org.example.shared.model.enums.MessageStatus;

public class MessageBroker {
    private static MessageBroker instance;
    private final Map<Long, MessageQueue> userQueues;
    private final MessageDAO messageDAO;

    public static synchronized MessageBroker getInstance() {
        if (instance == null) {
            instance = new MessageBroker();
        }
        return instance;
    }

    private MessageBroker() {
        this.userQueues = new ConcurrentHashMap<>();
        this.messageDAO = new MessageDAO();
    }

    public void registerListener(final long userId, final ClientHandler listener) {
        final MessageQueue queue = getOrCreateQueue(userId);
        queue.setListener(listener);
        queue.loadPersistedMessages();
        queue.deliverPendingMessages();
    }

    public void unregisterListener(final long userId) {
        final MessageQueue queue = userQueues.remove(userId);
        if (queue != null) {
            queue.setListener(null);
            queue.clearQueue();
        }
    }

    public void sendMessage(final Message message) {
        if (message.isGroupMessage()) {
            // Récupérer les membres du groupe via GroupDAO
            final GroupDAO groupDAO = new GroupDAO();
            final List<Long> groupMemberIds = groupDAO.getMembersForGroup(message.getGroupId());
            for (final Long memberId : groupMemberIds) {
                // Ignorer l'expéditeur
                if (memberId != message.getSenderUserId()) {
                    final Message messageCopy = copyMessageForRecipient(message);
                    final MessageQueue queue = getOrCreateQueue(memberId);
                    if (queue != null && queue.tryDeliver(messageCopy)) {
                        messageCopy.setStatus(MessageStatus.DELIVERED);
                    } else {
                        persistMessage(messageCopy);
                    }
                }
            }
        } else {
            final MessageQueue queue = userQueues.get(message.getReceiverUserId());
            if (queue != null && queue.tryDeliver(message)) {
                message.setStatus(MessageStatus.DELIVERED);
            } else {
                persistMessage(message);
            }
        }
    }

    /**
     * Achemine un signal d'appel vers le destinataire approprié.
     * 
     * @param signal Le signal d'appel à acheminer
     */
    public void routeCallSignal(final CallSignal signal) {
        try {
            // Traiter les signaux selon leur type
            switch (signal.getType()) {
                case CALL_REQUEST:
                    // Enregistrer la session d'appel dans le serveur UDP pour un éventuel relais
                    UdpCallServer.getInstance().registerSession(signal.getSessionId());
                    // Transmettre la demande d'appel au destinataire
                    deliverCallSignal(signal.getReceiverUserId(), signal);
                    break;
                    
                case CALL_ACCEPT:
                    // Enregistrer le point de terminaison du destinataire dans le serveur UDP
                    UdpCallServer.getInstance().registerEndpoint(
                            signal.getSessionId(), 
                            false, 
                            java.net.InetAddress.getByName(signal.getIpAddress()), 
                            signal.getPort());
                    // Transmettre l'acceptation à l'appelant
                    deliverCallSignal(signal.getReceiverUserId(), signal);
                    break;
                    
                case CALL_REJECT:
                case CALL_BUSY:
                    // Supprimer la session d'appel du serveur UDP
                    UdpCallServer.getInstance().removeSession(signal.getSessionId());
                    // Transmettre le rejet à l'appelant
                    deliverCallSignal(signal.getReceiverUserId(), signal);
                    break;
                    
                case CALL_END:
                    // Supprimer la session d'appel du serveur UDP
                    UdpCallServer.getInstance().removeSession(signal.getSessionId());
                    // Transmettre la fin d'appel à l'autre partie
                    deliverCallSignal(signal.getReceiverUserId(), signal);
                    break;
            }
        } catch (final Exception e) {
            System.err.println("Erreur lors du routage du signal d'appel: " + e.getMessage());
        }
    }
    
    /**
     * Délivre un signal d'appel à un utilisateur spécifique.
     * 
     * @param userId L'ID de l'utilisateur destinataire
     * @param signal Le signal d'appel à délivrer
     */
    private void deliverCallSignal(final long userId, final CallSignal signal) {
        final MessageQueue queue = userQueues.get(userId);
        if (queue != null) {
            queue.tryDeliverCallSignal(signal);
        }
    }

    private void persistMessage(final Message message) {
        message.setStatus(MessageStatus.QUEUED);
        try {
            messageDAO.createMessage(message);
        } catch (final Exception e) {
            System.err.println("Failed to queue message: " + e.getMessage());
        }
    }

    private MessageQueue getOrCreateQueue(final long userId) {
        return userQueues.computeIfAbsent(userId, MessageQueue::new);
    }

    private Message copyMessageForRecipient(final Message originalMessage) {
        final Message copy = new Message();
        copy.setSenderUserId(originalMessage.getSenderUserId());
        copy.setContent(originalMessage.getContent());
        copy.setTimestamp(originalMessage.getTimestamp());
        copy.setGroupId(originalMessage.getGroupId());
        return copy;
    }

    private class MessageQueue {
        private final long userId;
        private final BlockingQueue<Message> messages;
        private ClientHandler listener;

        MessageQueue(final long userId) {
            this.userId = userId;
            this.messages = new LinkedBlockingQueue<>();
        }

        synchronized void setListener(final ClientHandler listener) {
            this.listener = listener;
        }

        void addMessageToQueue(final Message message) {
            messages.offer(message);
        }

        synchronized void clearQueue() {
            messages.clear();
        }

        synchronized void loadPersistedMessages() {
            try {
                final List<Message> pendingMessages = messageDAO.getPendingMessagesForUser(userId);
                pendingMessages.forEach(this::addMessageToQueue);
            } catch (final Exception e) {
                System.err.println("Error reloading persisted messages for user " + userId + ": " + e.getMessage());
            }
        }

        boolean tryDeliver(final Message message) {
            if (listener != null) {
                try {
                    listener.onMessageReceived(message);
                    return true;
                } catch (final IOException e) {
                    System.err.println("Delivery failed for message " + message.getId());
                }
            }
            return false;
        }
        
        /**
         * Tente de délivrer un signal d'appel au client.
         * 
         * @param signal Le signal d'appel à délivrer
         * @return true si la livraison a réussi
         */
        boolean tryDeliverCallSignal(final CallSignal signal) {
            if (listener != null) {
                try {
                    listener.onCallSignalReceived(signal);
                    return true;
                } catch (final IOException e) {
                    System.err.println("Delivery failed for call signal to user " + userId);
                }
            }
            return false;
        }

        void deliverPendingMessages() {
            while (!messages.isEmpty()) {
                final Message message = messages.peek();
                if (tryDeliver(message)) {
                    try {
                        messages.poll();
                        messageDAO.deleteMessage(message.getId());
                    } catch (final Exception e) {
                        System.err.println("Delivery failed, keeping message in queue");
                        break;
                    }
                } else {
                    break;
                }
            }
        }
    }
}