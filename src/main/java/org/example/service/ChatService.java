package org.example.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.example.dto.Credentials;
import org.example.model.Message;
import org.example.model.User;
import org.example.repository.JsonMessageRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ChatService {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String userEmail;
    private final ObjectMapper objectMapper;
    private Consumer<Message> messageConsumer;
    private final JsonMessageRepository messageRepository;
    private final UserService userService;

    private Thread listenerThread;
    private boolean isRunning = false;

    public ChatService() {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.messageRepository = new JsonMessageRepository();
        this.userService = new UserService();
    }

    public boolean connect(final Credentials credentials) throws IOException {
        try {
            // Se connecter au serveur
            System.out.println("Tentative de connexion au serveur " + SERVER_ADDRESS + ":" + SERVER_PORT);
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
        // Envoyer un message explicite de déconnexion
        final Message logoutMsg = new Message();
        logoutMsg.setType("LOGOUT");
        logoutMsg.setSenderEmail(userEmail);
        sendMessage(logoutMsg);

        // Fermer les connexions
        if (out != null)
            out.close();
        if (in != null)
            in.close();
        if (socket != null)
            socket.close();

        // Réinitialiser l'état
        userEmail = null;
        messageConsumer = null;

        System.out.println("Déconnexion complète");
    }

    public boolean sendMessage(final Message message) throws IOException {
        if (socket == null || socket.isClosed() || out == null) {
            throw new IOException("Non connecté au serveur");
        }
        // Ne modifier le type que si ce n'est pas un message spécial (comme LOGOUT)
        if (!"LOGOUT".equals(message.getType())) {
            message.setType("CHAT");
        }
        final String jsonMessage = objectMapper.writeValueAsString(message);
        out.println(jsonMessage);
        return true;
    }

    public void acknowledgeMessage(final String messageId) throws IOException {
        messageRepository.deleteMessage(messageId);
    }

    public List<String> getContacts(final String userEmail) throws IOException {
        final Optional<User> optionalUser = userService.getUserByEmail(userEmail);
        return optionalUser.map(User::getContacts).orElse(new ArrayList<>());
    }

    public boolean addContact(final String userEmail, final String contactEmail) throws IOException {
        return userService.addContact(userEmail, contactEmail);
    }

    public boolean removeContact(final String userEmail, final String contactEmail) throws IOException {
        return userService.removeContact(userEmail, contactEmail);
    }

    public List<Message> getConversation(final String user1Email, final String user2Email) throws IOException {
        // Utiliser la méthode du repository pour récupérer la conversation réelle
        return messageRepository.getConversation(user1Email, user2Email);
    }

    public void setMessageConsumer(final Consumer<Message> consumer) {
        this.messageConsumer = consumer;
    }

    private void startMessageListener() {
        isRunning = true;

        listenerThread = new Thread(() -> {
            try {
                String jsonMessage;
                while (isRunning && (jsonMessage = in.readLine()) != null) {
                    try {
                        final Message message = objectMapper.readValue(jsonMessage, Message.class);

                        if ("LOGOUT_CONFIRM".equals(message.getType())) {
                            break;
                        }

                        if ("CHAT".equals(message.getType())) {
                            // Acquitter automatiquement la réception du message
                            acknowledgeMessage(message.getId());

                            // Transmettre le message au consommateur
                            if (messageConsumer != null) {
                                messageConsumer.accept(message);
                            }
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
