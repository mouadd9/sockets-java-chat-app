package org.example.broker;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.example.model.Message;
import org.example.repository.JsonMessageRepository;
import org.example.server.ClientHandler;

public class MessageBroker {
    private static MessageBroker instance;
    private final Map<String, MessageQueue> userQueues;
    private final JsonMessageRepository messageRepo;

    public static synchronized MessageBroker getInstance() {
        if (instance == null) {
            instance = new MessageBroker();
        }
        return instance;
    }

    private MessageBroker() {
        this.userQueues = new ConcurrentHashMap<>();
        this.messageRepo = new JsonMessageRepository();
        loadPendingMessages();
    }

    // Subscription
    public void registerListener(final String email, final ClientHandler listener) {

        final MessageQueue queue = getOrCreateQueue(email);
        queue.setListener(listener);

        queue.clearQueue();
        queue.reloadPersistedMessages();
        queue.deliverPendingMessages();
    }

    public void unregisterListener(final String email) {
        final MessageQueue queue = userQueues.remove(email);
        if (queue != null) {
            queue.setListener(null);
            queue.clearQueue();
        }
    }

    public void sendMessage(final Message message) {
        initializeMessage(message);

        final MessageQueue queue = userQueues.get(message.getReceiverEmail());
        if (queue != null && queue.tryDeliver(message)) {
            message.setStatus("DELIVERED");
        } else {
            PersistMessage(message);
        }
    }

    private void initializeMessage(final Message message) {
        if (message.getId() == null) {
            message.setId(UUID.randomUUID().toString());
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }
        message.setStatus("PENDING");
    }

    private void PersistMessage(final Message message) {
        message.setStatus("QUEUED");
        try {
            messageRepo.saveMessage(message);
            // getOrCreateQueue(message.getReceiverEmail()).addMessageToQueue(message);
        } catch (final IOException e) {
            System.err.println("Failed to queue message: " + e.getMessage());
        }
    }

    private MessageQueue getOrCreateQueue(final String email) {
        return userQueues.computeIfAbsent(email, MessageQueue::new);
    }

    private void loadPendingMessages() {
        try {
            messageRepo.loadMessages().stream()
                    .filter(m -> "QUEUED".equals(m.getStatus()))
                    .forEach(m -> getOrCreateQueue(m.getReceiverEmail()).addMessageToQueue(m));
        } catch (final IOException e) {
            System.err.println("Error loading pending messages: " + e.getMessage());
        }
    }

    private class MessageQueue {
        private final String userEmail;
        private final BlockingQueue<Message> messages;
        private ClientHandler listener;

        MessageQueue(final String userEmail) {
            this.userEmail = userEmail;
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

        synchronized void reloadPersistedMessages() {
            try {
                messageRepo.loadMessages().stream()
                        .filter(m -> "QUEUED".equals(m.getStatus()) &&
                                userEmail.equals(m.getReceiverEmail()))
                        .forEach(this::addMessageToQueue);
            } catch (final IOException e) {
                System.err.println("Error reloading persisted messages for " + userEmail + ": " + e.getMessage());
            }
        }

        boolean tryDeliver(final Message message) {
            if (listener != null) {
                try {
                    listener.onMessageReceived(message);
                    return true;
                } catch (final IOException e) {
                    System.err.println("Delivery failed for " + message.getId());
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