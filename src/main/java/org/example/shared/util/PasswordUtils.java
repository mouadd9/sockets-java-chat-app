package org.example.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordUtils {
    
    /**
     * Génère un hash sécurisé du mot de passe
     * Note: Dans une application réelle, on utiliserait un algorithme plus robuste comme bcrypt ou PBKDF2
     * 
     * @param password le mot de passe à hasher
     * @return le hash du mot de passe
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erreur lors du hashage du mot de passe", e);
        }
    }
    
    /**
     * Vérifie si un mot de passe correspond à un hash
     * 
     * @param password le mot de passe en clair
     * @param storedHash le hash stocké
     * @return true si le mot de passe correspond au hash, false sinon
     */
    public static boolean verifyPassword(String password, String storedHash) {
        String passwordHash = hashPassword(password);
        return passwordHash.equals(storedHash);
    }
    
    /**
     * Génère un mot de passe aléatoire
     * 
     * @param length la longueur du mot de passe
     * @return un mot de passe aléatoire
     */
    public static String generateRandomPassword(int length) {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(chars.length());
            sb.append(chars.charAt(randomIndex));
        }
        
        return sb.toString();
    }
}
