package org.example;

import org.example.Entities.Message;
import org.example.Entities.Utilisateur;
import org.example.repository.JsonMessageRepository;
import org.example.repository.JsonUserRepository;
import org.example.service.MessageService;
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

public class MessageServiceTest {
    
    private MessageService messageService;
    private JsonMessageRepository messageRepository;
    private JsonUserRepository userRepository;
    private File tempMessagesFile;
    private File tempUsersFile;

    @BeforeEach
    public void setUp(@TempDir final Path tempDir) throws Exception {
        // Créer les fichiers JSON temporaires
        tempMessagesFile = new File(tempDir.toFile(), "messages.json");
        tempUsersFile = new File(tempDir.toFile(), "utilisateurs.json");
        
        try (FileWriter messageWriter = new FileWriter(tempMessagesFile);
             FileWriter userWriter = new FileWriter(tempUsersFile)) {
            messageWriter.write("[]");
            userWriter.write("[]");
        }

        // Configuration du message repository
        messageRepository = new JsonMessageRepository();
        Field messagesFileField = JsonMessageRepository.class.getDeclaredField("messagesFile");
        messagesFileField.setAccessible(true);
        messagesFileField.set(messageRepository, tempMessagesFile);

        // Configuration du user repository
        userRepository = new JsonUserRepository();
        Field usersFileField = JsonUserRepository.class.getDeclaredField("usersFile");
        usersFileField.setAccessible(true);
        usersFileField.set(userRepository, tempUsersFile);

        // Création du service avec injection des repositories
        messageService = new MessageService();
        Field messageRepoField = MessageService.class.getDeclaredField("messageRepository");
        Field userRepoField = MessageService.class.getDeclaredField("userRepository");
        messageRepoField.setAccessible(true);
        userRepoField.setAccessible(true);
        messageRepoField.set(messageService, messageRepository);
        userRepoField.set(messageService, userRepository);

        // Créer des utilisateurs de test
        setupTestUsers();
    }

    private void setupTestUsers() throws IOException {
        Utilisateur user1 = new Utilisateur("sender@test.com", "password1");
        Utilisateur user2 = new Utilisateur("receiver@test.com", "password2");
        userRepository.saveUser(user1);
        userRepository.saveUser(user2);
    }

    @Test
    public void testSendMessage_Success() throws IOException {
        boolean result = messageService.sendMessage(
            "sender@test.com",
            "receiver@test.com",
            "Hello receiver!"
        );

        assertTrue(result, "L'envoi du message devrait réussir");
        List<Message> messages = messageRepository.loadMessages();
        assertEquals(1, messages.size(), "Un seul message devrait être enregistré");
        assertEquals("Hello receiver!", messages.get(0).getContent());
    }

    @Test
    public void testSendMessage_NonExistentUser() throws IOException {
        boolean result = messageService.sendMessage(
            "nonexistent@test.com",
            "receiver@test.com",
            "This should fail"
        );

        assertFalse(result, "L'envoi devrait échouer avec un utilisateur inexistant");
        assertTrue(messageRepository.loadMessages().isEmpty(), "Aucun message ne devrait être enregistré");
    }

    @Test
    public void testGetUnreadMessages() throws IOException {
        // Envoyer deux messages dont un lu
        messageService.sendMessage("sender@test.com", "receiver@test.com", "Message 1");
        messageService.sendMessage("sender@test.com", "receiver@test.com", "Message 2");
        
        // Marquer le premier message comme lu
        List<Message> allMessages = messageRepository.loadMessages();
        messageRepository.markMessageAsRead(allMessages.get(0).getId());

        // Vérifier les messages non lus
        List<Message> unreadMessages = messageService.getUnreadMessages("receiver@test.com");
        assertEquals(1, unreadMessages.size(), "Il devrait y avoir un seul message non lu");
        assertEquals("Message 2", unreadMessages.get(0).getContent());
    }

    @Test
    public void testMarkMessageAsRead() throws IOException {
        // Envoyer un message
        messageService.sendMessage("sender@test.com", "receiver@test.com", "Test message");
        
        // Récupérer l'ID du message
        String messageId = messageRepository.loadMessages().get(0).getId();
        
        // Marquer comme lu
        messageService.markMessageAsRead(messageId);
        
        // Vérifier que le message est marqué comme lu
        List<Message> messages = messageRepository.loadMessages();
        assertTrue(messages.get(0).isRead(), "Le message devrait être marqué comme lu");
    }
}