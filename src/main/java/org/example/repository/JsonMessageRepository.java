package org.example.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.Entities.Message;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JsonMessageRepository {
    private static final String MESSAGES_FILE = "data/messages.json";
    private final ObjectMapper objectMapper;
    private final File messagesFile;

    public JsonMessageRepository() {
        this.objectMapper = new ObjectMapper();
        // Register JavaTimeModule to handle LocalDateTime serialization
        this.objectMapper.registerModule(new JavaTimeModule());
        this.messagesFile = new File(MESSAGES_FILE);
    }

    public void saveMessages(List<Message> messages) throws IOException {
        objectMapper.writeValue(messagesFile, messages);
    }

    public List<Message> loadMessages() throws IOException {
        if (!messagesFile.exists()) {
            throw new IOException("messages.json file not found in data directory");
        }
        CollectionType listType = objectMapper.getTypeFactory()
            .constructCollectionType(ArrayList.class, Message.class);
        return objectMapper.readValue(messagesFile, listType);
    }

    public void saveMessage(Message message) throws IOException {
        List<Message> messages = loadMessages();
        messages.add(message);
        saveMessages(messages);
        System.out.println("Message saved: " + message.getContent());
    }

    // Get all messages for a specific user (both sent and received)
    public List<Message> getUserMessages(String userEmail) throws IOException {
        return loadMessages().stream()
            .filter(m -> m.getSenderEmail().equals(userEmail) || 
                        m.getReceiverEmail().equals(userEmail))
            .collect(Collectors.toList());
    }

    // Get only unread messages for a user
    public List<Message> getUnreadMessagesForUser(String userEmail) throws IOException {
        return loadMessages().stream()
            .filter(m -> m.getReceiverEmail().equals(userEmail) && !m.isRead())
            .collect(Collectors.toList());
    }

    // Mark a message as read
    public void markMessageAsRead(String messageId) throws IOException {
        List<Message> messages = loadMessages();
        messages.stream()
            .filter(m -> m.getId().equals(messageId))
            .findFirst()
            .ifPresent(m -> {
                m.setRead(true);
                System.out.println("Message " + messageId + " marked as read");
            });
        saveMessages(messages);
    }

    // Get conversation between two users
    public List<Message> getConversation(String user1Email, String user2Email) throws IOException {
        return loadMessages().stream()
            .filter(m -> (m.getSenderEmail().equals(user1Email) && m.getReceiverEmail().equals(user2Email)) ||
                        (m.getSenderEmail().equals(user2Email) && m.getReceiverEmail().equals(user1Email)))
            .collect(Collectors.toList());
    }

    // Delete a message
    public boolean deleteMessage(String messageId) throws IOException {
        List<Message> messages = loadMessages();
        boolean removed = messages.removeIf(m -> m.getId().equals(messageId));
        if (removed) {
            saveMessages(messages);
            System.out.println("Message " + messageId + " deleted");
        }
        return removed;
    }
}
