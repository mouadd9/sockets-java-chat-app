package org.example.server.broker;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.example.server.ClientHandler;
import org.example.shared.dao.GroupDAO;
import org.example.shared.dao.MessageDAO;
import org.example.shared.model.Message;
import org.example.shared.model.enums.MessageStatus;

public class MessageBroker {
    private static MessageBroker instance;
    private final Map<Long, MessageQueue> userQueues;
    private final MessageDAO messageDAO;
    final GroupDAO groupDAO;

    public static synchronized MessageBroker getInstance() {
        if (instance == null) {
            instance = new MessageBroker();
        }
        return instance;
    }

    private MessageBroker() {
        this.userQueues = new ConcurrentHashMap<>();
        this.messageDAO = new MessageDAO();
        this.groupDAO = new GroupDAO();
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
        try {
            final String clientTempId = message.getClientTempId();
            messageDAO.createMessage(message); // Affecte l'ID persistant au message original

            if (message.isGroupMessage()) {
                final List<Long> groupMemberIds = groupDAO.getMembersForGroup(message.getGroupId());
                for (final Long memberId : groupMemberIds) {
                    if (memberId != message.getSenderUserId()) {
                        // ...logique d'envoi au groupe...
                        final Message memberMessage = new Message();
                        memberMessage.setSenderUserId(message.getSenderUserId());
                        memberMessage.setGroupId(message.getGroupId());
                        memberMessage.setSenderUserId(message.getSenderUserId());
                        memberMessage.setGroupId(message.getGroupId());
                        memberMessage.setContent(message.getContent());
                        memberMessage.setTimestamp(message.getTimestamp());
                        memberMessage.setOriginalMessageId(message.getId()); // Référence vers le message original

                        final MessageQueue queue = getOrCreateQueue(memberId);
                        if (queue.tryDeliver(memberMessage)) {
                            memberMessage.setStatus(MessageStatus.DELIVERED);
                        } else {
                            memberMessage.setStatus(MessageStatus.QUEUED);
                            persistMessage(memberMessage);
                        }
                    }
                }
                // Retourner une copie du message original à l'expéditeur
                final MessageQueue senderQueue = getOrCreateQueue(message.getSenderUserId());
                final Message senderMessage = new Message();
                senderMessage.setId(message.getId()); // transmet l'ID persistant
                senderMessage.setSenderUserId(message.getSenderUserId());
                senderMessage.setGroupId(message.getGroupId());
                senderMessage.setContent(message.getContent());
                senderMessage.setTimestamp(message.getTimestamp());
                senderMessage.setStatus(MessageStatus.SENT);
                senderMessage.setClientTempId(clientTempId); // Lien client
                senderQueue.addMessageToQueue(senderMessage);
                senderQueue.deliverPendingMessages();

            } else {
                final MessageQueue receiverQueue = getOrCreateQueue(message.getReceiverUserId());
                if (receiverQueue.tryDeliver(message)) {
                    message.setStatus(MessageStatus.DELIVERED);
                } else {
                    message.setStatus(MessageStatus.QUEUED);
                    persistMessage(message);
                }
                // Envoyer une copie au sender pour confirmer l'envoi
                final MessageQueue senderQueue = getOrCreateQueue(message.getSenderUserId());
                final Message senderCopy = new Message();
                senderCopy.setId(message.getId()); // ID persistant
                senderCopy.setSenderUserId(message.getSenderUserId());
                senderCopy.setReceiverUserId(message.getReceiverUserId());
                senderCopy.setContent(message.getContent());
                senderCopy.setTimestamp(message.getTimestamp());
                senderCopy.setStatus(message.getStatus());
                senderCopy.setClientTempId(clientTempId); // Lien client

                if (!senderQueue.tryDeliver(senderCopy)) {
                    System.err.println("Impossible de renvoyer la confirmation au sender " + message.getSenderUserId());
                }
            }
        } catch (final SQLException e) {
            System.err.println("Erreur d'envoi: " + e.getMessage());
        }
    }

    public void acknowledgeMessageRead(final Message message) throws IOException {
        try {
            final Message originalMessage = messageDAO.findMessageById(message.getOriginalMessageId());
            if (originalMessage != null) {
                final long senderUserId = originalMessage.getSenderUserId();
                final Message ackMessage = new Message();
                ackMessage.setOriginalMessageId(originalMessage.getId());
                ackMessage.setStatus(MessageStatus.READ);

                final MessageQueue senderQueue = userQueues.get(senderUserId);
                if (senderQueue != null && senderQueue.listener != null) {
                    senderQueue.listener.onMessageReceived(ackMessage);
                }
            }
        } catch (final SQLException e) {
            System.err.println("Failed to process ACK: " + e.getMessage());
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

        void deliverPendingMessages() {
            while (!messages.isEmpty()) {
                final Message message = messages.peek();
                try {
                    if (tryDeliver(message)) {
                        messages.poll();
                        messageDAO.deleteMessage(message.getId());
                    } else {
                        break;
                    }
                } catch (final Exception e) {
                    System.err.println("Erreur de livraison, réessai plus tard: " + e.getMessage());
                    break;
                }
            }
        }
    }
}