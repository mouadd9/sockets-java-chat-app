package org.example.client.gui.service;

import java.io.IOException;

import org.example.shared.dao.UserDAO;
import org.example.shared.model.User;

/**
 * Service pour gérer les utilisateurs avec cache
 */
public class UserService {
    private final UserDAO userDAO;

    public UserService() {
        this.userDAO = new UserDAO();
    }
    
    /**
     * Récupère un utilisateur par email 
     */
    public User getUserByEmail(final String email) throws IOException {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("L'email ne peut pas être vide");
        }
        
        // Sinon, interroger la base de données
        final User user = userDAO.findUserByEmail(email);
        if (user == null) {
            throw new IOException("Utilisateur non trouvé: " + email);
        }
        
        return user;
    }
    
    /**
     * Récupère un utilisateur par ID
     */
    public User getUserById(final long userId) throws IOException {
        if (userId <= 0) {
            throw new IllegalArgumentException("L'ID utilisateur doit être positif");
        }
        
        // Sinon, interroger la base de données
        final User user = userDAO.findUserById(userId);
        if (user == null) {
            throw new IOException("Utilisateur non trouvé avec l'ID: " + userId);
        }
        
        return user;
    }
    
    /**
     * Met à jour un utilisateur dans la base de données et le cache
     */
    public void updateUser(final User user) throws IOException {
        if (user == null) {
            throw new IllegalArgumentException("L'utilisateur ne peut pas être null");
        }
        
        userDAO.updateUser(user);
    }
}
