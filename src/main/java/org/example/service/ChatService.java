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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.example.dto.Credentials;
import org.example.model.Message;
import org.example.model.MessageType;
import org.example.model.User;
import org.example.repository.JsonMessageRepository;
import org.example.client.repository.JsonLocalMessageRepository;

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
    private final PresenceManager presenceManager;

    private Thread listenerThread;
    private boolean isRunning = false;

    // Ajouter ces champs pour la gestion plus intelligente des statuts
    private final Map<String, Boolean> contactStatusCache = new ConcurrentHashMap<>();
    private long lastStatusRefreshTime = 0;

    public ChatService() {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.messageRepository = new JsonMessageRepository();
        this.userService = new UserService();
        this.presenceManager = new PresenceManager(this, userService);
    }

    public String getUserEmail() {
        return userEmail;
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
                presenceManager.start(); // Démarrer le gestionnaire de présence
                System.out.println("Authentification réussie pour " + userEmail);
            } else {
                System.out.println("Échec de l'authentification");
                disconnect();
            }

            return success;
        } catch (final ConnectException e) {
            throw new IOException("Impossible de se connecter au serveur. Vérifiez qu'il est en cours d'exécution.");
        }
    }

    public void disconnect() throws IOException {
        // Marquer l'utilisateur comme hors ligne via le PresenceManager
        if (presenceManager != null) {
            presenceManager.stop();
        }
        
        // Envoyer un message LOGOUT explicite
        try {
            Message logoutMessage = new Message();
            logoutMessage.setType(MessageType.LOGOUT);
            logoutMessage.setSenderEmail(userEmail);
            sendMessage(logoutMessage);
            System.out.println("Message de déconnexion envoyé pour " + userEmail);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoi du message de déconnexion: " + e.getMessage());
        }
        
        // Fermer les connexions
        if (out != null) {
            out.close();
        }
        if (in != null) {
            in.close();
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        isRunning = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }

        // Réinitialiser l'état
        userEmail = null;
        messageConsumer = null;

        System.out.println("Déconnexion complète");
    }

    public boolean sendMessage(final Message message) throws IOException {
        if (socket == null || socket.isClosed() || out == null) {
            throw new IOException("Non connecté au serveur");
        }
        // Ne modifier le type que si c'est un nouveau message sans type spécifié
        if (message.getType() == null) {
            message.setType(MessageType.CHAT);
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
        try {
            List<Message> messages = messageRepository.getConversation(user1Email, user2Email);
            
            // Mettre à jour le statut des messages reçus comme DELIVERED
            for (Message message : messages) {
                if (message.getReceiverEmail().equals(userEmail) && 
                    !"DELIVERED".equals(message.getStatus())) {
                    message.setStatus("DELIVERED");
                    messageRepository.updateMessage(message);
                }
            }
            
            // Ajouter les messages locaux
            JsonLocalMessageRepository localRepo = new JsonLocalMessageRepository();
            List<Message> localMessages = localRepo.loadLocalMessages(userEmail);
            
            // Filtrer les messages locaux pour n'inclure que ceux de cette conversation
            List<Message> filteredLocalMessages = localMessages.stream()
                .filter(msg -> (msg.getSenderEmail().equals(user1Email) && msg.getReceiverEmail().equals(user2Email)) ||
                            (msg.getSenderEmail().equals(user2Email) && msg.getReceiverEmail().equals(user1Email)))
                .toList();
            
            // Combiner les deux ensembles de messages en évitant les doublons
            List<Message> combined = new ArrayList<>(messages);
            for (Message localMsg : filteredLocalMessages) {
                if (messages.stream().noneMatch(m -> m.getId() != null && m.getId().equals(localMsg.getId()))) {
                    combined.add(localMsg);
                }
            }
            
            // Trier par timestamp
            combined.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));
            
            return combined;
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération de la conversation: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void setMessageConsumer(Consumer<Message> consumer) {
        this.messageConsumer = consumer;
        
        // Après avoir configuré le consumer, initialiser les statuts
        refreshOnlineStatuses();
        
        // Programmation d'une tâche pour essayer d'envoyer les messages en attente
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::retryPendingMessages, 10, 30, TimeUnit.SECONDS);
    }

    /**
     * Tente de réenvoyer les messages en attente
     */
    private void retryPendingMessages() {
        if (!isRunning || userEmail == null) {
            return;
        }
        
        try {
            System.out.println("Tentative d'envoi des messages en attente...");
            List<Message> pendingMessages = messageRepository.getUserMessages(userEmail).stream()
                .filter(m -> "PENDING".equals(m.getStatus()) || "QUEUED".equals(m.getStatus()))
                .toList();
            
            if (!pendingMessages.isEmpty()) {
                System.out.println("Envoi de " + pendingMessages.size() + " messages en attente");
                for (Message message : pendingMessages) {
                    try {
                        message.setStatus("DELIVERED");
                        sendMessage(message);
                    } catch (Exception e) {
                        System.err.println("Échec d'envoi du message en attente " + message.getId() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la tentative d'envoi des messages en attente: " + e.getMessage());
        }
    }

    /**
     * Récupère et rafraîchit les statuts en ligne de tous les utilisateurs
     */
    public void refreshOnlineStatuses() {
        try {
            // Vérifier si le rafraîchissement est nécessaire (pas plus d'une fois toutes les 15 secondes)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastStatusRefreshTime < 15000) {
                return; // Éviter les rafraîchissements trop fréquents
            }
            lastStatusRefreshTime = currentTime;
            
            // Vérifier si notre socket est toujours connecté
            boolean clientConnected = socket != null && !socket.isClosed() && socket.isConnected();
            
            // Si nous ne sommes pas connectés, notre utilisateur doit être hors ligne
            if (!clientConnected && userEmail != null) {
                Optional<User> currentUser = userService.getUserByEmail(userEmail);
                if (currentUser.isPresent() && currentUser.get().isOnline()) {
                    userService.setUserOnlineStatus(userEmail, false);
                    System.out.println("Client local déconnecté, statut mis à jour en conséquence");
                }
                return; // Ne pas continuer si nous sommes déconnectés
            }
            
            // Récupérer les statuts à jour et les envoyer au client, seulement pour nos contacts
            if (userEmail != null && messageConsumer != null) {
                List<String> contacts = getContacts(userEmail);
                for (String contactEmail : contacts) {
                    Optional<User> contactUser = userService.getUserByEmail(contactEmail);
                    if (contactUser.isPresent()) {
                        User user = contactUser.get();
                        boolean isOnline = user.isOnline() && userService.isUserReallyConnected(contactEmail);
                        
                        // Vérifier si le statut a changé depuis la dernière notification
                        Boolean lastKnownStatus = contactStatusCache.get(contactEmail);
                        if (lastKnownStatus == null || lastKnownStatus != isOnline) {
                            contactStatusCache.put(contactEmail, isOnline);
                            System.out.println("Envoi du statut pour " + contactEmail + ": " + (isOnline ? "en ligne" : "hors ligne"));
                            
                            // Envoyer une notification de statut
                            Message statusMsg = new Message();
                            statusMsg.setType(MessageType.STATUS_UPDATE);
                            statusMsg.setSenderEmail(contactEmail);
                            statusMsg.setContent(String.valueOf(isOnline));
                            messageConsumer.accept(statusMsg);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du rafraîchissement des statuts: " + e.getMessage());
        }
    }

    private void startMessageListener() {
        isRunning = true;

        listenerThread = new Thread(() -> {
            try {
                String jsonMessage;
                while (isRunning && (jsonMessage = in.readLine()) != null) {
                    try {
                        final Message message = objectMapper.readValue(jsonMessage, Message.class);

                        if (message.getType() == MessageType.LOGOUT_CONFIRM) {
                            break;
                        }

                        // Transmettre tous les types de messages au consommateur
                        if (messageConsumer != null) {
                            // N'acquitter que les messages de type CHAT
                            if (message.getType() == MessageType.CHAT) {
                                acknowledgeMessage(message.getId());
                            }
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

    public User getUser(String email) throws IOException {
        Optional<User> optionalUser = userService.getUserByEmail(email);
        if (optionalUser.isPresent()) {
            return optionalUser.get();
        }
        throw new IOException("Utilisateur non trouvé: " + email);
    }

    public void updateUserStatus(String email, String status) throws IOException {
        Optional<User> optionalUser = userService.getUserByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setStatus(status);
            userService.updateUser(user);
        } else {
            throw new IOException("Utilisateur non trouvé: " + email);
        }
    }
}
