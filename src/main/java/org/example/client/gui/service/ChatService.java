package org.example.client.gui.service;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.util.List;
import java.util.function.Consumer;

import org.example.shared.dao.GroupDAO;
import org.example.shared.dao.MessageDAO;
import org.example.shared.dao.UserDAO;
import org.example.shared.dto.Credentials;
import org.example.shared.model.Message;
import org.example.shared.model.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.shared.model.enums.MessageType;

public class ChatService {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String userEmail;
    private final ObjectMapper objectMapper;
    private Consumer<Message> messageConsumer;
    private Thread listenerThread;
    private boolean isRunning = false;

    // Instances DAO pour la persistance locale
    private final MessageDAO messageDAO;
    private final GroupDAO groupDAO;
    private final UserDAO userDAO; // Accès direct au DAO sans UserService

    // New file service for handling multimedia
    private final FileService fileService;

    public ChatService() {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.messageDAO = new MessageDAO();
        this.groupDAO = new GroupDAO();
        this.userDAO = new UserDAO();
        this.fileService = new FileService();
    }

    // resp : initie la connexion avec le serveur / l'authentification
    public boolean connect(final Credentials credentials) throws IOException {
        try {
            // resp 1 : etablissement de la connexion avec le serveur (creation de la socket coté serveur)
            System.out.println("Connexion au serveur " + SERVER_ADDRESS + ":" + SERVER_PORT);
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Envoyer les identifiants
            final String jsonCredentials = objectMapper.writeValueAsString(credentials);
            out.println(jsonCredentials);

            // Attendre la réponse du serveur
            final String response = in.readLine();
            final boolean success = "AUTH_SUCCESS".equals(response);

            if (success) {
                this.userEmail = credentials.getEmail();
                startMessageListener();
                System.out.println("Authentification réussie pour " + userEmail);
            } else {
                System.out.println("Échec de l'authentification");
                disconnect();
            }

            return success;
        } catch (final ConnectException e) {
            throw new IOException(
                    "Impossible de se connecter au serveur. Assurez-vous que le serveur est démarré et accessible sur "
                            + SERVER_ADDRESS + ":" + SERVER_PORT,
                    e);
        } catch (final Exception e) {
            throw new IOException("Erreur lors de la connexion au serveur: " + e.getMessage(), e);
        }
    }

    public void disconnect() throws IOException {
        // Envoi d'un message de déconnexion (exemple simplifié)
        final Message logoutMsg = new Message();
        logoutMsg.setSenderUserId(getCurrentUserId());
        out.println(objectMapper.writeValueAsString(logoutMsg));

        closeResources();

        userEmail = null;
        messageConsumer = null;
        isRunning = false;
        System.out.println("Déconnexion complète");
    }

    private void closeResources() {
        try {
            if (out != null) out.close();
        } catch (final Exception e) { /* Ignorer */ }
        try {
            if (in != null) in.close();
        } catch (final Exception e) { /* Ignorer */ }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (final IOException e) { /* Ignorer */ }
        out = null;
        in = null;
        socket = null;
    }

    public long getCurrentUserId() {
        try {
            // Utiliser directement UserDAO
            final User currentUser = userDAO.findUserByEmail(userEmail);
            return currentUser != null ? currentUser.getId() : -1;
        } catch (final Exception e) {
            return -1;
        }
    }

    public Message createDirectMessage(final String senderEmail, final String receiverEmail, final String content)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);
        final User receiver = userDAO.findUserByEmail(receiverEmail);
        return Message.newDirectMessage(sender.getId(), receiver.getId(), content);
    }

    public Message createGroupMessage(final String senderEmail, final long groupId, final String content) throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);
        return Message.newGroupMessage(sender.getId(), groupId, content);
    }

    // New methods for creating multimedia messages
    public Message createDirectMediaMessage(final String senderEmail, final String receiverEmail, final File mediaFile)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);
        final User receiver = userDAO.findUserByEmail(receiverEmail);

        // Detect file type and save the file
        final MessageType type = fileService.detectMessageType(mediaFile.getName());
        // relative path of the media
        final String filePath = fileService.saveFile(mediaFile, type, mediaFile.getName());
        final String mimeType = fileService.getMimeType(mediaFile);

        return Message.newDirectMediaMessage(
                sender.getId(),
                receiver.getId(),
                filePath,
                type,
                mediaFile.getName(),
                mediaFile.length(),
                mimeType);
    }

    public Message createGroupMediaMessage(final String senderEmail, final long groupId, final File mediaFile)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);

        // Detect file type and save the file
        final MessageType type = fileService.detectMessageType(mediaFile.getName());
        final String filePath = fileService.saveFile(mediaFile, type, mediaFile.getName());
        final String mimeType = fileService.getMimeType(mediaFile);

        return Message.newGroupMediaMessage(
                sender.getId(),
                groupId,
                filePath,
                type,
                mediaFile.getName(),
                mediaFile.length(),
                mimeType);
    }

    public Message createDirectAudioMessage(final String senderEmail, final String receiverEmail, final File audioFile)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);
        final User receiver = userDAO.findUserByEmail(receiverEmail);

        // Save the audio file
        final String filePath = fileService.saveFile(audioFile, MessageType.AUDIO, audioFile.getName());
        final String mimeType = fileService.getMimeType(audioFile);

        return Message.newDirectMediaMessage(
                sender.getId(),
                receiver.getId(),
                filePath,
                MessageType.AUDIO,
                audioFile.getName(),
                audioFile.length(),
                mimeType);
    }

    public Message createGroupAudioMessage(final String senderEmail, final long groupId, final File audioFile)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);

        // Save the audio file
        final String filePath = fileService.saveFile(audioFile, MessageType.AUDIO, audioFile.getName());
        final String mimeType = fileService.getMimeType(audioFile);

        return Message.newGroupMediaMessage(
                sender.getId(),
                groupId,
                filePath,
                MessageType.AUDIO,
                audioFile.getName(),
                audioFile.length(),
                mimeType);
    }
    // resp : envoi des messages au serveur via socket
    public boolean sendMessage(final Message message) throws IOException {
        if (socket == null || socket.isClosed() || out == null) {
            throw new IOException("Non connecté au serveur");
        }
        final String jsonMessage = objectMapper.writeValueAsString(message);
        out.println(jsonMessage);
        return true;
    }

    /**
     * Gets the file associated with a media message.
     *
     * @param message The message
     * @return The file
     */
    public File getMediaFile(final Message message) {
        if (!message.isMediaMessage()) {
            throw new IllegalArgumentException("Not a media message");
        }
        File file = fileService.getFile(message.getContent());
        System.out.println("Looking for media file at: " + file.getAbsolutePath());
        System.out.println("File exists: " + file.exists());
        return file;
    }

    // Récupère la conversation entre deux utilisateurs
    public List<Message> getConversation(final long user1Id, final long user2Id) throws IOException {
        return messageDAO.getConversation(user1Id, user2Id);
    }

    // resp : configuration du message consumer (handleIncomingMessage)
    public void setMessageConsumer(final Consumer<Message> consumer) {
        this.messageConsumer = consumer;
    }
    // resp : initie une boucle qui reagit a tous message recue
    private void startMessageListener() {
        isRunning = true;
        listenerThread = new Thread(() -> {
            try {
                String jsonMessage;
                while (isRunning && (jsonMessage = in.readLine()) != null) {
                    try {
                        final Message message = objectMapper.readValue(jsonMessage, Message.class);
                        if (messageConsumer != null) {
                            messageConsumer.accept(message);
                        }
                    } catch (final Exception e) {
                        System.err.println("Erreur lors du traitement du message: " + e.getMessage());
                    }
                }
            } catch (final IOException e) {
                if (isRunning) {
                    System.err.println("Connexion perdue: " + e.getMessage());
                }
            } finally {
                isRunning = false;
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
}
