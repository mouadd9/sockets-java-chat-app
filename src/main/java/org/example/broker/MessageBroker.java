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
        // a broker has a map of email and MessageQueue
        // each message Queue is for a User
        this.userQueues = new ConcurrentHashMap<>();
        this.messageRepo = new JsonMessageRepository();
    }

    // when a user authenticats this function registers the User so he can get queued messages that were sent to him when he was offline, and for further messaging before when he is online 
    // we solve this by introducing queues, each user should be registered in the broker in order to get his queued messages
    public void registerListener(final String email, final ClientHandler listener) {
        // we first need to create a queue for the authenticated user, and then register the user in the broker using his email
        final MessageQueue queue = getOrCreateQueue(email);
        // then after creatingn and registering the queue in the broker we will set the client handler in the queue so we can send to the client his queued messages
        queue.setListener(listener);
        // this will load Qeued messages for this user from the database to the user messages queue
        queue.loadPersistedMessages();
        // and this will deliver them via the Client Handler
        queue.deliverPendingMessages();
    }

    // this is called when the user disconnects, this removes the user from the broker and clears its queue
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

    // this function returns the Message Queue of a User  
    private MessageQueue getOrCreateQueue(final String email) {
        // this is a key element here, the idea is, if the message queue exists in the map we return it, if not we create it.
        return userQueues.computeIfAbsent(email, MessageQueue::new);
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

        // this function loads all Queued messages from the database into the message queues of the reveicers (for each Queued message we check retrieve the Queue of the receiver we then add the Queued message to it)
        synchronized void loadPersistedMessages() {
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