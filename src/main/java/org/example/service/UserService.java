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
}
