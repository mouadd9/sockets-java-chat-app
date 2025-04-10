package org.example.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.util.List;
import java.util.function.Consumer;

import org.example.dao.ContactDAO;
import org.example.dao.MessageDAO;
import org.example.dao.UserDAO;
import org.example.dto.Credentials;
import org.example.model.Contact;
import org.example.model.Message;
import org.example.model.User;

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
    private Thread listenerThread;
    private boolean isRunning = false;

    // Instances DAO et persistance locale
    private final MessageDAO messageDAO;
    private final UserDAO userDAO;
    private final ContactDAO contactDAO;

    public ChatService() {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.messageDAO = new MessageDAO();
        this.userDAO = new UserDAO();
        this.contactDAO = new ContactDAO();
    }

    public boolean connect(final Credentials credentials) throws IOException {
        try {
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

        if (out != null)
            out.close();
        if (in != null)
            in.close();
        if (socket != null)
            socket.close();

        userEmail = null;
        messageConsumer = null;
        isRunning = false;
        System.out.println("Déconnexion complète");
    }

    public long getCurrentUserId() {
        try {
            final User currentUser = userDAO.findUserByEmail(userEmail);
            return currentUser != null ? currentUser.getId() : -1;
        } catch (final Exception e) {
            return -1;
        }
    }

    public long getUserId(final String email) throws IOException {
        final User user = userDAO.findUserByEmail(email);
        if(user == null) {
            throw new IOException("Utilisateur non trouvé");
        }
        return user.getId();
    }

    public String getUserEmail(final long userId) throws IOException {
        final User user = userDAO.findUserById(userId);
        if (user == null) {
            throw new IOException("Utilisateur non trouvé pour id " + userId);
        }
        return user.getEmail();
    }

    public Message createDirectMessage(final String senderEmail, final String receiverEmail, final String content)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);
        final User receiver = userDAO.findUserByEmail(receiverEmail);
        if (sender == null || receiver == null) {
            throw new IOException("Utilisateur non trouvé");
        }
        return Message.newDirectMessage(sender.getId(), receiver.getId(), content);
    }

    public boolean sendMessage(final Message message) throws IOException {
        if (socket == null || socket.isClosed() || out == null) {
            throw new IOException("Non connecté au serveur");
        }
        // Envoi du message via le socket
        final String jsonMessage = objectMapper.writeValueAsString(message);
        out.println(jsonMessage);

        // Persistance du message côté serveur et en local pour un historique hors ligne
        messageDAO.createMessage(message);
        return true;
    }

    // Récupère la conversation entre deux utilisateurs en convertissant leurs
    // emails en IDs
    public List<Message> getConversation(final String user1Email, final String user2Email) throws IOException {
        final User user1 = userDAO.findUserByEmail(user1Email);
        final User user2 = userDAO.findUserByEmail(user2Email);

        if (user1 == null || user2 == null) {
            throw new IOException("Utilisateur non trouvé");
        }
        return messageDAO.getConversation(user1.getId(), user2.getId());
    }

    public List<String> getContacts(final String userEmail) throws IOException {
        final User user = userDAO.findUserByEmail(userEmail);
        if (user == null) {
            throw new IOException("Utilisateur non trouvé");
        }
        return contactDAO.getContactsByUserId(user.getId());
    }

    public boolean addContact(final String userEmail, final String contactEmail) throws IOException {
        final User sender = userDAO.findUserByEmail(userEmail);
        final User contact = userDAO.findUserByEmail(contactEmail);
        if (sender == null || contact == null) {
            throw new IllegalArgumentException("Utilisateur non trouvé");
        }
        final Contact newContact = new Contact(sender.getId(), contact.getId());
        contactDAO.createContact(newContact);
        return true;
    }

    public boolean removeContact(final String userEmail, final String contactEmail) throws IOException {
        final User sender = userDAO.findUserByEmail(userEmail);
        final User contact = userDAO.findUserByEmail(contactEmail);
        if (sender == null || contact == null) {
            throw new IllegalArgumentException("Utilisateur non trouvé");
        }
        return contactDAO.deleteContact(sender.getId(), contact.getId());
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
