package org.example;

import org.example.Entities.Message;
import org.example.repository.JsonMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class JsonMessageRepositoryTest {
    
    private JsonMessageRepository messageRepository;
    private File tempMessagesFile;

    @BeforeEach
    public void setUp(@TempDir final Path tempDir) throws Exception {
        // Créer un fichier JSON temporaire avec un tableau vide
        tempMessagesFile = new File(tempDir.toFile(), "messages.json");
        try (FileWriter writer = new FileWriter(tempMessagesFile)) {
            writer.write("[]");
        }
        
        // Instancier le repository et remplacer son champ messagesFile via réflexion
        messageRepository = new JsonMessageRepository();
        final Field messagesFileField = JsonMessageRepository.class.getDeclaredField("messagesFile");
        messagesFileField.setAccessible(true);
        messagesFileField.set(messageRepository, tempMessagesFile);
    }

    @Test
    public void testSaveAndLoadMessage() throws IOException {
        Message message = new Message("sender@test.com", "receiver@test.com", "Hello!");
        messageRepository.saveMessage(message);

        List<Message> messages = messageRepository.loadMessages();
        assertFalse(messages.isEmpty(), "Messages list should not be empty");
        assertEquals(1, messages.size(), "Should have exactly one message");
        assertEquals("Hello!", messages.get(0).getContent());
    }

    @Test
    public void testGetUnreadMessages() throws IOException {
        // Sauvegarder deux messages, un lu et un non lu
        Message readMessage = new Message("sender@test.com", "receiver@test.com", "Read message");
        readMessage.setRead(true);
        Message unreadMessage = new Message("sender@test.com", "receiver@test.com", "Unread message");
        
        messageRepository.saveMessage(readMessage);
        messageRepository.saveMessage(unreadMessage);

        List<Message> unreadMessages = messageRepository.getUnreadMessagesForUser("receiver@test.com");
        assertEquals(1, unreadMessages.size(), "Should have exactly one unread message");
        assertEquals("Unread message", unreadMessages.get(0).getContent());
    }

    @Test
    public void testMarkMessageAsRead() throws IOException {
        Message message = new Message("sender@test.com", "receiver@test.com", "Test message");
        messageRepository.saveMessage(message);

        assertFalse(message.isRead(), "Message should be unread initially");
        messageRepository.markMessageAsRead(message.getId());

        List<Message> messages = messageRepository.loadMessages();
        assertTrue(messages.get(0).isRead(), "Message should be marked as read");
    }

    @Test
    public void testGetConversation() throws IOException {
        Message msg1 = new Message("user1@test.com", "user2@test.com", "Hello");
        Message msg2 = new Message("user2@test.com", "user1@test.com", "Hi back");
        Message msg3 = new Message("user1@test.com", "user3@test.com", "Different conversation");

        messageRepository.saveMessage(msg1);
        messageRepository.saveMessage(msg2);
        messageRepository.saveMessage(msg3);

        List<Message> conversation = messageRepository.getConversation("user1@test.com", "user2@test.com");
        assertEquals(2, conversation.size(), "Conversation should have 2 messages");
    }

    @Test
    public void testDeleteMessage() throws IOException {
        Message message = new Message("sender@test.com", "receiver@test.com", "To be deleted");
        messageRepository.saveMessage(message);

        assertTrue(messageRepository.deleteMessage(message.getId()), "Message should be deleted successfully");
        assertTrue(messageRepository.loadMessages().isEmpty(), "Messages list should be empty after deletion");
    }
}