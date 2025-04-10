package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

import org.example.broker.MessageBroker;
import org.example.dto.Credentials;
import org.example.model.Message;
import org.example.model.MessageType;
import org.example.model.User;
import org.example.service.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ClientHandler implements Runnable {
    private static final String AUTH_SUCCESS = "AUTH_SUCCESS";
    private static final String AUTH_FAILED = "AUTH_FAILED";

    private final Socket clientSocket;
    private final MessageBroker broker;
    private final UserService userService;
    private final ObjectMapper mapper;

    private String clientEmail;
    private PrintWriter output;
    private BufferedReader input;
    private volatile boolean isConnected;

    public ClientHandler(final Socket socket) {
        this.clientSocket = socket;
        this.broker = MessageBroker.getInstance();
        this.userService = new UserService();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public void run() {
        try {
            // Configurer la communication
            output = new PrintWriter(clientSocket.getOutputStream(), true);
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Authentifier l'utilisateur
            if (authenticateUser()) {
                isConnected = true;
                
                // Mettre à jour le statut de l'utilisateur en ligne
                updateUserStatus(true);
                
                // Enregistrer ce client pour les notifications
                broker.registerListener(clientEmail, this);
                
                // Envoyer les statuts de tous les contacts actuellement connectés
                sendAllContactStatuses();
                
                // Commencer à traiter les messages
                processMessages();
            }
        } catch (IOException e) {
            System.err.println("Error in client communication: " + e.getMessage());
        } finally {
            // Nettoyage en cas de déconnexion
            cleanup();
            closeResources();
        }
    }

    private boolean authenticateUser() throws IOException {
        final Credentials credentials = mapper.readValue(input.readLine(), Credentials.class);
        if (userService.authenticate(credentials.getEmail(), credentials.getPassword())) {
            this.clientEmail = credentials.getEmail();
            sendResponse(AUTH_SUCCESS);
            System.out.println("Authentification réussie pour " + clientEmail);
            return true;
        }
        sendResponse(AUTH_FAILED);
        System.out.println("Échec d'authentification pour " + credentials.getEmail());
        return false;
    }

    private void initializeSubscription() throws IOException {
        userService.setUserOnlineStatus(clientEmail, true);
        // broker.unregisterListener(clientEmail);
        broker.registerListener(clientEmail, this);
        isConnected = true;
    }

    private void processMessages() throws IOException {
        String messageJson;
        long lastActivityTime = System.currentTimeMillis();
        long timeoutInterval = 5000; // 5 secondes d'inactivité = déconnexion
        
        // Démarrer un thread de surveillance pour la détection de déconnexion
        Thread watchdog = startWatchdogThread(timeoutInterval);
        
        try {
            while (isConnected && (messageJson = input.readLine()) != null) {
                lastActivityTime = System.currentTimeMillis();
                try {
                    final Message message = mapper.readValue(messageJson, Message.class);
                    
                    if (message.getType() == MessageType.CHAT || message.getType() == MessageType.FILE) {
                        broker.sendMessage(message);
                    } else if (message.getType() == MessageType.LOGOUT) {
                        terminateSession();
                        break;
                    } else if (message.getType() == MessageType.PING) {
                        // Mise à jour du statut actif sur réception de ping
                        updateUserStatus(true);
                    } else if (message.getType() == MessageType.TYPING || 
                             message.getType() == MessageType.STOP_TYPING ||
                             message.getType() == MessageType.STATUS_UPDATE) {
                        // Transmettre directement les événements spéciaux sans les persister
                        broker.sendMessage(message);
                    }
                } catch (final IOException e) {
                    System.out.println("Invalid message format: " + messageJson);
                }
                
                // Vérifier si le socket est toujours connecté
                if (!clientSocket.isConnected() || clientSocket.isClosed()) {
                    System.out.println("Socket déconnecté pour " + clientEmail);
                    isConnected = false;
                    break;
                }
            }
        } finally {
            // Arrêter le thread de surveillance
            watchdog.interrupt();
            
            // Si on sort de la boucle sans terminateSession explicite, c'est une déconnexion inattendue
            if (isConnected) {
                System.out.println("Déconnexion inattendue détectée pour " + clientEmail);
                terminateSession();
            }
        }
    }

    private Thread startWatchdogThread(long timeoutInterval) {
        Thread watchdog = new Thread(() -> {
            long lastCheck = System.currentTimeMillis();
            while (isConnected && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000); // Vérifier toutes les secondes
                    
                    long now = System.currentTimeMillis();
                    
                    // Si le socket est fermé ou si la connexion est inactive depuis trop longtemps
                    if (!clientSocket.isConnected() || clientSocket.isClosed() ||
                        now - lastCheck > timeoutInterval) {
                        System.out.println("Watchdog: déconnexion détectée pour " + clientEmail);
                        isConnected = false;
                        
                        // S'assurer que l'utilisateur est marqué hors ligne
                        updateUserStatus(false);
                        break;
                    }
                    
                    lastCheck = now;
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        
        watchdog.setDaemon(true);
        watchdog.start();
        return watchdog;
    }

    public void onMessageReceived(final Message message) throws IOException {
        output.println(mapper.writeValueAsString(message));
    }

    private void sendResponse(final String response) {
        output.println(response);
    }

    private void terminateSession() {
        isConnected = false;
        
        try {
            // Envoyer confirmation de déconnexion
            Message confirmMsg = new Message();
            confirmMsg.setType(MessageType.LOGOUT_CONFIRM);
            onMessageReceived(confirmMsg);
        } catch (IOException e) {
            System.err.println("Error sending logout confirmation: " + e.getMessage());
        }
        
        // Mettre à jour le statut de l'utilisateur hors ligne
        updateUserStatus(false);
    }

    private void cleanup() {
        if (clientEmail != null) {
            try {
                broker.unregisterListener(clientEmail);
                updateUserStatus(false);
                System.out.println("Client déconnecté: " + clientEmail);
            } catch (Exception e) {
                System.err.println("Error during cleanup: " + e.getMessage());
            }
        }
    }

    private void sendAllContactStatuses() {
        try {
            // Récupérer tous les utilisateurs en ligne
            List<User> onlineUsers = userService.getOnlineUsers();
            
            // Pour chaque utilisateur en ligne, envoyer son statut à ce client
            for (User user : onlineUsers) {
                if (!user.getEmail().equals(clientEmail)) {
                    Message statusMsg = new Message();
                    statusMsg.setType(MessageType.STATUS_UPDATE);
                    statusMsg.setSenderEmail(user.getEmail());
                    statusMsg.setContent("true");
                    onMessageReceived(statusMsg);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoi des statuts des contacts: " + e.getMessage());
        }
    }

    private void updateUserStatus(boolean online) {
        if (clientEmail != null) {
            try {
                // Vérifier d'abord si le statut a changé pour éviter les notifications inutiles
                Optional<User> user = userService.getUserByEmail(clientEmail);
                if (user.isPresent() && user.get().isOnline() != online) {
                    userService.setUserOnlineStatus(clientEmail, online);
                    System.out.println("Statut utilisateur " + clientEmail + " mis à jour: " + (online ? "en ligne" : "hors ligne"));
                    
                    // Notifier les autres utilisateurs du changement de statut
                    Message statusUpdate = new Message();
                    statusUpdate.setType(MessageType.STATUS_UPDATE);
                    statusUpdate.setSenderEmail(clientEmail);
                    statusUpdate.setContent(String.valueOf(online));
                    broker.sendMessage(statusUpdate);
                }
            } catch (IOException e) {
                System.err.println("Error updating user status: " + e.getMessage());
            }
        }
    }

    private void closeResources() {
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }
}