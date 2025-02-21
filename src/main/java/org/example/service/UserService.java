package org.example.service;

import org.example.Entities.Utilisateur;
import org.example.repository.JsonUserRepository;

import java.io.IOException;
import java.util.Optional;

public class UserService {
    private final JsonUserRepository userRepository;

    public UserService() {
        this.userRepository = new JsonUserRepository();
    }

    public boolean registerUser(String email, String password) throws IOException {
        // Check if user already exists
        if (userRepository.findByEmail(email).isPresent()) {
            return false;
        }

        // Create and save new user
        Utilisateur newUser = new Utilisateur(email, password);
        userRepository.saveUser(newUser);
        return true;
    }

    public boolean authenticateUser(String email, String password) throws IOException {
        Optional<Utilisateur> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            userRepository.updateUserStatus(userOpt.get().getEmail(), true);
            return true;
        }
         return false;
    }

    public void setUserOnlineStatus(String email, boolean isOnline) throws IOException {
        userRepository.updateUserStatus(email, isOnline);
    }
/*
    public boolean addContact(String userEmail, String contactEmail) throws IOException {
        Optional<Utilisateur> userOpt = userRepository.findByEmail(userEmail);
        Optional<Utilisateur> contactOpt = userRepository.findByEmail(contactEmail);
        
        if (userOpt.isPresent() && contactOpt.isPresent()) {
            Utilisateur user = userOpt.get();
            user.addContact(contactEmail);
            userRepository.saveUser(user);
            return true;
        }
        return false;
    } */
}
