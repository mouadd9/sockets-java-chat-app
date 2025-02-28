package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.example.broker.MessageBroker;
import org.example.dto.Credentials;
import org.example.model.Message;
import org.example.service.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private String clientEmail;
    private PrintWriter out;
    private BufferedReader in;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final MessageBroker messageBroker;
    private boolean isRunning = true;

    // Map to store all online clients (shared between all handlers)
    private static Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    public ClientHandler(final Socket socket) {
        this.clientSocket = socket;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.userService = new UserService();
        this.messageBroker = MessageBroker.getInstance();

        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Step 1: Handle Authentication
            final String jsonCredentials = in.readLine();
            final Credentials credentials = objectMapper.readValue(jsonCredentials, Credentials.class);

            if (userService.authenticateUser(credentials.getEmail(), credentials.getPassword())) {
                // Authentication successful
                setClientEmail(credentials.getEmail());
                out.println("AUTH_SUCCESS");

                // Enregistrer ce client comme consommateur de messages
                registerAsConsumer();

                // Start handling messages
                handleMessages();
            } else {
                out.println("AUTH_FAILED");
                clientSocket.close();
            }
        } catch (final IOException e) {
            System.out.println("Client disconnected during authentication: " + e.getMessage());
        } finally {
            handleDisconnection();
        }
    }

    private void registerAsConsumer() {
        messageBroker.registerConsumer(clientEmail, message -> {
            try {
                final String jsonMessage = objectMapper.writeValueAsString(message);
                sendMessage(jsonMessage);
                System.out.println("Message envoyé à " + clientEmail + ": " + message.getId());
            } catch (final IOException e) {
                System.err.println("Erreur lors de l'envoi d'un message à " + clientEmail + ": " + e.getMessage());
                throw new RuntimeException("Échec d'envoi du message", e);
            }
        });
    }

    private void handleMessages() {
        try {
            String messageData;
            while (isRunning && (messageData = in.readLine()) != null) {
                try {
                    final Message message = objectMapper.readValue(messageData, Message.class);

                    switch (message.getType()) {
                        case "CHAT":
                            handleChatMessage(message);
                            break;
                        case "ACKNOWLEDGE":
                            handleAcknowledge(message);
                            break;
                        case "LOGOUT":
                            handleLogout();
                            return;
                        default:
                            System.out.println("Unknown message type: " + message.getType());
                    }
                } catch (final IOException e) {
                    System.out.println("Error processing message: " + e.getMessage());
                    sendMessage("{\"type\":\"ERROR\",\"content\":\"Invalid message format\"}");
                }
            }
        } catch (final IOException e) {
            if (isRunning) {
                System.out.println("Client disconnected: " + clientEmail);
            }
        }
    }

    private void handleChatMessage(final Message message) throws IOException {
        // Check if receiver is online
        final ClientHandler receiverHandler = onlineClients.get(message.getReceiverEmail());

        // Utiliser le broker pour envoyer le message
        final boolean delivered = messageBroker.sendMessage(message,
                receiverHandler != null ? msg -> {
                    try {
                        final String jsonMessage = objectMapper.writeValueAsString(msg);
                        receiverHandler.sendMessage(jsonMessage);
                    } catch (final IOException e) {
                        System.err.println("Erreur lors de l'envoi direct du message: " + e.getMessage());
                        throw new RuntimeException("Échec d'envoi du message", e);
                    }
                } : null);

        // Envoyer une confirmation à l'expéditeur
        final Message confirmation = new Message(null, message.getSenderEmail(), null);
        confirmation.setType("CONFIRMATION");
        confirmation.setStatus(delivered ? "DELIVERED" : "QUEUED");
        confirmation.setId(message.getId());
        sendMessage(objectMapper.writeValueAsString(confirmation));
    }

    private void handleAcknowledge(final Message message) {
        // Le client confirme la réception d'un message
        if (message.getId() != null) {
            messageBroker.acknowledgeMessage(message.getId());
        }
    }

    private void handleLogout() {
        try {
            // Envoyer la confirmation de déconnexion
            sendMessage("{\"type\":\"LOGOUT_CONFIRM\"}");
        } catch (final Exception e) {
            System.out.println("Erreur lors de l'envoi du LOGOUT_CONFIRM: " + e.getMessage());
        } finally {
            // Assurer la déconnexion proprement
            handleDisconnection();
        }
    }

    private void handleDisconnection() {
        try {
            if (clientEmail != null) {
                // Désenregistrer ce client comme consommateur de messages et mettre à jour son
                // statut
                messageBroker.unregisterConsumer(clientEmail);
                userService.setUserOnlineStatus(clientEmail, false);
                onlineClients.remove(clientEmail);
                System.out.println("Client déconnecté et retiré: " + clientEmail);
            }
            isRunning = false;
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (final IOException e) {
            System.out.println("Erreur lors de la déconnexion: " + e.getMessage());
        }
    }

    public void sendMessage(final String message) {
        out.println(message);
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public void setClientEmail(final String email) throws IOException {
        this.clientEmail = email;
        onlineClients.put(email, this);
        userService.setUserOnlineStatus(email, true);
        System.out.println("Client registered: " + email);
    }
}

/*
 * ClientHandler Flow Schema:
 * 
 * 1. Client Connection:
 * Client Socket ----connects----> Server Socket
 * │
 * ▼
 * Create ClientHandler
 * │
 * ▼
 * Start Thread
 * 
 * 2. Authentication Flow:
 * Client ----sends credentials----> ClientHandler
 * │
 * ▼
 * Validate User
 * │
 * ┌─────────┴──────────┐
 * ▼ ▼
 * AUTH_SUCCESS AUTH_FAILED
 * │ │
 * ▼ ▼
 * Add to onlineClients Close Connection
 * │
 * ▼
 * Send Offline Messages
 * 
 * 3. Message Handling Flow:
 * a) Sending Message:
 * Client A ----sends message----> ClientHandler A
 * │
 * Save to DB
 * │
 * Check Receiver
 * │
 * ┌─────────────┴────────────┐
 * ▼ ▼
 * Receiver Online Receiver Offline
 * │ │
 * Forward Message Queue Message
 * │ │
 * Send "delivered" Send "queued"
 * to sender to sender
 * 
 * b) Receiving Message:
 * ClientHandler A ----forwards----> ClientHandler B
 * │
 * ▼
 * Send to Client B
 * 
 * 4. Disconnection Flow:
 * Client ----closes/crashes----> ClientHandler
 * │
 * ▼
 * Set User Offline
 * │
 * ▼
 * Remove from onlineClients
 * │
 * ▼
 * Close Socket
 * 
 * Static Data Structure:
 * onlineClients = {
 * "user1@email.com": ClientHandler1,
 * "user2@email.com": ClientHandler2,
 * ...
 * }
 * 
 * Message Types:
 * 1. CHAT: Regular chat message
 * 2. LOGOUT: Client logout request
 * 3. CONFIRMATION: Message delivery status
 * 4. ERROR: Error notifications
 */