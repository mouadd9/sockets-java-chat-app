package org.example.server.broker;

import java.io.IOException;
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
        
        if (message.isGroupMessage()) {
            final List<Long> groupMemberIds = groupDAO.getMembersForGroup(message.getGroupId());
            for (final Long memberId : groupMemberIds) {
                // Ignorer l'expéditeur
                if (memberId != message.getSenderUserId()) {
                    final MessageQueue queue = getOrCreateQueue(memberId);
                    if (queue != null ) {
                        message.setStatus(MessageStatus.DELIVERED);
                        queue.tryDeliver(message);
                    } else {
                        persistMessage(message);
                    }
                }
            }
        } else {
            final MessageQueue queue = userQueues.get(message.getReceiverUserId());
            if (queue != null) {
                message.setStatus(MessageStatus.DELIVERED);
                queue.tryDeliver(message);
            } else {
                persistMessage(message);
            }
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