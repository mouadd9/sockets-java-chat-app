package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;

import org.example.server.broker.MessageBroker;
import org.example.shared.dao.UserDAO;
import org.example.shared.dto.Credentials;
import org.example.shared.dto.RegistrationDTO;
import org.example.shared.model.CallSignal;
import org.example.shared.model.Message;
import org.example.shared.model.User;
import org.example.shared.util.PasswordUtils;
import org.example.shared.util.ValidationUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final MessageBroker broker;
    private final UserDAO userDAO;
    private final ObjectMapper mapper;

    private String clientEmail;
    private long clientId;
    private PrintWriter output;
    private BufferedReader input;
    private volatile boolean isConnected;

    public ClientHandler(final Socket socket) {
        this.clientSocket = socket;
        this.broker = MessageBroker.getInstance();
        this.userDAO = new UserDAO();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            this.input = in;
            this.output = out;

            // Lire la première ligne pour déterminer le type de requête
            final String requestType = input.readLine();
            
            if ("REGISTER".equals(requestType)) {
                handleRegistration();
            } else {
                // Traiter comme une demande d'authentification normale
                if (!authenticateUser()) {
                    sendResponse("AUTH_FAILED");
                    return;
                }
                sendResponse("AUTH_SUCCESS");
                
                handleUserLogin(clientEmail);
                initializeSubscription();
                processMessages();
            }
        } catch (final IOException e) {
            System.out.println("Client connection error: " + e.getMessage());
        } finally {
            if (clientEmail != null) {
                handleUserLogout(clientEmail);
            }
            cleanup();
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (final IOException e) {
                System.err.println("Erreur lors de la fermeture du socket: " + e.getMessage());
            }
        }
    }

    private void handleRegistration() throws IOException {
        try {
            // Lire les données d'inscription
            final RegistrationDTO registrationDTO = mapper.readValue(input.readLine(), RegistrationDTO.class);
            
            // Valider l'email
            if (!ValidationUtils.isValidEmail(registrationDTO.getEmail())) {
                sendResponse("Format d'email invalide");
                return;
            }
            
            // Valider le mot de passe
            if (!ValidationUtils.isStrongPassword(registrationDTO.getPassword())) {
                sendResponse("Le mot de passe ne respecte pas les critères de sécurité");
                return;
            }
            
            // Vérifier que les mots de passe correspondent
            if (!ValidationUtils.doPasswordsMatch(registrationDTO.getPassword(), registrationDTO.getPasswordConfirmation())) {
                sendResponse("Les mots de passe ne correspondent pas");
                return;
            }
            
            // Vérifier si l'email existe déjà
            final User existingUser = userDAO.findUserByEmail(registrationDTO.getEmail());
            if (existingUser != null) {
                sendResponse("Cet email est déjà utilisé");
                return;
            }
            
            // Créer le nouvel utilisateur
            final User newUser = new User();
            newUser.setEmail(registrationDTO.getEmail());
            newUser.setDisplayName(registrationDTO.getEmail().split("@")[0]); // Utiliser la partie locale de l'email comme nom d'affichage par défaut
            newUser.setPasswordHash(PasswordUtils.hashPassword(registrationDTO.getPassword()));
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setOnline(false);
            
            // Persister l'utilisateur
            userDAO.createUser(newUser);
            
            // Envoyer une réponse de succès
            sendResponse("REGISTER_SUCCESS");
            
        } catch (final Exception e) {
            System.out.println("Registration error: " + e.getMessage());
            e.printStackTrace();
            sendResponse("Erreur lors de l'inscription: " + e.getMessage());
        }
    }

    private boolean authenticateUser() throws IOException {
        final Credentials credentials = mapper.readValue(input.readLine(), Credentials.class);
        final User user = userDAO.findUserByEmail(credentials.getEmail());
        if (user != null && PasswordUtils.verifyPassword(credentials.getPassword(), user.getPasswordHash())) {
            this.clientEmail = user.getEmail();
            this.clientId = user.getId();
            return true;
        }
        return false;
    }

    private void initializeSubscription() throws IOException {
        broker.registerListener(clientId, this);
        isConnected = true;
    }

    private void processMessages() throws IOException {
        String jsonData;
        while (isConnected && (jsonData = input.readLine()) != null) {
            try {
                // Vérifier si c'est un signal d'appel ou un message normal
                if (jsonData.contains("\"type\":\"CALL_")) {
                    // C'est un signal d'appel
                    final CallSignal signal = mapper.readValue(jsonData, CallSignal.class);
                    broker.routeCallSignal(signal);
                } else {
                    // C'est un message normal
                    final Message message = mapper.readValue(jsonData, Message.class);
                    if ("LOGOUT".equalsIgnoreCase(message.getContent())) {
                        handleUserLogout(clientEmail);
                        terminateSession();
                        break; // Sortir de la boucle après déconnexion
                    } else {
                        broker.sendMessage(message);
                    }
                }
            } catch (final IOException e) {
                System.out.println("Invalid message format: " + jsonData);
            }
        }
    }

    public void onMessageReceived(final Message message) throws IOException {
        output.println(mapper.writeValueAsString(message));
    }
    
    /**
     * Appelé lorsqu'un signal d'appel est reçu pour ce client.
     * 
     * @param signal Le signal d'appel à transmettre au client
     * @throws IOException En cas d'erreur de communication
     */
    public void onCallSignalReceived(final CallSignal signal) throws IOException {
        output.println(mapper.writeValueAsString(signal));
    }

    private void sendResponse(final String response) {
        output.println(response);
    }

    private void terminateSession() {
        isConnected = false;
    }

    private void cleanup() {
        if (clientEmail != null) {
            broker.unregisterListener(clientId);
        }
    }

    // Lors de la connexion d'un utilisateur
    public void handleUserLogin(final String email) {
        try {
            final User user = userDAO.findUserByEmail(email);
            if (user != null) {
                user.setOnline(true);
                final boolean success = userDAO.updateUser(user);
                if (!success) {
                    System.err.println("Échec de la mise à jour du statut en ligne pour: " + email);
                }
            }
        } catch (final Exception e) {
            System.err.println("Erreur lors de la mise à jour du statut en ligne: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Lors de la déconnexion d'un utilisateur
    public void handleUserLogout(final String email) {
        try {
            final User user = userDAO.findUserByEmail(email);
            if (user != null) {
                user.setOnline(false);
                final boolean success = userDAO.updateUser(user);
                if (!success) {
                    System.err.println("Échec de la mise à jour du statut hors ligne pour: " + email);
                }
            }
        } catch (final Exception e) {
            System.err.println("Erreur lors de la mise à jour du statut hors ligne: " + e.getMessage());
            e.printStackTrace();
        }
    }
}