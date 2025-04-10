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
 * Cette classe permet de stocker et récupérer les messages dans des fichiers JSON locaux.
 * Chaque utilisateur possède son propre fichier de messages.
 * 
 * Caractéristiques principales :
 * - Stockage des messages dans le dossier "src/main/client_data"
 * - Utilisation de Jackson pour la sérialisation/désérialisation JSON
 * - Gestion automatique de la création du dossier de stockage
 * - Support des dates/heures avec JavaTimeModule
 */
public class JsonLocalMessageRepository {
    // Chemin du dossier où sont stockés les fichiers de messages
    private static final String LOCAL_FOLDER = System.getProperty("user.dir")
            + File.separator + "src" + File.separator + "main" + File.separator + "client_data";
    private final ObjectMapper objectMapper;

    /**
     * Constructeur qui initialise l'ObjectMapper et crée le dossier de stockage si nécessaire.
     * Configure l'ObjectMapper pour :
     * - Gérer les dates/heures avec JavaTimeModule
     * - Formater le JSON de manière lisible (indentation)
     */
    public JsonLocalMessageRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        ensureLocalFolderExists();
    }

    /**
     * Vérifie l'existence du dossier de stockage et le crée si nécessaire.
     * @throws IOException si la création du dossier échoue
     */
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
     * Génère le chemin du fichier JSON pour un utilisateur donné.
     * @param userEmail l'email de l'utilisateur
     * @return le chemin complet du fichier de messages de l'utilisateur
     */
    private String getUserFilePath(final String userEmail) {
        // Remplacer les caractères spéciaux pour créer un nom de fichier valide
        final String fileName = userEmail.replaceAll("[^a-zA-Z0-9]", "_") + "_messages.json";
        return LOCAL_FOLDER + File.separator + fileName;
    }

    /**
     * Charge les messages locaux d'un utilisateur depuis son fichier JSON.
     * @param userEmail l'email de l'utilisateur
     * @return la liste des messages de l'utilisateur, ou une liste vide si le fichier n'existe pas
     * @throws IOException si une erreur survient lors de la lecture du fichier
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
     * Sauvegarde la liste des messages d'un utilisateur dans son fichier JSON.
     * @param userEmail l'email de l'utilisateur
     * @param messages la liste des messages à sauvegarder
     * @throws IOException si une erreur survient lors de l'écriture du fichier
     */
    public void saveLocalMessages(final String userEmail, final List<Message> messages) throws IOException {
        final String filePath = getUserFilePath(userEmail);
        objectMapper.writeValue(new File(filePath), messages);
    }

    /**
     * Ajoute un nouveau message à l'historique local d'un utilisateur.
     * @param userEmail l'email de l'utilisateur
     * @param message le message à ajouter
     * @throws IOException si une erreur survient lors de la lecture/écriture du fichier
     */
    public void addLocalMessage(final String userEmail, final Message message) throws IOException {
        final List<Message> messages = loadLocalMessages(userEmail);
        messages.add(message);
        saveLocalMessages(userEmail, messages);
    }

    /**
     * Supprime toutes les conversations entre l'utilisateur et un contact donné.
     * @param userEmail l'email de l'utilisateur
     * @param contactEmail l'email du contact
     * @throws IOException si une erreur survient lors de la lecture/écriture du fichier
     */
    public void removeConversation(final String userEmail, final String contactEmail) throws IOException {
        final List<Message> messages = loadLocalMessages(userEmail);
        messages.removeIf(m ->
                (m.getSenderEmail().equals(userEmail) && m.getReceiverEmail().equals(contactEmail))
             || (m.getSenderEmail().equals(contactEmail) && m.getReceiverEmail().equals(userEmail)));
        saveLocalMessages(userEmail, messages);
    }
}