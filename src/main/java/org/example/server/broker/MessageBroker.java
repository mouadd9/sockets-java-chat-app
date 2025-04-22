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
            messageDAO.createMessage(message); // Assigns persistent ID

            if (message.isGroupMessage()) {
                handleGroupMessage(message, clientTempId);
            } else {
                handleDirectMessage(message, clientTempId);
            }
        } catch (final SQLException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }
    
    private void handleGroupMessage(final Message originalMessage, final String clientTempId) throws SQLException {
        final List<Long> groupMemberIds = groupDAO.getMembersForGroup(originalMessage.getGroupId());
        for (final Long memberId : groupMemberIds) {
            if (memberId != originalMessage.getSenderUserId()) {
                sendMemberMessage(originalMessage, memberId);
            }
        }
        sendSenderConfirmation(originalMessage, clientTempId);
    }

    private void sendMemberMessage(final Message originalMessage, final Long memberId) throws SQLException {
        final Message memberMessage = createMemberMessage(originalMessage);
        final MessageQueue queue = getOrCreateQueue(memberId);
        if (queue.tryDeliver(memberMessage)) {
            memberMessage.setStatus(MessageStatus.DELIVERED);
        } else {
            memberMessage.setStatus(MessageStatus.QUEUED);
            persistMessage(memberMessage);
        }
    }
    
    private Message createMemberMessage(final Message originalMessage) {
        final Message memberMessage = new Message();
        memberMessage.setSenderUserId(originalMessage.getSenderUserId());
        memberMessage.setGroupId(originalMessage.getGroupId());
        memberMessage.setContent(originalMessage.getContent());
        memberMessage.setTimestamp(originalMessage.getTimestamp());
        memberMessage.setOriginalMessageId(originalMessage.getId());
        return memberMessage;
    }
    
    private void sendSenderConfirmation(final Message originalMessage, final String clientTempId) {
        final MessageQueue senderQueue = getOrCreateQueue(originalMessage.getSenderUserId());
        final Message senderMessage = createSenderMessage(originalMessage, clientTempId);
        // Màj du status à DELIVERED pour que le client mette à jour la persistentIdStatusMap
        senderMessage.setStatus(MessageStatus.DELIVERED);
        senderQueue.addMessageToQueue(senderMessage);
        senderQueue.deliverPendingMessages();
    }
    
    private Message createSenderMessage(final Message originalMessage, final String clientTempId) {
        final Message senderMessage = new Message();
        senderMessage.setId(originalMessage.getId());
        senderMessage.setSenderUserId(originalMessage.getSenderUserId());
        senderMessage.setGroupId(originalMessage.getGroupId());
        senderMessage.setContent(originalMessage.getContent());
        senderMessage.setTimestamp(originalMessage.getTimestamp());
        senderMessage.setClientTempId(clientTempId);
        return senderMessage;
    }
    
    private void handleDirectMessage(final Message message, final String clientTempId) throws SQLException {
        final MessageQueue receiverQueue = getOrCreateQueue(message.getReceiverUserId());
        if (receiverQueue.tryDeliver(message)) {
            message.setStatus(MessageStatus.DELIVERED);
        } else {
            message.setStatus(MessageStatus.QUEUED);
            persistMessage(message);
        }
        sendSenderCopy(message, clientTempId);
    }
    
    private void sendSenderCopy(final Message originalMessage, final String clientTempId) {
        final MessageQueue senderQueue = getOrCreateQueue(originalMessage.getSenderUserId());
        final Message senderCopy = createSenderCopy(originalMessage, clientTempId);
        if (!senderQueue.tryDeliver(senderCopy)) {
            System.err.println("Failed to send confirmation to sender " + originalMessage.getSenderUserId());
        }
    }
    
    private Message createSenderCopy(final Message originalMessage, final String clientTempId) {
        final Message senderCopy = new Message();
        senderCopy.setId(originalMessage.getId());
        senderCopy.setSenderUserId(originalMessage.getSenderUserId());
        senderCopy.setReceiverUserId(originalMessage.getReceiverUserId());
        senderCopy.setContent(originalMessage.getContent());
        senderCopy.setTimestamp(originalMessage.getTimestamp());
        senderCopy.setStatus(originalMessage.getStatus());
        senderCopy.setClientTempId(clientTempId);
        return senderCopy;
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
                        updateMessageStatusAndNotifySender(message);
                    } else {
                        break;
                    }
                } catch (final Exception e) {
                    System.err.println("Erreur de livraison, réessai plus tard: " + e.getMessage());
                    break;
                }
            }
        }

        private void updateMessageStatusAndNotifySender(final Message message) throws SQLException {
            message.setStatus(MessageStatus.DELIVERED);
            messageDAO.updateMessageStatus(message.getId(), MessageStatus.DELIVERED);

            final Message ackMessage = new Message();
            ackMessage.setOriginalMessageId(message.getId());
            ackMessage.setStatus(MessageStatus.DELIVERED);

            final long senderId = message.getSenderUserId();
            final MessageQueue senderQueue = userQueues.get(senderId);
            if (senderQueue != null && senderQueue.listener != null) {
                try {
                    senderQueue.listener.onMessageReceived(ackMessage);
                } catch (final IOException e) {
                    System.err.println("Échec de l'ACK pour " + senderId);
                }
            }
        }
    }
}