package org.example.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.model.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

public class JsonUserRepository {
    private static final String USERS_FILE = "src/main/data/utilisateurs.json";
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

    // this method takes in a list of users of type User
    public void saveUsers(final List<User> users) throws IOException {
        // this method WriteValues takes the objects in the array users and writes them in the udersFile
        objectMapper.writeValue(usersFile, users);
    }

    // this method reads Users from the json file and returns them
    public List<User> loadUsers() throws IOException {
        // if the userFIle is deleted we return an empty list
        if (!usersFile.exists()) {
            throw new IOException("utilisateurs.json file not found in data directory");
        }
        // here we Create a type definition for List<User>
        final CollectionType listType = objectMapper.getTypeFactory()
            .constructCollectionType(ArrayList.class, User.class);

        // here we Read and convert JSON back to Java objects
        return objectMapper.readValue(usersFile, listType);
    }

    // this method calls loadUsers() and then uses streams to filter out only the users with a certain email
    // it returns an optional that can have a user or cannot have anything 
    public Optional<User> findByEmail(final String email) throws IOException {
        return loadUsers() // get list of users ArrayList<Users>
            .stream()  // convert to stream
            .filter(user -> user.getEmail().equals(email)) // filter users
            .findFirst(); // triggers the execution and returns an Optional with the first user to pass the filter
            // findFirst returns an Optional of type User
                // - this Optional can either be empty if the stream doesn return anything 
                // - or can contain a user

                // - if the Optional is not empty, optn.isPresent() will return true and optn.get() will return the value stored
                // - if its empty optn.isPresent() will return false
        // - opts.empty() true
        // opts.ifPresent(u -> u.setStatus(true))
    }



    public void saveUser(final User user) throws IOException {
        final List<User> users = loadUsers();
        users.removeIf(u -> u.getEmail().equals(user.getEmail()));
        users.add(user);
        saveUsers(users);
    }

    public void updateUserStatus(final String email, final boolean isOnline) throws IOException {
        final List<User> users = loadUsers();
        users.stream()
            .filter(u -> u.getEmail().equals(email))
            .findFirst()
            .ifPresent(u -> {
                u.setOnline(isOnline);
                System.out.println("User " + email + " status updated to: " + isOnline);
            });
        saveUsers(users);
    }

    public void updateUser(User user) throws IOException {
        if (user == null || user.getEmail() == null) {
            throw new IllegalArgumentException("User and email cannot be null");
        }

        List<User> users = loadUsers();
        boolean found = false;

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getEmail().equals(user.getEmail())) {
                users.set(i, user);
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IllegalArgumentException("User not found: " + user.getEmail());
        }

        saveUsers(users);
    }
}
