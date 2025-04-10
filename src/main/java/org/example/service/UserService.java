package org.example.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.example.model.User;
import org.example.repository.JsonUserRepository;
import org.example.broker.MessageBroker;

public class UserService {
    private final JsonUserRepository userRepository;
    private final Map<String, Long> lastStatusUpdateTime = new ConcurrentHashMap<>();

    public UserService() {
        this.userRepository = new JsonUserRepository();
    }

    /**
     * Authentifie un utilisateur par email et mot de passe
     */
    public boolean authenticate(final String email, final String password) throws IOException {
        final Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            final User user = userOpt.get();
            // Dans une application réelle, utiliser une méthode sécurisée de comparaison de mot de passe
            return user.getPassword().equals(password);
        }
        return false;
    }

    /**
     * Met à jour le statut en ligne d'un utilisateur
     */
    public void setUserOnlineStatus(final String email, final boolean status) throws IOException {
        final Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Ne mettre à jour que si le statut a changé
            if (user.isOnline() != status) {
                user.setOnline(status);
                userRepository.saveUser(user);
                System.out.println("Statut de " + email + " mis à jour dans la base de données: " + (status ? "en ligne" : "hors ligne"));
            }
        } else {
            System.err.println("Tentative de mise à jour du statut pour un utilisateur inexistant: " + email);
        }
    }

    /**
     * Récupère un utilisateur par son email
     */
    public Optional<User> getUserByEmail(final String email) throws IOException {
        return userRepository.findByEmail(email);
    }

    /**
     * Récupère la liste de tous les utilisateurs
     */
    public List<User> getAllUsers() throws IOException {
        return userRepository.loadUsers();
    }

    /**
     * Récupère la liste de tous les utilisateurs en ligne
     */
    public List<User> getOnlineUsers() throws IOException {
        return userRepository.loadUsers().stream()
                .filter(User::isOnline)
                .toList();
    }

    /**
     * Crée un nouvel utilisateur
     */
    public boolean createUser(final User user) throws IOException {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return false; // Utilisateur existe déjà
        }
        userRepository.saveUser(user);
        return true;
    }

    /**
     * Ajoute un contact à un utilisateur.
     * Vérifie que l'utilisateur et le contact existent, que l'utilisateur n'ajoute pas lui-même
     * et qu'il n'y a pas déjà de doublon.
     */
    public boolean addContact(final String userEmail, final String contactEmail) throws IOException {
        // Vérification si l'utilisateur tente de s'ajouter lui-même
        if (userEmail.equals(contactEmail)) {
            throw new IllegalArgumentException("Vous ne pouvez pas vous ajouter vous-même comme contact");
        }
        
        // Vérification de l'existence du contact
        final Optional<User> contactOpt = userRepository.findByEmail(contactEmail);
        if (contactOpt.isEmpty()) {
            throw new IllegalArgumentException("Cet utilisateur n'existe pas");
        }
        
        final Optional<User> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isPresent()) {
            final User user = userOpt.get();
            // Vérification si le contact est déjà présent
            if (user.getContacts().contains(contactEmail)) {
                throw new IllegalArgumentException("Ce contact est déjà dans votre liste");
            }
            user.addContact(contactEmail);
            userRepository.saveUser(user);
            return true;
        }
        throw new IllegalArgumentException("Une erreur est survenue lors de l'ajout du contact");
    }

    /**
     * Supprime un contact de l'utilisateur.
     */
    public boolean removeContact(final String userEmail, final String contactEmail) throws IOException {
        final Optional<User> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isPresent()) {
            final User user = userOpt.get();
            if (user.removeContact(contactEmail)) {
                userRepository.saveUser(user);
                return true;
            }
        }
        return false;
    }

    /**
     * Met à jour un utilisateur existant
     */
    public void updateUser(User user) throws IOException {
        if (userRepository.findByEmail(user.getEmail()).isEmpty()) {
            throw new IOException("Utilisateur non trouvé: " + user.getEmail());
        }
        userRepository.saveUser(user);
    }

    /**
     * Vérifie si un utilisateur est vraiment connecté au serveur
     * Cette méthode est utilisée pour vérifier si un utilisateur est actuellement connecté au serveur,
     * pas seulement marqué comme en ligne dans la base de données
     */
    public boolean isUserReallyConnected(final String email) {
        try {
            // Demander au broker quels utilisateurs ont une session active
            return MessageBroker.getInstance().hasActiveClient(email);
        } catch (Exception e) {
            System.err.println("Erreur lors de la vérification de la connexion: " + e.getMessage());
            return false;
        }
    }

    /**
     * Synchronise les statuts des utilisateurs avec l'état réel des connexions
     */
    public void synchronizeOnlineStatuses() throws IOException {
        List<User> users = getAllUsers();
        for (User user : users) {
            // Vérifier si l'utilisateur a une session active auprès du broker
            boolean isReallyConnected = isUserReallyConnected(user.getEmail());
            
            // Ne mettre à jour que s'il y a une différence et éviter les mises à jour trop fréquentes
            if (user.isOnline() != isReallyConnected) {
                // Vérifier le timestamp de la dernière mise à jour pour éviter les oscillations
                Long lastUpdateTime = lastStatusUpdateTime.getOrDefault(user.getEmail(), 0L);
                long currentTime = System.currentTimeMillis();
                
                // N'autoriser les mises à jour qu'après 30 secondes depuis la dernière modification
                if (currentTime - lastUpdateTime > 30000) {
                    System.out.println("Correction du statut de " + user.getEmail() + ": " 
                        + (user.isOnline() ? "en ligne" : "hors ligne") 
                        + " -> " + (isReallyConnected ? "en ligne" : "hors ligne"));
                        
                    setUserOnlineStatus(user.getEmail(), isReallyConnected);
                    lastStatusUpdateTime.put(user.getEmail(), currentTime);
                }
            }
        }
    }
}
