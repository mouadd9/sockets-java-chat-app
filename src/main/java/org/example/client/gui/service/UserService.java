package org.example.client.gui.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.example.shared.dao.UserDAO;
import org.example.shared.model.User;

/**
 * Service pour gérer les utilisateurs avec cache
 */
public class UserService {
    private final UserDAO userDAO;
    private final Map<String, User> userEmailCache = new HashMap<>();
    private final Map<Long, User> userIdCache = new HashMap<>();

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
        
        // Vérifier le cache d'abord
        if (userEmailCache.containsKey(email)) {
            return userEmailCache.get(email);
        }
        
        // Sinon, interroger la base de données
        final User user = userDAO.findUserByEmail(email);
        if (user == null) {
            throw new IOException("Utilisateur non trouvé avec email: " + email);
        }
        
        // Mettre en cache
        userEmailCache.put(email, user);
        userIdCache.put(user.getId(), user);
        
        return user;
    }
    
    /**
     * Récupère un utilisateur par ID
     */
    public User getUserById(final long userId) throws IOException {
        if (userId <= 0) {
            throw new IllegalArgumentException("L'ID utilisateur doit être positif");
        }
        
        // Vérifier le cache d'abord
        if (userIdCache.containsKey(userId)) {
            return userIdCache.get(userId);
        }
        
        // Sinon, interroger la base de données
        final User user = userDAO.findUserById(userId);
        if (user == null) {
            throw new IOException("Utilisateur non trouvé avec ID: " + userId);
        }
        
        // Mettre en cache
        userEmailCache.put(user.getEmail(), user);
        userIdCache.put(userId, user);
        
        return user;
    }
    
    /**
     * Efface le cache pour forcer le rechargement des données
     */
    public void clearCache() {
        userEmailCache.clear();
        userIdCache.clear();
    }
    
    /**
     * Met à jour un utilisateur dans la base de données et le cache
     */
    public void updateUser(final User user) throws IOException {
        if (user == null) {
            throw new IllegalArgumentException("L'utilisateur ne peut pas être null");
        }
        
        userDAO.updateUser(user);
        
        // Mettre à jour le cache
        userEmailCache.put(user.getEmail(), user);
        userIdCache.put(user.getId(), user);
    }
}
