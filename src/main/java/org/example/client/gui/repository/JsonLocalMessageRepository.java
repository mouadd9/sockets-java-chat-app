package org.example.client.gui.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.example.shared.model.Message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Classe de persistance locale pour l'historique des messages d'un utilisateur.
 * L'historique est stocké sous forme de fichier JSON dans le dossier
 * "src/main/resources/client_data".
 */
public class JsonLocalMessageRepository {
    // Utilisation du répertoire de projet pour stocker les données clients
    private static final String LOCAL_FOLDER = System.getProperty("user.dir")
            + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator
            + "client_data";
    private final ObjectMapper objectMapper;

    public JsonLocalMessageRepository() {
        this.objectMapper = new ObjectMapper();
        // Register the JavaTimeModule for date-time support
        this.objectMapper.registerModule(new JavaTimeModule());
        // Pretty-print output
        this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        ensureLocalFolderExists();
    }

    private void ensureLocalFolderExists() {
        final Path folderPath = Paths.get(LOCAL_FOLDER);
        if (!folderPath.toFile().exists()) {
            try {
                folderPath.toFile().mkdirs();
            } catch (final Exception e) {
                System.err.println("Erreur lors de la création du dossier client_data : " + e.getMessage());
            }
        }
    }

    /**
     * Renvoie le chemin complet du fichier de l'utilisateur.
     */
    private String getUserFilePath(final String userEmail) {
        // Remplacer les caractères spéciaux pour créer un nom de fichier valide
        return LOCAL_FOLDER + File.separator + userEmail.replace("@", "_at_") + "_messages.json";
    }

    /**
     * Charge la liste des messages locaux pour l'utilisateur.
     */
    public List<Message> loadLocalMessages(final String userEmail) throws IOException {
        final String filePath = getUserFilePath(userEmail);
        final File file = new File(filePath);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        final CollectionType listType = objectMapper.getTypeFactory()
                .constructCollectionType(ArrayList.class, Message.class);
        return objectMapper.readValue(file, listType);
    }

    /**
     * Enregistre la liste des messages locaux pour l'utilisateur.
     */
    public void saveLocalMessages(final String userEmail, final List<Message> messages) throws IOException {
        final String filePath = getUserFilePath(userEmail);
        objectMapper.writeValue(new File(filePath), messages);
    }

    /**
     * Ajoute un nouveau message dans l'historique local pour l'utilisateur.
     */
    public void addLocalMessage(final String userEmail, final Message message) throws IOException {
        final List<Message> messages = loadLocalMessages(userEmail);
        messages.add(message);
        saveLocalMessages(userEmail, messages);
    }

    /**
     * Retourne la conversation entre deux utilisateurs en filtrant les messages
     * directs (sans groupId).
     */
    public List<Message> loadContactMessages(final String userEmail, final long myId, final long contactId)
            throws IOException {
        final List<Message> allMessages = loadLocalMessages(userEmail);
        final List<Message> contactMessages = allMessages.stream()
                .filter(msg -> msg.getGroupId() == null
                        && ((msg.getSenderUserId() == myId && msg.getReceiverUserId() != null
                                && msg.getReceiverUserId() == contactId)
                                || (msg.getSenderUserId() == contactId && msg.getReceiverUserId() != null
                                        && msg.getReceiverUserId() == myId)))
                .distinct()
                .collect(Collectors.toList());
        return contactMessages;
    }

    /**
     * Retourne la conversation de groupe en filtrant les messages dont le groupId
     * correspond
     * au groupe passé.
     */
    public List<Message> loadGroupMessages(final String userEmail, final long groupId) throws IOException {
        final List<Message> allMessages = loadLocalMessages(userEmail);
        final List<Message> groupMessages = new ArrayList<>();
        for (final Message msg : allMessages) {
            if (msg.getGroupId() != null && msg.getGroupId() == groupId) {
                groupMessages.add(msg);
            }
        }
        return groupMessages;
    }

    /**
     * Supprime un message de l'historique local pour l'utilisateur.
     */
    public void removeConversation(final String userEmail, final long myId, final long contactId) throws IOException {
        final List<Message> messages = loadLocalMessages(userEmail);
        messages.removeIf(m -> (m.getSenderUserId() == myId && m.getReceiverUserId() != null
                && m.getReceiverUserId() == contactId)
                || (m.getSenderUserId() == contactId && m.getReceiverUserId() != null
                        && m.getReceiverUserId() == myId));
        saveLocalMessages(userEmail, messages);
    }
}