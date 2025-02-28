package org.example.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.example.model.User;
import org.example.repository.JsonUserRepository;

public class UserService {
    private final JsonUserRepository userRepository;

    public UserService() {
        this.userRepository = new JsonUserRepository();
    }

    /**
     * Authentifie un utilisateur par email et mot de passe
     */
    public boolean authenticateUser(final String email, final String password) throws IOException {
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
        userRepository.updateUserStatus(email, status);
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
     * Ajoute un contact à un utilisateur
     */
    public boolean addContact(final String userEmail, final String contactEmail) throws IOException {
        final Optional<User> userOpt = userRepository.findByEmail(userEmail);
        final Optional<User> contactOpt = userRepository.findByEmail(contactEmail);
        
        if (userOpt.isPresent() && contactOpt.isPresent()) {
            final User user = userOpt.get();
            user.addContact(contactEmail);
            userRepository.saveUser(user);
            return true;
        }
        return false;
    }
    
    /**
     * Supprime un contact d'un utilisateur
     */
    public boolean removeContact(final String userEmail, final String contactEmail) throws IOException {
        final Optional<User> userOpt = userRepository.findByEmail(userEmail);
        
        if (userOpt.isPresent()) {
            final User user = userOpt.get();
            final boolean removed = user.removeContact(contactEmail);
            if (removed) {
                userRepository.saveUser(user);
                return true;
            }
        }
        return false;
    }
}
