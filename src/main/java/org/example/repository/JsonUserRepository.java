package org.example.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.example.Entities.Utilisateur;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JsonUserRepository {
    private static final String USERS_FILE = "data/utilisateurs.json";
    private final ObjectMapper objectMapper;
    private final File usersFile;

    public JsonUserRepository() {
        // this object will be imported from jackson, it handles JSON serialization/deserialization
        // Jackson's main class for JSON operations (JSON conversion)
        this.objectMapper = new ObjectMapper(); 
        // this is the file representing the user json 
        this.usersFile = new File(USERS_FILE);
        // No need for initializeRepository() since file already exists
    }

    // this method takes in a list of users of type Utilisateur
    public void saveUsers(List<Utilisateur> users) throws IOException {
        // this method WriteValues takes the objects in the array users and writes them in the udersFile
        objectMapper.writeValue(usersFile, users);
    }

    // this method reads Users from the json file and returns them
    public List<Utilisateur> loadUsers() throws IOException {
        // if the userFIle is deleted we return an empty list
        if (!usersFile.exists()) {
            throw new IOException("utilisateurs.json file not found in data directory");
        }
        // here we Create a type definition for List<Utilisateur>
        CollectionType listType = objectMapper.getTypeFactory()
            .constructCollectionType(ArrayList.class, Utilisateur.class);

        // here we Read and convert JSON back to Java objects
        return objectMapper.readValue(usersFile, listType);
    }

    // this method calls loadUsers() and then uses streams to filter out only the users with a certain email
    // it returns an optional that can have a user or cannot have anything 
    public Optional<Utilisateur> findByEmail(String email) throws IOException {
        return loadUsers() // get list of users ArrayList<Users>
            .stream()  // convert to stream
            .filter(user -> user.getEmail().equals(email)) // filter users
            .findFirst(); // triggers the execution and returns an Optional with the first user to pass the filter
            // findFirst returns an Optional of type User
                // - this Optional can either be empty if the stream doesn return anything 
                // - or can contain a user

                // - if the Optional is not empty, optn.isPresent() will return true and optn.get() will return the value stored
                // - if its empty optn.isPresent() will return false 
    }

    public void saveUser(Utilisateur user) throws IOException {
        List<Utilisateur> users = loadUsers();
        users.removeIf(u -> u.getEmail().equals(user.getEmail()));
        users.add(user);
        saveUsers(users);
    }

    public void updateUserStatus(String email, boolean isOnline) throws IOException {
        List<Utilisateur> users = loadUsers();
        users.stream()
            .filter(u -> u.getEmail().equals(email))
            .findFirst()
            .ifPresent(u -> {
                u.setOnline(isOnline);
                System.out.println("User " + email + " status updated to: " + isOnline);
            });
        saveUsers(users);
    }

    // Add a method to verify if a user exists and password matches
    public boolean verifyUser(String email, String password) throws IOException {
        Optional<Utilisateur> user = findByEmail(email);
        return user.isPresent() && user.get().getPassword().equals(password);
    }
}
