package org.example.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.example.model.Message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// this class will provide methods that will do the following : 
// - a method to save a list of messages objects to a json file (messages.json), by writing to the json file
// - a method to load all messages from the JSON file (messages.json) and return them as a list of messages objects 
public class JsonMessageRepository {
    // location of the messages.json
    private static final String MESSAGES_FILE = "src/main/data/messages.json";
    private final ObjectMapper objectMapper;
    private final File messagesFile;

    // constructor that creates an instance of ObjectMapper()
    public JsonMessageRepository() {
        // object Mapper is a class provided by the jackson library it serializes and
        // deserialzes JSON strings;
        this.objectMapper = new ObjectMapper();
        // Register JavaTimeModule to handle LocalDateTime serialization
        this.objectMapper.registerModule(new JavaTimeModule());
        // this provides the path for the messages.json file
        this.messagesFile = new File(MESSAGES_FILE);
        initializeMessagesFile();
    }

    private void initializeMessagesFile() {
        try {
            if (!messagesFile.exists()) {
                // Créer le répertoire parent s'il n'existe pas
                File parentDir = messagesFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                // Créer le fichier avec un tableau vide
                saveMessages(new ArrayList<>());
                System.out.println("Fichier messages.json initialisé avec succès");
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'initialisation du fichier messages.json: " + e.getMessage());
        }
    }

    // this method uses a jackson method used to serialize any Java value as JSON
    // output, written to File provided.
    public void saveMessages(final List<Message> messages) throws IOException {
        try {
            // Créer une sauvegarde avant d'écrire
            if (messagesFile.exists()) {
                File backupFile = new File(MESSAGES_FILE + ".bak");
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                messagesFile.renameTo(backupFile);
            }
            
            // Écrire les nouveaux messages
            objectMapper.writeValue(messagesFile, messages);
            System.out.println("Messages sauvegardés avec succès");
        } catch (IOException e) {
            System.err.println("Erreur lors de la sauvegarde des messages: " + e.getMessage());
            // Restaurer la sauvegarde si disponible
            File backupFile = new File(MESSAGES_FILE + ".bak");
            if (backupFile.exists()) {
                backupFile.renameTo(messagesFile);
            }
            throw e;
        }
    }

    // this method will retrieve data from the JSON file and return a list of
    // messages
    // the issue here is, jackson do not know to what type of objects the list will
    // have
    public List<Message> loadMessages() throws IOException {
        if (!messagesFile.exists()) {
            System.out.println("Fichier messages.json non trouvé, création d'un nouveau fichier");
            saveMessages(new ArrayList<>());
            return new ArrayList<>();
        }

        try {
            final CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(ArrayList.class, Message.class);
            return objectMapper.readValue(messagesFile, listType);
        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture des messages: " + e.getMessage());
            
            // Essayer de restaurer depuis la sauvegarde
            File backupFile = new File(MESSAGES_FILE + ".bak");
            if (backupFile.exists()) {
                System.out.println("Tentative de restauration depuis la sauvegarde...");
                try {
                    final CollectionType listType = objectMapper.getTypeFactory()
                            .constructCollectionType(ArrayList.class, Message.class);
                    List<Message> messages = objectMapper.readValue(backupFile, listType);
                    // Si la restauration réussit, sauvegarder les messages restaurés
                    saveMessages(messages);
                    return messages;
                } catch (IOException backupError) {
                    System.err.println("Échec de la restauration depuis la sauvegarde: " + backupError.getMessage());
                }
            }
            
            // Si tout échoue, réinitialiser le fichier
            System.out.println("Réinitialisation du fichier messages.json");
            List<Message> emptyList = new ArrayList<>();
            saveMessages(emptyList);
            return emptyList;
        }
    }

    public void saveMessage(final Message message) throws IOException {
        // we will first load all messages from the messages.json using the Jackson
        // desirializer
        final List<Message> messages = loadMessages();
        // then we will add the new message at the end of the list
        messages.add(message);
        System.out.println("saving message: ");

        // then we serialize and save the new array of messages
        saveMessages(messages);
        System.out.println("Message saved: " + message.getContent());
    }

    /**
     * Met à jour un message existant
     */
    public void updateMessage(final Message updatedMessage) throws IOException {
        final List<Message> messages = loadMessages();
        
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(updatedMessage.getId())) {
                messages.set(i, updatedMessage);
                saveMessages(messages);
                return;
            }
        }
        
        // Si le message n'existe pas, le sauvegarder
        messages.add(updatedMessage);
        saveMessages(messages);
    }
    
    /**
     * Recherche un message par son ID
     */
    public Optional<Message> findById(final String messageId) throws IOException {
        return loadMessages().stream()
                .filter(message -> message.getId().equals(messageId))
                .findFirst();
    }

    // Get all messages for a specific user (both sent and received)
    public List<Message> getUserMessages(final String userEmail) throws IOException {
        return loadMessages().stream()
                .filter(m -> m.getSenderEmail().equals(userEmail) ||
                        m.getReceiverEmail().equals(userEmail))
                .collect(Collectors.toList());
    }

    // Get only messages sent from the user we pass in argument
    public List<Message> getSentMessages(final String userEmail) throws IOException {
        return loadMessages()
                .stream() // here we turn this into a stream
                .filter(m -> m.getSenderEmail().equals(userEmail))
                .collect(Collectors.toList()); // this will gather all sent messages and put them into a List
    }

    // this will get only messages received by a user passed in
    public List<Message> getReceivedMessages(final String userEmail) throws IOException {
        return loadMessages()
                .stream()
                .filter(m -> m.getReceiverEmail().equals(userEmail))
                .collect(Collectors.toList()); // here we tranform a stream back into a usable list
    }

    // Get only unread messages for a user
    public List<Message> getUnreadMessages(final String userEmail) throws IOException {
        return loadMessages()
                .stream()
                .filter(m -> m.getReceiverEmail().equals(userEmail) && !m.isRead())
                .collect(Collectors.toList()); // this will gather all received and unread messages
    }

    // Get conversation between two users
    public List<Message> getConversation(final String user1Email, final String user2Email) throws IOException {
        return loadMessages().stream()
                .filter(m -> (m.getSenderEmail().equals(user1Email) && m.getReceiverEmail().equals(user2Email)) ||
                        (m.getSenderEmail().equals(user2Email) && m.getReceiverEmail().equals(user1Email)))
                .collect(Collectors.toList());
    }

    // Delete a message
    public boolean deleteMessage(final String messageId) throws IOException {
        final List<Message> messages = loadMessages();
        final boolean removed = messages.removeIf(m -> m.getId().equals(messageId));
        if (removed) {
            // Mettre à jour les messages qui citaient ce message
            messages.forEach(m -> {
                if (messageId.equals(m.getQuotedMessageId())) {
                    m.setQuotedMessageId(null); // Supprimer la référence
                }
            });
            saveMessages(messages);
            System.out.println("Message " + messageId + " deleted");
        }
        return removed;
    }
}
