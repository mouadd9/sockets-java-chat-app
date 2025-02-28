package org.example.dto;

/**
 * Classe DTO pour les informations d'authentification.
 */
public class Credentials {
    private String email;
    private String password;
    
    // Constructeur par défaut requis pour Jackson
    public Credentials() {
    }
    
    public Credentials(final String email, final String password) {
        this.email = email;
        this.password = password;
    }
    
    // Getters et Setters
    public String getEmail() {
        return email;
    }
    
    public void setEmail(final String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(final String password) {
        this.password = password;
    }
}