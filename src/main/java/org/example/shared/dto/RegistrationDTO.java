package org.example.shared.dto;

import java.io.Serializable;

public class RegistrationDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String email;
    private String password;
    private String passwordConfirmation;
    
    // Constructeur par défaut pour Jackson
    public RegistrationDTO() {
    }
    
    public RegistrationDTO(String email, String password, String passwordConfirmation) {
        this.email = email;
        this.password = password;
        this.passwordConfirmation = passwordConfirmation;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getPasswordConfirmation() {
        return passwordConfirmation;
    }
    
    public void setPasswordConfirmation(String passwordConfirmation) {
        this.passwordConfirmation = passwordConfirmation;
    }
}
