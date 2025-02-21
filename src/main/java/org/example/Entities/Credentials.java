package org.example.Entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Credentials {
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("password")
    private String password;

    // Default constructor for Jackson
    public Credentials() {}

    public Credentials(String email, String password) {
        this.email = email;
        this.password = password;
    }

    // Getters and setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}