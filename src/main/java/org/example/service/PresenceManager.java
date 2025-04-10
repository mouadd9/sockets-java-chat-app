package org.example.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.example.model.Message;
import org.example.model.MessageType;
import org.example.model.User;

public class PresenceManager {
    private static final int PING_INTERVAL = 20; // 20 secondes entre les pings
    private static final int OFFLINE_THRESHOLD = 60; // 60 secondes pour être marqué hors ligne
    private final ChatService chatService;
    private final UserService userService;
    private final ScheduledExecutorService pingExecutor;
    private final Map<String, Long> lastActivityMap;
    private int failedPings = 0;
    private static final int MAX_FAILED_PINGS = 2;
    
    // Ajouter cette map pour éviter les mises à jour redondantes
    private final Map<String, Boolean> userStatusCache = new ConcurrentHashMap<>();

    public PresenceManager(ChatService chatService, UserService userService) {
        this.chatService = chatService;
        this.userService = userService;
        this.pingExecutor = Executors.newSingleThreadScheduledExecutor();
        this.lastActivityMap = new ConcurrentHashMap<>();
    }

    public void start() {
        try {
            // Mettre à jour et notifier immédiatement le statut en ligne
            String userEmail = chatService.getUserEmail();
            if (userEmail != null) {
                // Forcer le statut en ligne au démarrage
                User user = userService.getUserByEmail(userEmail).orElse(null);
                if (user != null) {
                    user.setOnline(true);
                    userService.updateUser(user);
                    userStatusCache.put(userEmail, true); // Mettre à jour le cache
                    notifyContactsOfStatusChange(userEmail, true);
                }
                updateLastActivity(userEmail);
            }
            
            // Démarrer l'envoi périodique des pings (toutes les 20 secondes)
            pingExecutor.scheduleAtFixedRate(this::sendPing, 0, PING_INTERVAL, TimeUnit.SECONDS);
            
            // Vérifier les inactifs toutes les 20 secondes
            pingExecutor.scheduleAtFixedRate(this::checkInactiveUsers, 0, 20, TimeUnit.SECONDS);
        } catch (IOException e) {
            System.err.println("Erreur lors du démarrage du PresenceManager: " + e.getMessage());
        }
    }

    public void stop() {
        try {
            // Marquer explicitement l'utilisateur comme hors ligne avant de s'arrêter
            String userEmail = chatService.getUserEmail();
            if (userEmail != null) {
                User user = userService.getUserByEmail(userEmail).orElse(null);
                if (user != null && user.isOnline()) {
                    user.setOnline(false);
                    userService.updateUser(user);
                    userStatusCache.put(userEmail, false); // Mettre à jour le cache
                    notifyContactsOfStatusChange(userEmail, false);
                    System.out.println("Utilisateur " + userEmail + " marqué hors ligne lors de la déconnexion");
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la déconnexion: " + e.getMessage());
        }
        
        // Arrêter l'exécuteur
        pingExecutor.shutdown();
        try {
            if (!pingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                pingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pingExecutor.shutdownNow();
        }
    }

    private void sendPing() {
        try {
            String userEmail = chatService.getUserEmail();
            Message pingMessage = new Message();
            pingMessage.setType(MessageType.PING);
            pingMessage.setSenderEmail(userEmail);
            chatService.sendMessage(pingMessage);
            failedPings = 0; // Réinitialiser le compteur en cas de succès
            
            // Forcer la mise à jour du statut en ligne à chaque ping réussi
            updateLastActivity(userEmail);
        } catch (IOException e) {
            failedPings++;
            if (failedPings >= MAX_FAILED_PINGS) {
                handleConnectionLost();
            }
        }
    }

    private void checkInactiveUsers() {
        long currentTime = System.currentTimeMillis();
        lastActivityMap.forEach((email, lastActivity) -> {
            if (currentTime - lastActivity > OFFLINE_THRESHOLD * 1000) {
                try {
                    // Vérifier si l'utilisateur est déjà marqué comme hors ligne dans le cache
                    Boolean cachedStatus = userStatusCache.get(email);
                    if (cachedStatus == null || cachedStatus) { // null ou true signifie qu'il n'est pas marqué hors ligne
                        User user = userService.getUserByEmail(email).orElse(null);
                        if (user != null && user.isOnline()) {
                            System.out.println("Utilisateur " + email + " marqué hors ligne après " + OFFLINE_THRESHOLD + " secondes d'inactivité");
                            
                            // Marquer comme hors ligne dans la base de données
                            user.setOnline(false);
                            userService.updateUser(user);
                            userStatusCache.put(email, false); // Mettre à jour le cache
                            
                            // Notifier tous les utilisateurs du changement de statut
                            notifyContactsOfStatusChange(email, false);
                            
                            // Supprimer l'entrée pour éviter les notifications répétées
                            lastActivityMap.remove(email);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Erreur lors de la mise à jour du statut: " + e.getMessage());
                }
            }
        });
    }

    private void handleConnectionLost() {
        try {
            String userEmail = chatService.getUserEmail();
            if (userEmail != null) {
                User user = userService.getUserByEmail(userEmail).orElse(null);
                if (user != null) {
                    user.setOnline(false);
                    userService.updateUser(user);
                    userStatusCache.put(userEmail, false); // Mettre à jour le cache
                    notifyContactsOfStatusChange(userEmail, false);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la gestion de la perte de connexion: " + e.getMessage());
        }
    }

    public void updateLastActivity(String userEmail) {
        if (userEmail == null) return;
        
        // Enregistrer le dernier timestamp d'activité
        Long lastTimestamp = lastActivityMap.get(userEmail);
        long currentTime = System.currentTimeMillis();
        lastActivityMap.put(userEmail, currentTime);
        
        // Ne pas envoyer de mise à jour trop fréquemment (au maximum une fois par minute)
        if (lastTimestamp != null && currentTime - lastTimestamp < 60000) {
            return;
        }
        
        try {
            // Vérifier si l'utilisateur est déjà marqué comme en ligne dans le cache
            Boolean cachedStatus = userStatusCache.get(userEmail);
            if (cachedStatus == null || !cachedStatus) { // null ou false signifie qu'il n'est pas marqué en ligne
                User user = userService.getUserByEmail(userEmail).orElse(null);
                if (user != null && !user.isOnline()) {
                    user.setOnline(true);
                    userService.updateUser(user);
                    userStatusCache.put(userEmail, true); // Mettre à jour le cache
                    notifyContactsOfStatusChange(userEmail, true);
                    System.out.println("Utilisateur " + userEmail + " marqué en ligne");
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la mise à jour de l'activité: " + e.getMessage());
        }
    }

    private void notifyContactsOfStatusChange(String userEmail, boolean isOnline) {
        try {
            Message statusUpdate = new Message();
            statusUpdate.setType(MessageType.STATUS_UPDATE);
            statusUpdate.setSenderEmail(userEmail);
            statusUpdate.setContent(String.valueOf(isOnline));
            chatService.sendMessage(statusUpdate);
            
            System.out.println("Notification de changement de statut envoyée pour " + userEmail + ": " + (isOnline ? "en ligne" : "hors ligne"));
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoi de la mise à jour de statut: " + e.getMessage());
        }
    }
} 