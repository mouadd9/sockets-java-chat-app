package org.example.client.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.example.model.Message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Classe de persistance locale pour l'historique des messages d'un utilisateur.
 * L'historique est stocké sous forme de fichier JSON dans le dossier "src/main/client_data".
 */
public class JsonLocalMessageRepository {
    // Utilisation du répertoire de projet pour stocker les données clients
    private static final String LOCAL_FOLDER = System.getProperty("user.dir")
            + File.separator + "src" + File.separator + "main" + File.separator + "client_data";
    private final ObjectMapper objectMapper;

    public JsonLocalMessageRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        ensureLocalFolderExists();
    }

    private void ensureLocalFolderExists() {
        final Path folderPath = Paths.get(LOCAL_FOLDER);
        if (!Files.exists(folderPath)) {
            try {
                Files.createDirectories(folderPath);
            } catch (final IOException e) {
                System.err.println("Erreur lors de la création du dossier client_data : " + e.getMessage());
            }
        }
    }

    /**
     * Renvoie le chemin complet du fichier de l'utilisateur.
     */
    private String getUserFilePath(final String userEmail) {
        // Remplacer les caractères spéciaux pour créer un nom de fichier valide
        final String fileName = userEmail.replaceAll("[^a-zA-Z0-9]", "_") + "_messages.json";
        return LOCAL_FOLDER + File.separator + fileName;
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
     * Supprime un message de l'historique local pour l'utilisateur.
     */
    public void removeConversation(final String userEmail, final String contactEmail) throws IOException {
        final List<Message> messages = loadLocalMessages(userEmail);
        messages.removeIf(m ->
                (m.getSenderEmail().equals(userEmail) && m.getReceiverEmail().equals(contactEmail))
             || (m.getSenderEmail().equals(contactEmail) && m.getReceiverEmail().equals(userEmail)));
        saveLocalMessages(userEmail, messages);
    }
}