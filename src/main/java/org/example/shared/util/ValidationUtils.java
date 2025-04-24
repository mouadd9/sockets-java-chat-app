package org.example.shared.util;

import java.util.regex.Pattern;

public class ValidationUtils {
    
    // Regex pour valider un email
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    
    /**
     * Vérifie si l'email est valide selon le pattern défini
     * @param email l'email à valider
     * @return true si l'email est valide, false sinon
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Vérifie si le mot de passe respecte les critères de sécurité:
     * - Au moins 8 caractères
     * - Au moins une lettre majuscule
     * - Au moins une lettre minuscule
     * - Au moins un chiffre
     * - Au moins un caractère spécial
     * 
     * @param password le mot de passe à valider
     * @return true si le mot de passe est valide, false sinon
     */
    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowercase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }
        
        return hasUppercase && hasLowercase && hasDigit && hasSpecial;
    }
    
    /**
     * Vérifie si les deux mots de passe correspondent
     * @param password le mot de passe
     * @param confirmPassword la confirmation du mot de passe
     * @return true si les mots de passe correspondent, false sinon
     */
    public static boolean doPasswordsMatch(String password, String confirmPassword) {
        return password != null && password.equals(confirmPassword);
    }
}
